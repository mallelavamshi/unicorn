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

package ai.unicorn.memory.vector

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * In-memory vector index tuned for a single user's prompt memory (thousands to ~100k vectors).
 *
 * Every vector is kept twice in flat primitive arrays: as a 1-bit-per-dimension sign code and as int8 with a
 * per-vector scale. A search first ranks all vectors by Hamming distance of the sign codes (a few XOR + popcount
 * per vector), then reranks the best [rerankDepth] candidates with the int8 dot product. At personal scale this
 * beats graph indexes like HNSW: no build cost, cheap inserts and deletes, and a scan of 50k vectors stays well
 * under a millisecond.
 */
class VectorIndex(val dim: Int, private val rerankDepth: Int = 64) {
    init {
        require(dim > 0 && dim % 64 == 0) { "dim must be a positive multiple of 64" }
    }

    data class Hit(val id: Long, val score: Float)

    /** Quantized form of a vector, which is also what gets persisted. */
    class Quantized(val values: ByteArray, val scale: Float)

    private val words = dim / 64
    private var capacity = 256
    private var ids = LongArray(capacity)
    private var codes = LongArray(capacity * words)
    private var values = ByteArray(capacity * dim)
    private var scales = FloatArray(capacity)
    private val slotOf = HashMap<Long, Int>()
    private var distances = IntArray(capacity)

    var size = 0
        private set

    /** Incremented on every mutation, used to invalidate result caches. */
    @Volatile
    var version = 0L
        private set

    @Synchronized
    fun upsert(id: Long, quantized: Quantized) {
        require(quantized.values.size == dim)
        val slot = slotOf[id] ?: run {
            ensureCapacity(size + 1)
            val newSlot = size++
            slotOf[id] = newSlot
            ids[newSlot] = id
            newSlot
        }
        System.arraycopy(quantized.values, 0, values, slot * dim, dim)
        scales[slot] = quantized.scale
        writeCode(quantized.values, codes, slot * words)
        version++
    }

    fun upsert(id: Long, vector: FloatArray) = upsert(id, quantize(vector))

    @Synchronized
    fun remove(id: Long) {
        val slot = slotOf.remove(id) ?: return
        val last = --size
        if (slot != last) {
            val movedId = ids[last]
            ids[slot] = movedId
            System.arraycopy(values, last * dim, values, slot * dim, dim)
            System.arraycopy(codes, last * words, codes, slot * words, words)
            scales[slot] = scales[last]
            slotOf[movedId] = slot
        }
        version++
    }

    @Synchronized
    fun clear() {
        slotOf.clear()
        size = 0
        version++
    }

    @Synchronized
    fun contains(id: Long): Boolean = slotOf.containsKey(id)

    /**
     * Returns up to [k] hits with cosine similarity of at least [minScore], best first. [query] must be
     * L2-normalized. [accept] can exclude ids (e.g. the entry currently being typed).
     */
    @Synchronized
    fun search(query: FloatArray, k: Int, minScore: Float = -1f, accept: ((Long) -> Boolean)? = null): List<Hit> {
        require(query.size == dim)
        if (size == 0 || k <= 0) return emptyList()

        val candidates = if (size <= rerankDepth) {
            IntArray(size) { it }
        } else {
            hammingCandidates(query, rerankDepth)
        }

        val bestSlots = IntArray(k) { -1 }
        val bestScores = FloatArray(k) { Float.NEGATIVE_INFINITY }
        for (slot in candidates) {
            if (accept != null && !accept(ids[slot])) continue
            val score = dot(query, slot)
            if (score < minScore || score <= bestScores[k - 1]) continue
            var pos = k - 1
            while (pos > 0 && bestScores[pos - 1] < score) {
                bestScores[pos] = bestScores[pos - 1]
                bestSlots[pos] = bestSlots[pos - 1]
                pos--
            }
            bestScores[pos] = score
            bestSlots[pos] = slot
        }
        return buildList {
            for (i in 0 until k) {
                if (bestSlots[i] < 0) break
                add(Hit(ids[bestSlots[i]], bestScores[i]))
            }
        }
    }

    /** Exact search over every vector, for tests and benchmarks of the two-stage search. */
    @Synchronized
    fun searchExact(query: FloatArray, k: Int): List<Hit> {
        return (0 until size).map { Hit(ids[it], dot(query, it)) }.sortedByDescending { it.score }.take(k)
    }

    private fun hammingCandidates(query: FloatArray, depth: Int): IntArray {
        val queryCode = LongArray(words)
        for (d in 0 until dim) {
            if (query[d] > 0f) queryCode[d ushr 6] = queryCode[d ushr 6] or (1L shl (d and 63))
        }
        if (distances.size < size) distances = IntArray(capacity)
        val histogram = IntArray(dim + 1)
        val codes = codes
        val distances = distances
        if (words == 4) {
            // Unrolled hot path for 256-dim models
            val q0 = queryCode[0]; val q1 = queryCode[1]; val q2 = queryCode[2]; val q3 = queryCode[3]
            var base = 0
            for (slot in 0 until size) {
                val dist = java.lang.Long.bitCount(codes[base] xor q0) +
                    java.lang.Long.bitCount(codes[base + 1] xor q1) +
                    java.lang.Long.bitCount(codes[base + 2] xor q2) +
                    java.lang.Long.bitCount(codes[base + 3] xor q3)
                distances[slot] = dist
                histogram[dist]++
                base += 4
            }
        } else {
            for (slot in 0 until size) {
                var dist = 0
                val base = slot * words
                for (w in 0 until words) {
                    dist += java.lang.Long.bitCount(codes[base + w] xor queryCode[w])
                }
                distances[slot] = dist
                histogram[dist]++
            }
        }
        // Smallest distance threshold that admits at least `depth` candidates
        var threshold = 0
        var cumulative = 0
        while (threshold <= dim) {
            cumulative += histogram[threshold]
            if (cumulative >= depth) break
            threshold++
        }
        val belowThreshold = cumulative - histogram[threshold.coerceAtMost(dim)]
        var tieBudget = depth - belowThreshold
        val out = IntArray(depth)
        var n = 0
        for (slot in 0 until size) {
            val dist = distances[slot]
            if (dist < threshold || (dist == threshold && tieBudget-- > 0)) {
                out[n++] = slot
                if (n == depth) break
            }
        }
        return if (n == depth) out else out.copyOf(n)
    }

    private fun dot(query: FloatArray, slot: Int): Float {
        var acc = 0f
        val base = slot * dim
        for (d in 0 until dim) {
            acc += query[d] * values[base + d]
        }
        return acc * scales[slot]
    }

    private fun ensureCapacity(required: Int) {
        if (required <= capacity) return
        var newCapacity = capacity
        while (newCapacity < required) newCapacity *= 2
        ids = ids.copyOf(newCapacity)
        codes = codes.copyOf(newCapacity * words)
        values = values.copyOf(newCapacity * dim)
        scales = scales.copyOf(newCapacity)
        capacity = newCapacity
    }

    private fun writeCode(q: ByteArray, target: LongArray, offset: Int) {
        for (w in 0 until words) target[offset + w] = 0L
        for (d in 0 until dim) {
            if (q[d] > 0) target[offset + (d ushr 6)] = target[offset + (d ushr 6)] or (1L shl (d and 63))
        }
    }

    fun quantize(vector: FloatArray): Quantized {
        require(vector.size == dim)
        var maxAbs = 0f
        for (v in vector) maxAbs = maxOf(maxAbs, abs(v))
        val scale = if (maxAbs > 0f) maxAbs / 127f else 1f
        val q = ByteArray(dim) { d -> (vector[d] / scale).roundToInt().coerceIn(-127, 127).toByte() }
        return Quantized(q, scale)
    }
}
