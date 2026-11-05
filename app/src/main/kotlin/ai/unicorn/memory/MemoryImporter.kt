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

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence

data class ImportedMessage(val role: String, val text: String, val createdAt: Long)

data class ImportedConversation(
    val externalId: String?,
    val sourceId: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ImportedMessage>,
)

/**
 * Parses data exports of AI apps into conversations. Supported:
 *  - ChatGPT: Settings > Data controls > Export data (zip or `conversations.json`).
 *  - Claude: Settings > Privacy > Export data (zip or `conversations.json`).
 *  - Gemini: Google Takeout > My Activity > Gemini Apps, JSON format (`MyActivity.json`), HTML as a fallback.
 *  - Perplexity: a thread exported as Markdown, or any text/link shared to Unicorn.
 *
 * Large exports are streamed one conversation at a time, so memory use does not grow with the export size.
 */
@OptIn(ExperimentalSerializationApi::class)
object MemoryImporter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val PEEK_BYTES = 64 * 1024
    private const val SESSION_GAP_MS = 30 * 60 * 1000L

    private val UrlSources = listOf(
        Regex("""https?://(chatgpt\.com|chat\.openai\.com)\S*""") to CaptureSource.CHATGPT.id,
        Regex("""https?://claude\.ai\S*""") to CaptureSource.CLAUDE.id,
        Regex("""https?://(gemini\.google\.com|g\.co/gemini)\S*""") to CaptureSource.GEMINI.id,
        Regex("""https?://(www\.)?perplexity\.ai\S*""") to CaptureSource.PERPLEXITY.id,
        Regex("""https?://(grok\.com|x\.com/i/grok)\S*""") to CaptureSource.GROK.id,
    )

    /**
     * Lazily parses [input], which may be a zip archive, a JSON export, a Gemini HTML export or a Markdown thread.
     * The sequence must be consumed while [input] is open.
     */
    fun parse(fileName: String?, input: InputStream): Sequence<Pair<MemoryOrigin, ImportedConversation>> = sequence {
        val buffered = BufferedInputStream(input, PEEK_BYTES)
        buffered.mark(4)
        val magic = ByteArray(4).also { buffered.read(it) }
        buffered.reset()
        if (magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
            val zip = ZipInputStream(buffered)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                yieldAll(parseFile(entry.name, NonClosingInputStream(zip)))
            }
        } else {
            yieldAll(parseFile(fileName.orEmpty(), buffered))
        }
    }

    private fun parseFile(name: String, input: InputStream): Sequence<Pair<MemoryOrigin, ImportedConversation>> {
        val lower = name.lowercase()
        val buffered = BufferedInputStream(input, PEEK_BYTES)
        val head = peek(buffered)
        return when {
            lower.endsWith(".json") && head.contains("\"mapping\"") -> parseChatGpt(buffered).map { MemoryOrigin.CHATGPT_EXPORT to it }
            lower.endsWith(".json") && head.contains("\"chat_messages\"") -> parseClaude(buffered).map { MemoryOrigin.CLAUDE_EXPORT to it }
            lower.endsWith(".json") && head.contains("\"header\"") && head.contains("Gemini") ->
                parseGeminiActivity(buffered).map { MemoryOrigin.GEMINI_TAKEOUT to it }
            lower.endsWith(".html") && lower.contains("myactivity") && head.contains("Gemini") ->
                parseGeminiActivityHtml(buffered.readBytes().toString(Charsets.UTF_8)).asSequence().map { MemoryOrigin.GEMINI_TAKEOUT to it }
            lower.endsWith(".md") || lower.endsWith(".markdown") ->
                listOfNotNull(parseMarkdownThread(buffered.readBytes().toString(Charsets.UTF_8), name)).asSequence()
                    .map { MemoryOrigin.PERPLEXITY_EXPORT to it }
            else -> emptySequence()
        }
    }

    private fun peek(input: BufferedInputStream): String {
        input.mark(PEEK_BYTES)
        val bytes = ByteArray(PEEK_BYTES)
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
        }
        input.reset()
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    // ChatGPT ------------------------------------------------------------------------------------------------------

    @Serializable
    private class GptConversation(
        val title: String? = null,
        @SerialName("create_time") val createTime: Double? = null,
        @SerialName("update_time") val updateTime: Double? = null,
        val mapping: Map<String, GptNode> = emptyMap(),
        @SerialName("current_node") val currentNode: String? = null,
        @SerialName("conversation_id") val conversationId: String? = null,
        val id: String? = null,
    )

    @Serializable
    private class GptNode(val message: GptMessage? = null, val parent: String? = null)

    @Serializable
    private class GptMessage(
        val author: GptAuthor? = null,
        @SerialName("create_time") val createTime: Double? = null,
        val content: GptContent? = null,
        val metadata: GptMetadata? = null,
    )

    @Serializable
    private class GptAuthor(val role: String? = null)

    @Serializable
    private class GptContent(
        @SerialName("content_type") val contentType: String? = null,
        val parts: List<JsonElement>? = null,
    )

    @Serializable
    private class GptMetadata(
        @SerialName("is_visually_hidden_from_conversation") val hidden: Boolean? = null,
    )

    internal fun parseChatGpt(input: InputStream): Sequence<ImportedConversation> {
        return json.decodeToSequence(input, GptConversation.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
            .mapNotNull { conversation ->
                // The export stores a tree of edits/regenerations; follow the branch the user last saw.
                val branch = ArrayList<GptMessage>()
                var nodeId = conversation.currentNode
                val visited = HashSet<String>()
                while (nodeId != null && visited.add(nodeId)) {
                    val node = conversation.mapping[nodeId] ?: break
                    node.message?.let { branch.add(it) }
                    nodeId = node.parent
                }
                branch.reverse()
                val fallbackTime = ((conversation.createTime ?: 0.0) * 1000).toLong()
                val messages = branch.mapNotNull { message ->
                    val role = when (message.author?.role) {
                        "user" -> MemoryRole.USER
                        "assistant" -> MemoryRole.ASSISTANT
                        else -> return@mapNotNull null
                    }
                    if (message.metadata?.hidden == true) return@mapNotNull null
                    val type = message.content?.contentType
                    if (type != "text" && type != "multimodal_text") return@mapNotNull null
                    val text = message.content.parts.orEmpty()
                        .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                        .joinToString("\n")
                        .trim()
                    if (text.isEmpty()) return@mapNotNull null
                    ImportedMessage(role, text, message.createTime?.let { (it * 1000).toLong() } ?: fallbackTime)
                }
                if (messages.isEmpty()) return@mapNotNull null
                ImportedConversation(
                    externalId = conversation.conversationId ?: conversation.id,
                    sourceId = CaptureSource.CHATGPT.id,
                    title = conversation.title,
                    createdAt = fallbackTime.takeIf { it > 0 } ?: messages.first().createdAt,
                    updatedAt = conversation.updateTime?.let { (it * 1000).toLong() } ?: messages.last().createdAt,
                    messages = messages,
                )
            }
    }

    // Claude -------------------------------------------------------------------------------------------------------

    @Serializable
    private class ClaudeConversation(
        val uuid: String? = null,
        val name: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("chat_messages") val messages: List<ClaudeMessage> = emptyList(),
    )

    @Serializable
    private class ClaudeMessage(
        val text: String? = null,
        val sender: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        val content: List<ClaudeContent>? = null,
    )

    @Serializable
    private class ClaudeContent(val type: String? = null, val text: String? = null)

    internal fun parseClaude(input: InputStream): Sequence<ImportedConversation> {
        return json.decodeToSequence(input, ClaudeConversation.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
            .mapNotNull { conversation ->
                val created = parseIsoTime(conversation.createdAt) ?: 0L
                val messages = conversation.messages.mapNotNull { message ->
                    val role = when (message.sender) {
                        "human" -> MemoryRole.USER
                        "assistant" -> MemoryRole.ASSISTANT
                        else -> return@mapNotNull null
                    }
                    val fromContent = message.content.orEmpty()
                        .filter { it.type == "text" }
                        .mapNotNull { it.text }
                        .joinToString("\n")
                    val text = fromContent.ifBlank { message.text.orEmpty() }.trim()
                    if (text.isEmpty()) return@mapNotNull null
                    ImportedMessage(role, text, parseIsoTime(message.createdAt) ?: created)
                }
                if (messages.isEmpty()) return@mapNotNull null
                ImportedConversation(
                    externalId = conversation.uuid,
                    sourceId = CaptureSource.CLAUDE.id,
                    title = conversation.name?.takeIf { it.isNotBlank() },
                    createdAt = created.takeIf { it > 0 } ?: messages.first().createdAt,
                    updatedAt = parseIsoTime(conversation.updatedAt) ?: messages.last().createdAt,
                    messages = messages,
                )
            }
    }

    // Gemini (Google Takeout "My Activity") -----------------------------------------------------------------------

    @Serializable
    private class ActivityItem(
        val header: String? = null,
        val title: String? = null,
        val time: String? = null,
        val products: List<String>? = null,
        val safeHtmlItem: List<SafeHtml>? = null,
    )

    @Serializable
    private class SafeHtml(val html: String? = null)

    private val GeminiPromptPrefixes = listOf("Prompted ", "Prompted ", "Asked ", "Asked ")

    internal fun parseGeminiActivity(input: InputStream): Sequence<ImportedConversation> {
        val turns = json.decodeToSequence(input, ActivityItem.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
            .filter { item -> item.header?.contains("Gemini") == true || item.products.orEmpty().any { it.contains("Gemini") } }
            .mapNotNull { item ->
                val title = item.title ?: return@mapNotNull null
                val prefix = GeminiPromptPrefixes.firstOrNull { title.startsWith(it) } ?: return@mapNotNull null
                val prompt = title.removePrefix(prefix).trim()
                if (prompt.isEmpty()) return@mapNotNull null
                val time = parseIsoTime(item.time) ?: return@mapNotNull null
                val reply = item.safeHtmlItem.orEmpty().mapNotNull { it.html }.joinToString("\n") { htmlToText(it) }.trim()
                Triple(time, prompt, reply)
            }
            .toList()
        return groupGeminiTurns(turns).asSequence()
    }

    internal fun parseGeminiActivityHtml(html: String): List<ImportedConversation> {
        // Best effort: the HTML export has no machine readable timestamps, so turns keep their order only.
        val now = System.currentTimeMillis()
        val turns = html.split("outer-cell").mapIndexedNotNull { i, cell ->
            val text = htmlToText(cell)
            val line = text.lines().map { it.trim() }.firstOrNull { l -> GeminiPromptPrefixes.any { l.startsWith(it) } }
                ?: return@mapIndexedNotNull null
            val prefix = GeminiPromptPrefixes.first { line.startsWith(it) }
            Triple(now - i * 60_000L, line.removePrefix(prefix).trim(), "")
        }.filter { it.second.isNotEmpty() }
        return groupGeminiTurns(turns)
    }

    /** Takeout has no conversation ids, so turns closer than [SESSION_GAP_MS] form one conversation. */
    private fun groupGeminiTurns(turns: List<Triple<Long, String, String>>): List<ImportedConversation> {
        val sorted = turns.sortedBy { it.first }
        val groups = ArrayList<MutableList<Triple<Long, String, String>>>()
        for (turn in sorted) {
            val last = groups.lastOrNull()
            if (last != null && turn.first - last.last().first < SESSION_GAP_MS) last.add(turn) else groups.add(mutableListOf(turn))
        }
        return groups.map { group ->
            val messages = group.flatMap { (time, prompt, reply) ->
                listOfNotNull(
                    ImportedMessage(MemoryRole.USER, prompt, time),
                    reply.takeIf { it.isNotEmpty() }?.let { ImportedMessage(MemoryRole.ASSISTANT, it, time) },
                )
            }
            ImportedConversation(
                externalId = sha1("gemini:${group.first().first}:${group.first().second}"),
                sourceId = CaptureSource.GEMINI.id,
                title = group.first().second.take(120),
                createdAt = group.first().first,
                updatedAt = group.last().first,
                messages = messages,
            )
        }
    }

    // Perplexity / Markdown threads --------------------------------------------------------------------------------

    /**
     * Parses a thread exported as Markdown, where each question is a level 1 heading followed by the answer. Falls
     * back to storing the whole file as a single shared message.
     */
    internal fun parseMarkdownThread(markdown: String, name: String, now: Long = System.currentTimeMillis()): ImportedConversation? {
        val text = markdown.trim()
        if (text.isEmpty()) return null
        val messages = ArrayList<ImportedMessage>()
        var answer = StringBuilder()
        for (line in text.lines()) {
            if (line.startsWith("# ")) {
                if (messages.isNotEmpty() && answer.isNotBlank()) {
                    messages.add(ImportedMessage(MemoryRole.ASSISTANT, answer.toString().trim(), now))
                }
                answer = StringBuilder()
                messages.add(ImportedMessage(MemoryRole.USER, line.removePrefix("# ").trim(), now))
            } else if (messages.isNotEmpty()) {
                answer.appendLine(line)
            }
        }
        if (messages.isNotEmpty() && answer.isNotBlank()) {
            messages.add(ImportedMessage(MemoryRole.ASSISTANT, answer.toString().trim(), now))
        }
        if (messages.isEmpty()) messages.add(ImportedMessage(MemoryRole.SHARED, text, now))
        return ImportedConversation(
            externalId = sha1("md:$text"),
            sourceId = detectSource(text) ?: CaptureSource.PERPLEXITY.id,
            title = messages.first().text.take(120).ifBlank { name },
            createdAt = now,
            updatedAt = now,
            messages = messages,
        )
    }

    // Share sheet ---------------------------------------------------------------------------------------------------

    /** Builds a conversation from text shared to Unicorn. [senderSourceId] comes from the sharing app, if known. */
    fun fromSharedText(text: String, subject: String?, senderSourceId: String?, now: Long = System.currentTimeMillis()): ImportedConversation? {
        val body = text.trim()
        if (body.isEmpty()) return null
        val sourceId = senderSourceId ?: detectSource(body) ?: OTHER_SOURCE_ID
        return ImportedConversation(
            externalId = sha1("share:$body"),
            sourceId = sourceId,
            title = subject?.takeIf { it.isNotBlank() } ?: body.lineSequence().first().take(120),
            createdAt = now,
            updatedAt = now,
            messages = listOf(ImportedMessage(MemoryRole.SHARED, body, now)),
        )
    }

    fun detectSource(text: String): String? = UrlSources.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second

    // Helpers -------------------------------------------------------------------------------------------------------

    private fun parseIsoTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun htmlToText(html: String): String {
        return html
            .replace(Regex("(?i)<br\\s*/?>|</p>|</li>|</h\\d>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Lets a parser close its stream without closing the surrounding zip archive. */
    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }
}
