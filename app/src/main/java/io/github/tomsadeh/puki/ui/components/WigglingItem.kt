package io.github.tomsadeh.puki.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

private const val WIGGLE_SECONDS = 1.2f

@Composable
fun WigglingItem(
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val clock = LocalAmbientClock.current
    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = if (enabled) clock.wave(WIGGLE_SECONDS) * 3f else 0f
        }
    ) {
        content()
    }
}
