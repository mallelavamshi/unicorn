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

import ai.unicorn.memory.MemoryRepository
import ai.unicorn.memory.sourceLabel
import ai.unicorn.ui.Bars
import ai.unicorn.ui.EmptyHint
import ai.unicorn.ui.HourStrip
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Section
import ai.unicorn.ui.ShareRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun InsightsScreen() {
    val memory = rememberMemory()
    val insights = rememberInsights(memory)
    val colors = LocalUnicorn.current
    val scope = rememberCoroutineScope()
    var latency by remember { mutableStateOf(memory?.latencyStats()) }
    var measuring by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Insights", Modifier.padding(start = 20.dp, top = 20.dp), style = MaterialTheme.typography.headlineLarge)
        val data = insights
        if (data == null) {
            Text("Reading your history…", Modifier.padding(20.dp), color = colors.muted)
            return@Column
        }
        if (data.totalPrompts == 0) {
            Section("Nothing to show yet") {
                EmptyHint("Insights build up from the questions you ask AI apps, or from history you bring in from You.")
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(20.dp)) {
            BigStat(data.totalPrompts.toString(), "questions", Modifier.weight(1f))
            BigStat(data.activeDays.toString(), "active days", Modifier.weight(1f))
            BigStat(data.longestStreak.toString(), "longest streak", Modifier.weight(1f))
        }
        Section("Last 30 days") {
            Bars(data.daily.map { it.second }, emptyList(), colors.ink, height = 96.dp)
        }
        Section("When you ask", "Darker hours are when you ask AI the most.") {
            HourStrip(data.byHour, colors.ink)
        }
        Section("Where you ask") {
            val max = data.bySource.maxOfOrNull { it.second } ?: 1
            data.bySource.forEach { (source, count) -> ShareRow(sourceLabel(source), count, max, colors.ink) }
        }
        if (data.intents.isNotEmpty()) {
            Section("What you ask for") {
                val max = data.intents.maxOf { it.second }
                data.intents.forEach { (intent, count) -> ShareRow(intent, count, max, colors.glacier) }
            }
        }
        if (data.brands.isNotEmpty()) {
            Section("Brands on your mind") {
                val max = data.brands.maxOf { it.second }
                data.brands.take(8).forEach { (brand, count) -> ShareRow(brand, count, max, colors.gold) }
            }
        }
        if (data.topics.isNotEmpty()) {
            Section("Topics") {
                data.topics.take(10).forEach { topic ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(topic.label.replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${topic.count} questions, for example \"${topic.samples.first()}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
        Section("Suggestion speed", "How fast Unicorn finds your similar earlier questions while you type, on this phone.") {
            val stats = latency
            Text(
                if (stats == null || stats.samples == 0) {
                    "Not measured yet. ${stats?.indexedPrompts ?: 0} questions remembered."
                } else {
                    "Typically ${"%.2f".format(stats.p50Micros / 1000)} ms, at most ${"%.2f".format(stats.p95Micros / 1000)} ms " +
                        "across ${stats.indexedPrompts} remembered questions."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(
                enabled = memory != null && !measuring,
                onClick = {
                    measuring = true
                    scope.launch {
                        latency = memory?.runLatencyBenchmark()
                        measuring = false
                    }
                },
            ) { Text(if (measuring) "Measuring" else "Measure now") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.displayMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalUnicorn.current.muted)
    }
}
