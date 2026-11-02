package io.github.tomsadeh.puki.data.models

data class AppSettings(
    val language: Language = Language.HEBREW,
    val gender: Gender = Gender.GIRL,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    /** Volumes, 0..1: music, voices (recorded lines and Puki), sound effects. */
    val musicVolume: Float = 1f,
    val voiceVolume: Float = 1f,
    val sfxVolume: Float = 1f,
    val timeLimitMinutes: Int = 0, // 0 = no limit
    val childName: String = "",
    val onboardingDone: Boolean = false,
    /** False until the stored settings have been read (so the welcome screen doesn't flash). */
    val loaded: Boolean = false,
    val companionEnabled: Boolean = true,
    /** Puki thinks on this device (models downloaded); off, he plays his recorded lines. */
    val brainOnDevice: Boolean = false,
    /** After a game's question, Puki listens a few seconds for the answer said out loud. */
    val answersOutLoud: Boolean = true,
    /** Islands a parent took off the map (fewer choices at two), by id. */
    val hiddenIslands: Set<String> = emptySet()
)

enum class Language {
    HEBREW, ENGLISH
}

enum class Gender {
    BOY, GIRL
}
