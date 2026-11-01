package io.github.tomsadeh.puki.ui.myday.brush

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
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

private enum class BrushStep { PASTE, SCRUB, RINSE }

/** Teddy's teeth, as fractions of his face's (square) box: five on top, five below. */
private val teeth = List(10) { i ->
    Offset(0.304f + (i % 5) * 0.098f, if (i < 5) 0.595f else 0.805f)
}
private const val TOOTH_W = 0.086f
private const val TOOTH_H = 0.085f

class BrushyBrushyViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("brush", (application as PukiApplication).progressRepository, viewModelScope)
}

/** Brushing Teddy's teeth: toothpaste on the brush, scrub every tooth, a sip of water, a big smile. */
@Composable
fun BrushyBrushyScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: BrushyBrushyViewModel = viewModel()
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()

    val game = rememberStepGame(BrushStep.PASTE, BrushStep.entries.size, viewModel.session)
    val step = game.step
    val celebrate = game.celebrate
    val dirt = remember { mutableStateListOf(*Array(teeth.size) { 1f }) }  // 1: dirty, 0: clean
    val foam = remember { mutableStateListOf<Offset>() }
    var foamLeft by remember { mutableStateOf(1f) }
    var brush by remember { mutableStateOf<Offset?>(null) }  // the brush, under the finger
    var lastShake by remember { mutableStateOf(0L) }
    val paste = remember { Animatable(0f) }  // the toothpaste coming out onto the brush
    val swish = remember { Animatable(0f) }  // the rinse: cheeks full of water, swishing
    val smile = remember { Animatable(0f) }  // the open mouth closing into a big grin

    // Brushing at [p] (fractions of the face): scrubs the teeth near it by [amount]
    fun scrub(p: Offset, amount: Float) {
        if (step != BrushStep.SCRUB || dirt.all { it <= 0f }) return
        var touched = false
        teeth.forEachIndexed { i, t ->
            val dx = (p.x - t.x) / (TOOTH_W * 1.3f)
            val dy = (p.y - t.y) / (TOOTH_H * 1.4f)
            if (dirt[i] > 0f && dx * dx + dy * dy < 1f) {
                dirt[i] = (dirt[i] - amount).coerceAtLeast(0f)
                touched = true
                if (dirt[i] == 0f) sfxPlayer.play(Sfx.POP, 0.7f)
            }
        }
        if (!touched) return
        val now = System.currentTimeMillis()
        if (now - lastShake > 280) {  // a soft shh-shh while brushing
            sfxPlayer.play(Sfx.MARACAS, 0.35f)
            lastShake = now
        }
        if (foam.size < 70 && Random.nextFloat() < 0.6f) {
            foam.add(p + Offset(Random.nextFloat() * 0.05f - 0.025f, Random.nextFloat() * 0.04f - 0.02f))
        }
        if (dirt.all { it <= 0f }) game.done(BrushStep.RINSE)
    }

    LaunchedEffect(step) { brush = null }  // (a new step cancels the drag, so its end never comes)

    val help = rememberHelp(step, active = !celebrate && !game.sessionComplete && !paste.isRunning && !swish.isRunning) {
        voicePlayer.play(
            when (step) {
                BrushStep.PASTE -> "voice_teeth_paste"
                BrushStep.SCRUB -> "voice_teeth_scrub"
                BrushStep.RINSE -> "voice_teeth_rinse"
            },
            language
        )
    }
    when (step) {
        BrushStep.PASTE -> RoundPrompt(0, "בצחצוח שיניים: מצחצחים לדובי את השיניים. קודם שמים משחה על המברשת.", "Brushing teeth: brushing Teddy's teeth. First, toothpaste on the brush.", "voice_teeth_intro")
        BrushStep.SCRUB -> RoundPrompt(1, "בצחצוח שיניים: מצחצחים כל שן של דובי עד שהיא מבריקה.", "Brushing teeth: brushing each of Teddy's teeth until it sparkles.", "voice_teeth_scrub")
        BrushStep.RINSE -> RoundPrompt(2, "בצחצוח שיניים: נותנים לדובי מים לשטוף את הפה.", "Brushing teeth: giving Teddy water to rinse his mouth.", "voice_teeth_rinse")
    }

    GameBackground {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawBathroom() }
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(onBack = onBack, roundIndex = step.ordinal, totalRounds = BrushStep.entries.size, starsEarned = game.stars)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Teddy as big as the screen allows (the teeth are the targets), the tool beside him
                val faceW = minOf(maxHeight * 0.95f, maxWidth * 0.62f)
                val idle = rememberInfiniteTransition(label = "teddy")
                val blink by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "blink")
                val eyesShut by remember { derivedStateOf { blink > 0.96f } }
                val shimmer by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "shine")
                val giggle by idle.animateFloat(-1f, 1f, infiniteRepeatable(tween(160), RepeatMode.Reverse), label = "giggle")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(faceW * 0.08f, Alignment.CenterHorizontally), modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .size(faceW)
                            .graphicsLayer {
                                // Giggles while brushed; sways while swishing the water around
                                rotationZ = (if (brush != null) giggle * 2.5f else 0f) +
                                    sin(swish.value * Math.PI * 6).toFloat() * 6f * (1f - smile.value)
                            }
                            .pointerInput(step) {
                                detectTapGestures { o -> scrub(Offset(o.x / size.width, o.y / size.height), 0.34f) }
                            }
                            .pointerInput(step) {
                                if (step != BrushStep.SCRUB) return@pointerInput
                                detectDragGestures(
                                    onDragEnd = { brush = null },
                                    onDragCancel = { brush = null },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val p = Offset(change.position.x / size.width, change.position.y / size.height)
                                        brush = p
                                        scrub(p, delta.getDistance() / size.width * 2.6f)
                                    }
                                )
                            }
                    ) {
                        val swishing = swish.value > 0f && swish.value < 1f
                        drawTeddy(
                            happy = brush != null || smile.value > 0f,
                            eyesShut = eyesShut,
                            puff = if (swishing) sin(swish.value * Math.PI).toFloat() else 0f,
                            smile = smile.value
                        )
                        if (smile.value < 0.5f && !swishing) {
                            teeth.forEachIndexed { i, t -> drawTooth(t, dirt[i], i) }
                            foam.forEachIndexed { i, f -> drawFoam(f, i, foamLeft) }
                        }
                        if (smile.value > 0.5f) drawSparkles(shimmer)
                        brush?.let { drawBrush(it, pasteOn = 1f, scale = 0.7f) }
                    }
                    // The tools: the toothpaste tube, then the cup of water
                    Box(modifier = Modifier.size(faceW * 0.36f), contentAlignment = Alignment.Center) {
                        if (step == BrushStep.PASTE) ToolButton(
                            size = faceW * 0.34f,
                            hint = help.showHint && !paste.isRunning,
                            enabled = !paste.isRunning && paste.value < 1f,
                            onClick = {
                                sfxPlayer.play(Sfx.WHOOSH, 0.6f)
                                scope.launch {
                                    paste.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
                                    game.done(BrushStep.SCRUB)
                                }
                            }
                        ) {
                            Canvas(modifier = Modifier.size(faceW * 0.3f)) {
                                drawBrush(Offset(0.55f, 0.4f), pasteOn = paste.value, scale = 1.25f)
                                drawTube(squeeze = paste.value)
                            }
                        }
                        if (step == BrushStep.RINSE && swish.value == 0f && !celebrate) ToolButton(
                            size = faceW * 0.34f,
                            hint = help.showHint,
                            onClick = {
                                    sfxPlayer.play(Sfx.DROP)
                                    scope.launch {
                                        // Swish, swish... and the foam is gone
                                        launch { Animatable(1f).animateTo(0f, tween(1600)) { foamLeft = value } }
                                        swish.animateTo(1f, tween(2200, easing = LinearEasing))
                                        sfxPlayer.play(Sfx.WHOOSH)
                                        foam.clear()
                                        smile.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                                        voicePlayer.play("voice_teeth_clean", language) {
                                            companion?.react("בצחצוח שיניים: צחצחו לדובי את כל השיניים, הוא שטף את הפה ועכשיו הוא מחייך חיוך מבריק!", "Brushing teeth: the child brushed all of Teddy's teeth, he rinsed, and now he has a sparkly smile!") { }
                                        }
                                        game.done(null)
                                        delay(2200)
                                        game.finish()
                                    }
                            }
                        ) { Canvas(modifier = Modifier.size(faceW * 0.2f)) { drawCup() } }
                    }
                }
            }
        }

        ConfettiOverlay(show = celebrate)
        if (game.sessionComplete) RoundCompleteSheet(starsEarned = game.stars, onContinue = onPlayAgain, onHome = onBack)
    }
}

// ---------- Drawing ----------

/** A tiled bathroom wall, and the counter with the sink. */
private fun DrawScope.drawBathroom() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2))))
    val tile = 70f * density
    val counter = size.height * 0.8f
    var x = 0f
    while (x < size.width) {
        drawLine(Color(0x55FFFFFF), Offset(x, 0f), Offset(x, counter), 3f * density)
        x += tile
    }
    var y = 0f
    while (y < counter) {
        drawLine(Color(0x55FFFFFF), Offset(0f, y), Offset(size.width, y), 3f * density)
        y += tile
    }
    drawRect(Color.White, Offset(0f, counter), Size(size.width, size.height - counter))
    drawRect(Color(0xFF80DEEA), Offset(0f, counter), Size(size.width, 6f * density))
    drawOval(Color(0xFFE0F2F1), Offset(size.width * 0.3f, counter + size.height * 0.04f), Size(size.width * 0.4f, size.height * 0.08f))
    drawOval(Color(0xFFB2DFDB), Offset(size.width * 0.47f, counter + size.height * 0.07f), Size(size.width * 0.06f, size.height * 0.02f))
}

/** Teddy's face, filling a square box: mouth wide open, shut while [puff]ing water, or grinning as [smile] goes to 1. */
private fun DrawScope.drawTeddy(happy: Boolean, eyesShut: Boolean, puff: Float, smile: Float) {
    val w = size.width
    val fur = Color(0xFFA1887F)
    // Ears
    for (ex in listOf(0.2f, 0.8f)) {
        drawCircle(fur, w * 0.13f, Offset(w * ex, w * 0.2f))
        drawCircle(Color(0xFFF8BBD0), w * 0.075f, Offset(w * ex, w * 0.2f))
    }
    // Head, with cheeks that puff out with water
    drawCircle(fur, w * 0.4f, Offset(w * 0.5f, w * 0.52f))
    if (puff > 0f) for (cx in listOf(0.2f, 0.8f)) drawCircle(fur, w * (0.1f + 0.07f * puff), Offset(w * cx, w * 0.66f))
    drawOval(Color(0xFFD7CCC8), Offset(w * 0.17f, w * 0.42f), Size(w * 0.66f, w * 0.5f))
    for (cx in listOf(0.24f, 0.76f)) drawCircle(Color(0x55F48FB1), w * (0.04f + 0.02f * puff), Offset(w * cx, w * 0.48f))
    // Eyes: round, shut for a blink, or happy arcs
    for (ex in listOf(0.37f, 0.63f)) {
        val c = Offset(w * ex, w * 0.36f)
        if (happy || eyesShut) {
            drawArc(Color(0xFF3E2723), if (happy) 200f else 20f, 140f, false, c - Offset(w * 0.04f, w * 0.03f), Size(w * 0.08f, w * 0.06f),
                style = Stroke(w * 0.014f, cap = StrokeCap.Round))
        } else {
            drawCircle(Color(0xFF3E2723), w * 0.04f, c)
            drawCircle(Color.White, w * 0.013f, c + Offset(-w * 0.012f, -w * 0.014f))
        }
    }
    // Nose
    drawOval(Color(0xFF4E342E), Offset(w * 0.445f, w * 0.43f), Size(w * 0.11f, w * 0.075f))
    drawCircle(Color.White.copy(alpha = 0.5f), w * 0.012f, Offset(w * 0.475f, w * 0.45f))
    // Mouth
    when {
        puff > 0f -> drawLine(Color(0xFF6D4C41), Offset(w * 0.42f, w * 0.68f), Offset(w * 0.58f, w * 0.68f), w * 0.018f, StrokeCap.Round)
        smile >= 0.5f -> {
            val grin = Path().apply {
                moveTo(w * 0.28f, w * 0.6f)
                quadraticBezierTo(w * 0.5f, w * 0.86f, w * 0.72f, w * 0.6f)
                quadraticBezierTo(w * 0.5f, w * 0.68f, w * 0.28f, w * 0.6f)
                close()
            }
            drawPath(grin, Color(0xFF6D2B2B))
            drawRoundRect(Color.White, Offset(w * 0.34f, w * 0.635f), Size(w * 0.32f, w * 0.045f), CornerRadius(w * 0.022f))
        }
        else -> {
            val h = w * 0.34f * (1f - smile * 2f)
            drawRoundRect(Color(0xFF6D2B2B), Offset(w * 0.24f, w * 0.7f - h / 2), Size(w * 0.52f, h), CornerRadius(w * 0.14f))
            drawOval(Color(0xFFF06292), Offset(w * 0.36f, w * 0.7f + h * 0.02f), Size(w * 0.28f, h * 0.3f))
        }
    }
}

/** One of Teddy's teeth, with [dirt] (0..1) of fuzz and a crumb on it; a glint once clean. */
private fun DrawScope.drawTooth(at: Offset, dirt: Float, seed: Int) {
    val w = size.width
    val c = Offset(at.x * w, at.y * w)
    val tw = w * TOOTH_W
    val th = w * TOOTH_H
    val top = seed < 5
    drawRoundRect(Color.White, c - Offset(tw / 2, th / 2), Size(tw, th), CornerRadius(tw * (if (top) 0.25f else 0.35f)))
    if (dirt <= 0f) {
        drawLine(Color(0xFFB3E5FC), c + Offset(-tw * 0.2f, -th * 0.25f), c + Offset(-tw * 0.2f, th * 0.1f), tw * 0.12f, StrokeCap.Round)
        return
    }
    val rnd = Random(seed)
    val plaque = Color(0xFFFFD54F).copy(alpha = 0.9f * dirt)
    drawCircle(plaque, tw * 0.32f, c + Offset((rnd.nextFloat() - 0.5f) * tw * 0.3f, (rnd.nextFloat() - 0.5f) * th * 0.3f))
    drawCircle(plaque, tw * 0.2f, c + Offset((rnd.nextFloat() - 0.5f) * tw * 0.5f, (rnd.nextFloat() - 0.5f) * th * 0.5f))
    val crumb = listOf(Color(0xFF8D6E63), Color(0xFF66BB6A), Color(0xFFEF5350))[seed % 3]
    drawCircle(crumb.copy(alpha = dirt), tw * 0.12f, c + Offset(tw * 0.18f, th * (if (top) 0.22f else -0.22f)))
}

/** A toothbrush with its head at [at] (fractions of the box's width), [pasteOn] (0..1) of toothpaste on it. */
private fun DrawScope.drawBrush(at: Offset, pasteOn: Float, scale: Float) {
    val w = size.width
    val c = Offset(at.x * w, at.y * w)
    val s = w * 0.12f * scale
    rotate(-25f, c) {
        drawRoundRect(Color(0xFF42A5F5), c + Offset(-s * 0.4f, s * 0.15f), Size(s * 3.2f, s * 0.35f), CornerRadius(s * 0.18f))
        drawRoundRect(Color(0xFF1E88E5), c + Offset(-s * 0.5f, -s * 0.05f), Size(s * 1.1f, s * 0.3f), CornerRadius(s * 0.1f))
        for (k in 0 until 5) drawLine(Color.White, c + Offset(-s * 0.4f + k * s * 0.22f, -s * 0.05f), c + Offset(-s * 0.4f + k * s * 0.22f, -s * 0.4f), s * 0.12f, StrokeCap.Round)
        if (pasteOn > 0f) {
            // A minty stripe along the bristles
            val len = s * pasteOn
            drawLine(Color.White, c + Offset(-s * 0.45f, -s * 0.5f), c + Offset(-s * 0.45f + len, -s * 0.5f), s * 0.28f, StrokeCap.Round)
            drawLine(Color(0xFF80CBC4), c + Offset(-s * 0.45f, -s * 0.5f), c + Offset(-s * 0.45f + len * 0.9f, -s * 0.5f), s * 0.1f, StrokeCap.Round)
        }
    }
}

/** A bubble of toothpaste foam; a pale rim so it shows on the white teeth. */
private fun DrawScope.drawFoam(at: Offset, i: Int, left: Float) {
    val r = size.width * (0.016f + (i % 3) * 0.007f)
    val c = Offset(at.x * size.width, at.y * size.width)
    drawCircle(Color.White.copy(alpha = 0.95f * left), r, c)
    drawCircle(Color(0xFF80DEEA).copy(alpha = 0.9f * left), r, c, style = Stroke(r * 0.22f))
    drawCircle(Color(0xFFB2EBF2).copy(alpha = left), r * 0.28f, c + Offset(-r * 0.35f, -r * 0.35f))
}

/** The toothpaste tube, flattening as it's squeezed, its nozzle towards the brush. */
private fun DrawScope.drawTube(squeeze: Float) {
    val w = size.width
    val h = size.height
    val fat = h * 0.11f * (1f - 0.35f * squeeze)  // half its thickness
    val y = h * 0.72f
    val tube = Path().apply {
        moveTo(w * 0.06f, y - h * 0.1f)            // the crimped end
        lineTo(w * 0.14f, y - h * 0.1f)
        lineTo(w * 0.56f, y - fat)
        quadraticBezierTo(w * 0.64f, y, w * 0.56f, y + fat)
        lineTo(w * 0.14f, y + h * 0.1f)
        lineTo(w * 0.06f, y + h * 0.1f)
        close()
    }
    drawPath(tube, Color.White)
    drawPath(tube, Color(0xFF90A4AE), style = Stroke(w * 0.012f))
    for (k in 0..2) drawLine(Color(0xFFB0BEC5), Offset(w * (0.075f + k * 0.025f), y - h * 0.09f), Offset(w * (0.075f + k * 0.025f), y + h * 0.09f), w * 0.008f)
    drawRoundRect(Color(0xFFEF5350), Offset(w * 0.24f, y - fat * 0.55f), Size(w * 0.22f, fat * 1.1f), CornerRadius(fat * 0.3f))
    drawRoundRect(Color(0xFF26A69A), Offset(w * 0.6f, y - h * 0.045f), Size(w * 0.09f, h * 0.09f), CornerRadius(h * 0.02f))
}

/** A cup of water, for the rinse. */
private fun DrawScope.drawCup() {
    val w = size.width
    val h = size.height
    val cup = Path().apply { moveTo(w * 0.15f, h * 0.15f); lineTo(w * 0.85f, h * 0.15f); lineTo(w * 0.72f, h * 0.92f); lineTo(w * 0.28f, h * 0.92f); close() }
    drawPath(cup, Color(0xFFFFB74D))
    val water = Path().apply { moveTo(w * 0.2f, h * 0.3f); lineTo(w * 0.8f, h * 0.3f); lineTo(w * 0.7f, h * 0.85f); lineTo(w * 0.3f, h * 0.85f); close() }
    drawPath(water, Color(0xFF4FC3F7))
    drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.3f, h * 0.4f), Offset(w * 0.36f, h * 0.75f), w * 0.06f, StrokeCap.Round)
}

/** Sparkles around Teddy's clean grin. */
private fun DrawScope.drawSparkles(phase: Float) {
    listOf(0.27f to 0.58f, 0.73f to 0.58f, 0.5f to 0.84f, 0.12f to 0.4f, 0.88f to 0.42f).forEachIndexed { i, (fx, fy) ->
        val t = ((phase + i * 0.2f) % 1f)
        val s = size.width * 0.035f * sin(t * Math.PI).toFloat()
        val c = Offset(size.width * fx, size.width * fy)
        drawLine(Color.White, c - Offset(s, 0f), c + Offset(s, 0f), size.width * 0.009f, StrokeCap.Round)
        drawLine(Color.White, c - Offset(0f, s), c + Offset(0f, s), size.width * 0.009f, StrokeCap.Round)
        drawLine(Color(0xFFFFF59D), c - Offset(s * 0.5f, s * 0.5f), c + Offset(s * 0.5f, s * 0.5f), size.width * 0.006f, StrokeCap.Round)
    }
}
