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

package ai.unicorn.keyboard

import ai.unicorn.Unicorn
import android.content.Context
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import com.android.inputmethod.latin.common.Constants

/**
 * Ask Unicorn's prompt bar. While it is open the keys type into the prompt instead of the app; sending it shows the
 * answers over the keys, and tapping one puts it into the app. The app's own text and the copied message are the
 * material the request works with, so a reply or an email fits the conversation.
 */
class AskSession(private val context: Context) {
    data class Request(
        val command: AskCommand,
        val instruction: String,
        /** What the user had typed in the app (or selected), reworked or replaced by the answer. */
        val draft: EditorText.Target?,
        val thread: String?,
        val writeContext: WriteContext,
        /** Who or what the thread is, shown to the user: "Priya" or "the text you copied". */
        val threadFrom: String? = null,
    )

    var isOpen = false
        private set
    var text = ""
        private set
    var writeContext = WriteContext(AppKind.OTHER, false)
        private set
    /** Whether the app's field had text when the bar opened, which changes the suggested starters. */
    var hasDraft = false
        private set
    /** The app being typed in, to find its recent conversations. */
    var packageName: String? = null
        private set

    /** Called after every change, so the bar and the starter chips redraw. */
    var onChange: () -> Unit = {}

    fun open(editorInfo: EditorInfo?) {
        isOpen = true
        text = ""
        writeContext = WriteContext.of(editorInfo)
        packageName = editorInfo?.packageName
        hasDraft = EditorText.read(Unicorn.currentInputConnection())?.text?.isNotBlank() == true
        onChange()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        text = ""
        onChange()
    }

    fun setPrompt(value: String) {
        text = value
        onChange()
    }

    /** Handles a key while the bar is open. Returns false for keys the engine should still handle (shift, symbols). */
    fun onKey(codePoint: Int, keyCode: Int, submit: () -> Unit): Boolean {
        when {
            codePoint == Constants.CODE_ENTER || keyCode == Constants.CODE_SHIFT_ENTER -> submit()
            keyCode == Constants.CODE_DELETE -> {
                if (text.isNotEmpty()) text = text.substring(0, text.offsetByCodePoints(text.length, -1))
                onChange()
            }
            keyCode == Constants.CODE_ACTION_NEXT || keyCode == Constants.CODE_ACTION_PREVIOUS -> Unit
            codePoint > 0 -> {
                text += String(Character.toChars(codePoint))
                onChange()
            }
            else -> return false
        }
        return true
    }

    fun onText(value: String) {
        text += value
        onChange()
    }

    fun onSwipedWord(word: String) {
        text += if (text.isEmpty() || text.endsWith(" ")) word else " $word"
        onChange()
    }

    /** Shift for the prompt: a capital to start, lower case after. */
    fun capsMode(): Int = if (text.isBlank()) TextUtils.CAP_MODE_SENTENCES else Constants.TextUtils.CAP_MODE_OFF

    /**
     * [conversation] is the one the user picked from the starters. Otherwise a reply answers the latest conversation
     * in this app (from its notifications), then text copied in the last few minutes. Older copied text is never
     * used: it is usually something else entirely.
     */
    fun request(prompt: String = text, conversation: RecentMessages.Conversation? = null): Request? {
        val parsed = ParsedPrompt.parse(prompt)
        val draft = EditorText.read(Unicorn.currentInputConnection())?.takeIf { it.text.isNotBlank() }
        if (parsed.command == AskCommand.FREE && parsed.instruction.isBlank()) return null
        if (!parsed.command.usesCopied) return Request(parsed.command, parsed.instruction, draft, null, writeContext)
        val copied = { CopiedText.readFresh(context)?.let { it to "the text you copied" } }
        val latest = { (conversation ?: RecentMessages.recent(packageName, 1).firstOrNull())?.let { it.asThread() to it.title } }
        // Summaries are mostly asked for long copied text; replies for the conversation on screen
        val (thread, from) = when {
            conversation != null -> latest()
            parsed.command == AskCommand.SUMMARIZE -> copied() ?: latest()
            else -> latest() ?: copied()
        } ?: (null to null)
        return Request(parsed.command, parsed.instruction, draft, thread, writeContext, from)
    }

    /** Puts an answer into the app: reworked drafts replace the draft, anything else goes in at the cursor. */
    fun insert(request: Request, answer: String) {
        val ic = Unicorn.currentInputConnection() ?: return
        Unicorn.finishComposing()
        EditorText.readAll(ic)?.let { Unicorn.rememberForUndo(it) }
        val replacesDraft = request.draft != null && when (request.command) {
            AskCommand.CONTINUE, AskCommand.SUMMARIZE -> false
            else -> true
        }
        if (replacesDraft) {
            EditorText.replace(ic, request.draft!!, answer)
        } else {
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            val glue = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
            ic.commitText(glue + answer.trimStart(), 1)
        }
        close()
    }
}
