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
import io.github.tomsadeh.puki.ui.components.TalkingParrotImage
import io.github.tomsadeh.puki.audio.hasMicPermission
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
import io.github.tomsadeh.puki.ui.components.BackButton
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.Scene
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class EchoMode { IDLE, LISTENING, ECHOING }

/**
 * Echo parrot: she says anything and Puki says it back in a silly parrot voice. Holding Puki
 * records until she lets go; a tap records until she stops talking. Works with no PC at all
 * (the recording only goes to the speaker, a moment later, and is never kept).
 */
@Composable
fun EchoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val voicePlayer = LocalVoicePlayer.current
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val recorder = remember { MicRecorder() }
    var mode by remember { mutableStateOf(EchoMode.IDLE) }
    var lastActive by remember { mutableStateOf(SystemClock.uptimeMillis()) }
    DisposableEffect(Unit) { onDispose { if (recorder.isRecording) recorder.stop() } }

    // The microphone is asked for once, the first time she talks to Puki
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun hasMic() = hasMicPermission(context)

    // "Say something to Puki!", and again (twice at most) if she goes quiet for a while
    LaunchedEffect(Unit) {
        delay(600)
        voicePlayer.play("voice_prompt_echo", language)
        repeat(2) {
            while (SystemClock.uptimeMillis() - lastActive < 15_000 || mode != EchoMode.IDLE) delay(1000)
            voicePlayer.play("voice_prompt_echo", language)
            lastActive = SystemClock.uptimeMillis()
        }
    }

    fun echo() {
        val wav = recorder.stop()
        mode = EchoMode.ECHOING
        scope.launch {
            EchoVoice.play(wav, voicePlayer.level)
            mode = EchoMode.IDLE
            lastActive = SystemClock.uptimeMillis()
        }
    }

    /** Starts listening; with [untilQuiet] it stops by itself once she pauses (a tap), else on release. */
    fun listen(untilQuiet: Boolean): Boolean {
        if (!hasMic()) {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        voicePlayer.stop()  // so the microphone doesn't hear the app
        if (!recorder.start()) return false
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
                    if (heard) echo() else { recorder.stop(); mode = EchoMode.IDLE }
                    break
                }
            }
        }
        return true
    }

    val lean by animateFloatAsState(if (mode == EchoMode.LISTENING) 1f else 0f, spring(dampingRatio = 0.5f), label = "lean")
    val motion = rememberInfiniteTransition(label = "echo")
    val bob by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(if (mode == EchoMode.ECHOING) 220 else 1400), RepeatMode.Reverse), label = "bob")
    val pulse by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(900)), label = "pulse")

    GameBackground(scene = Scene.STAGE, withCompanion = false, colors = listOf(Color(0xFF7B1FA2), Color(0xFF9C27B0))) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackButton(onBack, Modifier.align(Alignment.TopStart).padding(16.dp))

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(340.dp)
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
                            when {
                                holding && mode == EchoMode.LISTENING -> if (released) echo() else { recorder.stop(); mode = EchoMode.IDLE }
                                !holding && released -> listen(untilQuiet = true)
                            }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rings while Puki listens
                if (mode == EchoMode.LISTENING) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (k in 0..1) {
                            val t = (pulse + k * 0.5f) % 1f
                            drawCircle(
                                Color(0xFFFFEB3B).copy(alpha = 0.8f * (1f - t)),
                                radius = size.minDimension * (0.32f + 0.18f * t),
                                center = Offset(size.width / 2, size.height / 2),
                                style = Stroke(width = 8.dp.toPx())
                            )
                        }
                    }
                }
                TalkingParrotImage(
                    talking = mode == EchoMode.ECHOING,
                    flapMs = 110,
                    modifier = Modifier
                        .size(260.dp)
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
    }
}
