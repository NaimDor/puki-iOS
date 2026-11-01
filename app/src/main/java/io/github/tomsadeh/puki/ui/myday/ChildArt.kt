package io.github.tomsadeh.puki.ui.myday

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/*
 * The toddler of My Day (getting dressed, bath time), drawn to fill a square: a big round head,
 * a round tummy and chubby arms and legs, with hair for a girl or a boy. Her clothes are drawn on
 * the same body, so they fit: sleeves on the arms, pants down the legs, socks and shoes on the feet.
 * Everything is in fractions of the square, and the picture doesn't mirror in Hebrew.
 */

/** Room a My Day scene leaves for Puki in the bottom start corner, so he doesn't stand on the child. */
val PukiCorner = 130.dp

private val Skin = Color(0xFFFFD1A6)
private val SkinShade = Color(0xFFF5B98A)
private val Hair = Color(0xFF8D5524)
private val Ink = Color(0xFF4E342E)

// The body's joints
private val HeadC = Offset(0.5f, 0.25f)
private const val HeadR = 0.135f
private val ShoulderL = Offset(0.385f, 0.43f)
private val ShoulderR = Offset(0.615f, 0.43f)
private val HandL = Offset(0.25f, 0.6f)
private val HandR = Offset(0.75f, 0.6f)
private val HipL = Offset(0.445f, 0.64f)
private val HipR = Offset(0.555f, 0.64f)
private val AnkleL = Offset(0.425f, 0.855f)
private val AnkleR = Offset(0.575f, 0.855f)
private const val ArmW = 0.075f
private const val LegW = 0.092f

private fun DrawScope.p(o: Offset) = Offset(o.x * size.width, o.y * size.width)
private fun DrawScope.u(f: Float) = f * size.width
private fun mix(a: Offset, b: Offset, t: Float) = a + (b - a) * t

/**
 * The child. [worn] says how far each piece of clothing has appeared (0 = not on, 1 = on;
 * underwear, shirt, pants, socks, shoes), so a piece can grow into place as it's put on.
 */
fun DrawScope.drawToddler(girl: Boolean, worn: Map<String, Float> = emptyMap()) {
    fun on(id: String) = worn[id] ?: 0f
    // A soft shadow on the floor
    drawOval(Color.Black.copy(alpha = 0.10f), Offset(u(0.3f), u(0.9f)), Size(u(0.4f), u(0.05f)))
    if (girl) drawPigtails()
    drawBody()
    drawHead(girl)
    // Clothes, from the skin out
    piece(on("underwear"), Offset(0.5f, 0.63f)) { drawUnderwear() }
    piece(on("pants"), Offset(0.5f, 0.72f)) { drawPants() }
    piece(on("shirt"), Offset(0.5f, 0.5f)) { drawShirt() }
    piece(on("socks"), Offset(0.5f, 0.87f)) { drawSocks() }
    piece(on("shoes"), Offset(0.5f, 0.88f)) { drawShoes() }
    piece(on("pajamas"), Offset(0.5f, 0.6f)) { drawPajamas() }
}

/** Just her head, eyes shut, for a child asleep on a pillow: in the same square as [drawToddler]. */
fun DrawScope.drawSleepingHead(girl: Boolean) {
    if (girl) drawPigtails()
    drawHead(girl, asleep = true)
}

/** A piece of clothing growing into place around [center] as [t] goes 0 → 1. */
private inline fun DrawScope.piece(t: Float, center: Offset, draw: DrawScope.() -> Unit) {
    if (t <= 0f) return
    val s = 0.6f + 0.4f * t.coerceAtMost(1.15f)
    scale(s, s, pivot = p(center)) { draw() }
}

private fun DrawScope.limb(from: Offset, to: Offset, width: Float, color: Color) =
    drawLine(color, p(from), p(to), u(width), StrokeCap.Round)

private fun DrawScope.drawBody() {
    // Arms behind the tummy, with round hands
    for ((s, h) in listOf(ShoulderL to HandL, ShoulderR to HandR)) {
        limb(s, h, ArmW, Skin)
        drawCircle(Skin, u(0.043f), p(h))
        drawCircle(SkinShade.copy(alpha = 0.5f), u(0.043f), p(h), style = Stroke(u(0.006f)))
    }
    // Legs and feet
    for ((hip, ankle, dir) in listOf(Triple(HipL, AnkleL, -1f), Triple(HipR, AnkleR, 1f))) {
        limb(hip, ankle, LegW, Skin)
        drawOval(Skin, p(ankle) + Offset(u(-0.055f + dir * 0.012f), u(-0.005f)), Size(u(0.11f), u(0.055f)))
    }
    // A round tummy and a belly button
    val top = u(0.37f)
    drawRoundRect(Skin, Offset(u(0.36f), top), Size(u(0.28f), u(0.31f)), CornerRadius(u(0.12f)))
    drawCircle(SkinShade, u(0.008f), p(Offset(0.5f, 0.58f)))
}

private fun DrawScope.drawHead(girl: Boolean, asleep: Boolean = false) {
    val c = p(HeadC)
    val r = u(HeadR)
    // Ears, then the head
    for (dx in listOf(-1f, 1f)) drawCircle(Skin, u(0.026f), c + Offset(dx * r * 0.98f, u(0.012f)))
    drawCircle(Skin, r, c)
    // Hair: a cap over the top with a soft fringe
    val hair = Path().apply {
        moveTo(c.x - r * 1.02f, c.y + r * 0.05f)
        cubicTo(c.x - r * 1.12f, c.y - r * 1.42f, c.x + r * 1.12f, c.y - r * 1.42f, c.x + r * 1.02f, c.y + r * 0.05f)
        quadraticBezierTo(c.x + r * 0.85f, c.y - r * 0.45f, c.x + r * 0.35f, c.y - r * 0.42f)
        quadraticBezierTo(c.x + r * 0.15f, c.y - r * 0.2f, c.x - r * 0.05f, c.y - r * 0.45f)
        quadraticBezierTo(c.x - r * 0.35f, c.y - r * 0.25f, c.x - r * 0.55f, c.y - r * 0.45f)
        quadraticBezierTo(c.x - r * 0.9f, c.y - r * 0.4f, c.x - r * 1.02f, c.y + r * 0.05f)
        close()
    }
    drawPath(hair, Hair)
    drawArc(Color.White.copy(alpha = 0.18f), 200f, 60f, false, c - Offset(r * 0.8f, r * 0.85f), Size(r * 1.6f, r * 1.2f),
        style = Stroke(r * 0.1f, cap = StrokeCap.Round))
    if (girl) {
        // A clip in her hair
        drawCircle(Color(0xFFEC407A), r * 0.13f, c + Offset(r * 0.55f, -r * 0.62f))
        drawCircle(Color.White.copy(alpha = 0.5f), r * 0.05f, c + Offset(r * 0.51f, -r * 0.66f))
    }
    // Eyes with a shine, rosy cheeks and a big smile
    for (dx in listOf(-0.36f, 0.36f)) {
        val e = c + Offset(dx * r, r * 0.05f)
        if (asleep) {
            drawArc(Ink, 20f, 140f, false, e - Offset(r * 0.14f, r * 0.08f), Size(r * 0.28f, r * 0.16f),
                style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            continue
        }
        drawOval(Ink, e - Offset(r * 0.1f, r * 0.13f), Size(r * 0.2f, r * 0.26f))
        drawCircle(Color.White, r * 0.05f, e + Offset(-r * 0.03f, -r * 0.05f))
    }
    for (dx in listOf(-0.6f, 0.6f)) drawOval(Color(0x66FF8A80), c + Offset(dx * r - r * 0.15f, r * 0.3f), Size(r * 0.3f, r * 0.18f))
    drawArc(Ink, 20f, 140f, false, c + Offset(-r * 0.28f, r * 0.12f), Size(r * 0.56f, r * 0.42f),
        style = Stroke(r * 0.08f, cap = StrokeCap.Round))
}

private fun DrawScope.drawPigtails() {
    val c = p(HeadC)
    val r = u(HeadR)
    for (dx in listOf(-1f, 1f)) {
        val tail = c + Offset(dx * r * 1.12f, -r * 0.25f)
        drawCircle(Hair, r * 0.38f, tail)
        drawCircle(Color(0xFFEC407A), r * 0.12f, c + Offset(dx * r * 0.88f, -r * 0.35f))
    }
}

// ---------- Clothes ----------

private fun DrawScope.drawUnderwear() {
    val color = Color(0xFF81D4FA)
    val shape = Path().apply {
        moveTo(u(0.37f), u(0.585f))
        lineTo(u(0.63f), u(0.585f))
        lineTo(u(0.625f), u(0.675f))
        quadraticBezierTo(u(0.565f), u(0.66f), u(0.53f), u(0.7f))
        lineTo(u(0.47f), u(0.7f))
        quadraticBezierTo(u(0.435f), u(0.66f), u(0.375f), u(0.675f))
        close()
    }
    drawPath(shape, color)
    drawRoundRect(Color(0xFF29B6F6), Offset(u(0.365f), u(0.578f)), Size(u(0.27f), u(0.025f)), CornerRadius(u(0.012f)))
    for ((x, y) in listOf(0.43f to 0.635f, 0.5f to 0.65f, 0.57f to 0.635f)) drawCircle(Color.White, u(0.01f), p(Offset(x, y)))
}

/** Soft pajamas, all in one go: long sleeves, long legs and little moons. */
private fun DrawScope.drawPajamas() {
    val color = Color(0xFFB39DDB)
    val dark = lerp(color, Color.Black, 0.18f)
    for ((s, h) in listOf(ShoulderL to HandL, ShoulderR to HandR)) limb(s, mix(s, h, 0.88f), ArmW + 0.03f, color)
    for ((hip, ankle) in listOf(HipL to AnkleL, HipR to AnkleR)) limb(hip, mix(hip, ankle, 0.95f), LegW + 0.03f, color)
    drawRoundRect(color, Offset(u(0.35f), u(0.375f)), Size(u(0.3f), u(0.32f)), CornerRadius(u(0.09f)))
    drawArc(dark, 0f, 180f, false, Offset(u(0.45f), u(0.36f)), Size(u(0.1f), u(0.05f)), style = Stroke(u(0.012f), cap = StrokeCap.Round))
    for (y in listOf(0.45f, 0.53f, 0.61f)) drawCircle(Color(0xFFFFF59D), u(0.012f), p(Offset(0.5f, y)))
    for ((x, y) in listOf(0.41f to 0.5f, 0.59f to 0.57f, 0.42f to 0.76f, 0.58f to 0.8f)) drawStar(p(Offset(x, y)), u(0.016f), Color(0xFFFFF59D))
}

private fun DrawScope.drawShirt() {
    val color = Color(0xFF42A5F5)
    val dark = lerp(color, Color.Black, 0.2f)
    // Short sleeves over the tops of the arms
    for ((s, h) in listOf(ShoulderL to HandL, ShoulderR to HandR)) limb(s, mix(s, h, 0.42f), ArmW + 0.03f, color)
    drawRoundRect(color, Offset(u(0.35f), u(0.375f)), Size(u(0.3f), u(0.25f)), CornerRadius(u(0.09f)))
    drawRoundRect(dark, Offset(u(0.35f), u(0.595f)), Size(u(0.3f), u(0.03f)), CornerRadius(u(0.015f)))
    drawArc(dark, 0f, 180f, false, Offset(u(0.45f), u(0.36f)), Size(u(0.1f), u(0.05f)), style = Stroke(u(0.012f), cap = StrokeCap.Round))
    drawStar(p(Offset(0.5f, 0.5f)), u(0.045f), Color(0xFFFFEE58))
}

private fun DrawScope.drawPants() {
    val color = Color(0xFF1E88E5)
    val light = Color(0xFF64B5F6)
    for ((hip, ankle) in listOf(HipL to AnkleL, HipR to AnkleR)) {
        val cuff = mix(hip, ankle, 0.86f)
        limb(hip, cuff, LegW + 0.022f, color)
        drawLine(light, p(cuff) - Offset(u(0.05f), 0f), p(cuff) + Offset(u(0.05f), 0f), u(0.016f), StrokeCap.Round)
    }
    drawRoundRect(color, Offset(u(0.37f), u(0.58f)), Size(u(0.26f), u(0.11f)), CornerRadius(u(0.04f)))
    drawRoundRect(Color(0xFF1565C0), Offset(u(0.365f), u(0.578f)), Size(u(0.27f), u(0.028f)), CornerRadius(u(0.014f)))
    drawCircle(Color(0xFFFFCA28), u(0.011f), p(Offset(0.5f, 0.592f)))
}

private fun DrawScope.drawSocks() {
    for ((ankle, dir) in listOf(AnkleL to -1f, AnkleR to 1f)) {
        val a = p(ankle)
        // Up the ankle, then over the foot
        drawRoundRect(Color.White, a - Offset(u(0.05f), u(0.06f)), Size(u(0.1f), u(0.075f)), CornerRadius(u(0.03f)))
        for (k in 0..1) drawRect(Color(0xFFEF5350), a + Offset(u(-0.05f), u(-0.05f + k * 0.025f)), Size(u(0.1f), u(0.011f)))
        drawOval(Color.White, a + Offset(u(-0.06f + dir * 0.012f), u(-0.01f)), Size(u(0.12f), u(0.064f)))
        drawOval(Color(0xFFEF5350), a + Offset(u(dir * 0.03f), u(0.0f)), Size(u(0.035f), u(0.05f)))
    }
}

private fun DrawScope.drawShoes() {
    for ((ankle, dir) in listOf(AnkleL to -1f, AnkleR to 1f)) {
        val a = p(ankle)
        val left = a.x + u(-0.068f + dir * 0.014f)
        val w = u(0.136f)
        // A red sneaker with a white sole and laces
        val top = Path().apply {
            moveTo(left, a.y + u(0.045f))
            lineTo(left, a.y + u(0.0f))
            quadraticBezierTo(left + w * 0.5f, a.y - u(0.04f), left + w, a.y + u(0.015f))
            lineTo(left + w, a.y + u(0.045f))
            close()
        }
        drawPath(top, Color(0xFFE53935))
        drawRoundRect(Color.White, Offset(left - u(0.004f), a.y + u(0.038f)), Size(w + u(0.008f), u(0.024f)), CornerRadius(u(0.012f)))
        for (k in 0..1) {
            val lx = left + w * (0.35f + k * 0.18f)
            drawLine(Color.White, Offset(lx, a.y - u(0.008f)), Offset(lx + w * 0.1f, a.y + u(0.012f)), u(0.008f), StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val star = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians((-90 + i * 36).toDouble())
        val pt = Offset(c.x + rr * kotlin.math.cos(a).toFloat(), c.y + rr * kotlin.math.sin(a).toFloat())
        if (i == 0) star.moveTo(pt.x, pt.y) else star.lineTo(pt.x, pt.y)
    }
    star.close()
    drawPath(star, color)
}

// ---------- Bath time ----------

/** A splat of mud at [c] (fractions), [amount] of it left. Lumpy, with a drip running down. */
fun DrawScope.drawMudSplat(c: Offset, r: Float, seed: Int, amount: Float = 1f) {
    if (amount <= 0f) return
    val center = p(c)
    val rr = u(r)
    val mud = Color(0xFF6D4C41).copy(alpha = 0.92f * amount)
    val rnd = kotlin.random.Random(seed)
    drawCircle(mud, rr * 0.62f, center)
    for (k in 0 until 6) {
        val a = k / 6f * 2 * Math.PI + rnd.nextFloat()
        val d = rr * (0.45f + rnd.nextFloat() * 0.25f)
        drawCircle(mud, rr * (0.22f + rnd.nextFloat() * 0.16f),
            center + Offset((kotlin.math.cos(a) * d).toFloat(), (kotlin.math.sin(a) * d).toFloat()))
    }
    // A drip, and a lighter lump catching the light
    drawLine(mud, center + Offset(rr * 0.15f, rr * 0.3f), center + Offset(rr * 0.15f, rr * 1.0f), rr * 0.22f, StrokeCap.Round)
    drawCircle(Color(0xFF8D6E63).copy(alpha = 0.8f * amount), rr * 0.2f, center + Offset(-rr * 0.25f, -rr * 0.22f))
}

/** Soap bubbles where the mud was, drifting up a little with [wave]; [left] fades them as the rinse washes them off. */
fun DrawScope.drawSoapBubbles(c: Offset, r: Float, wave: Float, left: Float = 1f) {
    if (left <= 0f) return
    val center = p(c)
    val rr = u(r) * 1.45f
    listOf(Triple(-0.4f, -0.1f, 0.34f), Triple(0.3f, -0.35f, 0.26f), Triple(0.32f, 0.28f, 0.34f),
        Triple(-0.18f, 0.42f, 0.22f), Triple(0.0f, -0.05f, 0.18f)).forEachIndexed { i, (dx, dy, br) ->
        val bc = center + Offset(dx * rr, dy * rr - ((wave + i * 0.2f) % 1f) * rr * 0.18f)
        drawCircle(Color.White.copy(alpha = 0.75f * left), br * rr, bc)
        drawCircle(Color(0xFF81D4FA).copy(alpha = left), br * rr, bc, style = Stroke(u(0.004f)))
        drawCircle(Color.White.copy(alpha = left), br * rr * 0.32f, bc + Offset(-br * rr * 0.35f, -br * rr * 0.35f))
    }
}

/**
 * The tub in front of the child's legs, with water ([wave] makes it move), foam, a rubber duck
 * bobbing on it, and the shower head on the wall above; while [rinsing], water pours from it.
 */
fun DrawScope.drawTub(wave: Float, fall: Float, rinsing: Boolean) {
    val w = size.width
    val top = w * 0.8f
    // The shower head on its pipe, right above the child's head
    val pipe = Color(0xFF90A4AE)
    drawLine(pipe, Offset(w * 0.8f, -w * 0.05f), Offset(w * 0.8f, w * 0.012f), w * 0.022f, StrokeCap.Round)
    drawLine(pipe, Offset(w * 0.8f, w * 0.012f), Offset(w * 0.5f, w * 0.012f), w * 0.022f, StrokeCap.Round)
    val head = Path().apply {
        moveTo(w * 0.42f, w * 0.075f)
        quadraticBezierTo(w * 0.5f, -w * 0.02f, w * 0.58f, w * 0.075f)
        close()
    }
    drawPath(head, Color(0xFFB0BEC5))
    drawLine(Color(0xFF78909C), Offset(w * 0.415f, w * 0.075f), Offset(w * 0.585f, w * 0.075f), w * 0.016f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.6f), Offset(w * 0.455f, w * 0.05f), Offset(w * 0.48f, w * 0.035f), w * 0.008f, StrokeCap.Round)
    if (rinsing) {
        // A fan of water from the head down over the child to the tub
        for (i in 0 until 48) {
            val spread = (i * 37 % 100) / 100f - 0.5f  // -0.5..0.5 across the head
            val t = (fall + i * 0.113f) % 1f
            val x = w * (0.5f + spread * 0.15f + spread * 0.42f * t)
            val y = w * 0.08f + t * w * 0.74f
            drawLine(Color(0xCC4FC3F7), Offset(x, y), Offset(x, y + w * 0.04f), w * 0.011f, StrokeCap.Round)
        }
    }
    // Water with a moving surface
    val water = Path().apply {
        moveTo(w * 0.09f, top + w * 0.02f)
        var x = w * 0.09f
        while (x <= w * 0.91f) {
            lineTo(x, top + w * 0.02f + kotlin.math.sin((x / w * 6f + wave) * 2 * Math.PI.toFloat()) * w * 0.007f)
            x += w * 0.02f
        }
        lineTo(w * 0.91f, w * 0.92f); lineTo(w * 0.09f, w * 0.92f); close()
    }
    drawPath(water, Color(0xFF81D4FA).copy(alpha = 0.9f))
    for (i in 0 until 11) {
        val fx = w * (0.11f + i * 0.078f)
        drawCircle(Color.White, w * (0.02f + 0.009f * (i % 3)), Offset(fx, top + w * 0.022f))
    }
    // A rubber duck bobbing on the left
    val bob = kotlin.math.sin(wave * 2 * Math.PI.toFloat()) * w * 0.006f
    val duck = Offset(w * 0.2f, top + bob)
    drawOval(Color(0xFFFFD54F), duck + Offset(-w * 0.045f, -w * 0.025f), Size(w * 0.09f, w * 0.05f))
    drawCircle(Color(0xFFFFD54F), w * 0.025f, duck + Offset(w * 0.025f, -w * 0.04f))
    drawOval(Color(0xFFFF8F00), duck + Offset(w * 0.043f, -w * 0.045f), Size(w * 0.025f, w * 0.014f))
    drawCircle(Ink, w * 0.005f, duck + Offset(w * 0.03f, -w * 0.047f))
    // The tub: white enamel with a rim and golden feet
    val body = Path().apply {
        moveTo(w * 0.05f, top + w * 0.03f)
        lineTo(w * 0.95f, top + w * 0.03f)
        quadraticBezierTo(w * 0.94f, top + w * 0.17f, w * 0.82f, top + w * 0.175f)
        lineTo(w * 0.18f, top + w * 0.175f)
        quadraticBezierTo(w * 0.06f, top + w * 0.17f, w * 0.05f, top + w * 0.03f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color.White, Color(0xFFE3F2FD)), startY = top, endY = top + w * 0.18f))
    drawRoundRect(Color(0xFFCFD8DC), Offset(w * 0.035f, top + w * 0.012f), Size(w * 0.93f, w * 0.03f), CornerRadius(w * 0.015f))
    drawLine(Color.White.copy(alpha = 0.8f), Offset(w * 0.15f, top + w * 0.065f), Offset(w * 0.4f, top + w * 0.065f), w * 0.01f, StrokeCap.Round)
    for (fx in listOf(0.2f, 0.8f)) {
        drawOval(Color(0xFFFFC107), Offset(w * (fx - 0.035f), top + w * 0.165f), Size(w * 0.07f, w * 0.035f))
    }
}

/** Wraps [draw] so only what's above the tub's water line shows (the legs go into the water). */
fun DrawScope.aboveWater(draw: DrawScope.() -> Unit) {
    clipRect(0f, 0f, size.width, size.width * 0.83f) { draw() }
}
