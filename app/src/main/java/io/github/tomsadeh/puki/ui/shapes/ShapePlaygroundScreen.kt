package io.github.tomsadeh.puki.ui.shapes

import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberRoundFlow
import io.github.tomsadeh.puki.ui.components.rememberSessionSeed
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.tomsadeh.puki.ui.components.Scatter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.PromptBubble
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.ShapeDef
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.localized
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.DragDropState
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.DragTarget
import io.github.tomsadeh.puki.ui.components.DropZone
import io.github.tomsadeh.puki.ui.components.LocalDragDropState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import io.github.tomsadeh.puki.audio.reactOr
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ShapePlaygroundScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: ShapePlaygroundViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionSeed = rememberSessionSeed()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val dragDropState = remember { DragDropState() }
    val round = rememberRoundFlow()
    val targetReaction = rememberReactionState(state.roundIndex)

    if (state.isLoading) return

    // Stuck? Ask again after a quiet moment; after misses, the right piece wiggles
    val help = rememberHelp(state.roundIndex, active = !round.won) {
        voicePlayer.playSequence(listOf("voice_prompt_drag_it", "voice_shape_${state.targetShape.id}"), language)
    }

    RoundPrompt(
        state.roundIndex,
        scene = "במשחק הצורות: מחפשים ${state.targetShape.name.he}.",
        sceneEn = "In the shape game: we're looking for the ${state.targetShape.name.en.lowercase()}.",
        "voice_prompt_drag_it", "voice_shape_${state.targetShape.id}"
    )

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
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

                Spacer(modifier = Modifier.height(16.dp))

                PromptBubble(
                    BilingualText(
                        "תזיז את ה${state.targetShape.name.he}!",
                        "Drag the ${state.targetShape.name.en}!",
                        heGirl = "תזיזי את ה${state.targetShape.name.he}!"
                    ).localized()
                )

                // Target outline (drop zone) in center
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    DropZone(zoneId = "target") {
                        Canvas(modifier = Modifier.size(270.dp).reaction(targetReaction)) {
                            drawShapeOutline(state.targetShape, size)
                        }
                    }

                    // The pieces lie around the target, half on each side
                    if (!state.roundComplete) {
                        Scatter(seed = sessionSeed + state.roundIndex, aroundCenter = true, modifier = Modifier.fillMaxSize()) {
                            state.shapeChoices.forEachIndexed { index, shape ->
                                val shapeReaction = rememberReactionState(state.roundIndex, shape.id)
                                DragTarget(
                                    itemId = shape.id,
                                    tapToZone = "target",  // a tap tries it in the outline
                                    onDragStart = { voicePlayer.play("voice_shape_${shape.id}", language) },
                                    onDragEnd = { dropTargetId ->
                                        if (dropTargetId == "target") {
                                            val correct = viewModel.onShapeDropped(shape.id)
                                            if (correct == true) {
                                                sfxPlayer.play(Sfx.SUCCESS)
                                                companion.reactOr("במשחק הצורות שמו את ה${shape.name.he} במקום הנכון!", "In the shape game the child put the ${shape.name.en.lowercase()} in the right place!") { voicePlayer.playPraise(language) }
                                                scope.launch { targetReaction.celebrate() }
                                                round.win(2000, 3400) {
                                                    viewModel.nextRound()
                                                }
                                            } else if (correct == false) {
                                                sfxPlayer.play(Sfx.WRONG_GENTLE)
                                                companion.reactOr("במשחק הצורות ניסו לשים ${shape.name.he} במקום של ${state.targetShape.name.he}.", "In the shape game the child tried to put the ${shape.name.en.lowercase()} where the ${state.targetShape.name.en.lowercase()} goes.") { voicePlayer.play("voice_prompt_try_again", language) }
                                                help.missed()
                                                scope.launch { shapeReaction.shake() }
                                            }
                                        }
                                    }
                                ) {
                                    Canvas(modifier = Modifier.size(190.dp).popIn(index, state.roundIndex).idleBob(index).reaction(shapeReaction)
                                        .hintGlow(help.showHint && shape.id == state.targetShape.id)) {
                                        drawShapeFilled(shape, size)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ConfettiOverlay(show = round.won)

            if (state.sessionComplete) {
                RoundCompleteSheet(
                    starsEarned = state.totalStarsEarned,
                    onContinue = onPlayAgain,
                    onHome = onBack
                )
            }
        }
    }
}

private fun DrawScope.drawShapeOutline(shape: ShapeDef, canvasSize: Size) {
    val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
    val radius = canvasSize.minDimension / 2 * 0.8f
    // A soft "hole" with a dashed rim, where the shape goes
    val dash = Stroke(width = 10f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 18f)))
    val path = shapePath(shape, center, radius)
    if (path == null) {
        drawCircle(Color.White.copy(alpha = 0.45f), radius, center)
        drawCircle(shape.color, radius, center, style = dash)
    } else {
        drawPath(path, Color.White.copy(alpha = 0.45f))
        drawPath(path, shape.color, style = dash)
    }
}

private fun DrawScope.drawShapeFilled(shape: ShapeDef, canvasSize: Size) {
    val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
    val radius = canvasSize.minDimension / 2 * 0.8f
    // Chunky toy pieces: a shadow, a top-lit fill and a white rim
    val fill = Brush.verticalGradient(listOf(lerp(shape.color, Color.White, 0.3f), shape.color, lerp(shape.color, Color.Black, 0.15f)))
    val shadow = Offset(0f, 8f)
    val path = shapePath(shape, center, radius)
    if (path == null) {
        drawCircle(Color.Black.copy(alpha = 0.15f), radius, center + shadow)
        drawCircle(fill, radius, center)
        drawCircle(Color.White.copy(alpha = 0.8f), radius, center, style = Stroke(width = 6f))
    } else {
        drawPath(shapePath(shape, center + shadow, radius)!!, Color.Black.copy(alpha = 0.15f))
        drawPath(path, fill)
        drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(width = 6f))
    }
}

/** The outline of [shape] (null for a circle). Square sits flat, diamond stands on a point. */
private fun shapePath(shape: ShapeDef, c: Offset, r: Float): Path? = when (shape.id) {
    "circle" -> null
    "square" -> Path().apply {
        val h = r * 0.78f
        moveTo(c.x - h, c.y - h); lineTo(c.x + h, c.y - h); lineTo(c.x + h, c.y + h); lineTo(c.x - h, c.y + h); close()
    }
    "diamond" -> Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.72f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.72f, c.y); close()
    }
    "heart" -> Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.35f, c.y + r * 0.05f, c.x - r * 0.8f, c.y - r * 1.1f, c.x, c.y - r * 0.42f)
        cubicTo(c.x + r * 0.8f, c.y - r * 1.1f, c.x + r * 1.35f, c.y + r * 0.05f, c.x, c.y + r * 0.9f)
        close()
    }
    "star" -> Path().apply {
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = (i * 36f - 90f) * Math.PI.toFloat() / 180f
            val x = c.x + cos(a) * rr
            val y = c.y + sin(a) * rr + r * 0.08f
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    else -> polygonPath(c, r, shape.sides)
}

private fun polygonPath(center: Offset, radius: Float, sides: Int): Path {
    val path = Path()
    val angleStep = 360f / sides
    for (i in 0 until sides) {
        val angle = (angleStep * i - 90f) * Math.PI.toFloat() / 180f
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
