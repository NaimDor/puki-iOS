package io.github.tomsadeh.puki.brain.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Random
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.max

/**
 * Puki's voice on this device: BlueTTS (github.com/maxmelichov/BlueTTS, MIT) with the Noa
 * voice, ported from blue_onnx's BlueTTS.synthesize. Hebrew only for now (English needs
 * espeak, which isn't on the phone yet).
 *
 * [modelDir] holds the BlueTTS2.5 ONNX bundle (duration_predictor_style, text_encoder,
 * vector_estimator, vocoder, uncond.npz, stats.npz, vocab.json, tts.json); [voiceStyle] is
 * voices/noa.json; [renikud] reads the Hebrew (without it, only [voice] can speak, from phonemes).
 */
class BlueVoice(
    private val env: OrtEnvironment,
    modelDir: File,
    voiceStyle: File,
    private val renikud: Renikud?,
    threads: Int = 2,  // fastest on a Galaxy S25 (more threads were slower)
) {
    val sampleRate: Int
    private val baseChunkSize: Int
    private val compressFactor: Int
    private val latentDim: Int
    private val charToId: Map<String, Int>
    private val padId: Int
    private val styleTtl: FloatArray  // [1, 50, 256]
    private val styleDp: FloatArray   // [1, 8, 16]
    private val ttlShape: LongArray
    private val dpShape: LongArray
    private val uText: FloatArray     // [1, 256, 1]: classifier-free guidance's "no text"
    private val uRef: FloatArray      // [1, 50, 256]: and "no speaker"
    private val latentMean: FloatArray
    private val latentStd: FloatArray
    private val normalizerScale: Float

    private val opts = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val dp = env.createSession(File(modelDir, "duration_predictor_style.onnx").path, opts)
    private val textEnc = env.createSession(File(modelDir, "text_encoder.onnx").path, opts)
    private val vectorEst = env.createSession(File(modelDir, "vector_estimator.onnx").path, opts)
    private val vocoder = env.createSession(File(modelDir, "vocoder.onnx").path, opts)

    /** Where the flow-matching noise comes from (tests replay the PC's). */
    var noise: (size: Int) -> FloatArray = { n -> val r = Random(); FloatArray(n) { r.nextGaussian().toFloat() } }

    init {
        val cfg = JSONObject(File(modelDir, "tts.json").readText())
        sampleRate = cfg.getJSONObject("ae").getInt("sample_rate")
        baseChunkSize = cfg.getJSONObject("ae").getInt("base_chunk_size")
        compressFactor = cfg.getJSONObject("ttl").getInt("chunk_compress_factor")
        latentDim = cfg.getJSONObject("ttl").getInt("latent_dim")

        val vocab = JSONObject(File(modelDir, "vocab.json").readText())
        padId = vocab.optInt("pad_id", 0)
        val c2i = vocab.getJSONObject("char_to_id")
        charToId = c2i.keys().asSequence().associateWith { c2i.getInt(it) }

        val style = JSONObject(voiceStyle.readText())
        fun styleOf(key: String): Pair<FloatArray, LongArray> {
            val o = style.getJSONObject(key)
            val dims = o.getJSONArray("dims")
            val flat = mutableListOf<Float>()
            fun walk(v: Any) {
                if (v is org.json.JSONArray) for (i in 0 until v.length()) walk(v.get(i)) else flat += (v as Number).toFloat()
            }
            walk(o.get("data"))
            return flat.toFloatArray() to LongArray(dims.length()) { dims.getLong(it) }
        }
        styleOf("style_ttl").let { styleTtl = it.first; ttlShape = it.second }
        styleOf("style_dp").let { styleDp = it.first; dpShape = it.second }

        val uncond = readNpz(File(modelDir, "uncond.npz"))
        uText = uncond.getValue("u_text")
        uRef = uncond.getValue("u_ref")
        val stats = readNpz(File(modelDir, "stats.npz"))
        latentMean = stats.getValue("mean")
        latentStd = stats.getValue("std")
        normalizerScale = stats["normalizer_scale"]?.firstOrNull()?.takeIf { it != 0f } ?: 1f
    }

    /** Hebrew [text] spoken, as a 16-bit mono WAV (with a short silence at the end), or null. */
    fun speak(text: String, speed: Float = 0.92f, totalStep: Int = 8): ByteArray? {
        val samples = synthesize(text, speed, totalStep) ?: return null
        return wav(samples + FloatArray((sampleRate * 0.15).toInt()))
    }

    /** BlueTTS.synthesize(text, lang="he"): normalized, read, chunked, voiced, peak-limited. */
    fun synthesize(text: String, speed: Float = 0.92f, totalStep: Int = 8): FloatArray? {
        val normalized = VoiceText.prepareTextForSynthesis(text)
        if (normalized.none { it in '֐'..'׿' }) return null  // nothing Hebrew to say
        val reader = renikud ?: return null
        return voice(VoiceText.stripLangTags(reader.phonemize(normalized)), speed, totalStep)
    }

    /** [phonemes] (IPA, as Renikud writes them) spoken: chunked, voiced, peak-limited. */
    fun voice(phonemes: String, speed: Float = 0.92f, totalStep: Int = 8): FloatArray? {
        var out = FloatArray(0)
        for (chunk in VoiceText.chunkText(phonemes)) out += infer(chunk, speed, totalStep)
        if (out.isEmpty()) return null
        // limit_peak: the vocoder sometimes overshoots ±1, so scale down (never up)
        val peak = out.maxOf { abs(it) }
        if (peak > 0.95f) for (i in out.indices) out[i] *= 0.95f / peak
        return out
    }

    fun idsFor(phonemeChunk: String): IntArray = VoiceText.encode(phonemeChunk, charToId, padId)

    /** TextToSpeech._infer for one chunk, with classifier-free guidance (scale 4). */
    private fun infer(phonemeChunk: String, speed: Float, totalStep: Int): FloatArray {
        val ids = idsFor(phonemeChunk)
        val nTxt = ids.size.toLong()
        val tensors = mutableListOf<OnnxTensor>()
        fun f(data: FloatArray, vararg shape: Long) = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).also { tensors += it }
        try {
            val textIds = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() }), longArrayOf(1, nTxt)).also { tensors += it }
            val textMask = f(FloatArray(ids.size) { 1f }, 1, 1, nTxt)
            val sTtl = f(styleTtl, *ttlShape)
            val sDp = f(styleDp, *dpShape)

            val duration = dp.run(mapOf("text_ids" to textIds, "style_dp" to sDp, "text_mask" to textMask)).use {
                (it.get(0).value as FloatArray)[0]
            } / max(speed, 1e-6f)
            val textEmb = textEnc.run(mapOf("text_ids" to textIds, "style_ttl" to sTtl, "text_mask" to textMask)).use {
                flatten3(it.get(0).value)
            }
            val tEmb = f(textEmb, 1, 256, nTxt)

            // sample_noisy_latent
            val frame = baseChunkSize * compressFactor
            val wavLen = (duration.toDouble() * sampleRate).toLong()  // float32 duration, float64 product, as numpy does
            val latLen = max(0L, (wavLen + frame - 1) / frame).toInt()
            val ch = latentDim * compressFactor
            var xt = noise(ch * latLen)
            val latentMask = f(FloatArray(latLen) { 1f }, 1, 1, latLen.toLong())
            val total = f(floatArrayOf(totalStep.toFloat()), 1)
            val uTxt = f(uText, 1, 256, 1)
            val uRf = f(uRef, 1, 50, 256)
            val uMask = f(floatArrayOf(1f), 1, 1, 1)

            for (step in 0 until totalStep) {
                val x = f(xt, 1, ch.toLong(), latLen.toLong())
                val cur = f(floatArrayOf(step.toFloat()), 1)
                val vCond = vectorEst.run(mapOf("noisy_latent" to x, "text_emb" to tEmb, "style_ttl" to sTtl, "text_mask" to textMask,
                    "latent_mask" to latentMask, "current_step" to cur, "total_step" to total)).use { flatten3(it.get(0).value) }
                val vUncond = vectorEst.run(mapOf("noisy_latent" to x, "text_emb" to uTxt, "style_ttl" to uRf, "text_mask" to uMask,
                    "latent_mask" to latentMask, "current_step" to cur, "total_step" to total)).use { flatten3(it.get(0).value) }
                xt = FloatArray(xt.size) { vUncond[it] + CFG_SCALE * (vCond[it] - vUncond[it]) }
            }

            // _prepare_vocoder_latent: de-normalize, then (24*6, T) -> (24, T*6)
            val z = FloatArray(xt.size)
            for (c in 0 until ch) for (t in 0 until latLen) {
                val v = (xt[c * latLen + t] / normalizerScale) * latentStd[c] + latentMean[c]
                val l = c / compressFactor
                val k = c % compressFactor
                z[l * latLen * compressFactor + t * compressFactor + k] = v
            }
            val latent = f(z, 1, latentDim.toLong(), (latLen * compressFactor).toLong())
            var wav = vocoder.run(mapOf("latent" to latent)).use { flattenAny(it.get(0).value) }
            if (wav.size > 2 * frame) wav = wav.copyOfRange(frame, wav.size - frame)
            return wav
        } finally {
            tensors.forEach { it.close() }
        }
    }

    fun close() {
        listOf(dp, textEnc, vectorEst, vocoder).forEach { it.close() }
        renikud?.close()  // 311 MB of its own
    }

    private fun wav(samples: FloatArray): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        return io.github.tomsadeh.puki.audio.wavBytes(data.array(), sampleRate)
    }

    companion object {
        private const val CFG_SCALE = 4f

        @Suppress("UNCHECKED_CAST")
        private fun flatten3(v: Any): FloatArray {
            val a = v as Array<Array<FloatArray>>
            val n = a[0].size * a[0][0].size
            val out = FloatArray(n)
            var i = 0
            for (row in a[0]) for (x in row) out[i++] = x
            return out
        }

        private fun flattenAny(v: Any): FloatArray = when (v) {
            is FloatArray -> v
            is Array<*> -> v.map { flattenAny(it!!) }.reduce { acc, x -> acc + x }
            else -> error("unexpected output ${v::class}")
        }

        /** The float32 arrays in a NumPy .npz (a zip of .npy files), flattened. */
        fun readNpz(file: File): Map<String, FloatArray> {
            val out = mutableMapOf<String, FloatArray>()
            ZipFile(file).use { zip ->
                for (entry in zip.entries()) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    // .npy: magic (6) + version (2) + header length (2 or 4) + header + data
                    val major = bytes[6].toInt()
                    val headerLen = if (major == 1) buf.getShort(8).toInt() and 0xFFFF else buf.getInt(8)
                    val start = (if (major == 1) 10 else 12) + headerLen
                    val header = String(bytes, if (major == 1) 10 else 12, headerLen)
                    require("'<f4'" in header) { "${entry.name}: only float32 is supported ($header)" }
                    val fb = ByteBuffer.wrap(bytes, start, bytes.size - start).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    out[entry.name.removeSuffix(".npy")] = FloatArray(fb.remaining()).also { fb.get(it) }
                }
            }
            return out
        }
    }
}
