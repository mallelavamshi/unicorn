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
import ai.unicorn.ui.EmptyHint
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Section
import ai.unicorn.ui.ShareRow
import ai.unicorn.ui.Tag
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch

private sealed interface TrendsState {
    data object Loading : TrendsState
    data class Loaded(val trends: Trends) : TrendsState
    data class Failed(val message: String) : TrendsState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen() {
    val context = LocalContext.current
    val core = remember { Unicorn.get(context) }
    val colors = LocalUnicorn.current
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(core.prefs.trendsConsent) }
    var showConsent by rememberSaveable { mutableStateOf(false) }
    var area by rememberSaveable { mutableStateOf("region") }
    var neighbourhoodOn by remember { mutableStateOf(core.prefs.useNeighbourhood) }
    var stardust by remember { mutableStateOf(core.prefs.stardust) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        core.prefs.useNeighbourhood = granted
        neighbourhoodOn = granted
    }
    val state by produceState<TrendsState>(TrendsState.Loading, consent, area) {
        value = TrendsState.Loading
        if (consent) {
            runCatching { core.trends.contributeIfDue() }
            stardust = core.prefs.stardust
        }
        value = runCatching { TrendsState.Loaded(core.trends.fetch(area)) }
            .getOrElse { TrendsState.Failed("Couldn't load trends. Check your connection and open Near you again.") }
    }
    val hunches by produceState(core.hunches.cached(), neighbourhoodOn) {
        value = runCatching { core.hunches.fetch() }.getOrNull()
    }
    fun startSharing() {
        core.prefs.trendsConsent = true
        consent = true
        showConsent = false
        scope.launch { runCatching { core.trends.contributeIfDue() } }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Near you", Modifier.padding(start = 20.dp, top = 20.dp), style = MaterialTheme.typography.headlineLarge)
        val header = (state as? TrendsState.Loaded)?.trends?.area
        Text(
            when {
                header == null -> "What's happening and what people ask AI around you"
                header.region != null -> "${header.region}, ${countryName(header.country)}, last ${header.days} days"
                else -> "${countryName(header.country)}, last ${header.days} days"
            },
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
        )

        hunches?.takeIf { it.hunches.isNotEmpty() }?.let { HunchesSection(it, limit = 6) }
        Text(
            if (neighbourhoodOn) "📍 Using your neighborhood for hunches · Turn off"
            else "📍 Make hunches about my neighborhood",
            Modifier.fillMaxWidth()
                .clickable {
                    if (neighbourhoodOn) {
                        core.prefs.useNeighbourhood = false
                        neighbourhoodOn = false
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.ink,
        )
        if (!neighbourhoodOn) {
            Text(
                "Uses your approximate location to find your area's name. The name and a rough location (within about a mile) are sent to pick hunches, and aren't kept with anything that identifies you.",
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }

        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
            Tag("My state", colors.gold, selected = area == "region") { area = "region" }
            Spacer(Modifier.width(8.dp))
            Tag("My country", colors.gold, selected = area == "country") { area = "country" }
        }
        when (val s = state) {
            TrendsState.Loading -> Text("Loading", Modifier.padding(20.dp), color = colors.muted)
            is TrendsState.Failed -> Column(Modifier.padding(20.dp)) { EmptyHint(s.message) }
            is TrendsState.Loaded -> {
                val t = s.trends
                if (consent) {
                    // People near you: give-to-get, so only shown while this person shares too
                    if (!t.isEmpty) {
                        CommunityTrends(t)
                    } else {
                        Section("What people near you ask AI") {
                            EmptyHint(
                                "You're sharing, thank you. ${t.sharersToday} of ${t.minPeople} people in ${t.area.region ?: countryName(t.area.country)} shared today. " +
                                    "Trends appear here once ${t.minPeople} different people share the same thing.",
                            )
                        }
                    }
                    Section("Your Unicorn") { StardustProgress(stardust, sharing = true) }
                } else {
                    NearYouPeek(t) { showConsent = true }
                    if (showConsent) {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            SharePreviewCard(startOpen = true)
                            Spacer(Modifier.height(12.dp))
                            ChoiceCard("See and share trends near me", TrendsDisclosure, checked = false) { if (it) startSharing() }
                        }
                    } else {
                        Column(Modifier.padding(horizontal = 20.dp)) { SharePreviewCard() }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityTrends(t: Trends) {
    val colors = LocalUnicorn.current
    if (t.questions.isNotEmpty()) {
        Section("Questions people are asking") {
            t.questions.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("${i + 1}", Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium, color = colors.gold)
                    Column(Modifier.weight(1f)) {
                        Text(item.key.replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.bodyLarge)
                        Text("${item.people} people", style = MaterialTheme.typography.labelMedium, color = colors.muted)
                    }
                }
            }
        }
    }
    if (t.topics.isNotEmpty()) {
        Section("Topics") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                t.topics.forEach { Tag("${it.key} ${it.people}", colors.glacier) }
            }
        }
    }
    if (t.brands.isNotEmpty()) {
        Section("Brands people ask about") {
            val max = t.brands.maxOf { it.people }
            t.brands.forEach { ShareRow(it.key.replaceFirstChar(Char::titlecase), it.people, max, colors.gold) }
        }
    }
    if (t.intents.isNotEmpty()) {
        Section("What people use AI for") {
            val max = t.intents.maxOf { it.people }
            t.intents.forEach { ShareRow(it.key.replaceFirstChar(Char::titlecase), it.people, max, colors.ink) }
        }
    }
    Text(
        "Counts are numbers of different people, from anonymous signals only. Anything shared by fewer than ${t.minPeople} people is never shown.",
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.muted,
    )
}

internal fun countryName(code: String): String =
    if (code.length == 2 && code != "XX") Locale("", code).displayCountry.ifBlank { code } else "your country"
