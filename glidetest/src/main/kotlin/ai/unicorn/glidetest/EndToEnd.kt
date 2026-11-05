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

/** The emulator end-to-end checks: every behavior a person relies on, through the real keys. */
class EndToEnd(private val device: Device, private val words: WordLists, private val report: Report) {
    private val realistic = SwipeSynth.Noise.REALISTIC
    private val fast = SwipeSynth.Noise("fast", 1.6f, 2.0f, 2.5f, 0.3f, 2.5f, 3f, 0.25f, 0.35f, 2.2f, 3.2f)
    private val random = Random(20261103)
    private val latencies = ArrayList<Long>()
    private val finals = ArrayList<Int>()
    private val previews = ArrayList<Int>()
    private val finalsCpu = ArrayList<Int>()
    private val previewsCpu = ArrayList<Int>()
    private val startPid = device.appPid()

    private fun norm(s: String) = s.trim().replace('’', '\'').lowercase(Locale.US)

    /** One-key words ("I", "a") are tapped; everything else is swiped. */
    private fun isSwiped(word: String) =
        word.lowercase().filter { it in 'a'..'z' }.fold("") { keys, c -> if (keys.lastOrNull() == c) keys else keys + c }.length >= 2

    /**
     * Two letters on neighboring keys ("we", "as"): people tap these. A swipe that short isn't recognized as one before
     * the key's long press opens its popup (the engine's gesture detection, unchanged from AOSP); see the report's limits.
     */
    private fun isNeighborPair(word: String): Boolean {
        val keys = word.lowercase().filter { it in 'a'..'z' }.fold("") { k, c -> if (k.lastOrNull() == c) k else k + c }
        if (keys.length != 2) return false
        val (ax, ay) = device.keys[keys[0].toString()] ?: return false
        val (bx, by) = device.keys[keys[1].toString()] ?: return false
        val pitch = (device.keys["p"]!!.first - device.keys["q"]!!.first) / 9f
        return kotlin.math.hypot(ax - bx, ay - by) < 1.3f * pitch
    }

    /** Swipes [word] where the cursor is and returns the field's text once it settles. */
    /**
     * A digit or accented letter where a plain word was entered: the emulator stalled past the long-press time and the
     * first key's popup (q-p carry digits, vowels accents) took the touch instead of the keys.
     */
    private fun isLongPressPopup(entered: String, intended: String) =
        intended.all { it.code < 128 } && entered.any { it.isDigit() || it.code in 128..0x24F }

    /** Long presses the stalling emulator caused, each undone and tried once more; reported, not hidden. */
    private var popupRetries = 0

    private fun swipe(word: String, noise: SwipeSynth.Noise = realistic): String {
        // People pause a moment between words; it also keeps a stalled emulator from turning a swipe into a long press
        SystemClock.sleep(300)
        val before = device.text()
        for (attempt in 1..2) {
            device.swipe(SwipeSynth.swipe(word, device.synthKeys(), noise, random))
            val ms = device.awaitChange(before)
            val after = device.text()
            if (attempt == 1 && isLongPressPopup(after.removePrefix(before), word)) {
                popupRetries++
                device.setText(before)
                continue
            }
            if (ms >= 0) latencies.add(ms)
            break
        }
        return device.text()
    }

    private fun collectTimes() {
        val times = device.decodeTimes()
        finals.addAll(times.finalWall)
        finalsCpu.addAll(times.finalCpu)
        previews.addAll(times.previewWall)
        previewsCpu.addAll(times.previewCpu)
    }

    private fun section(title: String, block: () -> Unit) {
        report.line()
        report.line("#### $title")
        report.line()
        device.dismissSystemDialogs()
        try {
            block()
        } catch (t: Throwable) {
            report.check("$title ran to the end", false, "${t.javaClass.simpleName}: ${t.message}")
        }
        collectTimes()
    }

    fun run(): Boolean {
        report.line("### Emulator end-to-end (injected touches through the real keys)")
        report.line()
        report.line("Screen ${device.activity.resources.displayMetrics.widthPixels}x${device.activity.resources.displayMetrics.heightPixels}, " +
            "${String.format(Locale.US, "%.1f", device.pxPerMm)} px/mm. Swipes come from the same generator as the JVM tests (realistic noise).")
        device.decodeTimes() // start the decode-time log from here

        section("Taps work as before") {
            // A tap held up by a stalled emulator past the long-press time opens accents or digits instead; that is
            // the emulator, not the keys, so each check gets a second try
            fun typed(keys: String, expected: String): String {
                var got = ""
                for (attempt in 1..2) {
                    device.clear()
                    device.typeKeys(keys)
                    SystemClock.sleep(500)
                    got = device.text()
                    if (norm(got) == expected) break
                    if (isLongPressPopup(got, keys)) popupRetries++ else break
                }
                return got
            }
            val hello = typed("hello world ", "hello world")
            report.check("tapping 'hello world ' types it", norm(hello) == "hello world", "got '$hello'")
            val corrected = typed("teh end ", "the end")
            report.check("tapped typos are still corrected ('teh' to 'The')", norm(corrected) == "the end", "got '$corrected'")
        }

        var top1 = 0
        var top3 = 0
        var total = 0
        section("Single words") {
            for ((name, list) in words.sections) {
                var ok = 0
                val misses = ArrayList<String>()
                for (word in list) {
                    device.clear()
                    val got = swipe(word)
                    total++
                    if (norm(got) == norm(word)) {
                        ok++; top1++; top3++
                    } else {
                        val strip = device.suggestions().map { norm(it.first) }
                        if (norm(word) in strip) top3++
                        misses.add("$word → ${got.trim().ifEmpty { "nothing" }}")
                    }
                }
                report.line("- $name: $ok of ${list.size} first time${if (misses.isNotEmpty()) "; missed ${misses.joinToString(", ")}" else ""}")
            }
            report.check("top-1 at least 85% on the device", top1 >= 0.85 * total, "$top1 of $total")
            report.check("top-3 (word in the strip) at least 95%", top3 >= 0.95 * total, "$top3 of $total")
        }

        section("Sentences, with automatic spaces") {
            var right = 0
            var count = 0
            var spacing = 0
            val checked = words.sentences.take(12)
            for (sentence in checked) {
                device.clear()
                var lastWasTap = false
                for (word in sentence) {
                    if (isSwiped(word) && !isNeighborPair(word)) {
                        if (lastWasTap) SystemClock.sleep(450)
                        swipe(word)
                        lastWasTap = false
                    } else {
                        val before = device.text()
                        device.typeKeys(word.lowercase())
                        if (isLongPressPopup(device.text().removePrefix(before), word)) {
                            popupRetries++
                            device.setText(before)
                            device.typeKeys(word.lowercase())
                        }
                        lastWasTap = true
                    }
                }
                SystemClock.sleep(300)
                val got = device.text()
                val gotWords = got.trim().split(Regex("\\s+"))
                if (gotWords.size == sentence.size && !got.trimEnd().contains("  ")) spacing++
                count += sentence.size
                right += sentence.indices.count { it < gotWords.size && norm(gotWords[it]) == norm(sentence[it]) }
                report.line("- '${got.trim()}'")
            }
            report.check("one space between every word", spacing == checked.size, "$spacing of ${checked.size} sentences")
            report.check("sentence words right at least 85%", right >= 0.85 * count, "$right of $count")
        }

        section("Rapid back-to-back swipes") {
            device.clear()
            // No words that share a path with a more common one ("typing" and "topping"): this checks the spacing
            val list = listOf("this", "is", "really", "fast", "writing", "with", "short", "breaks")
            for (word in list) {
                device.swipe(SwipeSynth.swipe(word, device.synthKeys(), realistic, random))
                SystemClock.sleep(60)
            }
            SystemClock.sleep(1200)
            val got = device.text().trim().split(Regex("\\s+"))
            report.check("every swipe becomes one word", got.size == list.size, "'${device.text().trim()}'")
            report.check("most words right", list.indices.count { it < got.size && norm(got[it]) == list[it] } >= 6, "'${device.text().trim()}'")
        }

        section("Fast swipes") {
            var ok = 0
            val list = words.sections["common"].orEmpty().shuffled(random).take(20)
            for (word in list) {
                device.clear()
                if (norm(swipe(word, fast)) == norm(word)) ok++
            }
            report.check("fast swipes (2 to 3 times the usual speed) right at least 75%", ok >= 15, "$ok of ${list.size}")
        }

        section("Swipes mixed with taps and punctuation") {
            device.clear()
            swipe("hello")
            device.tapKey("comma".takeIf { it in device.keys } ?: "space")
            SystemClock.sleep(450)
            swipe("how")
            swipe("are")
            swipe("you")
            val got = device.text()
            // The spacing around the tapped comma is what this checks; the words themselves are measured elsewhere
            report.check("swipe, tapped comma, swipes: 'Word, word word word'", Regex("^\\p{L}+, \\p{L}[\\p{L}']* \\p{L}[\\p{L}']* \\p{L}[\\p{L}']*$").matches(got.trim()), "got '$got'")
            device.clear()
            device.typeKeys("ok")
            SystemClock.sleep(450)
            swipe("thanks")
            report.check("a tapped word then a swipe", norm(device.text()) == "ok thanks", "got '${device.text()}'")
        }

        section("Capitals") {
            device.clear()
            swipe("this")
            swipe("is")
            device.tapKey("shift")
            SystemClock.sleep(450)
            swipe("great")
            val got = device.text().trim()
            report.check("first word capitalized by itself, shift capitalizes a swiped word", got == "This is Great", "got '$got'")
            // Caps lock is a double tap on shift within 300 ms, which a stalled emulator can miss. Two tapped letters
            // show whether it took before the swipe is judged; the double tap gets up to three tries.
            var engaged = false
            var capsGot = ""
            for (attempt in 1..3) {
                device.clear()
                device.keys["shift"]?.let { (x, y) -> device.doubleTap(x, y) }
                SystemClock.sleep(450)
                device.typeKeys("aa")
                val probe = device.text().trim()
                device.clear()
                if (probe == "AA") {
                    engaged = true
                    capsGot = swipe("hello", SwipeSynth.Noise.CLEAN).trim()
                    break
                }
                // Not locked: shift may be on once; a tap on shift and a clear field start the next try fresh
                device.tapKey("shift")
                SystemClock.sleep(300)
            }
            report.check("caps lock types a swiped word in capitals", engaged && capsGot == "HELLO",
                if (engaged) "got '$capsGot'" else "the double tap on shift never took")
            if (engaged) {
                device.tapKey("shift")
                SystemClock.sleep(300)
            }
        }

        section("Backspace after a swipe") {
            device.clear()
            swipe("hello")
            swipe("world")
            device.tapKey("delete")
            SystemClock.sleep(400)
            val got = device.text()
            report.check("one backspace deletes the whole swiped word", got.trimEnd() == "Hello", "got '$got'")
        }

        section("Choosing an alternative") {
            var done = 0
            for (word in listOf("for", "to", "in", "good", "were")) {
                device.clear()
                val first = swipe(word).trim()
                SystemClock.sleep(300)
                val alternative = device.alternatives(first).firstOrNull() ?: continue
                val (text, bounds) = alternative
                device.tap(bounds.exactCenterX(), bounds.exactCenterY())
                SystemClock.sleep(500)
                val got = device.text().trim()
                report.check("picking '$text' after swiping '$word' replaces '$first'", norm(got) == norm(text), "got '$got'")
                done++
                if (done >= 3) break
            }
            report.check("alternatives were offered", done > 0)
        }

        section("Cancelling a swipe") {
            device.clear()
            swipe("hello")
            val before = device.text()
            val keys = device.synthKeys()
            val partial = SwipeSynth.swipe("wor", keys, SwipeSynth.Noise.CLEAN, random)
            val n = partial.x.size
            val xs = partial.x + FloatArray(10) { partial.x.last() }
            val ys = partial.y + FloatArray(10) { i -> partial.y.last() - (i + 1) * (partial.y.last() - device.keyboardTop + 400) / 10 }
            val ts = partial.t + IntArray(10) { i -> partial.t.last() + (i + 1) * 15 }
            device.drag(xs, ys, ts)
            SystemClock.sleep(800)
            report.check("sliding off the top of the keys cancels the word", device.text() == before, "before '$before', after '${device.text()}' ($n points)")
        }

        section("Swipes that start on other keys") {
            for (start in listOf("space", "shift", "delete", "symbols", "enter")) {
                val from = device.keys[start] ?: continue
                device.clear()
                device.typeKeys("ok ")
                SystemClock.sleep(450)
                val path = SwipeSynth.swipe("hello", device.synthKeys(), SwipeSynth.Noise.CLEAN, random)
                val lead = 8
                val xs = FloatArray(lead) { i -> from.first + (path.x[0] - from.first) * i / lead } + path.x
                val ys = FloatArray(lead) { i -> from.second + (path.y[0] - from.second) * i / lead } + path.y
                val ts = IntArray(lead) { i -> i * 12 } + IntArray(path.t.size) { path.t[it] + lead * 12 }
                val before = device.text()
                device.drag(xs, ys, ts)
                device.awaitChange(before, 1500)
                val got = device.text()
                // Back to letters and a clean field state before judging the keys still work
                if ("hello" !in got.lowercase()) device.keys["symbols"]?.let { _ -> }
                device.clear()
                val alive = device.appPid() == startPid
                val after = swipe("test", SwipeSynth.Noise.CLEAN)
                report.check("starting on $start: '${got.trim()}', and the keys still work", alive && norm(after) == "test", "then 'test' gave '${after.trim()}'")
            }
        }

        section("Rotation") {
            val turned = device.rotate(landscape = true)
            report.check("keys laid out again in landscape", turned)
            if (turned) {
                var ok = 0
                val misses = ArrayList<String>()
                val list = words.sections["common"].orEmpty().shuffled(random).take(25)
                for (word in list) {
                    device.clear()
                    val got = swipe(word)
                    if (norm(got) == norm(word)) ok++ else misses.add("$word → ${got.trim().ifEmpty { "nothing" }}")
                }
                report.check("landscape words right at least 80%", ok >= 20, "$ok of ${list.size}; missed ${misses.joinToString(", ")}")
            }
            val back = device.rotate(landscape = false)
            report.check("keys laid out again in portrait", back)
            if (back) {
                device.clear()
                report.check("portrait works after turning back", norm(swipe("hello")) == "hello", "got '${device.text()}'")
            }
            device.unfreezeRotation()
        }

        report.check("Unicorn never restarted (no crash)", device.appPid() == startPid, "pid $startPid → ${device.appPid()}")
        report.line("- \"Isn't responding\" dialogs from other apps answered with Wait: ${device.systemDialogs}")
        report.line("- Long presses from emulator stalls (a digit or accent popup instead of the word), undone and tried once more: $popupRetries")

        report.line()
        report.line("#### Speed on the emulator")
        report.line()
        report.line("| Measure | Count | p50 | p95 | Target |")
        report.line("|---|---:|---:|---:|---|")
        report.line("| Final word decode, CPU time | ${finalsCpu.size} | ${pct(finalsCpu, 0.5)} ms | ${pct(finalsCpu, 0.95)} ms | under 100 ms |")
        report.line("| Live preview decode, CPU time | ${previewsCpu.size} | ${pct(previewsCpu, 0.5)} ms | ${pct(previewsCpu, 0.95)} ms | under 50 ms |")
        report.line("| Final word decode, wall clock | ${finals.size} | ${pct(finals, 0.5)} ms | ${pct(finals, 0.95)} ms | |")
        report.line("| Live preview decode, wall clock | ${previews.size} | ${pct(previews, 0.5)} ms | ${pct(previews, 0.95)} ms | |")
        report.line("| Finger up to word in the field | ${latencies.size} | ${pct(latencies.map { it.toInt() }, 0.5)} ms | ${pct(latencies.map { it.toInt() }, 0.95)} ms | |")
        report.line()
        report.line("CPU time is the decoding thread's own work. Wall clock adds time the emulator's CPU spent on anything else, " +
            "including waiting for the host computer.")
        report.check("final decode p95 under 100 ms (CPU time)", finalsCpu.isNotEmpty() && pct(finalsCpu, 0.95) < 100, "${pct(finalsCpu, 0.95)} ms")
        report.check("live preview p95 under 50 ms (CPU time)", previewsCpu.isNotEmpty() && pct(previewsCpu, 0.95) < 50, "${pct(previewsCpu, 0.95)} ms")
        report.line()
        report.line("${report.failures} failure(s)")
        return report.failures == 0
    }

    companion object {
        fun pct(values: List<Int>, p: Double): Int {
            if (values.isEmpty()) return -1
            val sorted = values.sorted()
            return sorted[((sorted.size - 1) * p).toInt()]
        }
    }
}
