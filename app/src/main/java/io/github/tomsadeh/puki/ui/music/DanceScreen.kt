package io.github.tomsadeh.puki.ui.music

import android.app.Application
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.audio.reactOr
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.level1Animals
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.reaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** What Puki calls out when the music stops; the animals do it too, and so does the child. */
private enum class Move(val voice: String) {
    FREEZE("voice_dance_freeze"), JUMP("voice_dance_jump"), CLAP("voice_dance_clap"), SPIN("voice_dance_spin")
}

private const val STOPS = 5  // music stops in one dance

class DanceViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("dance", (application as PukiApplication).progressRepository, viewModelScope)
}

/**
 * Dance and Freeze: the animals dance while the music plays; when it stops Puki calls out a move
 * (freeze, jump, clap, spin) and everyone does it, away from the screen. Then the dancing goes on.
 */
@Composable
fun DanceScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,
    viewModel: DanceViewModel = viewModel()
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val musicPlayer = LocalMusicPlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val companion = LocalCompanion.current
    val scope = rememberCoroutineScope()

    var started by remember { mutableStateOf(false) }
    var dancing by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf<Move?>(null) }
    var stops by remember { mutableStateOf(0) }
    var celebrate by remember { mutableStateOf(false) }
    var sessionComplete by remember { mutableStateOf(false) }
    // The dance eases out when the music stops, instead of everyone snapping still
    val dance by animateFloatAsState(if (dancing) 1f else 0f, tween(350), label = "dance_amount")
    val help = rememberHelp(started, active = !started) { voicePlayer.play("voice_dance_intro", language) }
    val dancers = remember { level1Animals.shuffled().take(3) }
    val reactions = remember { dancers.map { ReactionState() } }

    // The music is the game: make sure it's the lively track, and never leave it paused
    LaunchedEffect(Unit) {
        companion?.setScene("ריקוד ועצירה: רוקדים עם החיות, וכשהמוזיקה נעצרת עושים מה שפוקי אומר.", "Dance and freeze: dancing with the animals, and when the music stops doing what Puki says.")
        // The dance track (res/raw/music_dance.mp3) once there is one; until then the main theme plays on
        musicPlayer.playTrack("music_dance")
        delay(500)
        voicePlayer.play("voice_dance_intro", language)
    }
    DisposableEffect(Unit) {
        onDispose {
            musicPlayer.resume()
            musicPlayer.playTrack("music_hub")
        }
    }

    // One dance: play for a while, stop, call a move, hold it, and again
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        var last: Move? = null
        repeat(STOPS) { i ->
            dancing = true
            move = null
            musicPlayer.resume()
            delay(Random.nextLong(5000, 9000))
            dancing = false
            musicPlayer.pause()
            val next = (Move.entries - setOfNotNull(last)).random()
            last = next
            move = next
            voicePlayer.play(next.voice, language)
            delay(3800)
            stops = i + 1
            viewModel.session.star()
            sfxPlayer.play(Sfx.STAR_COLLECT, 0.7f)
            if (i < STOPS - 1) {
                voicePlayer.play("voice_dance_again", language)
                delay(900)
            }
        }
        // The end: everyone takes a bow
        move = null
        musicPlayer.resume()
        celebrate = true
        sfxPlayer.play(Sfx.SUCCESS)
        reactions.forEachIndexed { i, r -> launch { delay(i * 120L); r.celebrate() } }
        companion.reactOr("ריקוד ועצירה: רקדו עד הסוף ועצרו בכל פעם!", "Dance and Freeze: the child danced to the end and stopped every time!") {
            voicePlayer.playPraise(language)
        }
        delay(2600)
        celebrate = false
        viewModel.session.finish(STOPS)
        sessionComplete = true
    }

    GameBackground(scene = Scene.STAGE) {
        // Read only inside drawing and graphicsLayer, so the beat redraws without recomposing
        val beat = rememberInfiniteTransition(label = "beat").animateFloat(
            0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Restart), label = "beat_phase"
        )
        val spin = rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin_turn"
        )
        val ball = rememberInfiniteTransition(label = "ball").animateFloat(
            0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "ball_turn"
        )
        // The mirror ball over the stage, turning all the time; the lights it throws dance with the music
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            drawLine(Color(0xFFB0BEC5), Offset(size.width * 0.74f, 0f), Offset(size.width * 0.74f, size.height * 0.1f), size.height * 0.006f)
            drawDiscoBall(Offset(size.width * 0.74f, size.height * 0.17f), size.height * 0.08f, ball.value)
            if (dance > 0f) drawBallLights(ball.value, dance)
        }
        Column(modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(onBack = onBack, roundIndex = stops.coerceAtMost(STOPS - 1), totalRounds = STOPS, starsEarned = stops)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                val side = minOf(maxWidth / 3.6f, maxHeight * 0.66f)
                val gap = side * 0.15f
                val slot = minOf(maxHeight * 0.32f, 130.dp)
                // Spots on the floor that flash to the beat and go still with the music, and the dancers' shadows
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    drawDanceFloor(beat.value, dance, side.toPx(), gap.toPx())
                }
                Row(
                    modifier = Modifier.padding(bottom = maxHeight * 0.05f),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Bottom
                ) {
                    dancers.forEachIndexed { i, animal ->
                        Image(
                            painter = painterResource(animal.drawableRes),
                            contentDescription = null,
                            modifier = Modifier
                                .then(if (started) Modifier else Modifier.idleBob(i, 0.8f))
                                .size(side)
                                .reaction(reactions[i])
                                .graphicsLayer {
                                    val wave = sin(((beat.value + i * 0.33f) % 1f) * 2 * Math.PI).toFloat()
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    when {
                                        dance > 0f -> {  // sway and bounce to the beat
                                            rotationZ = wave * 12f * dance
                                            translationY = -kotlin.math.abs(wave) * side.toPx() * 0.12f * dance
                                        }
                                        move == Move.JUMP -> translationY = -kotlin.math.abs(wave) * side.toPx() * 0.3f
                                        move == Move.SPIN -> rotationY = spin.value
                                        move == Move.CLAP -> { scaleX = 1f + 0.08f * wave; scaleY = 1f - 0.06f * wave }
                                        move == Move.FREEZE -> rotationZ = if (i % 2 == 0) -10f else 10f  // a funny frozen pose
                                    }
                                }
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    scope.launch { reactions[i].celebrate() }
                                    voicePlayer.play("voice_sound_${animal.id}", language)
                                }
                        )
                    }
                }
                // Up above the dancers: the start, and then the move being called, big and simple
                Box(modifier = Modifier.align(Alignment.TopCenter).size(slot), contentAlignment = Alignment.Center) {
                    move?.let { m ->
                        Canvas(
                            modifier = Modifier
                                .popIn(key = m)
                                .idleBob(amount = 0.8f)
                                .size(slot)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) { drawMoveSign(m) }
                    }
                    if (!started) Box(
                        modifier = Modifier
                            .popIn()
                            .idleBob(0, 1.4f)
                            .hintGlow(help.showHint)
                            .size(slot)
                            .shadow(10.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFFFFCA28))
                            .border(6.dp, Color.White, CircleShape)
                            .clickable {
                                started = true
                                sfxPlayer.play(Sfx.POP)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(slot * 0.5f)) { drawPlayTriangle() }
                    }
                }
            }
        }
        ConfettiOverlay(show = celebrate)
        if (sessionComplete) RoundCompleteSheet(starsEarned = stops, onContinue = onPlayAgain, onHome = onBack)
    }
}

private fun DrawScope.drawPlayTriangle() {
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(w * 0.2f, h * 0.08f); lineTo(w, h * 0.5f); lineTo(w * 0.2f, h * 0.92f); close() }, Color.White)
}

/** A picture for each move, so even a child who doesn't follow the words sees what to do. */
private fun DrawScope.drawMoveSign(m: Move) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension * 0.34f
    when (m) {
        Move.FREEZE -> {  // a snowflake
            val ice = Color(0xFF4FC3F7)
            for (i in 0 until 6) rotate(i * 60f, c) {
                drawLine(ice, c, c - Offset(0f, r), r * 0.14f, StrokeCap.Round)
                for (s in listOf(-1f, 1f)) drawLine(ice, c - Offset(0f, r * 0.62f), c - Offset(s * r * 0.26f, r * 0.9f), r * 0.1f, StrokeCap.Round)
            }
        }
        Move.JUMP -> {  // up, up
            val green = Color(0xFF66BB6A)
            for (k in 0..1) {
                val y = c.y + r * (0.5f - k * 0.75f)
                val tip = Path().apply { moveTo(c.x - r * 0.75f, y + r * 0.4f); lineTo(c.x, y - r * 0.35f); lineTo(c.x + r * 0.75f, y + r * 0.4f) }
                drawPath(tip, green, style = Stroke(r * 0.28f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Move.CLAP -> {  // two hands, with little lines of sound
            val hand = Color(0xFFFFCC80)
            for (s in listOf(-1f, 1f)) {
                rotate(s * 16f, c + Offset(0f, r)) {
                    drawRoundRect(hand, c + Offset(s * r * 0.34f - r * 0.3f, -r * 0.5f), Size(r * 0.6f, r * 1.3f), CornerRadius(r * 0.28f))
                }
                drawLine(Color(0xFFFFB300), c + Offset(s * r * 0.9f, -r * 0.65f), c + Offset(s * r * 1.15f, -r * 0.95f), r * 0.13f, StrokeCap.Round)
            }
            drawLine(Color(0xFFFFB300), c - Offset(0f, r * 0.8f), c - Offset(0f, r * 1.15f), r * 0.13f, StrokeCap.Round)
        }
        Move.SPIN -> {  // a circle with an arrowhead
            val pink = Color(0xFFEC407A)
            drawArc(pink, -60f, 300f, false, c - Offset(r * 0.85f, r * 0.85f), Size(r * 1.7f, r * 1.7f), style = Stroke(r * 0.26f, cap = StrokeCap.Round))
            val tip = c + Offset(r * 0.42f, -r * 0.74f)
            drawPath(Path().apply { moveTo(tip.x + r * 0.5f, tip.y - r * 0.05f); lineTo(tip.x - r * 0.1f, tip.y - r * 0.42f); lineTo(tip.x - r * 0.05f, tip.y + r * 0.34f); close() }, pink)
        }
    }
}

/** A mirror ball at [c], its facets sliding round as [turn] goes 0 → 1. */
internal fun DrawScope.drawDiscoBall(c: Offset, r: Float, turn: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFECEFF1), Color(0xFF78909C)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.5f), r, c)
    val line = Color.White.copy(alpha = 0.55f)
    for (k in -2..2) {  // the rows
        val y = c.y + k * r * 0.36f
        val half = kotlin.math.sqrt((r * r - (y - c.y) * (y - c.y)).coerceAtLeast(0f))
        drawLine(line, Offset(c.x - half, y), Offset(c.x + half, y), r * 0.04f)
    }
    for (k in 0 until 6) {  // the columns, going round
        val x = c.x + r * kotlin.math.cos(((k / 6f + turn) * Math.PI).toFloat())
        val half = kotlin.math.sqrt((r * r - (x - c.x) * (x - c.x)).coerceAtLeast(0f))
        drawLine(line, Offset(x, c.y - half), Offset(x, c.y + half), r * 0.04f)
    }
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.12f, c - Offset(r * 0.36f, r * 0.4f))
}

private val LIGHTS = listOf(Color(0xFFFFEE58), Color(0xFFF48FB1), Color(0xFF81D4FA), Color(0xFFA5D6A7))

/** Flecks of light thrown round the stage by the ball. */
private fun DrawScope.drawBallLights(turn: Float, amount: Float) {
    for (i in 0 until 12) {
        val t = (turn + i / 12f) % 1f
        val x = size.width * (0.14f + 0.72f * t)
        val y = size.height * (0.3f + 0.42f * (((i * 37) % 10) / 10f))
        drawCircle(LIGHTS[i % LIGHTS.size].copy(alpha = 0.5f * amount), size.height * 0.012f, Offset(x, y))
    }
}

/** The dancers' shadows, and colored spots on the floor flashing to [beat] while the music plays. */
private fun DrawScope.drawDanceFloor(beat: Float, dance: Float, side: Float, gap: Float) {
    val y = size.height * 0.9f
    val total = 3 * side + 2 * gap
    for (k in 0 until 7) {
        val x = size.width * (0.14f + k * 0.12f)
        val flash = if ((beat * 2 + k * 0.5f) % 1f < 0.5f) 0.55f else 0.2f
        val alpha = 0.16f + (flash - 0.16f) * dance
        val r = side * 0.2f
        drawOval(LIGHTS[k % LIGHTS.size].copy(alpha = alpha), Offset(x - r, y - r * 0.28f), Size(r * 2, r * 0.56f))
    }
    for (i in 0..2) {
        val x = size.width / 2 - total / 2 + i * (side + gap)
        drawOval(Color.Black.copy(alpha = 0.18f), Offset(x + side * 0.14f, y - side * 0.045f), Size(side * 0.72f, side * 0.1f))
    }
}

/** The disco ball on the music stage that opens Dance and Freeze. */
@Composable
fun DanceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val turn by rememberInfiniteTransition(label = "door_ball").animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "door_ball_turn"
    )
    Box(
        modifier = modifier
            .idleBob(2, 1.2f)
            .size(110.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF4A148C))
            .border(5.dp, Color(0xFFFFCA28), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) { drawDiscoBall(Offset(size.width / 2, size.height / 2), size.width * 0.46f, turn) }
    }
}
