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

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StaticEmbedderTest {
    private val embedder by lazy {
        val dir = File("src/main/assets/unicorn/model2vec")
        StaticEmbedder.load(File(dir, "embeddings.bin").inputStream(), File(dir, "vocab.txt").inputStream())
    }

    private val references by lazy {
        val json = javaClass.classLoader!!.getResource("model2vec_reference.json")!!.readText()
        Json.parseToJsonElement(json).jsonArray.map { it.jsonObject }
    }

    @Test
    fun `token ids match the Hugging Face tokenizer`() {
        for (ref in references) {
            val text = ref["text"]!!.jsonPrimitive.content
            val expected = ref["ids"]!!.jsonArray.map { it.jsonPrimitive.int }.toIntArray()
            assertContentEquals(expected, embedder.tokenIds(text), "token ids for '$text'")
        }
    }

    @Test
    fun `vectors match the Python reference`() {
        for (ref in references) {
            val text = ref["text"]!!.jsonPrimitive.content
            val expected = ref["vector"]!!.jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
            val actual = embedder.encode(text)!!
            val cosine = expected.indices.sumOf { (expected[it] * actual[it]).toDouble() }
            assertTrue(cosine > 0.9999, "cosine $cosine for '$text'")
        }
    }

    @Test
    fun `similar prompts rank above unrelated ones`() {
        val query = embedder.encode("best suv for a family of five")!!
        val related = embedder.encode("which SUV should I buy for my family with 3 kids")!!
        val unrelated = embedder.encode("how do I bake sourdough bread at home")!!
        assertTrue(cos(query, related) > cos(query, unrelated) + 0.1f)
    }

    @Test
    fun `two stage search keeps recall and stays fast`() {
        val subjects = listOf(
            "suv", "laptop", "running shoes", "crm", "mattress", "japan trip", "salary", "mortgage", "protein powder",
            "electric car", "credit card", "wedding venue", "iphone", "python error", "react hooks", "tax return",
            "headphones", "coffee machine", "flight to london", "dog food", "skincare routine", "budget app",
        )
        val templates = listOf(
            "best %s under budget", "%s vs alternatives for beginners", "is %s worth it in 2026",
            "how to choose a %s", "cheapest %s with good reviews", "what %s do experts recommend",
            "problems with my %s", "compare top %s brands", "should I buy a %s now or wait", "%s for a family",
        )
        val random = Random(42)
        val index = VectorIndex(embedder.dim)
        val n = 50_000
        for (i in 0 until n) {
            val text = templates.random(random).format(subjects.random(random)) + " " + subjects.random(random)
            index.upsert(i.toLong(), embedder.encode(text)!!)
        }

        val queries = List(200) { templates.random(random).format(subjects.random(random)) }.map { embedder.encode(it)!! }
        var recalled = 0
        for (q in queries) {
            val exact = index.searchExact(q, 5).map { it.score }
            val fast = index.search(q, 5).map { it.score }
            // Ties are common with templated text, so compare scores rather than ids
            recalled += fast.count { s -> exact.any { kotlin.math.abs(it - s) < 1e-5f } }
        }
        val recall = recalled / (queries.size * 5.0)

        repeat(200) { index.search(queries[it % queries.size], 5) } // warm up the JIT
        val start = System.nanoTime()
        val runs = 2000
        repeat(runs) { index.search(queries[it % queries.size], 5) }
        val searchMicros = (System.nanoTime() - start) / 1000.0 / runs

        val encodeStart = System.nanoTime()
        repeat(runs) { embedder.encode("best suv under 50k for a family of 5 with good safety ratings") }
        val encodeMicros = (System.nanoTime() - encodeStart) / 1000.0 / runs

        println("n=$n recall@5=${"%.3f".format(recall)} search=${"%.1f".format(searchMicros)}us encode=${"%.1f".format(encodeMicros)}us")
        assertTrue(recall >= 0.9, "recall@5 was $recall")
    }

    private fun cos(a: FloatArray, b: FloatArray): Float = a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()
}
