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
import ai.unicorn.ui.EmptyHint
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.ShareRow
import ai.unicorn.ui.Tag
import ai.unicorn.words.PersonalWord
import ai.unicorn.words.PersonalWords
import ai.unicorn.words.Shortcuts
import ai.unicorn.words.TextShortcut
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private data class WordsState(
    val total: Int,
    val newWords: List<PersonalWord>,
    val mostUsed: List<PersonalWord>,
    val shortcuts: List<TextShortcut>,
)

// Too common to say anything about the person
private val Common = setOf(
    "the", "and", "you", "that", "this", "for", "are", "with", "have", "not", "but", "was", "your", "can", "will",
    "what", "all", "just", "from", "they", "there", "about", "would", "how", "out", "get", "its", "it's", "i'm",
    "yes", "okay", "then", "when", "also", "been", "were", "has", "had", "him", "her", "she", "his", "our", "them",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { Unicorn.get(context).prefs }
    val colors = LocalUnicorn.current
    val revision by PersonalWords.revision.collectAsState()
    var learn by remember { mutableStateOf(prefs.learnWords) }
    var addShortcut by remember { mutableStateOf(false) }
    var addWord by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    val state by produceState<WordsState?>(null, revision) {
        value = suspendCancellableCoroutine { done ->
            PersonalWords.read(context, { dao ->
                WordsState(
                    total = dao.count(),
                    newWords = dao.newWords(60),
                    mostUsed = dao.mostUsed(120).filter { it.count > 0 && it.key !in Common && it.key.length > 2 }.take(25),
                    shortcuts = dao.shortcuts(),
                )
            }) { done.resume(it) }
        }
    }

    SubScreen(nav, "Your words") {
        Text(
            "Unicorn learns the words you use, so suggestions sound like you. Single words only, kept on this " +
                "phone, and never from password or incognito fields.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        SwitchRow("Learn my words", "New words and names join your suggestions after you use them twice", learn) {
            learn = it
            prefs.learnWords = it
        }
        val s = state
        if (s != null) {
            Text(
                "${s.total} words learned · ${s.newWords.size} new words and names · ${s.shortcuts.size} shortcuts",
                Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
            )
        }

        Heading("Shortcuts", "Type the short form and press space")
        s?.shortcuts?.forEach { shortcut ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(shortcut.trigger, style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Text("  →  ${shortcut.expansion}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(
                    "Remove",
                    Modifier.clickable { PersonalWords.deleteShortcut(context, shortcut.trigger) }.padding(8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.muted,
                )
            }
        }
        val presets = Shortcuts.Presets.filter { (t, _) -> s?.shortcuts?.none { it.trigger == t } != false }
        if (presets.isNotEmpty()) {
            Text("Tap to add", Modifier.padding(top = 6.dp, bottom = 6.dp), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (trigger, expansion) ->
                    Tag("$trigger → $expansion", colors.ink) { PersonalWords.saveShortcut(context, trigger, expansion) }
                }
            }
        }
        LinkRow("Add a shortcut") { addShortcut = true }

        Heading("New words and names", "Words the dictionary didn't know, and names you write")
        val newWords = s?.newWords.orEmpty()
        if (s != null && newWords.isEmpty()) {
            EmptyHint("Names, places and brands you type show up here, like a friend's name or your street.")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                newWords.forEach { word ->
                    WordTag(word) {
                        PersonalWords.delete(context, word.key)
                        Unicorn.forgetLearnedWord(word.word)
                    }
                }
            }
        }
        LinkRow("Add a word") { addWord = true }

        Heading("Most used", null)
        val mostUsed = s?.mostUsed.orEmpty()
        if (s != null && mostUsed.isEmpty()) {
            EmptyHint("Start typing in any app. Your most used words show up here.")
        } else {
            val max = mostUsed.maxOfOrNull { it.count } ?: 1
            mostUsed.forEach { ShareRow(it.word, it.count, max, colors.glacier) }
        }
        Spacer(Modifier.height(12.dp))
        LinkRow("Forget all learned words", danger = true) { confirmForget = true }
        Spacer(Modifier.height(24.dp))
    }

    if (addShortcut) {
        var trigger by remember { mutableStateOf("") }
        var expansion by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addShortcut = false },
            title = { Text("New shortcut") },
            text = {
                Column {
                    OutlinedTextField(trigger, { trigger = it.replace(" ", "") }, label = { Text("Shortcut, like omw") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(expansion, { expansion = it }, label = { Text("Becomes, like On my way!") })
                }
            },
            confirmButton = {
                TextButton(enabled = trigger.isNotBlank() && expansion.isNotBlank(), onClick = {
                    PersonalWords.saveShortcut(context, trigger, expansion)
                    addShortcut = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { addShortcut = false }) { Text("Cancel") } },
        )
    }
    if (addWord) {
        var word by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addWord = false },
            title = { Text("Add a word") },
            text = {
                OutlinedTextField(word, { word = it.trim() }, label = { Text("A name, place or word") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = PersonalWords.isLearnable(word), onClick = {
                    PersonalWords.add(context, word)
                    addWord = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addWord = false }) { Text("Cancel") } },
        )
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget all learned words?") },
            text = { Text("Your word list is cleared. Shortcuts stay.") },
            confirmButton = {
                TextButton(onClick = {
                    PersonalWords.deleteAll(context)
                    confirmForget = false
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Heading(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 22.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalUnicorn.current.muted)
        }
    }
}

@Composable
private fun WordTag(word: PersonalWord, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val label = if (word.added) "${word.word} ✓" else word.word
    Tag(label, LocalUnicorn.current.ink) { confirm = true }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(word.word) },
            text = {
                Text(
                    if (word.added) "You added this word." else "Used ${word.count} time${if (word.count == 1) "" else "s"}." +
                        " Remove it and Unicorn stops suggesting it."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove()
                    confirm = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
        )
    }
}
