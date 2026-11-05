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

import ai.unicorn.Unicorn
import ai.unicorn.ai.AiClient
import ai.unicorn.ai.AiTask
import ai.unicorn.ai.AiTone
import ai.unicorn.trends.Hunches
import ai.unicorn.ui.UnicornLogo
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import com.android.inputmethod.latin.R
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Every install hatches its own Unicorn: a name, a number and colors from the install id. Nothing else feeds it. */
data class UnicornIdentity(val name: String, val number: Int, val from: Color, val to: Color, val superpower: String, val born: Long) {
    companion object {
        private val Adjectives = listOf(
            "Stardust", "Moonlit", "Velvet", "Cosmic", "Aurora", "Golden", "Misty", "Electric", "Crystal", "Midnight",
            "Rosy", "Silver", "Ember", "Ocean", "Sunny", "Lunar", "Wild", "Lucky", "Neon", "Gentle",
        )
        private val Nouns = listOf(
            "Dreamer", "Spark", "Whisper", "Comet", "Nova", "Bloom", "Echo", "Flare", "Breeze", "Glimmer", "Muse",
            "Halo", "Rider", "Wish", "Storm", "Pixel",
        )
        // Hand-picked so every card looks good; a random hue can land on muddy colors
        private val Gradients = listOf(
            Color(0xFF8E2DE2) to Color(0xFF4A00E0), Color(0xFFFF6A88) to Color(0xFFFF99AC),
            Color(0xFFF7797D) to Color(0xFFC471ED), Color(0xFF12C2E9) to Color(0xFF7F53AC),
            Color(0xFFFC5C7D) to Color(0xFF6A82FB), Color(0xFF11998E) to Color(0xFF38A3D1),
            Color(0xFFF2994A) to Color(0xFFEB5757), Color(0xFF7F00FF) to Color(0xFFE100FF),
            Color(0xFF00B4DB) to Color(0xFF0083B0), Color(0xFFDA4453) to Color(0xFF89216B),
            Color(0xFFF6D365) to Color(0xFFFDA085), Color(0xFF654EA3) to Color(0xFFEAAFC8),
        )
        private val Superpowers = mapOf(
            "Work" to "Gets things done", "Studies" to "Endless curiosity", "Business" to "Big moves",
            "Creating content" to "Main character energy", "Shopping research" to "Always finds the best deal",
            "Travel" to "Wanderlust", "Everyday questions" to "Knows a bit of everything",
        )

        fun of(context: Context): UnicornIdentity {
            val core = Unicorn.get(context)
            val hash = MessageDigest.getInstance("SHA-256").digest(core.prefs.installId.toByteArray())
            fun byte(i: Int) = hash[i].toInt() and 0xFF
            val (from, to) = Gradients[byte(0) % Gradients.size]
            val born = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            }.getOrDefault(System.currentTimeMillis())
            return UnicornIdentity(
                name = "${Adjectives[byte(2) % Adjectives.size]} ${Nouns[byte(3) % Nouns.size]}",
                number = 1000 + ((byte(4) shl 8) or byte(5)) % 9000,
                from = from,
                to = to,
                superpower = Superpowers[core.prefs.persona] ?: "Pure magic",
                born = born,
            )
        }
    }
}

private enum class Scene { HELLO, CARD, MAGIC, NEARBY }

private val Night = Color(0xFF140A24)
private val NightEnd = Color(0xFF2A1250)

/**
 * The first-run reveal: the Unicorn appears under a starry sky, the user meets their own one-of-a-kind Unicorn (and
 * can share it), watches it fix a messy message live, and sees what's happening around them right now.
 */
@Composable
fun Reveal(onDone: () -> Unit) {
    val context = LocalContext.current
    val core = remember { Unicorn.get(context) }
    val identity = remember { UnicornIdentity.of(context) }
    var scene by rememberSaveable { mutableIntStateOf(0) }
    val finish = {
        core.prefs.revealSeen = true
        onDone()
    }
    // Loaded early so the last scene is ready when the user gets there
    val nearby by produceState(core.hunches.cached()) {
        value = runCatching { core.hunches.fetch() }.getOrNull()
    }

    NightSystemBars()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Night, NightEnd)))) {
        Stars()
        AnimatedContent(
            targetState = Scene.entries[scene],
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
            label = "scene",
        ) { current ->
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (current) {
                    Scene.HELLO -> Hello(core.prefs.displayName.ifBlank { null }) { scene = 1 }
                    Scene.CARD -> CardScene(identity, core.prefs.stardust) { scene = 2 }
                    Scene.MAGIC -> Magic {
                        if (nearby?.hunches.isNullOrEmpty()) finish() else scene = 3
                    }
                    Scene.NEARBY -> Nearby(nearby, finish)
                }
            }
        }
        if (Scene.entries[scene] != Scene.NEARBY) {
            TextButton(onClick = finish, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Text("Skip", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

/** Light status and navigation icons over the night sky, back to the app's own when the reveal ends. */
@Composable
private fun NightSystemBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        val contrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced else false
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = contrast
        }
    }
}

@Composable
private fun Stars() {
    val stars = remember { List(70) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle",
    )
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (x, y, phase) ->
            val a = 0.25f + 0.75f * ((twinkle + phase) % 1f)
            drawCircle(Color.White.copy(alpha = a * 0.8f), radius = 1.2.dp.toPx() + phase * 1.4.dp.toPx(), center = Offset(x * size.width, y * size.height))
        }
    }
}

@Composable
private fun Hello(name: String?, next: () -> Unit) {
    val grow = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(300)
        launch { grow.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessVeryLow)) }
        delay(350)
        launch { burst.animateTo(1f, tween(1400)) }
        delay(700)
        words.animateTo(1f, tween(900))
    }
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        0.85f, 1.15f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "halo",
    )
    Spacer(Modifier.height(90.dp))
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        val sparkles = remember { List(18) { i -> Triple(i * 20f + Random.nextFloat() * 12f, 0.6f + Random.nextFloat() * 0.5f, Random.nextFloat()) } }
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFB98AE8).copy(alpha = 0.55f * grow.value), Color.Transparent), center, size.minDimension / 2 * glow),
                radius = size.minDimension / 2 * glow, center = center,
            )
            val t = burst.value
            if (t in 0.01f..0.99f) {
                sparkles.forEach { (angle, reach, tint) ->
                    val r = size.minDimension / 2 * reach * t
                    val rad = Math.toRadians(angle.toDouble())
                    val color = if (tint > 0.5f) Color(0xFFF2C14E) else Color.White
                    drawCircle(color.copy(alpha = 1f - t), radius = 3.dp.toPx() * (1f - t / 2), center = Offset(center.x + (r * cos(rad)).toFloat(), center.y + (r * sin(rad)).toFloat()))
                }
            }
        }
        Box(Modifier.size(150.dp).scale(grow.value).clip(RoundedCornerShape(75.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            UnicornLogo(size = 130.dp)
        }
    }
    Spacer(Modifier.height(28.dp))
    Column(Modifier.alpha(words.value), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (name != null) "Hi $name." else "Hi there.",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Text("Your Unicorn has hatched.", style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(48.dp))
        RevealButton("Meet it", next)
    }
}

@Composable
private fun CardScene(identity: UnicornIdentity, stardust: Int, next: () -> Unit) {
    val context = LocalContext.current
    val flip = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { flip.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    Spacer(Modifier.height(40.dp))
    Text("One of a kind", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF2C14E))
    Spacer(Modifier.height(6.dp))
    Text(
        "No one else has this Unicorn",
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    UnicornCard(identity, stardust, Modifier.scale(flip.value))
    Spacer(Modifier.height(16.dp))
    Text(
        "It grows sparkles, wings and more while you share anonymous trends.",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    RevealButton("Share my Unicorn") { shareUnicornCard(context, identity, stardust) }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = next) { Text("Next", color = Color.White, style = MaterialTheme.typography.titleMedium) }
}

private const val MESSY = "hey sry cant make it tmrw nite, smthing came up at wrk. can we do sat instead??"

@Composable
private fun Magic(next: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(answer) {
        val full = answer ?: return@LaunchedEffect
        shown = ""
        for (i in 1..full.length) {
            shown = full.take(i)
            delay(16)
        }
    }
    fun run(tone: AiTone) {
        busy = true
        failed = null
        scope.launch {
            runCatching { Unicorn.get(context).ai.run(AiTask.TONE, MESSY, tone).first() }
                .onSuccess { answer = it }
                .onFailure {
                    failed = (it as? AiClient.AiException)?.message ?: "Unicorn AI needs a connection. Try it later in any app."
                }
            busy = false
        }
    }
    Spacer(Modifier.height(40.dp))
    Text("Watch this", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF2C14E))
    Spacer(Modifier.height(6.dp))
    Text(
        "A messy text, fixed in a tap",
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Bubble(MESSY, Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.8f))
    Spacer(Modifier.height(16.dp))
    if (answer != null) {
        Bubble(shown, Color.White, Color(0xFF241A33))
        Spacer(Modifier.height(16.dp))
    }
    failed?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
    }
    if (busy) {
        Text("✨ Unicorn is writing…", style = MaterialTheme.typography.titleMedium, color = Color.White)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RevealChip(if (answer == null) "✨ Make it polite" else "🙏 Polite") { run(AiTone.POLITE) }
            RevealChip("😂 Funny") { run(AiTone.FUNNY) }
            RevealChip("💼 Pro") { run(AiTone.PROFESSIONAL) }
        }
    }
    if (answer != null) {
        Spacer(Modifier.height(24.dp))
        Text(
            "This works in every app. Tap ✨ Ask Unicorn above your keys, or copy a message and tap Reply to copied.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(32.dp))
    RevealButton(if (answer == null) "Skip the demo" else "Next", next)
}

@Composable
private fun Nearby(hunches: Hunches?, done: () -> Unit) {
    val town = hunches?.town
    Spacer(Modifier.height(40.dp))
    Text("Right now", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF2C14E))
    Spacer(Modifier.height(6.dp))
    Text(
        if (town != null) "Probably on $town's mind" else "Probably on people's minds",
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Unicorn's playful guesses from today's weather, the calendar and your town",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    hunches?.hunches?.take(3)?.forEach { hunch ->
        Bubble("${hunch.emoji}  ${hunch.text}", Color.White.copy(alpha = 0.12f), Color.White)
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "Near you has new ones every day. Share what you ask AI, anonymously, and real trends from people around you take their place.",
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    RevealButton("Let's go", done)
}

@Composable
private fun Bubble(text: String, background: Color, color: Color) {
    Text(
        text,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(background).padding(16.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = color,
    )
}

@Composable
private fun RevealButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF5B21B6)),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun RevealChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
    )
}
