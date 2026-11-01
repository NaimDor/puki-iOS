package io.github.tomsadeh.puki.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SfxPlayer(private val context: Context) {

    var enabled = true

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedSounds = java.util.concurrent.ConcurrentHashMap<String, Int>()  // (filled off the main thread)

    fun preload(name: String, resId: Int) {
        loadedSounds[name] = soundPool.load(context, resId, 1)
    }

    /** The parent's sound-effects volume, 0..1 (parent settings). */
    var level = 1f

    /** [rate] plays it faster or slower (0.5..2), which also raises or lowers its pitch. */
    fun play(sfx: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!enabled || level <= 0f) return
        loadedSounds[sfx.name]?.let { soundId ->
            soundPool.play(soundId, volume * level, volume * level, 1, 0, rate)
        }
    }

    fun release() {
        soundPool.release()
    }
}
