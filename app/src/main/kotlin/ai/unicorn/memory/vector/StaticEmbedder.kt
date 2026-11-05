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

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Model2Vec static embedding model: a sentence embedding is the L2-normalized mean of its token embeddings, so
 * encoding a prompt costs a tokenizer pass plus a few hundred additions (tens of microseconds) instead of a
 * transformer forward pass.
 *
 * The model is swappable: any Model2Vec model converted with `convert_model2vec.py` (asset files `embeddings.bin`
 * and `vocab.txt`) can be dropped in without code changes.
 */
class StaticEmbedder private constructor(
    private val tokenizer: WordPieceTokenizer,
    private val scales: FloatArray,
    private val weights: ByteArray,
    val dim: Int,
) {
    companion object {
        private const val MAGIC = 0x3156324D // "M2V1" little endian

        fun load(embeddings: InputStream, vocab: InputStream): StaticEmbedder {
            val tokens = vocab.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.filter { it.isNotEmpty() }.toList() }
            val bytes = DataInputStream(embeddings.buffered()).use { it.readBytes() }
            val header = ByteBuffer.wrap(bytes, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
            require(header.int == MAGIC) { "Not a Model2Vec asset" }
            val vocabSize = header.int
            val dim = header.int
            require(vocabSize == tokens.size) { "Vocab size mismatch: $vocabSize vs ${tokens.size}" }
            val scales = FloatArray(vocabSize)
            ByteBuffer.wrap(bytes, 12, vocabSize * 4).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(scales)
            val weightsOffset = 12 + vocabSize * 4
            val weights = bytes.copyOfRange(weightsOffset, weightsOffset + vocabSize * dim)
            return StaticEmbedder(WordPieceTokenizer(tokens), scales, weights, dim)
        }
    }

    /** Encodes [text] into [out] (size [dim]) and returns false if the text has no known tokens. */
    fun encodeInto(text: String, out: FloatArray): Boolean {
        out.fill(0f)
        val ids = tokenizer.encode(text)
        if (ids.isEmpty()) return false
        for (id in ids) {
            val scale = scales[id]
            val base = id * dim
            for (d in 0 until dim) {
                out[d] += weights[base + d] * scale
            }
        }
        var norm = 0f
        for (d in 0 until dim) norm += out[d] * out[d]
        norm = sqrt(norm)
        if (norm < 1e-12f) return false
        for (d in 0 until dim) out[d] /= norm
        return true
    }

    fun encode(text: String): FloatArray? {
        val out = FloatArray(dim)
        return if (encodeInto(text, out)) out else null
    }

    internal fun tokenIds(text: String): IntArray = tokenizer.encode(text)
}
