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
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Tag
import ai.unicorn.ui.UnicornLogo
import ai.unicorn.ui.UnicornSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

private enum class Step { WELCOME, WRITE, REMEMBER, DISCOVER, SETUP, YOU, PRIVACY, READY }

/** First-run flow: what Unicorn does, turning it on, a little personalization, and the privacy choices. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Unicorn.get(context).prefs }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val current = Step.entries[step]
    val next = { step = (step + 1).coerceAtMost(Step.entries.lastIndex) }
    val colors = LocalUnicorn.current

    Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()) {
        if (current != Step.WELCOME && current != Step.READY) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Step.entries.drop(1).dropLast(1).forEachIndexed { i, _ ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(CircleShape)
                            .background(if (i < step) colors.ink else colors.track),
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            when (current) {
                Step.WELCOME -> Welcome()
                Step.WRITE -> Feature(
                    title = "Write anything, beautifully",
                    body = "Fix grammar, change the tone, translate or have Unicorn draft a message, right where you type, in every app.",
                    sample = listOf("Fix grammar", "Professional", "Friendly", "Translate", "Ask Unicorn"),
                    color = colors.ink,
                )
                Step.REMEMBER -> Feature(
                    title = "Never lose a good AI question",
                    body = "Unicorn can remember the prompts you send to ChatGPT, Claude, Perplexity, Grok and Google, and suggests them again when you start asking something similar.",
                    sample = listOf("best suv for a family of five", "plan 10 days in Japan", "negotiate my salary"),
                    color = colors.glacier,
                )
                Step.DISCOVER -> Feature(
                    title = "See what people near you ask AI",
                    body = "Trending topics and questions in your area, built from anonymous signals only when many people share the same one.",
                    sample = listOf("Japan trip", "Monsoon travel", "iPhone 18", "Home loans"),
                    color = colors.gold,
                )
                Step.SETUP -> Setup()
                Step.YOU -> AboutYou()
                Step.PRIVACY -> PrivacyChoices()
                Step.READY -> Ready()
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Button(
                onClick = {
                    if (current == Step.READY) {
                        prefs.onboardingDone = true
                        onDone()
                    } else {
                        next()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink),
            ) {
                Text(
                    when (current) {
                        Step.WELCOME -> "Get started"
                        Step.READY -> "Open Unicorn"
                        else -> "Continue"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (current in listOf(Step.WRITE, Step.REMEMBER, Step.DISCOVER)) {
                TextButton(onClick = { step = Step.SETUP.ordinal }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Skip intro", color = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    val colors = LocalUnicorn.current
    Spacer(Modifier.height(72.dp))
    UnicornLogo(size = 140.dp)
    Spacer(Modifier.height(24.dp))
    Text("Unicorn", style = MaterialTheme.typography.displayLarge, color = colors.ink)
    Spacer(Modifier.height(12.dp))
    Text(
        "Your everyday AI companion: better writing in every app, and your AI life in one place.",
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text("Free. Private by design.", style = MaterialTheme.typography.bodyLarge, color = colors.muted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Feature(title: String, body: String, sample: List<String>, color: Color) {
    Spacer(Modifier.height(40.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(14.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = LocalUnicorn.current.muted)
    Spacer(Modifier.height(32.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sample.forEach { Tag(it, color) }
    }
}

@Composable
private fun Setup() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ImeStatus.isEnabled(context)) }
    var selected by remember { mutableStateOf(ImeStatus.isSelected(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = ImeStatus.isEnabled(context)
        selected = ImeStatus.isSelected(context)
        onPauseOrDispose { }
    }
    // The picker returns without pausing this screen, so re-check whenever focus comes back
    androidx.compose.runtime.LaunchedEffect(enabled) {
        while (!selected) {
            kotlinx.coroutines.delay(800)
            enabled = ImeStatus.isEnabled(context)
            selected = ImeStatus.isSelected(context)
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("Turn on Unicorn", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(10.dp))
    Text(
        "Unicorn works where you type, in every app.",
        style = MaterialTheme.typography.bodyLarge,
        color = LocalUnicorn.current.muted,
    )
    Spacer(Modifier.height(16.dp))
    AndroidWarningCard()
    Spacer(Modifier.height(24.dp))
    SetupStep("Allow Unicorn in settings", done = enabled) { ImeStatus.openEnableSettings(context) }
    Spacer(Modifier.height(12.dp))
    SetupStep("Choose Unicorn for typing", done = selected, enabled = enabled) { ImeStatus.showPicker(context) }
}

/**
 * Android shows the same alarming dialog for every typing app. Saying so before it appears, and what Unicorn
 * actually does, keeps people from backing out (it is behind about one in eight 1★ reviews of other AI keyboards).
 */
@Composable
private fun AndroidWarningCard() {
    val colors = LocalUnicorn.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(18.dp),
    ) {
        Text("Android will show a warning next", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "“This input method may be able to collect all the text you type, including personal data like " +
                "passwords and credit card numbers.”",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        listOf(
            "Android shows this for every typing app you install, even Google's own. Only the one your phone came with skips it.",
            "Passwords, PINs, codes and card numbers: Unicorn never reads or keeps them.",
            "Unicorn can only ever save prompts in ChatGPT, Claude, Perplexity, Grok and Google, and only if you turn that on in a moment. Every other app is off-limits.",
            "Everything else you type stays on this phone. Text goes to AI only when you tap an AI tool.",
        ).forEach { point ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("✓", Modifier.width(22.dp), color = colors.ink, style = MaterialTheme.typography.bodyMedium)
                Text(point, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SetupStep(label: String, done: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalUnicorn.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (done) colors.ink.copy(alpha = 0.12f) else colors.surface)
            .clickable(enabled = enabled && !done, onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) colors.ink else colors.track),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Text("✓", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled || done) colors.text else colors.muted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutYou() {
    val context = LocalContext.current
    val prefs = remember { Unicorn.get(context).prefs }
    var name by rememberSaveable { mutableStateOf(prefs.displayName) }
    var persona by rememberSaveable { mutableStateOf(prefs.persona) }
    val colors = LocalUnicorn.current
    Spacer(Modifier.height(24.dp))
    Text("A little about you", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(30); prefs.displayName = name.trim() },
        label = { Text("What should Unicorn call you?") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Text("What do you use AI for most?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Work", "Studies", "Business", "Creating content", "Shopping research", "Travel", "Everyday questions")
            .forEach { option ->
                Tag(option, colors.ink, selected = persona == option) {
                    persona = option
                    prefs.persona = option
                }
            }
    }
}

@Composable
private fun PrivacyChoices() {
    val context = LocalContext.current
    val prefs = remember { Unicorn.get(context).prefs }
    var memory by remember { mutableStateOf(prefs.captureConsent && prefs.captureEnabled) }
    var trends by remember { mutableStateOf(prefs.trendsConsent) }
    Spacer(Modifier.height(24.dp))
    Text("Your choices", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Both are off until you turn them on, and you can change them any time in You.",
        style = MaterialTheme.typography.bodyLarge,
        color = LocalUnicorn.current.muted,
    )
    Spacer(Modifier.height(20.dp))
    ChoiceCard(
        title = "Remember my AI prompts",
        points = MemoryDisclosure,
        checked = memory,
    ) {
        memory = it
        prefs.captureConsent = it
        prefs.captureEnabled = it
    }
    Spacer(Modifier.height(14.dp))
    ChoiceCard(
        title = "See and share trends near me",
        points = TrendsDisclosure,
        checked = trends,
    ) {
        trends = it
        prefs.trendsConsent = it
    }
    Spacer(Modifier.height(10.dp))
    SharePreviewCard()
}

/** Prominent disclosure for saving prompts. Keep in sync with the store listing and privacy policy. */
val MemoryDisclosure = listOf(
    "Saves the prompts you send in ChatGPT, Claude, Perplexity, Grok and Google (Search, AI Mode and Gemini), with the app and time. No other app, ever.",
    "Never saves anything from other apps, passwords, numbers, or fields an app marks as private. Drafts you delete or don't send are not saved.",
    "Stays on this phone. Nothing is uploaded and it is not included in device backups.",
)

/** Trends are give-to-get, like read receipts: you see the community's trends only while you share your own signals. */
val TrendsDisclosure = listOf(
    "Works both ways, like read receipts: while this is on you see what people near you ask AI, and your anonymous signals count toward what they see. Turn it off and neither happens.",
    "Sends anonymous summaries of your saved prompts once a day: topics, brands, what kind of help you asked for, and short questions of 12 words or fewer, without names you use, numbers, links or emails. Never longer prompts or conversations.",
    "Uses your approximate area from your network connection (state level). No GPS.",
    "A trend only appears once at least 25 different people share it, so nobody can be singled out.",
)

@Composable
fun ChoiceCard(title: String, points: List<String>, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalUnicorn.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface).padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (checked) "On" else "Off",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (checked) colors.ink else colors.muted,
                )
            }
            Spacer(Modifier.width(12.dp))
            UnicornSwitch(checked = checked, onCheckedChange = null)
        }
        points.forEach {
            Row(Modifier.padding(top = 10.dp)) {
                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(colors.ink))
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
    }
}

@Composable
private fun Ready() {
    val context = LocalContext.current
    val prefs = remember { Unicorn.get(context).prefs }
    val name = prefs.displayName.ifBlank { null }
    Spacer(Modifier.height(80.dp))
    UnicornLogo(size = 96.dp)
    Spacer(Modifier.height(24.dp))
    Text(if (name != null) "You're all set, $name" else "You're all set", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(12.dp))
    Text(
        "Open any app and tap a text field. The Unicorn tools sit right above your keys.",
        style = MaterialTheme.typography.bodyLarge,
        color = LocalUnicorn.current.muted,
    )
}
