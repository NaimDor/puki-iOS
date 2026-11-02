package io.github.tomsadeh.puki.ui.echo

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import io.github.tomsadeh.puki.ui.components.LocalAmbientClock
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absolutePadding
import io.github.tomsadeh.puki.ui.hub.components.StarCounter
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.PukiApplication
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AndroidViewModel
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import android.app.Application
import io.github.tomsadeh.puki.ui.components.TalkingParrotImage
import io.github.tomsadeh.puki.audio.hasMicPermission
import io.github.tomsadeh.puki.audio.micRefused
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.audio.EchoVoice
import io.github.tomsadeh.puki.audio.MicRecorder
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.common.awaitUnpaused
import io.github.tomsadeh.puki.ui.common.rememberGamePaused
import io.github.tomsadeh.puki.ui.components.BackButton
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.Scene
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class EchoMode { IDLE, LISTENING, ECHOING }

/**
 * Echo parrot: she says anything and Puki says it back in a silly parrot voice. Holding Puki
 * records until she lets go; a tap records until she stops talking. Works with no brain at all
 * (the recording only goes to the speaker, a moment later, and is never kept).
 */
class EchoViewModel(application: Application) : AndroidViewModel(application) {
    private val session = GameSession("echo", (application as PukiApplication).progressRepository, viewModelScope)
    fun star() = session.star()
}

/** A different parrot each time: squeaky, deep, squeakier. */
private val ECHO_PITCHES = listOf(1.45f, 0.8f, 1.8f)
private const val ECHOES_FOR_A_STAR = 3

@Composable
fun EchoScreen(onBack: () -> Unit, viewModel: EchoViewModel = viewModel()) {
    val context = LocalContext.current
    val voicePlayer = LocalVoicePlayer.current
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val paused = rememberGamePaused()
    val recorder = remember { MicRecorder() }
    var mode by remember { mutableStateOf(EchoMode.IDLE) }
    var lastActive by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    var echoes by remember { mutableStateOf(0) }
    var stars by remember { mutableStateOf(0) }
    var party by remember { mutableStateOf(false) }
    val puki = rememberReactionState()
    val sfxPlayer = LocalSfxPlayer.current
    var loudness by remember { mutableStateOf(0f) }  // how loud she is, smoothed, for the rings and the microphone
    LaunchedEffect(mode) {
        if (mode != EchoMode.LISTENING) {
            loudness = 0f
            return@LaunchedEffect
        }
        while (true) {
            loudness += ((recorder.level * 6f).coerceIn(0f, 1f) - loudness) * 0.35f
            delay(50)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.stopInBackground() } }

    // The microphone is asked for once, the first time she talks to Puki
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> micRefused = !granted }
    fun hasMic() = hasMicPermission(context)

    // "Say something to Puki!", and again (twice at most) if she goes quiet for a while
    LaunchedEffect(Unit) {
        delay(600)
        voicePlayer.play("voice_prompt_echo", language)
        repeat(2) {
            while (SystemClock.uptimeMillis() - lastActive < 15_000 || mode != EchoMode.IDLE) delay(1000)
            awaitUnpaused(paused)  // (not over the time limit's goodbye)
            voicePlayer.play("voice_prompt_echo", language)
            lastActive = SystemClock.uptimeMillis()
        }
    }

    fun echo() {
        mode = EchoMode.ECHOING
        scope.launch {
            val wav = recorder.stopOffMain()
            EchoVoice.play(wav, voicePlayer.level, ECHO_PITCHES[echoes % ECHO_PITCHES.size])
            mode = EchoMode.IDLE
            lastActive = SystemClock.uptimeMillis()
            echoes++
            if (echoes % ECHOES_FOR_A_STAR == 0) {
                // Every few echoes: a star, and Puki dances
                stars++
                viewModel.star()
                sfxPlayer.play(Sfx.SUCCESS)
                party = true
                puki.celebrate()
                delay(1800)
                party = false
            }
        }
    }

    /** Starts listening; with [untilQuiet] it stops by itself once she pauses (a tap), else on release. */
    suspend fun listen(untilQuiet: Boolean): Boolean {
        if (!hasMic()) {
            // Refused already: he tilts his head rather than a dialog that won't come
            if (micRefused) scope.launch { puki.oops() } else askMic.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        voicePlayer.stop()  // so the microphone doesn't hear the app
        if (!recorder.startOffMain()) return false
        mode = EchoMode.LISTENING
        lastActive = SystemClock.uptimeMillis()
        if (untilQuiet) scope.launch {
            val t0 = SystemClock.uptimeMillis()
            var heard = false
            var quietMs = 0
            while (mode == EchoMode.LISTENING) {
                delay(100)
                if (recorder.level > 0.08f) { heard = true; quietMs = 0 } else quietMs += 100
                val elapsed = SystemClock.uptimeMillis() - t0
                if ((heard && quietMs >= 900) || elapsed > 7000 || (!heard && elapsed > 4500)) {
                    if (heard) echo() else {
                        // Quiet: not a dead end, Puki tilts his head and asks again
                        recorder.stopOffMain()
                        mode = EchoMode.IDLE
                        lastActive = SystemClock.uptimeMillis()
                        voicePlayer.play("voice_say_again", language)
                        scope.launch { puki.oops() }
                    }
                    break
                }
            }
        }
        return true
    }

    val lean by animateFloatAsState(if (mode == EchoMode.LISTENING) 1f else 0f, spring(dampingRatio = 0.5f), label = "lean")
    val motion = rememberInfiniteTransition(label = "echo")
    val bob by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(if (mode == EchoMode.ECHOING) 220 else 1400), RepeatMode.Reverse), label = "bob")
    val clock = LocalAmbientClock.current

    GameBackground(scene = Scene.TREEHOUSE, withCompanion = false) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Sized from the screen's height, so the microphone stays at his beak
            val stage = minOf(maxHeight * 0.92f, 560.dp)
            val micHeight = maxHeight * 0.62f
            BackButton(onBack, Modifier.align(Alignment.TopStart).padding(16.dp))
            StarCounter(stars = stars, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))

            // A standing microphone for her, in front of his beak in either language, leaning in as she gets louder
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .absolutePadding(right = stage, bottom = 12.dp)
                    .size(micHeight * 0.55f, micHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        rotationZ = -6f * loudness
                        val g = 1f + 0.06f * loudness
                        scaleX = g; scaleY = g
                    }
            ) { drawStandingMic() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(stage)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            if (mode == EchoMode.ECHOING) return@detectTapGestures
                            if (mode == EchoMode.LISTENING) {  // a tap while listening: done talking
                                tryAwaitRelease()
                                if (mode == EchoMode.LISTENING) echo()
                                return@detectTapGestures
                            }
                            // Held for a moment: listen until she lets go; a quick tap: until she's quiet
                            var holding = false
                            val hold = scope.launch {
                                delay(300)
                                holding = listen(untilQuiet = false)
                            }
                            val released = tryAwaitRelease()
                            hold.cancel()
                            hold.join()  // (the microphone may have been opening)
                            when {
                                holding && mode == EchoMode.LISTENING -> if (released) echo() else {
                                    mode = EchoMode.IDLE
                                    scope.launch { recorder.stopOffMain() }
                                }
                                !holding && released -> scope.launch { listen(untilQuiet = true) }
                            }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rings while Puki listens
                if (mode == EchoMode.LISTENING) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (k in 0..1) {
                            val t = (clock.loop(0.9f) + k * 0.5f) % 1f
                            drawCircle(
                                Color(0xFFFFEB3B).copy(alpha = 0.8f * (1f - t)),
                                radius = size.minDimension * (0.32f + (0.14f + 0.14f * loudness) * t),
                                center = Offset(size.width / 2, size.height / 2),
                                style = Stroke(width = (6f + 8f * loudness).dp.toPx())
                            )
                        }
                    }
                }
                TalkingParrotImage(
                    talking = mode == EchoMode.ECHOING,
                    flapMs = 110,
                    modifier = Modifier
                        .size(stage * 0.76f)
                        .reaction(puki)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            val s = 1f + 0.08f * lean
                            scaleX = s; scaleY = s
                            rotationZ = -8f * lean + if (mode == EchoMode.ECHOING) (bob - 0.5f) * 10f else 0f
                            translationY = -bob * (if (mode == EchoMode.ECHOING) 14.dp.toPx() else 5.dp.toPx())
                        }
                )
            }
        }
        ConfettiOverlay(show = party)
    }
}

/** A round microphone on a stand with a heavy foot. */
private fun DrawScope.drawStandingMic() {
    val w = size.width
    val h = size.height
    val steel = Color(0xFF78909C)
    drawLine(steel, Offset(w / 2, h * 0.32f), Offset(w / 2, h * 0.95f), w * 0.07f, StrokeCap.Round)
    drawOval(Color(0xFF546E7A), Offset(w * 0.2f, h * 0.91f), Size(w * 0.6f, h * 0.08f))
    drawRoundRect(Color(0xFF455A64), Offset(w * 0.38f, h * 0.24f), Size(w * 0.24f, h * 0.12f), CornerRadius(w * 0.05f))
    val c = Offset(w / 2, h * 0.15f)
    val r = w * 0.27f
    drawCircle(Color(0xFFB0BEC5), r, c)
    val mesh = Color(0xFF78909C)
    for (k in -2..2) {
        drawLine(mesh, c + Offset(-r * 0.86f, k * r * 0.34f), c + Offset(r * 0.86f, k * r * 0.34f), w * 0.012f)
        drawLine(mesh, c + Offset(k * r * 0.34f, -r * 0.86f), c + Offset(k * r * 0.34f, r * 0.86f), w * 0.012f)
    }
    drawCircle(Color.White.copy(alpha = 0.6f), r * 0.2f, c - Offset(r * 0.35f, r * 0.35f))
}
