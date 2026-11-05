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

package ai.unicorn.glidetest

import ai.unicorn.glide.synth.SwipeSynth
import android.os.SystemClock
import java.util.Locale
import kotlin.random.Random

/**
 * A long, busy session: random swipes, sentences, taps, backspaces, alternatives, cancelled and fast swipes, and a turn
 * of the phone every few minutes. Every 100 actions a clean "hello" must still come out right. Crashes, ANRs and
 * memory are watched from outside by tools/glide-stress.sh.
 */
class Soak(private val device: Device, private val words: WordLists, private val report: Report, private val minutes: Double) {
    private val random = Random(7)
    private val fast = SwipeSynth.Noise("fast", 1.6f, 2.0f, 2.5f, 0.3f, 2.5f, 3f, 0.25f, 0.35f, 2.2f, 3.2f)

    private companion object {
        const val SANITY_EVERY = 25
    }

    fun run(): Boolean {
        val startPid = device.appPid()
        val start = SystemClock.uptimeMillis()
        val end = start + (minutes * 60_000).toLong()
        var lastTurn = start
        var landscape = false
        val counts = LinkedHashMap<String, Int>()
        var sanity = 0
        var sanityFailures = 0
        var actions = 0
        val windows = ArrayList<String>()
        var windowStart = start
        val finals = ArrayList<Int>()
        val finalsCpu = ArrayList<Int>()
        val vocabulary = words.all + words.sentences.flatten().filter { it.length > 1 }
        device.clear()
        device.decodeTimes()
        while (SystemClock.uptimeMillis() < end) {
            val roll = random.nextInt(100)
            val action = when {
                roll < 55 -> "swipe"
                roll < 65 -> "sentence"
                roll < 75 -> "taps"
                roll < 83 -> "backspace"
                roll < 90 -> "alternative"
                roll < 95 -> "cancel"
                else -> "fast"
            }
            try {
                when (action) {
                    "swipe" -> device.swipe(SwipeSynth.swipe(vocabulary.random(random), device.synthKeys(), SwipeSynth.Noise.REALISTIC, random))
                    "fast" -> device.swipe(SwipeSynth.swipe(vocabulary.random(random), device.synthKeys(), fast, random))
                    "sentence" -> for (w in words.sentences.random(random).take(6)) {
                        if (w.length > 1) device.swipe(SwipeSynth.swipe(w, device.synthKeys(), SwipeSynth.Noise.REALISTIC, random))
                        SystemClock.sleep(40)
                    }
                    "taps" -> {
                        device.typeKeys(listOf("ok ", "yes ", "no ", "lol ", "hi ").random(random))
                        SystemClock.sleep(400)
                    }
                    "backspace" -> repeat(1 + random.nextInt(3)) { device.tapKey("delete") }
                    "alternative" -> device.alternatives(device.text().trim().substringAfterLast(' '))
                        .randomOrNull(random)?.let { (_, r) -> device.tap(r.exactCenterX(), r.exactCenterY()) }
                    "cancel" -> {
                        val s = SwipeSynth.swipe("wor", device.synthKeys(), SwipeSynth.Noise.REALISTIC, random)
                        device.drag(s.x + floatArrayOf(s.x.last()), s.y + floatArrayOf(device.keyboardTop - 300f), s.t + intArrayOf(s.t.last() + 80))
                    }
                }
                counts[action] = (counts[action] ?: 0) + 1
            } catch (t: Throwable) {
                counts["error"] = (counts["error"] ?: 0) + 1
            }
            actions++
            if (actions % 10 == 0) device.dismissSystemDialogs()
            SystemClock.sleep(150L + random.nextInt(250))
            if (device.text().length > 400) device.clear()
            if (actions % SANITY_EVERY == 0) {
                sanity++
                device.clear()
                val before = device.text()
                device.swipe(SwipeSynth.swipe("hello", device.synthKeys(), SwipeSynth.Noise.CLEAN, random))
                device.awaitChange(before)
                if (!device.text().trim().equals("hello", ignoreCase = true)) {
                    // Something in the run left the keys out of reach (symbols showing, a turn not settled): note
                    // it, put things back, and keep going; a stuck Unicorn would fail every check after this
                    sanityFailures++
                    if (landscape) {
                        device.rotate(false)
                        landscape = false
                        lastTurn = SystemClock.uptimeMillis()
                    }
                    device.recover()
                }
                device.clear()
            }
            if (SystemClock.uptimeMillis() - lastTurn > 4 * 60_000) {
                landscape = !landscape
                if (!device.rotate(landscape)) {
                    counts["turn not settled"] = (counts["turn not settled"] ?: 0) + 1
                    device.recover()
                }
                lastTurn = SystemClock.uptimeMillis()
            }
            if (SystemClock.uptimeMillis() - windowStart > 2 * 60_000) {
                val times = device.decodeTimes()
                finals.addAll(times.finalWall)
                finalsCpu.addAll(times.finalCpu)
                windows.add("${(SystemClock.uptimeMillis() - start) / 60_000} min: ${times.finalWall.size} swipes decoded, " +
                    "p95 ${EndToEnd.pct(times.finalCpu, 0.95)} ms CPU, ${EndToEnd.pct(times.finalWall, 0.95)} ms wall clock")
                windowStart = SystemClock.uptimeMillis()
            }
        }
        if (landscape) device.rotate(false)
        device.unfreezeRotation()
        device.decodeTimes().let {
            finals.addAll(it.finalWall)
            finalsCpu.addAll(it.finalCpu)
        }
        val pid = device.appPid()

        report.line("### Soak (${String.format(Locale.US, "%.0f", minutes)} minutes on the emulator)")
        report.line()
        report.line("$actions actions: ${counts.entries.joinToString(", ") { "${it.value} ${it.key}" }}. The phone turned every 4 minutes. " +
            "\"Isn't responding\" dialogs from other apps answered with Wait: ${device.systemDialogs}.")
        report.line()
        for (w in windows) report.line("- $w")
        report.line()
        report.check("Unicorn kept running (same process the whole time)", pid == startPid, "pid $startPid → $pid")
        report.check("a clean 'hello' every $SANITY_EVERY actions came out right (at least 90%, the keys never stuck)",
            sanity >= 3 && sanityFailures * 10 <= sanity, "$sanityFailures of $sanity wrong")
        report.check("the keys kept decoding all through the soak", finalsCpu.size >= actions / 2, "${finalsCpu.size} words decoded in $actions actions")
        report.check("final decode p95 under 100 ms over the whole soak (CPU time)", finalsCpu.isNotEmpty() && EndToEnd.pct(finalsCpu, 0.95) < 100,
            "${finalsCpu.size} decodes, p50 ${EndToEnd.pct(finalsCpu, 0.5)} ms, p95 ${EndToEnd.pct(finalsCpu, 0.95)} ms; " +
                "wall clock p50 ${EndToEnd.pct(finals, 0.5)} ms, p95 ${EndToEnd.pct(finals, 0.95)} ms")
        report.line()
        report.line("${report.failures} failure(s)")
        return report.failures == 0
    }
}
