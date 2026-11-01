package io.github.tomsadeh.puki.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.tomsadeh.puki.audio.Companion
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A round of a pick-one game: [won] while the win is being celebrated (the confetti goes by it). */
@Stable
class RoundFlow internal constructor(private val scope: CoroutineScope, private val companion: Companion?) {
    var won by mutableStateOf(false)
        private set

    /** The round is won: a party of [quietMs], or [talkingMs] if Puki will have something to say, then [next]. */
    fun win(quietMs: Long, talkingMs: Long, next: suspend () -> Unit) {
        won = true
        scope.launch {
            delay(if (companion?.isReady == true) talkingMs else quietMs)
            won = false
            next()
        }
    }
}

@Composable
fun rememberRoundFlow(): RoundFlow {
    val scope = rememberCoroutineScope()
    val companion = LocalCompanion.current
    return remember { RoundFlow(scope, companion) }
}
