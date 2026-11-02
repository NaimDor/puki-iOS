package io.github.tomsadeh.puki.data

import androidx.datastore.preferences.core.floatPreferencesKey
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.tomsadeh.puki.data.models.AppSettings
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.data.models.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val GENDER = stringPreferencesKey("gender")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val VOICE_VOLUME = floatPreferencesKey("voice_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val TIME_LIMIT = intPreferencesKey("time_limit_minutes")
        val CHILD_NAME = stringPreferencesKey("child_name")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val COMPANION_ENABLED = booleanPreferencesKey("companion_enabled")
        val BRAIN_ON_DEVICE = booleanPreferencesKey("brain_on_device")
        val ANSWERS_OUT_LOUD = booleanPreferencesKey("answers_out_loud")
        val PLAYED_MS = longPreferencesKey("played_ms")
        val LAST_SEEN = longPreferencesKey("play_last_seen")
        val TIME_UP = booleanPreferencesKey("time_up")
        val HIDDEN_ISLANDS = stringSetPreferencesKey("hidden_islands")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            language = Language.valueOf(prefs[Keys.LANGUAGE] ?: Language.HEBREW.name),
            gender = Gender.valueOf(prefs[Keys.GENDER] ?: Gender.GIRL.name),
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: true,
            musicVolume = prefs[Keys.MUSIC_VOLUME] ?: 1f,
            voiceVolume = prefs[Keys.VOICE_VOLUME] ?: 1f,
            sfxVolume = prefs[Keys.SFX_VOLUME] ?: 1f,
            timeLimitMinutes = prefs[Keys.TIME_LIMIT] ?: 0,
            childName = prefs[Keys.CHILD_NAME] ?: "",
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            loaded = true,
            companionEnabled = prefs[Keys.COMPANION_ENABLED] ?: true,
            brainOnDevice = prefs[Keys.BRAIN_ON_DEVICE] ?: false,
            answersOutLoud = prefs[Keys.ANSWERS_OUT_LOUD] ?: true,
            hiddenIslands = prefs[Keys.HIDDEN_ISLANDS] ?: emptySet()
        )
    }

    suspend fun updateLanguage(language: Language) {
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun updateSound(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun updateMusic(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MUSIC_ENABLED] = enabled }
    }

    suspend fun updateVolumes(music: Float? = null, voice: Float? = null, sfx: Float? = null) {
        context.settingsDataStore.edit { prefs ->
            music?.let { prefs[Keys.MUSIC_VOLUME] = it.coerceIn(0f, 1f) }
            voice?.let { prefs[Keys.VOICE_VOLUME] = it.coerceIn(0f, 1f) }
            sfx?.let { prefs[Keys.SFX_VOLUME] = it.coerceIn(0f, 1f) }
        }
    }

    suspend fun updateTimeLimit(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.TIME_LIMIT] = minutes }
    }

    suspend fun updateChildName(name: String) {
        context.settingsDataStore.edit { it[Keys.CHILD_NAME] = name.trim().take(30) }
    }

    suspend fun setOnboardingDone() {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun updateCompanionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.COMPANION_ENABLED] = enabled }
    }

    suspend fun updateGender(gender: Gender) {
        context.settingsDataStore.edit { it[Keys.GENDER] = gender.name }
    }

    /** The time limit's clock: time played, when the game was last on screen, and whether time was up. */
    data class PlayClock(val playedMs: Long, val lastSeen: Long, val timeUp: Boolean)

    suspend fun playClock(): PlayClock = context.settingsDataStore.data.first().let {
        PlayClock(it[Keys.PLAYED_MS] ?: 0L, it[Keys.LAST_SEEN] ?: 0L, it[Keys.TIME_UP] ?: false)
    }

    suspend fun savePlayClock(clock: PlayClock) {
        context.settingsDataStore.edit {
            it[Keys.PLAYED_MS] = clock.playedMs
            it[Keys.LAST_SEEN] = clock.lastSeen
            it[Keys.TIME_UP] = clock.timeUp
        }
    }

    suspend fun updateHiddenIslands(ids: Set<String>) {
        context.settingsDataStore.edit { it[Keys.HIDDEN_ISLANDS] = ids }
    }

    suspend fun updateAnswersOutLoud(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.ANSWERS_OUT_LOUD] = on }
    }

    suspend fun updateBrainOnDevice(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.BRAIN_ON_DEVICE] = on }
    }
}
