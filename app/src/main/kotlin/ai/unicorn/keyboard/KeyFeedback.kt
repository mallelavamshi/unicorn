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

package ai.unicorn.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.preference.PreferenceManager
import com.android.inputmethod.latin.settings.Settings

/**
 * What a key press feels or sounds like, chosen in You. Stored in LatinIME's own preferences (vibrate_on, sound_on)
 * so the typing engine picks it up, plus a vibration strength. The default is a short, light vibration.
 */
object KeyFeedback {
    private const val PREF_STRENGTH = "unicorn_vibration_strength"

    enum class Mode(val label: String, val vibrate: Boolean, val sound: Boolean) {
        VIBRATE("Vibration", true, false),
        SOUND("Click sound", false, true),
        BOTH("Both", true, true),
        OFF("Off", false, false),
    }

    /** Predefined effects feel crisp on modern haptic motors; older phones get a plain pulse of similar length. */
    enum class Strength(val label: String, val effect: Int, val fallbackMs: Long) {
        SOFT("Soft", VibrationEffect.EFFECT_TICK, 8),
        LIGHT("Light", VibrationEffect.EFFECT_CLICK, 14),
        STRONG("Strong", VibrationEffect.EFFECT_HEAVY_CLICK, 28),
    }

    @Suppress("DEPRECATION")
    private fun prefs(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun mode(context: Context): Mode {
        val res = context.resources
        val p = prefs(context)
        val vibrate = p.getBoolean(Settings.PREF_VIBRATE_ON, res.getBoolean(com.android.inputmethod.latin.R.bool.config_default_vibration_enabled))
        val sound = p.getBoolean(Settings.PREF_SOUND_ON, res.getBoolean(com.android.inputmethod.latin.R.bool.config_default_sound_enabled))
        return Mode.entries.first { it.vibrate == vibrate && it.sound == sound }
    }

    fun setMode(context: Context, mode: Mode) {
        prefs(context).edit().putBoolean(Settings.PREF_VIBRATE_ON, mode.vibrate).putBoolean(Settings.PREF_SOUND_ON, mode.sound).apply()
    }

    @JvmStatic
    fun strength(prefs: SharedPreferences): Strength =
        Strength.entries.getOrElse(prefs.getInt(PREF_STRENGTH, Strength.LIGHT.ordinal)) { Strength.LIGHT }

    fun strength(context: Context): Strength = strength(prefs(context))

    fun setStrength(context: Context, strength: Strength) {
        prefs(context).edit().putInt(PREF_STRENGTH, strength.ordinal).apply()
    }

    @JvmStatic
    fun effect(strength: Strength): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(strength.effect)
        else VibrationEffect.createOneShot(strength.fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)

    /** Plays one key press with these choices, so people feel or hear what they picked. */
    fun preview(context: Context, mode: Mode, strength: Strength) {
        if (mode.vibrate) {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.takeIf { it.hasVibrator() }?.vibrate(effect(strength))
        }
        if (mode.sound) {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
        }
    }
}
