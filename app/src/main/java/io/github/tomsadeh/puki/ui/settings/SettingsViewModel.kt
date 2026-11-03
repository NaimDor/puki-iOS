package io.github.tomsadeh.puki.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.data.models.BrainLanguages
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.data.models.Language
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as PukiApplication).settingsRepository

    fun toggleLanguage(current: Language) {
        viewModelScope.launch {
            val next = when (current) {
                Language.HEBREW -> Language.ENGLISH
                Language.ENGLISH -> Language.HEBREW
            }
            settingsRepository.updateLanguage(next)
        }
    }

    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSound(!enabled) }
    }

    fun toggleMusic(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMusic(!enabled) }
    }

    fun toggleGender(current: Gender) {
        viewModelScope.launch {
            val next = when (current) {
                Gender.GIRL -> Gender.BOY
                Gender.BOY -> Gender.GIRL
            }
            settingsRepository.updateGender(next)
        }
    }

    fun setChildName(name: String) {
        viewModelScope.launch { settingsRepository.updateChildName(name) }
    }

    fun setLanguage(language: Language) {
        viewModelScope.launch { settingsRepository.updateLanguage(language) }
    }

    fun setGender(gender: Gender) {
        viewModelScope.launch { settingsRepository.updateGender(gender) }
    }

    fun setCompanionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateCompanionEnabled(enabled) }
    }

    fun finishOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    fun toggleCompanion(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateCompanionEnabled(!enabled) }
    }

    fun setVolumes(music: Float? = null, voice: Float? = null, sfx: Float? = null) {
        viewModelScope.launch { settingsRepository.updateVolumes(music, voice, sfx) }
    }

    // ---- Puki's brain on this device ----

    private val app = application as PukiApplication
    val deviceModels get() = app.deviceModels
    var downloadError by mutableStateOf<String?>(null)
        private set

    fun downloadDeviceBrain(langs: BrainLanguages) {
        downloadError = null
        // In the app's scope, so it carries on when the parent leaves settings (or the welcome screen).
        // Once it's all in, Puki starts thinking on the device: that's what the parent asked for
        app.appScope.launch {
            try {
                app.deviceModels.download(langs)
                settingsRepository.updateBrainOnDevice(true)
            } catch (e: Exception) {
                downloadError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun setIslandShown(id: String, shown: Boolean, hidden: Set<String>) {
        viewModelScope.launch { settingsRepository.updateHiddenIslands(if (shown) hidden - id else hidden + id) }
    }

    fun setAnswersOutLoud(on: Boolean) {
        viewModelScope.launch { settingsRepository.updateAnswersOutLoud(on) }
    }

    fun setRememberBetween(on: Boolean) {
        viewModelScope.launch { settingsRepository.updateRememberBetween(on) }
    }

    fun setBrainOnDevice(on: Boolean) {
        viewModelScope.launch { settingsRepository.updateBrainOnDevice(on) }
    }

    /** Which languages the brain is for; one already downloaded that's no longer picked is freed. */
    fun setBrainLanguages(langs: BrainLanguages) {
        viewModelScope.launch {
            settingsRepository.updateBrainLanguages(langs)
            // (nothing to fetch: download() then only frees the reader that wasn't picked)
            if (app.deviceModels.ready(langs)) app.appScope.launch { runCatching { app.deviceModels.download(langs) } }
        }
    }

    fun removeDeviceBrain() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            settingsRepository.updateBrainOnDevice(false)
            settingsRepository.updateRememberBetween(false)  // (and what Puki remembered goes with him)
            runCatching { app.localBrain.close() }
            app.deviceModels.delete()
        }
    }

    fun cycleTimeLimit(current: Int) {
        val options = listOf(0, 15, 30, 45, 60)
        val nextIndex = (options.indexOf(current) + 1) % options.size
        viewModelScope.launch { settingsRepository.updateTimeLimit(options[nextIndex]) }
    }
}
