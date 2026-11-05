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

package ai.unicorn.trends

import ai.unicorn.UnicornCore
import ai.unicorn.memory.MemoryInsightsEngine
import ai.unicorn.memory.MemoryRole
import ai.unicorn.words.PersonalWords
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TrendItem(val key: String, val people: Int)

@Serializable
data class TrendArea(
    val country: String,
    val region: String? = null,
    /** The connection's approximate city. */
    val city: String? = null,
    val scope: String,
    val days: Int,
)

@Serializable
data class Trends(
    val area: TrendArea,
    val minPeople: Int,
    /** Different people who shared in the area today: how close it is to showing its first trends. */
    val sharersToday: Int = 0,
    val topics: List<TrendItem> = emptyList(),
    val brands: List<TrendItem> = emptyList(),
    val intents: List<TrendItem> = emptyList(),
    val questions: List<TrendItem> = emptyList(),
) {
    /** True when no community trend passed the minimum number of people. */
    val isEmpty get() = topics.isEmpty() && brands.isEmpty() && intents.isEmpty() && questions.isEmpty()
}

/**
 * "Trending near you". Contributing requires the user's trends consent and sends only anonymous signals computed on
 * the phone (see [MemoryInsightsEngine.signalsFor]), at most once a day. Viewing trends sends nothing personal.
 */
class TrendsRepository(private val core: UnicornCore) {
    companion object {
        private const val MAX_PROMPTS_PER_DAY = 60
        private const val MAX_SIGNALS = 60
        const val STARDUST_PER_DAY = 10

        /** Shown in the preview before anything is saved, run through the same code as real prompts. */
        const val EXAMPLE_PROMPT = "best tacos near me for a birthday dinner"

        /** A short question is shared only when it reads as one, so chat lines and drafts stay on the phone. */
        private val QuestionStart = Regex(
            "^(how|what|whats|which|why|when|where|who|is|are|can|could|should|would|will|do|does|did|best|top|" +
                "cheapest|easiest|ideas|tips|recipe|recipes|good|cheap)\\b",
        )
    }

    /** What sharing would send this week, computed on the phone exactly as contributing does. */
    data class Preview(val prompts: Int, val signals: List<Pair<String, String>>, val example: String?)

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = core.appContext.getSharedPreferences("unicorn_trends", 0)

    suspend fun fetch(scope: String): Trends {
        val body = core.ai.get("/v1/trends?scope=$scope&days=7")
        return json.decodeFromString(Trends.serializer(), body)
    }

    /** Sends today's anonymous signals once per day, only with consent. Returns how many were accepted. */
    suspend fun contributeIfDue(): Int {
        if (!core.prefs.trendsConsent) return 0
        val memory = core.memory ?: return 0
        val since = prefs.getLong("last_contribution", 0L)
        val now = System.currentTimeMillis()
        if (now - since < 20 * 60 * 60 * 1000L) return 0
        val texts = memory.read { getTimeline(MAX_PROMPTS_PER_DAY, 0) }
            .filter { it.role != MemoryRole.ASSISTANT && it.createdAt > maxOf(since, now - 24 * 60 * 60 * 1000L) }
            .map { it.text }
        if (texts.isEmpty()) return 0
        val signals = signalsFor(texts)
        if (signals.isEmpty()) return 0
        val items = signals.joinToString(",") { (kind, key) ->
            "{\"kind\":${json.encodeToString(kotlinx.serialization.serializer<String>(), kind)}," +
                "\"key\":${json.encodeToString(kotlinx.serialization.serializer<String>(), key.take(80))}}"
        }
        core.ai.post("/v1/signals", "{\"items\":[$items]}")
        prefs.edit().putLong("last_contribution", now).apply()
        core.prefs.stardust += STARDUST_PER_DAY
        return signals.size
    }

    /**
     * The anonymous signals for some prompts. Topics and short questions that contain one of the user's own names or
     * new words are dropped, so "how do i tell priya im sorry" never leaves the phone, and so are short messages that
     * aren't questions ("see you at 5").
     */
    suspend fun signalsFor(texts: List<String>): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val memory = core.memory ?: return@withContext emptyList()
        val private = runCatching { PersonalWords.dao(core.appContext)?.privateWords() }.getOrNull().orEmpty()
            .map { it.lowercase(Locale.ROOT) }.toSet()
        MemoryInsightsEngine(memory).signalsFor(texts)
            .filter { (kind, key) ->
                val words = key.split(' ')
                when (kind) {
                    "question" -> QuestionStart.containsMatchIn(key) && words.none { it in private }
                    "topic" -> words.all { w -> w.length >= 4 && w.all(Char::isLetter) && w !in private }
                    else -> true
                }
            }
            .take(MAX_SIGNALS)
    }

    /** This week's saved prompts turned into what would be shared; an example when nothing is saved yet. */
    suspend fun preview(): Preview {
        val memory = core.memory ?: return Preview(0, emptyList(), null)
        val since = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val texts = memory.read { getTimeline(500, 0) }
            .filter { it.role != MemoryRole.ASSISTANT && it.createdAt > since }
            .map { it.text }
        if (texts.isEmpty()) return Preview(0, signalsFor(listOf(EXAMPLE_PROMPT)), EXAMPLE_PROMPT)
        return Preview(texts.size, signalsFor(texts), null)
    }
}
