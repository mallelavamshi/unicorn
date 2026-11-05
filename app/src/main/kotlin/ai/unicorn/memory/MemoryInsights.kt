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

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MemoryTopic(val label: String, val count: Int, val samples: List<String>, val lastAt: Long)

data class MemoryInsights(
    val totalPrompts: Int,
    val totalReplies: Int,
    val totalConversations: Int,
    val activeDays: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val promptsLast7Days: Int,
    val promptsPrevious7Days: Int,
    val averagePromptChars: Int,
    /** Prompts per day for the last 30 days, oldest first. */
    val daily: List<Pair<LocalDate, Int>>,
    val bySource: List<Pair<String, Int>>,
    /** Prompts per hour of day (local time), index 0..23. */
    val byHour: List<Int>,
    val intents: List<Pair<String, Int>>,
    val topics: List<MemoryTopic>,
    val brands: List<Pair<String, Int>>,
    val analyzedPrompts: Int,
) {
    companion object {
        val Empty = MemoryInsights(0, 0, 0, 0, 0, 0, 0, 0, 0, emptyList(), emptyList(), List(24) { 0 }, emptyList(), emptyList(), emptyList(), 0)
    }
}

/**
 * Computes the Unicorn app's metrics on-device. Topics are clusters of prompt embeddings, intents are assigned by
 * the nearest intent prototype embedding, brand mentions use a curated dictionary.
 */
class MemoryInsightsEngine(private val repository: MemoryRepository) {
    companion object {
        private const val MAX_ANALYZED_PROMPTS = 3000
        private const val TOPIC_THRESHOLD = 0.55f
        private const val MAX_TOPICS = 300
        private const val INTENT_THRESHOLD = 0.30f

        private val IntentSeeds = linkedMapOf(
            "Compare" to listOf("x vs y which is better", "compare these two options", "difference between a and b"),
            "Recommend" to listOf("best option for me", "recommend a good one", "top 10 picks"),
            "Buy & price" to listOf("how much does it cost", "is it worth buying", "cheapest price deal discount"),
            "How-to" to listOf("how do i do this", "step by step guide", "how to fix this problem"),
            "Explain" to listOf("what is this", "explain this concept", "why does this happen"),
            "Write" to listOf("write an email", "draft a message", "rewrite this paragraph"),
            "Code" to listOf("python code error", "write a function", "debug this javascript"),
            "Plan" to listOf("plan a trip itinerary", "create a weekly schedule", "make a meal plan"),
            "Health" to listOf("symptoms of", "is it safe to take this medicine", "workout and diet"),
        )

        private val Brands = listOf(
            // Tech & phones
            "Apple", "iPhone", "MacBook", "iPad", "Samsung", "Galaxy", "Google", "Pixel", "OnePlus", "Xiaomi", "Redmi",
            "Oppo", "Vivo", "Realme", "Nothing", "Motorola", "Sony", "Dell", "HP", "Lenovo", "Asus", "Acer", "Microsoft",
            "Surface", "Bose", "JBL", "Boat", "Logitech", "Canon", "Nikon", "GoPro", "Nintendo", "PlayStation", "Xbox",
            // AI & software
            "ChatGPT", "OpenAI", "Claude", "Anthropic", "Gemini", "Perplexity", "Copilot", "Notion", "Slack", "Zoom",
            "HubSpot", "Salesforce", "Zoho", "Shopify", "WordPress", "Canva", "Figma", "Adobe", "Excel", "Jira",
            // Retail & food
            "Amazon", "Flipkart", "Myntra", "Meesho", "Nykaa", "Walmart", "Target", "Costco", "IKEA", "Zara", "H&M",
            "Uniqlo", "Nike", "Adidas", "Puma", "Hoka", "Brooks", "Asics", "New Balance", "Decathlon", "Starbucks",
            "McDonald's", "Domino's", "Swiggy", "Zomato", "Blinkit", "Zepto", "BigBasket", "Uber", "Ola", "Rapido",
            // Cars & mobility
            "Tesla", "Toyota", "Honda", "Hyundai", "Kia", "Tata", "Mahindra", "Maruti", "Suzuki", "BMW", "Mercedes",
            "Audi", "Volkswagen", "Skoda", "Ford", "Rivian", "Ather", "Ola Electric", "Royal Enfield", "Bajaj", "TVS",
            // Finance & travel
            "HDFC", "ICICI", "SBI", "Axis", "Kotak", "Paytm", "PhonePe", "Google Pay", "Zerodha", "Groww", "Visa",
            "Mastercard", "American Express", "PayPal", "Stripe", "Airbnb", "Booking.com", "MakeMyTrip", "Expedia",
            "IndiGo", "Air India", "Emirates", "Marriott", "Taj", "OYO",
            // US money, travel, food and shopping (multi-word where the name is also an everyday word)
            "Bank of America", "Wells Fargo", "Capital One", "Citibank", "JPMorgan", "Robinhood", "Venmo", "Cash App",
            "Zelle", "Fidelity", "Vanguard", "Schwab", "Coinbase", "TurboTax", "Geico", "State Farm", "JetBlue",
            "Delta Airlines", "United Airlines", "American Airlines", "Southwest Airlines", "Hilton", "Hyatt",
            "Chipotle", "Chick-fil-A", "Taco Bell", "Wendy's", "Dunkin'", "Whataburger", "In-N-Out", "DoorDash",
            "Uber Eats", "Instacart", "Trader Joe's", "Whole Foods", "Kroger", "H-E-B", "Publix", "Best Buy",
            "Home Depot", "Lowe's", "Walgreens", "CVS", "Etsy", "eBay", "Shein", "Temu", "Lululemon", "Sephora", "Ulta",
            "Peloton", "AirPods", "Apple Watch", "Chevrolet", "Chevy", "Jeep", "Subaru", "Nissan", "Lucid",
            "Disney World", "Universal Studios",
            // Streaming & media
            "Netflix", "Spotify", "YouTube", "Prime Video", "Disney+", "Hotstar", "Instagram", "WhatsApp", "LinkedIn",
            "Hulu", "HBO Max", "Paramount+", "Apple TV", "TikTok", "Snapchat", "Reddit",
            // Health & beauty
            "Ozempic", "Optimum Nutrition", "MuscleBlaze", "Cetaphil", "CeraVe", "The Ordinary", "Minimalist", "Dove",
            "Wegovy", "Zepbound", "Mounjaro",
        )
        private val BrandRegex = Regex(
            Brands.sortedByDescending { it.length }.joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(", postfix = ")(?![\\p{L}\\p{N}])") {
                Regex.escape(it)
            },
            RegexOption.IGNORE_CASE,
        )
        private val CanonicalBrand = Brands.associateBy { it.lowercase() }

        private val StopWords = setOf(
            "the", "and", "for", "with", "what", "how", "which", "that", "this", "are", "you", "your", "can", "best",
            "does", "should", "from", "about", "have", "will", "would", "could", "into", "than", "then", "there",
            "their", "them", "they", "when", "where", "why", "who", "give", "make", "need", "want", "like", "some",
            "more", "most", "good", "please", "tell", "explain", "write", "between", "much", "many", "also", "just",
            // Everyday filler, so a chat line like "see you at 5, can't wait" doesn't become a topic
            "see", "get", "got", "let", "now", "out", "yes", "not", "but", "all", "any", "one", "too", "was", "were",
            "has", "had", "did", "been", "here", "know", "think", "really", "very", "still", "going", "gonna",
            "wanna", "come", "back", "again", "well", "wait", "free", "soon", "later", "today", "tonight", "tomorrow",
            "tmrw", "hey", "yeah", "okay", "sure", "thanks", "thank", "sorry", "love", "miss", "lol", "haha", "omg",
            "something", "anything", "thing", "things", "someone", "said", "told", "say", "ask", "use", "using",
            "near", "help", "find", "show",
        )
        private val WordRegex = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'-]+")

        /** A word that can name a topic: three or more letters (no digits or contractions) and not filler. */
        private fun isTopicWord(word: String) =
            word.length >= 3 && word.all { it.isLetter() || it == '-' } && word !in StopWords

        /**
         * Names a group of similar prompts after the one or two words most of them share, in the order people write
         * them ("waterproof trekking"). Null when the prompts hold only filler, so small talk never shows up as a topic.
         */
        internal fun topicLabel(texts: List<String>): String? {
            val tokenized = texts.map { text -> WordRegex.findAll(text.lowercase()).map { it.value }.toList() }
            val frequency = HashMap<String, Int>()
            for (tokens in tokenized) {
                tokens.filter(::isTopicWord).toSet().forEach { frequency[it] = (frequency[it] ?: 0) + 1 }
            }
            // Ties go to the longer, more specific word
            val shared = frequency.entries.filter { it.value >= 2 }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length }.thenBy { it.key })
                .take(2).map { it.key }
            // Prompts that say the same thing in different words are named after the newest one
            val words = shared.ifEmpty { tokenized.first().filter(::isTopicWord).distinct().take(2) }
            if (words.isEmpty()) return null
            val sample = tokenized.firstOrNull { it.containsAll(words) }
            return (if (sample == null) words else words.sortedBy { sample.indexOf(it) }).joinToString(" ")
        }
    }

    suspend fun compute(zone: ZoneId = ZoneId.systemDefault()): MemoryInsights = withContext(Dispatchers.Default) {
        val rows = repository.read { getMetricRows() }
        val prompts = rows.filter { it.role != MemoryRole.ASSISTANT }
        if (prompts.isEmpty()) return@withContext MemoryInsights.Empty

        val today = LocalDate.now(zone)
        val promptDays = prompts.groupingBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }.eachCount()
        val activeDays = promptDays.keys.sorted()
        var longest = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in activeDays) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            longest = maxOf(longest, run)
            previous = day
        }
        var current = 0
        var cursor = if (promptDays.containsKey(today)) today else today.minusDays(1)
        while (promptDays.containsKey(cursor)) {
            current++
            cursor = cursor.minusDays(1)
        }

        val byHour = IntArray(24)
        prompts.forEach { byHour[Instant.ofEpochMilli(it.createdAt).atZone(zone).hour]++ }

        val texts = repository.read { getTimeline(MAX_ANALYZED_PROMPTS, 0) }
        val vectors = texts.mapNotNull { row -> repository.embed(row.text)?.let { row to it } }

        MemoryInsights(
            totalPrompts = prompts.size,
            totalReplies = rows.size - prompts.size,
            totalConversations = rows.map { it.conversationId }.distinct().size,
            activeDays = activeDays.size,
            currentStreak = current,
            longestStreak = longest,
            promptsLast7Days = (0L until 7L).sumOf { promptDays[today.minusDays(it)] ?: 0 },
            promptsPrevious7Days = (7L until 14L).sumOf { promptDays[today.minusDays(it)] ?: 0 },
            averagePromptChars = prompts.map { it.length }.average().toInt(),
            daily = (29L downTo 0L).map { today.minusDays(it).let { day -> day to (promptDays[day] ?: 0) } },
            bySource = prompts.groupingBy { it.sourceId }.eachCount().toList().sortedByDescending { it.second },
            byHour = byHour.toList(),
            intents = classifyIntents(vectors.map { it.second }),
            topics = clusterTopics(vectors),
            brands = countBrands(texts.map { it.text }),
            analyzedPrompts = texts.size,
        )
    }

    /**
     * Anonymous signals for consented "trending near you": per prompt an intent, brands, a short topic phrase and a
     * normalized question. Prompts with personal looking content (digits, emails, links) yield no question.
     */
    fun signalsFor(texts: List<String>): List<Pair<String, String>> {
        val prototypes = IntentSeeds.mapNotNull { (intent, seeds) ->
            val embedded = seeds.mapNotNull { repository.embed(it) }
            if (embedded.isEmpty()) null else intent to normalize(sum(embedded))
        }
        val out = LinkedHashSet<Pair<String, String>>()
        for (text in texts) {
            val vector = repository.embed(text)
            if (vector != null) {
                val best = prototypes.maxByOrNull { dot(vector, it.second) }
                if (best != null && dot(vector, best.second) >= INTENT_THRESHOLD) out.add("intent" to best.first)
            }
            BrandRegex.findAll(text).forEach { out.add("brand" to (CanonicalBrand[it.value.lowercase()] ?: it.value)) }
            val words = WordRegex.findAll(text.lowercase()).map { it.value }.filter(::isTopicWord).toList()
            if (words.size >= 2) out.add("topic" to words.take(2).joinToString(" "))
            normalizeQuestion(text)?.let { out.add("question" to it) }
        }
        return out.toList()
    }

    private fun normalizeQuestion(text: String): String? {
        val lower = text.lowercase().trim()
        if (lower.contains('@') || lower.contains("http") || lower.contains("www.") || Regex("\\d{3,}").containsMatchIn(lower)) {
            return null
        }
        val cleaned = lower.replace(Regex("[^\\p{L}\\p{N}' ]+"), " ").replace(Regex("\\s+"), " ").trim()
        val wordCount = cleaned.split(' ').size
        return cleaned.takeIf { wordCount in 3..12 && it.length <= 80 }
    }

    private fun classifyIntents(vectors: List<FloatArray>): List<Pair<String, Int>> {
        val prototypes = IntentSeeds.mapNotNull { (intent, seeds) ->
            val embedded = seeds.mapNotNull { repository.embed(it) }
            if (embedded.isEmpty()) null else intent to normalize(sum(embedded))
        }
        val counts = LinkedHashMap<String, Int>()
        for (vector in vectors) {
            val best = prototypes.maxByOrNull { dot(vector, it.second) } ?: continue
            val intent = if (dot(vector, best.second) >= INTENT_THRESHOLD) best.first else "Other"
            counts[intent] = (counts[intent] ?: 0) + 1
        }
        return counts.toList().sortedByDescending { it.second }
    }

    private fun clusterTopics(vectors: List<Pair<MemoryMessageRow, FloatArray>>): List<MemoryTopic> {
        class Cluster(val sum: FloatArray, val centroid: FloatArray, val members: MutableList<MemoryMessageRow>)
        val clusters = ArrayList<Cluster>()
        for ((row, vector) in vectors) {
            var best: Cluster? = null
            var bestScore = TOPIC_THRESHOLD
            for (cluster in clusters) {
                val score = dot(vector, cluster.centroid)
                if (score >= bestScore) {
                    bestScore = score
                    best = cluster
                }
            }
            if (best != null) {
                for (d in vector.indices) best.sum[d] += vector[d]
                System.arraycopy(best.sum, 0, best.centroid, 0, vector.size)
                normalize(best.centroid)
                best.members.add(row)
            } else if (clusters.size < MAX_TOPICS) {
                clusters.add(Cluster(vector.copyOf(), vector.copyOf(), mutableListOf(row)))
            }
        }
        return clusters
            .filter { it.members.size >= 2 }
            .sortedByDescending { it.members.size }
            .mapNotNull { cluster ->
                val texts = cluster.members.map { it.text }
                topicLabel(texts)?.let { label ->
                    MemoryTopic(
                        label = label,
                        count = cluster.members.size,
                        samples = texts.distinct().take(5),
                        lastAt = cluster.members.maxOf { it.createdAt },
                    )
                }
            }
            .take(30)
    }

    private fun countBrands(texts: List<String>): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        for (text in texts) {
            BrandRegex.findAll(text).map { CanonicalBrand[it.value.lowercase()] ?: it.value }.toSet()
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.toList().sortedByDescending { it.second }.take(20)
    }

    private fun sum(vectors: List<FloatArray>): FloatArray {
        val out = FloatArray(vectors.first().size)
        for (v in vectors) for (d in v.indices) out[d] += v[d]
        return out
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm > 0f) for (d in v.indices) v[d] /= norm
        return v
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var acc = 0f
        for (d in a.indices) acc += a[d] * b[d]
        return acc
    }
}
