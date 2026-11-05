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

package ai.unicorn.glide

import java.nio.ByteBuffer
import java.util.Locale

/**
 * How much likelier a word is after the previous one than on its own, in nats, from assets/glide/en_next.bin (built
 * by tools/make_glide_bigrams.py from Tatoeba's English sentences). This is what picks "to" after "want" and "too"
 * after "me", which have the same path.
 */
class NextWords(bytes: ByteArray, lexicon: GlideLexicon) {
    private val buffer = ByteBuffer.wrap(bytes)
    /** Case group in the lexicon of each word in the file, or -1. */
    private val groups: IntArray
    private val contexts = HashMap<String, Int>()
    private val contextBackoff: ByteArray
    private val contextCount: IntArray
    private val contextOffset: IntArray
    private val pairsStart: Int

    init {
        require(buffer.int == MAGIC) { "Not a next-word file" }
        val words = buffer.short.toInt() and 0xFFFF
        val text = arrayOfNulls<String>(words)
        groups = IntArray(words) { i ->
            val length = buffer.get().toInt() and 0xFF
            val word = String(bytes, buffer.position(), length, Charsets.UTF_8)
            buffer.position(buffer.position() + length)
            text[i] = word
            lexicon.find(word).firstOrNull()?.let { lexicon.group(it) } ?: -1
        }
        val count = buffer.short.toInt() and 0xFFFF
        contextBackoff = ByteArray(count)
        contextCount = IntArray(count)
        contextOffset = IntArray(count)
        for (c in 0 until count) {
            val word = buffer.short.toInt() and 0xFFFF
            contextBackoff[c] = buffer.get()
            contextCount[c] = buffer.short.toInt() and 0xFFFF
            contextOffset[c] = buffer.int
            contexts[text[word]!!] = c
        }
        pairsStart = buffer.position()
    }

    /** The boosts after [previous] (null at the start of a sentence), or null when nothing is known about it. */
    fun after(previous: String?): Context? {
        val key = previous?.lowercase(Locale.ROOT)?.replace('’', '\'') ?: START
        val c = contexts[key] ?: return null
        val n = contextCount[c]
        val ids = IntArray(n)
        val boosts = FloatArray(n)
        var kept = 0
        var at = pairsStart + contextOffset[c]
        for (i in 0 until n) {
            val group = groups[buffer.getShort(at).toInt() and 0xFFFF]
            val boost = buffer.get(at + 2) / 8f
            at += 3
            if (group < 0) continue
            ids[kept] = group
            boosts[kept] = boost
            kept++
        }
        // Sorted by group id for binary search
        val order = (0 until kept).sortedBy { ids[it] }
        return Context(
            contextBackoff[c] / 8f,
            IntArray(kept) { ids[order[it]] },
            FloatArray(kept) { boosts[order[it]] },
        )
    }

    class Context(private val backoff: Float, private val groups: IntArray, private val boosts: FloatArray) {
        /** The largest boost any word gets after this context. */
        val maxBoost: Float = boosts.fold(backoff) { a, b -> maxOf(a, b) }

        /** The boost for a word in case group [group]: its own if seen after the context, otherwise the backoff. */
        fun boost(group: Int): Float {
            val i = groups.binarySearch(group)
            return if (i >= 0) boosts[i] else backoff
        }
    }

    companion object {
        private const val MAGIC = 0x55474E31 // "UGN1"
        private const val START = "<s>"
    }
}
