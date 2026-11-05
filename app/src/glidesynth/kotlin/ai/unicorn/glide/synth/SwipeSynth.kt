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

package ai.unicorn.glide.synth

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthetic swipes for testing glide typing without a person: a finger path through a word's keys with the mistakes
 * people make, in millimetres so it is equally sloppy on every screen. Shared by the JVM accuracy tests and the
 * emulator end-to-end test (glidetest/), which injects the same paths as real touches.
 */
object SwipeSynth {

    /** One swipe: points in pixels and times in milliseconds from the first touch. */
    class Swipe(val x: FloatArray, val y: FloatArray, val t: IntArray) {
        val size get() = x.size
    }

    /**
     * How sloppy the finger is. Offsets are Gaussian standard deviations in millimetres; a phone key is about 7 mm
     * wide and 10 mm tall in portrait.
     */
    class Noise(
        val name: String,
        /** Where the finger passes each letter key, around its center. */
        val targetMm: Float,
        /** The first and last key: people start and lift less precisely. */
        val endMm: Float,
        /**
         * Up to this far (mm), a middle letter is cut toward the inside of the turn, most at a right-angle turn; people
         * still reach the key where they reverse direction.
         */
        val cornerCutMm: Float,
        /** Chance of carrying on past a sharp turn before turning, and by up to how far. */
        val overshootChance: Float,
        val overshootMm: Float,
        /** Rounding of the whole path (moving average over this length). */
        val smoothMm: Float,
        /** Touch sensor noise on each sample. */
        val jitterMm: Float,
        /** Chance of a small wiggle on a double letter ("hello"); otherwise people just pass through once. */
        val doubleWiggleChance: Float,
        /** Swipe speed varies between these multiples of a typical speed. */
        val slowest: Float,
        val fastest: Float,
    ) {
        companion object {
            /** No noise at all: the ideal path through the key centers. */
            val CLEAN = Noise("clean", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f)

            /** A typical person swiping at a normal pace. */
            val REALISTIC = Noise("realistic", 1.6f, 2.0f, 2.5f, 0.3f, 2.5f, 3f, 0.25f, 0.35f, 0.7f, 1.5f)

            /** Fast and careless: well past what a careful user does, for headroom. */
            val SLOPPY = Noise("sloppy", 2.4f, 2.8f, 4f, 0.5f, 4f, 4f, 0.4f, 0.5f, 0.5f, 2.2f)
        }
    }

    /**
     * A screen's letter keys: centers in pixels by key (0 = a ... 25 = z, NaN when absent) and pixels per millimetre.
     */
    class Keys(val name: String, val centerX: FloatArray, val centerY: FloatArray, val pxPerMm: Float) {
        fun center(c: Char): Pair<Float, Float>? {
            val k = keyOf(c)
            if (k < 0 || centerX[k].isNaN()) return null
            return centerX[k] to centerY[k]
        }

        companion object {
            private const val ROW1 = "qwertyuiop"
            private const val ROW2 = "asdfghjkl"
            private const val ROW3 = "zxcvbnm"

            /**
             * The engine's QWERTY layout (res/xml/rows_qwerty.xml): ten keys a row, the second row inset half a key,
             * the third a key and a half behind the shift key, on a screen [widthPx] wide with rows [rowPitchPx] apart,
             * the first row's centers at [row1Y].
             */
            fun qwerty(name: String, widthPx: Int, rowPitchPx: Float, row1Y: Float, pxPerMm: Float): Keys {
                val cx = FloatArray(26) { Float.NaN }
                val cy = FloatArray(26) { Float.NaN }
                val pitch = widthPx / 10f
                ROW1.forEachIndexed { i, c -> cx[c - 'a'] = (i + 0.5f) * pitch; cy[c - 'a'] = row1Y }
                ROW2.forEachIndexed { i, c -> cx[c - 'a'] = (i + 1f) * pitch; cy[c - 'a'] = row1Y + rowPitchPx }
                ROW3.forEachIndexed { i, c -> cx[c - 'a'] = (i + 2f) * pitch; cy[c - 'a'] = row1Y + 2 * rowPitchPx }
                return Keys(name, cx, cy, pxPerMm)
            }

            /** A 6.4" phone at 1080 x 2400, like the emulator and most Galaxy S and A phones. */
            val PHONE_FHD = qwerty("1080x2400 portrait", 1080, 164f, 1686f, 15.4f)
            /** A budget phone at 720 x 1600, same physical size. */
            val PHONE_HD = qwerty("720x1600 portrait", 720, 109f, 1124f, 10.3f)
            /** The FHD phone turned sideways: keys twice as wide and a little shorter. */
            val PHONE_LANDSCAPE = qwerty("2400x1080 landscape", 2400, 104f, 700f, 15.4f)

            val ALL = listOf(PHONE_FHD, PHONE_HD, PHONE_LANDSCAPE)

            fun keyOf(c: Char): Int {
                val l = when (c) {
                    in 'A'..'Z' -> c + 32
                    'à', 'á', 'â', 'ã', 'ä', 'å' -> 'a'
                    'ç' -> 'c'
                    'è', 'é', 'ê', 'ë' -> 'e'
                    'ì', 'í', 'î', 'ï' -> 'i'
                    'ñ' -> 'n'
                    'ò', 'ó', 'ô', 'õ', 'ö' -> 'o'
                    'ù', 'ú', 'û', 'ü' -> 'u'
                    'ý', 'ÿ' -> 'y'
                    else -> c
                }
                return if (l in 'a'..'z') l - 'a' else -1
            }
        }
    }

    private class P(var x: Float, var y: Float)

    /** A swipe for [word] on [keys] with [noise]. Letters without a key (apostrophes, hyphens) are skipped. */
    fun swipe(word: String, keys: Keys, noise: Noise, random: Random): Swipe {
        val mm = keys.pxPerMm
        // Targets: one per key, double letters merged (and flagged for a possible wiggle)
        val targets = ArrayList<P>()
        val doubled = ArrayList<Boolean>()
        var last = -1
        for (c in word) {
            val k = Keys.keyOf(c)
            if (k < 0 || keys.centerX[k].isNaN()) continue
            if (k == last) {
                doubled[doubled.size - 1] = true
                continue
            }
            targets.add(P(keys.centerX[k], keys.centerY[k]))
            doubled.add(false)
            last = k
        }
        require(targets.isNotEmpty()) { "No keys for '$word'" }
        val n = targets.size
        for (i in 0 until n) {
            val sigma = (if (i == 0 || i == n - 1) noise.endMm else noise.targetMm) * mm
            targets[i].x += gaussian(random) * sigma
            targets[i].y += gaussian(random) * sigma
        }
        // Corner cutting: middle letters drift toward the inside of the turn, most at right angles
        if (noise.cornerCutMm > 0f && n >= 3) {
            val original = targets.map { P(it.x, it.y) }
            for (i in 1 until n - 1) {
                val turn = angleBetween(
                    original[i].x - original[i - 1].x, original[i].y - original[i - 1].y,
                    original[i + 1].x - original[i].x, original[i + 1].y - original[i].y,
                )
                val mx = (original[i - 1].x + original[i + 1].x) / 2 - original[i].x
                val my = (original[i - 1].y + original[i + 1].y) / 2 - original[i].y
                val toMid = hypot(mx, my)
                if (toMid <= 0f) continue
                val cut = min(toMid, random.nextFloat() * noise.cornerCutMm * mm * sin(turn))
                targets[i].x += mx / toMid * cut
                targets[i].y += my / toMid * cut
            }
        }
        // Overshoot: past a sharp turn before turning
        if (noise.overshootChance > 0f) {
            for (i in 1 until n - 1) {
                val ax = targets[i].x - targets[i - 1].x
                val ay = targets[i].y - targets[i - 1].y
                val bx = targets[i + 1].x - targets[i].x
                val by = targets[i + 1].y - targets[i].y
                val turn = angleBetween(ax, ay, bx, by)
                if (turn > PI / 3 && random.nextFloat() < noise.overshootChance) {
                    val len = hypot(ax, ay)
                    if (len > 0f) {
                        val o = random.nextFloat() * noise.overshootMm * mm
                        targets[i].x += ax / len * o
                        targets[i].y += ay / len * o
                    }
                }
            }
        }
        // The polyline, with wiggles on double letters
        val line = ArrayList<P>()
        for (i in 0 until n) {
            line.add(targets[i])
            if (doubled[i] && random.nextFloat() < noise.doubleWiggleChance) {
                val r = (0.8f + random.nextFloat()) * mm
                val start = random.nextFloat() * 2 * PI.toFloat()
                for (s in 1..6) {
                    val a = start + s * (2 * PI.toFloat() / 6)
                    line.add(P(targets[i].x + r * cos(a) - r * cos(start), targets[i].y + r * sin(a) - r * sin(start)))
                }
                line.add(P(targets[i].x, targets[i].y))
            }
        }
        if (line.size == 1) line.add(P(line[0].x + 0.5f * mm, line[0].y))
        // Dense path every 0.5 mm, then rounded
        val dense = densify(line, 0.5f * mm)
        val smooth = smooth(dense, (noise.smoothMm * mm / (0.5f * mm)).toInt())
        // Timing: slower near turns, a random overall speed, sampled at 120 Hz like a phone's touch screen
        val lengthMm = pathLength(smooth) / mm
        val speed = noise.slowest + random.nextFloat() * (noise.fastest - noise.slowest)
        val duration = (160f + 9f * lengthMm) / speed
        val weights = FloatArray(smooth.size)
        for (i in 1 until smooth.size) {
            val prev = smooth[i - 1]
            val cur = smooth[i]
            val next = smooth[min(i + 1, smooth.size - 1)]
            val turn = if (i + 1 < smooth.size) angleBetween(cur.x - prev.x, cur.y - prev.y, next.x - cur.x, next.y - cur.y) else 0f
            weights[i] = hypot(cur.x - prev.x, cur.y - prev.y) * (1f + 6f * turn)
        }
        val total = weights.sum().coerceAtLeast(1e-3f)
        val times = FloatArray(smooth.size)
        for (i in 1 until smooth.size) times[i] = times[i - 1] + weights[i] / total * duration
        val samples = max(2, (duration / SAMPLE_MS).toInt() + 1)
        val xs = FloatArray(samples)
        val ys = FloatArray(samples)
        val ts = IntArray(samples)
        var j = 0
        for (s in 0 until samples) {
            val t = min(s * SAMPLE_MS, times.last())
            while (j < smooth.size - 2 && times[j + 1] < t) j++
            val span = times[j + 1] - times[j]
            val f = if (span > 0f) ((t - times[j]) / span).coerceIn(0f, 1f) else 0f
            val jitter = noise.jitterMm * mm
            xs[s] = smooth[j].x + f * (smooth[j + 1].x - smooth[j].x) + gaussian(random) * jitter
            ys[s] = smooth[j].y + f * (smooth[j + 1].y - smooth[j].y) + gaussian(random) * jitter
            ts[s] = t.toInt()
        }
        return Swipe(xs, ys, ts)
    }

    private const val SAMPLE_MS = 1000f / 120f

    private fun gaussian(random: Random): Float {
        // Box-Muller
        val u = random.nextDouble().coerceAtLeast(1e-12)
        val v = random.nextDouble()
        return (sqrt(-2.0 * kotlin.math.ln(u)) * cos(2 * PI * v)).toFloat()
    }

    private fun angleBetween(ax: Float, ay: Float, bx: Float, by: Float): Float {
        if ((ax == 0f && ay == 0f) || (bx == 0f && by == 0f)) return 0f
        var d = atan2(by, bx) - atan2(ay, ax)
        while (d > PI) d -= (2 * PI).toFloat()
        while (d < -PI) d += (2 * PI).toFloat()
        return kotlin.math.abs(d)
    }

    private fun densify(line: List<P>, step: Float): List<P> {
        val out = ArrayList<P>()
        out.add(P(line[0].x, line[0].y))
        for (i in 1 until line.size) {
            val a = line[i - 1]
            val b = line[i]
            val d = hypot(b.x - a.x, b.y - a.y)
            val parts = max(1, (d / step).toInt())
            for (s in 1..parts) {
                val f = s.toFloat() / parts
                out.add(P(a.x + f * (b.x - a.x), a.y + f * (b.y - a.y)))
            }
        }
        return out
    }

    private fun smooth(points: List<P>, window: Int): List<P> {
        if (window < 2 || points.size < 3) return points
        val half = window / 2
        return points.indices.map { i ->
            // Ends stay put: people start and lift where they mean to
            val h = min(half, min(i, points.size - 1 - i))
            var sx = 0f
            var sy = 0f
            for (k in i - h..i + h) {
                sx += points[k].x
                sy += points[k].y
            }
            P(sx / (2 * h + 1), sy / (2 * h + 1))
        }
    }

    private fun pathLength(points: List<P>): Float {
        var length = 0f
        for (i in 1 until points.size) length += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        return length
    }
}
