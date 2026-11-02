package io.github.tomsadeh.puki.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.tomsadeh.puki.brain.SpokenAnswer
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay

/**
 * Lets the child answer the round's question out loud instead of tapping: once the question has
 * been asked and everyone is quiet, Puki listens for a few seconds (his corner glows). If she said
 * one of [options] (each: the words that count for it), [onAnswer] gets its index, just as if
 * she'd tapped it; after a wrong one Puki listens once more. Silence or other words end it, and
 * tapping works all along (a tap that ends the round turns the microphone off at once).
 * Needs the microphone, the device brain awake to hear and the parent's setting.
 */
@Composable
fun AnswerOutLoud(
    key: Any?,
    options: List<List<String>>,
    active: Boolean,
    askedAfterMs: Long = 800,  // how long until the question starts (the round's prompt waits a breath)
    onAnswer: (Int) -> Unit
) {
    val companion = LocalCompanion.current ?: return
    val voicePlayer = LocalVoicePlayer.current
    val answer by rememberUpdatedState(onAnswer)
    val choices by rememberUpdatedState(options)
    val paused = LocalGamePaused.current
    LaunchedEffect(key, active, paused) {
        if (!active || paused) return@LaunchedEffect
        repeat(2) { tries ->
            // Wait for the question (or the "try again") to be said, and a breath after it
            delay(if (tries == 0) askedAfterMs else 800)
            var quiet = 0
            while (quiet < 3) {
                delay(150)
                quiet = if (voicePlayer.isPlaying() || companion.speaking.value) 0 else quiet + 1
            }
            val heard = companion.listenForAnswer(choices.flatten()) ?: return@LaunchedEffect
            val picked = SpokenAnswer.pick(heard, choices)
            if (picked < 0) return@LaunchedEffect
            answer(picked)
        }
    }
}
