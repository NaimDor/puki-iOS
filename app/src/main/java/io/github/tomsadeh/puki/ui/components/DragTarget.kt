package io.github.tomsadeh.puki.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LIFTED = 1.12f
private const val LAND_MS = 220

/**
 * Something the child can drag onto a [DropZone]. With [tapToZone], a plain tap works too: the
 * piece flies to that zone by itself, as if dragged there (two-year-olds tap far more than they
 * drag); without it, a tap gets a little shake and [onTap]. Right or wrong is still the game's
 * call, through [onDragEnd]. It lifts the moment it's touched. When [landed] says the zone took
 * it, it shrinks away where it was dropped instead of flying back.
 */
@Composable
fun DragTarget(
    itemId: String,
    modifier: Modifier = Modifier,
    onDragEnd: (dropTargetId: String?) -> Unit = {},
    onDragStart: () -> Unit = {},
    tapToZone: String? = null,
    onTap: () -> Unit = {},
    landed: (dropTargetId: String) -> Boolean = { false },
    content: @Composable () -> Unit
) {
    val dragDropState = LocalDragDropState.current
    val scope = rememberCoroutineScope()
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val lift = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val land = remember { Animatable(1f) }  // 1: here; 0: swallowed by the zone
    var isDragging by remember { mutableStateOf(false) }
    // How far the finger has moved the piece; folded into [offsetAnim] when it lets go
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var pieceSize by remember { mutableStateOf(Size.Zero) }
    val flyTo by rememberUpdatedState(tapToZone)
    val dropped by rememberUpdatedState(onDragEnd)
    val started by rememberUpdatedState(onDragStart)
    val tapped by rememberUpdatedState(onTap)
    val tookIt by rememberUpdatedState(landed)
    val goHome: () -> Unit = {
        val delta = dragDelta
        scope.launch {
            offsetAnim.snapTo(offsetAnim.value + delta)
            dragDelta = Offset.Zero
            offsetAnim.animateTo(Offset.Zero, animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f))
        }
    }
    val settle: () -> Unit = {
        scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
    }
    // After a drop: into the zone, or back home
    val finish: (String?) -> Unit = { zone ->
        settle()
        if (zone != null && tookIt(zone)) scope.launch { land.animateTo(0f, tween(LAND_MS)) } else goHome()
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                rootPosition = coords.positionInRoot()
                pieceSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
            // Lifts on the touch itself, not when a drag starts (only watching: it consumes nothing)
            .pointerInput(itemId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    scope.launch { lift.animateTo(LIFTED, spring(dampingRatio = 0.5f, stiffness = 800f)) }
                    waitForUpOrCancellation(PointerEventPass.Initial)
                    if (!isDragging) settle()
                }
            }
            // The touch handling sits before the movement: measured on the part that stays put,
            // finger movement is exact (inside the moving part, each move would count for less)
            .pointerInput(itemId) {
                detectDragGestures(
                    onDragStart = {
                        started()
                        isDragging = true
                        dragDropState.draggedItemId = itemId
                        dragDropState.draggedSize = pieceSize
                        dragDropState.isDragging = true
                        scope.launch { offsetAnim.stop() }  // grabbed while flying home: stay put
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDelta += dragAmount
                        dragDropState.dragPosition = rootPosition + offsetAnim.value + dragDelta +
                            Offset(size.width / 2f, size.height / 2f)
                    },
                    onDragEnd = {
                        isDragging = false
                        val dropTarget = dragDropState.findDropTarget()
                        dragDropState.reset()
                        dropped(dropTarget)
                        finish(dropTarget)
                    },
                    onDragCancel = {
                        isDragging = false
                        dragDropState.reset()
                        finish(null)
                    }
                )
            }
            .pointerInput(itemId) {
                detectTapGestures(onTap = {
                    val zone = flyTo
                    val target = zone?.let { dragDropState.zoneCenter(it) }
                    if (target == null) {
                        // Nowhere to fly: it still answers
                        tapped()
                        scope.launch {
                            shake.animateTo(0f, keyframes {
                                durationMillis = 300
                                -10f at 60
                                10f at 150
                                -6f at 230
                            })
                        }
                        return@detectTapGestures
                    }
                    if (isDragging || dragDropState.isDragging) return@detectTapGestures
                    val start = rootPosition + Offset(pieceSize.width / 2f, pieceSize.height / 2f)
                    started()
                    isDragging = true
                    dragDropState.draggedItemId = itemId
                    dragDropState.draggedSize = pieceSize
                    dragDropState.isDragging = true
                    scope.launch {
                        offsetAnim.animateTo(target - start, tween(450, easing = FastOutSlowInEasing))
                        dragDropState.dragPosition = target
                        val dropTarget = dragDropState.findDropTarget()
                        isDragging = false
                        dragDropState.reset()
                        dropped(dropTarget)
                        finish(dropTarget)  // a wrong piece bounces back; a right one usually disappears first
                    }
                })
            }
            .absoluteOffset {
                val at = offsetAnim.value + dragDelta
                IntOffset(at.x.roundToInt(), at.y.roundToInt())
            }
            // After the touch handling: inside a scaled layer, finger movement is measured scaled
            // down, and the item would fall behind the finger the farther it's dragged
            .graphicsLayer {
                // Lifted items just grow; a shadow here would be a square box around round things
                val s = lift.value * (0.35f + 0.65f * land.value)
                scaleX = s
                scaleY = s
                alpha = land.value
                translationX = shake.value * density
            }
    ) {
        content()
    }
}
