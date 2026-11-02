package io.github.tomsadeh.puki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.github.tomsadeh.puki.brain.LocalBrain
import io.github.tomsadeh.puki.brain.DeviceModels
import android.app.Application
import io.github.tomsadeh.puki.data.ProgressRepository
import io.github.tomsadeh.puki.data.SettingsRepository

class PukiApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set
    /** Puki's brain on this device, and the models it needs (only used on capable phones). */
    val deviceModels by lazy { DeviceModels(this) }
    private val brainHolder = lazy { LocalBrain(deviceModels) }
    val localBrain by brainHolder
    /** For work that outlives a screen (the device brain's download). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The device brain was put to sleep while the game was away; the game wakes it on its return. */
    @Volatile var brainSlept = false

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        progressRepository = ProgressRepository(this)
        // Left by older versions, like reports.jsonl and the pack folder (see Companion)
        appScope.launch(Dispatchers.IO) { runCatching { settingsRepository.forgetPcBrain() } }
    }

    /**
     * Gives the device brain's models back (gigabytes of them) while the game is away. Off the
     * main thread: it waits for an answer in progress.
     */
    fun sleepBrain() {
        if (!brainHolder.isInitialized()) return
        brainSlept = true
        appScope.launch(Dispatchers.IO) { runCatching { localBrain.close() } }
    }

    @Suppress("DEPRECATION")  // (still sent in the background, which is the case that matters)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) sleepBrain()
    }
}
