/*
 * Copyright (C) 2026 Unicorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.unicorn

import ai.unicorn.ai.AiClient
import ai.unicorn.memory.CaptureDiagnostics
import ai.unicorn.memory.MEMORY_DATABASE_NAME
import ai.unicorn.memory.MemoryRepository
import ai.unicorn.memory.PromptCapture
import ai.unicorn.trends.HunchesRepository
import ai.unicorn.trends.PlaceRepository
import ai.unicorn.trends.TrendsRepository
import ai.unicorn.keyboard.AskSession
import ai.unicorn.keyboard.EditorText
import ai.unicorn.keyboard.KeyArea
import ai.unicorn.keyboard.RecentMessages
import ai.unicorn.keyboard.SensitiveField
import ai.unicorn.keyboard.TapAim
import ai.unicorn.keyboard.TextActionKey
import ai.unicorn.keyboard.UnicornBar
import ai.unicorn.keyboard.VoiceTyping
import ai.unicorn.words.PersonalWords
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.ImageButton
import android.util.Log
import android.widget.Toast
import com.android.inputmethod.latin.LatinIME
import com.android.inputmethod.latin.RichInputMethodManager
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * App-wide Unicorn services, shared by the keyboard service and the app UI (same process).
 *
 * Prompt memory lives in credential-encrypted storage, so it only exists once the user unlocked the phone after boot;
 * before that the keyboard works normally and nothing is saved.
 */
class UnicornCore internal constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val prefs = UnicornPrefs(appContext)
    val diagnostics = CaptureDiagnostics(appContext)
    val ai by lazy { AiClient(prefs) }
    val trends by lazy { TrendsRepository(this) }
    val places by lazy { PlaceRepository(appContext, prefs) }
    val hunches by lazy { HunchesRepository(this) }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var memoryInstance: MemoryRepository? = null
    private var captureInstance: PromptCapture? = null

    val isUnlocked: Boolean
        get() = appContext.getSystemService(UserManager::class.java)?.isUserUnlocked != false

    /** The prompt memory, or null before the first unlock. */
    val memory: MemoryRepository?
        @Synchronized get() {
            if (memoryInstance == null && isUnlocked) {
                memoryInstance = MemoryRepository(appContext, memoryDatabasePath())
            }
            return memoryInstance
        }

    /** Prompt saving, or null before the first unlock. */
    val capture: PromptCapture?
        @Synchronized get() {
            if (captureInstance == null) {
                val memory = memory ?: return null
                captureInstance = PromptCapture(prefs, memory, diagnostics) { onPromptSaved() }
            }
            return captureInstance
        }

    /** App-private, credential-encrypted storage: readable only after the user unlocked the phone. */
    private fun memoryDatabasePath(): String = appContext.getDatabasePath(MEMORY_DATABASE_NAME).path

    private fun onPromptSaved() {
        if (!prefs.notifyOnSave) return
        mainHandler.post { Toast.makeText(appContext, "Saved to Unicorn", Toast.LENGTH_SHORT).show() }
    }
}

/** Entry points for the keyboard engine (Java) and the app. */
object Unicorn {
    @Volatile
    private var core: UnicornCore? = null
    private var ime = WeakReference<InputMethodService>(null)

    @JvmStatic
    fun get(context: Context): UnicornCore {
        return core ?: synchronized(this) {
            core ?: UnicornCore(context).also { core = it }
        }
    }

    private var bar = WeakReference<UnicornBar>(null)
    private var editorInfo: EditorInfo? = null
    private var voice: VoiceTyping? = null
    private var ask: AskSession? = null
    private var textKey: TextActionKey? = null

    fun currentInputConnection(): InputConnection? = ime.get()?.currentInputConnection

    fun hideKeyboard() {
        ime.get()?.requestHideSelf(0)
    }

    fun attachBar(view: UnicornBar) {
        bar = WeakReference(view)
        voice?.let { view.showVoice(it.state) }
        ask?.let { view.showAsk(it) }
    }

    fun detachBar(view: UnicornBar) {
        if (bar.get() === view) bar = WeakReference(null)
    }

    /** Unicorn is about to rewrite the field itself, so an emptied field is not a sent prompt. */
    fun markLocalEdit() {
        core?.capture?.onLocalDelete()
    }

    /** Settles the word being typed before Unicorn writes to the field. */
    fun finishComposing() {
        val service = ime.get()
        if (service is LatinIME) service.unicornFinishComposing() else currentInputConnection()?.finishComposingText()
    }

    // Ask Unicorn ---------------------------------------------------------------------------------------------------

    fun openAsk() {
        val service = ime.get() ?: return
        voice?.cancel()
        finishComposing()
        session(service).open(editorInfo)
        (service as? LatinIME)?.unicornUpdateShiftState(true)
    }

    fun setAskPrompt(text: String) {
        ask?.setPrompt(text)
    }

    /** Stops suggesting a word the user removed from their words, if the keys are running. */
    fun forgetLearnedWord(word: String) {
        (ime.get() as? LatinIME)?.unicornForgetWord(word)
    }

    /** The free daily AI ran out: AI views close and the tools hide, quietly. */
    fun restAi() {
        bar.get()?.restAi()
    }

    fun toggleVoiceFromBar() {
        ime.get()?.let { toggleVoice(it) }
    }

    fun closeAsk() {
        ask?.close()
        (ime.get() as? LatinIME)?.unicornUpdateShiftState(false)
    }

    fun submitAsk(prompt: String? = null, conversation: RecentMessages.Conversation? = null) {
        val session = ask ?: return
        val request = session.request(prompt ?: session.text, conversation) ?: return
        bar.get()?.showAskResults(request)
    }

    /** Opens the system screen where the user lets Unicorn read message notifications, hiding the keys first. */
    fun openNotificationAccess() {
        val service = ime.get() ?: return
        hideKeyboard()
        runCatching { service.startActivity(RecentMessages.settingsIntent(service)) }
    }

    fun insertAnswer(request: AskSession.Request, answer: String) {
        ask?.insert(request, answer)
        (ime.get() as? LatinIME)?.unicornUpdateShiftState(false)
    }

    private fun session(context: Context): AskSession = ask ?: AskSession(context.applicationContext).also { session ->
        session.onChange = {
            bar.get()?.showAsk(session)
            // Chips and voice change the prompt without a key press, so shift follows here too
            if (session.isOpen) (ime.get() as? LatinIME)?.unicornUpdateShiftState(true)
        }
        ask = session
    }

    @JvmStatic
    fun promptCapsMode(): Int = runCatching { ask?.takeIf { it.isOpen }?.capsMode() ?: 0 }.getOrDefault(0)

    // Voice typing --------------------------------------------------------------------------------------------------

    @JvmStatic
    fun toggleVoice(service: InputMethodService) {
        safely("toggleVoice") {
            ime = WeakReference(service)
            val voice = voice(service)
            if (voice.isActive) {
                voice.stop()
                return
            }
            if (isSensitiveField()) {
                bar.get()?.showVoice(VoiceTyping.State.Failed("Voice is off in private fields like passwords and payments"))
                return
            }
            finishComposing()
            val subtype = RichInputMethodManager.getInstance().currentSubtypeLocale
            val device = Locale.getDefault()
            voice.start(if (subtype.language == device.language) device else subtype)
        }
    }

    private fun voice(context: Context): VoiceTyping = voice ?: VoiceTyping(context).also { voice ->
        voice.onState = { state -> bar.get()?.showVoice(state) }
        voice.onText = { text -> deliverDictation(text) }
        this.voice = voice
    }

    private fun deliverDictation(text: String) {
        val session = ask
        if (session != null && session.isOpen) {
            val glue = if (session.text.isNotEmpty() && !session.text.last().isWhitespace()) " " else ""
            session.onText(glue + text)
            return
        }
        val ic = currentInputConnection() ?: return
        finishComposing()
        val before = ic.getTextBeforeCursor(3, 0)?.toString().orEmpty()
        val sentenceStart = before.isBlank() || before.trimEnd().lastOrNull()?.let { it in ".!?\n" } == true
        val phrase = if (sentenceStart) text.replaceFirstChar { it.titlecase(Locale.getDefault()) } else text
        val glue = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        ic.commitText(glue + phrase, 1)
    }

    // Select all, clear and undo -------------------------------------------------------------------------------------

    @JvmStatic
    fun attachTextKey(button: ImageButton) {
        safely("attachTextKey") {
            textKey = TextActionKey(button) { currentInputConnection() }
        }
    }

    /** Offers undo back to [previousText] on the key at the left of the suggestions. */
    fun rememberForUndo(previousText: String) {
        textKey?.rememberForUndo(previousText)
    }

    /** True while the phone is on its lock screen: the keys then show nothing Unicorn remembers. */
    fun isPhoneLocked(context: Context): Boolean =
        context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Passwords, codes, numbers, private fields and payment apps: Unicorn's extras stay out (see [SensitiveField]). */
    fun isSensitiveField(): Boolean = SensitiveField.of(editorInfo)

    // Keyboard engine hooks ---------------------------------------------------------------------------------------
    // Every hook runs inside safely(): Unicorn's extras must never break typing. A failure is noted in the saving
    // activity log (the error type and where, never text) and the keys carry on as plain keys.

    @PublishedApi
    internal fun noteFailure(where: String, e: Throwable) {
        Log.w("Unicorn", "$where failed", e)
        core?.diagnostics?.log("recovered from an error in $where: ${e.javaClass.simpleName} at ${e.stackTrace.firstOrNull()}")
    }

    @PublishedApi
    internal inline fun safely(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            noteFailure(where, e)
        } catch (e: LinkageError) {
            noteFailure(where, e)
        }
    }

    @JvmStatic
    fun onStartInput(service: InputMethodService, editorInfo: EditorInfo?, restarting: Boolean) {
        // The engine replays a pending start with no field after a rotation; there is nothing to track then
        if (editorInfo == null) return
        safely("onStartInput") {
            ime = WeakReference(service)
            this.editorInfo = editorInfo
            val capture = get(service).capture ?: return
            capture.inputConnection = { ime.get()?.currentInputConnection }
            capture.onStartInput(editorInfo, restarting)
        }
    }

    @JvmStatic
    fun onStartInputView(service: InputMethodService) {
        safely("onStartInputView") {
            ime = WeakReference(service)
            KeyArea.onStartInputView(service)
            get(service).capture?.onStartInputView()
            bar.get()?.closePanel()
            bar.get()?.refreshTools()
            closeAsk()
            val info = editorInfo
            textKey?.update(info?.initialSelStart ?: 0, info?.initialSelEnd ?: 0, isSensitiveField())
            PersonalWords.loadShortcuts(service)
            refreshBar(service)
            // The microphone was just allowed from the keys: start listening as they come back
            voice?.takeIf { it.pendingAfterPermission }?.let {
                it.pendingAfterPermission = false
                toggleVoice(service)
            }
        }
    }

    @JvmStatic
    fun onFinishInputView(service: InputMethodService) {
        safely("onFinishInputView") {
            KeyArea.onFinishInputView()
            voice?.cancel()
            ask?.close()
            get(service).capture?.onFinishInputView()
        }
    }

    @JvmStatic
    fun onFinishInput(service: InputMethodService) {
        safely("onFinishInput") {
            get(service).capture?.onFinishInput()
            bar.get()?.update(null, emptyList(), "")
        }
    }

    @JvmStatic
    fun onSelectionUpdate(service: InputMethodService, selStart: Int, selEnd: Int) {
        safely("onSelectionUpdate") {
            get(service).capture?.onSelectionUpdate()
            textKey?.update(selStart, selEnd, isSensitiveField())
            refreshBar(service)
        }
    }

    /** While the prompt bar is open the keys type into it. A key also ends voice typing. */
    @JvmStatic
    fun interceptKey(codePoint: Int, keyCode: Int): Boolean {
        safely("interceptKey") {
            voice?.takeIf { it.isActive }?.stop()
            val session = ask?.takeIf { it.isOpen } ?: return false
            return session.onKey(codePoint, keyCode) { submitAsk() }
        }
        return false
    }

    @JvmStatic
    fun interceptText(text: String): Boolean {
        safely("interceptText") {
            val session = ask?.takeIf { it.isOpen } ?: return false
            session.onText(text)
            return true
        }
        return false
    }

    /** A word glided while the prompt bar is open goes into the prompt, after a space, not into the app. */
    @JvmStatic
    fun interceptSwipedWord(word: String): Boolean {
        safely("interceptSwipedWord") {
            val session = ask?.takeIf { it.isOpen } ?: return false
            session.onSwipedWord(word)
            return true
        }
        return false
    }

    /** Learns the user's words for the words page, never in password or incognito fields. */
    @JvmStatic
    fun onWordCommitted(service: LatinIME, word: String, sentenceStart: Boolean, insisted: Boolean) {
        safely("onWordCommitted") {
            // Learn my aim: a word typed without fixing tells where this person lands on each letter
            if (editorInfo != null && !isSensitiveField()) TapAim.onWordCommitted(word)
            if (!get(service).prefs.learnWords) return
            if (editorInfo == null || isSensitiveField()) return
            if (!PersonalWords.isLearnable(word)) return
            PersonalWords.record(service, word, sentenceStart, service.unicornIsKnownWord(word), insisted)
        }
    }

    private fun refreshBar(context: Context) {
        val view = bar.get() ?: return
        val capture = get(context).capture
        val source = capture?.activeSource?.value
        if (capture == null || source == null) {
            view.update(null, emptyList(), "")
            return
        }
        val text = EditorText.read(currentInputConnection())?.text.orEmpty()
        // Earlier questions never show over the lock screen
        view.update(source, if (isPhoneLocked(context)) emptyList() else capture.recall(text), text)
    }

    @JvmStatic
    fun onEditorAction(context: Context) {
        safely("onEditorAction") {
            get(context).capture?.onEditorAction()
        }
    }

    @JvmStatic
    fun onLocalDelete(context: Context) {
        safely("onLocalDelete") {
            get(context).capture?.onLocalDelete()
        }
    }
}
