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

package ai.unicorn.ai

import ai.unicorn.UnicornPrefs
import ai.unicorn.keyboard.AppKind
import ai.unicorn.keyboard.AskCommand
import ai.unicorn.keyboard.WriteContext
import com.android.inputmethod.latin.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The writing tools offered in the keyboard. */
enum class AiTask(val id: String, val title: String) {
    GRAMMAR("grammar", "Fix grammar"),
    TONE("tone", "Change tone"),
    TRANSLATE("translate", "Translate"),
    COMPOSE("compose", "Ask Unicorn"),
}

enum class AiTone(val id: String, val label: String, val emoji: String) {
    REWRITE("rewrite", "Rewrite", "✍️"),
    PROFESSIONAL("professional", "Professional", "💼"),
    FRIENDLY("friendly", "Friendly", "😊"),
    CASUAL("casual", "Casual", "😎"),
    CONFIDENT("confident", "Confident", "💪"),
    SHORTER("shorter", "Shorter", "✂️"),
    FUNNY("funny", "Funny", "😂"),
    POLITE("polite", "Polite", "🙏"),
}

/**
 * Client for Unicorn's backend (a Cloudflare Worker that calls Claude). The app never holds an AI provider key; the
 * worker authenticates the install with a random id and applies per-install rate limits.
 */
class AiClient(private val prefs: UnicornPrefs, private val baseUrl: String = BuildConfig.UNICORN_API_URL) {
    @Serializable
    private data class AiRequest(
        val task: String,
        val text: String,
        val tone: String? = null,
        val language: String? = null,
    )

    @Serializable
    private data class WriteRequest(
        val task: String,
        val text: String,
        val command: String,
        val draft: String? = null,
        val thread: String? = null,
        val app: String,
        val field: String? = null,
    )

    @Serializable
    private data class AiResponse(val results: List<String> = emptyList(), val error: String? = null, val quota: Quota? = null)

    @Serializable
    private data class Quota(val resetsAt: String? = null)

    class AiException(message: String) : IOException(message)

    /**
     * The free daily AI is used up (or Unicorn's budget for the day is). Nothing is shown to the user: the AI tools
     * step aside until [prefs].aiPausedUntil and the keys work as usual.
     */
    class AiPaused : IOException("paused")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun run(task: AiTask, text: String, tone: AiTone? = null, language: String? = null): List<String> {
        val body = json.encodeToString(AiRequest.serializer(), AiRequest(task.id, text.take(4000), tone?.id, language))
        val response = post("/v1/ai", body)
        val parsed = json.decodeFromString(AiResponse.serializer(), response)
        parsed.error?.let { throw AiException(it) }
        return parsed.results.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Ask Unicorn's prompt bar: [draft] is what the user typed in the app, [thread] what they copied. */
    suspend fun write(
        command: AskCommand,
        instruction: String,
        draft: String?,
        thread: String?,
        context: WriteContext,
    ): List<String> {
        val request = WriteRequest(
            task = "write",
            text = instruction.ifBlank { command.hint }.take(4000),
            command = command.id,
            draft = draft?.takeIf { it.isNotBlank() }?.take(4000),
            thread = thread?.takeIf { it.isNotBlank() }?.take(8000),
            app = context.app.id,
            field = if (context.app == AppKind.EMAIL) (if (context.isSubject) "subject" else "body") else null,
        )
        val response = post("/v1/ai", json.encodeToString(WriteRequest.serializer(), request))
        val parsed = json.decodeFromString(AiResponse.serializer(), response)
        parsed.error?.let { throw AiException(it) }
        return parsed.results.map { it.trim() }.filter { it.isNotEmpty() }
    }

    internal suspend fun post(path: String, body: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Unicorn-Install", prefs.installId)
            setRequestProperty("X-Unicorn-Version", BuildConfig.VERSION_NAME)
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 429 || (code == 503 && connection.getHeaderField("Retry-After") != null) -> {
                    prefs.aiPausedUntil = pausedUntil(code, text, connection.getHeaderField("Retry-After"))
                    throw AiPaused()
                }
                code !in 200..299 -> throw AiException(
                    runCatching { json.decodeFromString(AiResponse.serializer(), text).error }.getOrNull()
                        ?: "Unicorn AI is unavailable right now ($code)."
                )
                else -> text
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun pausedUntil(code: Int, body: String, retryAfter: String?): Long {
        val now = System.currentTimeMillis()
        if (code == 503) return now + (retryAfter?.toLongOrNull() ?: 1800L) * 1000
        val resetsAt = runCatching { json.decodeFromString(AiResponse.serializer(), body).quota?.resetsAt }.getOrNull()
        return resetsAt?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: (now + 6 * 60 * 60 * 1000L)
    }

    internal suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("X-Unicorn-Install", prefs.installId)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw AiException("Request failed ($code).")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
