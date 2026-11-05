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
import ai.unicorn.trends.Hunches
import ai.unicorn.trends.Trends
import ai.unicorn.trends.TrendsRepository
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Section
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Hunches -------------------------------------------------------------------------------------------------------------

/**
 * Playful guesses about what the area is asking, until enough people near the user share. Always titled as a guess
 * ("Probably on Prosper's mind") and each one says what it is based on, so nobody mistakes it for real data.
 */
@Composable
fun HunchesSection(hunches: Hunches, limit: Int = hunches.hunches.size, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = LocalUnicorn.current
    val town = hunches.town
    var shown by remember(hunches) { mutableStateOf(limit) }
    Section(
        if (town != null) "Probably on $town's mind" else "Probably on people's minds",
        subtitle = if (compact) "Unicorn's guesses for today" else
            "Unicorn's playful guesses from today's weather, the calendar and your town. Real trends replace them as people near you share.",
    ) {
        Column(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
            hunches.hunches.take(shown).forEach { hunch ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(hunch.emoji, Modifier.width(34.dp), style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(hunch.text, style = MaterialTheme.typography.bodyLarge)
                        if (!compact) Text(hunch.why, style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    }
                }
            }
        }
        if (!compact && shown < hunches.hunches.size) {
            Text(
                "More hunches",
                Modifier.clickable { shown = hunches.hunches.size }.padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
            )
        }
        if (!compact && hunches.credits.isNotEmpty()) {
            Text(hunches.credits.joinToString(" · "), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

// Nudge 1: the blurred peek ---------------------------------------------------------------------------------------------

/**
 * What people near you ask AI, for someone who isn't sharing: the first trend in clear and the rest blurred, like read
 * receipts made visible. Before an area has any trends, it shows how close it is instead, with nothing made up.
 */
@Composable
fun NearYouPeek(trends: Trends?, onShare: () -> Unit) {
    val colors = LocalUnicorn.current
    val items = trends?.let { t -> t.questions.ifEmpty { t.topics }.map { it.key.replaceFirstChar(Char::titlecase) } }.orEmpty()
    val area = trends?.area?.region ?: "your area"
    Section("What people near you ask AI", subtitle = "🔒 It works both ways, like read receipts") {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(16.dp)) {
            if (items.isNotEmpty()) {
                items.take(4).forEachIndexed { i, text ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text("${i + 1}", Modifier.width(26.dp), style = MaterialTheme.typography.titleMedium, color = colors.gold)
                        Redacted(text, hidden = i > 0)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Share yours, anonymously, to see the rest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            } else {
                listOf(0.9f, 0.7f, 0.8f).forEach { width ->
                    Box(Modifier.padding(vertical = 7.dp).fillMaxWidth(width).height(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.track))
                }
                Spacer(Modifier.height(10.dp))
                val min = trends?.minPeople ?: 25
                val sharers = trends?.sharersToday ?: 0
                LinearProgressIndicator(
                    progress = { (sharers.toFloat() / min).coerceIn(0.02f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = colors.gold,
                    trackColor = colors.track,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$sharers of $min people in $area shared today. Trends near you appear once $min different people share the same thing. Yours would help unlock them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onShare,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink),
            ) { Text(if (items.isNotEmpty()) "Share yours to see all" else "Share yours to help unlock") }
        }
    }
}

/** Text that is blurred when hidden; older phones can't blur, so they get a bar of the same length instead. */
@Composable
private fun Redacted(text: String, hidden: Boolean) {
    val colors = LocalUnicorn.current
    when {
        !hidden -> Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            Text(text, Modifier.blur(7.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        else -> Box(
            Modifier.padding(top = 4.dp).fillMaxWidth((text.length / 40f).coerceIn(0.35f, 0.95f)).height(14.dp)
                .clip(RoundedCornerShape(7.dp)).background(colors.track),
        )
    }
}

// Nudge 3: see exactly what's shared ----------------------------------------------------------------------------------

/**
 * The user's own week turned into exactly what sharing would send, worked out on the phone with the same code that
 * shares. Before anything is saved, an example prompt goes through it instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharePreviewCard(startOpen: Boolean = false) {
    val colors = LocalUnicorn.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val core = remember { Unicorn.get(context) }
    var open by remember { mutableStateOf(startOpen) }
    val preview by produceState<TrendsRepository.Preview?>(null, open) {
        if (open && value == null) value = runCatching { core.trends.preview() }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp)) {
            Text("👀  See exactly what's shared", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                val p = preview
                when {
                    p == null -> Text("Working it out on your phone…", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    else -> {
                        Text(
                            if (p.example != null) "Nothing saved yet, so here's an example. The prompt \"${p.example}\" would share:"
                            else "What would leave your phone from this week:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (p.signals.isEmpty()) {
                            Text("Nothing. None of this week's prompts makes a signal worth sharing.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.signals.take(14).forEach { (kind, key) -> SignalChip(kind, key) }
                            }
                            if (p.signals.size > 14) {
                                Text("and ${p.signals.size - 14} more like these", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = colors.muted)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            (if (p.prompts > 0) "Your ${p.prompts} ${if (p.prompts == 1) "prompt stays" else "prompts stay"} on this phone. " else "") +
                                "Only summaries like these are sent, once a day, with no account or name. Your own names and new words are left out, and a trend only appears once 25 different people share it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalChip(kind: String, key: String) {
    val colors = LocalUnicorn.current
    val (label, color) = when (kind) {
        "topic" -> "Topic" to colors.glacier
        "brand" -> "Brand" to colors.gold
        "intent" -> "Asking for" to colors.ink
        else -> "Short question" to colors.muted
    }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(if (kind == "question") "\"$key\"" else key, style = MaterialTheme.typography.bodyMedium)
    }
}

// Nudge 2: your Unicorn grows ------------------------------------------------------------------------------------------

/** Stardust levels: each one adds something to the user's Unicorn card. */
object Stardust {
    data class Level(val number: Int, val name: String, val at: Int, val unlocks: String)

    val Levels = listOf(
        Level(1, "Hatchling", 0, "a fresh glow"),
        Level(2, "Sparkling", 30, "sparkles"),
        Level(3, "Winged", 80, "wings"),
        Level(4, "Starborn", 160, "an aurora halo"),
        Level(5, "Legendary", 300, "a golden frame"),
    )

    fun level(stardust: Int): Level = Levels.last { stardust >= it.at }

    fun next(stardust: Int): Level? = Levels.firstOrNull { stardust < it.at }
}

/** Level, progress to the next one, and how stardust is earned. */
@Composable
fun StardustProgress(stardust: Int, sharing: Boolean, onStartSharing: (() -> Unit)? = null) {
    val colors = LocalUnicorn.current
    val level = Stardust.level(stardust)
    val next = Stardust.next(stardust)
    Text("Level ${level.number} · ${level.name}", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { if (next == null) 1f else ((stardust - level.at).toFloat() / (next.at - level.at)).coerceIn(0.03f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = colors.gold,
        trackColor = colors.track,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (next == null) "$stardust stardust · fully grown" else "$stardust stardust · ${next.unlocks} at ${next.at}",
        style = MaterialTheme.typography.labelLarge,
        color = colors.muted,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        if (sharing) "Your Unicorn earns ${TrendsRepository.STARDUST_PER_DAY} stardust each day you share anonymous trends."
        else "Your Unicorn grows sparkles, wings and more while you share anonymous trends.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.muted,
    )
    if (!sharing && onStartSharing != null) {
        Text(
            "Start sharing",
            Modifier.clickable(onClick = onStartSharing).padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
        )
    }
}
