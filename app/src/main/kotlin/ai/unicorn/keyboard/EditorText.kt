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

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/** Reads and replaces the text the AI tools work on: the selection if there is one, otherwise the whole field. */
object EditorText {
    private const val MAX_CHARS = 4000

    data class Target(val text: String, val isSelection: Boolean)

    fun read(ic: InputConnection?): Target? {
        ic ?: return null
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrBlank()) return Target(selected, isSelection = true)
        val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAX_CHARS }, 0)?.text
        val text = extracted?.toString() ?: run {
            val before = ic.getTextBeforeCursor(MAX_CHARS, 0) ?: return null
            val after = ic.getTextAfterCursor(MAX_CHARS, 0) ?: ""
            "$before$after"
        }
        return Target(text, isSelection = false)
    }

    /** Replaces [target] with [replacement]; the caller passes the target it read just before. */
    /** The whole field, ignoring any selection. */
    fun readAll(ic: InputConnection?): String? {
        ic ?: return null
        val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAX_CHARS }, 0)?.text
        if (extracted != null) return extracted.toString()
        val before = ic.getTextBeforeCursor(MAX_CHARS, 0) ?: return null
        val selected = ic.getSelectedText(0) ?: ""
        val after = ic.getTextAfterCursor(MAX_CHARS, 0) ?: ""
        return "$before$selected$after"
    }

    /** Replaces everything in the field. */
    fun replaceAll(ic: InputConnection?, replacement: String) = replace(ic, Target("", isSelection = false), replacement)

    fun replace(ic: InputConnection?, target: Target, replacement: String) {
        ic ?: return
        // The field may be empty for a moment; that is Unicorn editing, not the user sending
        ai.unicorn.Unicorn.markLocalEdit()
        ic.beginBatchEdit()
        ic.finishComposingText()
        if (target.isSelection) {
            ic.commitText(replacement, 1)
        } else {
            ic.deleteSurroundingText(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
            ic.commitText(replacement, 1)
        }
        ic.endBatchEdit()
    }
}
