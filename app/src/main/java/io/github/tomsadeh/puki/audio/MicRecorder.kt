package io.github.tomsadeh.puki.audio

import android.annotation.SuppressLint
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/**
 * Records the child talking to the parrot: 16 kHz mono 16-bit PCM (what Whisper
 * wants), returned as a WAV file. Stops on its own after [MAX_SECONDS].
 */
class MicRecorder {
    @Volatile private var record: AudioRecord? = null
    @Volatile private var worker: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false

    val isRecording get() = running

    /** How loud the last moment was, 0 (silence) to about 1 (shouting): for "has she stopped talking?". */
    @Volatile var level = 0f
        private set

    /** Caller must hold the RECORD_AUDIO permission. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running && worker?.isAlive == true) return true
        if (running) stop()  // an old recording that ran out its time and was never collected: start fresh
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        pcm.reset()
        record = rec
        running = true
        rec.startRecording()
        worker = thread(name = "mic") {
            val buf = ByteArray(minBuf)
            val maxBytes = RATE * 2 * MAX_SECONDS
            while (running && pcm.size() < maxBytes) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    synchronized(pcm) { pcm.write(buf, 0, n) }
                    level = rms(buf, n)
                }
            }
            // Out of time (or stopped): the microphone goes off now, even if nobody collects the recording
            runCatching { rec.stop() }
            level = 0f
        }
        return true
    }

    /** [start], off the main thread: opening the microphone takes a while. */
    suspend fun startOffMain(): Boolean = withContext(NonCancellable + Dispatchers.IO) { start() }

    /** [stop], off the main thread: it waits for the recording thread to finish. */
    suspend fun stopOffMain(): ByteArray = withContext(NonCancellable + Dispatchers.IO) { stop() }

    /** For a screen closing, which can't wait: the microphone goes off, just not on this thread. */
    fun stopInBackground() {
        if (isRecording) thread(name = "mic-stop") { stop() }
    }

    /** Stops recording and returns the WAV bytes (empty if nothing was recorded). */
    fun stop(): ByteArray {
        running = false
        worker?.join(500)
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
        level = 0f
        val data = synchronized(pcm) { pcm.toByteArray() }
        return if (data.isEmpty()) data else wav(data)
    }

    private fun rms(b: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val v = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort() / 32768.0
            sum += v * v
            i += 2
        }
        return kotlin.math.sqrt(sum / maxOf(1, n / 2)).toFloat() * 4f  // speech sits around 0.1-0.6
    }

    private fun wav(pcmData: ByteArray): ByteArray = wavBytes(pcmData, RATE)

    companion object {
        const val RATE = 16000
        const val MAX_SECONDS = 8
    }
}
