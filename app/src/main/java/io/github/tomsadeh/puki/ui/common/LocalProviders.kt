package io.github.tomsadeh.puki.ui.common

import io.github.tomsadeh.puki.audio.Companion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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

/** Puki in the corner of the current game: confetti makes him hop, a miss makes him tilt his head. */
val LocalPukiMood = compositionLocalOf<ReactionState?> { null }

/** Resolves to the current language, and to the child's gender for Hebrew lines that have one. */
@Composable
fun BilingualText.localized(): String = resolve(LocalLanguage.current, LocalGender.current)
