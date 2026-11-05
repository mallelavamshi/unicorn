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

package ai.unicorn.app

import ai.unicorn.Unicorn
import ai.unicorn.memory.MemoryInsights
import ai.unicorn.memory.MemoryMessageRow
import ai.unicorn.memory.sourceLabel
import ai.unicorn.trends.Hunches
import ai.unicorn.trends.Trends
import ai.unicorn.ui.Bars
import ai.unicorn.ui.CuriosityRings
import ai.unicorn.ui.EmptyHint
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Ring
import ai.unicorn.ui.RingLegend
import ai.unicorn.ui.Section
import ai.unicorn.ui.Tag
import ai.unicorn.ui.UnicornLogo
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(nav: NavHostController) {
    val context = LocalContext.current
    val core = remember { Unicorn.get(context) }
    val memory = rememberMemory()
    val insights = rememberInsights(memory)
    val colors = LocalUnicorn.current
    val recent by produceState<List<MemoryMessageRow>>(emptyList(), insights) {
        value = memory?.read { getTimeline(4, 0) } ?: emptyList()
    }
    val trends by produceState<Trends?>(null) {
        value = runCatching { core.trends.fetch("region") }.getOrNull()
    }
    val hunches by produceState(core.hunches.cached()) {
        value = runCatching { core.hunches.fetch() }.getOrNull()
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(core.prefs.displayName), style = MaterialTheme.typography.headlineLarge)
                Text(
                    // "Monday, November 2" in the US, "Monday 2 November" in the UK: the phone's own order
                    java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern(
                            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"),
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
            UnicornLogo(size = 44.dp)
        }

        val data = insights ?: MemoryInsights.Empty
        val weekTopics = data.topics.count { System.currentTimeMillis() - it.lastAt < 7 * DateUtils.DAY_IN_MILLIS }
        val promptGoal = maxOf(20, (data.promptsPrevious7Days * 1.1).toInt())
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CuriosityRings(
                rings = listOf(
                    Ring(data.promptsLast7Days.toFloat() / promptGoal, colors.ink),
                    Ring(data.currentStreak / 7f, colors.gold),
                    Ring(weekTopics / 5f, colors.glacier),
                ),
                modifier = Modifier.size(176.dp),
            )
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RingLegend(colors.ink, data.promptsLast7Days.toString(), "questions this week")
                RingLegend(colors.gold, data.currentStreak.toString(), if (data.currentStreak == 1) "day streak" else "days streak")
                RingLegend(colors.glacier, weekTopics.toString(), "topics explored")
            }
        }
        Text(
            weekSummary(data),
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )

        if (data.totalPrompts == 0) {
            Section("Fill your rings") {
                if (!(core.prefs.captureConsent && core.prefs.captureEnabled)) {
                    EmptyHint(
                        "Turn on Remember my AI prompts, then ask ChatGPT, Gemini or Claude anything. Your rings fill as you go.",
                        action = "Turn it on in You",
                        onAction = { nav.navigate("you") },
                    )
                } else {
                    EmptyHint(
                        "Ask ChatGPT, Gemini, Claude or Perplexity something while you type with Unicorn. Your first question fills the purple ring.",
                        action = "Bring in your past chats",
                        onAction = { nav.navigate("import") },
                    )
                }
            }
        } else {
            Section("This week") {
                val days = data.daily.takeLast(7)
                Bars(
                    values = days.map { it.second },
                    labels = days.map { it.first.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                    color = colors.ink,
                )
            }
            if (data.topics.isNotEmpty()) {
                Section("On your mind") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        data.topics.take(6).forEach { topic -> Tag(topic.label, colors.glacier) { nav.navigate("library") } }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Section("Recently asked") {
                    recent.forEach { row -> PromptRow(row) { nav.navigate("conversation/${row.conversationId}") } }
                }
            }
        }

        val current = trends
        val sharing = core.prefs.trendsConsent
        if (sharing && current != null && !current.isEmpty) {
            Section("Near you", subtitle = "What people around you ask AI") {
                Column(Modifier.clickable { nav.navigate("explore") }) {
                    (current.questions.ifEmpty { current.topics }).take(3).forEach {
                        Text(it.key.replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
        hunches?.takeIf { it.hunches.isNotEmpty() }?.let { HunchesSection(it, limit = 3, compact = true) { nav.navigate("explore") } }
        if (!sharing) {
            Section("What people near you ask AI") {
                EmptyHint(
                    "🔒 It works both ways, like read receipts: share what you ask AI, anonymously, and see what people around you ask.",
                    action = "Open Near you",
                    onAction = { nav.navigate("explore") },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PromptRow(row: MemoryMessageRow, trailing: String? = null, text: String = row.text, onClick: () -> Unit) {
    val colors = LocalUnicorn.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(
                sourceLabel(row.sourceId),
                DateUtils.getRelativeTimeSpanString(row.createdAt).toString().lowercase(),
                trailing,
            ).joinToString(", "),
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
    }
}

private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, $name"
}

private fun weekSummary(data: MemoryInsights): String {
    val now = data.promptsLast7Days
    val before = data.promptsPrevious7Days
    return when {
        now == 0 && before == 0 -> "Nothing asked this week yet."
        before == 0 -> "You asked $now ${if (now == 1) "question" else "questions"} this week."
        now > before -> "You asked $now questions this week, ${now - before} more than last week."
        now < before -> "You asked $now questions this week, ${before - now} fewer than last week."
        else -> "You asked $now questions this week, the same as last week."
    }
}
