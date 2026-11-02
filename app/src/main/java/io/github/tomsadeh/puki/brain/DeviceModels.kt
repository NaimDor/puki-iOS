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

    val models = listOf(
        // The voice: BlueTTS (MIT) with the Noa voice, kept with Syrinx's int8 parts in
        // dtsj89/puki-syrinx so an upstream change can't break new installs. Syrinx is its int8
        // build for weak tablets (train/squawk_quant.py: no audible difference, 3x smaller, twice
        // as fast). noa.json stays on BlueTTS's GitHub at its pinned commit
        hf(SYRINX_REPO, "duration_predictor_style.onnx", "voice/duration_predictor_style.onnx", 1508749, "3d0347cfc09234b78a20d5fa50b5c402d338fb99825f40ada8c70301edbb60ea"),
        hf(SYRINX_REPO, "text_encoder.onnx", "voice/text_encoder.onnx", 27419694, "f971aa25315b93efff1850b39fe293aa93ae85df69fabf5e5022318cc632973c"),
        hf(SYRINX_REPO, "vector_estimator.onnx", "voice/vector_estimator.onnx", 34535720, "e4ae775c1287a8d441a26c4d0b6961a941762032690bb061757ae26afccfb82f"),
        hf(SYRINX_REPO, "vocoder.onnx", "voice/vocoder.onnx", 35518078, "525c2c3ea5b10d2f9c57f7ac1093a568d3e3351d7efeec087cf89202eeac3e81"),
        hf(SYRINX_REPO, "uncond.npz", "voice/uncond.npz", 52732, "1c71c2b2836fd97f4c56eb21e258227650bbe0dcd906f64e2bba58e6ac09e76b"),
        hf(SYRINX_REPO, "stats.npz", "voice/stats.npz", 1920, "20d44fae010d46b859ee6238609780eb7bd5bf82320bad17b826a5dcfa4ed658"),
        hf(SYRINX_REPO, "vocab.json", "voice/vocab.json", 3157, "13fba5e735222fcf30d9c062d898982a1ba86d481f11560fbbdf6e0af79417b3"),
        hf(SYRINX_REPO, "tts.json", "voice/tts.json", 9260, "862d44194197de4873eccc8bfdab1835d15f98e469a90489df6da8f92a48c7ea"),
        Model("https://raw.githubusercontent.com/maxmelichov/BlueTTS/0e38dbf08ed53f85863d1eab092bd9572c53a503/voices/noa.json",
            "voice/noa.json", 288898, "c233f066a7d505306892509649bc80abeb7d0a565687f143e277ec93874a47c8"),
        // Puki's own models (train/): Brains writes his lines, Beak reads them aloud (int8, ONNX)
        hf(BRAINS_REPO, "brains_int8.onnx", "$BRAINS/brains_int8.onnx", 71704455, "fb2c45325df821fff309c3785622061c0960f15a7925599f8e523cf36c6f20af"),
        hf(BRAINS_REPO, "tokenizer.json", "$BRAINS/tokenizer.json", 1000032, "f00623972a58b663923fea399afae21fbc148bf3ee9725a6c505a86fc004fe45"),
        hf(BRAINS_REPO, "brains.json", "$BRAINS/brains.json", 103, "e248f6a8a0d6ac8cfdfd3def0308e993085c84d2f852f9b2ae2664b520f2e92a"),
        hf(BEAK_REPO, "beak_int8.onnx", "$BEAK/beak_int8.onnx", 2545483, "1d4f77719d21a5ec6cdd7710066ba9b081b39fdcc4052ca1eed2236996f0702d"),
        hf(BEAK_REPO, "vocab.json", "$BEAK/vocab.json", 1346, "c432d47d12e89098b28c53b9666179dad04e0beb2755ed5632fddd29a757b00f"),
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
            // Gemma and RenikudPlus, before Puki's own models (4 GB)
            for (old in listOf("gemma.litertlm", "voice/renikud_int8.onnx")) {
                File(dir, old).delete()
                File(dir, "$old.ok").delete()
            }
            cacheDir.deleteRecursively()  // (LiteRT's compiled programs)
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
        private const val EARS_REPO = "dtsj89/puki-ears"
        /** Brains' and Beak's folders in [dir]. */
        const val BRAINS = "brains"
        const val BEAK = "beak"
        private const val BRAINS_REPO = "dtsj89/puki-brains"
        private const val BEAK_REPO = "dtsj89/puki-beak"
        private const val SYRINX_REPO = "dtsj89/puki-syrinx"

        /** Whether settings offer the download. They do, on capable devices ([capable]). */
        const val OFFERED = true

        /**
         * Devices that can run it: 64-bit ARM with about 3 GB of memory or more. Measured on a Galaxy
         * Tab A7 Lite (3 GB, MT8768): a line written, read and spoken in about 1.5 seconds.
         */
        fun capable(context: Context): Boolean {
            val mem = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            return "arm64-v8a" in Build.SUPPORTED_ABIS && mem.totalMem >= 2_500_000_000L
        }
    }
}
