package io.github.tomsadeh.puki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import io.github.tomsadeh.puki.R
import kotlinx.coroutines.delay
import kotlin.random.Random

private val LID = Color(0xFF4CAF50)
private val LID_LINE = Color(0xFF2E7D32)

/**
 * Puki's picture, flapping his beak while [talking]: both pictures are drawn, one hidden, and only
 * their alpha flips (read in the draw layer), so a flap every [flapMs] redraws without recomposing.
 * He blinks now and then, a lid drawn over his eye the same way.
 */
@Composable
fun TalkingParrotImage(talking: Boolean, modifier: Modifier = Modifier, flapMs: Long = 140) {
    val beakOpen = remember { mutableStateOf(false) }
    LaunchedEffect(talking) {
        beakOpen.value = false
        while (talking) {
            beakOpen.value = !beakOpen.value
            delay(flapMs)
        }
        beakOpen.value = false
    }
    val blink = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2500, 6000))
            blink.value = true
            delay(130)
            blink.value = false
        }
    }
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.ic_companion_parrot),
            contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = if (beakOpen.value) 0f else 1f }
        )
        Image(
            painter = painterResource(R.drawable.ic_companion_parrot_talk),
            contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = if (beakOpen.value) 1f else 0f }
        )
        Spacer(
            modifier = Modifier.matchParentSize().drawBehind {
                if (blink.value) {
                    val u = size.width / 100  // (the pictures are drawn on a 100 x 100 viewport)
                    drawCircle(LID, 8.8f * u, Offset(40 * u, 28 * u))
                    drawLine(LID_LINE, Offset(32 * u, 29 * u), Offset(48 * u, 29 * u), 1.6f * u)
                }
            }
        )
    }
}
