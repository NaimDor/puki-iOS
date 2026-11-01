package io.github.tomsadeh.puki.ui.myday.garage

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

/*
 * What comes in for a wash. Each fills a box twice as wide as tall and looks left, the way it
 * drives off. [spots] are where its mud goes, as fractions of that box, so they land on it.
 */
enum class Vehicle(val spots: List<Offset>) {
    CAR(listOf(
        Offset(0.2f, 0.55f), Offset(0.36f, 0.38f), Offset(0.52f, 0.6f),
        Offset(0.66f, 0.42f), Offset(0.82f, 0.58f), Offset(0.42f, 0.7f)
    )),
    TRUCK(listOf(
        Offset(0.2f, 0.56f), Offset(0.46f, 0.3f), Offset(0.52f, 0.58f),
        Offset(0.7f, 0.34f), Offset(0.84f, 0.58f), Offset(0.28f, 0.64f)
    )),
    BIKE(listOf(
        Offset(0.125f, 0.62f), Offset(0.42f, 0.33f), Offset(0.52f, 0.73f),
        Offset(0.59f, 0.42f), Offset(0.875f, 0.62f), Offset(0.2f, 0.17f)
    )),
}

/** A little truck: the cab with its smile at the front, a big box behind. */
fun DrawScope.drawTruck(color: Color) {
    val w = size.width
    val h = size.height
    val dark = lerp(color, Color.Black, 0.25f)
    val back = lerp(color, Color.White, 0.55f)
    // The box, with planks
    drawRoundRect(back, Offset(w * 0.36f, h * 0.08f), Size(w * 0.59f, h * 0.64f), CornerRadius(h * 0.05f))
    for (k in 1..4) {
        val x = w * (0.36f + k * 0.118f)
        drawLine(lerp(back, Color.Black, 0.12f), Offset(x, h * 0.1f), Offset(x, h * 0.68f), w * 0.005f)
    }
    drawRoundRect(lerp(back, Color.Black, 0.12f), Offset(w * 0.36f, h * 0.08f), Size(w * 0.59f, h * 0.05f), CornerRadius(h * 0.025f))
    // The cab
    val cab = Path().apply {
        moveTo(w * 0.05f, h * 0.78f)
        lineTo(w * 0.05f, h * 0.55f)
        quadraticBezierTo(w * 0.055f, h * 0.45f, w * 0.13f, h * 0.43f)
        lineTo(w * 0.17f, h * 0.2f)
        quadraticBezierTo(w * 0.19f, h * 0.14f, w * 0.24f, h * 0.14f)
        lineTo(w * 0.34f, h * 0.14f)
        quadraticBezierTo(w * 0.36f, h * 0.14f, w * 0.36f, h * 0.2f)
        lineTo(w * 0.36f, h * 0.78f)
        close()
    }
    drawPath(cab, color)
    drawRoundRect(dark, Offset(w * 0.05f, h * 0.7f), Size(w * 0.9f, h * 0.08f), CornerRadius(h * 0.04f))
    // The window
    val window = Path().apply { moveTo(w * 0.17f, h * 0.43f); lineTo(w * 0.205f, h * 0.22f); lineTo(w * 0.32f, h * 0.22f); lineTo(w * 0.32f, h * 0.43f); close() }
    drawPath(window, Color(0xFFB3E5FC))
    drawLine(Color.White.copy(alpha = 0.6f), Offset(w * 0.23f, h * 0.26f), Offset(w * 0.21f, h * 0.36f), w * 0.01f, StrokeCap.Round)
    // A smiling face at the front: headlight eye and a grille smile
    drawCircle(Color(0xFFFFF59D), h * 0.06f, Offset(w * 0.11f, h * 0.52f))
    drawCircle(Color(0xFF3E2723), h * 0.025f, Offset(w * 0.105f, h * 0.52f))
    drawArc(Color(0xFF3E2723), 20f, 140f, false, Offset(w * 0.05f, h * 0.55f), Size(w * 0.1f, h * 0.1f), style = Stroke(h * 0.02f, cap = StrokeCap.Round))
    drawCircle(Color(0xFFFF8A65), h * 0.03f, Offset(w * 0.93f, h * 0.62f))  // tail light
    drawLine(dark, Offset(w * 0.285f, h * 0.48f), Offset(w * 0.325f, h * 0.48f), w * 0.01f, StrokeCap.Round)  // handle
    // Wheels
    for (cx in listOf(0.22f, 0.76f)) {
        drawCircle(Color(0xFF37474F), h * 0.16f, Offset(w * cx, h * 0.8f))
        drawCircle(Color(0xFFB0BEC5), h * 0.07f, Offset(w * cx, h * 0.8f))
    }
}

/** A chunky little bike: a lamp with a face at the front, a basket on the handlebars. */
fun DrawScope.drawBike(color: Color) {
    val w = size.width
    val h = size.height
    val dark = lerp(color, Color.Black, 0.25f)
    val hub = h * 0.68f
    val r = h * 0.3f
    val front = Offset(w * 0.24f, hub)
    val rear = Offset(w * 0.76f, hub)
    val crank = Offset(w * 0.52f, h * 0.72f)
    val neck = Offset(w * 0.31f, h * 0.34f)
    val seat = Offset(w * 0.6f, h * 0.28f)
    val tube = w * 0.03f
    // Wheels: fat tyres, spokes and a hub
    for (c in listOf(front, rear)) {
        drawCircle(Color(0xFFECEFF1), r, c)
        for (k in 0 until 4) {
            val a = k * Math.PI / 4
            val d = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat()) * r
            drawLine(Color(0xFFB0BEC5), c - d, c + d, w * 0.004f)
        }
        drawCircle(Color(0xFF37474F), r, c, style = Stroke(h * 0.065f))
        drawCircle(Color(0xFFB0BEC5), h * 0.05f, c)
    }
    // Mudguards over the wheels
    for (c in listOf(front, rear)) {
        drawArc(color, 200f, 140f, false, c - Offset(r * 1.18f, r * 1.18f), Size(r * 2.36f, r * 2.36f), style = Stroke(h * 0.04f, cap = StrokeCap.Round))
    }
    // The frame
    drawLine(color, rear, crank, tube, StrokeCap.Round)
    drawLine(color, rear, seat, tube, StrokeCap.Round)
    drawLine(color, seat, crank, tube, StrokeCap.Round)
    drawLine(color, seat + Offset(0f, h * 0.06f), neck, tube, StrokeCap.Round)
    drawLine(color, neck + Offset(0f, h * 0.05f), crank, tube, StrokeCap.Round)
    drawLine(dark, front, neck - Offset(0f, h * 0.1f), tube, StrokeCap.Round)
    // The chain guard and a pedal
    drawRoundRect(dark, Offset(crank.x - w * 0.05f, crank.y - h * 0.09f), Size(w * 0.2f, h * 0.17f), CornerRadius(h * 0.085f))
    drawCircle(lerp(color, Color.White, 0.3f), h * 0.075f, crank)
    drawLine(Color(0xFF78909C), crank, crank + Offset(0f, h * 0.13f), w * 0.01f, StrokeCap.Round)
    drawRoundRect(Color(0xFF37474F), Offset(crank.x - w * 0.045f, crank.y + h * 0.12f), Size(w * 0.09f, h * 0.035f), CornerRadius(h * 0.017f))
    // The saddle
    drawRoundRect(Color(0xFF5D4037), Offset(seat.x - w * 0.075f, seat.y - h * 0.05f), Size(w * 0.15f, h * 0.075f), CornerRadius(h * 0.04f))
    // The handlebar, with a soft grip
    val bar = neck - Offset(0f, h * 0.1f)
    drawLine(Color(0xFF90A4AE), bar, bar + Offset(w * 0.09f, -h * 0.07f), w * 0.016f, StrokeCap.Round)
    drawLine(Color(0xFF37474F), bar + Offset(w * 0.06f, -h * 0.047f), bar + Offset(w * 0.1f, -h * 0.078f), w * 0.024f, StrokeCap.Round)
    // A woven basket
    drawRoundRect(Color(0xFFD7A86E), Offset(w * 0.1f, h * 0.05f), Size(w * 0.2f, h * 0.24f), CornerRadius(h * 0.04f))
    for (k in 1..3) {
        drawLine(Color(0xFFB0834F), Offset(w * (0.1f + k * 0.05f), h * 0.07f), Offset(w * (0.1f + k * 0.05f), h * 0.27f), w * 0.004f)
    }
    drawRoundRect(Color(0xFFA1887F), Offset(w * 0.09f, h * 0.04f), Size(w * 0.22f, h * 0.04f), CornerRadius(h * 0.02f))
    // A round lamp with a smiling face
    val lamp = Offset(w * 0.25f, h * 0.38f)
    val lr = h * 0.095f
    drawCircle(Color(0xFF90A4AE), lr * 1.15f, lamp)
    drawCircle(Color(0xFFFFF59D), lr, lamp)
    for (dx in listOf(-0.36f, 0.36f)) drawCircle(Color(0xFF3E2723), lr * 0.14f, lamp + Offset(dx * lr, -lr * 0.15f))
    drawArc(Color(0xFF3E2723), 20f, 140f, false, lamp - Offset(lr * 0.45f, lr * 0.4f), Size(lr * 0.9f, lr * 0.9f), style = Stroke(h * 0.016f, cap = StrokeCap.Round))
}
