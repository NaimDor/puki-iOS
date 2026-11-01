package io.github.tomsadeh.puki.ui.components

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.tomsadeh.puki.ui.common.LocalPukiMood
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private enum class Bit { PAPER, DOT, STAR, RIBBON }

/** One piece of confetti. Positions are in screen heights so the burst looks the same on any screen. */
private class Piece(
    val x0: Float,          // start, as a fraction of the width
    val y0: Float,          // start, as a fraction of the height
    val vx: Float,          // heights per second
    val vy: Float,
    val delay: Float,       // seconds before it fires
    val spin: Float,        // degrees per second
    val flip: Float,        // radians per second (paper turning over)
    val phase: Float,
    val size: Float,        // dp
    val kind: Bit,
    val color: Color
)

private val confettiColors = listOf(
    Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
    Color(0xFF95E1D3), Color(0xFFF38181), Color(0xFFAA96DA),
    Color(0xFFFCBF49), Color(0xFF2EC4B6), Color(0xFFFF8FAB)
)

private const val DURATION = 3.4f
private const val GRAVITY = 1.5f    // heights / s²
private const val DRAG = 0.7f       // seconds; air slows the pieces and they flutter down

/**
 * A party: two confetti cannons fire from the bottom corners, then paper, stars and ribbons
 * flutter down over the whole screen.
 */
@Composable
fun ConfettiOverlay(
    show: Boolean,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!show) return

    val pieces = remember { makePieces() }
    val time = remember { Animatable(0f) }
    val mood = LocalPukiMood.current

    LaunchedEffect(show) {
        launch { mood?.celebrate() }  // Puki's happy too
        time.snapTo(0f)
        time.animateTo(DURATION, tween((DURATION * 1000).toInt(), easing = LinearEasing))
        onFinished()
    }

    // Its own layer: the confetti redraws every frame, the scene behind it doesn't have to
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer()) {
        val now = time.value
        val fade = if (now < DURATION - 0.8f) 1f else ((DURATION - now) / 0.8f).coerceIn(0f, 1f)
        pieces.forEach { drawPiece(it, now - it.delay, fade) }
    }
}

private fun makePieces(): List<Piece> {
    val r = Random(System.nanoTime())
    val list = ArrayList<Piece>()
    fun piece(x0: Float, y0: Float, angleDeg: Float, speed: Float, delay: Float) {
        val a = angleDeg * PI.toFloat() / 180f
        val kind = when (r.nextInt(10)) {
            in 0..4 -> Bit.PAPER
            5, 6 -> Bit.DOT
            7, 8 -> Bit.STAR
            else -> Bit.RIBBON
        }
        list += Piece(
            x0, y0, cos(a) * speed, sin(a) * speed, delay,
            spin = r.nextFloat() * 500f - 250f,
            flip = 4f + r.nextFloat() * 8f,
            phase = r.nextFloat() * 6.28f,
            size = if (kind == Bit.STAR) 9f + r.nextFloat() * 7f else 6f + r.nextFloat() * 6f,
            kind = kind,
            color = confettiColors[r.nextInt(confettiColors.size)]
        )
    }
    // Two cannons, aimed up and inwards
    // (about 90 pieces: plenty for a party, light enough for the tablet)
    repeat(34) { piece(-0.02f, 1.02f, -62f + r.nextFloat() * 26f - 13f, 1.7f + r.nextFloat() * 1.1f, r.nextFloat() * 0.25f) }
    repeat(34) { piece(1.02f, 1.02f, -118f + r.nextFloat() * 26f - 13f, 1.7f + r.nextFloat() * 1.1f, r.nextFloat() * 0.25f) }
    // And a gentle shower from above
    repeat(24) { piece(r.nextFloat(), -0.05f, 90f, 0.1f + r.nextFloat() * 0.2f, 0.3f + r.nextFloat() * 0.9f) }
    return list
}

private fun DrawScope.drawPiece(p: Piece, t: Float, fade: Float) {
    if (t <= 0f) return
    val h = size.height
    // Motion with air drag: v(t) = gτ + (v0 - gτ)·e^(-t/τ)
    val decay = 1f - exp(-t / DRAG)
    val terminal = GRAVITY * DRAG
    val flutter = sin(t * 3f + p.phase) * 0.025f * kotlin.math.min(t, 1f)
    val x = p.x0 * size.width + (p.vx * DRAG * decay + flutter) * h
    val y = p.y0 * h + (terminal * t + (p.vy - terminal) * DRAG * decay) * h
    if (y > h + 40f || x < -40f || x > size.width + 40f) return
    val c = Offset(x, y)
    val s = p.size * density
    val color = p.color.copy(alpha = fade)
    val turn = cos(t * p.flip + p.phase)
    rotate(p.phase * 57f + p.spin * t, pivot = c) {
        when (p.kind) {
            Bit.PAPER -> scale(scaleX = turn, scaleY = 1f, pivot = c) {
                drawRect(color, Offset(x - s / 2, y - s * 0.35f), Size(s, s * 0.7f))
            }
            Bit.DOT -> drawCircle(color, s * 0.45f, c)
            Bit.STAR -> scale(scaleX = 0.6f + 0.4f * kotlin.math.abs(turn), scaleY = 1f, pivot = c) {
                translate(x, y) { scale(s * 0.6f, s * 0.6f, pivot = Offset.Zero) { drawPath(UNIT_STAR, color) } }
            }
            Bit.RIBBON -> scale(scaleX = 1f, scaleY = turn, pivot = c) {
                drawRoundRect(color, Offset(x - s * 0.18f, y - s * 1.2f), Size(s * 0.36f, s * 2.4f), CornerRadius(s * 0.18f))
            }
        }
    }
}

/** A star of radius 1 around (0, 0), shared by every piece. */
private val UNIT_STAR = Path().apply {
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) 1f else 0.45f
        val a = (k * 36f - 90f) * PI.toFloat() / 180f
        val px = cos(a) * rad
        val py = sin(a) * rad
        if (k == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}
