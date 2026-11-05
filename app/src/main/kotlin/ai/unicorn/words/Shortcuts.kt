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

package ai.unicorn.words

import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.SuggestedWords
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import java.util.Locale

/**
 * Text shortcuts in the suggestions: when the word being typed is a shortcut, its expansion leads the suggestions and
 * replaces the word on space, like text replacement on other phones. Backspace right after brings the typed word back.
 */
object Shortcuts {
    @Volatile
    private var map: Map<String, String> = emptyMap()

    fun set(shortcuts: Map<String, String>) {
        map = shortcuts
    }

    /** Suggested presets for the words page. */
    val Presets = listOf(
        "omw" to "On my way!",
        "brb" to "Be right back",
        "ty" to "Thank you so much!",
        "tmrw" to "tomorrow",
        "lmk" to "Let me know",
        "np" to "No problem",
    )

    /** Never fails: on any error the engine's own suggestions are shown unchanged. */
    @JvmStatic
    fun apply(words: SuggestedWords): SuggestedWords = runCatching { withShortcut(words) }.getOrDefault(words)

    private fun withShortcut(words: SuggestedWords): SuggestedWords {
        val shortcuts = map
        // Typing on the keys gives INPUT_STYLE_TYPING, a hardware keyboard INPUT_STYLE_NONE; never predictions
        val typing = words.mInputStyle == SuggestedWords.INPUT_STYLE_TYPING || words.mInputStyle == SuggestedWords.INPUT_STYLE_NONE
        if (shortcuts.isEmpty() || words.isEmpty || !typing) return words
        val typed = words.mTypedWordInfo?.mWord ?: return words
        val expansion = shortcuts[typed.lowercase(Locale.ROOT)] ?: return words
        val list = ArrayList<SuggestedWordInfo>(words.size() + 1)
        list.add(words.getInfo(SuggestedWords.INDEX_OF_TYPED_WORD))
        list.add(
            SuggestedWordInfo(
                expansion, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_SHORTCUT,
                Dictionary.DICTIONARY_USER_SHORTCUT, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE,
            )
        )
        for (i in 1 until words.size()) {
            val info = words.getInfo(i)
            if (info.mWord != expansion) list.add(info)
        }
        return SuggestedWords(
            list, words.mRawSuggestions, words.mTypedWordInfo, words.mTypedWordValid,
            true /* willAutoCorrect: space swaps in the expansion */, words.mIsObsoleteSuggestions,
            words.mInputStyle, words.mSequenceNumber,
        )
    }
}
