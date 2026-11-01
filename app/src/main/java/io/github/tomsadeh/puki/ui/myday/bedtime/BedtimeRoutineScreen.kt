package io.github.tomsadeh.puki.ui.myday.bedtime

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalGender
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.DragDropState
import io.github.tomsadeh.puki.ui.components.DragTarget
import io.github.tomsadeh.puki.ui.components.DropZone
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
import io.github.tomsadeh.puki.ui.myday.drawSleepingHead
import io.github.tomsadeh.puki.ui.myday.drawToddler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

private enum class BedStep { PAJAMAS, TEETH, TEDDY, LIGHT, STARS }

private const val BED = "bed"
private val NIGHT = Color(0xFF0B1240)

/** The stars on the ceiling: fractions of the screen. */
private val CEILING_STARS = listOf(0.36f to 0.1f, 0.5f to 0.17f, 0.62f to 0.09f, 0.76f to 0.16f, 0.88f to 0.1f)

class BedtimeRoutineViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("bedtime", (application as PukiApplication).progressRepository, viewModelScope)
}

/**
 * Getting ready for bed in her room: pajamas on, teeth brushed, teddy in bed, the lamp off, and
 * stars on the ceiling. The room gets slowly darker, she's tucked in, and with the brain on, Puki
 * tells a story. No confetti here: it's bedtime.
 */
@Composable
fun BedtimeRoutineScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,
    viewModel: BedtimeRoutineViewModel = viewModel()
) {
    val companion = LocalCompanion.current
    LaunchedEffect(Unit) { companion?.wake() }  // now, so the story is ready by the end
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val girl = LocalGender.current == Gender.GIRL
    val scope = rememberCoroutineScope()
    // The calm night track while getting ready for bed, back to the main theme afterwards
    val musicPlayer = LocalMusicPlayer.current
    DisposableEffect(Unit) {
        musicPlayer.playTrack("music_night")
        onDispose {
            musicPlayer.duck(false)
            musicPlayer.playTrack("music_hub")
        }
    }

    val game = rememberStepGame(BedStep.PAJAMAS, BedStep.entries.size, viewModel.session)
    val step = game.step
    var pajamasOn by remember { mutableStateOf(false) }
    var brushing by remember { mutableStateOf(false) }
    var teddyInBed by remember { mutableStateOf(false) }
    var lampOn by remember { mutableStateOf(true) }
    var starsOut by remember { mutableStateOf(0) }
    var starring by remember { mutableStateOf(false) }
    var tucked by remember { mutableStateOf(false) }
    val busy = game.betweenSteps || brushing || starring || tucked

    val help = rememberHelp(step, active = !busy) {
        voicePlayer.play(
            when (step) {
                BedStep.PAJAMAS -> "voice_bedtime_pajamas"
                BedStep.TEETH -> "voice_bedtime_brush"
                BedStep.TEDDY -> "voice_bedtime_teddy"
                BedStep.LIGHT -> "voice_bedtime_light"
                BedStep.STARS -> "voice_bedtime_stars"
            },
            language
        )
    }
    when (step) {
        BedStep.PAJAMAS -> RoundPrompt(0, "בשגרת השינה: לובשים פיג'מה.", "Bedtime: putting on pajamas.", "voice_bedtime_pajamas")
        BedStep.TEETH -> RoundPrompt(1, "בשגרת השינה: מצחצחים שיניים.", "Bedtime: brushing teeth.", "voice_bedtime_brush")
        BedStep.TEDDY -> RoundPrompt(2, "בשגרת השינה: שמים את הדובי במיטה.", "Bedtime: putting teddy in bed.", "voice_bedtime_teddy")
        BedStep.LIGHT -> RoundPrompt(3, "בשגרת השינה: מכבים את האור.", "Bedtime: turning off the light.", "voice_bedtime_light")
        BedStep.STARS -> RoundPrompt(4, "בשגרת השינה: כוכבים בשמיים, לילה טוב.", "Bedtime: stars in the sky, good night.", "voice_bedtime_stars")
    }

    // The room gets dark slowly, step by step, and properly once the lamp is off
    val dark by animateFloatAsState(
        if (lampOn) step.ordinal * 0.05f else if (tucked) 0.72f else 0.62f, tween(1400), label = "dark"
    )
    val lamp by animateFloatAsState(if (lampOn) 1f else 0f, tween(700), label = "lamp")
    val moon by animateFloatAsState(step.ordinal / 4f, tween(1400), label = "moon")
    val pajamas by animateFloatAsState(if (pajamasOn) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "pajamas")
    val child by animateFloatAsState(if (tucked) 0f else 1f, tween(900), label = "child")
    val asleep by animateFloatAsState(if (tucked) 1f else 0f, tween(900), label = "asleep")
    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "twinkle"
    )

    CompositionLocalProvider(LocalDragDropState provides remember { DragDropState() }) {
        GameBackground {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().noticeTouches(help)) {
                val w = maxWidth
                val h = maxHeight
                val childSide = h * 0.62f
                val childX = w * 0.13f
                val childY = h * 0.9f - childSide * 0.93f
                val tool = minOf(h * 0.24f, 150.dp).coerceAtLeast(100.dp)
                val headSide = minOf(h * 0.44f, w * 0.26f)
                fun Rect.place() = Modifier.absoluteOffset(w * left, h * top).size(w * width, h * height)

                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawBedroom() }

                // The child, in the day's clothes until the pajamas go on
                if (child > 0f) Canvas(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(childX, childY)
                        .size(childSide)
                        .graphicsLayer { alpha = child }
                ) {
                    drawToddler(
                        girl,
                        if (pajamas > 0.05f) mapOf("underwear" to 1f, "pajamas" to pajamas)
                        else mapOf("underwear" to 1f, "shirt" to 1f, "pants" to 1f, "socks" to 1f)
                    )
                }
                if (brushing) BrushAtMouth(
                    size = childSide * 0.26f,
                    modifier = Modifier.align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(childX + childSide * 0.56f, childY + childSide * 0.2f)
                )

                // Asleep on the pillow, with the blanket pulled up
                if (asleep > 0f) {
                    Canvas(
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            .absoluteOffset(w * PillowCenter.x - headSide / 2, h * PillowCenter.y - headSide * 0.25f)
                            .size(headSide)
                            .graphicsLayer { alpha = asleep }
                    ) { drawSleepingHead(girl) }
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = asleep }) { drawTuckedBlanket() }
                }

                // The bed takes teddy
                DropZone(zoneId = BED, modifier = Modifier.align(AbsoluteAlignment.TopLeft).then(BedArea.place())) {}
                if (teddyInBed) Image(
                    painter = painterResource(R.drawable.ic_tidy_teddy),
                    contentDescription = null,
                    modifier = Modifier.align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(w * 0.55f, h * 0.42f)
                        .size(h * 0.22f)
                        .popIn()
                ) else DragTarget(
                    itemId = "teddy",
                    tapToZone = if (step == BedStep.TEDDY) BED else null,
                    modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * 0.5f, h * 0.76f),
                    onDragEnd = { zone ->
                        if (step == BedStep.TEDDY && zone == BED) {
                            teddyInBed = true
                            game.done(BedStep.LIGHT, party = false)
                        }
                    }
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_tidy_teddy),
                        contentDescription = null,
                        modifier = Modifier.hintGlow(step == BedStep.TEDDY && help.showHint).size(h * 0.2f).idleBob(amount = 0.4f)
                    )
                }

                // Everything dims together, except the window, the lamp and the stars
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = dark }) { drawRect(NIGHT) }
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawLamp(lamp) }
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawNightWindow(moon) }
                CEILING_STARS.take(starsOut).forEachIndexed { i, (fx, fy) ->
                    Image(
                        painter = painterResource(R.drawable.ic_ui_star),
                        contentDescription = null,
                        modifier = Modifier.align(AbsoluteAlignment.TopLeft)
                            .absoluteOffset(w * fx, h * fy)
                            .size(minOf(h * 0.1f, 56.dp))
                            .popIn()
                            .graphicsLayer { alpha = 0.75f + 0.25f * sin((twinkle + i * 0.2f) * 2 * Math.PI).toFloat() }
                    )
                }

                // What each step answers to
                val toolAt = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * 0.5f, h * 0.34f)
                if (step == BedStep.PAJAMAS && !busy) ToolButton(
                    size = tool,
                    hint = help.showHint,
                    color = Color(0xFFF3E5F5),
                    modifier = toolAt,
                    onClick = {
                        sfxPlayer.play(Sfx.WHOOSH, 0.5f)
                        pajamasOn = true
                        game.done(BedStep.TEETH, party = false)
                    }
                ) { Image(painterResource(R.drawable.ic_clothing_pajamas), null, Modifier.size(tool * 0.8f)) }
                if (step == BedStep.TEETH && !busy) ToolButton(
                    size = tool,
                    hint = help.showHint,
                    modifier = toolAt,
                    onClick = {
                        brushing = true
                        scope.launch {
                            repeat(3) {
                                sfxPlayer.play(Sfx.MARACAS, 0.35f)
                                delay(450)
                            }
                            brushing = false
                            game.done(BedStep.TEDDY, party = false)
                        }
                    }
                ) { Image(painterResource(R.drawable.ic_myday_toothbrush), null, Modifier.size(tool * 0.8f)) }
                if (step == BedStep.LIGHT && !busy) TapArea(Modifier.align(AbsoluteAlignment.TopLeft).then(LampArea.place()), help.showHint) {
                    sfxPlayer.play(Sfx.POP)
                    lampOn = false
                    game.done(BedStep.STARS, party = false)
                }
                if (step == BedStep.STARS && !busy) TapArea(Modifier.align(AbsoluteAlignment.TopLeft).then(WindowArea.place()), help.showHint) {
                    starring = true
                    scope.launch {
                        repeat(CEILING_STARS.size) {
                            sfxPlayer.play(Sfx.STAR_COLLECT, 0.5f)
                            starsOut = it + 1
                            delay(420)
                        }
                        game.done(null, party = false)
                        delay(600)
                        tucked = true
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GameTopBar(onBack = onBack, roundIndex = step.ordinal, totalRounds = BedStep.entries.size, starsEarned = game.stars)
                }
            }

            // פוקי's bedtime story, when the brain is reachable; then the quiet end card
            if (tucked) BedtimeStory(onFinished = { game.finish() })
            if (game.sessionComplete) {
                RoundCompleteSheet(starsEarned = game.stars, onContinue = onPlayAgain, onHome = onBack, calm = true)
            }
        }
    }
}

/** A part of the room that answers a tap, glowing as a hint. */
@Composable
private fun TapArea(modifier: Modifier, hint: Boolean, onTap: () -> Unit) {
    Box(
        modifier = modifier
            .hintGlow(hint)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    )
}

/** The toothbrush going back and forth at her mouth. */
@Composable
private fun BrushAtMouth(size: Dp, modifier: Modifier) {
    val move = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            move.animateTo(1f, tween(160))
            move.animateTo(-1f, tween(160))
        }
    }
    Image(
        painter = painterResource(R.drawable.ic_myday_toothbrush),
        contentDescription = null,
        modifier = modifier.size(size).graphicsLayer { translationX = move.value * 8.dp.toPx() }
    )
}
