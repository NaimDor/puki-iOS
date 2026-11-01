package io.github.tomsadeh.puki.ui.colors

import io.github.tomsadeh.puki.ui.components.AnswerOutLoud
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.data.content.ColorDef
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
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
    var showRoundSuccess by remember { mutableStateOf(false) }

    if (state.isLoading) return

    // Stuck? Ask again after a quiet moment; after misses, the right balloon wiggles
    val help = rememberHelp(state.roundIndex, active = !showRoundSuccess) {
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
            sfxPlayer.play(Sfx.SUCCESS)
            if (companion != null) companion.react("במשחק הצבעים בחרו ${color.name.he}. זה נכון!", "In the color game the child picked ${color.name.en.lowercase()}. That's right!") { voicePlayer.playPraise(language) } else voicePlayer.playPraise(language)
            showRoundSuccess = true
            scope.launch { blobReaction.celebrate() }
            scope.launch {
                delay(if (companion?.isReady == true) 3400 else 2000)
                showRoundSuccess = false
                viewModel.nextRound()
            }
        } else {
            sfxPlayer.play(Sfx.WRONG_GENTLE)
            if (companion != null) companion.react("במשחק הצבעים בחרו ${color.name.he}, אבל חיפשנו ${state.targetColor.name.he}.", "In the color game the child picked ${color.name.en.lowercase()}, but we were looking for ${state.targetColor.name.en.lowercase()}.") { voicePlayer.playSequence(listOf("voice_color_${color.id}", "voice_prompt_try_again"), language) } else voicePlayer.playSequence(listOf("voice_color_${color.id}", "voice_prompt_try_again"), language)
            help.missed()
            scope.launch { blobReaction.shake() }
        }
    }

    // "אדום!" picks the red balloon
    AnswerOutLoud(state.roundIndex, state.colorChoices.map { listOf(it.name.resolve(language)) }, active = !showRoundSuccess) { i ->
        state.colorChoices.getOrNull(i)?.let { pick(it) }
    }

    GameBackground(
            scene = Scene.PLAYGROUND
    ) {
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                onBack = onBack,
                roundIndex = state.roundIndex,
                totalRounds = state.totalRounds,
                starsEarned = state.totalStarsEarned
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Prompt
            PromptBubble(
                BilingualText(
                    "תלחץ על ${state.targetColor.name.he}!",
                    "Tap the ${state.targetColor.name.en} one!",
                    heGirl = "תלחצי על ${state.targetColor.name.he}!"
                ).localized()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Balloons floating about, high and low
            Scatter(seed = sessionSeed + state.roundIndex, modifier = Modifier.weight(1f).fillMaxWidth()) {
                state.colorChoices.forEachIndexed { i, color ->
                    Box(modifier = Modifier.popIn(i, state.roundIndex).idleBob(i).reaction(reactions.getValue(color.id))
                        .hintGlow(help.showHint && color.id == state.targetColor.id)
                        .graphicsLayer { rotationZ = scatterTilt(sessionSeed + state.roundIndex, i) }) {
                    ColorBlob(
                        color = color.color,
                        onClick = { pick(color) }
                    )
                    }
                }
            }
        }

        ConfettiOverlay(show = showRoundSuccess)

        if (state.sessionComplete) {
            RoundCompleteSheet(
                starsEarned = state.totalStarsEarned,
                onContinue = onPlayAgain,
                onHome = onBack
            )
        }
    }
}

/** A shiny balloon on a string, in [color]. */
@Composable
private fun ColorBlob(color: Color, onClick: () -> Unit) {
    val light = lerp(color, Color.White, 0.4f)
    val dark = lerp(color, Color.Black, 0.25f)
    Canvas(
        modifier = Modifier
            .size(width = 170.dp, height = 250.dp)
            .clickable(onClick = onClick)
    ) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.88f
        val bodyH = h * 0.7f
        val left = (w - bodyW) / 2
        val string = Path().apply {
            moveTo(w / 2, bodyH + h * 0.04f)
            cubicTo(w * 0.36f, bodyH + h * 0.11f, w * 0.64f, bodyH + h * 0.2f, w * 0.47f, h)
        }
        drawPath(string, Color(0xFF8D6E63), style = Stroke(3.dp.toPx()))
        val knot = Path().apply {
            moveTo(w / 2, bodyH - 4f)
            lineTo(w / 2 - 11.dp.toPx(), bodyH + h * 0.045f)
            lineTo(w / 2 + 11.dp.toPx(), bodyH + h * 0.045f)
            close()
        }
        drawPath(knot, dark)
        drawOval(Color.Black.copy(alpha = 0.12f), Offset(left + 7.dp.toPx(), 9.dp.toPx()), Size(bodyW, bodyH))
        drawOval(
            Brush.radialGradient(listOf(light, color, dark), center = Offset(w * 0.38f, bodyH * 0.32f), radius = bodyW * 0.95f),
            Offset(left, 0f), Size(bodyW, bodyH)
        )
        drawOval(Color.White.copy(alpha = 0.6f), Offset(left + bodyW * 0.17f, bodyH * 0.12f), Size(bodyW * 0.2f, bodyH * 0.27f))
    }
}
