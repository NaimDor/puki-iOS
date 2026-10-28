package io.github.tomsadeh.puki.brain

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * The models the device brain needs, downloaded once from where their makers publish them
 * (Hugging Face, and BlueTTS's GitHub for the voice style), checked against SHA-256, and kept
 * in the app's own storage. Downloads resume where they stopped.
 */
class DeviceModels(context: Context) {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "brain")
    val cacheDir = File(context.cacheDir, "brain")

    class Model(val url: String, val name: String, val size: Long, val sha256: String)

    private fun hf(repo: String, path: String, name: String, size: Long, sha: String) =
        Model("https://huggingface.co/$repo/resolve/main/$path", name, size, sha)

    private val bt = "notmax123/BlueTTS2.5-onnx"
    val models = listOf(
        // The voice: BlueTTS (MIT) with the Noa voice
        hf(bt, "duration_predictor_style.onnx", "voice/duration_predictor_style.onnx", 1508749, "3d0347cfc09234b78a20d5fa50b5c402d338fb99825f40ada8c70301edbb60ea"),
        hf(bt, "text_encoder.onnx", "voice/text_encoder.onnx", 27419694, "f971aa25315b93efff1850b39fe293aa93ae85df69fabf5e5022318cc632973c"),
        hf(bt, "vector_estimator.onnx", "voice/vector_estimator.onnx", 132475789, "50557b34acfafa4b34f21dae263faa74d3f3169f114ff927599a1eff110d0b6e"),
        hf(bt, "vocoder.onnx", "voice/vocoder.onnx", 101411919, "b0709df316ff44cc263c04fbf9d17371b16897071a48b79d3634d5f379fb3067"),
        hf(bt, "uncond.npz", "voice/uncond.npz", 52732, "1c71c2b2836fd97f4c56eb21e258227650bbe0dcd906f64e2bba58e6ac09e76b"),
        hf(bt, "stats.npz", "voice/stats.npz", 1920, "20d44fae010d46b859ee6238609780eb7bd5bf82320bad17b826a5dcfa4ed658"),
        hf(bt, "vocab.json", "voice/vocab.json", 3157, "13fba5e735222fcf30d9c062d898982a1ba86d481f11560fbbdf6e0af79417b3"),
        hf(bt, "tts.json", "voice/tts.json", 9260, "862d44194197de4873eccc8bfdab1835d15f98e469a90489df6da8f92a48c7ea"),
        Model("https://raw.githubusercontent.com/maxmelichov/BlueTTS/0e38dbf08ed53f85863d1eab092bd9572c53a503/voices/noa.json",
            "voice/noa.json", 288898, "c233f066a7d505306892509649bc80abeb7d0a565687f143e277ec93874a47c8"),
        // Hebrew pronunciation: RenikudPlus (the int8 build, a quarter of the size)
        hf("notmax123/RenikudPlus", "model_int8.onnx", "voice/renikud_int8.onnx", 311605741, "0337468fad56eadf5ecc7becfdf21648a4d668de41de5bc27e860141b5afcadd"),
        // Puki's thinking: Gemma 4 E4B for LiteRT-LM
        hf("litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm", "gemma.litertlm", 3659530240, "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"),
        // Puki's ears: kol-stream (Hebrew and English, Apache 2.0, on EmbeddingGemma 2), made
        // ready for phones by train/kolstream_export.py (int8, optimized) and kept on Hugging Face
        hf(EARS_REPO, "encoder.onnx", "$EARS/encoder.onnx", 357955315, "4193fc7e9a39bb33e3d1fbc645db39f9a917d6996aa0d4f9aba5080b0c77dd3d"),
        hf(EARS_REPO, "step.onnx", "$EARS/step.onnx", 5535895, "438b04f5eb825103fdb9f03fc06a4f91827aac8ef3d92837c4f20db4ec834bba"),
        hf(EARS_REPO, "tokens.txt", "$EARS/tokens.txt", 17060, "1dfdd2c55ec357b9b2dd575b2dcf4c729774155097416fa5d7c3f3565f4f16ae"),
    )

    val totalBytes get() = models.sumOf { it.size }

    fun file(name: String) = File(dir, name)
    private fun okMark(m: Model) = File(dir, m.name + ".ok")

    private fun done(m: Model) = okMark(m).exists() && File(dir, m.name).length() == m.size

    /** Everything downloaded, checked and ready to load. */
    fun ready() = models.all { done(it) }

    fun downloadedBytes() = models.sumOf { m -> if (done(m)) m.size else File(dir, m.name + ".part").length() }

    /** Download progress (0..1) while [download] runs, else null. */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress

    suspend fun download() = withContext(Dispatchers.IO) {
        try {
            _progress.value = downloadedBytes().toFloat() / totalBytes
            for (m in models) if (!done(m)) ensure(m)
            File(dir, "whisper").deleteRecursively()  // the ears before kol-stream (1.6 GB)
        } finally {
            _progress.value = null
        }
    }

    /** Frees the space again (parent settings). */
    fun delete() {
        dir.deleteRecursively()
        cacheDir.deleteRecursively()
    }

    private fun ensure(m: Model) {
        val dest = File(dir, m.name)
        if (dest.length() == m.size && okMark(m).exists()) return
        dest.parentFile!!.mkdirs()
        val part = File(dir, m.name + ".part")
        // Retry dropped connections, but give up after 20 tries that get nowhere (a server that
        // keeps ending the download early, with or without an error, would otherwise spin forever)
        var stuck = 0
        while (part.length() < m.size) {
            val before = part.length()
            try {
                fetch(m, part)
            } catch (e: Exception) {
                if (stuck >= 20) throw e
            }
            if (part.length() > before) stuck = 0 else if (++stuck > 20) error("${m.name}: the download keeps stopping")
            if (part.length() < m.size) Thread.sleep(3000)
        }
        if (sha256(part) != m.sha256) {
            part.delete()
            error("${m.name} came down damaged; try again")
        }
        part.renameTo(dest)
        okMark(m).writeText(m.sha256)
    }

    private fun fetch(m: Model, part: File) {
        val have = part.length()
        val conn = URL(m.url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        val code = conn.responseCode
        if (code != 200 && code != 206) error("${m.name}: HTTP $code")
        conn.inputStream.use { input ->
            FileOutputStream(part, code == 206).use { out ->
                val buf = ByteArray(1 shl 20)
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    val now = System.nanoTime()
                    if (now - last > 500_000_000L) {
                        _progress.value = downloadedBytes().toFloat() / totalBytes
                        last = now
                    }
                }
            }
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(4 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** The ears' folder in [dir]. */
        const val EARS = "ears"
        private const val EARS_REPO = "TomSadeh/puki-ears"

        /** Phones that can run it: 64-bit ARM with about 12 GB of memory (tested on a Galaxy S25). */
        fun capable(context: Context): Boolean {
            val mem = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            return "arm64-v8a" in Build.SUPPORTED_ABIS && mem.totalMem >= 10L * 1024 * 1024 * 1024 && Build.VERSION.SDK_INT >= 31
        }
    }
}
