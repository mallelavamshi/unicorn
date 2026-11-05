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

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.ImageButton
import com.android.inputmethod.latin.R

/**
 * The small key at the left end of the suggestions. It shows only while the field has text: the first tap selects
 * everything, the second clears it, and for a few seconds after a clear or an AI rewrite it offers undo.
 */
class TextActionKey(private val view: ImageButton, private val connection: () -> InputConnection?) {
    private enum class Mode(val icon: Int, val description: String) {
        HIDDEN(0, ""),
        SELECT_ALL(R.drawable.ic_unicorn_select_all, "Select all"),
        CLEAR(R.drawable.ic_unicorn_clear_all, "Clear all"),
        UNDO(R.drawable.ic_unicorn_undo, "Undo"),
    }

    private val style = KeyboardStyle(view.context)
    private val handler = Handler(Looper.getMainLooper())
    private var mode = Mode.HIDDEN
    private var undoText: String? = null
    private var hidden = true
    private var selStart = 0
    private var selEnd = 0

    private val expireUndo = Runnable {
        undoText = null
        refresh()
    }

    init {
        view.visibility = View.INVISIBLE
        view.setOnClickListener { onTap() }
    }

    /** [hidden] covers password fields, where nothing is kept for undo. */
    fun update(selStart: Int, selEnd: Int, hidden: Boolean) {
        this.selStart = selStart
        this.selEnd = selEnd
        this.hidden = hidden
        if (hidden) undoText = null
        refresh()
    }

    /** Offers undo back to [previousText] for a few seconds. */
    fun rememberForUndo(previousText: String) {
        if (hidden) return
        undoText = previousText
        handler.removeCallbacks(expireUndo)
        handler.postDelayed(expireUndo, UNDO_MS)
        refresh()
    }

    private fun refresh() {
        val ic = connection()
        val next = when {
            hidden || ic == null -> Mode.HIDDEN
            undoText != null -> Mode.UNDO
            else -> {
                val before = ic.getTextBeforeCursor(1, 0)?.isNotEmpty() == true
                val after = ic.getTextAfterCursor(1, 0)?.isNotEmpty() == true
                val selected = selEnd > selStart
                when {
                    !before && !after && !selected -> Mode.HIDDEN
                    // Nothing outside the selection: everything is selected
                    selected && !before && !after -> Mode.CLEAR
                    else -> Mode.SELECT_ALL
                }
            }
        }
        show(next)
    }

    private fun show(next: Mode) {
        if (next == mode) return
        mode = next
        if (next == Mode.HIDDEN) {
            view.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                if (mode == Mode.HIDDEN) view.visibility = View.INVISIBLE
            }.start()
            return
        }
        view.setImageResource(next.icon)
        view.setColorFilter(if (next == Mode.SELECT_ALL) style.secondaryText else style.accentText)
        view.contentDescription = next.description
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
        }
        view.animate().alpha(1f).setDuration(FADE_MS).start()
    }

    private fun onTap() {
        val ic = connection() ?: return
        when (mode) {
            Mode.SELECT_ALL -> {
                ai.unicorn.Unicorn.finishComposing()
                if (!ic.performContextMenuAction(android.R.id.selectAll)) {
                    EditorText.readAll(ic)?.let { ic.setSelection(0, it.length) }
                }
            }
            Mode.CLEAR -> {
                val text = EditorText.readAll(ic) ?: return
                ai.unicorn.Unicorn.finishComposing()
                EditorText.replaceAll(ic, "")
                rememberForUndo(text)
            }
            Mode.UNDO -> {
                val text = undoText ?: return
                undoText = null
                handler.removeCallbacks(expireUndo)
                ai.unicorn.Unicorn.finishComposing()
                EditorText.replaceAll(ic, text)
                refresh()
            }
            Mode.HIDDEN -> Unit
        }
    }

    private companion object {
        const val UNDO_MS = 6_000L
        const val FADE_MS = 150L
    }
}
