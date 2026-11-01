package io.github.tomsadeh.puki.ui.hub.components

import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.hub.IslandDef
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Size of the whole island composable; HubScreen centers it on the island's position. */
val IslandWidth = 220.dp
val IslandHeight = 230.dp

/** What answers a tap: the disc, the sand and the plank. The rest of the box is water, and the
 *  boxes overlap, so a full box would take the tap meant for the island behind it. */
private val IslandHitShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val unit = h / IslandHeight.value  // (a dp, in this box's pixels)
    addOval(Rect(Offset(w / 2, 72 * unit), 60 * unit))
    addOval(Rect(w * 0.04f, h * 0.48f, w * 0.96f, h * 0.76f))
    addRect(Rect(w * 0.08f, 148 * unit, w * 0.92f, 192 * unit))
}

private val PlankColor = Color(0xFFFFF8E1)
private val PlankBorder = Color(0xFFBC8F5F)
private val PlankText = Color(0xFF5D4037)

@Composable
fun IslandButton(
    island: IslandDef,
    onTap: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    palmOnLeft: Boolean = true
) {
    val language = LocalLanguage.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }

    // Islands bob on the water, each at its own pace so they don't move in lockstep
    val transition = rememberInfiniteTransition(label = "island_${island.id}")
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2200 + (island.id.hashCode() and 0x3FF)),
            RepeatMode.Reverse
        ),
        label = "bob"
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple"
    )
    val sway by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "sway"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "press_scale"
    )

    Box(
        modifier = modifier
            .size(IslandWidth, IslandHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = -hop.value * 16.dp.toPx()
            }
    ) {
        // Water ripple stays put while the island bobs above it
        Spacer(modifier = Modifier.fillMaxSize().drawWithCache {
            val stroke = Stroke(width = 3.dp.toPx())
            onDrawBehind {
                val c = Offset(size.width / 2, size.height * 0.66f)
                for (k in 0..1) {
                    val t = (ripple + k * 0.5f) % 1f
                    drawOval(
                        color = Color.White.copy(alpha = 0.45f * (1f - t)),
                        topLeft = Offset(c.x - size.width * (0.44f + 0.08f * t), c.y - size.height * (0.15f + 0.03f * t)),
                        size = Size(size.width * (0.88f + 0.16f * t), size.height * (0.30f + 0.06f * t)),
                        style = stroke
                    )
                }
            }
        })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = bob * 4.dp.toPx() }
        ) {
            // The sand, grass and palm are built once per size; each frame only redraws them,
            // turning the palm's crown by the sway
            Spacer(modifier = Modifier.fillMaxSize().drawWithCache {
                val base = IslandShapes(size.width, size.height, palmOnLeft)
                onDrawBehind { drawIsland(base, palmOnLeft, sway) }
            })

            // The island's sign: its picture on a colored disc
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .size(108.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(island.color)
                    .border(BorderStroke(5.dp, Color.White), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = island.drawableRes),
                    contentDescription = island.name.en,
                    modifier = Modifier.size(84.dp)
                )
            }

            // Name on a little wooden plank resting on the beach
            Text(
                text = island.name.resolve(language),
                style = MaterialTheme.typography.labelLarge.copy(shadow = null),
                fontWeight = FontWeight.Bold,
                color = PlankText,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlankColor)
                    .border(BorderStroke(2.dp, PlankBorder), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // Last, so it's on top: the only part that takes a tap. A little hop and the island says
        // its name, then off we go
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(IslandHitShape)
                .clickable(interactionSource = interactionSource, indication = null) {
                    onTap()
                    scope.launch {
                        launch {
                            hop.animateTo(1f, tween(90))
                            hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f))
                        }
                        delay(TAP_TO_GO_MS)
                        onClick()
                    }
                }
        )
    }
}

private const val TAP_TO_GO_MS = 150L

/** The island's shapes for a [w] x [h] box: sand, grass, the palm's trunk and one leaf. */
private class IslandShapes(w: Float, h: Float, palmOnLeft: Boolean) {
    val cx = w / 2
    val baseY = h * 0.64f
    private val side = if (palmOnLeft) -1f else 1f
    val trunkBase = Offset(cx + side * w * 0.30f, baseY - h * 0.04f)
    val top = Offset(cx + side * w * 0.40f, baseY - h * 0.42f)
    val trunkStroke = Stroke(width = w * 0.035f, cap = StrokeCap.Round)

    val sand = Path().apply {
        moveTo(cx - w * 0.42f, baseY)
        cubicTo(cx - w * 0.44f, baseY - h * 0.13f, cx + w * 0.40f, baseY - h * 0.15f, cx + w * 0.43f, baseY - h * 0.01f)
        cubicTo(cx + w * 0.45f, baseY + h * 0.10f, cx - w * 0.40f, baseY + h * 0.11f, cx - w * 0.42f, baseY)
        close()
    }
    val sandTop = Path().apply {
        moveTo(cx - w * 0.40f, baseY - h * 0.01f)
        cubicTo(cx - w * 0.42f, baseY - h * 0.13f, cx + w * 0.38f, baseY - h * 0.15f, cx + w * 0.41f, baseY - h * 0.02f)
        cubicTo(cx + w * 0.42f, baseY + h * 0.07f, cx - w * 0.38f, baseY + h * 0.08f, cx - w * 0.40f, baseY - h * 0.01f)
        close()
    }
    val grass = Path().apply {
        moveTo(cx - w * 0.32f, baseY - h * 0.03f)
        cubicTo(cx - w * 0.30f, baseY - h * 0.17f, cx + w * 0.28f, baseY - h * 0.19f, cx + w * 0.31f, baseY - h * 0.04f)
        cubicTo(cx + w * 0.30f, baseY + h * 0.03f, cx - w * 0.30f, baseY + h * 0.04f, cx - w * 0.32f, baseY - h * 0.03f)
        close()
    }
    val trunk = Path().apply {
        moveTo(trunkBase.x, trunkBase.y)
        quadraticBezierTo(trunkBase.x + side * w * 0.01f, baseY - h * 0.25f, top.x, top.y)
    }
    val leaf = Path().apply {
        moveTo(top.x, top.y)
        quadraticBezierTo(top.x + w * 0.08f, top.y - h * 0.05f, top.x + w * 0.16f, top.y + h * 0.02f)
        quadraticBezierTo(top.x + w * 0.08f, top.y - h * 0.005f, top.x, top.y)
        close()
    }
}

private val LEAF_ANGLES = floatArrayOf(-160f, -120f, -60f, -20f, 20f)

/** Sand, grass and a palm tree, drawn in the lower part of the island box. */
private fun DrawScope.drawIsland(s: IslandShapes, palmOnLeft: Boolean, sway: Float) {
    val w = size.width
    val h = size.height
    val cx = s.cx
    val baseY = s.baseY
    // Shallow water glow around the sand
    drawOval(Color(0x5580DEEA), Offset(cx - w * 0.47f, baseY - h * 0.12f), Size(w * 0.94f, h * 0.27f))
    drawPath(s.sand, Color(0xFFE0B972))
    drawPath(s.sandTop, Color(0xFFFFE0A3))
    drawPath(s.grass, Color(0xFF66BB6A))
    drawOval(Color(0xFF81C784), Offset(cx - w * 0.22f, baseY - h * 0.15f), Size(w * 0.30f, h * 0.06f))
    // Little bushes
    drawCircle(Color(0xFF43A047), w * 0.045f, Offset(cx + (if (palmOnLeft) 0.24f else -0.24f) * w, baseY - h * 0.05f))
    drawCircle(Color(0xFF4CAF50), w * 0.035f, Offset(cx + (if (palmOnLeft) 0.29f else -0.29f) * w, baseY - h * 0.03f))
    // Palm tree: the trunk, then the crown turned by the sway
    drawPath(s.trunk, Color(0xFF8D6E63), style = s.trunkStroke)
    val top = s.top
    rotate(sway, top) {
        for (angle in LEAF_ANGLES) rotate(angle, top) { drawPath(s.leaf, Color(0xFF2E7D32)) }
        drawCircle(Color(0xFF6D4C41), w * 0.018f, Offset(top.x - w * 0.012f, top.y + h * 0.012f))
        drawCircle(Color(0xFF6D4C41), w * 0.018f, Offset(top.x + w * 0.014f, top.y + h * 0.014f))
    }
}
