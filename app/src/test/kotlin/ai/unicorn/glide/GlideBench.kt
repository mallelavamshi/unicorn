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

import ai.unicorn.glide.synth.SwipeSynth
import java.io.File
import java.util.Locale
import kotlin.random.Random

/** The app's own dictionary and next-word table, and the word sets the glide tests swipe. */
object GlideBench {
    // Unit tests run in the app module's directory
    val lexicon: GlideLexicon by lazy { GlideLexicon.fromDictionary(File("src/main/res/raw/main_en.dict").readBytes()) }
    val nextWords: NextWords by lazy { NextWords(File("src/main/assets/glide/en_next.bin").readBytes(), lexicon) }

    fun geometry(keys: SwipeSynth.Keys) = KeyGeometry(keys.centerX, keys.centerY)

    /**
     * The dictionary's most common words that are swiped (two keys or more, not offensive), one spelling per
     * lower-case form, from rank [from] (0-based) for [count] words.
     */
    fun commonWords(count: Int, from: Int = 0): List<String> {
        val ids = (0 until lexicon.size).filter { !lexicon.isOffensive(it) }.sortedByDescending { lexicon.frequency(it) }
        val seen = HashSet<String>()
        val words = ArrayList<String>()
        for (id in ids) {
            val word = lexicon.word(id)
            if (seen.add(word.lowercase(Locale.ROOT))) words.add(word)
            if (words.size >= from + count) break
        }
        return words.subList(from, words.size)
    }

    /** Everyday messages, split into words. */
    val sentences: List<List<String>> by lazy {
        File("src/test/resources/glide/sentences.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim().split(Regex("\\s+")) }
    }

    /** Whether a word is swiped at all: one-key words ("I", "a") are tapped. */
    fun isSwiped(word: String): Boolean {
        var keys = 0
        var last = -1
        for (c in word) {
            val k = GlideLexicon.keyOf(c)
            if (k < 0 || k == last) continue
            keys++
            last = k
        }
        return keys >= 2
    }

    /** 0-based rank of [word] among [candidates] ignoring case, or -1. */
    fun rankOf(word: String, candidates: List<GlideDecoder.Candidate>): Int =
        candidates.indexOfFirst { it.word.equals(word, ignoreCase = true) }

    /** A deterministic seed per word, screen and noise, so every run swipes the same paths. */
    fun random(word: String, keys: SwipeSynth.Keys, noise: SwipeSynth.Noise, salt: Int = 0) =
        Random((word.hashCode() * 31 + keys.name.hashCode()) * 31 + noise.name.hashCode() + salt * 7919)

    fun decode(
        decoder: GlideDecoder, swipe: SwipeSynth.Swipe, geometry: KeyGeometry, context: GlideDecoder.Context,
        points: Int = swipe.size, shortlist: Int = GlideDecoder.SHORTLIST,
    ): List<GlideDecoder.Candidate> = decoder.decode(swipe.x, swipe.y, points, geometry, context, 18, shortlist)
}
