package io.github.tomsadeh.puki.ui.myday.bedtime

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

/*
 * The bedroom, in fractions of the screen so it fits a tablet and a phone on its side: the window
 * on the left, the bed in the middle with its pillow on the right, and the lamp beside the pillow.
 */
val WindowArea = Rect(0.04f, 0.2f, 0.215f, 0.5f)
val BedArea = Rect(0.4f, 0.5f, 0.82f, 0.88f)
val LampArea = Rect(0.83f, 0.3f, 0.97f, 0.76f)
val PillowCenter = Offset(0.74f, 0.56f)
const val FLOOR_Y = 0.76f
private val Blanket = Color(0xFF7E57C2)
private val BlanketLight = Color(0xFF9575CD)
private val Wood = Color(0xFFB98352)
private val WoodDark = Color(0xFF8D6240)

/** The room, drawn once: wall, floor, rug, bed, shelf and the lamp's table. */
fun DrawScope.drawBedroom() {
    val w = size.width
    val h = size.height
    val floor = h * FLOOR_Y
    // Wall with soft stripes, then the floorboards
    drawRect(Color(0xFFD9D2F0), size = Size(w, floor))
    var x = 0f
    while (x < w) {
        drawRect(Color(0xFFE4DEF6), Offset(x, 0f), Size(w * 0.04f, floor))
        x += w * 0.08f
    }
    drawRect(Color(0xFFC89B6C), Offset(0f, floor), Size(w, h - floor))
    for (k in 1..3) drawLine(Color(0xFFB98352), Offset(0f, floor + (h - floor) * k / 4f), Offset(w, floor + (h - floor) * k / 4f), h * 0.004f)
    drawRect(Color.White, Offset(0f, floor - h * 0.012f), Size(w, h * 0.012f))
    // A round rug
    drawOval(Color(0xFFF48FB1), Offset(w * 0.1f, h * 0.84f), Size(w * 0.36f, h * 0.12f))
    drawOval(Color(0xFFF8BBD0), Offset(w * 0.13f, h * 0.855f), Size(w * 0.3f, h * 0.09f))
    drawShelf(w, h)
    drawBed(w, h)
    // The lamp's little table
    drawRoundRect(Wood, Offset(w * 0.845f, h * 0.56f), Size(w * 0.11f, h * 0.025f), CornerRadius(h * 0.01f))
    drawRect(WoodDark, Offset(w * 0.86f, h * 0.585f), Size(w * 0.012f, h * 0.17f))
    drawRect(WoodDark, Offset(w * 0.928f, h * 0.585f), Size(w * 0.012f, h * 0.17f))
}

private fun DrawScope.drawShelf(w: Float, h: Float) {
    val y = h * 0.3f
    drawRoundRect(Wood, Offset(w * 0.46f, y), Size(w * 0.3f, h * 0.018f), CornerRadius(h * 0.008f))
    // Books and two blocks on it
    val books = listOf(Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFFFFCA28), Color(0xFF66BB6A))
    books.forEachIndexed { i, c ->
        val bookH = h * (0.09f + (i % 2) * 0.02f)
        drawRoundRect(c, Offset(w * (0.48f + i * 0.022f), y - bookH), Size(w * 0.018f, bookH), CornerRadius(w * 0.003f))
    }
    drawRoundRect(Color(0xFFAB47BC), Offset(w * 0.64f, y - h * 0.06f), Size(h * 0.06f, h * 0.06f), CornerRadius(h * 0.008f))
    drawRoundRect(Color(0xFF26A69A), Offset(w * 0.685f, y - h * 0.045f), Size(h * 0.045f, h * 0.045f), CornerRadius(h * 0.008f))
}

private fun DrawScope.drawBed(w: Float, h: Float) {
    // Legs, frame, and the headboard behind the pillow
    for (fx in listOf(0.42f, 0.79f)) drawRect(WoodDark, Offset(w * fx, h * 0.8f), Size(w * 0.016f, h * 0.075f))
    drawRoundRect(Wood, Offset(w * 0.8f, h * 0.42f), Size(w * 0.024f, h * 0.45f), CornerRadius(w * 0.012f))
    drawRoundRect(Wood, Offset(w * 0.405f, h * 0.56f), Size(w * 0.02f, h * 0.31f), CornerRadius(w * 0.01f))
    drawRoundRect(Wood, Offset(w * 0.41f, h * 0.72f), Size(w * 0.4f, h * 0.09f), CornerRadius(h * 0.012f))
    // The mattress, the pillow and the blanket
    drawRoundRect(Color(0xFFFFF8E1), Offset(w * 0.42f, h * 0.61f), Size(w * 0.38f, h * 0.12f), CornerRadius(h * 0.03f))
    drawRoundRect(Color.White, Offset(w * 0.68f, h * 0.5f), Size(w * 0.115f, h * 0.13f), CornerRadius(h * 0.05f))
    drawArc(Color(0xFFE0E0E0), 20f, 140f, false, Offset(w * 0.69f, h * 0.53f), Size(w * 0.095f, h * 0.08f), style = Stroke(h * 0.005f))
    drawBlanket(w, h, 0.585f)
}

/** The blanket, its top at [top] (fraction of the height): raised over a child in bed. */
private fun DrawScope.drawBlanket(w: Float, h: Float, top: Float) {
    drawRoundRect(Blanket, Offset(w * 0.42f, h * top), Size(w * 0.265f, h * (0.76f - top)), CornerRadius(h * 0.035f))
    drawRoundRect(BlanketLight, Offset(w * 0.655f, h * top), Size(w * 0.03f, h * (0.76f - top)), CornerRadius(h * 0.012f))
    var k = 0
    while (k < 4) {
        drawCircle(Color(0xFFFFEE58), h * 0.012f, Offset(w * (0.46f + k * 0.055f), h * (top + 0.05f + (k % 2) * 0.05f)))
        k++
    }
}

/** The blanket pulled up over her, drawn over the child's head on the pillow. */
fun DrawScope.drawTuckedBlanket() {
    val w = size.width
    val h = size.height
    drawBlanket(w, h, 0.555f)
    drawOval(BlanketLight.copy(alpha = 0.5f), Offset(w * 0.47f, h * 0.555f), Size(w * 0.2f, h * 0.05f))
}

/** The lamp on its table, lit by [on] (0..1). */
fun DrawScope.drawLamp(on: Float) {
    val w = size.width
    val h = size.height
    val c = Offset(w * 0.9f, h * 0.42f)
    if (on > 0f) drawCircle(
        Brush.radialGradient(listOf(Color(0xAAFFE082).copy(alpha = 0.67f * on), Color.Transparent), center = c, radius = h * 0.42f),
        h * 0.42f, c
    )
    drawRoundRect(WoodDark, Offset(w * 0.893f, h * 0.45f), Size(w * 0.014f, h * 0.11f), CornerRadius(w * 0.005f))
    drawOval(Wood, Offset(w * 0.875f, h * 0.545f), Size(w * 0.05f, h * 0.02f))
    val shade = Path().apply {
        moveTo(w * 0.875f, h * 0.345f)
        lineTo(w * 0.925f, h * 0.345f)
        lineTo(w * 0.948f, h * 0.46f)
        lineTo(w * 0.852f, h * 0.46f)
        close()
    }
    drawPath(shade, lerpColor(Color(0xFF9E8FB8), Color(0xFFFFE082), on))
    drawLine(Color(0xFF6D4C41), Offset(w * 0.934f, h * 0.46f), Offset(w * 0.934f, h * 0.53f), h * 0.005f)
    drawCircle(Color(0xFFFFCA28), h * 0.012f, Offset(w * 0.934f, h * 0.535f))
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** The window at night, with curtains and the moon coming up as [moon] goes 0 → 1. */
fun DrawScope.drawNightWindow(moon: Float) {
    val r = Rect(size.width * WindowArea.left, size.height * WindowArea.top, size.width * WindowArea.right, size.height * WindowArea.bottom)
    drawRoundRect(Color(0xFFFFF8E1), r.topLeft - Offset(r.width * 0.05f, r.width * 0.05f),
        Size(r.width * 1.1f, r.height + r.width * 0.1f), CornerRadius(r.width * 0.03f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF3F51B5)), startY = r.top, endY = r.bottom), r.topLeft, r.size)
    clipRect(r.left, r.top, r.right, r.bottom) {
        val c = Offset(r.left + r.width * 0.62f, r.bottom - r.height * (0.1f + 0.62f * moon))
        val m = r.width * 0.17f
        drawCircle(Color(0x33FFF59D), m * 1.7f, c)
        drawCircle(Color(0xFFFFF59D), m, c)
        drawCircle(Color(0xFF1F2A8A), m * 0.85f, c + Offset(m * 0.5f, -m * 0.2f))  // a crescent
        for ((fx, fy) in listOf(0.2f to 0.25f, 0.4f to 0.6f, 0.15f to 0.75f, 0.85f to 0.2f)) {
            drawCircle(Color.White, r.width * 0.012f, Offset(r.left + r.width * fx, r.top + r.height * fy))
        }
    }
    // The bars, and curtains tied back at the sides
    drawLine(Color(0xFFFFF8E1), Offset(r.center.x, r.top), Offset(r.center.x, r.bottom), r.width * 0.025f)
    drawLine(Color(0xFFFFF8E1), Offset(r.left, r.center.y), Offset(r.right, r.center.y), r.width * 0.025f)
    val curtain = Color(0xFFF48FB1)
    for (side in listOf(-1f, 1f)) {
        val edge = if (side < 0) r.left else r.right
        val p = Path().apply {
            moveTo(edge - side * r.width * 0.12f, r.top - r.width * 0.05f)
            lineTo(edge + side * r.width * 0.06f, r.top - r.width * 0.05f)
            lineTo(edge + side * r.width * 0.06f, r.bottom + r.width * 0.05f)
            quadraticBezierTo(edge - side * r.width * 0.02f, r.center.y, edge - side * r.width * 0.12f, r.top - r.width * 0.05f)
            close()
        }
        drawPath(p, curtain)
    }
    drawLine(WoodDark, Offset(r.left - r.width * 0.12f, r.top - r.width * 0.06f), Offset(r.right + r.width * 0.12f, r.top - r.width * 0.06f), r.width * 0.022f, StrokeCap.Round)
}
