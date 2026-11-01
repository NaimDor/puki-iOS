package io.github.tomsadeh.puki.ui.colors

import io.github.tomsadeh.puki.ui.components.AnswerOutLoud
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.data.content.ColorDef
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberRoundFlow
import io.github.tomsadeh.puki.ui.components.rememberSessionSeed
import io.github.tomsadeh.puki.ui.components.scatterTilt
import io.github.tomsadeh.puki.ui.components.Scatter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.PromptBubble
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.reaction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import io.github.tomsadeh.puki.ui.components.popAway
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.localized
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import io.github.tomsadeh.puki.audio.reactOr
import kotlinx.coroutines.launch

@Composable
fun ColorSplashScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: ColorSplashViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionSeed = rememberSessionSeed()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val round = rememberRoundFlow()
    val found = remember { mutableStateListOf<Color>() }  // the session's colors, as splats on the grass
    val rainbow = remember { Animatable(0f) }
    var showCard by remember { mutableStateOf(false) }
    // The last balloon is popped: her colors make a rainbow, and then the end card
    LaunchedEffect(state.sessionComplete) {
        if (!state.sessionComplete) return@LaunchedEffect
        rainbow.animateTo(1f, tween(1500))
        delay(1300)
        showCard = true
    }

    if (state.isLoading) return

    // Stuck? Ask again after a quiet moment; after misses, the right balloon wiggles
    val help = rememberHelp(state.roundIndex, active = !round.won && !state.sessionComplete, onAssisted = viewModel.session::slip) {
        voicePlayer.playSequence(listOf("voice_prompt_tap_the", "voice_color_${state.targetColor.id}"), language)
    }

    RoundPrompt(
        state.roundIndex,
        scene = "במשחק הצבעים: מחפשים את הצבע ${state.targetColor.name.he}.",
        sceneEn = "In the color game: we're looking for ${state.targetColor.name.en.lowercase()}.",
        "voice_prompt_tap_the", "voice_color_${state.targetColor.id}"
    )

    val reactions = remember(state.roundIndex) { state.colorChoices.associate { it.id to ReactionState() } }

    // A balloon picked, by a tap or by saying its color
    fun pick(color: ColorDef) {
        val blobReaction = reactions[color.id] ?: return
        val correct = viewModel.onColorTapped(color.id) ?: return
        if (correct) {
            sfxPlayer.play(Sfx.POP)
            sfxPlayer.play(Sfx.SUCCESS, 0.7f)
            found.add(color.color)
            companion.reactOr("במשחק הצבעים בחרו ${color.name.he}. זה נכון!", "In the color game the child picked ${color.name.en.lowercase()}. That's right!") { voicePlayer.playPraise(language) }
            scope.launch { blobReaction.celebrate() }
            round.win(2000, 3400) {
                viewModel.nextRound()
            }
        } else {
            sfxPlayer.play(Sfx.WRONG_GENTLE)
            companion.reactOr("במשחק הצבעים בחרו ${color.name.he}, אבל חיפשנו ${state.targetColor.name.he}.", "In the color game the child picked ${color.name.en.lowercase()}, but we were looking for ${state.targetColor.name.en.lowercase()}.") { voicePlayer.playSequence(listOf("voice_color_${color.id}", "voice_prompt_try_again"), language) }
            help.missed()
            scope.launch { blobReaction.shake() }
        }
    }

    // "אדום!" picks the red balloon
    AnswerOutLoud(state.roundIndex, state.colorChoices.map { listOf(it.name.resolve(language)) }, active = !round.won && !state.sessionComplete) { i ->
        state.colorChoices.getOrNull(i)?.let { pick(it) }
    }

    GameBackground(
            scene = Scene.PLAYGROUND
    ) {
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                onBack = onBack,
                roundIndex = state.roundIndex,
                totalRounds = state.totalRounds,
                starsEarned = state.totalStarsEarned
            )

            // Prompt
            PromptBubble(
                BilingualText(
                    "תלחץ על ${state.targetColor.name.he}!",
                    "Tap the ${state.targetColor.name.en} one!",
                    heGirl = "תלחצי על ${state.targetColor.name.he}!"
                ).localized()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Balloons floating about, high and low
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val balloonHeight = minOf(250.dp, maxHeight)
            val roundOver = round.won || state.sessionComplete
            Scatter(seed = sessionSeed + state.roundIndex, modifier = Modifier.fillMaxSize()) {
                state.colorChoices.forEachIndexed { i, color ->
                    val right = color.id == state.targetColor.id
                    Box(modifier = Modifier.popIn(i, state.roundIndex).popAway(roundOver && !right, i).idleBob(i).reaction(reactions.getValue(color.id))
                        .hintGlow(help.showHint && color.id == state.targetColor.id)
                        .graphicsLayer { rotationZ = scatterTilt(sessionSeed + state.roundIndex, i) }) {
                    ColorBlob(
                        color = color.color,
                        height = balloonHeight,
                        popped = roundOver && right,
                        onClick = { pick(color) }
                    )
                    }
                }
            }
            }
        }

        // The colors she's found, splashed on the grass
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                found.forEachIndexed { i, c ->
                    Canvas(modifier = Modifier.popIn(key = i).size(52.dp)) { drawSplat(c, i) }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { if (rainbow.value > 0f) drawColorBow(found, rainbow.value) }

        ConfettiOverlay(show = round.won || state.sessionComplete)

        if (showCard) {
            RoundCompleteSheet(
                starsEarned = state.totalStarsEarned,
                onContinue = onPlayAgain,
                onHome = onBack
            )
        }
    }
}

/** A splash of paint in [color], lumpy in its own way for each [seed]. */
private fun DrawScope.drawSplat(color: Color, seed: Int) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension * 0.3f
    val rnd = Random(seed * 31 + 7)
    drawCircle(lerp(color, Color.Black, 0.2f), r * 1.06f, c)
    drawCircle(color, r, c)
    for (k in 0 until 7) {
        val a = k / 7f * 2 * Math.PI + rnd.nextFloat()
        val d = r * (0.95f + rnd.nextFloat() * 0.5f)
        drawCircle(color, r * (0.18f + rnd.nextFloat() * 0.2f), c + Offset((cos(a) * d).toFloat(), (sin(a) * d).toFloat()))
    }
    drawCircle(Color.White.copy(alpha = 0.5f), r * 0.2f, c - Offset(r * 0.35f, r * 0.35f))
}

/** A rainbow of the session's [colors], rising across the sky as [t] goes 0 → 1. */
private fun DrawScope.drawColorBow(colors: List<Color>, t: Float) {
    val c = Offset(size.width / 2, size.height * 0.92f)
    val band = size.height * 0.075f
    colors.forEachIndexed { i, color ->
        val r = size.height * 0.78f - i * band
        drawArc(color, 180f, 180f * t, false, c - Offset(r, r), Size(r * 2, r * 2), style = Stroke(band * 0.96f))
    }
}

/** A shiny balloon on a string, in [color], [height] tall; when [popped] it bursts into drops. */
@Composable
private fun ColorBlob(color: Color, height: Dp, popped: Boolean, onClick: () -> Unit) {
    val light = lerp(color, Color.White, 0.4f)
    val dark = lerp(color, Color.Black, 0.25f)
    val rim = lerp(color, Color.Black, 0.35f)
    val burst by animateFloatAsState(if (popped) 1f else 0f, if (popped) tween(420) else snap(), label = "burst")
    Canvas(
        modifier = Modifier
            .size(width = height * 0.68f, height = height)
            .clickable(onClick = onClick)
    ) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.88f
        val bodyH = h * 0.7f
        val left = (w - bodyW) / 2
        if (burst > 0f) {
            // Drops of paint flying out from where the balloon was
            val c = Offset(w / 2, bodyH / 2)
            for (k in 0 until 14) {
                val a = k / 14f * 2 * Math.PI
                val reach = bodyW * (0.45f + (k % 3) * 0.14f) * burst
                drawCircle(color.copy(alpha = 1f - burst * burst), w * (0.05f + (k % 2) * 0.03f),
                    c + Offset((cos(a) * reach).toFloat(), (sin(a) * reach).toFloat() + burst * burst * h * 0.12f))
            }
            if (burst >= 1f) return@Canvas
        }
        val fade = 1f - burst
        scale(1f + burst * 0.3f, pivot = Offset(w / 2, bodyH / 2)) {
            val string = Path().apply {
                moveTo(w / 2, bodyH + h * 0.04f)
                cubicTo(w * 0.36f, bodyH + h * 0.11f, w * 0.64f, bodyH + h * 0.2f, w * 0.47f, h)
            }
            drawPath(string, Color(0xFF8D6E63).copy(alpha = fade), style = Stroke(3.dp.toPx()))
            val knot = Path().apply {
                moveTo(w / 2, bodyH - 4f)
                lineTo(w / 2 - w * 0.065f, bodyH + h * 0.045f)
                lineTo(w / 2 + w * 0.065f, bodyH + h * 0.045f)
                close()
            }
            drawPath(knot, dark.copy(alpha = fade))
            drawOval(Color.Black.copy(alpha = 0.12f * fade), Offset(left + 7.dp.toPx(), 9.dp.toPx()), Size(bodyW, bodyH))
            // A white halo and a darker rim, so a white or yellow balloon still stands out
            drawOval(Color.White.copy(alpha = 0.6f * fade), Offset(left - 3.dp.toPx(), -3.dp.toPx()), Size(bodyW + 6.dp.toPx(), bodyH + 6.dp.toPx()))
            drawOval(
                Brush.radialGradient(listOf(light, color, dark), center = Offset(w * 0.38f, bodyH * 0.32f), radius = bodyW * 0.95f),
                Offset(left, 0f), Size(bodyW, bodyH), alpha = fade
            )
            drawOval(rim.copy(alpha = fade), Offset(left, 0f), Size(bodyW, bodyH), style = Stroke(2.5f.dp.toPx()))
            drawOval(Color.White.copy(alpha = 0.6f * fade), Offset(left + bodyW * 0.17f, bodyH * 0.12f), Size(bodyW * 0.2f, bodyH * 0.27f))
        }
    }
}
