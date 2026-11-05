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

import ai.unicorn.keyboard.KeyboardPalette
import ai.unicorn.keyboard.KeyboardPalettes
import ai.unicorn.keyboard.KeyboardThemeChoice
import ai.unicorn.ui.LocalUnicorn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Picks the look of the keys: Automatic (Classic, light or dark with the phone) or one of the themes. */
@Composable
fun KeysThemePicker() {
    val context = LocalContext.current
    val colors = LocalUnicorn.current
    var selected by remember { mutableStateOf(KeyboardThemeChoice.get(context)) }
    val light = KeyboardPalettes.byId(KeyboardPalettes.DEFAULT_LIGHT)!!
    val dark = KeyboardPalettes.byId(KeyboardPalettes.DEFAULT_DARK)!!
    val options: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to "Automatic") +
        KeyboardPalettes.all.map { it.id to it.name }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(options, key = { it.first ?: -1 }) { (id, name) ->
            val isSelected = selected == id
            Column(
                Modifier.width(112.dp).clickable {
                    selected = id
                    KeyboardThemeChoice.set(context, id)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(
                    Modifier.width(112.dp).height(78.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) colors.ink else colors.track, RoundedCornerShape(14.dp)),
                ) {
                    if (id == null) {
                        // Half light, half dark: it follows the phone
                        clipRect(right = size.width / 2) { drawKeys(light) }
                        clipRect(left = size.width / 2) { drawKeys(dark) }
                    } else {
                        drawKeys(KeyboardPalettes.byId(id)!!)
                    }
                }
                Text(
                    name,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.ink else colors.text,
                )
            }
        }
    }
    Text(
        "Shows the next time you start typing.",
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.muted,
    )
}

/** A tiny keyboard: three rows of letters and a bottom row with symbols, space and the action key. */
private fun DrawScope.drawKeys(p: KeyboardPalette) {
    drawRect(Brush.verticalGradient(listOf(Color(p.background), Color(p.backgroundEnd))))
    val pad = size.width * 0.04f
    val gap = size.width * 0.018f
    val rows = 4
    val keyHeight = (size.height - pad * 2 - gap * (rows - 1)) / rows
    val radius = CornerRadius(keyHeight * 0.18f)
    fun key(x: Float, y: Float, w: Float, color: Int) =
        drawRoundRect(Color(color), Offset(x, y), Size(w, keyHeight), radius)
    val inner = size.width - pad * 2
    val letter = (inner - gap * 9) / 10
    for (row in 0 until 3) {
        val count = if (row == 0) 10 else if (row == 1) 9 else 7
        val y = pad + row * (keyHeight + gap)
        var x = pad + (inner - (count * letter + (count - 1) * gap)) / 2
        repeat(count) {
            key(x, y, letter, p.letter)
            x += letter + gap
        }
    }
    val y = pad + 3 * (keyHeight + gap)
    val side = inner * 0.22f
    key(pad, y, side, p.functional)
    key(pad + side + gap, y, inner - side * 2 - gap * 2, p.letter)
    key(pad + inner - side, y, side, p.action)
}
