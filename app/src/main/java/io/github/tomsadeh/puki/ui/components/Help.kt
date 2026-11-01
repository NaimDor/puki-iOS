package io.github.tomsadeh.puki.ui.components

import android.os.SystemClock
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import io.github.tomsadeh.puki.ui.common.LocalPukiMood
import kotlinx.coroutines.launch
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** How long a quiet moment lasts before Puki asks again. */
private const val QUIET_MS = 8_000L

/**
 * Help for a toddler who's stuck: notices quiet moments and misses, and says when to show
 * the answer. One per round (see [rememberHelp]).
 */
class Help internal constructor() {
    /** True once the right answer should wiggle and glow. */
    var showHint by mutableStateOf(false)
        internal set
    internal var lastTouch = SystemClock.uptimeMillis()
    private var misses = 0
    internal var onMiss: () -> Unit = {}

    fun touched() {
        lastTouch = SystemClock.uptimeMillis()
    }

    /** A wrong try: after two, show the answer. */
    fun missed() {
        touched()
        onMiss()
        if (++misses >= 2) showHint = true
    }
}

/**
 * Help for this round ([key]): after a quiet moment, [onNudge] (repeat the question); after a
 * second one, or two misses, [Help.showHint]. It stops nudging after three, so it never nags.
 * [active] pauses it (e.g. while the round is being celebrated).
 */
@Composable
fun rememberHelp(key: Any?, active: Boolean = true, onNudge: () -> Unit): Help {
    val help = remember(key) { Help() }
    val nudge by rememberUpdatedState(onNudge)
    val mood = LocalPukiMood.current
    val scope = rememberCoroutineScope()
    help.onMiss = { scope.launch { mood?.oops() } }
    val paused = LocalGamePaused.current  // (the time limit's goodbye is up: no "where's the red one?" over it)
    LaunchedEffect(key, active, paused) {
        if (!active || paused) return@LaunchedEffect
        help.touched()  // the clock starts when the round does
        var nudges = 0
        while (nudges < 3) {
            delay(500)
            if (SystemClock.uptimeMillis() - help.lastTouch >= QUIET_MS) {
                nudges++
                help.touched()
                nudge()
                if (nudges >= 2) help.showHint = true
            }
        }
    }
    return help
}

/** Counts any touch on this area as the child being busy (put it on the game's root). */
fun Modifier.noticeTouches(help: Help): Modifier = pointerInput(help) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)  // just watching, never taking the touch
            if (event.type == PointerEventType.Press) help.touched()
        }
    }
}

/** The answer gently wiggling and glowing, when [on]. */
fun Modifier.hintGlow(on: Boolean): Modifier = if (!on) this else composed {
    val t = rememberInfiniteTransition(label = "hint")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "hint_phase")
    this
        .drawBehind {
            val pulse = 0.5f + 0.5f * sin(phase * 2 * PI.toFloat())
            // A warm halo that reaches past the piece's edge (most pieces sit on a plate or block)
            val r = size.maxDimension * (0.7f + 0.06f * pulse)
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFFFEB3B).copy(alpha = 0.9f),
                    0.72f to Color(0xFFFFEB3B).copy(alpha = 0.75f + 0.2f * pulse),
                    1f to Color.Transparent,
                    center = center, radius = r
                ),
                radius = r
            )
        }
        .graphicsLayer {
            val a = phase * 2 * PI.toFloat()
            rotationZ = sin(a * 2) * 7f
            val s = 1f + 0.06f * sin(a)
            scaleX = s
            scaleY = s
        }
}
