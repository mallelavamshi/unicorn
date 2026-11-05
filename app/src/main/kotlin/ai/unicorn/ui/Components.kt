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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.inputmethod.latin.R

@Composable
fun UnicornLogo(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.mipmap.ic_unicorn_foreground),
        contentDescription = "Unicorn",
        modifier = modifier.size(size),
    )
}

data class Ring(val progress: Float, val color: Color)

/**
 * Three concentric activity rings, Unicorn's signature element. Rings fill once when first shown, which is the only
 * motion on the screen.
 */
@Composable
fun CuriosityRings(rings: List<Ring>, modifier: Modifier = Modifier, stroke: Dp = 16.dp, gap: Dp = 6.dp) {
    val track = LocalUnicorn.current.track
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val fill by animateFloatAsState(if (started) 1f else 0f, tween(900), label = "rings")
    Canvas(modifier) {
        val strokePx = stroke.toPx()
        val gapPx = gap.toPx()
        rings.forEachIndexed { i, ring ->
            val inset = strokePx / 2 + i * (strokePx + gapPx)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            val sweep = 360f * ring.progress.coerceIn(0f, 1f) * fill
            if (sweep > 0.5f) {
                drawArc(ring.color, -90f, sweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            }
        }
    }
}

/** A labelled value next to a colored ring key. */
@Composable
fun RingLegend(color: Color, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalUnicorn.current.muted)
        }
    }
}

/** Vertical bars, e.g. prompts per day. The last bar is today and is drawn in full color. */
@Composable
fun Bars(values: List<Int>, labels: List<String>, color: Color, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val muted = LocalUnicorn.current.muted
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                val isLast = i == values.lastIndex
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight((v.toFloat() / max).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (isLast) color else color.copy(alpha = 0.35f)),
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
                }
            }
        }
    }
}

/** A horizontal share bar row: label, bar, count. */
@Composable
fun ShareRow(label: String, value: Int, max: Int, color: Color, onClick: (() -> Unit)? = null) {
    val track = LocalUnicorn.current.track
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value.toString(), style = MaterialTheme.typography.labelLarge, color = LocalUnicorn.current.muted)
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(track)) {
            Box(
                Modifier.fillMaxWidth((value.toFloat() / max.coerceAtLeast(1)).coerceIn(0.02f, 1f))
                    .fillMaxHeight().clip(CircleShape).background(color),
            )
        }
    }
}

/** 24 cells, darker where more prompts were sent at that hour. */
@Composable
fun HourStrip(byHour: List<Int>, color: Color) {
    val max = (byHour.maxOrNull() ?: 0).coerceAtLeast(1)
    val track = LocalUnicorn.current.track
    Column {
        Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            byHour.forEach { v ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                        .background(if (v == 0) track else color.copy(alpha = 0.25f + 0.75f * v / max)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("Midnight", "6am", "Noon", "6pm").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = LocalUnicorn.current.muted)
            }
        }
    }
}

/** A screen section: title, optional one-line explanation, content. */
@Composable
fun Section(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LocalUnicorn.current.muted)
                }
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** An invitation shown where there is no data yet: what to do to fill it. */
@Composable
fun EmptyHint(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = LocalUnicorn.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).padding(18.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.muted)
        if (action != null) {
            Text(
                action,
                Modifier.padding(top = 10.dp).clickable(onClick = onAction),
                style = MaterialTheme.typography.labelLarge,
                color = colors.ink,
            )
        }
    }
}

/** A rounded tag, used for topics and filters. */
@Composable
fun Tag(text: String, color: Color, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.13f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else color, maxLines = 1)
    }
}

/** A switch that reads clearly when off too: the default one is almost invisible on Unicorn's white cards. */
@Composable
fun UnicornSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val colors = LocalUnicorn.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.ink,
            checkedBorderColor = colors.ink,
            uncheckedThumbColor = colors.muted,
            uncheckedTrackColor = colors.track,
            uncheckedBorderColor = colors.muted,
            disabledCheckedThumbColor = Color.White,
            disabledCheckedTrackColor = colors.ink.copy(alpha = 0.38f),
            disabledUncheckedThumbColor = colors.muted.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = colors.track.copy(alpha = 0.38f),
            disabledUncheckedBorderColor = colors.muted.copy(alpha = 0.38f),
        ),
    )
}
