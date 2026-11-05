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

package ai.unicorn.words

import android.content.Context
import android.os.UserManager
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A word the user typed or added. Only single words are kept, never sentences, and only on this phone. */
@Entity(tableName = "personal_word")
data class PersonalWord(
    /** Lower case, the key. */
    @PrimaryKey val key: String,
    /** As the user writes it most of the time, "Gachibowli" or "iPhone". */
    val word: String,
    val count: Int,
    @ColumnInfo(name = "first_used") val firstUsed: Long,
    @ColumnInfo(name = "last_used") val lastUsed: Long,
    /** Not in the dictionary: a name, a brand, slang, a word in another language. */
    @ColumnInfo(name = "is_new") val isNew: Boolean,
    /** Written with a capital in the middle of a sentence: a person, place or product. */
    @ColumnInfo(name = "is_name") val isName: Boolean,
    /** Added by hand on the words page; always suggested. */
    val added: Boolean = false,
)

/** "omw" becomes "On my way!": shown first while typing, and swapped in on space. */
@Entity(tableName = "text_shortcut")
data class TextShortcut(
    @PrimaryKey val trigger: String,
    val expansion: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface PersonalWordsDao {
    @Query("SELECT * FROM personal_word WHERE `key` = :key")
    fun get(key: String): PersonalWord?

    @Upsert
    fun upsert(word: PersonalWord)

    @Query("SELECT * FROM personal_word ORDER BY count DESC, last_used DESC LIMIT :limit")
    fun mostUsed(limit: Int): List<PersonalWord>

    @Query(
        "SELECT * FROM personal_word WHERE is_new = 1 OR is_name = 1 OR added = 1 " +
            "ORDER BY added DESC, last_used DESC LIMIT :limit"
    )
    fun newWords(limit: Int): List<PersonalWord>

    /** Words fed to suggestions: added by hand, or new words and names used at least twice. */
    @Query("SELECT word FROM personal_word WHERE added = 1 OR ((is_new = 1 OR is_name = 1) AND count >= 2)")
    fun suggestionWords(): List<String>

    /** Names and words the dictionary doesn't know: kept out of anything shared, even as part of a question. */
    @Query("SELECT word FROM personal_word WHERE is_name = 1 OR is_new = 1 OR added = 1")
    fun privateWords(): List<String>

    @Query("SELECT COUNT(*) FROM personal_word")
    fun count(): Int

    @Query("SELECT COUNT(*) FROM personal_word WHERE is_new = 1 OR is_name = 1 OR added = 1")
    fun newCount(): Int

    @Query("DELETE FROM personal_word WHERE `key` = :key")
    fun delete(key: String)

    @Query("DELETE FROM personal_word")
    fun deleteAll()

    @Query("SELECT * FROM text_shortcut ORDER BY trigger")
    fun shortcuts(): List<TextShortcut>

    @Upsert
    fun upsertShortcut(shortcut: TextShortcut)

    @Query("DELETE FROM text_shortcut WHERE trigger = :trigger")
    fun deleteShortcut(trigger: String)
}

@Database(entities = [PersonalWord::class, TextShortcut::class], version = 1)
abstract class PersonalWordsDatabase : RoomDatabase() {
    abstract fun dao(): PersonalWordsDao
}

/**
 * The user's own words: the ones they use most, new words and names the dictionary doesn't know, and their text
 * shortcuts. Learned from what they type in every app except password and incognito fields, and kept on this phone.
 * New words and names flow back into suggestions through the engine's user dictionary.
 */
object PersonalWords {
    private const val DATABASE = "unicorn_words"
    private const val MAX_WORD = 32
    private val WORD = Regex("^\\p{L}[\\p{L}\\p{M}'’-]*\\p{L}$")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "unicorn-words").apply { isDaemon = true } }
    private val listeners = CopyOnWriteArrayList<Runnable>()

    @Volatile
    private var database: PersonalWordsDatabase? = null

    private val _revision = MutableStateFlow(0L)
    /** Bumped after every change, for the words page. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Null until the phone is unlocked after boot: the words live in credential-protected storage. */
    fun dao(context: Context): PersonalWordsDao? {
        database?.let { return it.dao() }
        val app = context.applicationContext
        if (app.getSystemService(UserManager::class.java)?.isUserUnlocked == false) return null
        return synchronized(this) {
            database ?: Room.databaseBuilder(app, PersonalWordsDatabase::class.java, DATABASE).build().also {
                database = it
            }
        }.dao()
    }

    /** Engine dictionaries register here to reload when the words change. */
    @JvmStatic
    fun addChangeListener(listener: Runnable) = listeners.add(listener)

    @JvmStatic
    fun removeChangeListener(listener: Runnable) = listeners.remove(listener)

    /** For the engine's user dictionary; runs on its loading thread. */
    @JvmStatic
    fun wordsForSuggestions(context: Context): List<String> =
        runCatching { dao(context)?.suggestionWords().orEmpty() }.getOrDefault(emptyList())

    /** Whether a typed word is worth keeping: letters only (no numbers, codes, links or emails). */
    fun isLearnable(word: String): Boolean = word.length in 2..MAX_WORD && WORD.matches(word)

    /** [insisted]: the user undid an autocorrection to keep this word, so it is suggested from now on. */
    fun record(context: Context, word: String, sentenceStart: Boolean, known: Boolean, insisted: Boolean = false) {
        if (!isLearnable(word)) return
        val now = System.currentTimeMillis()
        executor.execute {
            val dao = dao(context) ?: return@execute
            val key = word.lowercase(Locale.ROOT)
            val existing = dao.get(key)
            val capitalized = word.first().isUpperCase() && word.drop(1).any { it.isLowerCase() }
            val isName = capitalized && !sentenceStart
            val bump = if (insisted) 2 else 1
            val updated = existing?.copy(
                // Keep the spelling the user uses when not starting a sentence
                word = if (sentenceStart) existing.word else word,
                count = existing.count + bump,
                lastUsed = now,
                isNew = (existing.isNew && !known) || insisted,
                isName = existing.isName || isName,
            ) ?: PersonalWord(key, word, bump, now, now, isNew = !known || insisted, isName = isName)
            dao.upsert(updated)
            // A new word or name joins suggestions on its second use (at once when the user insisted on it)
            val eligible = updated.count >= 2 && (updated.isNew || updated.isName)
            val wasEligible = existing != null && existing.count >= 2 && (existing.isNew || existing.isName)
            changed(suggestionsChanged = eligible && !wasEligible)
        }
    }

    fun add(context: Context, word: String) = write(context) {
        val clean = word.trim()
        if (!isLearnable(clean)) return@write
        val now = System.currentTimeMillis()
        val key = clean.lowercase(Locale.ROOT)
        val existing = it.get(key)
        it.upsert(existing?.copy(word = clean, added = true) ?: PersonalWord(key, clean, 0, now, now, isNew = true, isName = false, added = true))
    }

    fun delete(context: Context, key: String) = write(context) { it.delete(key) }

    fun deleteAll(context: Context) = write(context) { it.deleteAll() }

    fun saveShortcut(context: Context, trigger: String, expansion: String) = write(context) {
        val t = trigger.trim().lowercase(Locale.ROOT)
        val e = expansion.trim()
        if (t.isEmpty() || e.isEmpty() || t.contains(' ')) return@write
        it.upsertShortcut(TextShortcut(t, e, System.currentTimeMillis()))
    }

    fun deleteShortcut(context: Context, trigger: String) = write(context) { it.deleteShortcut(trigger) }

    /** Loads the shortcuts into memory for the keys; called whenever the keys show. */
    fun loadShortcuts(context: Context) = executor.execute {
        val dao = dao(context) ?: return@execute
        Shortcuts.set(dao.shortcuts().associate { it.trigger to it.expansion })
    }

    private fun write(context: Context, block: (PersonalWordsDao) -> Unit) = executor.execute {
        val dao = dao(context) ?: return@execute
        block(dao)
        Shortcuts.set(dao.shortcuts().associate { it.trigger to it.expansion })
        changed(suggestionsChanged = true)
    }

    private fun changed(suggestionsChanged: Boolean) {
        _revision.value++
        if (suggestionsChanged) listeners.forEach { it.run() }
    }

    /** Runs a read off the main thread, for the words page. */
    fun <T> read(context: Context, block: (PersonalWordsDao) -> T, done: (T?) -> Unit) = executor.execute {
        done(dao(context)?.let(block))
    }
}
