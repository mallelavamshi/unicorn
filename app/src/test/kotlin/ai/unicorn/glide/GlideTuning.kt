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

/**
 * Tunes [GlideWeights] on words the accuracy test doesn't use (ranks 1,001 to 3,500, other random seeds): records each
 * shortlisted word's evidence once, then searches the weights by re-ranking. Runs only with GLIDE_TUNE=1:
 *
 *     GLIDE_TUNE=1 ./gradlew :app:testDebugUnitTest --tests ai.unicorn.glide.GlideTuning
 *
 * and writes the best weights to build/glide/tuning.txt.
 */
class GlideTuning {
    private class Cand(
        val correct: Boolean, val location: Float, val shape: Float, val warp: Float, val visit: Float,
        val ends: Float, val frequency: Int, val abbreviation: Boolean,
    )

    private class Swipe(val candidates: Array<Cand>)

    private val names = listOf("location", "shape", "warp", "visit", "ends", "unigram", "abbreviation")

    private fun score(c: Cand, w: FloatArray) =
        -(w[0] * c.location + w[1] * c.shape + w[2] * c.warp + w[3] * c.visit + w[4] * c.ends) + w[5] * c.frequency -
            (if (c.abbreviation) w[6] else 0f)

    /** Top-1 plus a third of top-3, the bar's two numbers in one. */
    private fun objective(swipes: List<Swipe>, w: FloatArray): Double {
        var top1 = 0
        var top3 = 0
        for (s in swipes) {
            val correct = s.candidates.firstOrNull { it.correct } ?: continue
            val target = score(correct, w)
            var better = 0
            for (c in s.candidates) if (!c.correct && score(c, w) > target) better++
            if (better == 0) top1++
            if (better < 3) top3++
        }
        return (top1 + top3 / 3.0) / swipes.size
    }

    @Test
    fun tune() {
        if (System.getenv("GLIDE_TUNE") != "1") return
        val decoder = GlideDecoder(GlideBench.lexicon)
        val swipes = ArrayList<Swipe>()
        var current = ArrayList<Cand>()
        var target = ""
        decoder.features = GlideDecoder.Features { word, location, shape, warp, visit, ends, frequency, _, _ ->
            current.add(Cand(word.equals(target, ignoreCase = true), location, shape, warp, visit, ends, frequency, word.count { it.isUpperCase() } >= 2))
        }
        val words = GlideBench.commonWords(2500, from = 1000)
        for (noise in listOf(SwipeSynth.Noise.REALISTIC, SwipeSynth.Noise.SLOPPY)) {
            words.forEachIndexed { i, word ->
                val keys = SwipeSynth.Keys.ALL[i % SwipeSynth.Keys.ALL.size]
                target = word
                current = ArrayList()
                val swipe = SwipeSynth.swipe(word, keys, noise, GlideBench.random(word, keys, noise, salt = 77))
                GlideBench.decode(decoder, swipe, GlideBench.geometry(keys), GlideDecoder.Context())
                swipes.add(Swipe(current.toTypedArray()))
            }
        }
        val start = GlideWeights()
        var best = floatArrayOf(start.location, start.shape, start.warp, start.visit, start.ends, start.unigram, start.abbreviation)
        var bestScore = objective(swipes, best)
        val log = StringBuilder("start ${fmt(best)} -> ${"%.4f".format(Locale.US, bestScore)}\n")
        for (round in 0 until 8) {
            var improved = false
            for (p in best.indices) {
                for (m in floatArrayOf(0.5f, 0.7f, 0.85f, 1.18f, 1.4f, 2f)) {
                    val trial = best.copyOf()
                    trial[p] *= m
                    val s = objective(swipes, trial)
                    if (s > bestScore + 1e-5) {
                        best = trial
                        bestScore = s
                        improved = true
                    }
                }
            }
            log.append("round $round ${fmt(best)} -> ${"%.4f".format(Locale.US, bestScore)}\n")
            if (!improved) break
        }
        val reachable = swipes.count { s -> s.candidates.any { it.correct } }.toDouble() / swipes.size
        log.append("target in shortlist: ${"%.4f".format(Locale.US, reachable)}\n")

        // The next-word weight, on sentences the accuracy test doesn't use
        val sentences = File("src/test/resources/glide/tuning-sentences.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim().split(Regex("\\s+")) }
        decoder.features = null
        for (weight in floatArrayOf(0f, 0.6f, 0.8f, 1.0f, 1.2f, 1.5f)) {
            decoder.weights = GlideWeights().copy(nextWord = weight)
            var right = 0
            var count = 0
            for ((s, words) in sentences.withIndex()) {
                val keys = SwipeSynth.Keys.ALL[s % SwipeSynth.Keys.ALL.size]
                for ((i, word) in words.withIndex()) {
                    if (!GlideBench.isSwiped(word)) continue
                    for (noise in listOf(SwipeSynth.Noise.REALISTIC, SwipeSynth.Noise.SLOPPY)) {
                        val swipe = SwipeSynth.swipe(word, keys, noise, GlideBench.random(word, keys, noise, salt = 500 + s * 50 + i))
                        val next = GlideBench.nextWords.after(if (i == 0) null else words[i - 1])
                        val top = GlideBench.decode(decoder, swipe, GlideBench.geometry(keys), GlideDecoder.Context(next = next)).firstOrNull()
                        if (top?.word.equals(word, ignoreCase = true)) right++
                        count++
                    }
                }
            }
            log.append("nextWord=$weight: ${"%.4f".format(Locale.US, right.toDouble() / count)} of $count\n")
        }
        File("build/glide").mkdirs()
        File("build/glide/tuning.txt").writeText(log.toString())
        println(log)
    }

    private fun fmt(w: FloatArray) = names.indices.joinToString(" ") { "${names[it]}=${"%.4f".format(Locale.US, w[it])}" }
}
