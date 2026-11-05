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

package ai.unicorn.memory

import ai.unicorn.UnicornPrefs
import ai.unicorn.keyboard.SensitiveField
import android.os.SystemClock
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saves the prompts the user sends in allow-listed AI apps ([CaptureSource]) and provides typing-time recall.
 *
 * A field is used only if all of these hold: the user consented and turned saving on, the app is in [CaptureSource]
 * and enabled, the field is plain text (never passwords, email, numbers or phone), and the app did not mark the field
 * as private (IME_FLAG_NO_PERSONALIZED_LEARNING).
 *
 * A prompt counts as sent when:
 *  - the keyboard performs the field's action (send, search, go, ...),
 *  - the app empties a non-empty field without the user deleting the text. This is tracked while the field stays
 *    connected, including after an app hides the keyboard on send.
 * Text the user deletes, and text still in the field when the app disconnects it (a draft), is never saved.
 */
class PromptCapture(
    private val prefs: UnicornPrefs,
    private val memory: MemoryRepository,
    private val diagnostics: CaptureDiagnostics,
    private val onSaved: (CaptureSource) -> Unit,
) {
    companion object {
        private const val MIN_PROMPT_LENGTH = 3
        private const val MAX_PROMPT_LENGTH = 4000
        private const val LOCAL_DELETE_GRACE_MS = 750L
        private const val DUPLICATE_WINDOW_MS = 30_000L
        /** A field that disconnects holding text and reconnects empty this quickly was cleared by the app. */
        private const val RECONNECT_GRACE_MS = 3_000L
        /**
         * Apps that move to a new screen on send hide the keys and close the field together. When the keys were put
         * away this long before the field closed, the user dismissed them and then left: the text was a draft.
         */
        private const val HIDDEN_BEFORE_CLOSE_MS = 700L
    }

    private class Closed(
        val source: CaptureSource,
        val fieldKey: String,
        val text: String,
        val at: Long,
        val userHidKeys: Boolean,
    )

    /** Supplies the current editor connection (the keyboard service's). */
    var inputConnection: () -> InputConnection? = { null }

    private val _activeSource = MutableStateFlow<CaptureSource?>(null)
    /** The app whose field is currently being saved from, or null. */
    val activeSource = _activeSource.asStateFlow()

    private var lastSeenText = ""
    private var lastFieldKey = ""
    private var recentlyClosed: Closed? = null
    private var lastLocalDeleteAt = 0L
    private var lastSavedText = ""
    private var lastSavedAt = 0L
    private var keysHiddenAt = 0L

    /**
     * Called whenever a field connects, whether or not the keys are shown. [restarting] is true when the app
     * restarted input on the same field, which many apps trigger by replacing its text, e.g. clearing it after a send.
     */
    fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
        val closed = recentlyClosed?.takeIf { SystemClock.uptimeMillis() - it.at < RECONNECT_GRACE_MS }
        recentlyClosed = null
        val previousSource = _activeSource.value ?: closed?.source
        val previousText = lastSeenText.ifBlank { closed?.text.orEmpty() }
        val previousFieldKey = if (_activeSource.value != null) lastFieldKey else closed?.fieldKey.orEmpty()
        val fieldKey = fieldKey(editorInfo)
        lastFieldKey = fieldKey

        val listed = CaptureSource.fromPackageName(editorInfo.packageName)
        if (listed == null) {
            // Another app came up: a field closed with text in it was left as a draft
            closed?.let { diagnostics.log("left app=${it.source.id} with a draft: not saved") }
            _activeSource.value = null
            lastSeenText = ""
            return
        }
        val reason = rejectReason(listed, editorInfo)
        diagnostics.log(
            "start app=${listed.id} restarting=$restarting inputType=0x%x imeOptions=0x%x -> %s".format(
                editorInfo.inputType, editorInfo.imeOptions, reason ?: "saving",
            )
        )
        val source = listed.takeIf { reason == null }
        _activeSource.value = source
        val current = if (source != null) readFieldText().orEmpty() else ""
        lastSeenText = current

        // The field closed holding text and the same app moved on to a screen with nothing to type in, as apps do
        // right after a first question is sent (Perplexity, a new chat in Claude). Moving to another text box
        // instead is ambiguous and is not counted.
        val isTextField = editorInfo.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        if (closed != null && closed.source == listed && !isTextField) {
            when {
                closed.userHidKeys -> diagnostics.log("left the screen app=${listed.id} after hiding the keys: draft not saved")
                SystemClock.uptimeMillis() - lastLocalDeleteAt <= LOCAL_DELETE_GRACE_MS ->
                    diagnostics.log("cleared by user app=${listed.id}: not saved")
                else -> save(closed.text, closed.source, trigger = "new-screen")
            }
            return
        }

        val clearedOnRestart = source != null && source == previousSource && fieldKey == previousFieldKey &&
            previousText.isNotBlank() && current.isEmpty()
        if (clearedOnRestart) {
            if (SystemClock.uptimeMillis() - lastLocalDeleteAt > LOCAL_DELETE_GRACE_MS) {
                save(previousText, source!!, trigger = "cleared-on-restart")
            } else {
                diagnostics.log("cleared by user app=${source!!.id}: not saved")
            }
        }
    }

    /** The keyboard was hidden but the field stays connected: keep tracking it, apps often clear it right after. */
    /** The keys are on screen again. */
    fun onStartInputView() {
        keysHiddenAt = 0L
    }

    fun onFinishInputView() {
        keysHiddenAt = SystemClock.uptimeMillis()
        val source = _activeSource.value ?: return
        diagnostics.log("keyboard hidden app=${source.id}, still tracking the field")
    }

    /**
     * The app disconnected the field. Text still in it is a draft and is not saved, unless the same field reconnects
     * empty within [RECONNECT_GRACE_MS] (see [onStartInput]).
     */
    fun onFinishInput() {
        val source = _activeSource.value
        if (source != null && lastSeenText.isNotBlank()) {
            val now = SystemClock.uptimeMillis()
            val userHidKeys = keysHiddenAt > 0 && now - keysHiddenAt > HIDDEN_BEFORE_CLOSE_MS
            recentlyClosed = Closed(source, lastFieldKey, lastSeenText, now, userHidKeys)
            diagnostics.log("field closed app=${source.id} with ${lastSeenText.length} chars")
        }
        _activeSource.value = null
        lastSeenText = ""
    }

    /** Called after every selection update of the focused field. */
    fun onSelectionUpdate() {
        val source = _activeSource.value ?: return
        val text = readFieldText() ?: return
        if (text.isEmpty() && lastSeenText.isNotBlank()) {
            if (SystemClock.uptimeMillis() - lastLocalDeleteAt > LOCAL_DELETE_GRACE_MS) {
                save(lastSeenText, source, trigger = "cleared-by-app")
            } else {
                diagnostics.log("cleared by user app=${source.id}: not saved")
            }
        }
        lastSeenText = text
    }

    /** Called right before the keyboard performs the field's action (send, search, go, ...). */
    fun onEditorAction() {
        val source = _activeSource.value ?: return
        save(readFieldText() ?: lastSeenText, source, trigger = "editor-action")
        lastSeenText = ""
    }

    /** Called whenever the user deletes text with the keyboard, so clearing a draft is never a send. */
    fun onLocalDelete() {
        lastLocalDeleteAt = SystemClock.uptimeMillis()
    }

    /** Past prompts similar to what is being typed, only while saving is active in an AI app. */
    fun recall(currentText: String): List<MemoryRepository.RecallHit> {
        if (_activeSource.value == null || !prefs.recallEnabled) return emptyList()
        return memory.recall(currentText)
    }

    private fun rejectReason(source: CaptureSource, editorInfo: EditorInfo): String? {
        if (!prefs.captureConsent) return "off: no consent"
        if (!prefs.captureEnabled) return "off: paused"
        if (!prefs.isSourceEnabled(source)) return "off: app disabled"
        if (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return "skip: app marked field private"
        val type = editorInfo.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return "skip: not a text field"
        if (SensitiveField.of(editorInfo) && !source.searchBarOnly) return "skip: private field"
        val variation = type and InputType.TYPE_MASK_VARIATION
        if (source.searchBarOnly) {
            return if (variation == InputType.TYPE_TEXT_VARIATION_URI) null else "skip: not the address bar"
        }
        return when (variation) {
            InputType.TYPE_TEXT_VARIATION_NORMAL,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            -> null
            else -> "skip: field variation 0x%x".format(variation)
        }
    }

    private fun readFieldText(): String? {
        val ic = inputConnection() ?: return null
        val request = ExtractedTextRequest().apply { hintMaxChars = MAX_PROMPT_LENGTH }
        ic.getExtractedText(request, 0)?.text?.let { return it.toString() }
        val before = ic.getTextBeforeCursor(MAX_PROMPT_LENGTH, 0) ?: return null
        val selected = ic.getSelectedText(0) ?: ""
        val after = ic.getTextAfterCursor(MAX_PROMPT_LENGTH, 0) ?: ""
        return "$before$selected$after"
    }

    private fun save(rawText: String, source: CaptureSource, trigger: String) {
        val text = rawText.trim().take(MAX_PROMPT_LENGTH)
        if (text.length < MIN_PROMPT_LENGTH) {
            diagnostics.log("not saved app=${source.id} trigger=$trigger: too short")
            return
        }
        if (source.searchBarOnly && text.none { it.isWhitespace() } && (text.contains('.') || text.contains('/'))) {
            diagnostics.log("not saved app=${source.id} trigger=$trigger: web address")
            return
        }
        val now = System.currentTimeMillis()
        if (text == lastSavedText && now - lastSavedAt < DUPLICATE_WINDOW_MS) return
        lastSavedText = text
        lastSavedAt = now
        memory.addKeyboardPrompt(source, text)
        diagnostics.log("saved app=${source.id} trigger=$trigger length=${text.length}")
        onSaved(source)
    }

    /**
     * Identifies a text field across reconnects. The view id alone is not enough: every field on a Jetpack Compose
     * screen shares its host view's id, so switching from the chat box to a search box would look like the same field
     * coming back empty, and the abandoned draft would be saved. The hint is left out on purpose: chat apps change it
     * once a conversation starts ("How can I help you today?" becomes "Reply to Claude…").
     */
    private fun fieldKey(editorInfo: EditorInfo): String = listOf(
        editorInfo.packageName,
        editorInfo.fieldId,
        editorInfo.fieldName,
        editorInfo.inputType,
        editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION,
    ).joinToString("#")
}
