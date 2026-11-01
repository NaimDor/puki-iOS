package io.github.tomsadeh.puki.ui.counting

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import io.github.tomsadeh.puki.data.content.Habitat

/** Where the round's things live, filling the area they're counted in. */
fun DrawScope.drawHabitat(habitat: Habitat) {
    when (habitat) {
        Habitat.BED -> drawFlowerBed()
        Habitat.TREE -> drawTree()
        Habitat.POND -> drawPond()
        Habitat.NIGHT -> drawNight()
        Habitat.SKY -> drawOpenSky()
    }
}

/** The count's sun: rays around a box of [radius] (they reach past it, so nothing clips them). */
fun DrawScope.drawSunRays(radius: Float) {
    val c = Offset(size.width / 2, size.height / 2)
    for (i in 0 until 12) rotate(i * 30f, c) {
        drawLine(Color(0xFFFFCA28), c - Offset(0f, radius * 1.1f), c - Offset(0f, radius * 1.32f), radius * 0.12f, StrokeCap.Round)
    }
}

private fun DrawScope.sky(top: Color, bottom: Color) = drawRect(Brush.verticalGradient(listOf(top, bottom)))

private fun DrawScope.hill(color: Color, topFraction: Float) {
    val w = size.width
    val h = size.height
    drawOval(color, Offset(-w * 0.2f, h * topFraction), Size(w * 1.4f, h * 1.2f))
}

private fun DrawScope.drawFlowerBed() {
    val w = size.width
    val h = size.height
    sky(Color(0xFFBBDEFB), Color(0xFFE3F2FD))
    hill(Color(0xFFA5D6A7), 0.26f)
    hill(Color(0xFF81C784), 0.46f)
    // Rows dug in the bed, going away from the viewer
    for (k in 0 until 3) {
        val y = h * (0.62f + k * 0.13f)
        drawOval(Color(0x338D6E63), Offset(w * 0.06f, y), Size(w * 0.88f, h * 0.075f))
    }
}

private fun DrawScope.drawTree() {
    val w = size.width
    val h = size.height
    sky(Color(0xFFB3E5FC), Color(0xFFE1F5FE))
    hill(Color(0xFF81C784), 0.84f)
    drawRect(Color(0xFF8D6E63), Offset(w * 0.46f, h * 0.6f), Size(w * 0.08f, h * 0.34f))
    val leaves = Color(0xFF66BB6A)
    val light = Color(0xFF81C784)
    val r = h * 0.3f
    for ((fx, fy) in listOf(0.2f to 0.42f, 0.36f to 0.27f, 0.5f to 0.42f, 0.64f to 0.27f, 0.8f to 0.42f, 0.36f to 0.58f, 0.64f to 0.58f)) {
        drawCircle(leaves, r, Offset(w * fx, h * fy))
    }
    for ((fx, fy) in listOf(0.3f to 0.26f, 0.58f to 0.2f, 0.76f to 0.34f)) drawCircle(light, r * 0.42f, Offset(w * fx, h * fy))
}

private fun DrawScope.drawPond() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF81C784))
    drawOval(Color(0xFF8D6E63).copy(alpha = 0.35f), Offset(w * 0.02f, h * 0.05f), Size(w * 0.96f, h * 0.92f))
    drawOval(Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))), Offset(w * 0.04f, h * 0.08f), Size(w * 0.92f, h * 0.86f))
    val ripple = Stroke(h * 0.008f, cap = StrokeCap.Round)
    for ((fx, fy) in listOf(0.26f to 0.3f, 0.66f to 0.24f, 0.46f to 0.66f, 0.78f to 0.62f, 0.18f to 0.62f)) {
        drawArc(Color.White.copy(alpha = 0.4f), 200f, 140f, false, Offset(w * fx, h * fy), Size(w * 0.09f, h * 0.05f), style = ripple)
    }
    // Reeds at the edge
    for (k in 0 until 3) {
        val x = w * (0.03f + k * 0.03f)
        drawLine(Color(0xFF558B2F), Offset(x, h), Offset(x + w * 0.01f, h * (0.62f + k * 0.07f)), w * 0.006f, StrokeCap.Round)
        drawOval(Color(0xFF6D4C41), Offset(x, h * (0.62f + k * 0.07f)), Size(w * 0.015f, h * 0.08f))
    }
}

private fun DrawScope.drawNight() {
    val w = size.width
    val h = size.height
    sky(Color(0xFF1A237E), Color(0xFF3F51B5))
    for ((fx, fy) in listOf(0.08f to 0.15f, 0.3f to 0.08f, 0.55f to 0.2f, 0.86f to 0.12f, 0.7f to 0.5f, 0.16f to 0.55f, 0.45f to 0.6f, 0.92f to 0.6f)) {
        drawCircle(Color.White.copy(alpha = 0.7f), h * 0.008f, Offset(w * fx, h * fy))
    }
    hill(Color(0xFF283593), 0.82f)
}

private fun DrawScope.drawOpenSky() {
    val w = size.width
    val h = size.height
    sky(Color(0xFF81D4FA), Color(0xFFE1F5FE))
    for ((fx, fy, s) in listOf(Triple(0.2f, 0.2f, 1f), Triple(0.76f, 0.34f, 1.3f), Triple(0.44f, 0.7f, 0.8f))) {
        val r = h * 0.07f * s
        val c = Offset(w * fx, h * fy)
        val cloud = Color.White.copy(alpha = 0.8f)
        drawCircle(cloud, r, c)
        drawCircle(cloud, r * 0.8f, c + Offset(-r, r * 0.3f))
        drawCircle(cloud, r * 0.8f, c + Offset(r, r * 0.3f))
    }
    hill(Color(0xFF81C784), 0.88f)
}
