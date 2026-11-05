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
import ai.unicorn.memory.CaptureSource
import ai.unicorn.memory.MemoryImporter
import ai.unicorn.memory.MemoryOrigin
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Imports export files and shared text into the memory, for the import screen and the share target. */
object ImportRunner {
    private const val BATCH_SIZE = 100

    data class Summary(val conversations: Int, val messages: Int, val skipped: Int, val origins: Set<MemoryOrigin>) {
        fun describe(): String = when {
            conversations == 0 && skipped == 0 -> "No conversations found in this file. Choose the zip or JSON from your export."
            conversations == 0 -> "Already here: all $skipped conversations were brought in before."
            else -> buildString {
                append("Added $conversations conversations with $messages messages")
                if (origins.isNotEmpty()) append(" from ${origins.joinToString(" and ") { it.label }}")
                if (skipped > 0) append(". Skipped $skipped already here")
                append('.')
            }
        }
    }

    suspend fun importUri(context: Context, uri: Uri, onProgress: (Int) -> Unit = {}): Summary = withContext(Dispatchers.IO) {
        val memory = Unicorn.get(context).memory ?: error("locked")
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        var conversations = 0
        var messages = 0
        var skipped = 0
        val origins = LinkedHashSet<MemoryOrigin>()
        val input = context.contentResolver.openInputStream(uri) ?: error("unreadable")
        input.use {
            MemoryImporter.parse(name, it).chunked(BATCH_SIZE).forEach { batch ->
                batch.groupBy({ it.first }, { it.second }).forEach { (origin, items) ->
                    val result = memory.importConversations(origin, items)
                    conversations += result.conversations
                    messages += result.messages
                    skipped += result.skipped
                    origins.add(origin)
                }
                withContext(Dispatchers.Main) { onProgress(conversations + skipped) }
            }
        }
        Summary(conversations, messages, skipped, origins)
    }

    suspend fun importSharedText(context: Context, text: String, subject: String?, senderPackage: String?): Summary {
        val memory = Unicorn.get(context).memory ?: error("locked")
        val conversation = MemoryImporter.fromSharedText(text, subject, CaptureSource.fromPackageName(senderPackage)?.id)
            ?: return Summary(0, 0, 0, emptySet())
        val result = memory.importConversations(MemoryOrigin.SHARE, listOf(conversation))
        return Summary(result.conversations, result.messages, result.skipped, setOf(MemoryOrigin.SHARE))
    }
}

/** "Unicorn" in the share menu: saves shared answers, links and export files, then closes. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }
        val sender = referrer?.takeIf { it.scheme == "android-app" }?.host
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        lifecycleScope.launch {
            val message = runCatching {
                when {
                    stream != null -> ImportRunner.importUri(applicationContext, stream).describe()
                    !text.isNullOrBlank() -> {
                        val summary = ImportRunner.importSharedText(applicationContext, text, subject, sender)
                        if (summary.skipped > 0) "Already saved in Unicorn" else "Saved to Unicorn"
                    }
                    else -> "Nothing to save"
                }
            }.getOrElse { "Couldn't save that. Try sharing it again." }
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
