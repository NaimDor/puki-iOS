package io.github.tomsadeh.puki

import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import io.github.tomsadeh.puki.audio.Companion
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.tomsadeh.puki.audio.MusicPlayer
import io.github.tomsadeh.puki.audio.SfxPlayer
import io.github.tomsadeh.puki.audio.VoicePlayer

class MainActivity : ComponentActivity() {

    private lateinit var sfxPlayer: SfxPlayer
    private lateinit var musicPlayer: MusicPlayer
    private lateinit var voicePlayer: VoicePlayer
    private lateinit var companion: Companion
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sfxPlayer = SfxPlayer(this)
        musicPlayer = MusicPlayer(this)
        voicePlayer = VoicePlayer(this)
        companion = Companion(this, voicePlayer, appScope)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val app = application as PukiApplication

        setContent {
            PukiApp(
                sfxPlayer = sfxPlayer,
                musicPlayer = musicPlayer,
                voicePlayer = voicePlayer,
                companion = companion,
                settingsRepository = app.settingsRepository
            )
        }
    }

    override fun onPause() {
        super.onPause()
        musicPlayer.pause()
    }

    override fun onResume() {
        super.onResume()
        musicPlayer.resume()
    }

    // Away from the screen, Puki is silent: no music coming back, no lines, no late replies
    // (the games themselves wait too: PukiApp pauses them while off screen)
    override fun onStart() {
        super.onStart()
        musicPlayer.foreground = true
        voicePlayer.foreground = true
        companion.backgrounded = false
    }

    override fun onStop() {
        super.onStop()
        musicPlayer.foreground = false
        voicePlayer.foreground = false
        voicePlayer.stop()
        companion.backgrounded = true
    }

    override fun onDestroy() {
        super.onDestroy()
        sfxPlayer.release()
        musicPlayer.release()
        voicePlayer.release()
        appScope.cancel()
    }
}
