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

package ai.unicorn.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Unicorn's palette. Each ring color means one thing everywhere in the app: ink = prompts, gold = streak days,
 * glacier = topics.
 */
@Immutable
data class UnicornColors(
    val ink: Color,
    val gold: Color,
    val glacier: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val track: Color,
)

val LightUnicorn = UnicornColors(
    ink = Color(0xFF7430A4),
    gold = Color(0xFFE8A800),
    glacier = Color(0xFF1C9C96),
    background = Color(0xFFF7F3FC),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF241A33),
    muted = Color(0xFF6E6680),
    track = Color(0xFFE9E1F3),
)

val DarkUnicorn = UnicornColors(
    ink = Color(0xFFB98AE8),
    gold = Color(0xFFF2C14E),
    glacier = Color(0xFF4FC7C0),
    background = Color(0xFF120C1A),
    surface = Color(0xFF1E1628),
    text = Color(0xFFF1ECF7),
    muted = Color(0xFFA79DB5),
    track = Color(0xFF2E2440),
)

val LocalUnicorn = staticCompositionLocalOf { LightUnicorn }

/** Big personal numbers and screen titles are set in serif; everything else in the system sans. */
private fun typography(text: Color) = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp, color = text),
    displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, color = text),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, color = text),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, color = text),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = text),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, color = text),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = text),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = text),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)

@Composable
fun UnicornTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkUnicorn else LightUnicorn
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.ink, onPrimary = Color(0xFF1B0E2A), background = colors.background,
            surface = colors.surface, onSurface = colors.text, onBackground = colors.text,
            secondary = colors.glacier, tertiary = colors.gold, surfaceVariant = colors.track,
            onSurfaceVariant = colors.muted, outline = colors.track,
        )
    } else {
        lightColorScheme(
            primary = colors.ink, onPrimary = Color.White, background = colors.background,
            surface = colors.surface, onSurface = colors.text, onBackground = colors.text,
            secondary = colors.glacier, tertiary = colors.gold, surfaceVariant = colors.track,
            onSurfaceVariant = colors.muted, outline = colors.track,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalUnicorn provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography(colors.text), content = content)
    }
}
