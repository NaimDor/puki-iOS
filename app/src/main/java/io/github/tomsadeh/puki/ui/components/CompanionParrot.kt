package io.github.tomsadeh.puki.ui.components

import io.github.tomsadeh.puki.ui.common.LocalLanguage

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import io.github.tomsadeh.puki.audio.hasMicPermission
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomsadeh.puki.audio.CompanionStatus
import io.github.tomsadeh.puki.audio.MicRecorder
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalPukiMood
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val FULL_PARROT = 140.dp
private val COMPACT_PARROT = 96.dp

/**
 * פוקי sitting in the corner of a game. With his brain ready, he flaps his beak while he talks:
 * a tap makes him say something, and holding him records the child talking (Whisper listens)
 * and he answers when let go. Without a brain he's still there, with his recorded lines.
 */
@Composable
fun CompanionParrot(modifier: Modifier = Modifier) {
    val mood = LocalPukiMood.current
    val moved = if (mood != null) modifier.reaction(mood, jumpHeight = 24.dp) else modifier
    val parrotSize = if (rememberCompact()) COMPACT_PARROT else FULL_PARROT
    val companion = LocalCompanion.current
    if (companion == null) {
        OfflineParrot(moved)
        return
    }
    val voicePlayer = LocalVoicePlayer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by companion.status.collectAsStateWithLifecycle()
    val brainSpeaking by companion.speaking.collectAsStateWithLifecycle()
    val recordedLine by voicePlayer.speaking.collectAsStateWithLifecycle()
    val speaking = brainSpeaking || recordedLine  // (his beak moves for his recorded lines too)
    val thinking by companion.thinking.collectAsStateWithLifecycle()
    val autoListening by companion.listening.collectAsStateWithLifecycle()

    val recorder = remember { MicRecorder() }
    var listening by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.stopInBackground() } }

    // Asked once; a parent approves the microphone the first time someone holds the parrot
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun hasMic() = hasMicPermission(context)

    LaunchedEffect(Unit) { companion.wake() }
    // Until his brain is ready (or if it never is), the recorded Puki keeps the corner warm
    if (status != CompanionStatus.READY) {
        OfflineParrot(moved)
        return
    }


    val listenScale by animateFloatAsState(if (listening || autoListening) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "listen_scale")

    AnimatedVisibility(
        visible = status == CompanionStatus.READY,
        modifier = moved,
        enter = slideInVertically(spring(dampingRatio = 0.5f)) { it },
        exit = slideOutVertically { it } + fadeOut()
    ) {
        // (inside, so nothing ticks while the parrot is away)
        val bob by rememberInfiniteTransition(label = "parrot").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (speaking) 260 else 1400), RepeatMode.Reverse),
            label = "bob"
        )
        Box(contentAlignment = Alignment.Center) {
            // Pulsing rings while it listens
            if (listening || autoListening) {
                val pulse by rememberInfiniteTransition(label = "listen").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
                    label = "listen_pulse"
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    for (k in 0..1) {
                        val t = (pulse + k * 0.5f) % 1f
                        drawCircle(
                            color = Color(0xFFFFEB3B).copy(alpha = 0.8f * (1f - t)),
                            radius = size.minDimension * (0.35f + 0.25f * t),
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(width = 6.dp.toPx())
                        )
                    }
                }
            }
            // Dots while a line is on its way from the brain
            if (thinking && !speaking) {
                val phase by rememberInfiniteTransition(label = "think").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "think_phase"
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    val r = size.width * 0.035f
                    for (k in 0..2) {
                        val bounce = abs(sin((phase + k / 3f) * PI)).toFloat()
                        drawCircle(Color.White, r, Offset(size.width / 2 + (k - 1) * r * 3f, r * 2f - bounce * r * 1.5f))
                    }
                }
            }
            TalkingParrotImage(
                talking = speaking,
                modifier = Modifier
                    .size(parrotSize)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleX = listenScale
                        scaleY = listenScale
                        translationY = -bob * (if (speaking) 10.dp.toPx() else 4.dp.toPx())
                        rotationZ = if (speaking) (bob - 0.5f) * 8f else 0f
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            // Holding for a moment starts listening; a quick tap just chats
                            val hold = scope.launch {
                                delay(300)
                                if (!hasMic()) {
                                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    voicePlayer.stop()  // so the mic doesn't hear the app
                                    listening = recorder.startOffMain()
                                }
                            }
                            val released = tryAwaitRelease()
                            val quick = !hold.isCompleted
                            hold.cancel()
                            hold.join()  // (the microphone may have been opening)
                            if (listening) {
                                listening = false
                                scope.launch {
                                    val wav = recorder.stopOffMain()
                                    if (released) companion.listen(wav)
                                }
                            } else if (released && quick) {
                                scope.launch { mood?.hop() }  // he noticed, right away
                                companion.chat()
                            }
                        })
                    }
            )
        }
    }
}

/** What Puki says when tapped without his brain: a few recorded lines, never the same twice in a row. */
private val offlineLines = listOf("voice_puki_hi", "voice_puki_tickle", "voice_puki_fun", "voice_puki_here", "voice_puki_dance")

/** Puki with recorded lines only: he bobs in the corner, and a tap makes him talk and dance. */
@Composable
fun OfflineParrot(modifier: Modifier = Modifier, size: Dp = if (rememberCompact()) COMPACT_PARROT else FULL_PARROT) {
    val voicePlayer = LocalVoicePlayer.current
    val language = LocalLanguage.current
    val scope = rememberCoroutineScope()
    var tapped by remember { mutableStateOf(false) }
    val recordedLine by voicePlayer.speaking.collectAsStateWithLifecycle()
    val talking = tapped || recordedLine
    var last by remember { mutableStateOf(-1) }
    val bob by rememberInfiniteTransition(label = "offline_parrot").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (talking) 260 else 1400), RepeatMode.Reverse),
        label = "offline_bob"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TalkingParrotImage(
            talking = talking,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = -bob * (if (talking) 14.dp.toPx() else 4.dp.toPx())
                    rotationZ = if (talking) (bob - 0.5f) * 14f else 0f  // a little dance while he talks
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (tapped) return@detectTapGestures
                        val next = (offlineLines.indices - last).random()
                        last = next
                        tapped = true
                        scope.launch { delay(4000); tapped = false }  // in case the line never ends
                        voicePlayer.play(offlineLines[next], language) { tapped = false }
                    })
                }
        )
    }
}
