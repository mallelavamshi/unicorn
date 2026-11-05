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

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

const val MEMORY_DATABASE_NAME = "unicorn_memory"
private const val CONVERSATION_TABLE = "memory_conversation"
private const val MESSAGE_TABLE = "memory_message"

/** Where a conversation came from. */
enum class MemoryOrigin(val id: String, val label: String) {
    KEYBOARD("keyboard", "Keyboard"),
    CHATGPT_EXPORT("chatgpt_export", "ChatGPT export"),
    CLAUDE_EXPORT("claude_export", "Claude export"),
    GEMINI_TAKEOUT("gemini_takeout", "Google Takeout (Gemini)"),
    PERPLEXITY_EXPORT("perplexity_export", "Perplexity thread"),
    SHARE("share", "Shared to Unicorn");

    companion object {
        fun fromId(id: String): MemoryOrigin? = entries.firstOrNull { it.id == id }
    }
}

object MemoryRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    /** Text shared into Unicorn where it is unknown who wrote it. */
    const val SHARED = "shared"
}

/** Source id for imported or shared content whose app is not in [CaptureSource]. */
const val OTHER_SOURCE_ID = "other"

fun sourceLabel(sourceId: String): String = when (sourceId) {
    OTHER_SOURCE_ID -> "Other"
    else -> CaptureSource.fromId(sourceId)?.label ?: sourceId
}

@Entity(
    tableName = CONVERSATION_TABLE,
    indices = [Index(value = ["origin", "external_id"], unique = true), Index(value = ["updated_at"])],
)
data class MemoryConversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val origin: String,
    /** Id of the conversation in the exporting app, used to skip duplicates on re-import. Null for keyboard. */
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    val title: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/**
 * A single message. Prompts (user and shared messages) carry an int8 embedding (see [MemoryRepository]); assistant
 * replies are stored for display only.
 */
@Entity(
    tableName = MESSAGE_TABLE,
    foreignKeys = [
        ForeignKey(
            entity = MemoryConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversation_id"]), Index(value = ["created_at"])],
)
class MemoryMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    val role: String,
    val text: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** int8 vector followed by its float32 scale (little endian), or null until embedded. */
    val embedding: ByteArray? = null,
    /**
     * Whether the user explicitly marked this prompt for anonymous contribution. Nothing in this build uploads
     * messages; the flag only records the choice for a later, separately consented sync.
     */
    @ColumnInfo(name = "contribute_consent")
    val contributeConsent: Boolean = false,
)

/** A message joined with its conversation's source, used by every list in the Unicorn app. */
data class MemoryMessageRow(
    val id: Long,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    val role: String,
    val text: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "contribute_consent")
    val contributeConsent: Boolean,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val origin: String,
)

data class MemoryConversationSummary(
    val id: Long,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val origin: String,
    val title: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "message_count")
    val messageCount: Int,
    @ColumnInfo(name = "first_prompt")
    val firstPrompt: String?,
)

class MemoryEmbeddingRow(
    val id: Long,
    val text: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val embedding: ByteArray?,
)

data class MemoryMetricRow(
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    val role: String,
    val length: Int,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
)

private const val MESSAGE_ROW_SELECT = """
    SELECT m.id, m.conversation_id, m.role, m.text, m.created_at, m.contribute_consent, c.source_id, c.origin
    FROM $MESSAGE_TABLE m JOIN $CONVERSATION_TABLE c ON c.id = m.conversation_id
"""

@Dao
abstract class MemoryDao {
    @Insert
    abstract fun insertConversation(conversation: MemoryConversation): Long

    @Insert
    abstract fun insertMessage(message: MemoryMessage): Long

    @Insert
    abstract fun insertMessages(messages: List<MemoryMessage>): List<Long>

    @Query("SELECT * FROM $CONVERSATION_TABLE WHERE origin = :origin AND external_id = :externalId LIMIT 1")
    abstract fun findConversation(origin: String, externalId: String): MemoryConversation?

    @Query(
        "SELECT * FROM $CONVERSATION_TABLE WHERE origin = 'keyboard' AND source_id = :sourceId " +
            "ORDER BY updated_at DESC LIMIT 1"
    )
    abstract fun latestKeyboardConversation(sourceId: String): MemoryConversation?

    @Query("UPDATE $CONVERSATION_TABLE SET updated_at = :updatedAt WHERE id = :id")
    abstract fun touchConversation(id: Long, updatedAt: Long)

    @Transaction
    open fun insertConversationWithMessages(conversation: MemoryConversation, messages: List<MemoryMessage>): List<Long> {
        val conversationId = insertConversation(conversation)
        return insertMessages(messages.map {
            MemoryMessage(
                conversationId = conversationId,
                role = it.role,
                text = it.text,
                createdAt = it.createdAt,
                embedding = it.embedding,
            )
        })
    }

    @Query(
        "SELECT m.id, m.text, m.created_at, c.source_id, m.embedding FROM $MESSAGE_TABLE m " +
            "JOIN $CONVERSATION_TABLE c ON c.id = m.conversation_id " +
            "WHERE m.role != 'assistant' ORDER BY m.created_at DESC LIMIT :limit"
    )
    abstract fun getPromptsForIndex(limit: Int): List<MemoryEmbeddingRow>

    @Query("UPDATE $MESSAGE_TABLE SET embedding = :embedding WHERE id = :id")
    abstract fun setEmbedding(id: Long, embedding: ByteArray)

    @Query("$MESSAGE_ROW_SELECT WHERE m.id IN (:ids)")
    abstract fun getMessageRows(ids: List<Long>): List<MemoryMessageRow>

    @Query("$MESSAGE_ROW_SELECT WHERE m.role != 'assistant' ORDER BY m.created_at DESC LIMIT :limit OFFSET :offset")
    abstract fun getTimeline(limit: Int, offset: Int): List<MemoryMessageRow>

    /** Messages, questions before answers and newest first, whose text matches a LIKE pattern escaped with ''. */
    @Query(
        "$MESSAGE_ROW_SELECT WHERE m.text LIKE :pattern ESCAPE '\\' " +
            "ORDER BY m.role = 'assistant', m.created_at DESC LIMIT :limit"
    )
    abstract fun searchText(pattern: String, limit: Int): List<MemoryMessageRow>

    @Query("$MESSAGE_ROW_SELECT WHERE m.conversation_id = :conversationId ORDER BY m.created_at ASC, m.id ASC")
    abstract fun getConversationMessages(conversationId: Long): List<MemoryMessageRow>

    @Query(
        """
        SELECT c.id, c.source_id, c.origin, c.title, c.created_at, c.updated_at,
            (SELECT COUNT(*) FROM $MESSAGE_TABLE m WHERE m.conversation_id = c.id) AS message_count,
            (SELECT m.text FROM $MESSAGE_TABLE m WHERE m.conversation_id = c.id AND m.role != 'assistant'
                ORDER BY m.created_at ASC, m.id ASC LIMIT 1) AS first_prompt
        FROM $CONVERSATION_TABLE c ORDER BY c.updated_at DESC LIMIT :limit OFFSET :offset
        """
    )
    abstract fun getConversationSummaries(limit: Int, offset: Int): List<MemoryConversationSummary>

    @Query(
        "SELECT m.created_at, m.role, LENGTH(m.text) AS length, c.source_id, m.conversation_id " +
            "FROM $MESSAGE_TABLE m JOIN $CONVERSATION_TABLE c ON c.id = m.conversation_id"
    )
    abstract fun getMetricRows(): List<MemoryMetricRow>

    @Query("UPDATE $MESSAGE_TABLE SET contribute_consent = :consent WHERE id = :id")
    abstract fun setContributeConsent(id: Long, consent: Boolean)

    @Query("SELECT id FROM $MESSAGE_TABLE WHERE conversation_id = :conversationId")
    abstract fun getMessageIds(conversationId: Long): List<Long>

    @Query("DELETE FROM $MESSAGE_TABLE WHERE id = :id")
    abstract fun deleteMessage(id: Long)

    @Query("DELETE FROM $MESSAGE_TABLE WHERE conversation_id = :conversationId")
    abstract fun deleteMessagesOf(conversationId: Long)

    @Query("DELETE FROM $CONVERSATION_TABLE WHERE id = :id")
    abstract fun deleteConversationRow(id: Long)

    @Transaction
    open fun deleteConversation(id: Long) {
        deleteMessagesOf(id)
        deleteConversationRow(id)
    }

    @Query(
        "DELETE FROM $CONVERSATION_TABLE WHERE NOT EXISTS " +
            "(SELECT 1 FROM $MESSAGE_TABLE m WHERE m.conversation_id = $CONVERSATION_TABLE.id)"
    )
    abstract fun deleteEmptyConversations()

    @Query("DELETE FROM $MESSAGE_TABLE")
    abstract fun deleteAllMessages()

    @Query("DELETE FROM $CONVERSATION_TABLE")
    abstract fun deleteAllConversations()

    @Transaction
    open fun deleteAll() {
        deleteAllMessages()
        deleteAllConversations()
    }
}

@Database(entities = [MemoryConversation::class, MemoryMessage::class], version = 1)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun promptMemoryDao(): MemoryDao

    companion object {
        /** [path] is an absolute database file path, see MemoryRepository. */
        fun new(context: Context, path: String): MemoryDatabase {
            return Room
                .databaseBuilder(context, MemoryDatabase::class.java, path)
                .build()
        }
    }
}
