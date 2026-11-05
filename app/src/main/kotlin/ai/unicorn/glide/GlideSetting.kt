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

import android.content.Context
import androidx.core.content.edit
import com.android.inputmethod.latin.R
import com.android.inputmethod.latin.settings.Settings

/** The Glide typing switch in You. It is the engine's own preference, which it reads each time the keys show. */
object GlideSetting {
    // The engine uses the default shared preferences, which are named after the package.
    private fun prefs(context: Context) =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    /** Whether this build offers glide typing at all. */
    fun isAvailable(context: Context): Boolean = Settings.readFromBuildConfigIfGestureInputEnabled(context.resources)

    fun isOn(context: Context): Boolean = isAvailable(context) && prefs(context).getBoolean(
        Settings.PREF_GESTURE_INPUT, context.resources.getBoolean(R.bool.config_glide_typing_default_on),
    )

    fun set(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(Settings.PREF_GESTURE_INPUT, on) }
        if (on) Glide.preload(context)
    }
}
