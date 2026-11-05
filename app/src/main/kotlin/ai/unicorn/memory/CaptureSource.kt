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

/**
 * The complete, hard-coded list of apps whose prompts may ever be saved. Text typed into any app that is not listed
 * here is never read for saving, never stored and never transmitted.
 *
 * @property searchBarOnly If true, only the address/search bar (URI input variation) is used and text that looks
 *  like a web address is skipped. Used for browsers, where every other field (forms, banking, logins) stays untouched.
 */
enum class CaptureSource(
    val id: String,
    val label: String,
    val packageNames: Set<String>,
    val searchBarOnly: Boolean = false,
    val enabledByDefault: Boolean = true,
) {
    // Only these five AI makers, so people know exactly where saving can happen (user decision, 0.4.2)
    CHATGPT("chatgpt", "ChatGPT", setOf("com.openai.chatgpt")),
    CLAUDE("claude", "Claude", setOf("com.anthropic.claude")),
    PERPLEXITY("perplexity", "Perplexity", setOf("ai.perplexity.app.android")),
    GROK("grok", "Grok", setOf("ai.x.grok")),
    GOOGLE_SEARCH("google_search", "Google Search & AI Mode", setOf("com.google.android.googlequicksearchbox")),
    GEMINI("gemini", "Google Gemini", setOf("com.google.android.apps.bard"));

    companion object {
        private val byPackageName = entries.flatMap { source -> source.packageNames.map { it to source } }.toMap()
        private val byId = entries.associateBy { it.id }

        fun fromPackageName(packageName: String?): CaptureSource? = packageName?.let { byPackageName[it] }

        fun fromId(id: String): CaptureSource? = byId[id]
    }
}
