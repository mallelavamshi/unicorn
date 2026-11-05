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
import ai.unicorn.ai.AiTask
import ai.unicorn.memory.CaptureSource
import ai.unicorn.memory.MemoryRepository
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.android.inputmethod.latin.R

/**
 * The row above the suggestions. It shows the AI tools (or a matching earlier prompt), turns into a live transcript
 * while voice typing, and into Ask Unicorn's prompt bar, which the keys then type into.
 */
class UnicornBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val style = KeyboardStyle(context)

    // Tools ---------------------------------------------------------------------------------------------------------
    private val toolsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val lock = TextView(context).apply {
        text = "🔒"
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(style.dp(8f), 0, style.dp(10f), 0)
        contentDescription = "Prompts in this app are saved to Unicorn"
        isFocusable = false
        setOnClickListener { openApp("privacy") }
    }
    private var recallShown: MemoryRepository.RecallHit? = null
    private var dismissedRecallFor: String? = null

    // Voice ---------------------------------------------------------------------------------------------------------
    private val pulse = PulseView(context, style.accent)
    private val voiceText = style.label("", sizeSp = 15f).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
    }
    private val voiceRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        setPadding(style.dp(8f), 0, style.dp(8f), 0)
    }
    private var voiceMessageShown = false

    // Ask Unicorn's prompt bar --------------------------------------------------------------------------------------
    private val promptField = TextView(context).apply {
        textSize = 15f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
        gravity = Gravity.CENTER_VERTICAL
        background = style.rounded(style.surface, radiusDp = 18f, strokeColor = ColorUtils.setAlphaComponent(style.accent, 90))
        setPadding(style.dp(14f), 0, style.dp(14f), 0)
        contentDescription = "Ask Unicorn"
    }
    private val promptRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        setPadding(style.dp(4f), style.dp(4f), style.dp(6f), style.dp(4f))
    }
    private var promptText = ""
    private var caretOn = true
    private val blink = object : Runnable {
        override fun run() {
            caretOn = !caretOn
            renderPrompt()
            postDelayed(this, 530)
        }
    }

    var onToolSelected: (AiTask) -> Unit = {}

    init {
        // If the bar can't be built, the keys must still show: it hides instead
        try {
            build()
        } catch (e: Exception) {
            Unicorn.noteFailure("UnicornBar", e)
            removeAllViews()
            visibility = GONE
        }
    }

    private fun build() {
        setBackgroundColor(style.background)
        val logo = ImageView(context).apply {
            setImageResource(R.mipmap.ic_unicorn_foreground)
            // The purple line art disappears on dark and colored themes
            if (style.isDark) setColorFilter(style.text)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Open Unicorn"
            isFocusable = false
            setOnClickListener { openApp(null) }
        }
        toolsRow.addView(logo, LinearLayout.LayoutParams(style.dp(44f), style.dp(44f)))
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(content)
        }
        toolsRow.addView(scroll, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        toolsRow.addView(lock, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        // More: size and position of the keys, one-handed
        if (KeyArea.ENABLED) toolsRow.addView(iconButton(R.drawable.ic_unicorn_more, "More options", style.secondaryText, null) {
            if (menuShown) showTools() else showMenu()
        }, LinearLayout.LayoutParams(style.dp(40f), LayoutParams.MATCH_PARENT))
        addView(toolsRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        voiceRow.addView(pulse, LinearLayout.LayoutParams(style.dp(32f), style.dp(32f)).apply { marginEnd = style.dp(8f) })
        voiceRow.addView(voiceText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        voiceRow.addView(style.pill("Done") { Unicorn.toggleVoiceFromBar() }, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = style.dp(8f) })
        addView(voiceRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val close = style.label("✕", sizeSp = 17f, color = style.secondaryText).apply {
            gravity = Gravity.CENTER
            contentDescription = "Close Ask Unicorn"
            setOnClickListener { Unicorn.closeAsk() }
        }
        promptRow.addView(close, LinearLayout.LayoutParams(style.dp(36f), LayoutParams.MATCH_PARENT))
        promptRow.addView(promptField, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        promptRow.addView(iconButton(R.drawable.ic_unicorn_mic, "Speak your request", style.secondaryText, null) {
            Unicorn.toggleVoiceFromBar()
        }, LinearLayout.LayoutParams(style.dp(36f), LayoutParams.MATCH_PARENT).apply { marginStart = style.dp(2f) })
        promptRow.addView(iconButton(R.drawable.ic_unicorn_send, "Ask", android.graphics.Color.WHITE, style.accent) {
            Unicorn.submitAsk()
        }, LinearLayout.LayoutParams(style.dp(34f), style.dp(34f)).apply { marginStart = style.dp(2f) })
        addView(promptRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        showTools()
        update(null, emptyList(), "")
        Unicorn.attachBar(this)
    }

    private fun iconButton(icon: Int, description: String, tint: Int, circle: Int?, onClick: () -> Unit) =
        ImageView(context).apply {
            setImageResource(icon)
            setColorFilter(tint)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            isFocusable = false
            if (circle != null) background = style.rounded(circle, radiusDp = 17f)
            setOnClickListener { onClick() }
        }

    fun update(source: CaptureSource?, recall: List<MemoryRepository.RecallHit>, fieldText: String) {
        lock.visibility = if (source != null) VISIBLE else GONE
        if (fieldText.isBlank()) dismissedRecallFor = null
        val hit = recall.firstOrNull()?.takeIf { dismissedRecallFor == null }
        if (hit?.messageId == recallShown?.messageId) {
            // Same recall as before; the tools only change when the field turns private or the AI rests
            if (hit == null && toolsState() != shownToolsState) showTools()
            return
        }
        recallShown = hit
        if (hit != null) showRecall(hit, fieldText) else showTools()
    }

    private var shownToolsState = -1

    private fun toolsState(): Int = when {
        Unicorn.isSensitiveField() -> 0
        !Unicorn.get(context).prefs.aiAvailable -> 1
        else -> 2
    }

    private var menuShown = false

    private fun showMenu() {
        menuShown = true
        recallShown = null
        content.removeAllViews()
        content.addView(style.pill("↔  Size and position") {
            showTools()
            KeyArea.openEditor()
        }, pillParams())
        content.addView(style.pill(if (KeyArea.size.isFullWidth) "One-handed" else "Full width") {
            showTools()
            KeyArea.toggleOneHanded(context)
        }, pillParams())
    }

    private fun showTools() {
        menuShown = false
        shownToolsState = toolsState()
        content.removeAllViews()
        if (Unicorn.isSensitiveField()) {
            content.addView(style.label("🔒  Private field. Unicorn's tools are off here", 13f, style.secondaryText))
            return
        }
        // Off by the user, or resting until the free daily AI resets
        if (!Unicorn.get(context).prefs.aiAvailable) return
        content.addView(style.pill("✨ Ask Unicorn") { Unicorn.openAsk() }, pillParams())
        val tools = listOf(
            "✓ Grammar" to AiTask.GRAMMAR,
            "🎭 Tone" to AiTask.TONE,
            "🌐 Translate" to AiTask.TRANSLATE,
        )
        for ((label, task) in tools) {
            content.addView(style.pill(label) { onToolSelected(task) }, pillParams())
        }
    }

    private fun showRecall(hit: MemoryRepository.RecallHit, fieldText: String) {
        content.removeAllViews()
        val chip = TextView(context).apply {
            text = "🕘  ${hit.text}"
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = style.dp(260f)
            setTextColor(style.accentText)
            background = style.rounded(style.accentSoft)
            setPadding(style.dp(12f), style.dp(7f), style.dp(12f), style.dp(7f))
            contentDescription = "Use your earlier prompt: ${hit.text}"
            setOnClickListener {
                val ic = Unicorn.currentInputConnection() ?: return@setOnClickListener
                val target = EditorText.read(ic) ?: return@setOnClickListener
                EditorText.readAll(ic)?.let { Unicorn.rememberForUndo(it) }
                EditorText.replace(ic, target.copy(isSelection = false), hit.text)
            }
        }
        content.addView(chip, pillParams())
        content.addView(style.pill("✕") {
            dismissedRecallFor = fieldText
            recallShown = null
            showTools()
        }, pillParams())
    }

    private fun pillParams() = LinearLayout.LayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = style.dp(6f) }

    // Voice -----------------------------------------------------------------------------------------------------------

    fun showVoice(state: VoiceTyping.State) {
        when (state) {
            VoiceTyping.State.Idle -> if (!voiceMessageShown) hideVoice()
            VoiceTyping.State.Starting -> showVoiceRow("Starting…", 0f)
            is VoiceTyping.State.Listening ->
                showVoiceRow(state.partial.ifEmpty { "Listening… speak now" }, state.level)
            is VoiceTyping.State.Failed -> {
                showVoiceRow(state.message, 0f)
                pulse.listening = false
                voiceMessageShown = true
                removeCallbacks(hideVoiceMessage)
                postDelayed(hideVoiceMessage, 3500)
            }
        }
    }

    private val hideVoiceMessage = Runnable {
        voiceMessageShown = false
        hideVoice()
    }

    private fun showVoiceRow(text: String, level: Float) {
        removeCallbacks(hideVoiceMessage)
        voiceMessageShown = false
        voiceText.text = text
        voiceText.setTextColor(if (text.endsWith("…") || text.endsWith("now")) style.secondaryText else style.text)
        pulse.listening = true
        pulse.level = level
        voiceRow.visibility = VISIBLE
        toolsRow.visibility = INVISIBLE
        promptRow.visibility = INVISIBLE
    }

    private fun hideVoice() {
        voiceRow.visibility = GONE
        pulse.listening = false
        val askOpen = promptRow.tag == true
        toolsRow.visibility = if (askOpen) INVISIBLE else VISIBLE
        promptRow.visibility = if (askOpen) VISIBLE else GONE
    }

    // Ask Unicorn -----------------------------------------------------------------------------------------------------

    fun showAsk(session: AskSession) {
        val chips = rootView.findViewById<PromptChips>(R.id.unicorn_prompt_chips)
        chips?.render(session)
        promptRow.tag = session.isOpen
        if (!session.isOpen) {
            removeCallbacks(blink)
            closePanel()
            if (voiceRow.visibility != VISIBLE) {
                promptRow.visibility = GONE
                toolsRow.visibility = VISIBLE
            }
            return
        }
        promptText = session.text
        if (voiceRow.visibility != VISIBLE) {
            promptRow.visibility = VISIBLE
            toolsRow.visibility = INVISIBLE
        }
        caretOn = true
        renderPrompt()
        removeCallbacks(blink)
        postDelayed(blink, 530)
    }

    private fun renderPrompt() {
        val text = SpannableStringBuilder(promptText)
        val caretColor = if (caretOn) style.accent else android.graphics.Color.TRANSPARENT
        val caretStart = text.length
        text.append("▏")
        text.setSpan(ForegroundColorSpan(caretColor), caretStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (promptText.isEmpty()) {
            val hintStart = text.length
            text.append("Ask Unicorn to write anything…")
            text.setSpan(ForegroundColorSpan(style.secondaryText), hintStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        promptField.setTextColor(style.text)
        promptField.text = text
    }

    /** Redraws the tools when no earlier prompt is offered, so they return once the daily AI resets. */
    fun refreshTools() {
        if (recallShown == null) showTools()
    }

    /** The free daily AI ran out: close whatever AI view is open and let the tools step aside, without a message. */
    fun restAi() {
        Unicorn.closeAsk()
        closePanel()
        recallShown = null
        showTools()
    }

    fun showAskResults(request: AskSession.Request) {
        val panel = rootView.findViewById<AiPanelView>(R.id.unicorn_ai_panel) ?: return
        panel.visibility = VISIBLE
        panel.openAsk(request)
    }

    private fun openApp(destination: String?) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (destination != null) intent.putExtra("destination", destination)
        Unicorn.hideKeyboard()
        context.startActivity(intent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The engine hides the suggestions in password and similar fields, which left a see-through band between
        // this bar and the keys: paint the row behind them in the keyboard's color
        (rootView.findViewById<View>(R.id.suggestion_strip_view)?.parent as? View)?.setBackgroundColor(style.background)
        val panel = rootView.findViewById<AiPanelView>(R.id.unicorn_ai_panel) ?: return
        onToolSelected = { task ->
            panel.visibility = VISIBLE
            panel.open(task)
        }
        panel.onClose = { panel.visibility = GONE }
    }

    fun closePanel() {
        rootView.findViewById<AiPanelView>(R.id.unicorn_ai_panel)?.takeIf { it.visibility == VISIBLE }?.close()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(blink)
        removeCallbacks(hideVoiceMessage)
        Unicorn.detachBar(this)
    }

    /** A dot that breathes with the speaker's voice. */
    private class PulseView(context: Context, private val color: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shown = 0f
        var level = 0f
            set(value) {
                field = value
                invalidate()
            }
        var listening = false
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val base = minOf(width, height) * 0.22f
            // Ease towards the reported level so the halo moves smoothly
            shown += (level - shown) * 0.35f
            if (listening) {
                paint.color = ColorUtils.setAlphaComponent(color, 60)
                canvas.drawCircle(cx, cy, base + (minOf(width, height) / 2f - base) * shown, paint)
            }
            paint.color = if (listening) color else ColorUtils.setAlphaComponent(color, 120)
            canvas.drawCircle(cx, cy, base, paint)
            if (listening && kotlin.math.abs(level - shown) > 0.01f) postInvalidateOnAnimation()
        }
    }
}
