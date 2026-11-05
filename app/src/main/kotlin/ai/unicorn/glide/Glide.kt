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

package ai.unicorn.glide

import ai.unicorn.words.PersonalWords
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.BuildConfig
import com.android.inputmethod.latin.Dictionary
import com.android.inputmethod.latin.DictionaryFacilitator
import com.android.inputmethod.latin.NgramContext
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.SuggestedWords
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import com.android.inputmethod.latin.common.ComposedData
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.InputPointers
import com.android.inputmethod.latin.define.DecoderSpecificConstants
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion
import com.android.inputmethod.latin.utils.SuggestionResults
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.min

/**
 * Glide typing for the engine: the engine's batch input (gesture) path calls [suggest] instead of its native gesture
 * decoder, which is a stub in AOSP. Loads the dictionary's words for [GlideDecoder] in the background when the keys
 * first start, and keeps the user's own words and most-used words fresh.
 */
object Glide {
    const val TAG = "UnicornGlide"
    /** The emulator test app: only there are swiped words written to the debug log (never in a person's apps). */
    const val TEST_PACKAGE = "ai.unicorn.glidetest"

    private class Data(val lexicon: GlideLexicon, val nextWords: NextWords?)

    private class Personal(
        val revision: Long,
        val userWords: GlideLexicon?,
        /** Sorted case groups of main-dictionary words the user uses, and their boosts. */
        val groups: IntArray,
        val boosts: FloatArray,
    ) : GlideDecoder.Learned {
        override fun boost(userWords: Boolean, id: Int): Float {
            if (userWords) return USER_WORD_BOOST
            val i = groups.binarySearch(id)
            return if (i >= 0) boosts[i] else 0f
        }
    }

    private val loader = Executors.newSingleThreadExecutor { Thread(it, "unicorn-glide").apply { isDaemon = true } }
    @Volatile private var data: Future<Data?>? = null
    @Volatile private var personal: Personal? = null
    @Volatile private var personalLoading = false
    @Volatile private var personalLoadedAt = 0L
    private val decoder = ThreadLocal<GlideDecoder>()
    private var geometryFor = WeakReference<Keyboard>(null)
    private var geometry: KeyGeometry? = null
    @Volatile private var offsetX = 0f
    @Volatile private var offsetY = 0f
    @JvmStatic @Volatile var packageName: String? = null
    @Volatile private var lastKeyboard = WeakReference<Keyboard>(null)

    // Previous-word predictions from what the user typed before (the engine's history), per gesture
    private val NO_HISTORY = History(IntArray(0), FloatArray(0))
    // Suggestion thread only
    private var lastPreview: SuggestionResults? = null
    private var lastPreviewAt = 0L
    private var learnedContext: String? = null
    private var learnedNext = NO_HISTORY
    private var next: NextWords.Context? = null
    private var nextFor: String? = null
    private var nextForSet = false

    /** Starts loading the dictionary for glide typing; cheap to call again. */
    @JvmStatic
    fun preload(context: Context) {
        if (data != null) return
        val app = context.applicationContext
        synchronized(this) {
            if (data != null) return
            data = loader.submit<Data?> {
                runCatching {
                    val started = SystemClock.uptimeMillis()
                    // Built from the dictionary once per app version, then read back from the cache
                    val cache = File(app.cacheDir, "glide/en-$CACHE_FORMAT-${BuildConfig.VERSION_CODE}.bin")
                    val lexicon = runCatching { GlideLexicon.read(cache) }.getOrNull() ?: run {
                        val bytes = app.resources.openRawResource(R.raw.main_en).use { it.readBytes() }
                        GlideLexicon.fromDictionary(bytes).also { built ->
                            runCatching {
                                cache.parentFile?.listFiles()?.forEach { it.delete() }
                                built.write(cache)
                            }
                        }
                    }
                    val next = runCatching { NextWords(app.assets.open("glide/en_next.bin").use { it.readBytes() }, lexicon) }
                        .onFailure { Log.w(TAG, "No next-word table: ${it.javaClass.simpleName}") }.getOrNull()
                    Log.i(TAG, "Ready: ${lexicon.size} words in ${SystemClock.uptimeMillis() - started} ms")
                    // Keys shown before the words loaded: set up their layout now
                    lastKeyboard.get()?.let { geometryOf(it) }?.let { GlideDecoder.prepare(lexicon, it) }
                    Data(lexicon, next)
                }.onFailure { Log.e(TAG, "Glide typing unavailable: ${it.javaClass.simpleName}") }.getOrNull()
            }
        }
        refreshPersonal(app)
    }

    /** Where the keys' coordinates start in the view the touches come from (the view's padding). */
    @JvmStatic
    fun setTouchOffset(x: Float, y: Float) {
        offsetX = x
        offsetY = y
    }

    /** New keys on screen: works out the layout's word paths in the background, before the first swipe on it. */
    @JvmStatic
    fun onKeyboard(keyboard: Keyboard?) {
        keyboard ?: return
        lastKeyboard = WeakReference(keyboard)
        val loading = data ?: return
        loader.execute {
            runCatching {
                val ready = loading.get() ?: return@execute
                val geometry = geometryOf(keyboard) ?: return@execute
                GlideDecoder.prepare(ready.lexicon, geometry)
            }
        }
    }

    /**
     * Called on the suggestion thread as a swipe starts: looks up what is known about the previous word while the
     * finger is still moving, so the word under it doesn't wait for that.
     */
    @JvmStatic
    fun prefetch(
        ngramContext: NgramContext, keyboard: Keyboard, settings: SettingsValuesForSuggestion, facilitator: DictionaryFacilitator,
    ) {
        try {
            val loading = data ?: return
            if (!loading.isDone) return
            val ready = loading.get() ?: return
            val previous = if (ngramContext.isBeginningOfSentenceContext) null
                else ngramContext.getNthPrevWord(1)?.toString()
            if (ngramContext.isBeginningOfSentenceContext || previous != null) nextAfter(ready, previous)
            learnedNextWords(ready.lexicon, ngramContext, keyboard, settings, facilitator)
        } catch (e: Exception) {
            Log.w(TAG, "Glide prefetch failed: ${e.javaClass.simpleName}")
        }
    }

    /** Called as a swipe starts: picks up words the user learned since the last one. */
    @JvmStatic
    fun onGestureStarted(context: Context) {
        val current = personal
        // Every typed word changes the revision; a minute's staleness costs nothing and saves a rebuild per swipe
        if (current == null || current.revision != PersonalWords.revision.value &&
            SystemClock.uptimeMillis() - personalLoadedAt > PERSONAL_REFRESH_MS) {
            refreshPersonal(context.applicationContext)
        }
    }

    private fun refreshPersonal(app: Context) {
        if (personalLoading) return
        personalLoading = true
        personalLoadedAt = SystemClock.uptimeMillis()
        loader.execute {
            try {
                val revision = PersonalWords.revision.value
                val lexicon = data?.get()?.lexicon ?: return@execute
                val dao = PersonalWords.dao(app) ?: return@execute
                val used = dao.mostUsed(MOST_USED)
                val newWords = dao.suggestionWords().toSet()
                val userEntries = ArrayList<GlideLexicon.Entry>()
                val boosts = HashMap<Int, Float>()
                for (w in used) {
                    val ids = lexicon.find(w.word)
                    if (ids.isEmpty()) continue
                    boosts[lexicon.group(ids[0])] = min(MAX_USAGE_BOOST, USAGE_BOOST * ln(1f + w.count))
                }
                for (word in newWords) {
                    if (lexicon.find(word).isEmpty()) userEntries.add(GlideLexicon.Entry(word, USER_WORD_FREQUENCY))
                }
                val sorted = boosts.keys.sorted().toIntArray()
                personal = Personal(
                    revision,
                    if (userEntries.isEmpty()) null else GlideLexicon.build(userEntries),
                    sorted,
                    FloatArray(sorted.size) { boosts.getValue(sorted[it]) },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Personal words not loaded: ${e.javaClass.simpleName}")
            } finally {
                personalLoading = false
            }
        }
    }

    private fun geometryOf(keyboard: Keyboard): KeyGeometry? {
        synchronized(this) {
            if (geometryFor.get() === keyboard) return geometry
            val cx = FloatArray(GlideLexicon.KEYS) { Float.NaN }
            val cy = FloatArray(GlideLexicon.KEYS) { Float.NaN }
            for (key in keyboard.sortedKeys) {
                val code = key.code
                if (code < 'A'.code || code > 'z'.code) continue
                val k = GlideLexicon.keyOf(code.toChar())
                if (k < 0 || !cx[k].isNaN()) continue
                cx[k] = key.x + key.width / 2f
                cy[k] = key.y + key.height / 2f
            }
            geometry = runCatching { KeyGeometry(cx, cy).takeIf { it.isComplete } }.getOrNull()
            geometryFor = WeakReference(keyboard)
            return geometry
        }
    }

    /**
     * Suggestions for a gesture, best first, in the engine's format. Runs on the engine's suggestion thread. Empty
     * when the keys aren't QWERTY or the dictionary isn't loaded (the gesture then types nothing, as before).
     */
    @JvmStatic
    fun suggest(
        composedData: ComposedData, ngramContext: NgramContext, keyboard: Keyboard,
        settings: SettingsValuesForSuggestion, facilitator: DictionaryFacilitator, inputStyle: Int,
    ): SuggestionResults {
        val results = SuggestionResults(SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext, false)
        val isTail = inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH
        // Live previews at most every PREVIEW_INTERVAL_MS: the word under the finger barely changes faster, and
        // decoding every move would compete with the final word for the CPU
        if (!isTail && SystemClock.uptimeMillis() - lastPreviewAt < PREVIEW_INTERVAL_MS) return lastPreview ?: results
        try {
            val started = SystemClock.uptimeMillis()
            val startedCpu = SystemClock.currentThreadTimeMillis()
            val loading = data ?: return results
            // The dictionary loads in well under a second at startup; a swipe finished before that waits for it
            val ready = if (loading.isDone || isTail) loading.get(if (isTail) 2000 else 0, TimeUnit.MILLISECONDS) else null
            ready ?: return results
            val geometry = geometryOf(keyboard) ?: return results
            if (!facilitator.locale.language.equals(Locale.ENGLISH.language)) return results
            val pointers = composedData.mInputPointers
            val count = pointers.pointerSize
            if (count < 2) return results
            val xs = FloatArray(count)
            val ys = FloatArray(count)
            val px = pointers.xCoordinates
            val py = pointers.yCoordinates
            for (i in 0 until count) {
                xs[i] = px[i] + offsetX
                ys[i] = py[i] + offsetY
            }
            val previous = if (ngramContext.isBeginningOfSentenceContext) null
                else ngramContext.getNthPrevWord(1)?.toString()
            val known = ngramContext.isBeginningOfSentenceContext || previous != null
            val contextStarted = SystemClock.uptimeMillis()
            val next = if (known) nextAfter(ready, previous) else null
            val personalNow = personal
            val history = learnedNextWords(ready.lexicon, ngramContext, keyboard, settings, facilitator)
            val contextMs = SystemClock.uptimeMillis() - contextStarted
            val learned = GlideDecoder.Learned { userWords, id ->
                var boost = personalNow?.boost(userWords, id) ?: 0f
                if (!userWords && history.groups.isNotEmpty()) {
                    val i = history.groups.binarySearch(ready.lexicon.group(id))
                    if (i >= 0) boost += history.boosts[i]
                }
                boost
            }
            val context = GlideDecoder.Context(
                next, settings.mBlockPotentiallyOffensive, personalNow?.userWords, learned,
                learnedMax = MAX_USAGE_BOOST + HISTORY_BOOST,
            )
            val decoder = decoder.get() ?: GlideDecoder(ready.lexicon).also { decoder.set(it) }
            // The live preview only needs the likely word under the finger: a smaller shortlist keeps it quick
            val candidates = decoder.decode(
                xs, ys, count, geometry, context, SuggestedWords.MAX_SUGGESTIONS,
                if (isTail) GlideDecoder.SHORTLIST else PREVIEW_SHORTLIST,
            )
            var score = Int.MAX_VALUE / 2
            for (c in candidates) {
                results.add(SuggestedWordInfo(
                    c.word, "", score, SuggestedWordInfo.KIND_CORRECTION, Dictionary.DICTIONARY_GLIDE,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE,
                ))
                score -= 1000
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                val ms = SystemClock.uptimeMillis() - started
                // This thread's own CPU time: the decode's cost without waiting for a busy (or emulated) CPU
                val cpu = SystemClock.currentThreadTimeMillis() - startedCpu
                val test = packageName == TEST_PACKAGE
                val words = if (test) candidates.take(5).joinToString(",") { it.word } else "-"
                // Where the swipe ran, in key units, only for the test app
                val path = if (test) " from=%.2f,%.2f to=%.2f,%.2f".format(
                    Locale.US, xs[0] / geometry.pitchX, ys[0] / geometry.pitchY,
                    xs[count - 1] / geometry.pitchX, ys[count - 1] / geometry.pitchY,
                ) else ""
                Log.d(TAG, "${if (isTail) "final" else "preview"} ms=$ms cpu=$cpu points=$count context=${contextMs}ms " +
                    "stage1=${decoder.stage1Micros / 1000}ms stage2=${decoder.stage2Micros / 1000}ms " +
                    "scanned=${decoder.scanned} compared=${decoder.compared} words=$words$path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Glide decode failed: ${e.javaClass.simpleName}")
        }
        if (isTail) {
            lastPreview = null
            lastPreviewAt = 0L
        } else {
            lastPreview = results
            lastPreviewAt = SystemClock.uptimeMillis()
        }
        return results
    }

    private fun nextAfter(ready: Data, previous: String?): NextWords.Context? {
        if (nextFor != previous || nextForSet.not()) {
            next = ready.nextWords?.after(previous)
            nextFor = previous
            nextForSet = true
        }
        return next
    }

    private class History(val groups: IntArray, val boosts: FloatArray)

    /** Words the user often typed after the previous one, from the engine's history, as boosts by case group. */
    private fun learnedNextWords(
        lexicon: GlideLexicon, ngramContext: NgramContext, keyboard: Keyboard, settings: SettingsValuesForSuggestion,
        facilitator: DictionaryFacilitator,
    ): History {
        val key = ngramContext.toString()
        if (key == learnedContext) return learnedNext
        learnedContext = key
        learnedNext = runCatching {
            val empty = ComposedData(InputPointers(DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH), false, "")
            val predictions = facilitator.getSuggestionResults(
                empty, ngramContext, keyboard, settings, 0 /* session id: typing */, SuggestedWords.INPUT_STYLE_PREDICTION,
            )
            val boosts = HashMap<Int, Float>()
            for ((rank, info) in predictions.withIndex()) {
                if (info.mSourceDict.mDictType != Dictionary.TYPE_USER_HISTORY) continue
                val ids = lexicon.find(info.mWord)
                if (ids.isNotEmpty()) boosts[lexicon.group(ids[0])] = if (rank < 3) HISTORY_BOOST else HISTORY_BOOST / 2
            }
            val groups = boosts.keys.sorted().toIntArray()
            History(groups, FloatArray(groups.size) { boosts.getValue(groups[it]) })
        }.getOrDefault(NO_HISTORY)
        return learnedNext
    }

    /** For the emulator test: where every key is on screen, once the keys are laid out. Off unless enabled by adb. */
    @JvmStatic
    fun logKeys(view: View, keyboard: Keyboard?) = logKeys(view, keyboard, 0)

    private fun logKeys(view: View, keyboard: Keyboard?, attempt: Int) {
        if (keyboard == null || !Log.isLoggable(TAG, Log.DEBUG)) return
        if (!view.isLaidOut || !view.isShown) {
            // Not on screen yet (the keys are being created, or the phone is turning): log after the next layout
            if (attempt == 0) {
                view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (!view.isLaidOut || !view.isShown) return
                        view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        logKeys(view, keyboard, 1)
                    }
                })
            }
            return
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val keys = StringBuilder()
        for (key in keyboard.sortedKeys) {
            val name = when (key.code) {
                Constants.CODE_SPACE -> "space"
                Constants.CODE_SHIFT -> "shift"
                Constants.CODE_DELETE -> "delete"
                Constants.CODE_ENTER -> "enter"
                Constants.CODE_SWITCH_ALPHA_SYMBOL -> "symbols"
                '.'.code -> "period"
                ','.code -> "comma"
                in 'a'.code..'z'.code, in 'A'.code..'Z'.code -> key.code.toChar().lowercaseChar().toString()
                else -> continue
            }
            val x = location[0] + key.x + key.width / 2 - offsetX.toInt()
            val y = location[1] + key.y + key.height / 2 - offsetY.toInt()
            keys.append(' ').append(name).append('=').append(x).append(',').append(y)
        }
        Log.d(TAG, "keys w=${view.width} h=${view.height} at=${location[0]},${location[1]}$keys")
    }

    /** Bump when the lexicon's packing or word filter changes, so old caches are rebuilt. */
    private const val CACHE_FORMAT = 1
    private const val PREVIEW_INTERVAL_MS = 80L
    private const val PREVIEW_SHORTLIST = 20
    private const val MOST_USED = 3000
    private const val PERSONAL_REFRESH_MS = 60_000L
    private const val USAGE_BOOST = 0.35f
    private const val MAX_USAGE_BOOST = 1.5f
    private const val USER_WORD_FREQUENCY = 140
    private const val USER_WORD_BOOST = 0.5f
    private const val HISTORY_BOOST = 1.5f
}
