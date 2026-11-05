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

package ai.unicorn.memory

import android.content.Context
import android.os.SystemClock
import ai.unicorn.memory.vector.StaticEmbedder
import ai.unicorn.memory.vector.VectorIndex
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the on-device prompt memory: the Room database, the static embedding model and the in-memory vector index.
 *
 * All writes (database and index) run on a single serialized dispatcher, so the index always mirrors the database.
 * Recall reads the index directly from the IME thread; it never touches the database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryRepository(context: Context, private val databasePath: String) {
    companion object {
        const val MAX_INDEXED_PROMPTS = 50_000
        const val THREAD_GAP_MS = 30 * 60 * 1000L
        // potion-base-8M: paraphrases score ~0.55-0.9, same-topic near misses ~0.5-0.67, unrelated prompts ~0
        const val RECALL_MIN_SCORE = 0.55f
        const val SEARCH_MIN_SCORE = 0.3f
        private const val KEYWORD_CANDIDATES = 400
        private val WhitespaceRegex = Regex("\\s+")
        private const val RECALL_CACHE_SIZE = 64
        private const val LATENCY_SAMPLES = 256
        private const val MODEL_DIR = "unicorn/model2vec"
        private val UrlRegex = Regex("""(https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    }

    enum class State { LOADING, READY, FAILED }

    data class RecallHit(
        val messageId: Long,
        val text: String,
        val sourceId: String,
        val createdAt: Long,
        val score: Float,
    )

    data class ImportResult(val conversations: Int, val messages: Int, val skipped: Int)

    /** Recall latency (embedding + vector search) measured on this device, in microseconds. */
    data class LatencyStats(val samples: Int, val p50Micros: Double, val p95Micros: Double, val indexedPrompts: Int)

    /** [key] is the normalized text, used for the recall cache and to skip the prompt being typed. */
    private class PromptMeta(val text: String, val key: String, val sourceId: String, val createdAt: Long)

    private val appContext = context.applicationContext
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(writer + SupervisorJob())
    private val database by lazy { MemoryDatabase.new(appContext, databasePath) }
    private val dao get() = database.promptMemoryDao()

    @Volatile
    private var embedder: StaticEmbedder? = null
    @Volatile
    private var index: VectorIndex? = null
    private val prompts = ConcurrentHashMap<Long, PromptMeta>()

    private val recallCache = object : LinkedHashMap<String, Pair<Long, List<RecallHit>>>(RECALL_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<RecallHit>>>?): Boolean {
            return size > RECALL_CACHE_SIZE
        }
    }
    private val latencies = LongArray(LATENCY_SAMPLES)
    private var latencyCount = 0

    private val _state = MutableStateFlow(State.LOADING)
    val state = _state.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    /** Incremented after every change to the memory content. */
    val revision = _revision.asStateFlow()

    init {
        scope.launch { warmUp() }
    }

    private fun warmUp() {
        try {
            val model = appContext.assets.open("$MODEL_DIR/embeddings.bin").use { embeddings ->
                appContext.assets.open("$MODEL_DIR/vocab.txt").use { vocab -> StaticEmbedder.load(embeddings, vocab) }
            }
            val newIndex = VectorIndex(model.dim)
            database.runInTransaction {
                for (row in dao.getPromptsForIndex(MAX_INDEXED_PROMPTS)) {
                    val quantized = row.embedding?.let { decodeEmbedding(it, model.dim) }
                        ?: model.encode(embeddingText(row.text))?.let { newIndex.quantize(it) }?.also { dao.setEmbedding(row.id, encodeEmbedding(it)) }
                        ?: continue
                    newIndex.upsert(row.id, quantized)
                    prompts[row.id] = PromptMeta(row.text, normalizeKey(row.text), row.sourceId, row.createdAt)
                }
            }
            embedder = model
            index = newIndex
            _state.value = State.READY
        } catch (e: Exception) {
            Log.e("UnicornMemory", "Failed to load prompt memory", e)
            _state.value = State.FAILED
        }
    }

    /** Stores a prompt captured by the keyboard, threading it with the previous prompt from the same app. */
    fun addKeyboardPrompt(source: CaptureSource, text: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            val latest = dao.latestKeyboardConversation(source.id)
            val conversationId = if (latest != null && now - latest.updatedAt < THREAD_GAP_MS) {
                dao.touchConversation(latest.id, now)
                latest.id
            } else {
                dao.insertConversation(
                    MemoryConversation(
                        sourceId = source.id,
                        origin = MemoryOrigin.KEYBOARD.id,
                        title = text.take(120),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
            val quantized = quantize(text)
            val id = dao.insertMessage(
                MemoryMessage(
                    conversationId = conversationId,
                    role = MemoryRole.USER,
                    text = text,
                    createdAt = now,
                    embedding = quantized?.let { encodeEmbedding(it) },
                )
            )
            if (quantized != null) addToIndex(id, quantized, text, source.id, now)
            _revision.value++
        }
    }

    /** Imports conversations from an export, skipping conversations that were imported before. */
    suspend fun importConversations(origin: MemoryOrigin, conversations: List<ImportedConversation>): ImportResult {
        return withContext(writer) {
            var imported = 0
            var messages = 0
            var skipped = 0
            val toIndex = ArrayList<() -> Unit>()
            // One transaction per batch: per-row commits make large imports orders of magnitude slower
            database.runInTransaction {
                for (conversation in conversations) {
                    if (conversation.messages.isEmpty()) continue
                    val externalId = conversation.externalId
                    if (externalId != null && dao.findConversation(origin.id, externalId) != null) {
                        skipped++
                        continue
                    }
                    val vectors = conversation.messages.map { if (it.role != MemoryRole.ASSISTANT) quantize(it.text) else null }
                    val rows = conversation.messages.mapIndexed { i, message ->
                        MemoryMessage(
                            conversationId = 0,
                            role = message.role,
                            text = message.text,
                            createdAt = message.createdAt,
                            embedding = vectors[i]?.let { encodeEmbedding(it) },
                        )
                    }
                    val ids = dao.insertConversationWithMessages(
                        MemoryConversation(
                            sourceId = conversation.sourceId,
                            origin = origin.id,
                            externalId = externalId,
                            title = conversation.title,
                            createdAt = conversation.createdAt,
                            updatedAt = conversation.updatedAt,
                        ),
                        rows,
                    )
                    ids.forEachIndexed { i, id ->
                        val quantized = vectors[i] ?: return@forEachIndexed
                        val message = conversation.messages[i]
                        toIndex.add { addToIndex(id, quantized, message.text, conversation.sourceId, message.createdAt) }
                    }
                    imported++
                    messages += ids.size
                }
            }
            toIndex.forEach { it() } // only after the transaction committed
            _revision.value++
            ImportResult(imported, messages, skipped)
        }
    }

    /** Returns null before warm-up finished; warm-up embeds every prompt stored without an embedding. */
    private fun quantize(text: String): VectorIndex.Quantized? {
        val model = embedder ?: return null
        val currentIndex = index ?: return null
        return model.encode(embeddingText(text))?.let { currentIndex.quantize(it) }
    }

    private fun addToIndex(id: Long, quantized: VectorIndex.Quantized, text: String, sourceId: String, createdAt: Long) {
        prompts[id] = PromptMeta(text, normalizeKey(text), sourceId, createdAt)
        index?.upsert(id, quantized)
    }

    /**
     * Returns up to [maxCount] past prompts semantically similar to [text], best first. Safe to call on the IME
     * thread on every keystroke: it costs one static embedding plus one index scan, and repeated queries are served
     * from an LRU cache.
     */
    fun recall(text: String, maxCount: Int = 2): List<RecallHit> {
        val model = embedder ?: return emptyList()
        val currentIndex = index ?: return emptyList()
        val key = normalizeKey(text)
        if (key.length < 8) return emptyList()
        synchronized(recallCache) {
            recallCache[key]?.let { (version, hits) -> if (version == currentIndex.version) return hits }
        }
        val start = SystemClock.elapsedRealtimeNanos()
        val vector = model.encode(embeddingText(key)) ?: return emptyList()
        val hits = currentIndex.search(vector, maxCount + 4, RECALL_MIN_SCORE)
        recordLatency(SystemClock.elapsedRealtimeNanos() - start)
        val result = ArrayList<RecallHit>(maxCount)
        val seen = HashSet<String>()
        for (hit in hits) {
            val meta = prompts[hit.id] ?: continue
            if (meta.key == key || !seen.add(meta.key)) continue
            result.add(RecallHit(hit.id, meta.text, meta.sourceId, meta.createdAt, hit.score))
            if (result.size == maxCount) break
        }
        synchronized(recallCache) { recallCache[key] = currentIndex.version to result }
        return result
    }

    /**
     * Library search: messages containing every word of the query (questions and answers) come first, then prompts
     * similar in meaning. One result per conversation, so a long chat doesn't fill the list.
     */
    suspend fun search(query: String, maxCount: Int = 50): List<SearchResult> = withContext(Dispatchers.IO) {
        val words = query.lowercase().split(WhitespaceRegex).filter { it.length >= 2 }.distinct().take(6)
        val keyword = if (words.isEmpty()) emptyList() else {
            val selective = words.maxBy { it.length }
            dao.searchText("%" + escapeLike(selective) + "%", KEYWORD_CANDIDATES)
                .filter { row -> row.text.lowercase().let { text -> words.all { it in text } } }
                .map { SearchResult(it, 1f, SearchResult.Match.WORDS, snippet(it.text, selective)) }
        }
        val model = embedder
        val currentIndex = index
        val vector = model?.encode(embeddingText(query))
        val semantic = if (vector == null || currentIndex == null) emptyList() else {
            val hits = currentIndex.search(vector, maxCount, SEARCH_MIN_SCORE)
            val rows = dao.getMessageRows(hits.map { it.id }).associateBy { it.id }
            hits.mapNotNull { hit -> rows[hit.id]?.let { SearchResult(it, hit.score, SearchResult.Match.MEANING, it.text) } }
        }
        (keyword + semantic).distinctBy { it.row.conversationId }.take(maxCount)
    }

    fun embed(text: String): FloatArray? = embedder?.encode(embeddingText(text))

    suspend fun <T> read(block: MemoryDao.() -> T): T = withContext(Dispatchers.IO) { dao.block() }

    fun setContributeConsent(messageId: Long, consent: Boolean) {
        scope.launch {
            dao.setContributeConsent(messageId, consent)
            _revision.value++
        }
    }

    fun deleteMessage(messageId: Long) {
        scope.launch {
            dao.deleteMessage(messageId)
            dao.deleteEmptyConversations()
            unindex(listOf(messageId))
            _revision.value++
        }
    }

    fun deleteConversation(conversationId: Long) {
        scope.launch {
            val ids = dao.getMessageIds(conversationId)
            dao.deleteConversation(conversationId)
            unindex(ids)
            _revision.value++
        }
    }

    fun deleteAll() {
        scope.launch {
            dao.deleteAll()
            index?.clear()
            prompts.clear()
            synchronized(recallCache) { recallCache.clear() }
            _revision.value++
        }
    }

    private fun unindex(ids: List<Long>) {
        for (id in ids) {
            index?.remove(id)
            prompts.remove(id)
        }
    }

    fun latencyStats(): LatencyStats {
        val sorted = synchronized(latencies) { latencies.copyOf(minOf(latencyCount, LATENCY_SAMPLES)) }.sorted()
        fun percentile(p: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p).toInt()] / 1000.0
        return LatencyStats(sorted.size, percentile(0.5), percentile(0.95), index?.size ?: 0)
    }

    /** Runs [rounds] recall queries over random indexed prompts to measure latency without typing. */
    suspend fun runLatencyBenchmark(rounds: Int = 200): LatencyStats = withContext(Dispatchers.Default) {
        val samples = prompts.values.map { it.text }.shuffled().take(rounds)
        if (samples.isNotEmpty()) {
            synchronized(recallCache) { recallCache.clear() }
            repeat(rounds) { recall(samples[it % samples.size] + " ") }
        }
        latencyStats()
    }

    private fun recordLatency(nanos: Long) {
        synchronized(latencies) {
            latencies[latencyCount % LATENCY_SAMPLES] = nanos
            latencyCount++
        }
    }

    /**
     * Text that gets embedded: links are removed because their tokens (https, com, share, ...) dilute the meaning of
     * short prompts, e.g. answers shared from AI apps that end with a share link. Stored text keeps the links.
     */
    private fun embeddingText(text: String): String {
        if (!text.contains("://") && !text.contains("www.")) return text
        return text.replace(UrlRegex, " ")
    }

    /** Lowercases and collapses whitespace without regex, as this runs on every keystroke. */
    private fun normalizeKey(text: String): String {
        val sb = StringBuilder(text.length)
        var pendingSpace = false
        for (c in text) {
            if (c.isWhitespace()) {
                pendingSpace = sb.isNotEmpty()
            } else {
                if (pendingSpace) sb.append(' ')
                pendingSpace = false
                sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }

    private fun encodeEmbedding(quantized: VectorIndex.Quantized): ByteArray {
        val buffer = ByteBuffer.allocate(quantized.values.size + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(quantized.values)
        buffer.putFloat(quantized.scale)
        return buffer.array()
    }

    private fun decodeEmbedding(bytes: ByteArray, dim: Int): VectorIndex.Quantized? {
        if (bytes.size != dim + 4) return null // model changed, re-embed
        val scale = ByteBuffer.wrap(bytes, dim, 4).order(ByteOrder.LITTLE_ENDIAN).float
        return VectorIndex.Quantized(bytes.copyOf(dim), scale)
    }
}

/** One Library search result. [snippet] starts near the matched words when they are deep inside a long message. */
data class SearchResult(val row: MemoryMessageRow, val score: Float, val match: Match, val snippet: String) {
    enum class Match { WORDS, MEANING }
}

private fun escapeLike(value: String) = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private fun snippet(text: String, word: String): String {
    val at = text.lowercase().indexOf(word)
    if (at <= SNIPPET_LEAD_CHARS) return text
    val start = text.lastIndexOf(' ', at - SNIPPET_LEAD_CHARS / 2).let { if (it < 0) at - SNIPPET_LEAD_CHARS / 2 else it + 1 }
    return "…" + text.substring(start)
}

private const val SNIPPET_LEAD_CHARS = 48
