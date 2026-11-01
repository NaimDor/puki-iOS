package io.github.tomsadeh.puki.ui.myday.dress

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.cos
import kotlin.math.sin

/** What today looks like out of the window. */
enum class Weather { SUN, RAIN, PAJAMA }

/** The room's window, filling its box: today's sky, a wooden frame, curtains and a sill. */
fun DrawScope.drawWindow(weather: Weather) {
    val w = size.width
    val h = size.height
    val wood = Color(0xFFB07A45)
    val inX = w * 0.1f
    val inY = h * 0.05f
    val inW = w * 0.8f
    val inH = h * 0.82f
    // A soft shadow on the wall, then the frame
    drawRoundRect(Color.Black.copy(alpha = 0.08f), Offset(w * 0.06f, h * 0.02f), Size(w * 0.92f, h * 0.92f), CornerRadius(w * 0.05f))
    drawRoundRect(wood, Offset(w * 0.05f, 0f), Size(w * 0.9f, h * 0.92f), CornerRadius(w * 0.05f))
    clipRect(inX, inY, inX + inW, inY + inH) {
        when (weather) {
            Weather.SUN -> drawSunnySky(inX, inY, inW, inH)
            Weather.RAIN -> drawRainySky(inX, inY, inW, inH)
            Weather.PAJAMA -> drawNightSky(inX, inY, inW, inH)
        }
    }
    // The bars across it
    drawLine(wood, Offset(inX + inW / 2, inY), Offset(inX + inW / 2, inY + inH), w * 0.035f)
    drawLine(wood, Offset(inX, inY + inH * 0.48f), Offset(inX + inW, inY + inH * 0.48f), w * 0.035f)
    // Curtains tied at the sides
    for (right in listOf(false, true)) {
        val edge = if (right) inX + inW else inX
        val dir = if (right) -1f else 1f
        val curtain = Path().apply {
            moveTo(edge - dir * w * 0.05f, inY - h * 0.02f)
            lineTo(edge + dir * inW * 0.2f, inY - h * 0.02f)
            quadraticBezierTo(edge + dir * inW * 0.12f, inY + inH * 0.42f, edge + dir * inW * 0.02f, inY + inH * 0.56f)
            quadraticBezierTo(edge + dir * inW * 0.08f, inY + inH * 0.8f, edge + dir * inW * 0.07f, inY + inH)
            lineTo(edge - dir * w * 0.05f, inY + inH)
            close()
        }
        drawPath(curtain, Color(0xFFF8BBD0))
        drawLine(Color(0xFFEC407A), Offset(edge - dir * w * 0.05f, inY + inH * 0.54f), Offset(edge + dir * inW * 0.05f, inY + inH * 0.56f), w * 0.03f, StrokeCap.Round)
    }
    // The sill
    drawRoundRect(Color(0xFFD4A26A), Offset(0f, h * 0.875f), Size(w, h * 0.09f), CornerRadius(h * 0.03f))
    drawRoundRect(wood, Offset(w * 0.02f, h * 0.945f), Size(w * 0.96f, h * 0.03f), CornerRadius(h * 0.015f))
}

private fun DrawScope.drawSunnySky(x: Float, y: Float, w: Float, h: Float) {
    drawRect(Color(0xFF81D4FA), Offset(x, y), Size(w, h))
    // The sun, with its rays
    val sun = Offset(x + w * 0.56f, y + h * 0.27f)
    val r = w * 0.15f
    for (k in 0 until 8) {
        val a = k * Math.PI / 4
        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(Color(0xFFFFCA28), sun + d * (r * 1.3f), sun + d * (r * 1.75f), r * 0.26f, StrokeCap.Round)
    }
    drawCircle(Color(0xFFFFEE58), r, sun)
    drawCircle(Color.White.copy(alpha = 0.45f), r * 0.28f, sun - Offset(r * 0.3f, r * 0.3f))
    // A green hill
    drawOval(Color(0xFF9CCC65), Offset(x - w * 0.2f, y + h * 0.7f), Size(w * 1.4f, h * 0.7f))
    drawOval(Color(0xFF7CB342), Offset(x + w * 0.3f, y + h * 0.8f), Size(w * 1.1f, h * 0.6f))
}

private fun DrawScope.drawRainySky(x: Float, y: Float, w: Float, h: Float) {
    drawRect(Color(0xFFB0BEC5), Offset(x, y), Size(w, h))
    // Rain, falling a little to the side
    for (k in 0 until 16) {
        val fx = x + w * ((k * 0.37f) % 1f)
        val fy = y + h * (0.3f + ((k * 0.53f) % 1f) * 0.58f)
        drawLine(Color(0xFF42A5F5), Offset(fx, fy), Offset(fx - w * 0.025f, fy + h * 0.075f), w * 0.022f, StrokeCap.Round)
    }
    // A big fat cloud
    val cloud = Color(0xFF78909C)
    drawCircle(cloud, w * 0.17f, Offset(x + w * 0.35f, y + h * 0.2f))
    drawCircle(cloud, w * 0.21f, Offset(x + w * 0.54f, y + h * 0.17f))
    drawCircle(cloud, w * 0.14f, Offset(x + w * 0.72f, y + h * 0.24f))
    drawRoundRect(cloud, Offset(x + w * 0.2f, y + h * 0.2f), Size(w * 0.65f, h * 0.15f), CornerRadius(h * 0.07f))
    // A puddle at the bottom
    drawOval(Color(0xFF90A4AE), Offset(x - w * 0.1f, y + h * 0.88f), Size(w * 1.2f, h * 0.24f))
    drawOval(Color(0xFF81D4FA), Offset(x + w * 0.24f, y + h * 0.91f), Size(w * 0.52f, h * 0.07f))
}

private fun DrawScope.drawNightSky(x: Float, y: Float, w: Float, h: Float) {
    drawRect(Color(0xFF3949AB), Offset(x, y), Size(w, h))
    // The moon: a round moon with a bite of sky out of it
    val moon = Offset(x + w * 0.31f, y + h * 0.25f)
    val r = w * 0.17f
    drawCircle(Color(0xFFFFF59D), r, moon)
    drawCircle(Color(0xFF3949AB), r * 0.84f, moon + Offset(r * 0.46f, -r * 0.16f))
    // Stars
    for ((fx, fy) in listOf(0.66f to 0.14f, 0.82f to 0.3f, 0.6f to 0.36f, 0.3f to 0.62f, 0.72f to 0.62f, 0.42f to 0.84f)) {
        val c = Offset(x + w * fx, y + h * fy)
        val s = w * 0.03f
        drawLine(Color.White, c - Offset(s, 0f), c + Offset(s, 0f), w * 0.016f, StrokeCap.Round)
        drawLine(Color.White, c - Offset(0f, s), c + Offset(0f, s), w * 0.016f, StrokeCap.Round)
    }
}
