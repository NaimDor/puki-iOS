package io.github.tomsadeh.puki.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.MediaPlayer

/**
 * Background music. Tracks are optional files in res/raw (music_hub, music_night):
 * a missing track just means silence. Switching tracks fades out and in.
 */
class MusicPlayer(val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var enabled = true
    private var currentTrack: String? = null
    private var ducked = false
    private var currentVolume = 0f
    private var prepared = false  // a track loads in the background; until then it can't start

    /** The parent's music volume, 0..1 (parent settings). */
    var level = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            fadeTo(mediaPlayer, target())
        }

    private fun target() = (if (ducked) DUCKED_VOLUME else BASE_VOLUME) * level
    private var fade: ValueAnimator? = null

    /** Plays res/raw/[name] on a loop, fading over from whatever was playing. */
    fun playTrack(name: String) {
        if (name == currentTrack && mediaPlayer != null) return
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resId == 0) return
        currentTrack = name
        val old = mediaPlayer
        mediaPlayer = null
        prepared = false
        fadeTo(old, 0f) {
            old?.release()
            if (currentTrack != name) return@fadeTo
            val player = runCatching {
                MediaPlayer().apply {
                    context.resources.openRawResourceFd(resId).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    isLooping = true
                    setVolume(0f, 0f)
                }
            }.getOrNull() ?: return@fadeTo
            mediaPlayer = player
            currentVolume = 0f
            player.setOnPreparedListener {
                if (mediaPlayer !== it) return@setOnPreparedListener  // another track was picked meanwhile
                prepared = true
                if (enabled) it.start()
                fadeTo(it, target())
            }
            player.prepareAsync()  // (not on the main thread: a long track would stall the screen opening)
        }
    }

    /** Quieter while something important is spoken (e.g. a bedtime story). */
    fun duck(ducked: Boolean) {
        this.ducked = ducked
        fadeTo(mediaPlayer, target())
    }

    private fun fadeTo(player: MediaPlayer?, target: Float, then: () -> Unit = {}) {
        fade?.cancel()
        if (player == null) {
            then()
            return
        }
        fade = ValueAnimator.ofFloat(currentVolume, target).apply {
            duration = FADE_MILLIS
            // About ten steps a second: each volume change is a call into the system
            var step = -1
            addUpdateListener {
                val v = it.animatedValue as Float
                currentVolume = v
                val now = (it.animatedFraction * FADE_MILLIS / 100).toInt()
                if (now != step || it.animatedFraction >= 1f) {
                    step = now
                    runCatching { player.setVolume(v, v) }
                }
            }
            // Runs even when a newer fade cancels this one, so an old track is always released
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = then()
            })
            start()
        }
    }

    fun pause() {
        if (!prepared) return
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        if (!enabled || !prepared) return
        mediaPlayer?.takeIf { !it.isPlaying }?.start()
    }

    fun stop() {
        fade?.cancel()
        mediaPlayer?.apply {
            if (prepared && isPlaying) stop()
            release()
        }
        mediaPlayer = null
        prepared = false
        currentTrack = null
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            pause()
        } else if (mediaPlayer != null) {
            resume()
        }
    }

    fun release() {
        stop()
    }

    private companion object {
        const val BASE_VOLUME = 0.4f
        const val DUCKED_VOLUME = 0.12f
        const val FADE_MILLIS = 1200L
    }
}
