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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.android.inputmethod.keyboard.KeyboardTheme

/** Colors and view factories shared by the Unicorn views inside the keyboard, matching the key theme. */
class KeyboardStyle(val context: Context) {
    val palette = paletteFor(context)
    val isDark = palette.isDark

    val background = palette.background
    val surface = palette.letter
    val chip = palette.chip
    val text = palette.text
    val secondaryText = palette.secondaryText
    val accent = palette.action
    val accentSoft = ColorUtils.blendARGB(palette.chip, palette.accent, 0.18f)
    val accentText = palette.accent

    fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun rounded(color: Int, radiusDp: Float = 18f, strokeColor: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(dp(1f), strokeColor)
    }

    /** A pill shaped button used for tools, tones and languages. */
    fun pill(label: String, selected: Boolean = false, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (selected) Color.WHITE else this@KeyboardStyle.text)
        background = rounded(if (selected) accent else chip)
        gravity = Gravity.CENTER
        setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
        isClickable = true
        isFocusable = false
        setOnClickListener { onClick() }
    }

    fun label(value: String, sizeSp: Float = 14f, color: Int = this.text, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun spacer(widthDp: Float) = View(context).apply { minimumWidth = dp(widthDp) }

    companion object {
        /** The palette of the keyboard theme in use, which follows light and dark mode unless the user picked one. */
        @JvmStatic
        fun paletteFor(context: Context): KeyboardPalette =
            KeyboardPalettes.byId(KeyboardTheme.getKeyboardTheme(context).mThemeId)
                ?: KeyboardPalettes.byId(KeyboardPalettes.DEFAULT_LIGHT)!!

        /** Dark navigation buttons over light keyboards, light ones over dark keyboards. */
        @JvmStatic
        fun applyNavigationBarAppearance(window: Window?, lightKeyboard: Boolean) {
            if (window == null) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val decor = window.decorView
                @Suppress("DEPRECATION")
                decor.systemUiVisibility = if (lightKeyboard) {
                    decor.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    decor.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
                return
            }
            window.insetsController?.setSystemBarsAppearance(
                if (lightKeyboard) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        }
    }
}
