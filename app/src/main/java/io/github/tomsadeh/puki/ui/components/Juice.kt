package io.github.tomsadeh.puki.ui.components

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ---------- Touch feedback ----------

/**
 * Pressed things squish a little and spring back, instead of Material's ripple (which draws
 * a grey rectangle around anything that isn't clipped). Provided app-wide in the theme, so
 * every clickable gets it.
 */
object SquishIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = SquishNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 1
}

/** Scales what it's on down while pressed, with a springy return (a node: nothing recomposes). */
private class SquishNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val scale = Animatable(1f)
    private val spec = spring<Float>(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> launch { scale.animateTo(0.88f, spec) }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch { scale.animateTo(1f, spec) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        scale(scale.value) { this@draw.drawContent() }
    }
}

// ---------- Life ----------

/**
 * Gentle idle motion (a slow bob and breath), so a waiting screen feels alive. [index]
 * staggers neighbours so they don't move in lockstep.
 */
fun Modifier.idleBob(index: Int = 0, amount: Float = 1f): Modifier = composed {
    val t = rememberInfiniteTransition(label = "idle")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200 + index * 170, easing = LinearEasing)),
        label = "idle_phase"
    )
    graphicsLayer {
        val a = 2 * PI.toFloat() * (phase + index * 0.23f)
        translationY = sin(a) * 6f * density * amount
        val breath = 1f + sin(a * 2) * 0.025f * amount
        scaleX = breath
        scaleY = 2f - breath
        rotationZ = sin(a) * 1.5f * amount
    }
}

/** Pops in with a bounce when it first appears; [index] staggers a row of items. */
fun Modifier.popIn(index: Int = 0, key: Any? = Unit): Modifier = composed {
    val s = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(80L * index)
        s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        scaleX = s.value
        scaleY = s.value
        alpha = s.value.coerceIn(0f, 1f)
    }
}


/** What to do, in a white speech pill (readable on any scene; mostly for the grown-up). */
@Composable
fun PromptBubble(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .popIn(key = text)
            .shadow(6.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.93f))
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF5D4037))
    }
}

// ---------- Scenes ----------

/** Illustrated backdrops for the games, drawn in code (no image files). */
enum class Scene { FARM, GARDEN, PLAYGROUND, STAGE, HOME, SUNSET, BATH }

@Composable
fun SceneBackdrop(scene: Scene, modifier: Modifier = Modifier) {
    val rooms = scene == Scene.HOME || scene == Scene.BATH
    Box(modifier = modifier.fillMaxSize()) {
        // What never moves, drawn once in a layer of its own (hills built once per size)
        Spacer(modifier = Modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val hills = if (rooms || scene == Scene.STAGE) null else run {
                hillPath(size.height * 0.66f, size.height * 0.05f, 1.3f, 0.2f) to hillPath(size.height * 0.76f, size.height * 0.035f, 2.1f, 0.65f)
            }
            val pal = paletteFor(scene)
            onDrawBehind {
                when (scene) {
                    Scene.STAGE -> drawStage()
                    Scene.HOME -> drawRoom(wall = Color(0xFFFFF3E0), stripe = Color(0xFFFFE0B2), floor = Color(0xFFD7A86E))
                    Scene.BATH -> drawRoom(wall = Color(0xFFE1F5FE), stripe = Color(0xFFB3E5FC), floor = Color(0xFFB0BEC5), tiles = true)
                    else -> drawOutdoorsStill(scene, pal, hills!!)
                }
            }
        })
        // What moves (clouds, grass, spotlights); the rooms don't move at all
        if (!rooms) SceneMotion(scene)
    }
}

@Composable
private fun SceneMotion(scene: Scene) {
    val t = rememberInfiniteTransition(label = "scene")
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)),
        label = "clouds"
    )
    val sway by t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "sway"
    )
    Spacer(modifier = Modifier.fillMaxSize().graphicsLayer().drawWithCache {
        val beam = if (scene == Scene.STAGE) spotlightBeam() else null
        val pal = paletteFor(scene)
        onDrawBehind {
            if (beam != null) drawSpotlights(beam, sway) else drawOutdoorsMotion(scene, pal, drift, sway)
        }
    })
}

private class Palette(val skyTop: Color, val skyBottom: Color, val hillBack: Color, val hillFront: Color, val accent: Color)

private fun paletteFor(scene: Scene) = when (scene) {
    Scene.FARM -> Palette(Color(0xFF64B5F6), Color(0xFFBBDEFB), Color(0xFF9CCC65), Color(0xFF66BB6A), Color(0xFFFFF176))
    Scene.GARDEN -> Palette(Color(0xFF81D4FA), Color(0xFFE1F5FE), Color(0xFFAED581), Color(0xFF7CB342), Color(0xFFF48FB1))
    Scene.PLAYGROUND -> Palette(Color(0xFFCE93D8), Color(0xFFF8BBD0), Color(0xFF80CBC4), Color(0xFF4DB6AC), Color(0xFFFFE082))
    Scene.SUNSET -> Palette(Color(0xFFFFB74D), Color(0xFFFFF59D), Color(0xFFFFCC80), Color(0xFFA5D6A7), Color(0xFFFFFFFF))
    else -> Palette(Color(0xFF81D4FA), Color(0xFFE1F5FE), Color(0xFFAED581), Color(0xFF7CB342), Color(0xFFFFF176))
}

private val CLOUDS = listOf(Triple(0.1f, 0.14f, 1f), Triple(0.45f, 0.08f, 0.7f), Triple(0.7f, 0.26f, 0.85f))

private fun DrawScope.drawOutdoorsStill(scene: Scene, p: Palette, hills: Pair<Path, Path>) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom), endY = h * 0.75f))
    // Sun with a soft glow
    val sun = Offset(w * 0.86f, h * 0.17f)
    drawCircle(Brush.radialGradient(listOf(Color(0x88FFF59D), Color.Transparent), center = sun, radius = h * 0.2f), h * 0.2f, sun)
    drawCircle(Color(0xFFFFE082), h * 0.075f, sun)
    drawCircle(Color(0xFFFFF59D), h * 0.055f, sun)
    if (scene == Scene.PLAYGROUND) drawRainbow(Offset(w * 0.5f, h * 0.78f), h * 0.62f)
    // Two rows of hills
    drawPath(hills.first, p.hillBack)
    drawPath(hills.second, p.hillFront)
}

private fun DrawScope.drawOutdoorsMotion(scene: Scene, p: Palette, drift: Float, sway: Float) {
    val w = size.width
    val h = size.height
    // Clouds drifting by (high in the sky, clear of the hills)
    CLOUDS.forEachIndexed { i, (fx, fy, s) ->
        val x = ((fx + drift * (0.6f + i * 0.25f)) % 1.2f - 0.1f) * w
        drawCloud(Offset(x, h * fy), h * 0.06f * s)
    }
    // Grass tufts and flowers along the front hill
    val tuft = p.hillFront.copy(red = p.hillFront.red * 0.8f, green = p.hillFront.green * 0.85f, blue = p.hillFront.blue * 0.8f)
    for (i in 0 until 14) {
        val x = w * (i + 0.5f) / 14f + sin(i * 2.7f) * w * 0.02f
        val y = hillY(x, w, h * 0.76f, h * 0.035f, 2.1f, 0.65f) + h * (0.03f + 0.15f * ((i * 37) % 10) / 10f)
        if (y > h * 0.97f) continue
        for (b in -1..1) {
            drawLine(tuft, Offset(x + b * 5f, y), Offset(x + b * 9f + sway * 3f, y - h * 0.022f), strokeWidth = 4f)
        }
        if (i % 3 == 1 && scene != Scene.FARM) {
            val fc = if (i % 2 == 0) p.accent else Color.White
            for (k in 0 until 5) {
                val a = k * 2 * PI.toFloat() / 5
                drawCircle(fc, h * 0.008f, Offset(x + 14f + sin(a) * h * 0.009f, y - h * 0.03f + kotlin.math.cos(a) * h * 0.009f))
            }
            drawCircle(Color(0xFFFFC107), h * 0.006f, Offset(x + 14f, y - h * 0.03f))
        }
    }
    if (scene == Scene.FARM) drawFence(h * 0.74f)  // in front of the grass, as before
}

private fun hillY(x: Float, w: Float, base: Float, amp: Float, freq: Float, phase: Float) =
    base - sin((x / w * freq + phase) * PI.toFloat()) * amp

private fun CacheDrawScope.hillPath(base: Float, amp: Float, freq: Float, phase: Float): Path {
    val w = size.width
    return Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x <= w) {
            lineTo(x, hillY(x, w, base, amp, freq, phase))
            x += 16f
        }
        lineTo(w, hillY(w, w, base, amp, freq, phase))
        lineTo(w, size.height)
        close()
    }
}

private fun DrawScope.drawCloud(c: Offset, r: Float) {
    val cloud = Color.White.copy(alpha = 0.92f)
    drawCircle(cloud, r, c)
    drawCircle(cloud, r * 0.75f, c + Offset(-r * 1.0f, r * 0.25f))
    drawCircle(cloud, r * 0.8f, c + Offset(r * 1.05f, r * 0.2f))
    drawRoundRect(cloud, Offset(c.x - r * 1.6f, c.y + r * 0.1f), Size(r * 3.3f, r * 0.85f), CornerRadius(r * 0.45f))
}

private fun DrawScope.drawRainbow(center: Offset, radius: Float) {
    val bands = listOf(0xFFEF9A9A, 0xFFFFCC80, 0xFFFFF59D, 0xFFA5D6A7, 0xFF90CAF9, 0xFFCE93D8)
    val band = radius * 0.06f
    bands.forEachIndexed { i, c ->
        val r = radius - i * band
        drawArc(Color(c).copy(alpha = 0.55f), 180f, 180f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(band))
    }
}

private fun DrawScope.drawFence(y: Float) {
    val wood = Color(0xFFFFF8E1)
    val shade = Color(0xFFD7CCC8)
    val postW = size.height * 0.022f
    val postH = size.height * 0.09f
    var x = size.width * 0.02f
    drawRect(shade, Offset(0f, y - postH * 0.7f), Size(size.width, postW * 0.6f))
    drawRect(shade, Offset(0f, y - postH * 0.3f), Size(size.width, postW * 0.6f))
    while (x < size.width) {
        drawRoundRect(wood, Offset(x, y - postH), Size(postW, postH), CornerRadius(postW / 2))
        x += size.width / 18f
    }
}

/** A spotlight beam with its top at x = 0. */
private fun CacheDrawScope.spotlightBeam(): Path {
    val w = size.width
    val h = size.height
    return Path().apply {
        moveTo(-w * 0.02f, 0f); lineTo(w * 0.02f, 0f)
        lineTo(w * 0.16f, h * 0.8f); lineTo(-w * 0.16f, h * 0.8f); close()
    }
}

private fun DrawScope.drawSpotlights(beam: Path, sway: Float) {
    val w = size.width
    val brush = Brush.verticalGradient(listOf(Color(0x55FFF9C4), Color(0x10FFF9C4)), endY = size.height * 0.8f)
    listOf(0.3f, 0.7f).forEachIndexed { i, fx ->
        translate(left = w * fx + sway * w * 0.03f * (if (i == 0) 1 else -1)) { drawPath(beam, brush) }
    }
}

private fun DrawScope.drawStage() {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4A148C), Color(0xFF7B1FA2), Color(0xFFAB47BC))))
    // Wooden floor
    drawRect(Color(0xFFBF8A5A), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
    for (i in 1..3) drawLine(Color(0xFF9C6B40), Offset(0f, h * (0.78f + i * 0.055f)), Offset(w, h * (0.78f + i * 0.055f)), 3f)
    drawRect(Color(0xFF8D5A32), Offset(0f, h * 0.775f), Size(w, h * 0.012f))
    // Curtains
    val red = Color(0xFFE53935)
    val dark = Color(0xFFB71C1C)
    for (side in 0..1) {
        val x0 = if (side == 0) 0f else w * 0.88f
        drawRect(red, Offset(x0, 0f), Size(w * 0.12f, h))
        for (k in 0..3) {
            val x = x0 + w * 0.12f * (k + 0.5f) / 4f
            drawLine(dark, Offset(x, 0f), Offset(x, h), strokeWidth = w * 0.006f)
        }
    }
    // Valance with scallops
    drawRect(red, Offset(0f, 0f), Size(w, h * 0.07f))
    val scallops = 14
    for (k in 0 until scallops) {
        val cx = w * (k + 0.5f) / scallops
        drawCircle(red, w / scallops / 2f, Offset(cx, h * 0.07f))
    }
    drawRect(Color(0xFFFFD54F), Offset(0f, h * 0.065f), Size(w, h * 0.008f))
}

private fun DrawScope.drawRoom(wall: Color, stripe: Color, floor: Color, tiles: Boolean = false) {
    val w = size.width
    val h = size.height
    drawRect(wall)
    if (tiles) {
        val s = h * 0.09f
        var y = 0f
        while (y < h * 0.74f) {
            var x = 0f
            while (x < w) {
                drawRect(stripe, Offset(x + 2f, y + 2f), Size(s - 4f, s - 4f))
                x += s
            }
            y += s
        }
    } else {
        var x = 0f
        while (x < w) {
            drawRect(stripe, Offset(x, 0f), Size(w * 0.025f, h * 0.74f))
            x += w * 0.06f
        }
    }
    // Floor and skirting
    drawRect(floor, Offset(0f, h * 0.74f), Size(w, h * 0.26f))
    val plank = floor.copy(red = floor.red * 0.85f, green = floor.green * 0.85f, blue = floor.blue * 0.85f)
    for (i in 1..3) drawLine(plank, Offset(0f, h * (0.74f + i * 0.065f)), Offset(w, h * (0.74f + i * 0.065f)), 3f)
    drawRect(Color.White, Offset(0f, h * 0.725f), Size(w, h * 0.018f))
}
