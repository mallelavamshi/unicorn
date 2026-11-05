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
import ai.unicorn.glide.GlideSetting
import ai.unicorn.keyboard.RecentMessages
import ai.unicorn.memory.CaptureSource
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Section
import ai.unicorn.ui.UnicornLogo
import ai.unicorn.ui.UnicornSwitch
import ai.unicorn.ui.Tag
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.android.inputmethod.latin.BuildConfig
import com.android.inputmethod.latin.settings.SettingsActivity
import kotlinx.coroutines.launch

@Composable
fun YouScreen(nav: NavHostController) {
    val context = LocalContext.current
    val core = remember { Unicorn.get(context) }
    val prefs = core.prefs
    val colors = LocalUnicorn.current
    var memoryOn by remember { mutableStateOf(prefs.captureConsent && prefs.captureEnabled) }
    var trendsOn by remember { mutableStateOf(prefs.trendsConsent) }
    var aiOn by remember { mutableStateOf(prefs.aiToolsEnabled) }
    var recallOn by remember { mutableStateOf(prefs.recallEnabled) }
    var confirmWipe by remember { mutableStateOf(false) }
    var lockOn by remember { mutableStateOf(prefs.appLock) }
    var lockAfter by remember { mutableIntStateOf(prefs.lockAfterSeconds) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var showCard by remember { mutableStateOf(false) }
    val identity = remember { UnicornIdentity.of(context) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            UnicornLogo(size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(prefs.displayName.ifBlank { "You" }, style = MaterialTheme.typography.headlineLarge)
                if (prefs.persona.isNotBlank()) {
                    Text("Mostly ${prefs.persona.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
            }
        }
        Section("Your Unicorn", subtitle = "${identity.name} #${identity.number}") {
            StardustProgress(prefs.stardust, sharing = trendsOn) { nav.navigate("explore") }
            LinkRow(if (showCard) "Hide my card" else "Show my card") { showCard = !showCard }
            if (showCard) {
                UnicornCard(identity, prefs.stardust)
                LinkRow("Share my Unicorn") { shareUnicornCard(context, identity, prefs.stardust) }
            }
        }
        Section("Privacy") {
            SwitchRow(
                "Lock Unicorn",
                if (lockOn) "Fingerprint, face or your phone's screen lock to open" else "Ask for your fingerprint, face or screen lock to open the app",
                lockOn,
            ) { turnOn ->
                if (!turnOn) {
                    lockOn = false
                    prefs.appLock = false
                    return@SwitchRow
                }
                val activity = context.findFragmentActivity()
                if (activity == null || !AppLock.isAvailable(context)) {
                    lockMessage = "Set up a screen lock on your phone first, then turn this on."
                    return@SwitchRow
                }
                AppLock.authenticate(activity, "Turn on the Unicorn lock", onSuccess = {
                    lockOn = true
                    prefs.appLock = true
                    lockMessage = null
                }, onFailure = { lockMessage = it })
            }
            lockMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                if (!AppLock.isAvailable(context)) LinkRow("Open screen lock settings") { context.startActivity(AppLock.securitySettings()) }
            }
            if (lockOn) {
                Text("Lock again after", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = colors.muted)
                Row(Modifier.padding(vertical = 8.dp)) {
                    listOf(0 to "Right away", 60 to "1 min", 300 to "5 min", 1800 to "30 min").forEach { (seconds, label) ->
                        Tag(label, colors.ink, selected = lockAfter == seconds) {
                            lockAfter = seconds
                            prefs.lockAfterSeconds = seconds
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text("Unicorn also looks blank in your recent apps while the lock is on.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
            }
            SwitchRow("Remember my AI prompts", "Saved on this phone only", memoryOn) {
                memoryOn = it
                prefs.captureConsent = it
                prefs.captureEnabled = it
            }
            LinkRow("Apps Unicorn remembers from") { nav.navigate("apps") }
            SwitchRow("Suggest my earlier questions", "While you type in AI apps", recallOn) {
                recallOn = it
                prefs.recallEnabled = it
            }
            SwitchRow("See and share trends near me", "Both ways, like read receipts. Anonymous summaries only", trendsOn) {
                trendsOn = it
                prefs.trendsConsent = it
            }
            SharePreviewCard()
            Spacer(Modifier.height(6.dp))
            LinkRow("Delete everything Unicorn remembers", danger = true) { confirmWipe = true }
        }
        Section("Writing") {
            SwitchRow("Writing tools above the keys", "Grammar, tone, translate and Ask Unicorn", aiOn) {
                aiOn = it
                prefs.aiToolsEnabled = it
            }
            if (GlideSetting.isAvailable(context)) {
                var glideOn by remember { mutableStateOf(GlideSetting.isOn(context)) }
                SwitchRow("Glide typing", "Slide your finger from letter to letter to type a word", glideOn) {
                    glideOn = it
                    GlideSetting.set(context, it)
                }
            }
            KeyFeedbackPicker()
            LinkRow("Your words and shortcuts") { nav.navigate("words") }
            val repliesOn = remember { RecentMessages.isAllowed(context) }
            LinkRow(
                if (repliesOn) "Replies read your latest chat and email notifications · On"
                else "Let /reply read your latest chat and email notifications"
            ) { context.startActivity(RecentMessages.settingsIntent(context)) }
            LinkRow("Typing preferences") {
                context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Section("Look", subtitle = "Pick a theme for your keys") {}
        KeysThemePicker()
        if (ai.unicorn.keyboard.KeyArea.ENABLED) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                LinkRow("Size and position") { nav.navigate("size") }
            }
        }
        Section("Your history") {
            LinkRow("Bring in chats from ChatGPT, Claude, Gemini or Perplexity") { nav.navigate("import") }
        }
        Section("Help") {
            LinkRow("Saving activity log") { nav.navigate("diagnostics") }
            Text("Unicorn ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            if (GlideSetting.isAvailable(context)) {
                Text("Glide typing learns word order from Tatoeba.org sentences (CC BY 2.0 FR)", style = MaterialTheme.typography.labelSmall, color = colors.muted)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("All remembered prompts and conversations will be removed from this phone, and saving turns off.") },
            confirmButton = {
                TextButton(onClick = {
                    core.memory?.deleteAll()
                    prefs.captureEnabled = false
                    prefs.captureConsent = false
                    memoryOn = false
                    confirmWipe = false
                }) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalUnicorn.current.muted)
            }
        }
        UnicornSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun LinkRow(title: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        title,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (danger) androidx.compose.ui.graphics.Color(0xFFC0392B) else LocalUnicorn.current.ink,
    )
}

@Composable
internal fun SubScreen(nav: NavHostController, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.padding(horizontal = 20.dp)) { content() }
    }
}

@Composable
fun AppsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { Unicorn.get(context).prefs }
    val enabled = prefs.captureConsent && prefs.captureEnabled
    SubScreen(nav, "Apps") {
        Text(
            if (enabled) "Unicorn remembers the prompts you send in these apps. Everything else you type is never kept."
            else "Turn on Remember my AI prompts in You to choose apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalUnicorn.current.muted,
        )
        CaptureSource.entries.forEach { source ->
            var on by remember { mutableStateOf(prefs.isSourceEnabled(source)) }
            SwitchRow(
                source.label,
                if (source.searchBarOnly) "Only the address bar, web addresses are skipped" else null,
                on,
                enabled = enabled,
            ) {
                on = it
                prefs.setSourceEnabled(source, it)
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val diagnostics = remember { Unicorn.get(context).diagnostics }
    var lines by remember { mutableStateOf(diagnostics.snapshot()) }
    SubScreen(nav, "Saving activity") {
        Text(
            "What Unicorn decided each time you used an AI app. It never contains what you typed, only the app, the type of field and why something was or wasn't saved.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalUnicorn.current.muted,
        )
        Row(Modifier.padding(vertical = 8.dp)) {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
                context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Share log") }
            TextButton(onClick = { diagnostics.clear(); lines = emptyList() }) { Text("Clear") }
        }
        if (lines.isEmpty()) Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium)
        lines.asReversed().forEach { Text(it, Modifier.padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium) }
    }
}

private val ImportGuides = listOf(
    "ChatGPT" to "Settings, Data controls, Export data. Download the zip from the email on this phone and choose it here.",
    "Claude" to "Settings, Privacy, Export data. Download the zip from the email and choose it here.",
    "Gemini" to "takeout.google.com: select only My Activity, choose JSON format and Gemini Apps. Choose the Takeout zip here. Google exports your prompts, not Gemini's answers.",
    "Perplexity" to "Open a thread, then Export as Markdown, and choose the file here. You can also share any answer or link to Unicorn.",
)

@Composable
fun ImportScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        running = true
        result = null
        scope.launch {
            result = runCatching { ImportRunner.importUri(context, uri) { progress = it }.describe() }
                .getOrElse { "That file couldn't be read. Choose the zip or JSON file from your export." }
            running = false
        }
    }
    SubScreen(nav, "Bring in chats") {
        Text(
            "Add the conversations you already had, answers included. Files are read on this phone and nothing is uploaded. Bringing in the same file twice skips what's already here.",
            style = MaterialTheme.typography.bodyLarge,
            color = LocalUnicorn.current.muted,
        )
        Button(
            onClick = { picker.launch(arrayOf("application/zip", "application/json", "text/*", "application/octet-stream")) },
            enabled = !running,
            modifier = Modifier.padding(vertical = 16.dp),
        ) { Text("Choose export file") }
        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("$progress conversations so far", Modifier.padding(top = 8.dp))
        }
        result?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
        ImportGuides.forEach { (app, steps) ->
            Text(app, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
            Text(steps, style = MaterialTheme.typography.bodyMedium, color = LocalUnicorn.current.muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}
