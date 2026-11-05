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
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlideDecoderTest {
    private val keys = SwipeSynth.Keys.PHONE_FHD
    private val geometry = GlideBench.geometry(keys)
    private val decoder = GlideDecoder(GlideBench.lexicon)

    private fun clean(word: String) = SwipeSynth.swipe(word, keys, SwipeSynth.Noise.CLEAN, Random(1))

    private fun top(word: String, context: GlideDecoder.Context = GlideDecoder.Context()): String? =
        GlideBench.decode(decoder, clean(word), geometry, context).firstOrNull()?.word

    @Test
    fun readsTheEnginesDictionary() {
        val lexicon = GlideBench.lexicon
        assertTrue(lexicon.size > 150_000, "${lexicon.size} words")
        val the = lexicon.find("the")
        assertTrue(the.isNotEmpty())
        assertEquals(222, lexicon.frequency(the[0]))
        // Paths drop repeated keys: hello visits h, e, l, o
        val hello = lexicon.find("hello")[0]
        assertEquals(listOf('h', 'e', 'l', 'o'), (lexicon.pathStart(hello) until lexicon.pathEnd(hello)).map { 'a' + lexicon.pathKey(it) })
    }

    @Test
    fun cleanSwipesOfCommonWords() {
        for (word in listOf("hello", "world", "keyboard", "everyone", "swipe", "because", "people", "information")) {
            assertEquals(word, top(word)?.lowercase(), word)
        }
    }

    @Test
    fun apostrophesAndCapitalsComeFromTheDictionary() {
        assertEquals("don't", top("dont"))
        assertEquals("I'm", top("im"))
        assertEquals("Monday", top("monday"))
    }

    @Test
    fun doubleLettersNeedNoWiggle() {
        for (word in listOf("coffee", "letter", "address", "little")) assertEquals(word, top(word), word)
    }

    @Test
    fun thePreviousWordPicksBetweenWordsWithTheSamePath() {
        val after = { previous: String -> GlideDecoder.Context(next = GlideBench.nextWords.after(previous)) }
        assertEquals("for", top("for", after("thanks")))
        assertEquals("four", top("for", after("at")))
        assertEquals("to", top("to", after("want")))
        assertEquals("too", top("to", after("far")))
    }

    @Test
    fun swipesStartingOnOtherKeysUseTheLetters() {
        // From the space bar under m, the shift key beside z, and the number key above t
        val m = keys.center('m')!!
        val z = keys.center('z')!!
        val t = keys.center('t')!!
        for ((word, start) in listOf("most" to (m.first to m.second + 164f), "zone" to (z.first - 160f to z.second), "time" to (t.first to t.second - 164f))) {
            val path = clean(word)
            val (sx, sy) = start
            val xs = FloatArray(6) { sx + (path.x[0] - sx) * it / 6 } + path.x
            val ys = FloatArray(6) { sy + (path.y[0] - sy) * it / 6 } + path.y
            val result = decoder.decode(xs, ys, xs.size, geometry, GlideDecoder.Context(), 5)
            assertEquals(word, result.firstOrNull()?.word, "$word from $sx,$sy")
        }
    }

    @Test
    fun offensiveWordsOnlyWhenAllowed() {
        val word = GlideBench.lexicon.let { lex -> (0 until lex.size).first { lex.isOffensive(it) && lex.frequency(it) > 90 && GlideBench.isSwiped(lex.word(it)) }.let { lex.word(it) } }
        assertTrue(top(word)?.equals(word, ignoreCase = true) != true, "blocked: $word")
        assertEquals(word.lowercase(), top(word, GlideDecoder.Context(blockOffensive = false))?.lowercase())
    }

    @Test
    fun theUsersOwnWordsCanBeSwiped() {
        val userWords = GlideLexicon.build(listOf(GlideLexicon.Entry("Gachibowli", 140)))
        assertEquals("Gachibowli", top("gachibowli", GlideDecoder.Context(userWords = userWords)))
    }

    @Test
    fun theSavedLexiconDecodesTheSame() {
        val file = java.io.File.createTempFile("glide", ".bin")
        try {
            GlideBench.lexicon.write(file)
            val loaded = GlideLexicon.read(file)
            assertEquals(GlideBench.lexicon.size, loaded.size)
            val reloaded = GlideDecoder(loaded)
            for (word in listOf("hello", "don't", "Monday", "information", "coffee")) {
                val swipe = SwipeSynth.swipe(word, keys, SwipeSynth.Noise.REALISTIC, Random(word.hashCode()))
                val a = GlideBench.decode(decoder, swipe, geometry, GlideDecoder.Context()).map { it.word }
                val b = GlideBench.decode(reloaded, swipe, geometry, GlideDecoder.Context()).map { it.word }
                assertEquals(a, b, word)
            }
            assertEquals(GlideBench.lexicon.find("us").toList(), loaded.find("us").toList())
        } finally {
            file.delete()
        }
    }

    @Test
    fun tapsAndTinySwipesAreNotWords() {
        val x = floatArrayOf(540f, 541f)
        val y = floatArrayOf(1849f, 1849f)
        assertTrue(decoder.decode(x, y, 2, geometry, GlideDecoder.Context(), 5).isEmpty())
        assertTrue(decoder.decode(x, y, 1, geometry, GlideDecoder.Context(), 5).isEmpty())
    }
}
