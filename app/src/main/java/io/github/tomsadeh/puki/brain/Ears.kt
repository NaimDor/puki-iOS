package io.github.tomsadeh.puki.brain

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** whisper.cpp through libpuki_whisper.so (built by native/whisper/build.py). */
object WhisperNative {
    @JvmStatic external fun init(model: String): Long
    @JvmStatic external fun free(ctx: Long)
    @JvmStatic external fun transcribe(ctx: Long, samples: FloatArray, lang: String, prompt: String, audioCtx: Int, threads: Int): String
    @JvmStatic external fun quantizeQ8(input: String, output: String): Boolean

    @Volatile private var loaded = false

    /** On ARM, the fast build on chips with the i8mm instructions (e.g. a Galaxy S25), else the
     *  baseline one; x86_64 (an emulator) has only the one build. */
    @Synchronized
    fun load() {
        if (loaded) return
        val arm = android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
        val i8mm = arm && runCatching { File("/proc/cpuinfo").readText().contains(" i8mm") }.getOrDefault(false)
        System.loadLibrary(if (i8mm || !arm) "puki_whisper" else "puki_whisper_base")
        loaded = true
    }
}

/**
 * Puki's ears on the device: ivrit.ai's Hebrew Whisper (large-v3-turbo, q8_0) through
 * whisper.cpp. Kept loaded once woken, so each try only pays for the listening itself.
 */
class Ears(model: File) {
    private val ctx: Long

    init {
        WhisperNative.load()
        ctx = WhisperNative.init(model.path)
        require(ctx != 0L) { "couldn't load ${model.name}" }
    }

    /** What the child said in [wav] (16 kHz mono 16-bit, as the app records), "" if nothing. */
    @Synchronized
    fun transcribe(wav: ByteArray, lang: String, hints: String): String {
        val samples = pcm16(wav)
        if (samples.isEmpty()) return ""
        val seconds = samples.size / 16000f
        // The full window is 30 s (1500); a shorter one is far less work. Not under 320: measured on
        // a Galaxy S25, a lone word ("כחול") came out wrong ("תחול") in any smaller window, even
        // though sentences were fine; 320 took a third off 512's time (about 1.07 s against 1.66)
        val audioCtx = when {
            seconds <= 6f -> 320
            seconds <= 10f -> 512
            else -> 0
        }
        val heard = WhisperNative.transcribe(ctx, samples, lang, hints, audioCtx, THREADS).trim()
        // Whisper "hears" these in silence or noise; treat them as nothing heard (as on the PC)
        if (HALLUCINATIONS.any { it in heard.lowercase() }) return ""
        if (lang == "en" && heard.lowercase().trim(' ', '.', '!', '?', ',') in HALLUCINATIONS_ALONE) return ""
        return heard
    }

    fun close() = WhisperNative.free(ctx)

    private fun pcm16(wav: ByteArray): FloatArray {
        // A plain 44-byte WAV header from MicRecorder; find "data" anyway, in case
        var start = 44
        for (i in 12 until minOf(wav.size - 8, 200)) {
            if (wav[i] == 'd'.code.toByte() && wav[i + 1] == 'a'.code.toByte() && wav[i + 2] == 't'.code.toByte() && wav[i + 3] == 'a'.code.toByte()) {
                start = i + 8
                break
            }
        }
        if (wav.size <= start) return FloatArray(0)
        val sb = ByteBuffer.wrap(wav, start, wav.size - start).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(sb.remaining()) { sb.get(it) / 32768f }
    }

    private companion object {
        const val THREADS = 4
        val HALLUCINATIONS = listOf("כתוביות", "תודה רבה", "תודה שצפיתם", "subtitles", "thank you for watching",
            "thanks for watching", "please subscribe", "amara.org")
        val HALLUCINATIONS_ALONE = listOf("you", "thank you", "thanks", "bye")
    }
}
