package io.github.tomsadeh.puki.ui.words

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp

private val Wood = Color(0xFFC89B6C)
private val WoodDark = Color(0xFF8D6240)
private val Paper = Color(0xFFFFFDF5)

/** The ink of a letter on a block: dark, so it reads on the pale face. */
val LetterInk = Color(0xFF4E342E)

/** A chunky toy block: colored wood with a pale face for the letter. */
fun DrawScope.drawWoodBlock(color: Color) {
    val s = size.minDimension
    val r = CornerRadius(s * 0.15f)
    drawRoundRect(Color.Black.copy(alpha = 0.14f), Offset(s * 0.02f, s * 0.07f), Size(s, s * 0.97f), r)
    drawRoundRect(lerp(color, Color.Black, 0.28f), Offset.Zero, Size(s, s), r)
    drawRoundRect(color, Offset.Zero, Size(s, s * 0.93f), r)
    drawRoundRect(lerp(color, Color.White, 0.86f), Offset(s * 0.11f, s * 0.09f), Size(s * 0.78f, s * 0.74f), CornerRadius(s * 0.09f))
    drawLine(Color.White.copy(alpha = 0.5f), Offset(s * 0.16f, s * 0.045f), Offset(s * 0.5f, s * 0.045f), s * 0.03f, StrokeCap.Round)
}

/** The word's picture on a wooden sign in the garden: [size] is the picture's side. */
@Composable
fun WordSign(size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.size(size * 1.16f, size * 1.34f), contentAlignment = Alignment.TopCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawSign() }
        Box(modifier = Modifier.padding(top = size * 0.08f).size(size), contentAlignment = Alignment.Center) { content() }
    }
}

private fun DrawScope.drawSign() {
    val w = size.width
    val h = size.height
    val board = w  // the board is square, as wide as the sign
    // The post, going into the grass
    drawRect(WoodDark, Offset(w * 0.44f, board * 0.9f), Size(w * 0.12f, h - board * 0.9f))
    drawOval(Color.Black.copy(alpha = 0.12f), Offset(w * 0.3f, h * 0.95f), Size(w * 0.4f, h * 0.05f))
    drawRoundRect(WoodDark, Offset(0f, h * 0.012f), Size(w, board), CornerRadius(w * 0.1f))
    drawRoundRect(Wood, Offset.Zero, Size(w, board * 0.985f), CornerRadius(w * 0.1f))
    drawRoundRect(Paper, Offset(w * 0.065f, w * 0.065f), Size(w * 0.87f, w * 0.85f), CornerRadius(w * 0.07f))
}
