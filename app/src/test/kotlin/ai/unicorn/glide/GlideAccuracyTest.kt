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
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Glide typing accuracy and speed on synthetic swipes: the 1,000 and 10,000 most common words of the app's dictionary
 * on three screens, plus everyday sentences where the previous word counts. Writes build/glide/jvm-report.md, which
 * tools/glide-stress.sh puts in docs/research/glide-typing-report.md, and fails under the bar.
 */
class GlideAccuracyTest {
    private class Result(val word: String, val screen: String, val rank: Int, val top: String?)

    private class Score(val name: String, val results: List<Result>) {
        val top1 get() = results.count { it.rank == 0 }.toDouble() / results.size
        val top3 get() = results.count { it.rank in 0..2 }.toDouble() / results.size
    }

    private val finalMs = ArrayList<Double>()
    private val previewMs = ArrayList<Double>()

    private fun swipeWords(
        decoder: GlideDecoder, words: List<String>, screens: List<SwipeSynth.Keys>, noise: SwipeSynth.Noise,
        rotateScreens: Boolean, timed: Boolean, context: GlideDecoder.Context = GlideDecoder.Context(),
    ): List<Result> {
        val results = ArrayList<Result>()
        words.forEachIndexed { i, word ->
            val onScreens = if (rotateScreens) listOf(screens[i % screens.size]) else screens
            for (keys in onScreens) {
                val geometry = GlideBench.geometry(keys)
                val swipe = SwipeSynth.swipe(word, keys, noise, GlideBench.random(word, keys, noise))
                if (timed) {
                    val t0 = System.nanoTime()
                    // The app's live preview: 60% of the swipe, a smaller shortlist (Glide.PREVIEW_SHORTLIST)
                    GlideBench.decode(decoder, swipe, geometry, context, swipe.size * 6 / 10, shortlist = 20)
                    previewMs.add((System.nanoTime() - t0) / 1e6)
                }
                val t0 = System.nanoTime()
                val candidates = GlideBench.decode(decoder, swipe, geometry, context)
                if (timed) finalMs.add((System.nanoTime() - t0) / 1e6)
                results.add(Result(word, keys.name, GlideBench.rankOf(word, candidates), candidates.firstOrNull()?.word))
            }
        }
        return results
    }

    /** Every swiped word of every sentence, with or without the previous word. */
    private fun swipeSentences(decoder: GlideDecoder, withContext: Boolean): List<Result> {
        val results = ArrayList<Result>()
        val noise = SwipeSynth.Noise.REALISTIC
        GlideBench.sentences.forEachIndexed { s, words ->
            val keys = SwipeSynth.Keys.ALL[s % SwipeSynth.Keys.ALL.size]
            val geometry = GlideBench.geometry(keys)
            words.forEachIndexed { i, word ->
                if (!GlideBench.isSwiped(word)) return@forEachIndexed
                val next = if (withContext) GlideBench.nextWords.after(if (i == 0) null else words[i - 1]) else null
                val swipe = SwipeSynth.swipe(word, keys, noise, GlideBench.random(word, keys, noise, salt = s * 100 + i))
                val candidates = GlideBench.decode(decoder, swipe, geometry, GlideDecoder.Context(next = next))
                results.add(Result(word, keys.name, GlideBench.rankOf(word, candidates), candidates.firstOrNull()?.word))
            }
        }
        return results
    }

    @Test
    fun accuracyAndSpeed() {
        val decoder = GlideDecoder(GlideBench.lexicon)
        val top1000 = GlideBench.commonWords(1000)
        val top10000 = GlideBench.commonWords(10000)
        // Warm the JIT so the timings are the steady state a phone reaches after a few swipes
        swipeWords(decoder, GlideBench.commonWords(300, from = 20000), SwipeSynth.Keys.ALL, SwipeSynth.Noise.REALISTIC, false, false)

        val realistic1k = Score("1,000 words, realistic", swipeWords(decoder, top1000, SwipeSynth.Keys.ALL, SwipeSynth.Noise.REALISTIC, false, true))
        val sloppy1k = Score("1,000 words, sloppy", swipeWords(decoder, top1000, SwipeSynth.Keys.ALL, SwipeSynth.Noise.SLOPPY, false, false))
        val clean1k = Score("1,000 words, clean", swipeWords(decoder, top1000, SwipeSynth.Keys.ALL, SwipeSynth.Noise.CLEAN, false, false))
        val realistic10k = Score("10,000 words, realistic", swipeWords(decoder, top10000, SwipeSynth.Keys.ALL, SwipeSynth.Noise.REALISTIC, true, false))
        val sloppy10k = Score("10,000 words, sloppy", swipeWords(decoder, top10000, SwipeSynth.Keys.ALL, SwipeSynth.Noise.SLOPPY, true, false))
        val firstWords = Score("1,000 words, each as a message's first word, realistic", swipeWords(
            decoder, top1000, SwipeSynth.Keys.ALL, SwipeSynth.Noise.REALISTIC, false, false,
            GlideDecoder.Context(next = GlideBench.nextWords.after(null)),
        ))
        val sentences = Score("Sentences, previous word used", swipeSentences(decoder, withContext = true))
        val sentencesAlone = Score("Sentences, each word alone", swipeSentences(decoder, withContext = false))

        val p50Final = percentile(finalMs, 0.50)
        val p95Final = percentile(finalMs, 0.95)
        val p50Preview = percentile(previewMs, 0.50)
        val p95Preview = percentile(previewMs, 0.95)

        val gate = listOf(
            Triple("Top-1, 1,000 words, realistic", realistic1k.top1, TOP1_BAR),
            Triple("Top-3, 1,000 words, realistic", realistic1k.top3, TOP3_BAR),
        )
        val report = buildString {
            appendLine("### JVM harness (synthetic swipes)")
            appendLine()
            appendLine("Dictionary: ${GlideBench.lexicon.size} swipeable words from `main_en.dict`. Screens: ${SwipeSynth.Keys.ALL.joinToString { it.name }}.")
            appendLine("The 1,000-word sets swipe every word on all three screens (3,000 swipes); the 10,000-word sets swipe each word once, rotating screens.")
            appendLine("Words are matched ignoring case (\"us\" for \"US\"). One-key words (\"I\", \"a\") are tapped, so they are not in the sets.")
            appendLine()
            appendLine("| Set | Swipes | Top-1 | Top-3 |")
            appendLine("|---|---:|---:|---:|")
            for (s in listOf(realistic1k, sloppy1k, clean1k, firstWords, realistic10k, sloppy10k, sentences, sentencesAlone)) {
                appendLine("| ${s.name} | ${s.results.size} | ${pct(s.top1)} | ${pct(s.top3)} |")
            }
            appendLine()
            appendLine("By screen, 1,000 words, realistic:")
            appendLine()
            appendLine("| Screen | Top-1 | Top-3 |")
            appendLine("|---|---:|---:|")
            for ((screen, results) in realistic1k.results.groupBy { it.screen }) {
                val s = Score(screen, results)
                appendLine("| $screen | ${pct(s.top1)} | ${pct(s.top3)} |")
            }
            appendLine()
            appendLine("Decode time on this computer's JVM, 3,000 swipes: final word p50 ${ms(p50Final)}, p95 ${ms(p95Final)}; " +
                "live preview (60% of the swipe) p50 ${ms(p50Preview)}, p95 ${ms(p95Preview)}. Phone timings are in the emulator section.")
            appendLine()
            appendLine("Most frequent misses, 1,000 words, realistic (swiped → got):")
            appendLine()
            val misses = realistic1k.results.filter { it.rank != 0 }
                .groupingBy { "${it.word} → ${it.top ?: "nothing"}" }.eachCount()
                .entries.sortedByDescending { it.value }.take(25)
            appendLine(misses.joinToString(", ") { if (it.value > 1) "${it.key} (${it.value}×)" else it.key })
            appendLine()
            appendLine("| Bar | Measured | Needed | |")
            appendLine("|---|---:|---:|---|")
            for ((name, value, bar) in gate) appendLine("| $name | ${pct(value)} | ${pct(bar)} | ${if (value >= bar) "pass" else "FAIL"} |")
        }
        File("build/glide").mkdirs()
        File("build/glide/jvm-report.md").writeText(report)
        File("build/glide/jvm-summary.txt").writeText(
            "top1_1k=${realistic1k.top1}\ntop3_1k=${realistic1k.top3}\ntop1_10k=${realistic10k.top1}\ntop3_10k=${realistic10k.top3}\n" +
                "p95_final_ms=$p95Final\np95_preview_ms=$p95Preview\n"
        )
        println(report)
        for ((name, value, bar) in gate) assertTrue(value >= bar, "$name: ${pct(value)} is under ${pct(bar)}")
        assertTrue(p95Final < JVM_FINAL_P95_MS, "p95 final decode ${ms(p95Final)} on the JVM")
    }

    companion object {
        const val TOP1_BAR = 0.90
        const val TOP3_BAR = 0.97
        /** A desktop JVM is several times faster than a phone; the emulator gate checks the real 100 ms target. */
        const val JVM_FINAL_P95_MS = 40.0

        fun percentile(values: List<Double>, p: Double): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            return sorted[((sorted.size - 1) * p).toInt()]
        }

        fun pct(v: Double) = String.format(Locale.US, "%.1f%%", v * 100)
        fun ms(v: Double) = String.format(Locale.US, "%.1f ms", v)
    }
}
