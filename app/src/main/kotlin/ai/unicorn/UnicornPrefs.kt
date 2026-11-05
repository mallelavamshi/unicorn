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

package ai.unicorn

import ai.unicorn.memory.CaptureSource
import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unicorn's own settings (separate from the keyboard engine's preferences). Stored in device-protected storage so the
 * keyboard can read them before the first unlock; nothing in here is sensitive.
 */
class UnicornPrefs(context: Context) {
    private val prefs: SharedPreferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("unicorn", Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0L)
    /** Bumped on every change, so UI can observe settings without listeners. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _changes.value++ }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Random id for rate limiting the AI tools. Not linked to any account or device identifier. */
    val installId: String
        get() = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }

    var onboardingDone by bool("onboarding_done", false)
    var displayName by string("display_name", "")
    var persona by string("persona", "")

    /** The user accepted the disclosure for saving prompts sent in AI apps. */
    var captureConsent by bool("capture_consent", false)
    var captureEnabled by bool("capture_enabled", false)
    var notifyOnSave by bool("notify_on_save", true)
    var recallEnabled by bool("recall_enabled", true)

    /** The user opted into sharing anonymous, aggregated signals for "trending near you". */
    var trendsConsent by bool("trends_consent", false)

    var aiToolsEnabled by bool("ai_tools_enabled", true)

    /** The AI tools hide until this time after the free daily AI ran out (no counters or warnings are shown). */
    var aiPausedUntil: Long
        get() = prefs.getLong("ai_paused_until", 0L)
        set(value) = prefs.edit().putLong("ai_paused_until", value).apply()

    val aiAvailable: Boolean get() = aiToolsEnabled && System.currentTimeMillis() >= aiPausedUntil

    /** Learn the user's words (single words only, on the phone) for suggestions and the words page. */
    var learnWords by bool("learn_words", true)

    /** Use the phone's approximate location to make hunches local to the neighborhood, instead of the IP's city. */
    var useNeighbourhood by bool("use_neighbourhood", false)

    /** Earned by sharing anonymous trends, one helping a day; the user's Unicorn grows with it. */
    var stardust by int("stardust", 0)

    /** Ask for the fingerprint, face or the phone's screen lock to open Unicorn. */
    var appLock by bool("app_lock", false)

    /** How long Unicorn may sit in the background before it locks again; 0 locks every time. */
    var lockAfterSeconds by int("lock_after_seconds", 0)

    /** Shown once, right after setup: the first-run reveal. */
    var revealSeen by bool("reveal_seen", false)
    var translateTarget by string("translate_target", "Spanish")

    fun isSourceEnabled(source: CaptureSource): Boolean = prefs.getBoolean("app_" + source.id, source.enabledByDefault)

    fun setSourceEnabled(source: CaptureSource, enabled: Boolean) {
        prefs.edit().putBoolean("app_" + source.id, enabled).apply()
    }

    /** Capture is active only with consent, the master switch on, and the app enabled. */
    fun isCapturing(source: CaptureSource): Boolean = captureConsent && captureEnabled && isSourceEnabled(source)

    private fun bool(key: String, default: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    private fun int(key: String, default: Int) = object : kotlin.properties.ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = prefs.getInt(key, default)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Int) {
            prefs.edit().putInt(key, value).apply()
        }
    }

    private fun string(key: String, default: String) = object : kotlin.properties.ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = prefs.getString(key, default) ?: default
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) {
            prefs.edit().putString(key, value).apply()
        }
    }

    private companion object {
        const val KEY_INSTALL_ID = "install_id"
    }
}
