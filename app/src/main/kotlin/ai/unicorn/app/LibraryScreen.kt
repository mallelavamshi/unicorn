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

import ai.unicorn.memory.MemoryConversationSummary
import ai.unicorn.memory.MemoryMessageRow
import ai.unicorn.memory.MemoryOrigin
import ai.unicorn.memory.MemoryRole
import ai.unicorn.memory.SearchResult
import ai.unicorn.memory.sourceLabel
import ai.unicorn.ui.EmptyHint
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Tag
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun LibraryScreen(nav: NavHostController) {
    val memory = rememberMemory()
    val colors = LocalUnicorn.current
    val revision by (memory?.revision ?: MutableStateFlow(0L)).collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var app by rememberSaveable { mutableStateOf<String?>(null) }
    val conversations by produceState<List<MemoryConversationSummary>?>(null, revision) {
        value = memory?.read { getConversationSummaries(500, 0) } ?: emptyList()
    }
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    LaunchedEffect(query, revision) {
        results = null
        if (query.isNotBlank()) {
            delay(150)
            results = memory?.search(query) ?: emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = { nav.navigate("import") }) { Text("Bring in chats") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text("Search words or meaning") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )
        val all = conversations
        val apps = all?.map { it.sourceId }?.distinct().orEmpty()
        if (query.isBlank() && apps.size > 1) {
            LazyRow(
                Modifier.padding(vertical = 4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Tag("All", colors.ink, selected = app == null) { app = null } }
                items(apps) { id -> Tag(sourceLabel(id), colors.ink, selected = app == id) { app = id } }
            }
        }
        when {
            query.isNotBlank() -> {
                val found = results
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    when {
                        found == null -> item { Text("Searching", color = colors.muted) }
                        found.isEmpty() -> item { EmptyHint("Nothing found for \"${query.trim()}\". Try other words, or describe what it was about.") }
                        else -> items(found, key = { it.row.id }) { result ->
                            val row = result.row
                            val trailing = when {
                                result.match == SearchResult.Match.MEANING -> "${(result.score * 100).toInt()}% similar"
                                row.role == MemoryRole.ASSISTANT -> "match in answer"
                                else -> "match"
                            }
                            PromptRow(row, trailing = trailing, text = result.snippet) {
                                nav.navigate("conversation/${row.conversationId}")
                            }
                        }
                    }
                }
            }
            all == null -> Text("Loading", Modifier.padding(20.dp), color = colors.muted)
            all.isEmpty() -> Column(Modifier.padding(20.dp)) {
                EmptyHint(
                    "Your AI conversations show up here: the questions you ask with Unicorn, and history you bring in from ChatGPT, Claude or Gemini.",
                    action = "Bring in past chats",
                    onAction = { nav.navigate("import") },
                )
            }
            else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                items(all.filter { app == null || it.sourceId == app }, key = { it.id }) { conversation ->
                    ConversationRow(conversation) { nav.navigate("conversation/${conversation.id}") }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: MemoryConversationSummary, onClick: () -> Unit) {
    val colors = LocalUnicorn.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(
            conversation.title?.takeIf { it.isNotBlank() } ?: conversation.firstPrompt ?: "Untitled",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${sourceLabel(conversation.sourceId)}, ${conversation.messageCount} " +
                "${if (conversation.messageCount == 1) "message" else "messages"}, " +
                DateUtils.getRelativeTimeSpanString(conversation.updatedAt).toString().lowercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
    }
}

@Composable
fun ConversationScreen(nav: NavHostController, conversationId: Long) {
    val memory = rememberMemory()
    val colors = LocalUnicorn.current
    val revision by (memory?.revision ?: MutableStateFlow(0L)).collectAsState()
    val messages by produceState<List<MemoryMessageRow>?>(null, revision) {
        value = memory?.read { getConversationMessages(conversationId) } ?: emptyList()
    }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            val first = messages?.firstOrNull()
            Column(Modifier.weight(1f)) {
                Text(first?.let { sourceLabel(it.sourceId) } ?: "", style = MaterialTheme.typography.titleMedium)
                Text(
                    first?.let { MemoryOrigin.fromId(it.origin)?.label } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.muted,
                )
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete conversation") }
        }
        val list = messages ?: return@Column
        if (list.isNotEmpty() && list.none { it.role == MemoryRole.ASSISTANT }) {
            Text(
                "Only your questions are here. Bring in this app's history to see the answers too.",
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(list, key = { it.id }) { message ->
                val mine = message.role != MemoryRole.ASSISTANT
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp))
                            .background(if (mine) colors.ink else colors.surface)
                            .padding(14.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (mine) androidx.compose.ui.graphics.Color.White else colors.text,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this conversation?") },
            text = { Text("It will be removed from this phone for good.") },
            confirmButton = {
                TextButton(onClick = {
                    memory?.deleteConversation(conversationId)
                    confirmDelete = false
                    nav.popBackStack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
