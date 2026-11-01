package io.github.tomsadeh.puki.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.audio.SfxPlayer
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PARTY_MS = 1800L
private const val QUIET_MS = 1200L

/**
 * The skeleton of a game of a few steps (washing the car, brushing Teddy's teeth, baking, going
 * to bed): which step it's on, the stars, the party after a step, and the end.
 */
@Stable
class StepGame<S>(
    first: S,
    private val steps: Int,
    private val session: GameSession,
    private val sfxPlayer: SfxPlayer,
    private val scope: CoroutineScope
) {
    var step by mutableStateOf(first)
        private set
    var stars by mutableIntStateOf(0)
        private set
    var celebrate by mutableStateOf(false)
        private set
    /** Right after a step, before the next one starts. */
    var betweenSteps by mutableStateOf(false)
        private set
    var sessionComplete by mutableStateOf(false)
        private set

    /** A step done: a star, a little party ([party] false for a quiet chime), and on to [next]. */
    fun done(next: S?, party: Boolean = true) {
        stars++
        session.star()
        sfxPlayer.play(if (party) Sfx.SUCCESS else Sfx.STAR_COLLECT, if (party) 1f else 0.6f)
        celebrate = party
        betweenSteps = true
        scope.launch {
            delay(if (party) PARTY_MS else QUIET_MS)
            celebrate = false
            betweenSteps = false
            if (next != null) step = next
        }
    }

    /** The game's over: the end card. */
    fun finish() {
        session.finish(steps)
        sessionComplete = true
    }
}

@Composable
fun <S> rememberStepGame(first: S, steps: Int, session: GameSession): StepGame<S> {
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    return remember { StepGame(first, steps, session, sfxPlayer, scope) }
}

/** A tool on a round plate, for a step that starts with a tap (the hose, the toothpaste...). */
@Composable
fun ToolButton(
    size: Dp,
    hint: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE1F5FE),
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .popIn()
            .idleBob(1)
            .hintGlow(hint)
            .size(size)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
