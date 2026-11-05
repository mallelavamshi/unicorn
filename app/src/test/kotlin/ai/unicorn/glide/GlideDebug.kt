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
 * Shows why words decode the way they do: GLIDE_DEBUG=others,same ./gradlew :app:testDebugUnitTest --tests
 * ai.unicorn.glide.GlideDebug writes each swipe's points and the shortlist's evidence to build/glide/debug.txt.
 */
class GlideDebug {
    @Test
    fun explain() {
        val words = System.getenv("GLIDE_DEBUG")?.split(",")?.filter { it.isNotBlank() } ?: return
        val out = StringBuilder()
        val decoder = GlideDecoder(GlideBench.lexicon)
        val rows = ArrayList<String>()
        decoder.features = GlideDecoder.Features { word, location, shape, warp, visit, ends, frequency, next, _ ->
            rows.add(String.format(Locale.US, "%-14s loc=%.3f shape=%.3f warp=%.3f visit=%.3f ends=%.3f f=%d next=%.2f",
                word, location, shape, warp, visit, ends, frequency, next))
        }
        for (word in words) {
            val keys = SwipeSynth.Keys.PHONE_FHD
            val geometry = GlideBench.geometry(keys)
            val noise = SwipeSynth.Noise.REALISTIC
            val swipe = SwipeSynth.swipe(word, keys, noise, GlideBench.random(word, keys, noise))
            rows.clear()
            val result = GlideBench.decode(decoder, swipe, geometry, GlideDecoder.Context())
            out.appendLine("== $word: ${swipe.size} points")
            out.appendLine("path (key units): " + (0 until swipe.size step 4).joinToString(" ") {
                String.format(Locale.US, "%.1f,%.1f", swipe.x[it] / geometry.pitchX, swipe.y[it] / geometry.pitchY)
            })
            out.appendLine("result: " + result.take(8).joinToString { String.format(Locale.US, "%s %.1f", it.word, it.score) })
            val target = rows.firstOrNull { it.startsWith("$word ") }
            out.appendLine("target: ${target ?: "NOT SHORTLISTED"}")
            for (r in rows.take(400).filter { row -> result.take(5).any { row.startsWith(it.word + " ") } }) out.appendLine("  $r")
        }
        File("build/glide").mkdirs()
        File("build/glide/debug.txt").writeText(out.toString())
        println(out)
    }
}
