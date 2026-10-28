package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Puki's ears on the device: kol-stream (EmbeddingGemma 2's audio encoder with a small reader
 * and a transducer; Hebrew and English; Apache 2.0) through ONNX Runtime. [dir] holds what
 * train/kolstream_export.py makes:
 *  - encoder.onnx: audio frames (1, M, 320) -> the joiner's encoder side (1, T, 512), a row per 80 ms
 *  - step.onnx: rows (N, 512) and the last two pieces (1, 2) -> piece scores (N, 2048)
 *  - tokens.txt: the pieces
 * Measured on a Galaxy Tab A7 Lite (a slow tablet), 4 threads: a word in 0.6 s, a 2 s sentence
 * in 1.6 s; loading takes about 17 s there, so the brain loads it in the background.
 */
class KolEars(private val env: OrtEnvironment, dir: File, threads: Int = 4) {
    // Both come int8 and already optimized (on the PC: optimizing a 370 MB graph on a slow
    // tablet took seconds, and saving it there ran out of memory), so they load as they are
    private val opts = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
    }
    private val enc = env.createSession(File(dir, "encoder.onnx").path, opts)
    private val step = env.createSession(File(dir, "step.onnx").path, opts)
    private val pieces = File(dir, "tokens.txt").readLines(Charsets.UTF_8)

    /** What was said in [wav] (16 kHz mono 16-bit, as the app records), "" if nothing. */
    @Synchronized
    fun transcribe(wav: ByteArray): String = transcribe(pcm16(wav))

    @Synchronized
    fun transcribe(audio: FloatArray): String {
        val samples = louder(audio)
        // Frames as Gemma's feature extractor cuts them: 160 zeros in front, 320 samples every 160
        val m = (FRONT + samples.size - FRAME - 1) / HOP + 1
        if (m < MIN_FRAMES) return ""
        val frames = FloatArray(m * FRAME)
        for (i in 0 until m) for (j in 0 until FRAME) {
            val s = i * HOP + j - FRONT
            if (s >= 0) frames[i * FRAME + j] = samples[s]
        }
        val (e, t) = OnnxTensor.createTensor(env, FloatBuffer.wrap(frames), longArrayOf(1, m.toLong(), FRAME.toLong())).use { x ->
            enc.run(mapOf("frames" to x)).use { r ->
                val out = r.get(0) as OnnxTensor
                val b = out.floatBuffer
                FloatArray(b.remaining()).also { b.get(it) } to out.info.shape[1].toInt()
            }
        }
        return decode(greedy(e, t))
    }

    /** search.greedy_fast: score [BLOCK] rows at once with the current pieces and jump to the first
     *  row that writes something (most rows write nothing); up to [MAX_PER_ROW] pieces a row. */
    private fun greedy(e: FloatArray, t: Int): List<Int> {
        val ctx = intArrayOf(0, 0)
        val out = mutableListOf<Int>()
        var row = 0
        var atRow = 0
        while (row < t) {
            val n = if (atRow == 0) min(BLOCK, t - row) else 1
            val best = argmaxRows(e, row, n, ctx)
            val j = if (atRow == 0) best.indexOfFirst { it != 0 } else if (best[0] != 0) 0 else -1
            if (j < 0) {
                row += if (atRow == 0) n else 1
                atRow = 0
                continue
            }
            row += j
            out += best[j]
            ctx[0] = ctx[1]; ctx[1] = best[j]
            if (++atRow >= MAX_PER_ROW) { row++; atRow = 0 }
        }
        return out
    }

    private fun argmaxRows(e: FloatArray, row: Int, n: Int, ctx: IntArray): IntArray {
        val rows = OnnxTensor.createTensor(env, FloatBuffer.wrap(e, row * DJ, n * DJ).slice(), longArrayOf(n.toLong(), DJ.toLong()))
        val last = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ctx[0].toLong(), ctx[1].toLong())), longArrayOf(1, 2))
        try {
            step.run(mapOf("e" to rows, "ctx" to last)).use { r ->
                val b = (r.get(0) as OnnxTensor).floatBuffer
                val v = b.remaining() / n
                return IntArray(n) { i ->
                    var k = 0
                    var top = b.get(i * v)
                    for (c in 1 until v) b.get(i * v + c).let { if (it > top) { top = it; k = c } }
                    k
                }
            }
        } finally {
            rows.close(); last.close()
        }
    }

    /** Sentencepiece pieces back to text ("▁" starts a word; <0x..> pieces are UTF-8 bytes). */
    private fun decode(ids: List<Int>): String {
        val bytes = java.io.ByteArrayOutputStream()
        for (id in ids) {
            val p = pieces.getOrNull(id) ?: continue
            if (p.length == 6 && p.startsWith("<0x") && p.endsWith(">")) bytes.write(p.substring(3, 5).toInt(16))
            else if (!(p.startsWith("<") && p.endsWith(">"))) bytes.write(p.replace('▁', ' ').toByteArray(Charsets.UTF_8))
        }
        return bytes.toString(Charsets.UTF_8.name()).trim()
    }

    fun close() {
        enc.close(); step.close(); opts.close()
    }

    companion object {
        const val FRONT = 160
        const val FRAME = 320
        const val HOP = 160
        const val MIN_FRAMES = 16
        const val DJ = 512
        const val BLOCK = 16
        const val MAX_PER_ROW = 3

        /** model.level_gain: quiet input costs accuracy, so it's raised towards the training
         *  level (RMS 0.1 over the louder half of 32 ms frames), at most 10x, never lowered. */
        fun louder(x: FloatArray): FloatArray {
            val n = x.size / 512
            if (n < 4) return x
            val e = FloatArray(n) { f -> var s = 0.0; for (i in 0 until 512) { val v = x[f * 512 + i]; s += v * v }; sqrt(s / 512).toFloat() }
            val median = e.sorted().let { if (n % 2 == 1) it[n / 2] else (it[n / 2 - 1] + it[n / 2]) / 2 }
            val loud = e.filter { it >= median }
            val rms = sqrt(loud.sumOf { (it * it).toDouble() } / loud.size).toFloat()
            if (rms <= 1e-4f || rms >= 0.06f) return x
            val g = min(10f, 0.1f / rms)
            return FloatArray(x.size) { (x[it] * g).coerceIn(-1f, 1f) }
        }
    }
}

/** The samples of a 16 kHz mono 16-bit WAV, as -1..1. */
internal fun pcm16(wav: ByteArray): FloatArray {
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
