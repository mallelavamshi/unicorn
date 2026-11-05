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

package ai.unicorn.memory.vector

import java.text.Normalizer

/**
 * Pure Kotlin port of the Hugging Face `BertNormalizer` (clean text, CJK spacing, lowercase, strip accents),
 * `BertPreTokenizer` and `WordPiece` model, matching the tokenizer.json shipped with Model2Vec models.
 *
 * Special tokens are never added and unknown words are dropped, which mirrors how Model2Vec encodes text.
 */
class WordPieceTokenizer(
    vocab: List<String>,
    private val unkToken: String = "[UNK]",
    private val continuingSubwordPrefix: String = "##",
    private val maxInputCharsPerWord: Int = 100,
) {
    private val tokenToId = HashMap<String, Int>(vocab.size * 2).apply {
        vocab.forEachIndexed { id, token -> put(token, id) }
    }
    private val unkId = tokenToId[unkToken] ?: -1

    val vocabSize: Int = vocab.size

    /** Returns the token ids of [text], without special tokens and without the unknown token. */
    fun encode(text: String, maxTokens: Int = 512): IntArray {
        val out = IntArrayBuilder()
        for (word in preTokenize(normalize(text))) {
            wordPiece(word, out)
            if (out.size >= maxTokens) break
        }
        return out.toArray(maxTokens)
    }

    internal fun normalize(text: String): String {
        val sb = StringBuilder(text.length + 8)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0 || cp == 0xFFFD || isControl(cp)) continue
            when {
                isWhitespace(cp) -> sb.append(' ')
                isChineseChar(cp) -> sb.append(' ').appendCodePoint(cp).append(' ')
                else -> sb.appendCodePoint(cp)
            }
        }
        val lowered = sb.toString().lowercase()
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        val stripped = StringBuilder(decomposed.length)
        var j = 0
        while (j < decomposed.length) {
            val cp = decomposed.codePointAt(j)
            j += Character.charCount(cp)
            if (Character.getType(cp) != Character.NON_SPACING_MARK.toInt()) stripped.appendCodePoint(cp)
        }
        return stripped.toString()
    }

    internal fun preTokenize(text: String): List<String> {
        val words = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                isWhitespace(cp) -> {
                    if (current.isNotEmpty()) { words.add(current.toString()); current.setLength(0) }
                }
                isPunctuation(cp) -> {
                    if (current.isNotEmpty()) { words.add(current.toString()); current.setLength(0) }
                    words.add(String(Character.toChars(cp)))
                }
                else -> current.appendCodePoint(cp)
            }
        }
        if (current.isNotEmpty()) words.add(current.toString())
        return words
    }

    private fun wordPiece(word: String, out: IntArrayBuilder) {
        if (word.codePointCount(0, word.length) > maxInputCharsPerWord) return // would be [UNK], which is dropped
        val start = out.size
        var begin = 0
        while (begin < word.length) {
            var end = word.length
            var matchId = -1
            while (begin < end) {
                val piece = if (begin == 0) word.substring(begin, end) else continuingSubwordPrefix + word.substring(begin, end)
                val id = tokenToId[piece]
                if (id != null) { matchId = id; break }
                end = word.offsetByCodePoints(end, -1)
            }
            if (matchId < 0) {
                out.truncate(start) // whole word becomes [UNK], which is dropped
                return
            }
            if (matchId != unkId) out.add(matchId)
            begin = end
        }
    }

    private class IntArrayBuilder {
        private var data = IntArray(32)
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        fun truncate(newSize: Int) { size = newSize }

        fun toArray(max: Int): IntArray = data.copyOf(minOf(size, max))
    }

    private companion object {
        fun isWhitespace(cp: Int): Boolean {
            if (cp == ' '.code || cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return true
            return Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()
        }

        fun isControl(cp: Int): Boolean {
            if (cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return false
            return when (Character.getType(cp)) {
                Character.CONTROL.toInt(), Character.FORMAT.toInt(),
                Character.PRIVATE_USE.toInt(), Character.SURROGATE.toInt(), Character.UNASSIGNED.toInt() -> true
                else -> false
            }
        }

        fun isPunctuation(cp: Int): Boolean {
            if (cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126) return true
            return when (Character.getType(cp)) {
                Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt() -> true
                else -> false
            }
        }

        fun isChineseChar(cp: Int): Boolean {
            return cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2A6DF ||
                cp in 0x2A700..0x2B73F || cp in 0x2B740..0x2B81F || cp in 0x2B820..0x2CEAF ||
                cp in 0xF900..0xFAFF || cp in 0x2F800..0x2FA1F
        }
    }
}
