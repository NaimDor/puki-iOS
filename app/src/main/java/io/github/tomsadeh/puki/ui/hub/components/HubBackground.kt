package io.github.tomsadeh.puki.ui.hub.components

import kotlin.math.roundToInt
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin

/** Fraction of the screen height where the sky meets the sea. */
const val HORIZON = 0.30f

@Composable
fun HubBackground() {
    val transition = rememberInfiniteTransition(label = "hub_bg")
    val cloudOffset by transition.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(70000, easing = LinearEasing), RepeatMode.Restart),
        label = "cloud_drift"
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_phase"
    )
    val sunSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "sun_spin"
    )
    val boatX by transition.animateFloat(
        initialValue = 1.1f,
        targetValue = -0.1f,
        animationSpec = infiniteRepeatable(tween(90000, easing = LinearEasing), RepeatMode.Restart),
        label = "boat"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Sky, sea and the faraway islands never move: drawn once, in a layer of their own
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            val w = size.width
            val h = size.height
            val horizon = h * HORIZON
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF64C8F5), Color(0xFFB3E5FC)), endY = horizon),
                size = Size(w, horizon + 2f)
            )
            drawFarIsland(w * 0.08f, horizon, w * 0.07f, h * 0.025f)
            drawFarIsland(w * 0.58f, horizon, w * 0.05f, h * 0.018f)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF4FC3F7), Color(0xFF29B6F6), Color(0xFF0397D6)),
                    startY = horizon,
                    endY = h
                ),
                topLeft = Offset(0f, horizon),
                size = Size(w, h - horizon)
            )
            // Bright line where sky meets sea
            drawRect(Color(0x66FFFFFF), Offset(0f, horizon), Size(w, h * 0.006f))
        }

        // What moves: sun, clouds, the boat, waves and sparkles. Their shapes are built once
        // (per size) and only placed each frame, so the tablet doesn't make new ones 60 times a second
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val boat = unitBoat()
                    // Each row's wave drawn once into a little image: ~70 stroked curves a frame cost
                    // the tablet a sixth of its frame time; stamping images is nearly free
                    val stamps = waveStamps(this, WAVE_ROWS)
                    onDrawBehind {
                        val w = size.width
                        val h = size.height
                        val horizon = h * HORIZON

                        drawSun(Offset(w * 0.80f, h * 0.13f), h * 0.065f, sunSpin)
                        drawCloud(w * cloudOffset, h * 0.10f, w * 0.045f)
                        drawCloud(w * ((cloudOffset + 0.55f) % 1.3f - 0.15f), h * 0.19f, w * 0.035f)
                        drawBoat(boat, w * boatX, horizon + h * 0.035f, h * 0.035f, wavePhase)

                        // Little wave arcs drifting sideways, bigger towards the viewer
                        for (row in 0 until WAVE_ROWS) {
                            val t = (row + 0.5f) / WAVE_ROWS
                            val y = horizon + (h - horizon) * t
                            val stamp = stamps[row]
                            val gap = stamp.waveW * 5f
                            val drift = (wavePhase + row * 0.37f) % 1f * gap * (if (row % 2 == 0) 1 else -1)
                            val bob = sin((wavePhase + row * 0.2f) * 2 * PI).toFloat() * h * 0.003f
                            var x = -2 * gap + drift + (row * 0.43f % 1f) * gap
                            while (x < w + gap) {
                                drawImage(stamp.image, Offset(x - stamp.originX, y + bob - stamp.originY))
                                x += gap
                            }
                        }

                        // Twinkling sparkles on the water
                        SPARKLES.forEachIndexed { i, (sx, sy) ->
                            val a = (sin((wavePhase + i * 0.29f) * 2 * PI).toFloat() + 1f) / 2f
                            drawCircle(Color.White.copy(alpha = 0.7f * a), radius = h * 0.005f * (0.5f + a), center = Offset(w * sx, h * sy))
                        }
                    }
                }
        )
    }
}

private val SPARKLES = listOf(0.15f to 0.38f, 0.45f to 0.34f, 0.9f to 0.45f, 0.25f to 0.66f, 0.8f to 0.9f, 0.55f to 0.95f)

private fun DrawScope.drawSun(center: Offset, radius: Float, spin: Float) {
    drawCircle(Color(0x33FFE082), radius * 1.9f, center)
    rotate(spin, center) {
        for (i in 0 until 12) {
            rotate(i * 30f, center) {
                drawLine(
                    Color(0xFFFFCA28),
                    Offset(center.x, center.y - radius * 1.25f),
                    Offset(center.x, center.y - radius * 1.6f),
                    strokeWidth = radius * 0.16f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
    drawCircle(Color(0xFFFFD54F), radius, center)
    drawCircle(Color(0xFFFFE082), radius * 0.65f, center + Offset(-radius * 0.15f, -radius * 0.15f))
}

private fun DrawScope.drawCloud(x: Float, y: Float, r: Float) {
    val c = Color(0xEEFFFFFF)
    drawCircle(c, r, Offset(x, y))
    drawCircle(c, r * 0.8f, Offset(x - r * 0.9f, y + r * 0.25f))
    drawCircle(c, r * 0.75f, Offset(x + r * 0.95f, y + r * 0.25f))
    drawRoundRect(
        c,
        topLeft = Offset(x - r * 1.6f, y + r * 0.2f),
        size = Size(r * 3.2f, r * 0.8f),
        cornerRadius = CornerRadius(r * 0.4f)
    )
}

private fun DrawScope.drawFarIsland(cx: Float, horizon: Float, halfW: Float, height: Float) {
    val p = Path().apply {
        moveTo(cx - halfW, horizon + 1f)
        cubicTo(cx - halfW * 0.6f, horizon - height, cx + halfW * 0.4f, horizon - height * 1.2f, cx + halfW, horizon + 1f)
        close()
    }
    drawPath(p, Color(0xFF7CB9A8))
}

/** The boat's hull, sail and jib, at size 1 around (0, 0). */
private class Boat(val hull: Path, val sail: Path, val jib: Path)

private fun unitBoat() = Boat(
    hull = Path().apply { moveTo(-1.1f, 0f); lineTo(1.1f, 0f); lineTo(0.75f, 0.45f); lineTo(-0.75f, 0.45f); close() },
    sail = Path().apply { moveTo(0.06f, -1.55f); lineTo(0.9f, -0.15f); lineTo(0.06f, -0.15f); close() },
    jib = Path().apply { moveTo(-0.06f, -1.3f); lineTo(-0.7f, -0.15f); lineTo(-0.06f, -0.15f); close() },
)

private fun DrawScope.drawBoat(boat: Boat, x: Float, y: Float, s: Float, phase: Float) {
    val rock = sin(phase * 2 * PI).toFloat() * 4f
    withTransform({
        rotate(rock, Offset(x, y))
        translate(x, y)
        scale(s, s, pivot = Offset.Zero)
    }) {
        drawPath(boat.hull, Color(0xFFE57373))
        drawLine(Color(0xFF6D4C41), Offset(0f, 0f), Offset(0f, -1.6f), strokeWidth = 0.08f)
        drawPath(boat.sail, Color.White)
        drawPath(boat.jib, Color(0xFFFFF59D))
    }
}

private const val WAVE_ROWS = 7

/** A row's wave as an image; its baseline start sits at [originX], [originY] in the image. */
private class WaveStamp(val image: ImageBitmap, val originX: Float, val originY: Float, val waveW: Float)

/** One stamp per row of waves: bigger and brighter towards the viewer, as the rows are drawn. */
private fun waveStamps(scope: CacheDrawScope, rows: Int): List<WaveStamp> {
    // Kept between visits: the hub is rebuilt each time she comes back from a game
    waveCache?.let { (width, stamps) -> if (width == scope.size.width) return stamps }
    val wave = unitWave()
    val stroke = Stroke(width = 0.12f, cap = StrokeCap.Round)
    return List(rows) { row ->
        val t = (row + 0.5f) / rows
        val waveW = scope.size.width * (0.018f + 0.022f * t)
        val pad = waveW * 0.1f
        val ox = pad
        val oy = pad + waveW * 0.15f  // the arcs rise 0.15 above their baseline
        val image = ImageBitmap(maxOf(1, (waveW + 2 * pad).roundToInt()), maxOf(1, (waveW * 0.15f + 2 * pad).roundToInt()))
        CanvasDrawScope().draw(scope, scope.layoutDirection, androidx.compose.ui.graphics.Canvas(image),
            Size(image.width.toFloat(), image.height.toFloat())) {
            withTransform({
                translate(ox, oy)
                scale(waveW, waveW, pivot = Offset.Zero)
            }) { drawPath(wave, Color.White.copy(alpha = 0.18f + 0.22f * t), style = stroke) }
        }
        WaveStamp(image, ox, oy, waveW)
    }.also { waveCache = scope.size.width to it }
}

private var waveCache: Pair<Float, List<WaveStamp>>? = null

/** One little wave arc, 1 wide, starting at (0, 0). */
private fun unitWave() = Path().apply {
    moveTo(0f, 0f)
    quadraticBezierTo(0.25f, -0.3f, 0.5f, 0f)
    quadraticBezierTo(0.75f, -0.3f, 1f, 0f)
}
