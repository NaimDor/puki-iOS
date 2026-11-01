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
import io.github.tomsadeh.puki.ui.components.popAway
import io.github.tomsadeh.puki.ui.myday.PukiCorner
import io.github.tomsadeh.puki.data.content.level3Shapes
import io.github.tomsadeh.puki.data.content.ShapePicture
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
    val alive = remember { Animatable(0f) }  // the finished picture comes alive: 0 → 1
    var showCard by remember { mutableStateOf(false) }
    LaunchedEffect(state.sessionComplete) {
        if (!state.sessionComplete) return@LaunchedEffect
        sfxPlayer.play(Sfx.WHOOSH)
        launch { targetReaction.celebrate() }  // (the house can't drive off, so it jumps for joy)
        alive.animateTo(1f, tween(1700, easing = FastOutSlowInEasing))
        delay(500)
        showCard = true
    }

    if (state.isLoading) return

    // Stuck? Ask again after a quiet moment; after misses, the right piece wiggles
    val help = rememberHelp(state.roundIndex, active = !round.won && !state.sessionComplete, onAssisted = viewModel.session::slip) {
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
                modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(
                    onBack = onBack,
                    roundIndex = state.roundIndex,
                    totalRounds = state.totalRounds,
                    starsEarned = state.totalStarsEarned
                )

                PromptBubble(
                    BilingualText(
                        "תזיז את ה${state.targetShape.name.he}!",
                        "Drag the ${state.targetShape.name.en}!",
                        heGirl = "תזיזי את ה${state.targetShape.name.he}!"
                    ).localized()
                )

                // The picture in the middle, its next place an empty hole; the pieces lie around it
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val side = minOf(maxHeight, maxWidth * 0.44f)
                    val pieceSize = minOf(190.dp, maxHeight / 2.2f)
                    val picture = state.picture
                    val done = state.roundIndex + (if (state.roundComplete || state.sessionComplete) 1 else 0)
                    val slot = picture.slots[state.roundIndex.coerceAtMost(picture.slots.lastIndex)]
                    val wholeScreen = with(LocalDensity.current) { (maxWidth + maxHeight).toPx() }
                    Box(
                        modifier = Modifier
                            .size(side)
                            .graphicsLayer {
                                // The finished picture is off: the car drives away, the rocket takes off
                                when (picture) {
                                    ShapePicture.CAR -> translationX = -alive.value * alive.value * wholeScreen
                                    ShapePicture.ROCKET -> translationY = -alive.value * alive.value * wholeScreen
                                    ShapePicture.HOUSE -> Unit
                                }
                            }
                    ) {
                        DropZone(zoneId = "target", modifier = Modifier.fillMaxSize().reaction(targetReaction)) {
                            Canvas(modifier = Modifier.fillMaxSize()) { drawPicture(picture, done, allShapesById, alive.value) }
                        }
                        // The place itself, for a tap to fly to
                        DropZone(
                            zoneId = "slot",
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset(side * (slot.x - slot.size / 2), side * (slot.y - slot.size / 2))
                                .size(side * slot.size)
                        ) {}
                    }

                    // (clear of Puki's corner)
                    Scatter(seed = sessionSeed + state.roundIndex, aroundCenter = true, modifier = Modifier.fillMaxSize().padding(horizontal = PukiCorner * 0.6f)) {
                        state.shapeChoices.forEachIndexed { index, shape ->
                            key(state.roundIndex, shape.id) {
                                val shapeReaction = rememberReactionState(state.roundIndex, shape.id)
                                val right = shape.id == state.targetShape.id
                                DragTarget(
                                    itemId = shape.id,
                                    tapToZone = "slot",  // a tap tries it in the picture
                                    landed = { it in PICTURE_ZONES && right },
                                    modifier = Modifier.popAway((state.roundComplete || state.sessionComplete) && !right, index),
                                    onDragStart = { voicePlayer.play("voice_shape_${shape.id}", language) },
                                    onDragEnd = { dropTargetId ->
                                        if (dropTargetId in PICTURE_ZONES) {
                                            val correct = viewModel.onShapeDropped(shape.id)
                                            if (correct == true) {
                                                sfxPlayer.play(Sfx.DROP)
                                                sfxPlayer.play(Sfx.SUCCESS, 0.7f)
                                                companion.reactOr("במשחק הצורות שמו את ה${shape.name.he} במקום הנכון!", "In the shape game the child put the ${shape.name.en.lowercase()} in the right place!") { voicePlayer.playPraise(language) }
                                                scope.launch { targetReaction.hop() }
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
                                    Canvas(modifier = Modifier.size(pieceSize).popIn(index, state.roundIndex).idleBob(index).reaction(shapeReaction)
                                        .hintGlow(help.showHint && right)) {
                                        val c = Offset(size.width / 2, size.height / 2)
                                        drawShapeFilled(shape, c, size.minDimension * 0.4f)
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
}

private val PICTURE_ZONES = setOf("target", "slot")
private val allShapesById = level3Shapes.associateBy { it.id }

/** A chunky toy piece: a shadow, a top-lit fill and a white rim. */
private fun DrawScope.drawShapeFilled(shape: ShapeDef, c: Offset, r: Float) {
    val fill = Brush.verticalGradient(
        listOf(lerp(shape.color, Color.White, 0.3f), shape.color, lerp(shape.color, Color.Black, 0.15f)),
        startY = c.y - r, endY = c.y + r
    )
    val path = shapePath(shape, c, r)
    drawPath(shapePath(shape, c + Offset(0f, r * 0.05f), r), Color.Black.copy(alpha = 0.15f))
    drawPath(path, fill)
    drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(width = r * 0.05f))
}

/** The empty place for [shape]: a dark hole with a dashed rim, which reads whatever the piece's color. */
private fun DrawScope.drawSlotHole(shape: ShapeDef, c: Offset, r: Float) {
    val path = shapePath(shape, c, r)
    drawPath(path, Color(0x664E342E))
    drawPath(path, Color(0xCC4E342E), style = Stroke(width = r * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.2f, r * 0.14f))))
}

/** The picture: its base, the [done] places filled, the next one a hole, and later ones not yet shown. */
private fun DrawScope.drawPicture(picture: ShapePicture, done: Int, shapes: Map<String, ShapeDef>, alive: Float) {
    val w = size.width
    when (picture) {
        ShapePicture.HOUSE -> {
            drawOval(Color(0xFFA5D6A7), Offset(w * 0.02f, w * 0.86f), Size(w * 0.96f, w * 0.13f))
        }
        ShapePicture.CAR -> {
            val body = Color(0xFF26A69A)
            drawOval(Color.Black.copy(alpha = 0.12f), Offset(w * 0.08f, w * 0.85f), Size(w * 0.84f, w * 0.08f))
            drawRoundRect(body, Offset(w * 0.3f, w * 0.18f), Size(w * 0.4f, w * 0.34f), CornerRadius(w * 0.07f))
            drawRoundRect(body, Offset(w * 0.08f, w * 0.44f), Size(w * 0.84f, w * 0.3f), CornerRadius(w * 0.08f))
            drawCircle(Color(0xFFFFF59D), w * 0.035f, Offset(w * 0.13f, w * 0.54f))
        }
        ShapePicture.ROCKET -> {
            val fin = Color(0xFFEF5350)
            for (s in listOf(-1f, 1f)) {
                drawPath(Path().apply {
                    moveTo(w * (0.5f + s * 0.15f), w * 0.58f)
                    lineTo(w * (0.5f + s * 0.33f), w * 0.86f)
                    lineTo(w * (0.5f + s * 0.15f), w * 0.8f)
                    close()
                }, fin)
            }
            drawRoundRect(Color(0xFFECEFF1), Offset(w * 0.33f, w * 0.24f), Size(w * 0.34f, w * 0.64f), CornerRadius(w * 0.1f))
            if (alive > 0f) {
                drawOval(Color(0xFFFF9800), Offset(w * 0.4f, w * 0.93f), Size(w * 0.2f, w * 0.3f))
                drawOval(Color(0xFFFFEE58), Offset(w * 0.45f, w * 0.93f), Size(w * 0.1f, w * 0.18f))
            }
        }
    }
    picture.slots.forEachIndexed { i, slot ->
        if (i > done) return@forEachIndexed
        val shape = shapes.getValue(slot.shapeId)
        val c = Offset(w * slot.x, w * slot.y)
        val r = w * slot.size / 2
        if (i < done) drawShapeFilled(shape, c, r) else drawSlotHole(shape, c, r)
    }
}

/** The outline of [shape]. Square sits flat, diamond stands on a point. */
private fun shapePath(shape: ShapeDef, c: Offset, r: Float): Path = when (shape.id) {
    "circle" -> Path().apply { addOval(Rect(c, r)) }
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
