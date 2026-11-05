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

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How much each piece of evidence counts, in nats. Tuned by GlideTuning on words the accuracy tests don't use. */
data class GlideWeights(
    /** Per mean squared distance (key units) between the swipe and the word's ideal path, point by point. */
    val location: Float = 2.5f,
    /** The same after scaling both to a unit box: the word's shape wherever it was drawn (SHARK2's shape channel). */
    val shape: Float = 2.8f,
    /** The same after aligning the two paths elastically (dynamic time warping), forgiving cut corners and overshoot. */
    val warp: Float = 33f,
    /** Per mean squared distance of the word's keys to the nearest points of the swipe, keys in order. */
    val visit: Float = 4.2f,
    /** Per squared distance of the first and last key to where the swipe started and ended. */
    val ends: Float = 2.5f,
    /** Per step of the dictionary frequency (0 to 255). */
    val unigram: Float = 0.062f,
    /** Taken off abbreviations ("CPAs"), which the dictionary has many of and swipes rarely mean. */
    val abbreviation: Float = 1f,
    /** Times the next-word boost (nats) after the previous word. */
    val nextWord: Float = 1.0f,
    /** How far from where the swipe starts and ends a word's first and last key may be, in key units. */
    val endRadius: Float = 1.3f,
)

/**
 * Glide typing decoder: matches a swipe against the ideal path of each candidate word over the real key centers, the
 * shape-and-location template matching of SHARK2 (Kristensson and Zhai, "SHARK2: A Large Vocabulary Shorthand Writing
 * System for Pen-based Computers", UIST 2004), and weighs it with how common the word is, the previous word and the
 * user's own words.
 *
 * Candidates are words whose first and last keys are near where the swipe started and ended, and whose path length is
 * plausible. A cheap location match ranks them all; the best [SHORTLIST] are then scored on location, shape, an
 * elastic alignment and whether every key was visited in order. Not thread-safe: one decoder per thread.
 */
class GlideDecoder(private val lexicon: GlideLexicon, var weights: GlideWeights = GlideWeights()) {

    class Candidate(val word: String, val score: Float, val fromUserWords: Boolean)

    /** Boost in nats for a word the user uses or added. [userWords] is true for ids of [Context.userWords]. */
    fun interface Learned {
        fun boost(userWords: Boolean, id: Int): Float
    }

    /** Raw evidence for each shortlisted word, for tuning [weights] offline (GlideTuning). */
    fun interface Features {
        fun candidate(
            word: String, location: Float, shape: Float, warp: Float, visit: Float, ends: Float,
            frequency: Int, next: Float, learned: Float,
        )
    }

    var features: Features? = null

    /** Words scanned and words whose path was compared in stage 1 of the last decode, for profiling. */
    var scanned = 0
        private set
    var compared = 0
        private set
    /** Time spent in stage 1 and stage 2 of the last decode, in microseconds, for profiling. */
    var stage1Micros = 0L
        private set
    var stage2Micros = 0L
        private set

    class Context(
        val next: NextWords.Context? = null,
        val blockOffensive: Boolean = true,
        /** The user's own words the dictionary doesn't have (names, slang), decoded like the dictionary's. */
        val userWords: GlideLexicon? = null,
        val learned: Learned? = null,
        /** The most [learned] adds to any word, for skipping hopeless candidates early. */
        val learnedMax: Float = 0f,
    )

    private var inX = FloatArray(512)
    private var inY = FloatArray(512)
    private val q1x = FloatArray(N1)
    private val q1y = FloatArray(N1)
    private val q2x = FloatArray(N2)
    private val q2y = FloatArray(N2)
    private val s2x = FloatArray(N2)
    private val s2y = FloatArray(N2)
    private val tx = FloatArray(N2)
    private val ty = FloatArray(N2)
    private val ux = FloatArray(N2)
    private val uy = FloatArray(N2)
    private val dtwPrev = FloatArray(N2)
    private val dtwCur = FloatArray(N2)
    private val visitMin = FloatArray(N2)
    private val visitCur = FloatArray(N2)
    private val heapScore = FloatArray(SHORTLIST)
    private val heapId = IntArray(SHORTLIST)
    private var heapSize = 0
    private var heapCap = SHORTLIST
    private val startKeys = IntArray(GlideLexicon.KEYS)
    private val endKeys = IntArray(GlideLexicon.KEYS)
    private val pairCost = FloatArray(GlideLexicon.KEYS * GlideLexicon.KEYS)
    private val pairKeys = IntArray(GlideLexicon.KEYS * GlideLexicon.KEYS)

    private var lengthSignature = 0L
    private var mainLengths = FloatArray(0)
    private var userLengths = FloatArray(0)
    private var userLengthsFor: GlideLexicon? = null

    /**
     * The best words for a swipe through ([xs], [ys]) (pixels, the first [count] points), best first. Empty when the
     * swipe is too short to be a word.
     */
    fun decode(
        xs: FloatArray, ys: FloatArray, count: Int, geometry: KeyGeometry, context: Context, limit: Int,
        shortlist: Int = SHORTLIST,
    ): List<Candidate> {
        val w = weights
        heapCap = shortlist.coerceIn(1, SHORTLIST)
        // Input in key units, without repeated points
        if (inX.size < count) {
            inX = FloatArray(count)
            inY = FloatArray(count)
        }
        // A swipe that starts or ends on another key (space, shift, delete, a number) is read from the letters on
        var first = 0
        while (first < count && !nearLetters(geometry, xs[first] / geometry.pitchX, ys[first] / geometry.pitchY)) first++
        var last = count - 1
        while (last > first && !nearLetters(geometry, xs[last] / geometry.pitchX, ys[last] / geometry.pitchY)) last--
        // Points closer than a sixth of a key to the last one kept are dropped, as the engine does when it records a
        // swipe: touch jitter would otherwise add length and wiggles
        var n = 0
        for (i in first..last) {
            if (i >= count) break
            val x = xs[i] / geometry.pitchX
            val y = ys[i] / geometry.pitchY
            if (n > 0 && i != last && squared(x - inX[n - 1], y - inY[n - 1]) < MIN_STEP * MIN_STEP) continue
            if (n > 0 && x == inX[n - 1] && y == inY[n - 1]) continue
            inX[n] = x
            inY[n] = y
            n++
        }
        if (n < 2) return emptyList()
        val length = polylineLength(inX, inY, n)
        if (length < MIN_LENGTH) return emptyList()
        resample(inX, inY, n, length, N1, q1x, q1y)
        resample(inX, inY, n, length, N2, q2x, q2y)
        normalizeShape(q2x, q2y, s2x, s2y)
        val shapeFactor = ((length - 1f) / 3f).coerceIn(0f, 1f)

        ensureLengths(geometry, context.userWords)
        val starts = keysNear(geometry, q1x[0], q1y[0], w.endRadius, startKeys)
        val ends = keysNear(geometry, q1x[N1 - 1], q1y[N1 - 1], w.endRadius, endKeys)
        // Synthetic swipes need word paths from 0.6 to 1.6 times the swipe's length (realistic noise 0.75 to 1.46)
        val minLength = length * 0.55f - 0.8f
        val maxLength = length * 1.7f + 0.8f

        // Stage 1: location only, every candidate. The first and last key fix a bucket's end cost, so buckets go
        // nearest first, and a word whose end cost and prior can't beat the shortlist is skipped (location is never
        // negative); buckets are sorted by frequency, so the rest of a bucket is skipped with it.
        var pairs = 0
        for (si in 0 until starts) for (ei in 0 until ends) {
            val s = startKeys[si]
            val e = endKeys[ei]
            pairCost[pairs] = squared(q1x[0] - geometry.x[s], q1y[0] - geometry.y[s]) +
                squared(q1x[N1 - 1] - geometry.x[e], q1y[N1 - 1] - geometry.y[e])
            pairKeys[pairs] = s * GlideLexicon.KEYS + e
            pairs++
        }
        val pairOrder = (0 until pairs).sortedBy { pairCost[it] }
        val extraMax = w.nextWord * (context.next?.maxBoost ?: 0f) + context.learnedMax
        val stage1Start = System.nanoTime()
        heapSize = 0
        scanned = 0
        compared = 0
        for (lex in 0..1) {
            val words = if (lex == 0) lexicon else context.userWords ?: continue
            val lengths = if (lex == 0) mainLengths else userLengths
            for (pi in pairOrder) {
                val s = pairKeys[pi] / GlideLexicon.KEYS
                val e = pairKeys[pi] % GlideLexicon.KEYS
                val endScore = -w.ends * pairCost[pi]
                for (b in words.bucketFrom(s, e) until words.bucketTo(s, e)) {
                    val id = words.bucketWord(b)
                    scanned++
                    val full = heapSize == heapCap
                    if (full && endScore + w.unigram * words.frequency(id) + extraMax <= heapScore[0]) break
                    val templateLength = lengths[id]
                    if (templateLength < minLength || templateLength > maxLength) continue
                    if (context.blockOffensive && words.isOffensive(id)) continue
                    val upper = endScore + prior(words, lex, id, context)
                    if (full && upper <= heapScore[0]) continue
                    compared++
                    val budget = if (full) (upper - heapScore[0]) * N1 / w.location else Float.MAX_VALUE
                    val distance = locationDistance(words, id, geometry, templateLength, budget)
                    if (distance < budget) offer(upper - w.location * distance / N1, (lex shl 24) or id)
                }
            }
        }

        val stage2Start = System.nanoTime()
        stage1Micros = (stage2Start - stage1Start) / 1000
        // Stage 2: the shortlist, on every channel
        val results = ArrayList<Candidate>(heapSize)
        val scores = FloatArray(heapSize)
        for (h in 0 until heapSize) {
            val lex = heapId[h] ushr 24
            val id = heapId[h] and 0xFFFFFF
            val words = if (lex == 0) lexicon else context.userWords!!
            val templateLength = if (lex == 0) mainLengths[id] else userLengths[id]
            template(words, id, geometry, templateLength, N2, tx, ty)
            val location = meanSquared(q2x, q2y, tx, ty, N2)
            normalizeShape(tx, ty, ux, uy)
            val shape = meanSquared(s2x, s2y, ux, uy, N2)
            val warp = dtw(q2x, q2y, tx, ty)
            val visit = visitCost(words, id, geometry)
            val endCost = squared(q2x[0] - tx[0], q2y[0] - ty[0]) +
                squared(q2x[N2 - 1] - tx[N2 - 1], q2y[N2 - 1] - ty[N2 - 1])
            val gesture = w.location * location + w.shape * shapeFactor * shape + w.warp * warp +
                w.visit * visit + w.ends * endCost
            scores[h] = -gesture + prior(words, lex, id, context)
            results.add(Candidate(words.word(id), scores[h], lex == 1))
            features?.candidate(
                results.last().word, location, shapeFactor * shape, warp, visit, endCost, words.frequency(id),
                if (lex == 0) context.next?.boost(words.group(id)) ?: 0f else 0f,
                context.learned?.boost(lex == 1, id) ?: 0f,
            )
        }
        stage2Micros = (System.nanoTime() - stage2Start) / 1000
        val order = results.indices.sortedByDescending { scores[it] }
        val seen = HashSet<String>()
        val out = ArrayList<Candidate>(limit)
        for (i in order) {
            if (out.size >= limit) break
            if (seen.add(results[i].word)) out.add(results[i])
        }
        return out
    }

    private fun prior(words: GlideLexicon, lex: Int, id: Int, context: Context): Float {
        val w = weights
        var p = w.unigram * words.frequency(id)
        if (words.isAbbreviation(id)) p -= w.abbreviation
        if (lex == 0) context.next?.let { p += w.nextWord * it.boost(words.group(id)) }
        context.learned?.let { p += it.boost(lex == 1, id) }
        return p
    }

    private fun offer(score: Float, id: Int) {
        if (heapSize < heapCap) {
            var i = heapSize++
            heapScore[i] = score
            heapId[i] = id
            while (i > 0) {
                val parent = (i - 1) / 2
                if (heapScore[parent] <= heapScore[i]) break
                swap(i, parent)
                i = parent
            }
        } else if (score > heapScore[0]) {
            heapScore[0] = score
            heapId[0] = id
            var i = 0
            while (true) {
                val l = 2 * i + 1
                val r = l + 1
                var m = i
                if (l < heapSize && heapScore[l] < heapScore[m]) m = l
                if (r < heapSize && heapScore[r] < heapScore[m]) m = r
                if (m == i) break
                swap(i, m)
                i = m
            }
        }
    }

    private fun swap(a: Int, b: Int) {
        val s = heapScore[a]; heapScore[a] = heapScore[b]; heapScore[b] = s
        val d = heapId[a]; heapId[a] = heapId[b]; heapId[b] = d
    }

    private fun ensureLengths(geometry: KeyGeometry, userWords: GlideLexicon?) {
        if (lengthSignature != geometry.signature || mainLengths.size != lexicon.size) {
            mainLengths = lengths(lexicon, geometry)
            lengthSignature = geometry.signature
            userLengthsFor = null
        }
        if (userWords != null && userLengthsFor !== userWords) {
            userLengths = FloatArray(userWords.size) { pathLength(userWords, it, geometry) }
            userLengthsFor = userWords
        }
    }

    /**
     * Sum of squared distances between the swipe and the word's ideal path at [N1] points (the stage 1 location
     * cost), computed point by point and given up as soon as it passes [budget].
     */
    private fun locationDistance(words: GlideLexicon, id: Int, geometry: KeyGeometry, length: Float, budget: Float): Float {
        val start = words.pathStart(id)
        val end = words.pathEnd(id)
        val gx = geometry.x
        val gy = geometry.y
        val first = words.pathKey(start)
        val last = words.pathKey(end - 1)
        var sum = squared(q1x[0] - gx[first], q1y[0] - gy[first]) + squared(q1x[N1 - 1] - gx[last], q1y[N1 - 1] - gy[last])
        if (sum >= budget) return sum
        var filled = 1
        if (length > 0f) {
            val step = length / (N1 - 1)
            var target = step
            var travelled = 0f
            for (p in start + 1 until end) {
                val a = words.pathKey(p - 1)
                val b = words.pathKey(p)
                val dx = gx[b] - gx[a]
                val dy = gy[b] - gy[a]
                val d = sqrt(dx * dx + dy * dy)
                while (filled < N1 - 1 && target <= travelled + d) {
                    val t = (target - travelled) / d
                    sum += squared(q1x[filled] - (gx[a] + t * dx), q1y[filled] - (gy[a] + t * dy))
                    if (sum >= budget) return sum
                    filled++
                    target += step
                }
                travelled += d
            }
        }
        while (filled < N1 - 1) {
            sum += squared(q1x[filled] - gx[last], q1y[filled] - gy[last])
            filled++
        }
        return sum
    }

    private fun nearLetters(geometry: KeyGeometry, x: Float, y: Float): Boolean {
        for (k in 0 until GlideLexicon.KEYS) {
            if (geometry.has(k) && squared(geometry.x[k] - x, geometry.y[k] - y) <= LETTER_AREA * LETTER_AREA) return true
        }
        return false
    }

    /** Letter keys within [radius] of a point, nearest key always included. */
    private fun keysNear(geometry: KeyGeometry, x: Float, y: Float, radius: Float, out: IntArray): Int {
        var n = 0
        var nearest = -1
        var nearestDistance = Float.MAX_VALUE
        val r2 = radius * radius
        for (k in 0 until GlideLexicon.KEYS) {
            if (!geometry.has(k)) continue
            val d = squared(geometry.x[k] - x, geometry.y[k] - y)
            if (d < nearestDistance) { nearestDistance = d; nearest = k }
            if (d <= r2) out[n++] = k
        }
        if (n == 0 && nearest >= 0) out[n++] = nearest
        return n
    }

    private fun visitCost(words: GlideLexicon, id: Int, geometry: KeyGeometry): Float {
        // Each key of the word, in order, aligned to a point of the swipe at or after the previous key's point
        val start = words.pathStart(id)
        val end = words.pathEnd(id)
        for (p in start until end) {
            val k = words.pathKey(p)
            val kx = geometry.x[k]
            val ky = geometry.y[k]
            var running = Float.MAX_VALUE
            for (i in 0 until N2) {
                val cost = squared(q2x[i] - kx, q2y[i] - ky) + if (p == start) 0f else visitMin[i]
                running = min(running, cost)
                visitCur[i] = running
            }
            visitCur.copyInto(visitMin)
        }
        return visitMin[N2 - 1] / (end - start)
    }

    /**
     * Elastic alignment cost of the swipe and a word's path: dynamic time warping over every [DTW_STRIDE]th of the
     * [N2] points, within a band, as the mean squared distance per point.
     */
    private fun dtw(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray): Float {
        val inf = Float.MAX_VALUE / 4
        val n = DTW_POINTS
        dtwPrev.fill(inf, 0, n)
        for (i in 0 until n) {
            val xi = ax[i * DTW_STRIDE]
            val yi = ay[i * DTW_STRIDE]
            val lo = max(0, i - BAND)
            val hi = min(n - 1, i + BAND)
            var left = inf
            for (j in lo..hi) {
                val d = squared(xi - bx[j * DTW_STRIDE], yi - by[j * DTW_STRIDE])
                val best = if (i == 0) {
                    if (j == 0) 0f else left
                } else {
                    val up = dtwPrev[j]
                    val diagonal = if (j > 0) dtwPrev[j - 1] else inf
                    min(diagonal, min(up, left))
                }
                left = d + best
                dtwCur[j] = left
            }
            // Cells outside the band stay unreachable
            if (lo > 0) dtwCur[lo - 1] = inf
            if (hi < n - 1) dtwCur[hi + 1] = inf
            dtwCur.copyInto(dtwPrev, lo, lo, hi + 1)
            if (lo > 0) dtwPrev[lo - 1] = inf
        }
        return dtwPrev[n - 1] / n
    }

    companion object {
        private const val N1 = 16
        private const val N2 = 48
        /** Alignment on every point: every second point cost 0.7 points of top-1 accuracy for little speed. */
        private const val DTW_STRIDE = 1
        private const val DTW_POINTS = N2 / DTW_STRIDE
        private const val BAND = 8
        const val SHORTLIST = 50
        private const val LENGTH_CACHE = 3
        private val lengthCache = LinkedHashMap<Long, Pair<GlideLexicon, FloatArray>>()

        /**
         * Every word's path length on a layout, shared by all decoders. A few layouts (portrait, landscape) take
         * turns, and each costs 165,000 lengths, so [prepare] works them out before the first swipe.
         */
        fun lengths(lexicon: GlideLexicon, geometry: KeyGeometry): FloatArray = synchronized(lengthCache) {
            lengthCache[geometry.signature]?.takeIf { it.first === lexicon }?.second
        } ?: FloatArray(lexicon.size) { pathLength(lexicon, it, geometry) }.also { computed ->
            synchronized(lengthCache) {
                lengthCache[geometry.signature] = lexicon to computed
                while (lengthCache.size > LENGTH_CACHE) lengthCache.remove(lengthCache.keys.first())
            }
        }

        /** Sets up a layout ahead of the first swipe on it (any thread). */
        fun prepare(lexicon: GlideLexicon, geometry: KeyGeometry) {
            lengths(lexicon, geometry)
        }
        /** Same as the engine's gestureSamplingMinimumDistance, in key units. */
        private const val MIN_STEP = 1f / 6f
        /** Points farther than this (key units) from every letter key are on another key. */
        private const val LETTER_AREA = 0.8f
        /** Shorter swipes (key units) are not words. */
        private const val MIN_LENGTH = 0.25f

        private fun squared(dx: Float, dy: Float) = dx * dx + dy * dy

        private fun meanSquared(ax: FloatArray, ay: FloatArray, bx: FloatArray, by: FloatArray, n: Int): Float {
            var sum = 0f
            for (i in 0 until n) sum += squared(ax[i] - bx[i], ay[i] - by[i])
            return sum / n
        }

        private fun polylineLength(x: FloatArray, y: FloatArray, n: Int): Float {
            var length = 0f
            for (i in 1 until n) length += sqrt(squared(x[i] - x[i - 1], y[i] - y[i - 1]))
            return length
        }

        fun pathLength(words: GlideLexicon, id: Int, geometry: KeyGeometry): Float {
            var length = 0f
            for (p in words.pathStart(id) until words.pathEnd(id) - 1) {
                val a = words.pathKey(p)
                val b = words.pathKey(p + 1)
                length += sqrt(squared(geometry.x[b] - geometry.x[a], geometry.y[b] - geometry.y[a]))
            }
            return length
        }

        /** [n] points evenly spaced along a polyline of [length]. */
        private fun resample(x: FloatArray, y: FloatArray, count: Int, length: Float, n: Int, outX: FloatArray, outY: FloatArray) {
            val step = length / (n - 1)
            outX[0] = x[0]
            outY[0] = y[0]
            var filled = 1
            var target = step
            var travelled = 0f
            for (i in 1 until count) {
                val d = sqrt(squared(x[i] - x[i - 1], y[i] - y[i - 1]))
                while (filled < n - 1 && target <= travelled + d) {
                    val t = if (d > 0f) (target - travelled) / d else 0f
                    outX[filled] = x[i - 1] + t * (x[i] - x[i - 1])
                    outY[filled] = y[i - 1] + t * (y[i] - y[i - 1])
                    filled++
                    target += step
                }
                travelled += d
            }
            while (filled < n) {
                outX[filled] = x[count - 1]
                outY[filled] = y[count - 1]
                filled++
            }
        }

        /** The ideal path of a word: [n] points evenly spaced along the lines between its keys' centers. */
        private fun template(
            words: GlideLexicon, id: Int, geometry: KeyGeometry, length: Float, n: Int, outX: FloatArray, outY: FloatArray,
        ) {
            val start = words.pathStart(id)
            val end = words.pathEnd(id)
            val gx = geometry.x
            val gy = geometry.y
            val first = words.pathKey(start)
            outX[0] = gx[first]
            outY[0] = gy[first]
            var filled = 1
            if (length > 0f) {
                val step = length / (n - 1)
                var target = step
                var travelled = 0f
                for (p in start + 1 until end) {
                    val a = words.pathKey(p - 1)
                    val b = words.pathKey(p)
                    val dx = gx[b] - gx[a]
                    val dy = gy[b] - gy[a]
                    val d = sqrt(dx * dx + dy * dy)
                    while (filled < n - 1 && target <= travelled + d) {
                        val t = (target - travelled) / d
                        outX[filled] = gx[a] + t * dx
                        outY[filled] = gy[a] + t * dy
                        filled++
                        target += step
                    }
                    travelled += d
                }
            }
            val last = words.pathKey(end - 1)
            while (filled < n) {
                outX[filled] = gx[last]
                outY[filled] = gy[last]
                filled++
            }
            outX[n - 1] = gx[last]
            outY[n - 1] = gy[last]
        }

        /** Centroid at the origin, larger side of the bounding box 1. */
        private fun normalizeShape(x: FloatArray, y: FloatArray, outX: FloatArray, outY: FloatArray) {
            val n = x.size
            var cx = 0f
            var cy = 0f
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (i in 0 until n) {
                cx += x[i]; cy += y[i]
                minX = min(minX, x[i]); maxX = max(maxX, x[i])
                minY = min(minY, y[i]); maxY = max(maxY, y[i])
            }
            cx /= n
            cy /= n
            val scale = max(max(maxX - minX, maxY - minY), 0.5f)
            for (i in 0 until n) {
                outX[i] = (x[i] - cx) / scale
                outY[i] = (y[i] - cy) / scale
            }
        }
    }
}
