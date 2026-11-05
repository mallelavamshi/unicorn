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

import ai.unicorn.keyboard.KeyArea
import ai.unicorn.keyboard.KeyAreaSize
import ai.unicorn.keyboard.KeyScreen
import ai.unicorn.keyboard.TapAim
import ai.unicorn.ui.LocalUnicorn
import ai.unicorn.ui.Tag
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.android.inputmethod.latin.utils.ResourceUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The phone's screen in one orientation, as the keys see it. */
private fun keyScreen(context: android.content.Context, landscape: Boolean): KeyScreen {
    val res = context.resources
    val metrics = res.displayMetrics
    val short = min(metrics.widthPixels, metrics.heightPixels)
    val long = max(metrics.widthPixels, metrics.heightPixels)
    val nowLandscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val defaultHeight = if (landscape == nowLandscape) {
        ResourceUtils.getDefaultKeyboardHeight(res)
    } else {
        // The engine's rule for the other orientation: at least 68% of the width in portrait, 45% of the height in landscape
        val shortDp = short / metrics.density
        val dp = if (landscape) max(LANDSCAPE_DEFAULT_DP, shortDp * 0.45f) else max(PORTRAIT_DEFAULT_DP, shortDp * 0.68f)
        (dp * metrics.density).roundToInt()
    }
    // Toolbar, suggestions and the navigation bar, as the keys reserve them
    val reserved = ((44f + (if (landscape) 36f else 40f) + KeyAreaSize.NAV_BAR_RESERVE_DP) * metrics.density).roundToInt()
    return if (landscape) {
        KeyScreen(long, short, metrics.density, defaultHeight, reserved)
    } else {
        KeyScreen(short, long, metrics.density, defaultHeight, reserved)
    }
}

private const val PORTRAIT_DEFAULT_DP = 205.6f
private const val LANDSCAPE_DEFAULT_DP = 176f

@Composable
fun SizeScreen(nav: NavHostController) {
    val context = LocalContext.current
    val colors = LocalUnicorn.current
    var landscape by remember { mutableStateOf(false) }
    val screen = remember(landscape) { keyScreen(context, landscape) }
    val limits = remember(screen) { KeyAreaSize.Limits.of(screen) }
    var size by remember(landscape) { mutableStateOf(KeyArea.sizeFor(context, landscape).clampedTo(screen)) }
    var edgeTaps by remember { mutableStateOf(KeyArea.edgeTaps(context)) }
    var learnAim by remember { mutableStateOf(KeyArea.learnAim(context)) }
    var learned by remember { mutableStateOf(TapAim.learnedLetters(context, false)) }
    var tryText by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keys = LocalSoftwareKeyboardController.current

    fun apply(value: KeyAreaSize) {
        size = value.clampedTo(screen)
        KeyArea.save(context, landscape, size)
    }

    SubScreen(nav, "Size and position") {
        Text(
            "Make the keys fit your hand. The letters stay in the same places; only the size and position change. " +
                "Portrait and landscape each remember their own.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("Portrait", colors.ink, selected = !landscape) { landscape = false }
            Tag("Landscape", colors.ink, selected = landscape) { landscape = true }
        }
        Spacer(Modifier.height(14.dp))
        KeyAreaPreview(size, screen)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = tryText,
            onValueChange = { tryText = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Try your keys here") },
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                KeyArea.requestEditor(context)
                focus.requestFocus()
                keys?.show()
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = Color.White),
        ) { Text("Adjust on the keys", color = Color.White) }
        Text(
            "Drag the edges of the keys to resize them, or the middle to move them. Also in the ⋯ menu above the keys.",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )

        Spacer(Modifier.height(18.dp))
        Text("Quick sizes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("Standard", colors.ink, selected = size.isDefault) { apply(KeyAreaSize.DEFAULT) }
            Tag("One-handed left", colors.ink, selected = size.isOneHanded && size.position < 0.5f) {
                apply(KeyAreaSize.oneHanded(onRight = false, current = size))
            }
        }
        Spacer(Modifier.height(8.dp))
        Tag("One-handed right", colors.ink, selected = size.isOneHanded && size.position > 0.5f) {
            apply(KeyAreaSize.oneHanded(onRight = true, current = size))
        }

        Spacer(Modifier.height(18.dp))
        SizeSlider("Height", "${(size.heightScale * 100).roundToInt()}%", size.heightScale, limits.minHeightScale..limits.maxHeightScale,
            onChange = { size = size.copy(heightScale = it) }, onDone = { apply(size) })
        SizeSlider("Width", "${(size.widthFraction * 100).roundToInt()}%", size.widthFraction, limits.minWidthFraction..1f,
            onChange = { size = size.copy(widthFraction = it) }, onDone = { apply(size) })
        SizeSlider("Position", when {
            size.isFullWidth -> "Full width"
            size.position < 0.34f -> "Left"
            size.position > 0.66f -> "Right"
            else -> "Middle"
        }, size.position, 0f..1f, enabled = !size.isFullWidth, onChange = { size = size.copy(position = it) }, onDone = { apply(size) })
        // Taller keys leave less room to lift them
        val liftMax = limits.maxLiftDpWith(screen, size.heightScale)
        SizeSlider("Space under the keys", "${size.liftDp.roundToInt()} dp", size.liftDp, 0f..max(1f, liftMax), enabled = liftMax >= 1f,
            onChange = { size = size.copy(liftDp = it.coerceAtMost(liftMax)) }, onDone = { apply(size) })
        val keyWidthDp = size.widthFraction * screen.widthDp / 10
        Text(
            "Letter keys are about ${keyWidthDp.roundToInt()} dp wide (${"%.1f".format(keyWidthDp * MM_PER_DP)} mm).",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )

        Spacer(Modifier.height(22.dp))
        ThumbCheck(screen) { apply(it) }

        Spacer(Modifier.height(22.dp))
        SwitchRow("Edge taps", "When the keys are narrower than the screen, a tap just beside a row goes to its outer key", edgeTaps) {
            edgeTaps = it
            KeyArea.setEdgeTaps(context, it)
        }
        SwitchRow(
            "Learn my aim",
            "Learns where you actually tap each letter, on this phone only, and moves its touch area a little to match. " +
                "Glide typing isn't affected.",
            learnAim,
        ) {
            learnAim = it
            KeyArea.setLearnAim(context, it)
        }
        if (learnAim) {
            Text(
                if (learned == 0) "Still learning. It starts after about 40 taps on a letter."
                else "Learned $learned of 26 letters so far (portrait).",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
        }
        LinkRow("Forget how I tap") {
            TapAim.forget(context)
            learned = 0
        }
        LinkRow("Reset both orientations", danger = true) {
            KeyArea.resetAll(context)
            size = KeyAreaSize.DEFAULT
        }
        Spacer(Modifier.height(24.dp))
    }
}

private const val MM_PER_DP = 0.15875f

@Composable
private fun SizeSlider(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = LocalUnicorn.current.muted)
    }
    Slider(
        value = current.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        onValueChangeFinished = onDone,
        valueRange = range,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = "$title, $value" },
    )
}

/** A small drawing of the bottom of the phone with the key area where it will be. */
@Composable
private fun KeyAreaPreview(size: KeyAreaSize, screen: KeyScreen) {
    val colors = LocalUnicorn.current
    val keyColor = colors.surface
    val board = colors.track
    val outline = colors.ink
    Canvas(
        Modifier.fillMaxWidth().height(if (screen.widthPx > screen.heightPx) 130.dp else 190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface.copy(alpha = 0.5f))
            .semantics { contentDescription = "Preview of the keys at this size and position" },
    ) {
        // Fit the bottom of the phone (toolbar, suggestions, keys and lift) into the box, centred
        val chromePx = CHROME_DP * screen.density
        val marginPx = 10f * screen.density
        val keysPx = size.keysHeightPx(screen).toFloat()
        val liftPx = size.liftPx(screen).toFloat()
        val scale = min(this.size.width / screen.widthPx, this.size.height / (chromePx + keysPx + liftPx + marginPx * 2))
        val ox = (this.size.width - screen.widthPx * scale) / 2
        val bottom = this.size.height - marginPx * scale
        val keysH = keysPx * scale
        val lift = liftPx * scale
        val left = ox + size.leftPx(screen) * scale
        val width = size.widthPx(screen) * scale
        val top = bottom - lift - keysH - chromePx * scale
        drawRect(board, Offset(ox, top), Size(screen.widthPx * scale, this.size.height - top))
        drawRoundRect(outline, Offset(left, top), Size(width, bottom - lift - top), CornerRadius(8f), style = Stroke(2f * screen.density))
        val rowH = keysH / PREVIEW_ROWS.size
        val pitch = width / 10
        val gap = pitch * 0.1f
        for ((i, row) in PREVIEW_ROWS.withIndex()) {
            val y = bottom - lift - keysH + i * rowH
            var x = left + row.first * pitch
            for (keyWidth in row.second) {
                drawRoundRect(keyColor, Offset(x + gap / 2, y + gap / 2), Size(keyWidth * pitch - gap, rowH - gap), CornerRadius(4f))
                x += keyWidth * pitch
            }
        }
    }
}

/** The rows as drawn in the preview: indent, then each key's width, in letter keys. */
private val PREVIEW_ROWS = listOf(
    0f to List(10) { 1f },
    0f to List(10) { 1f },
    0.5f to List(9) { 1f },
    0f to listOf(1.5f) + List(7) { 1f } + listOf(1.5f),
    0f to listOf(1.5f, 1f, 5f, 1f, 1.5f),
)

/** Toolbar and suggestions above the keys, in dp. */
private const val CHROME_DP = 84f

/** Sweep a thumb across the area; suggests full width, or one-handed on that hand's side and how wide. */
@Composable
private fun ThumbCheck(screen: KeyScreen, onUse: (KeyAreaSize) -> Unit) {
    val colors = LocalUnicorn.current
    val points = remember { mutableStateListOf<Offset>() }
    var boxLeft by remember { mutableStateOf(0f) }
    var suggestion by remember { mutableStateOf<Pair<String, KeyAreaSize>?>(null) }
    Text("Thumb check", style = MaterialTheme.typography.titleMedium)
    Text(
        "Hold the phone in the hand you type with. Sweep your thumb across the area below as far as feels easy, then let go.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.muted,
    )
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier.fillMaxWidth().height(200.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.ink.copy(alpha = 0.07f))
            .onGloballyPositioned { boxLeft = it.positionInWindow().x }
            .semantics { contentDescription = "Thumb check area" }
            .pointerInput(screen) {
                detectDragGestures(
                    onDragStart = {
                        points.clear()
                        points.add(it)
                        suggestion = null
                    },
                    onDrag = { change, _ -> points.add(change.position) },
                    onDragEnd = {
                        if (points.size < 4) return@detectDragGestures
                        val xs = points.map { boxLeft + it.x }
                        val right = xs.first() > screen.widthPx / 2f
                        val reach = if (right) screen.widthPx - xs.min() else xs.max()
                        val share = (reach / screen.widthPx).coerceIn(0f, 1f)
                        val limits = KeyAreaSize.Limits.of(screen)
                        suggestion = if (share >= FULL_REACH) {
                            "Your thumb covers the whole width. Full width suits you." to KeyAreaSize.DEFAULT
                        } else {
                            val width = (ceil((share + 0.04f) * 20f) / 20f).coerceIn(limits.minWidthFraction, 1f)
                            "Your thumb comfortably covers ${(share * 100).roundToInt()}% of the width. Try one-handed on the " +
                                "${if (right) "right" else "left"}, ${(width * 100).roundToInt()}% wide." to
                                KeyAreaSize(widthFraction = width, position = if (right) 1f else 0f)
                        }
                    },
                )
            },
    ) {
        val ink = colors.ink
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            for (i in 1 until points.size) {
                drawLine(ink.copy(alpha = 0.55f), points[i - 1], points[i], strokeWidth = 14f, cap = StrokeCap.Round)
            }
        }
    }
    suggestion?.let { (text, value) ->
        Text(text, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge)
        Row {
            TextButton(onClick = {
                onUse(value)
                suggestion = null
                points.clear()
            }) { Text("Use this") }
            TextButton(onClick = {
                suggestion = null
                points.clear()
            }) { Text("Try again") }
        }
    }
}

private const val FULL_REACH = 0.92f
