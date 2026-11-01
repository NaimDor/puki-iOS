package io.github.tomsadeh.puki.ui.myday.brush

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/*
 * The faces that come in for a brush. Each fills a square box and has the very same mouth in the
 * very same place, so the teeth (fractions of that box) line up with all of them.
 */

/** Who's getting their teeth brushed. */
enum class Patient { TEDDY, LION, CROCODILE }

/** The two eyes at height [y]: round, shut for a blink, or happy arcs. */
fun DrawScope.drawFaceEyes(happy: Boolean, eyesShut: Boolean, y: Float, xs: List<Float>) {
    val w = size.width
    for (ex in xs) {
        val c = Offset(w * ex, w * y)
        if (happy || eyesShut) {
            drawArc(Color(0xFF3E2723), if (happy) 200f else 20f, 140f, false, c - Offset(w * 0.04f, w * 0.03f), Size(w * 0.08f, w * 0.06f),
                style = Stroke(w * 0.014f, cap = StrokeCap.Round))
        } else {
            drawCircle(Color(0xFF3E2723), w * 0.04f, c)
            drawCircle(Color.White, w * 0.013f, c + Offset(-w * 0.012f, -w * 0.014f))
        }
    }
}

/** The mouth: wide open, shut while [puff]ing water, or grinning as [smile] goes to 1. */
fun DrawScope.drawFaceMouth(puff: Float, smile: Float, lips: Color) {
    val w = size.width
    when {
        puff > 0f -> drawLine(lips, Offset(w * 0.42f, w * 0.68f), Offset(w * 0.58f, w * 0.68f), w * 0.018f, StrokeCap.Round)
        smile >= 0.5f -> {
            val grin = Path().apply {
                moveTo(w * 0.28f, w * 0.6f)
                quadraticBezierTo(w * 0.5f, w * 0.86f, w * 0.72f, w * 0.6f)
                quadraticBezierTo(w * 0.5f, w * 0.68f, w * 0.28f, w * 0.6f)
                close()
            }
            drawPath(grin, Color(0xFF6D2B2B))
            drawRoundRect(Color.White, Offset(w * 0.34f, w * 0.635f), Size(w * 0.32f, w * 0.045f), CornerRadius(w * 0.022f))
        }
        else -> {
            val h = w * 0.34f * (1f - smile * 2f)
            drawRoundRect(Color(0xFF6D2B2B), Offset(w * 0.24f, w * 0.7f - h / 2), Size(w * 0.52f, h), CornerRadius(w * 0.14f))
            drawOval(Color(0xFFF06292), Offset(w * 0.36f, w * 0.7f + h * 0.02f), Size(w * 0.28f, h * 0.3f))
        }
    }
}

/** A lion's face: a big fluffy mane all round, ears on top of it. */
fun DrawScope.drawLion(happy: Boolean, eyesShut: Boolean, puff: Float, smile: Float) {
    val w = size.width
    val mane = Color(0xFFC4621C)
    val fur = Color(0xFFFFC94A)
    val center = Offset(w * 0.5f, w * 0.5f)
    // The mane: fluffy tufts, and the lighter part inside them
    for (k in 0 until 14) {
        val a = k * 2 * Math.PI / 14
        drawCircle(mane, w * 0.1f, center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (w * 0.385f))
    }
    drawCircle(Color(0xFFE07A2B), w * 0.445f, center)
    // Ears
    for (ex in listOf(0.2f, 0.8f)) {
        drawCircle(fur, w * 0.09f, Offset(w * ex, w * 0.2f))
        drawCircle(mane, w * 0.05f, Offset(w * ex, w * 0.2f))
    }
    // Head, with cheeks that puff out with water
    drawCircle(fur, w * 0.39f, Offset(w * 0.5f, w * 0.52f))
    if (puff > 0f) for (cx in listOf(0.2f, 0.8f)) drawCircle(fur, w * (0.1f + 0.07f * puff), Offset(w * cx, w * 0.66f))
    drawOval(Color(0xFFFFF1C9), Offset(w * 0.17f, w * 0.42f), Size(w * 0.66f, w * 0.5f))
    for (cx in listOf(0.24f, 0.76f)) drawCircle(Color(0x55FF7F8F), w * (0.04f + 0.02f * puff), Offset(w * cx, w * 0.48f))
    drawFaceEyes(happy, eyesShut, 0.36f, listOf(0.37f, 0.63f))
    // Nose
    val nose = Path().apply {
        moveTo(w * 0.445f, w * 0.43f)
        quadraticBezierTo(w * 0.5f, w * 0.41f, w * 0.555f, w * 0.43f)
        quadraticBezierTo(w * 0.52f, w * 0.505f, w * 0.5f, w * 0.505f)
        quadraticBezierTo(w * 0.48f, w * 0.505f, w * 0.445f, w * 0.43f)
        close()
    }
    drawPath(nose, Color(0xFF7A4320))
    drawFaceMouth(puff, smile, Color(0xFF7A4320))
}

/** A crocodile, head on: eyes on two bumps on top, a long snout, a big pale jaw. */
fun DrawScope.drawCrocodile(happy: Boolean, eyesShut: Boolean, puff: Float, smile: Float) {
    val w = size.width
    val skin = Color(0xFF66BB6A)
    val dark = Color(0xFF43A047)
    // Bumps along his back, behind the head
    for (x in listOf(0.38f, 0.5f, 0.62f)) {
        val bump = Path().apply {
            moveTo(w * (x - 0.07f), w * 0.2f)
            quadraticBezierTo(w * x, w * 0.02f, w * (x + 0.07f), w * 0.2f)
            close()
        }
        drawPath(bump, dark)
    }
    // Knobbly bumps down the sides of his head
    for (y in listOf(0.36f, 0.52f, 0.68f, 0.84f)) for (side in listOf(-1f, 1f)) {
        drawCircle(dark, w * 0.06f, Offset(w * (0.5f + side * 0.4f), w * y))
    }
    // Eye bumps
    for (ex in listOf(0.29f, 0.71f)) {
        drawCircle(skin, w * 0.14f, Offset(w * ex, w * 0.2f))
        drawCircle(Color.White, w * 0.085f, Offset(w * ex, w * 0.19f))
    }
    // Head, with cheeks that puff out with water
    drawRoundRect(skin, Offset(w * 0.1f, w * 0.2f), Size(w * 0.8f, w * 0.76f), CornerRadius(w * 0.27f))
    if (puff > 0f) for (cx in listOf(0.14f, 0.86f)) drawCircle(skin, w * (0.1f + 0.06f * puff), Offset(w * cx, w * 0.66f))
    // The pale jaw around the mouth
    drawRoundRect(Color(0xFFDCEDC8), Offset(w * 0.17f, w * 0.5f), Size(w * 0.66f, w * 0.42f), CornerRadius(w * 0.2f))
    drawFaceEyes(happy, eyesShut, 0.19f, listOf(0.29f, 0.71f))
    // The snout, with two nostrils and a few bumps
    drawRoundRect(dark, Offset(w * 0.2f, w * 0.34f), Size(w * 0.6f, w * 0.18f), CornerRadius(w * 0.09f))
    for (nx in listOf(0.42f, 0.58f)) drawOval(Color(0xFF2E7D32), Offset(w * (nx - 0.03f), w * 0.4f), Size(w * 0.06f, w * 0.05f))
    drawCircle(skin, w * 0.016f, Offset(w * 0.28f, w * 0.41f))
    drawCircle(skin, w * 0.016f, Offset(w * 0.72f, w * 0.41f))
    drawFaceMouth(puff, smile, Color(0xFF2E7D32))
}
