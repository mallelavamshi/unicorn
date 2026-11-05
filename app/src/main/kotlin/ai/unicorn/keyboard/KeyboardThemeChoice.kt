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
import androidx.core.content.edit

/**
 * The theme the engine draws the keys with. It lives in the engine's own preference, which it reads each time the keys
 * show, so a change applies the next time a text field opens.
 */
object KeyboardThemeChoice {
    // Same as KeyboardTheme.LXX_KEYBOARD_THEME_KEY, which is package-private.
    private const val KEY = "pref_keyboard_theme_20140509"

    // The engine uses the default shared preferences, which are named after the package.
    private fun prefs(context: Context) =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    /** The picked theme id, or null for automatic: Classic, following light and dark mode. */
    fun get(context: Context): Int? = prefs(context).getString(KEY, null)?.toIntOrNull()
        ?.takeIf { KeyboardPalettes.byId(it) != null }

    fun set(context: Context, themeId: Int?) {
        prefs(context).edit { if (themeId == null) remove(KEY) else putString(KEY, themeId.toString()) }
    }
}
