package io.github.tomsadeh.puki.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lets a game make a character or item react: a happy jump for a right answer,
 * munching when an animal eats, and a gentle side-to-side "nope" for a wrong one.
 * Attach with [Modifier.reaction] and trigger from a coroutine scope.
 */
class ReactionState {
    internal val lift = Animatable(0f)     // 0 = resting, 1 = top of the jump
    internal val squash = Animatable(1f)   // vertical scale, width compensates
    internal val sway = Animatable(0f)     // -1..1 sideways shake
    internal val tilt = Animatable(0f)     // degrees
    internal val burst = Animatable(1f)    // 0..1 through a burst of stars; 1 = nothing showing
    internal var burstSeed = 0

    suspend fun celebrate() = coroutineScope {
        burstSeed++
        launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(1000, easing = LinearEasing))
        }
        repeat(2) {
            squash.animateTo(0.82f, tween(90))
            launch { squash.animateTo(1.12f, tween(160)) }
            launch { tilt.animateTo(if (it == 0) -8f else 8f, tween(200)) }
            lift.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
            launch { squash.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
            lift.animateTo(0f, tween(220))
        }
        squash.animateTo(0.9f, tween(70))
        launch { tilt.animateTo(0f, spring(dampingRatio = 0.4f)) }
        squash.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 400f))
    }

    /** One small jump: he noticed. */
    suspend fun hop() {
        lift.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
        lift.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f))
    }

    /** A puzzled head tilt. */
    suspend fun oops() {
        tilt.animateTo(-14f, tween(180))
        tilt.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 200f))
    }

    suspend fun munch() {
        repeat(3) {
            squash.animateTo(0.9f, tween(110))
            squash.animateTo(1.05f, tween(110))
        }
        squash.animateTo(1f, spring(dampingRatio = 0.4f))
    }

    suspend fun shake() {
        sway.animateTo(
            0f,
            keyframes {
                durationMillis = 480
                -1f at 60
                1f at 160
                -0.8f at 260
                0.6f at 350
                -0.3f at 420
            }
        )
    }
}

@Composable
fun rememberReactionState(vararg keys: Any?): ReactionState = remember(*keys) { ReactionState() }

/**
 * Applies [state]'s jumps, squashes and shakes. Squashing pivots on the bottom edge, like standing on the ground.
 * A celebration also bursts stars and sparkles out of the item (drawn outside the squash, so they fly straight).
 */
fun Modifier.reaction(state: ReactionState, jumpHeight: Dp = 48.dp, shakeDistance: Dp = 16.dp): Modifier =
    drawWithContent {
        drawContent()
        val p = state.burst.value
        if (p < 1f) drawStarBurst(p, state.burstSeed)
    }.graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        translationY = -state.lift.value * jumpHeight.toPx()
        translationX = state.sway.value * shakeDistance.toPx()
        scaleY = state.squash.value
        scaleX = 2f - state.squash.value
        rotationZ = state.tilt.value + state.sway.value * 4f
    }

private val burstColors = listOf(
    Color(0xFFFFD54F), Color(0xFFFF80AB), Color(0xFF80DEEA), Color(0xFFFFFFFF), Color(0xFFC5E1A5), Color(0xFFFFAB40)
)

/** Stars and twinkles flying out from the centre, with a soft ring, at [p] (0..1) of the way through. */
private fun DrawScope.drawStarBurst(p: Float, seed: Int) {
    val c = center
    val reach = size.minDimension * 0.55f + 40.dp.toPx()
    val out = 1f - (1f - p) * (1f - p) * (1f - p)          // fast out, gentle stop
    val fade = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f

    // A ring of light expanding from the item
    if (p < 0.5f) {
        val k = p / 0.5f
        drawCircle(
            Color(0xFFFFF59D).copy(alpha = 0.7f * (1f - k)),
            radius = size.minDimension * 0.35f + reach * 0.6f * k,
            center = c,
            style = Stroke(width = 10.dp.toPx() * (1f - k) + 1f)
        )
    }

    val count = 14
    val base = (size.minDimension * 0.11f).coerceIn(10.dp.toPx(), 26.dp.toPx())
    for (i in 0 until count) {
        val r = Random(seed * 31 + i)
        val angle = (i + r.nextFloat() * 0.6f) / count * 2f * PI.toFloat()
        val dist = reach * (0.55f + r.nextFloat() * 0.55f) * out
        val drop = p * p * reach * 0.25f                      // a little gravity
        val pos = Offset(c.x + cos(angle) * dist, c.y + sin(angle) * dist + drop)
        val grow = if (p < 0.12f) p / 0.12f else 1f
        val s = base * (0.7f + r.nextFloat() * 0.6f) * grow * (1f - p * 0.45f)
        val color = burstColors[(i + seed) % burstColors.size].copy(alpha = fade)
        if (i % 3 == 2) {
            drawTwinkle(pos, s * 0.9f, color, p * 360f)
        } else {
            rotate(p * 220f * (if (i % 2 == 0) 1f else -1f) + r.nextFloat() * 72f, pivot = pos) {
                drawPath(starPath(pos, s), color)
            }
        }
    }
}

private fun starPath(c: Offset, r: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) r else r * 0.45f
        val a = (k * 36f - 90f) * PI.toFloat() / 180f
        val x = c.x + cos(a) * rad
        val y = c.y + sin(a) * rad
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A four-pointed sparkle. */
internal fun DrawScope.drawTwinkle(c: Offset, r: Float, color: Color, spin: Float) {
    rotate(spin * 0.3f, pivot = c) {
        val path = Path().apply {
            moveTo(c.x, c.y - r)
            quadraticBezierTo(c.x, c.y, c.x + r, c.y)
            quadraticBezierTo(c.x, c.y, c.x, c.y + r)
            quadraticBezierTo(c.x, c.y, c.x - r, c.y)
            quadraticBezierTo(c.x, c.y, c.x, c.y - r)
            close()
        }
        drawPath(path, color)
    }
}

/** A slow, gentle breathing loop so characters look alive while waiting. */
fun Modifier.idleBreathing(periodMillis: Int = 2400): Modifier = composed {
    val breath by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        scaleY = 1f + 0.035f * breath
        scaleX = 1f - 0.015f * breath
    }
}
