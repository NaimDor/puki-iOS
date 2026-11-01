package io.github.tomsadeh.puki.ui.myday.garage

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.DragDropState
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.LocalDragDropState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.ToolButton
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberStepGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private enum class WashStep { SCRUB, RINSE, DRY }

/** The mud (and later the drops) on the car: where (fractions of the car's box) and how big. */
private val spots = listOf(
    Offset(0.2f, 0.55f), Offset(0.36f, 0.38f), Offset(0.52f, 0.6f),
    Offset(0.66f, 0.42f), Offset(0.82f, 0.58f), Offset(0.42f, 0.7f)
)
private const val SPOT_R = 0.11f  // as a fraction of the car's width
private val carColors = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF8E24AA))

class CarWashViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("garage", (application as PukiApplication).progressRepository, viewModelScope)
}

/** Washing a muddy car: scrub with the sponge, rinse with the hose, dry with the towel. */
@Composable
fun CarWashScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,
    viewModel: CarWashViewModel = viewModel()
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()

    val game = rememberStepGame(WashStep.SCRUB, WashStep.entries.size, viewModel.session)
    val step = game.step
    val celebrate = game.celebrate
    val carColor = remember { carColors.random() }
    val mud = remember { mutableStateListOf(*Array(spots.size) { 1f }) }    // 1: muddy, 0: clean
    val drops = remember { mutableStateListOf(*Array(spots.size) { 1f }) }  // water left after the rinse
    val bubbles = remember { mutableStateListOf<Offset>() }
    var bubblesLeft by remember { mutableStateOf(1f) }
    var tool by remember { mutableStateOf<Offset?>(null) }  // the sponge or towel, under the finger
    var spraying by remember { mutableStateOf(false) }
    val spray = remember { Animatable(0f) }
    val driveOff = remember { Animatable(0f) }

    // Rubbing at [p] (fractions of the car): wipes the mud or the drops near it by [amount]
    fun rub(p: Offset, amount: Float) {
        val list = if (step == WashStep.SCRUB) mud else drops
        if (list.all { it <= 0f }) return
        var touched = false
        spots.forEachIndexed { i, s ->
            val dx = (p.x - s.x)
            val dy = (p.y - s.y) * 0.5f  // the box is twice as wide as tall
            if (list[i] > 0f && dx * dx + dy * dy < SPOT_R * SPOT_R * 2.2f) {
                list[i] = (list[i] - amount).coerceAtLeast(0f)
                touched = true
                if (list[i] == 0f) sfxPlayer.play(Sfx.POP, 0.7f)
            }
        }
        if (touched && step == WashStep.SCRUB && bubbles.size < 60 && Random.nextFloat() < 0.5f) {
            bubbles.add(p + Offset(Random.nextFloat() * 0.06f - 0.03f, Random.nextFloat() * 0.1f - 0.05f))
        }
        if (list.all { it <= 0f }) {
            if (step == WashStep.SCRUB) game.done(WashStep.RINSE)
            else {
                // All dry: it shines, beeps and drives off
                voicePlayer.play("voice_car_clean", language) {
                    companion?.react("בשטיפת המכונית: סיבנו, שטפו וניגבו, והמכונית נקייה!", "At the car wash the child scrubbed, rinsed and dried the car, and it's clean!") { }
                }
                game.done(null)
                scope.launch {
                    delay(1200)
                    driveOff.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
                    game.finish()
                }
            }
        }
    }

    LaunchedEffect(step) { tool = null }  // (a new step cancels the drag, so its end never comes)

    val help = rememberHelp(step, active = !celebrate && !spraying && !game.sessionComplete) {
        voicePlayer.play(
            when (step) {
                WashStep.SCRUB -> "voice_car_scrub"
                WashStep.RINSE -> "voice_car_rinse"
                WashStep.DRY -> "voice_car_dry"
            },
            language
        )
    }
    when (step) {
        WashStep.SCRUB -> RoundPrompt(0, "בשטיפת המכונית: המכונית מלאה בוץ, משפשפים עם ספוג.", "At the car wash: the car is all muddy, scrubbing it with a sponge.", "voice_car_intro")
        WashStep.RINSE -> RoundPrompt(1, "בשטיפת המכונית: שוטפים את הקצף עם הצינור.", "At the car wash: rinsing the bubbles off with the hose.", "voice_car_rinse")
        WashStep.DRY -> RoundPrompt(2, "בשטיפת המכונית: מנגבים את המכונית עם מגבת.", "At the car wash: drying the car with a towel.", "voice_car_dry")
    }

    CompositionLocalProvider(LocalDragDropState provides remember { DragDropState() }) {
        GameBackground {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawGarage() }
            Column(
                modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(onBack = onBack, roundIndex = step.ordinal, totalRounds = WashStep.entries.size, starsEarned = game.stars)
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val carW = minOf(maxWidth * 0.72f, maxHeight * 1.5f)
                    val shimmer by rememberInfiniteTransition(label = "shine").animateFloat(
                        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "shine_phase"
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                        Canvas(
                            modifier = Modifier
                                .size(carW, carW * 0.5f)
                                .graphicsLayer { translationX = -driveOff.value * size.width * 1.6f }  // forward, the way its face looks
                                .pointerInput(step) {
                                    detectTapGestures { o -> rub(Offset(o.x / size.width, o.y / size.height), 0.34f) }
                                }
                                .pointerInput(step) {
                                    if (step == WashStep.RINSE) return@pointerInput
                                    detectDragGestures(
                                        onDragEnd = { tool = null },
                                        onDragCancel = { tool = null },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            val p = Offset(change.position.x / size.width, change.position.y / size.height)
                                            tool = p
                                            rub(p, delta.getDistance() / size.width * 2.2f)
                                        }
                                    )
                                }
                        ) {
                            drawCar(carColor)
                            spots.forEachIndexed { i, s ->
                                if (mud[i] > 0f) drawMud(s, mud[i], i)
                                if (step == WashStep.DRY && drops[i] > 0f) drawDrop(s, drops[i])
                            }
                            bubbles.forEachIndexed { i, b ->
                                drawCircle(Color.White.copy(alpha = 0.85f * bubblesLeft), size.width * (0.018f + (i % 3) * 0.008f), Offset(b.x * size.width, b.y * size.height))
                            }
                            if (spraying || spray.value in 0.01f..0.99f) drawSpray(spray.value)
                            if (game.sessionComplete || driveOff.value > 0f || (step == WashStep.DRY && drops.all { it <= 0f })) drawShine(shimmer)
                            tool?.let { drawTool(it, sponge = step == WashStep.SCRUB) }
                        }
                        // The hose, for the rinse
                        Box(modifier = Modifier.size(carW * 0.2f), contentAlignment = Alignment.Center) {
                            if (step == WashStep.RINSE && !spraying) ToolButton(
                                size = carW * 0.18f,
                                hint = help.showHint,
                                color = Color(0xFF29B6F6),
                                onClick = {
                                        spraying = true
                                        sfxPlayer.play(Sfx.WHOOSH)
                                        scope.launch {
                                            launch { spray.animateTo(1f, tween(2200, easing = LinearEasing)) }
                                            Animatable(1f).animateTo(0f, tween(2200)) { bubblesLeft = value }
                                            bubbles.clear()
                                            spraying = false
                                            game.done(WashStep.DRY)
                                        }
                                }
                            ) { Canvas(modifier = Modifier.size(carW * 0.12f)) { drawHose() } }
                        }
                    }
                }
            }

            ConfettiOverlay(show = celebrate)
            if (game.sessionComplete) RoundCompleteSheet(starsEarned = game.stars, onContinue = onPlayAgain, onHome = onBack)
        }
    }
}

// ---------- Drawing ----------

/** A garage wall and a wet, tiled floor. */
private fun DrawScope.drawGarage() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFCFD8DC), Color(0xFFECEFF1))))
    val floorTop = size.height * 0.74f
    drawRect(Color(0xFF90A4AE), Offset(0f, floorTop), Size(size.width, size.height - floorTop))
    var x = 0f
    while (x < size.width) {
        drawLine(Color(0x22FFFFFF), Offset(x, floorTop), Offset(x - size.width * 0.1f, size.height), 3f * density)
        x += 90f * density
    }
    // Puddles
    drawOval(Color(0x5581D4FA), Offset(size.width * 0.15f, size.height * 0.84f), Size(size.width * 0.22f, size.height * 0.05f))
    drawOval(Color(0x5581D4FA), Offset(size.width * 0.62f, size.height * 0.88f), Size(size.width * 0.18f, size.height * 0.04f))
}

/** A friendly little car, side on, filling its box (twice as wide as tall). */
private fun DrawScope.drawCar(color: Color) {
    val w = size.width
    val h = size.height
    val dark = lerp(color, Color.Black, 0.25f)
    // Body and cabin
    val body = Path().apply {
        moveTo(w * 0.06f, h * 0.78f)
        lineTo(w * 0.06f, h * 0.55f)
        quadraticBezierTo(w * 0.07f, h * 0.44f, w * 0.18f, h * 0.42f)
        lineTo(w * 0.3f, h * 0.4f)
        lineTo(w * 0.4f, h * 0.18f)
        quadraticBezierTo(w * 0.43f, h * 0.12f, w * 0.5f, h * 0.12f)
        lineTo(w * 0.68f, h * 0.12f)
        quadraticBezierTo(w * 0.74f, h * 0.12f, w * 0.77f, h * 0.2f)
        lineTo(w * 0.84f, h * 0.4f)
        quadraticBezierTo(w * 0.95f, h * 0.43f, w * 0.95f, h * 0.56f)
        lineTo(w * 0.95f, h * 0.78f)
        close()
    }
    drawPath(body, color)
    drawRoundRect(dark, Offset(w * 0.06f, h * 0.7f), Size(w * 0.89f, h * 0.08f), CornerRadius(h * 0.04f))
    // Windows
    val front = Path().apply { moveTo(w * 0.37f, h * 0.4f); lineTo(w * 0.44f, h * 0.2f); lineTo(w * 0.57f, h * 0.2f); lineTo(w * 0.57f, h * 0.4f); close() }
    val back = Path().apply { moveTo(w * 0.61f, h * 0.2f); lineTo(w * 0.72f, h * 0.2f); lineTo(w * 0.78f, h * 0.4f); lineTo(w * 0.61f, h * 0.4f); close() }
    drawPath(front, Color(0xFFB3E5FC))
    drawPath(back, Color(0xFFB3E5FC))
    drawLine(Color.White.copy(alpha = 0.6f), Offset(w * 0.46f, h * 0.24f), Offset(w * 0.42f, h * 0.34f), w * 0.01f, StrokeCap.Round)
    // A smiling face at the front: headlight eye and a grille smile
    drawCircle(Color(0xFFFFF59D), h * 0.06f, Offset(w * 0.12f, h * 0.52f))
    drawCircle(Color(0xFF3E2723), h * 0.025f, Offset(w * 0.115f, h * 0.52f))
    drawArc(Color(0xFF3E2723), 20f, 140f, false, Offset(w * 0.06f, h * 0.55f), Size(w * 0.1f, h * 0.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(h * 0.02f, cap = StrokeCap.Round))
    drawCircle(Color(0xFFFF8A65), h * 0.03f, Offset(w * 0.9f, h * 0.52f))  // tail light
    drawLine(dark, Offset(w * 0.6f, h * 0.42f), Offset(w * 0.6f, h * 0.68f), w * 0.006f)  // door
    drawLine(dark, Offset(w * 0.63f, h * 0.48f), Offset(w * 0.67f, h * 0.48f), w * 0.01f, StrokeCap.Round)  // handle
    // Wheels
    for (cx in listOf(0.26f, 0.76f)) {
        drawCircle(Color(0xFF37474F), h * 0.16f, Offset(w * cx, h * 0.8f))
        drawCircle(Color(0xFFB0BEC5), h * 0.07f, Offset(w * cx, h * 0.8f))
    }
}

/** A splotch of mud, [amount] (0..1) of it left. */
private fun DrawScope.drawMud(at: Offset, amount: Float, seed: Int) {
    val c = Offset(at.x * size.width, at.y * size.height)
    val r = size.width * SPOT_R * 0.48f
    val mudColor = Color(0xFF6D4C41).copy(alpha = 0.95f * amount)
    drawCircle(mudColor, r, c)
    val rnd = Random(seed)
    for (k in 0 until 5) {
        val o = Offset((rnd.nextFloat() - 0.5f) * r * 1.6f, (rnd.nextFloat() - 0.5f) * r * 1.1f)
        drawCircle(mudColor, r * (0.3f + rnd.nextFloat() * 0.25f), c + o)
    }
    drawCircle(Color(0xFF8D6E63).copy(alpha = 0.7f * amount), r * 0.3f, c + Offset(-r * 0.3f, -r * 0.25f))
}

/** A drop of water left after the rinse. */
private fun DrawScope.drawDrop(at: Offset, amount: Float) {
    val c = Offset(at.x * size.width, at.y * size.height)
    val r = size.width * 0.03f
    val drop = Path().apply {
        moveTo(c.x, c.y - r * 1.8f)
        cubicTo(c.x + r * 1.2f, c.y - r * 0.2f, c.x + r, c.y + r, c.x, c.y + r)
        cubicTo(c.x - r, c.y + r, c.x - r * 1.2f, c.y - r * 0.2f, c.x, c.y - r * 1.8f)
        close()
    }
    drawPath(drop, Color(0xFF4FC3F7).copy(alpha = amount))
    drawCircle(Color.White.copy(alpha = 0.7f * amount), r * 0.3f, c + Offset(-r * 0.3f, -r * 0.1f))
}

/** Water from the hose sweeping across the car, [p] (0..1) of the way. */
private fun DrawScope.drawSpray(p: Float) {
    val x = size.width * (0.05f + p * 0.9f)
    for (k in 0 until 14) {
        val y = size.height * (0.05f + k * 0.065f)
        val wob = sin((p * 30f + k).toDouble()).toFloat() * size.width * 0.01f
        drawLine(Color(0xAA4FC3F7), Offset(x - size.width * 0.06f + wob, y), Offset(x + wob, y + size.height * 0.04f), size.width * 0.012f, StrokeCap.Round)
    }
}

/** Sparkles all over a clean car. */
private fun DrawScope.drawShine(phase: Float) {
    listOf(0.3f to 0.3f, 0.55f to 0.15f, 0.75f to 0.35f, 0.2f to 0.6f, 0.85f to 0.62f).forEachIndexed { i, (fx, fy) ->
        val t = ((phase + i * 0.2f) % 1f)
        val s = size.width * 0.03f * sin(t * Math.PI).toFloat()
        val c = Offset(size.width * fx, size.height * fy)
        drawLine(Color.White, c - Offset(s, 0f), c + Offset(s, 0f), size.width * 0.008f, StrokeCap.Round)
        drawLine(Color.White, c - Offset(0f, s), c + Offset(0f, s), size.width * 0.008f, StrokeCap.Round)
    }
}

/** The sponge (scrubbing) or towel (drying) under the finger. */
private fun DrawScope.drawTool(at: Offset, sponge: Boolean) {
    val c = Offset(at.x * size.width, at.y * size.height)
    val w = size.width * 0.12f
    val h = size.width * 0.08f
    if (sponge) {
        drawRoundRect(Color(0xFFFFEE58), c - Offset(w / 2, h / 2), Size(w, h), CornerRadius(h * 0.3f))
        drawRoundRect(Color(0xFF66BB6A), c - Offset(w / 2, h / 2), Size(w, h * 0.3f), CornerRadius(h * 0.2f))
        for (k in 0 until 4) drawCircle(Color(0xFFFBC02D), h * 0.08f, c + Offset(w * (-0.3f + k * 0.2f), h * 0.15f))
    } else {
        drawRoundRect(Color(0xFFF48FB1), c - Offset(w / 2, h / 2), Size(w, h * 1.2f), CornerRadius(h * 0.2f))
        for (k in 0 until 3) drawLine(Color.White.copy(alpha = 0.7f), c + Offset(-w / 2, -h * 0.2f + k * h * 0.3f), c + Offset(w / 2, -h * 0.2f + k * h * 0.3f), h * 0.08f)
    }
}

/** A hose nozzle with a spurt of water, on the rinse button. */
private fun DrawScope.drawHose() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF43A047), Offset(w * 0.1f, h * 0.45f), Size(w * 0.45f, h * 0.22f), CornerRadius(h * 0.1f))
    drawRoundRect(Color(0xFFFFCA28), Offset(w * 0.5f, h * 0.4f), Size(w * 0.18f, h * 0.32f), CornerRadius(h * 0.06f))
    for (k in 0..2) drawLine(Color.White, Offset(w * 0.72f, h * 0.56f), Offset(w * 0.95f, h * (0.35f + k * 0.21f)), w * 0.05f, StrokeCap.Round)
}
