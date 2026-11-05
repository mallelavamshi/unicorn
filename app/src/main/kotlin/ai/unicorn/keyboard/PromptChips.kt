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
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * Covers the suggestions while Ask Unicorn's prompt bar is open: one-tap starters that fit the app and what is
 * already typed or copied, and the list of slash commands once the prompt starts with "/".
 */
class PromptChips @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val style = KeyboardStyle(context)
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(style.dp(8f), 0, style.dp(8f), 0)
    }

    init {
        setBackgroundColor(style.background)
        visibility = GONE
        isClickable = true // keep the suggestions underneath from taking taps
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun render(session: AskSession) {
        if (!session.isOpen) {
            visibility = GONE
            return
        }
        visibility = VISIBLE
        row.removeAllViews()
        val text = session.text
        when {
            text.startsWith("/") && !text.contains(' ') -> commands(text.drop(1))
            text.isEmpty() -> starters(session)
            else -> row.addView(style.label("Tap ↑ or return to ask   ·   / for commands", 13f, style.secondaryText))
        }
    }

    private fun commands(prefix: String) {
        val matches = AskCommand.listed.filter { it.id.startsWith(prefix, true) }
            .ifEmpty { AskCommand.listed.filter { c -> c.aliases.any { it.startsWith(prefix, true) } } }
        if (matches.isEmpty()) {
            row.addView(style.label("No command \"/$prefix\". Keep typing to ask anything.", 13f, style.secondaryText))
            return
        }
        for (command in matches) {
            chip("/${command.id}  ${command.label.substringAfter(' ')}") {
                // Commands that only rework the draft or the copied text can run straight away
                if (command.needsNoInstruction) Unicorn.submitAsk("/${command.id}")
                else Unicorn.setAskPrompt("/${command.id} ")
            }
        }
    }

    private fun starters(session: AskSession) {
        val copied = CopiedText.hasFresh(context)
        val app = session.writeContext.app
        // The conversations the user is most likely answering, from this app's notifications
        val conversations = if (Unicorn.isPhoneLocked(context)) emptyList() else RecentMessages.recent(session.packageName, 3)
        for (conversation in conversations) {
            chip("↩️ Reply to ${conversation.title.take(18)}") { Unicorn.submitAsk("/reply", conversation) }
        }
        if (conversations.isEmpty() && !copied && RecentMessages.isMessagingApp(session.packageName) &&
            !session.writeContext.isSubject) {
            chip("↩️ Reply") { Unicorn.submitAsk("/reply") }
        }
        if (copied) {
            chip(if (app == AppKind.EMAIL) "↩️ Reply to copied email" else "↩️ Reply to copied") {
                Unicorn.submitAsk("/reply")
            }
        }
        if (session.hasDraft) {
            for (command in listOf(AskCommand.IMPROVE, AskCommand.SHORTER, AskCommand.FORMAL, AskCommand.CASUAL,
                AskCommand.FIX, AskCommand.CONTINUE)) {
                chip(command.label) { Unicorn.submitAsk("/${command.id}") }
            }
        } else if (!session.writeContext.isSubject) {
            val ideas = when (app) {
                AppKind.EMAIL -> listOf(
                    "📅 Ask for a meeting" to "/email Ask for a short meeting this week",
                    "🔁 Follow up" to "/email A polite follow-up on my last email",
                    "🙏 Thank you" to "/email Thank them for their help",
                    "🙅 Decline politely" to "/email Politely decline",
                )
                AppKind.CHAT -> listOf(
                    "⏰ Running late" to "Tell them I'm running about 10 minutes late, sorry",
                    "🎂 Birthday wish" to "A warm, personal birthday wish",
                    "🍽️ Make plans" to "Ask if they're free for dinner this weekend",
                    "🙏 Say thanks" to "Say thanks, warmly",
                )
                AppKind.SOCIAL -> listOf(
                    "✍️ Caption" to "A catchy caption for my post",
                    "👏 Congratulate" to "Congratulate them warmly",
                )
                AppKind.OTHER -> emptyList()
            }
            for ((label, prompt) in ideas) chip(label) { Unicorn.submitAsk(prompt) }
        }
        if (copied) chip("📝 Summarize copied") { Unicorn.submitAsk("/summarize") }
        chip("/  Commands") { Unicorn.setAskPrompt("/") }
    }

    private fun chip(label: String, onClick: () -> Unit) {
        row.addView(style.pill(label, onClick = onClick), LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = style.dp(6f) })
    }
}
