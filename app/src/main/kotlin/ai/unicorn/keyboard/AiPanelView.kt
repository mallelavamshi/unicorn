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
import ai.unicorn.ai.AiClient
import ai.unicorn.ai.AiTask
import ai.unicorn.ai.AiTone
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Covers the keys while an AI tool is open: tool title, options (tones or languages), and result cards. Tapping a
 * result replaces the text it was made from.
 */
class AiPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    companion object {
        val Languages = listOf(
            "Spanish", "English", "French", "Chinese", "Vietnamese", "Tagalog", "Korean", "Arabic", "Portuguese",
            "German", "Japanese", "Russian", "Italian", "Hindi", "Telugu", "Tamil", "Bengali", "Urdu", "Punjabi",
            "Marathi", "Gujarati", "Kannada", "Malayalam",
        )
    }

    private val style = KeyboardStyle(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val title = style.label("", sizeSp = 16f, bold = true)
    private val options = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val results = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val status = style.label("", sizeSp = 14f, color = style.secondaryText).apply {
        gravity = Gravity.CENTER
        setPadding(style.dp(16f), style.dp(20f), style.dp(16f), style.dp(20f))
    }
    private val progress = ProgressBar(context).apply { isIndeterminate = true; visibility = GONE }

    private var task = AiTask.GRAMMAR
    private var target: EditorText.Target? = null
    /** Puts the tapped answer into the app. */
    private var onPick: (String) -> Unit = {}

    /** Called when the panel closes, so the keys come back. */
    var onClose: () -> Unit = {}

    init {
        setBackgroundColor(style.background)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(style.dp(4f), style.dp(4f), style.dp(12f), style.dp(4f))
        }
        val back = style.label("←", sizeSp = 22f).apply {
            setPadding(style.dp(12f), style.dp(4f), style.dp(12f), style.dp(4f))
            contentDescription = "Back to keys"
            setOnClickListener { close() }
        }
        header.addView(back)
        header.addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(progress, LinearLayout.LayoutParams(style.dp(22f), style.dp(22f)))
        column.addView(header)
        val optionScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(style.dp(10f), 0, style.dp(10f), style.dp(6f))
            clipToPadding = false
            addView(options)
        }
        column.addView(optionScroll)
        val resultScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(status)
                addView(results)
            })
        }
        column.addView(resultScroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = true // swallow touches so keys underneath never fire
    }

    fun open(task: AiTask) {
        this.task = task
        title.text = task.title
        options.removeAllViews()
        results.removeAllViews()
        target = EditorText.read(Unicorn.currentInputConnection())
        onPick = { text ->
            val ic = Unicorn.currentInputConnection()
            target?.let { source ->
                Unicorn.finishComposing()
                EditorText.readAll(ic)?.let { Unicorn.rememberForUndo(it) }
                EditorText.replace(ic, source, text)
            }
            close()
        }
        val text = target?.text.orEmpty()
        if (text.isBlank()) {
            showStatus(
                if (task == AiTask.COMPOSE) {
                    "Type what you want written, like \"reply politely that I can't make it\", then tap Ask Unicorn."
                } else {
                    "Type or select some text first, then tap ${task.title}."
                }
            )
            return
        }
        when (task) {
            AiTask.TONE -> {
                renderOptions(AiTone.entries.map { "${it.emoji} ${it.label}" }, selected = 0) { i ->
                    request(tone = AiTone.entries[i])
                }
                request(tone = AiTone.REWRITE)
            }
            AiTask.TRANSLATE -> {
                val prefs = Unicorn.get(context).prefs
                val start = Languages.indexOf(prefs.translateTarget).coerceAtLeast(0)
                renderOptions(Languages, selected = start) { i ->
                    prefs.translateTarget = Languages[i]
                    request(language = Languages[i])
                }
                request(language = Languages[start])
            }
            else -> request()
        }
    }

    private fun renderOptions(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        options.removeAllViews()
        labels.forEachIndexed { i, label ->
            options.addView(style.pill(label, selected = i == selected) {
                renderOptions(labels, i, onPick)
                onPick(i)
            }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = style.dp(6f)
            })
        }
    }

    private fun request(tone: AiTone? = null, language: String? = null) {
        val source = target ?: return
        job?.cancel()
        results.removeAllViews()
        showStatus("")
        progress.visibility = VISIBLE
        job = scope.launch {
            val outcome = runCatching { Unicorn.get(context).ai.run(task, source.text, tone, language) }
            progress.visibility = GONE
            if (outcome.exceptionOrNull() is AiClient.AiPaused) return@launch Unicorn.restAi()
            outcome.onSuccess { list ->
                if (list.isEmpty()) {
                    showStatus("No suggestions this time. Try again.")
                } else if (task == AiTask.GRAMMAR && list.first() == source.text.trim()) {
                    showStatus("Looks good. No mistakes found ✓")
                } else {
                    showStatus("")
                    list.forEach { addResult(it) }
                }
            }.onFailure { showStatus(it.message ?: "Something went wrong. Try again.") }
        }
    }

    private fun addResult(text: String) {
        val card = TextView(context).apply {
            this.text = text
            textSize = 15f
            setTextColor(style.text)
            background = style.rounded(style.surface, radiusDp = 14f)
            setPadding(style.dp(14f), style.dp(12f), style.dp(14f), style.dp(12f))
            setOnClickListener { onPick(text) }
        }
        results.addView(card, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(style.dp(10f), 0, style.dp(10f), style.dp(8f))
        })
    }

    /**
     * Answers for Ask Unicorn's prompt bar. Refining ("Shorter", "Formal") reworks the first answer, but the one the
     * user taps still replaces what [request] would have replaced in the app.
     */
    fun openAsk(request: AskSession.Request) {
        title.text = if (request.command == AskCommand.FREE) "✨ Ask Unicorn" else request.command.label
        options.removeAllViews()
        results.removeAllViews()
        onPick = { text -> Unicorn.insertAnswer(request, text) }
        when {
            request.command.usesCopied && request.thread == null && request.instruction.isBlank() -> {
                val verb = if (request.command == AskCommand.SUMMARIZE) "sum up" else "reply to"
                if (RecentMessages.isAllowed(context)) {
                    showStatus("Nothing to $verb yet. Copy the message or email, then try again.")
                } else {
                    showStatus(
                        "Unicorn can't see this chat. Let it read your message notifications to $verb the latest " +
                            "conversation (kept on your phone, in memory only), or copy the message first."
                    )
                    options.addView(style.pill("Allow message notifications", selected = true) {
                        Unicorn.openNotificationAccess()
                    })
                }
            }
            request.command.usesDraft && request.draft == null ->
                showStatus("Type something in the app first, then ask Unicorn to ${request.command.hint.lowercase()}.")
            else -> ask(request, request)
        }
    }

    private fun ask(original: AskSession.Request, current: AskSession.Request) {
        job?.cancel()
        results.removeAllViews()
        options.removeAllViews()
        showStatus(
            when {
                current.thread != null -> "Replying to ${current.threadFrom ?: "the conversation"}…"
                current.draft != null && current.command.usesDraft -> "Working on what you typed…"
                else -> "Writing…"
            }
        )
        progress.visibility = VISIBLE
        job = scope.launch {
            val outcome = runCatching {
                Unicorn.get(context).ai.write(
                    current.command, current.instruction, current.draft?.text, current.thread, current.writeContext,
                )
            }
            progress.visibility = GONE
            if (outcome.exceptionOrNull() is AiClient.AiPaused) return@launch Unicorn.restAi()
            outcome.onSuccess { list ->
                if (list.isEmpty()) {
                    showStatus("No answer this time. Try again.")
                    return@onSuccess
                }
                showStatus(current.threadFrom?.let { "Replying to $it" }.orEmpty())
                list.forEach { addResult(it) }
                refineOptions(original, current, list.first())
            }.onFailure { showStatus(it.message ?: "Something went wrong. Try again.") }
        }
    }

    private fun refineOptions(original: AskSession.Request, current: AskSession.Request, first: String) {
        options.removeAllViews()
        val rework = { command: AskCommand ->
            ask(original, current.copy(command = command, instruction = "", draft = EditorText.Target(first, false), thread = null))
        }
        val refinements = listOf<Pair<String, () -> Unit>>(
            "↻ Try again" to { ask(original, current) },
            "✂️ Shorter" to { rework(AskCommand.SHORTER) },
            "💼 Formal" to { rework(AskCommand.FORMAL) },
            "😊 Friendlier" to { rework(AskCommand.CASUAL) },
            "➕ Longer" to { rework(AskCommand.LONGER) },
        )
        for ((label, action) in refinements) {
            options.addView(style.pill(label) { action() }, LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = style.dp(6f) })
        }
    }

    private fun showStatus(message: String) {
        status.text = message
        status.visibility = if (message.isEmpty()) GONE else VISIBLE
    }

    fun close() {
        job?.cancel()
        progress.visibility = GONE
        onClose()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job?.cancel()
    }
}
