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

import ai.unicorn.keyboard.KeyFeedback
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Tag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** "When you tap a key": vibration, a click sound, both or nothing, and how strong the vibration is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyFeedbackPicker() {
    val context = LocalContext.current
    val colors = LocalUnicorn.current
    var mode by remember { mutableStateOf(KeyFeedback.mode(context)) }
    var strength by remember { mutableStateOf(KeyFeedback.strength(context)) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("When you tap a key", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyFeedback.Mode.entries.forEach { option ->
                Tag(option.label, colors.ink, selected = mode == option) {
                    mode = option
                    KeyFeedback.setMode(context, option)
                    KeyFeedback.preview(context, option, strength)
                }
            }
        }
        if (mode.vibrate) {
            Text("Vibration strength", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = colors.muted)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyFeedback.Strength.entries.forEach { option ->
                    Tag(option.label, colors.ink, selected = strength == option) {
                        strength = option
                        KeyFeedback.setStrength(context, option)
                        KeyFeedback.preview(context, KeyFeedback.Mode.VIBRATE, option)
                    }
                }
            }
        }
        if (mode.sound) {
            Text(
                "Clicks follow your phone's volume and stay quiet when it's on silent or vibrate.",
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
    }
}
