package io.github.tomsadeh.puki.ui.common

import io.github.tomsadeh.puki.audio.Companion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import io.github.tomsadeh.puki.audio.MusicPlayer
import io.github.tomsadeh.puki.audio.SfxPlayer
import io.github.tomsadeh.puki.audio.VoicePlayer
import io.github.tomsadeh.puki.data.models.AppSettings
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.components.ReactionState

val LocalLanguage = compositionLocalOf { Language.HEBREW }
val LocalGender = compositionLocalOf { Gender.GIRL }
val LocalAppSettings = compositionLocalOf { AppSettings() }
val LocalSfxPlayer = compositionLocalOf<SfxPlayer> { error("No SfxPlayer provided") }
val LocalMusicPlayer = compositionLocalOf<MusicPlayer> { error("No MusicPlayer provided") }
val LocalCompanion = compositionLocalOf<Companion?> { null }

/** Opens the sticker album, from an end card that's just given her a sticker. */
val LocalOpenAlbum = compositionLocalOf<(() -> Unit)?> { null }
val LocalVoicePlayer = compositionLocalOf<VoicePlayer> { error("No VoicePlayer provided") }
/** True while the game is paused under the time limit's goodbye: no nudges, no listening. */
val LocalGamePaused = compositionLocalOf { false }

/**
 * The game's pause, readable from a running loop: `val paused = rememberGamePaused()`, then
 * `awaitUnpaused(paused)` before each step, so nothing carries on under the goodbye.
 */
@Composable
fun rememberGamePaused(): State<Boolean> = rememberUpdatedState(LocalGamePaused.current)

/** Waits until the game isn't paused (returns at once if it isn't). */
suspend fun awaitUnpaused(paused: State<Boolean>) {
    if (paused.value) snapshotFlow { paused.value }.first { !it }
}

/** Puki in the corner of the current game: confetti makes him hop, a miss makes him tilt his head. */
val LocalPukiMood = compositionLocalOf<ReactionState?> { null }

/** Resolves to the current language, and to the child's gender for Hebrew lines that have one. */
@Composable
fun BilingualText.localized(): String = resolve(LocalLanguage.current, LocalGender.current)
