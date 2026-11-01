package io.github.tomsadeh.puki

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.ui.onboarding.OnboardingScreen
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.audio.Companion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.tomsadeh.puki.ui.components.LocalAmbientClock
import io.github.tomsadeh.puki.ui.components.AmbientClock
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import io.github.tomsadeh.puki.audio.MusicPlayer
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.audio.SfxPlayer
import io.github.tomsadeh.puki.audio.VoicePlayer
import io.github.tomsadeh.puki.data.ProgressRepository
import io.github.tomsadeh.puki.data.SettingsRepository
import io.github.tomsadeh.puki.ui.common.LocalGamePaused
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.navigation.PukiNavGraph
import io.github.tomsadeh.puki.theme.PukiTheme
import io.github.tomsadeh.puki.ui.common.AppViewModel
import io.github.tomsadeh.puki.ui.common.LocalAppSettings
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalGender
import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.TimeLimitOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PukiApp(
    sfxPlayer: SfxPlayer,
    musicPlayer: MusicPlayer,
    voicePlayer: VoicePlayer,
    companion: Companion,
    settingsRepository: SettingsRepository
) {
    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.Factory(settingsRepository)
    )
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val language = settings.language
    val layoutDirection = when (language) {
        Language.HEBREW -> LayoutDirection.Rtl
        Language.ENGLISH -> LayoutDirection.Ltr
    }

    LaunchedEffect(Unit) {
        Sfx.preloadAll(sfxPlayer)
        // Optional tracks: app/src/main/res/raw/music_hub.mp3 (and music_night.mp3 for bedtime)
        musicPlayer.playTrack("music_hub")
    }

    LaunchedEffect(settings.musicVolume, settings.voiceVolume, settings.sfxVolume) {
        musicPlayer.level = settings.musicVolume
        voicePlayer.level = settings.voiceVolume
        sfxPlayer.level = settings.sfxVolume
    }

    LaunchedEffect(settings.soundEnabled) {
        sfxPlayer.enabled = settings.soundEnabled
        voicePlayer.enabled = settings.soundEnabled
    }

    val pukiApp = LocalContext.current.applicationContext as PukiApplication
    LaunchedEffect(settings.companionEnabled, settings.companionAddress, settings.companionCode, settings.companionPin, language) {
        companion.enabled = settings.companionEnabled
        companion.address = settings.companionAddress
        companion.code = settings.companionCode
        companion.pin = settings.companionPin
        companion.onPinLearned = { pin -> pukiApp.appScope.launch { pukiApp.settingsRepository.learnCompanionPin(pin) } }
        companion.language = language
    }
    LaunchedEffect(settings.answersOutLoud) { companion.answersOutLoud = settings.answersOutLoud }

    // Where Puki thinks: the PC, or this device once its models are downloaded
    val context = LocalContext.current
    LaunchedEffect(settings.brainOnDevice) {
        val app = context.applicationContext as PukiApplication
        // Only touched when switched on, and never allowed to stop the game from opening:
        // if anything about it fails, Puki simply stays on the PC
        val brain = if (settings.brainOnDevice) runCatching {
            // About two dozen file checks: off the main thread
            app.localBrain.takeIf { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.deviceModels.ready() } }
        }.onFailure { android.util.Log.e("PukiBrain", "device brain unavailable", it) }.getOrNull() else null
        val onDevice = brain != null
        if (onDevice) companion.deviceBrain = brain
        if (onDevice != companion.onDevice) {
            companion.onDevice = onDevice
            if (!onDevice && companion.deviceBrain != null) {
                // Gives the memory back; off the main thread, as it waits for an answer in progress
                app.appScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { app.localBrain.close() } }
            }
            companion.recheck()
        }
    }

    LaunchedEffect(settings.childName, settings.gender) {
        companion.setChild(settings.childName, settings.gender == Gender.GIRL)
    }

    LaunchedEffect(settings.gender) {
        voicePlayer.gender = settings.gender
    }

    LaunchedEffect(settings.musicEnabled) {
        musicPlayer.setEnabled(settings.musicEnabled)
    }

    // Time limit: counts only the time the game is on screen, not while it sits in the background.
    // The clock is saved, so swiping the app away (or Android closing it) doesn't start a new
    // allowance: that takes an hour away from the game, or a parent ending the goodbye
    var showTimeLimit by remember { mutableStateOf(false) }
    var playedMs by remember { mutableLongStateOf(0L) }
    var clockLoaded by remember { mutableStateOf(false) }
    var onScreen by remember { mutableStateOf(true) }
    val clockApp = LocalContext.current.applicationContext as PukiApplication
    fun saveClock() {
        val clock = SettingsRepository.PlayClock(playedMs, System.currentTimeMillis(), showTimeLimit)
        clockApp.appScope.launch { clockApp.settingsRepository.savePlayClock(clock) }
    }
    LaunchedEffect(Unit) {
        val clock = clockApp.settingsRepository.playClock()
        val rested = System.currentTimeMillis() - clock.lastSeen > REST_FOR_NEW_ALLOWANCE_MS
        playedMs = if (rested) 0L else clock.playedMs
        showTimeLimit = !rested && clock.timeUp
        clockLoaded = true
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) onScreen = true
            if (event == Lifecycle.Event.ON_STOP) onScreen = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A parent changing the limit starts a new allowance (not the settings being read at start)
    var limitSeen by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(settings.timeLimitMinutes, settings.loaded, clockLoaded) {
        if (!settings.loaded || !clockLoaded) return@LaunchedEffect
        if (settings.timeLimitMinutes <= 0 && showTimeLimit) {
            showTimeLimit = false  // the limit was turned off since time was up
            saveClock()
        }
        if (limitSeen != null && limitSeen != settings.timeLimitMinutes) {
            playedMs = 0L
            showTimeLimit = false
            saveClock()
        }
        limitSeen = settings.timeLimitMinutes
    }
    LaunchedEffect(settings.timeLimitMinutes, onScreen, clockLoaded) {
        if (!clockLoaded || settings.timeLimitMinutes <= 0) return@LaunchedEffect
        if (!onScreen) {
            saveClock()  // when it went away: the hour's rest counts from here
            return@LaunchedEffect
        }
        while (true) {
            delay(5_000)
            if (!showTimeLimit) {
                playedMs += 5_000
                if (playedMs >= settings.timeLimitMinutes * 60_000L) showTimeLimit = true
            }
            saveClock()  // (also while the goodbye is up: it's still on screen, not resting)
        }
    }
    LaunchedEffect(showTimeLimit) { companion.paused = showTimeLimit }

    val ambientClock = remember { AmbientClock() }
    LaunchedEffect(ambientClock) { ambientClock.run() }

    PukiTheme {
        CompositionLocalProvider(
            LocalAmbientClock provides ambientClock,
            LocalLanguage provides language,
            LocalGender provides settings.gender,
            LocalAppSettings provides settings,
            LocalSfxPlayer provides sfxPlayer,
            LocalMusicPlayer provides musicPlayer,
            LocalVoicePlayer provides voicePlayer,
            LocalCompanion provides companion,
            LocalGamePaused provides showTimeLimit,
            LocalLayoutDirection provides layoutDirection
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (settings.loaded && !settings.onboardingDone) {
                    OnboardingScreen(settings)
                } else if (settings.loaded) {
                    val navController = rememberNavController()
                    PukiNavGraph(navController = navController)
                }

                if (showTimeLimit) {
                    TimeLimitOverlay(
                        onDismiss = {
                            showTimeLimit = false
                            playedMs = 0L  // a new allowance starts counting
                            saveClock()
                        }
                    )
                }
            }
        }
    }
}

/** An hour away from the game starts a new time-limit allowance. */
private const val REST_FOR_NEW_ALLOWANCE_MS = 60 * 60 * 1000L
