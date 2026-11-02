package io.github.tomsadeh.puki.ui.components

import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * The calm end of playtime, when the parent's time limit is up. No countdown (warnings make
 * stopping harder, not easier): the evening comes in over the game, Puki yawns and says it's
 * time for a break, waves and says see you soon, and the music fades away. The game can't be
 * played underneath; a parent closes this with a long press.
 */
@Composable
fun TimeLimitOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val musicPlayer = LocalMusicPlayer.current
    val evening = remember { Animatable(0f) }
    val yawn = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    var mouthOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        voicePlayer.stop()
        musicPlayer.duck(true)
        launch { evening.animateTo(1f, tween(5000, easing = LinearOutSlowInEasing)) }
        delay(700)
        // A big, slow yawn and stretch
        mouthOpen = true
        yawn.animateTo(1f, tween(1100))
        delay(400)
        yawn.animateTo(0f, tween(800))
        mouthOpen = false
        voicePlayer.play("voice_prompt_time_break", language)
        delay(2300)
        // Wave goodbye
        repeat(3) {
            wave.animateTo(1f, tween(320))
            wave.animateTo(-1f, tween(320))
        }
        wave.animateTo(0f, tween(260))
        voicePlayer.play("voice_prompt_see_you_soon", language)
        delay(2500)
        musicPlayer.pause()
        musicPlayer.held = true  // (from here no game loop or return to the app brings it back)
    }
    // The game's scene holds still behind the evening, and comes back to life when a parent closes it
    val clock = LocalAmbientClock.current
    DisposableEffect(Unit) {
        clock.hold()
        onDispose {
            clock.release()
            musicPlayer.held = false
            musicPlayer.duck(false)
            musicPlayer.resume()
        }
    }

    val twinkle by rememberInfiniteTransition(label = "stars").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "twinkle"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } },  // the game underneath can't be played
        contentAlignment = Alignment.Center
    ) {
        // The evening sky comes in over the game, with a moon and stars
        Canvas(modifier = Modifier.fillMaxSize()) {
            val e = evening.value
            drawRect(Color(0xFF1A237E).copy(alpha = 0.82f * e))
            // A crescent: the moon's disc minus a slightly smaller one beside it
            val moon = Offset(size.width * 0.82f, size.height * 0.2f)
            val r = size.height * 0.07f
            val disc = Path().apply { addOval(Rect(moon, r)) }
            val bite = Path().apply { addOval(Rect(moon + Offset(r * 0.5f, -r * 0.28f), r * 0.86f)) }
            drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, Color(0xFFFFF59D).copy(alpha = e))
            STARS.forEachIndexed { i, (fx, fy) ->
                val glow = 0.5f + 0.5f * sin((twinkle + i * 0.37f) * Math.PI.toFloat() * 2)
                drawCircle(Color.White.copy(alpha = e * (0.4f + 0.6f * glow)), size.height * 0.006f, Offset(size.width * fx, size.height * fy))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(if (mouthOpen) R.drawable.ic_companion_parrot_talk else R.drawable.ic_companion_parrot),
                    contentDescription = null,
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = 1f + 0.14f * yawn.value
                            scaleX = 1f - 0.05f * yawn.value
                            rotationZ = wave.value * 6f
                        }
                )
                Image(
                    painter = painterResource(R.drawable.ic_ui_wave),
                    contentDescription = null,
                    modifier = Modifier
                        .size(90.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            rotationZ = wave.value * 28f
                            alpha = evening.value
                        }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = BilingualText("הגיע הזמן להפסקה!", "Time for a break!").resolve(language),
                style = MaterialTheme.typography.headlineLarge,
                color = CloudWhite.copy(alpha = evening.value)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = BilingualText("נתראה בקרוב!", "See you soon!").resolve(language),
                style = MaterialTheme.typography.headlineMedium,
                color = CloudWhite.copy(alpha = evening.value)
            )
        }
        // For the grown-up: a long press closes it (small and out of the way)
        LongPressGate(onUnlocked = onDismiss, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = BilingualText("לחיצה ארוכה לסגירה", "Long press to close").resolve(language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloudWhite.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private val STARS = listOf(
    0.08f to 0.12f, 0.2f to 0.28f, 0.33f to 0.09f, 0.47f to 0.22f, 0.6f to 0.07f,
    0.68f to 0.32f, 0.93f to 0.4f, 0.12f to 0.46f, 0.88f to 0.08f, 0.4f to 0.38f
)
