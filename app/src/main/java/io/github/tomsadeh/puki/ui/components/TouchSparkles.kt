package io.github.tomsadeh.puki.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val LIFE_MS = 500L
private const val MAX_SPARKS = 6
private const val POINTS = 5
private val SPARK_COLORS = listOf(Color(0xFFFFEE58), Color.White, Color(0xFFF8BBD0))

internal class Spark(val at: Offset, val born: Long)

/** The sparkles under her finger: every touch answers, even one that misses everything. */
class TouchSparkles internal constructor() {
    internal val sparks = mutableStateListOf<Spark>()
    internal val now = mutableLongStateOf(0L)

    internal fun add(at: Offset) {
        if (sparks.size >= MAX_SPARKS) sparks.removeAt(0)
        sparks.add(Spark(at, SystemClock.uptimeMillis()))
    }
}

@Composable
fun rememberTouchSparkles(): TouchSparkles = remember { TouchSparkles() }

/** Sparkles where this area is touched. It only watches: the touch still goes where it was going. */
fun Modifier.touchSparkles(state: TouchSparkles): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { if (it.changedToDown()) state.add(it.position) }
        }
    }
}

/** Draws the sparkles: put it last in the area with [touchSparkles]. It ticks only while one is alive. */
@Composable
fun SparkleLayer(state: TouchSparkles) {
    LaunchedEffect(state) {
        snapshotFlow { state.sparks.isNotEmpty() }.collectLatest { alive ->
            while (alive) {
                withFrameMillis { }
                val t = SystemClock.uptimeMillis()
                state.now.longValue = t
                state.sparks.removeAll { t - it.born >= LIFE_MS }
            }
        }
    }
    // Its own layer: the scene under it doesn't redraw with the sparkles
    Spacer(
        modifier = Modifier.fillMaxSize().graphicsLayer().drawBehind {
            val now = state.now.longValue
            val reach = 56.dp.toPx()
            val big = 11.dp.toPx()
            state.sparks.forEach { s ->
                val p = ((now - s.born) / LIFE_MS.toFloat()).coerceIn(0f, 1f)
                for (k in 0 until POINTS) {
                    val a = (k * 2 * PI / POINTS + s.born % 7).toFloat()
                    val at = s.at + Offset(cos(a), sin(a)) * (reach * p)
                    drawTwinkle(at, big * (1f - p), SPARK_COLORS[k % SPARK_COLORS.size].copy(alpha = 1f - p * p), p * 360f)
                }
            }
        }
    )
}
