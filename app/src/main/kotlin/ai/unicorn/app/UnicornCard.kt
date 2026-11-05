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

import ai.unicorn.ui.UnicornLogo
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.android.inputmethod.latin.R
import java.io.File
import java.text.DateFormat
import java.util.Date

private val Gold = Color(0xFFF2C14E)
private val GoldDeep = Color(0xFFD99A1E)
private val Aurora = listOf(Color(0xFF7FFFD4), Color(0xFFFF8AD8), Color(0xFFFFE27A), Color(0xFF8AB4FF), Color(0xFF7FFFD4))

/** Sparkle positions around the logo, as (x, y) in units of the logo's radius from its center. */
private val SparkleSpots = listOf(-1.95f to 0.35f, 1.95f to 0.3f, -1.4f to 1.0f, 1.45f to 0.95f, -0.15f to -1.4f, 0.9f to 1.3f)

/** Wings are drawn a little smaller than the logo's circle so their tips stay inside the card. */
private const val WING_SCALE = 0.9f

/** The user's one-of-a-kind Unicorn, grown to its stardust level: sparkles, wings, an aurora halo, a golden frame. */
@Composable
fun UnicornCard(identity: UnicornIdentity, stardust: Int, modifier: Modifier = Modifier) {
    val level = Stardust.level(stardust).number
    val twinkle by rememberInfiniteTransition(label = "card").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle",
    )
    val frame = if (level >= 5) Brush.linearGradient(listOf(Gold, Color.White, GoldDeep)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.35f)))
    Column(
        modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(identity.from, identity.to)))
            .border(if (level >= 5) 4.dp else 1.5.dp, frame, RoundedCornerShape(28.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("UNICORN", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), letterSpacing = 3.sp)
            Text("#${identity.number}", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val r = 60.dp.toPx()
                if (level >= 4) {
                    drawCircle(Brush.sweepGradient(Aurora, center), radius = r + 14.dp.toPx(), style = Stroke(10.dp.toPx()), alpha = 0.55f + 0.3f * twinkle)
                }
                if (level >= 3) {
                    drawWing(-1f, r)
                    drawWing(1f, r)
                }
                if (level >= 2) {
                    SparkleSpots.forEachIndexed { i, (x, y) ->
                        val phase = (twinkle + i * 0.17f) % 1f
                        drawSparkle(Offset(center.x + x * r, center.y + y * r), (5 + 4 * phase).dp.toPx(), if (level >= 5) Gold else Color.White)
                    }
                }
            }
            Box(Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                UnicornLogo(size = 100.dp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(identity.name, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Superpower: ${identity.superpower}", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f))
            Text(
                "Level $level · ${Stardust.level(stardust).name} · hatched ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(identity.born))}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

/** A swept wing: a leading edge up to the tip, then three feathers back to the body. Units are the radius [r]. */
private fun wingPath(side: Float, cx: Float, cy: Float, r: Float) = Path().apply {
    fun x(f: Float) = cx + side * r * f
    fun y(f: Float) = cy + r * f
    moveTo(x(0.7f), y(-0.45f))
    cubicTo(x(0.95f), y(-1.15f), x(1.55f), y(-1.55f), x(2.15f), y(-1.45f))
    cubicTo(x(2.1f), y(-0.95f), x(1.95f), y(-0.5f), x(1.62f), y(-0.42f))
    cubicTo(x(1.62f), y(-0.1f), x(1.42f), y(0.05f), x(1.28f), y(-0.08f))
    cubicTo(x(1.25f), y(0.2f), x(1.05f), y(0.3f), x(0.92f), y(0.12f))
    cubicTo(x(0.85f), y(0.05f), x(0.8f), y(0.0f), x(0.75f), y(-0.05f))
    close()
}

private fun DrawScope.drawWing(side: Float, r: Float) {
    val path = wingPath(side, center.x, center.y, r * WING_SCALE)
    drawPath(path, Color.White.copy(alpha = 0.88f))
    drawPath(path, Color.White, style = Stroke(1.5.dp.toPx()))
}

private fun sparklePath(c: Offset, size: Float) = Path().apply {
    val w = size * 0.28f
    moveTo(c.x, c.y - size)
    quadraticTo(c.x + w * 0.4f, c.y - w * 0.4f, c.x + size, c.y)
    quadraticTo(c.x + w * 0.4f, c.y + w * 0.4f, c.x, c.y + size)
    quadraticTo(c.x - w * 0.4f, c.y + w * 0.4f, c.x - size, c.y)
    quadraticTo(c.x - w * 0.4f, c.y - w * 0.4f, c.x, c.y - size)
    close()
}

private fun DrawScope.drawSparkle(c: Offset, size: Float, color: Color) = drawPath(sparklePath(c, size), color)

/** Draws the card as an image (story-sized), at its current level, and opens the share sheet. */
fun shareUnicornCard(context: Context, identity: UnicornIdentity, stardust: Int) {
    val level = Stardust.level(stardust)
    val width = 1080
    val height = 1350
    val bitmap = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), identity.from.toArgb(), identity.to.toArgb(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    val cx = width / 2f
    val cy = 520f
    val r = 250f
    if (level.number >= 4) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 34f
            alpha = 190
            shader = SweepGradient(cx, cy, Aurora.map { it.toArgb() }.toIntArray(), null)
        }
        canvas.drawCircle(cx, cy, r + 44f, ring)
    }
    if (level.number >= 3) {
        val wing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(225, 255, 255, 255) }
        // Wings are narrower on the image so they stay inside it
        listOf(-1f, 1f).forEach { side -> canvas.drawPath(wingPath(side, cx, cy, r * WING_SCALE).asAndroidPath(), wing) }
    }
    if (level.number >= 2) {
        val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (level.number >= 5) Gold.toArgb() else android.graphics.Color.WHITE }
        SparkleSpots.forEach { (x, y) -> canvas.drawPath(sparklePath(Offset(cx + x * r * 0.9f, cy + y * r * 0.9f), 26f).asAndroidPath(), sparkle) }
    }
    paint.color = android.graphics.Color.argb(235, 255, 255, 255)
    canvas.drawCircle(cx, cy, r, paint)
    ContextCompat.getDrawable(context, R.mipmap.ic_unicorn_foreground)?.let { logo ->
        logo.setBounds(width / 2 - 240, 280, width / 2 + 240, 760)
        logo.draw(canvas)
    }
    if (level.number >= 5) {
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 28f; color = Gold.toArgb() }
        canvas.drawRect(14f, 14f, width - 14f, height - 14f, frame)
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER }
    text.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    text.textSize = 96f
    canvas.drawText(identity.name, width / 2f, 940f, text)
    text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    text.textSize = 46f
    canvas.drawText("Superpower: ${identity.superpower}", width / 2f, 1020f, text)
    text.textSize = 40f
    text.alpha = 210
    canvas.drawText("#${identity.number} · Level ${level.number} ${level.name} · 1 of 1", width / 2f, 1085f, text)
    text.textSize = 38f
    text.alpha = 190
    canvas.drawText("Meet yours with Unicorn ✨", width / 2f, 1250f, text)
    text.textAlign = Paint.Align.LEFT
    text.textSize = 34f
    canvas.drawText("UNICORN", 60f, 90f, text)

    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "my-unicorn.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Meet my Unicorn: ${identity.name} #${identity.number}, level ${level.number} ${level.name} ✨ One of a kind.")
        clipData = ClipData.newRawUri("Unicorn", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your Unicorn").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
