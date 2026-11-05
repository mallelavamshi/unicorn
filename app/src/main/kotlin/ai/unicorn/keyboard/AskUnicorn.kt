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

import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo

/** The kind of app the text goes into; the AI writes an email differently from a chat message. */
enum class AppKind(val id: String) { EMAIL("email"), CHAT("chat"), SOCIAL("social"), OTHER("other") }

data class WriteContext(val app: AppKind, val isSubject: Boolean) {
    companion object {
        private val EMAIL_APPS = setOf(
            "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
            "com.yahoo.mobile.client.android.mail", "ch.protonmail.android", "me.proton.android.mail",
            "me.bluemail.mail", "com.readdle.spark", "com.zoho.mail", "com.fsck.k9", "net.thunderbird.android",
        )
        private val CHAT_APPS = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.facebook.orca",
            "com.discord", "com.Slack", "com.microsoft.teams", "jp.naver.line.android", "com.viber.voip",
            "com.snapchat.android", "com.google.android.apps.dynamite", "in.mohalla.video",
        )
        private val SOCIAL_APPS = setOf(
            "com.twitter.android", "com.instagram.android", "com.linkedin.android", "com.facebook.katana",
            "com.reddit.frontpage", "com.zhiliaoapp.musically", "com.instagram.barcelona", "com.google.android.youtube",
        )

        fun of(editorInfo: EditorInfo?): WriteContext {
            val pkg = editorInfo?.packageName.orEmpty()
            val variation = (editorInfo?.inputType ?: 0) and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) return WriteContext(AppKind.EMAIL, true)
            val app = when {
                pkg in EMAIL_APPS -> AppKind.EMAIL
                pkg in CHAT_APPS || variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> AppKind.CHAT
                pkg in SOCIAL_APPS -> AppKind.SOCIAL
                else -> AppKind.OTHER
            }
            return WriteContext(app, false)
        }
    }
}

/**
 * Prompt bar commands. [usesDraft] commands rework what the user typed in the app; [usesCopied] ones read the
 * message the user copied, which is how Unicorn sees a thread: keys can't read the rest of another app's screen.
 */
enum class AskCommand(
    val id: String,
    val label: String,
    val hint: String,
    val usesDraft: Boolean = false,
    val usesCopied: Boolean = false,
    val aliases: List<String> = emptyList(),
) {
    REPLY("reply", "↩️ Reply", "Write a natural reply to the latest message", usesCopied = true, aliases = listOf("thread", "read-thread", "r")),
    EMAIL("email", "✉️ Email", "Write an email", aliases = listOf("mail")),
    IMPROVE("improve", "✨ Improve", "Polish what you typed", usesDraft = true, aliases = listOf("polish", "better")),
    SHORTER("shorter", "✂️ Shorter", "Make what you typed shorter", usesDraft = true, aliases = listOf("short")),
    LONGER("longer", "➕ Longer", "Expand what you typed", usesDraft = true, aliases = listOf("expand")),
    FORMAL("formal", "💼 Formal", "Make it professional", usesDraft = true, aliases = listOf("professional")),
    CASUAL("casual", "😊 Casual", "Make it relaxed and friendly", usesDraft = true, aliases = listOf("friendly")),
    FIX("fix", "✓ Fix", "Fix spelling and grammar", usesDraft = true, aliases = listOf("grammar", "spell")),
    CONTINUE("continue", "➡️ Continue", "Keep writing from where you stopped", usesDraft = true, aliases = listOf("more")),
    SUMMARIZE("summarize", "📝 Summarize", "Sum up the conversation", usesCopied = true, aliases = listOf("summary", "tldr")),
    TRANSLATE("translate", "🌐 Translate", "Translate what you typed, like /translate Spanish", usesDraft = true, aliases = listOf("tr")),
    FREE("free", "✨ Ask", "Ask for anything");

    /** Runs straight from its chip: it works on what is typed or copied and needs no further words. */
    val needsNoInstruction get() = this != EMAIL && this != TRANSLATE && this != FREE

    companion object {
        val listed = entries.filter { it != FREE }

        fun find(word: String): AskCommand? = word.lowercase().let { w ->
            entries.firstOrNull { it.id == w || w in it.aliases }
        }
    }
}

/** A typed prompt split into its command ("/reply") and the rest of the instruction. */
data class ParsedPrompt(val command: AskCommand, val instruction: String) {
    companion object {
        fun parse(prompt: String): ParsedPrompt {
            val text = prompt.trim()
            if (text.startsWith("/")) {
                val word = text.drop(1).substringBefore(' ')
                AskCommand.find(word)?.let { return ParsedPrompt(it, text.drop(1 + word.length).trim()) }
            }
            return ParsedPrompt(AskCommand.FREE, text)
        }
    }
}

/** What the user copied, read only when they ask for it (Android shows a notice on every read). */
object CopiedText {
    private const val FRESH_MS = 15 * 60 * 1000L
    private const val MAX_CHARS = 8000

    /** True if something was copied recently; checking this does not read the text. */
    fun hasFresh(context: Context): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        val description = runCatching { clipboard.primaryClipDescription }.getOrNull() ?: return false
        if (!description.hasMimeType("text/*")) return false
        val age = System.currentTimeMillis() - description.timestamp
        return description.timestamp > 0 && age in 0..FRESH_MS
    }

    /** The copied text if it was copied recently; older clipboard content is never used on its own. */
    fun readFresh(context: Context): String? = if (hasFresh(context)) read(context) else null

    fun read(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_CHARS)
    }
}
