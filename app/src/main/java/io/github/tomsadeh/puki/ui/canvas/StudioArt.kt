package io.github.tomsadeh.puki.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** A warm wall with soft polka dots behind the easel. */
internal fun DrawScope.drawStudioWall() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1DC), Color(0xFFFFE2BF))))
    val dot = Color(0x22FF8A65)
    val step = 70.dp.toPx()
    var row = 0
    var y = step / 2
    while (y < size.height) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width) {
            drawCircle(dot, 6.dp.toPx(), Offset(x, y))
            x += step
        }
        y += step * 0.75f
        row++
    }
    // Floorboards along the bottom
    val floorTop = size.height * 0.93f
    drawRect(Color(0xFFD7A86E), Offset(0f, floorTop), Size(size.width, size.height - floorTop))
    drawRect(Color(0xFFBF8A55), Offset(0f, floorTop), Size(size.width, 4.dp.toPx()))
}

/** Very faint fibres so the page reads as paper rather than a white screen. */
internal fun DrawScope.drawPaperGrain() {
    val fibre = Color(0x068D6E63)
    val step = 22.dp.toPx()
    var y = step
    var i = 0
    while (y < size.height) {
        val wobble = sin(i * 1.7f) * 3.dp.toPx()
        drawLine(fibre, Offset(0f, y + wobble), Offset(size.width, y - wobble), 1.dp.toPx())
        y += step
        i++
    }
}

internal fun DrawScope.drawTape() {
    val tape = Color(0xAAFFF59D)
    val w = 110.dp.toPx()
    val h = 34.dp.toPx()
    listOf(
        Triple(Offset(30.dp.toPx(), 26.dp.toPx()), -35f, tape),
        Triple(Offset(size.width - 30.dp.toPx(), 26.dp.toPx()), 35f, Color(0xAAB3E5FC)),
        Triple(Offset(30.dp.toPx(), size.height - 26.dp.toPx()), 35f, Color(0xAAF8BBD0)),
        Triple(Offset(size.width - 30.dp.toPx(), size.height - 26.dp.toPx()), -35f, tape)
    ).forEach { (c, angle, color) ->
        rotate(angle, pivot = c) {
            drawRect(color, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
            // Zig-zag torn ends
            for (end in listOf(-1f, 1f)) {
                val x = c.x + end * w / 2
                var y = c.y - h / 2
                val tooth = h / 5
                while (y < c.y + h / 2 - 1f) {
                    val tri = Path().apply {
                        moveTo(x, y); lineTo(x + end * tooth * 0.6f, y + tooth / 2); lineTo(x, y + tooth); close()
                    }
                    drawPath(tri, color)
                    y += tooth
                }
            }
        }
    }
}

internal fun DrawScope.drawPaintPot(color: Color, lift: Float) {
    val w = size.width
    val h = size.height
    val dark = color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f)
    // Shadow on the "table"
    drawOval(Color(0x33000000), Offset(w * 0.2f, h * 0.88f), Size(w * 0.6f, h * 0.1f))

    // A brush standing in the chosen pot
    if (lift > 0.01f) {
        rotate(18f, pivot = Offset(w * 0.5f, h * 0.45f)) {
            val top = h * (0.42f - 0.42f * lift)
            drawLine(Color(0xFFA1683A), Offset(w * 0.5f, top), Offset(w * 0.5f, h * 0.5f), w * 0.07f, StrokeCap.Round)
            drawRoundRect(Color(0xFFB0BEC5), Offset(w * 0.46f, h * 0.38f), Size(w * 0.08f, h * 0.1f), CornerRadius(3f))
        }
    }

    // Pot body: a white ceramic jar with paint dribbling down and a coloured band
    val bodyTop = h * 0.36f
    val bodyLeft = w * 0.18f
    val bodyW = w * 0.64f
    drawRoundRect(Color(0xFFFAFAFA), Offset(bodyLeft, bodyTop), Size(bodyW, h * 0.54f), CornerRadius(w * 0.12f))
    // Paint pooled at the bottom, and drips running down from the rim
    drawRoundRect(color.copy(alpha = 0.35f), Offset(bodyLeft, h * 0.76f), Size(bodyW, h * 0.14f), CornerRadius(w * 0.12f))
    listOf(0.3f to 0.3f, 0.5f to 0.4f, 0.68f to 0.2f).forEach { (fx, len) ->
        val x = w * fx
        drawLine(color, Offset(x, bodyTop), Offset(x, bodyTop + h * len), w * 0.07f, StrokeCap.Round)
        drawCircle(color, w * 0.05f, Offset(x, bodyTop + h * len))
    }
    // Rim and the paint inside
    drawOval(Color(0xFFE0E0E0), Offset(w * 0.14f, h * 0.27f), Size(w * 0.72f, h * 0.18f))
    drawOval(color, Offset(w * 0.19f, h * 0.29f), Size(w * 0.62f, h * 0.13f))
    drawOval(dark.copy(alpha = 0.35f), Offset(w * 0.3f, h * 0.33f), Size(w * 0.4f, h * 0.06f))
    // Shine
    drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(bodyLeft + w * 0.06f, h * 0.48f), Size(w * 0.06f, h * 0.3f), CornerRadius(w * 0.03f))
}

/** A paintbrush lying diagonally, its tip dipped in [color]. */
internal fun DrawScope.drawBrush(color: Color, fat: Boolean) {
    val w = size.width
    val h = size.height
    rotate(-45f, pivot = Offset(w / 2, h / 2)) {
        val cx = w / 2
        val handleW = if (fat) w * 0.13f else w * 0.09f
        drawLine(Color(0xFFA1683A), Offset(cx, h * 0.92f), Offset(cx, h * 0.5f), handleW, StrokeCap.Round)
        val ferruleW = if (fat) w * 0.22f else w * 0.13f
        drawRoundRect(Color(0xFFB0BEC5), Offset(cx - ferruleW / 2, h * 0.38f), Size(ferruleW, h * 0.14f), CornerRadius(4f))
        val tipW = if (fat) w * 0.3f else w * 0.15f
        val tip = Path().apply {
            moveTo(cx - tipW / 2, h * 0.39f)
            cubicTo(cx - tipW / 2, h * 0.2f, cx, h * 0.12f, cx, h * 0.04f)
            cubicTo(cx, h * 0.12f, cx + tipW / 2, h * 0.2f, cx + tipW / 2, h * 0.39f)
            close()
        }
        drawPath(tip, color)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - tipW * 0.15f, h * 0.34f), Offset(cx - tipW * 0.05f, h * 0.16f), w * 0.025f, StrokeCap.Round)
    }
}

/** A pink rubber eraser with a blue paper sleeve, lying at a slant. */
internal fun DrawScope.drawEraser() {
    val w = size.width
    val h = size.height
    rotate(-30f, pivot = Offset(w / 2, h / 2)) {
        val top = h * 0.3f
        val bodyH = h * 0.4f
        drawRoundRect(Color(0xFFF48FB1), Offset(w * 0.08f, top), Size(w * 0.84f, bodyH), CornerRadius(w * 0.08f))
        drawRect(Color(0xFF64B5F6), Offset(w * 0.5f, top), Size(w * 0.3f, bodyH))
        drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(w * 0.14f, top + bodyH * 0.15f), Size(w * 0.28f, bodyH * 0.18f), CornerRadius(w * 0.04f))
    }
}

/** A little blue camera with a big lens, for the button that saves the drawing. */
internal fun DrawScope.drawCamera() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF455A64), Offset(w * 0.32f, h * 0.2f), Size(w * 0.36f, h * 0.18f), CornerRadius(w * 0.05f))
    drawRoundRect(Color(0xFF546E7A), Offset(w * 0.08f, h * 0.3f), Size(w * 0.84f, h * 0.54f), CornerRadius(w * 0.12f))
    drawRoundRect(Color(0xFFFFCA28), Offset(w * 0.68f, h * 0.36f), Size(w * 0.14f, h * 0.09f), CornerRadius(w * 0.03f))
    val c = Offset(w * 0.5f, h * 0.57f)
    drawCircle(Color(0xFFECEFF1), w * 0.21f, c)
    drawCircle(Color(0xFF29B6F6), w * 0.15f, c)
    drawCircle(Color.White.copy(alpha = 0.75f), w * 0.05f, c - Offset(w * 0.05f, w * 0.05f))
}
