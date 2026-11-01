package io.github.tomsadeh.puki.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import kotlin.random.Random

/**
 * Lays its children out loosely instead of in a row: high and low in turn, nudged a little
 * each round ([seed]) so no two rounds look the same. Order is kept (first child first, in
 * reading direction), so counting still goes one by one.
 *
 * With [aroundCenter] the middle is left free (for a target), and the children stand
 * half on one side of it and half on the other.
 */
@Composable
fun Scatter(
    seed: Int,
    modifier: Modifier = Modifier,
    aroundCenter: Boolean = false,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val w = constraints.maxWidth
        val tallest = placeables.maxOfOrNull { it.height } ?: 0
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else (tallest * 1.7f).toInt()
        val n = placeables.size
        val centers = scatterCenters(n, seed, aroundCenter, flat = h < tallest * 1.5f)

        layout(w, h) {
            placeables.forEachIndexed { i, p ->
                val (fx, fy) = centers[i]
                val x = (w * fx - p.width / 2f).toInt().coerceIn(0, (w - p.width).coerceAtLeast(0))
                val y = (h * fy - p.height / 2f).toInt().coerceIn(0, (h - p.height).coerceAtLeast(0))
                p.placeRelative(x, y)  // mirrors in Hebrew, so the first one is where reading starts
            }
        }
    }
}

/**
 * Where [n] scattered things stand, as fractions of the area. [flat]: the area is too short for
 * high and low, so they stand in a line.
 */
internal fun scatterCenters(n: Int, seed: Int, aroundCenter: Boolean, flat: Boolean): List<Pair<Float, Float>> {
    val rnd = Random(seed * 7919 + n)
    val flip = rnd.nextBoolean()  // whether the first one starts high or low
    return if (flat && aroundCenter) {
        // In a line on each side of the target, instead of on top of each other
        val left = (n + 1) / 2
        List(n) { i ->
            val onLeft = i < left
            val k = if (onLeft) i else i - left
            (if (onLeft) 0.08f + k * 0.14f else 0.92f - k * 0.14f) to 0.5f
        }
    } else if (!aroundCenter) {
        List(n) { i ->
            // (the first one never starts low: that's Puki's corner)
            val high = i == 0 || (i % 2 == 0) != flip
            val x = (i + 0.5f) / n + (rnd.nextFloat() - 0.5f) * 0.25f / n
            val y = if (flat) 0.5f else (if (high) 0.3f else 0.7f) + (rnd.nextFloat() - 0.5f) * 0.12f
            x to y
        }
    } else {
        val left = (n + 1) / 2
        List(n) { i ->
            val onLeft = i < left
            val k = if (onLeft) i else i - left
            val m = if (onLeft) left else n - left
            val x = (if (onLeft) 0.15f else 0.85f) + (if (k % 2 == 0) -0.04f else 0.04f) + (rnd.nextFloat() - 0.5f) * 0.04f
            val y = (k + 0.5f) / m + (rnd.nextFloat() - 0.5f) * 0.1f
            x to y
        }
    }
}

/** A small tilt for the [index]th scattered thing, different each round. */
fun scatterTilt(seed: Int, index: Int): Float = Random(seed * 131 + index).nextFloat() * 16f - 8f

/** A random number picked once per visit to a screen: added to the round, so round 1 doesn't
 *  look the same every time she plays. */
@Composable
fun rememberSessionSeed(): Int = remember { Random.nextInt(100_000) }
