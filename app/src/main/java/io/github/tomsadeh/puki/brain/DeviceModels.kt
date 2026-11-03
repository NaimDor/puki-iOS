package io.github.tomsadeh.puki.brain

import kotlinx.coroutines.sync.withLock
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import io.github.tomsadeh.puki.data.models.BrainLanguages
import io.github.tomsadeh.puki.data.models.Language
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
 * in the app's own storage. Downloads resume where they stopped. Some are shared, and each
 * language has its own Brains and reader: the parent picks Hebrew, English or both ([BrainLanguages]).
 */
class DeviceModels(context: Context) {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "brain")
    val cacheDir = File(context.cacheDir, "brain")

    class Model(val url: String, val name: String, val size: Long, val sha256: String)

    private fun hf(repo: String, path: String, name: String, size: Long, sha: String) =
        Model("https://huggingface.co/$repo/resolve/main/$path", name, size, sha)

    /** What every language needs: the voice and the ears. */
    val shared = listOf(
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
        // Puki's ears: kol-stream (Hebrew and English, Apache 2.0, on EmbeddingGemma 2), made
        // ready for phones by train/kolstream_export.py (int8, optimized) and kept on Hugging Face
        hf(EARS_REPO, "encoder.onnx", "$EARS/encoder.onnx", 357955315, "4193fc7e9a39bb33e3d1fbc645db39f9a917d6996aa0d4f9aba5080b0c77dd3d"),
        hf(EARS_REPO, "step.onnx", "$EARS/step.onnx", 5535895, "438b04f5eb825103fdb9f03fc06a4f91827aac8ef3d92837c4f20db4ec834bba"),
        hf(EARS_REPO, "tokens.txt", "$EARS/tokens.txt", 17060, "1dfdd2c55ec357b9b2dd575b2dcf4c729774155097416fa5d7c3f3565f4f16ae"),
    )

    // Puki's own models (train/), one of each per language: Brains writes his lines, Beak reads them aloud (int8, ONNX)

    /** Hebrew: its Brains, and Beak. */
    val hebrew = listOf(
        hf(BRAINS_HE_REPO, "brains_int8.onnx", "$BRAINS_HE/brains_int8.onnx", 61198215, "a4412965e18e3d335d64dc19fb4222dba889f7e0e7dec0ef4db1813976ba48ce"),
        hf(BRAINS_HE_REPO, "tokenizer.json", "$BRAINS_HE/tokenizer.json", 700924, "9cdd53bc43bbbf9ada1840137c7996ee2aa0352dfccc156c342ef9696dce7acf"),
        hf(BRAINS_HE_REPO, "brains.json", "$BRAINS_HE/brains.json", 101, "fed5775d484310b3ffff311052805b237761d3d0a6151ac5d8a39a4bfd77d16d"),
        hf(BEAK_REPO, "beak_int8.onnx", "$BEAK/beak_int8.onnx", 2545483, "1d4f77719d21a5ec6cdd7710066ba9b081b39fdcc4052ca1eed2236996f0702d"),
        hf(BEAK_REPO, "vocab.json", "$BEAK/vocab.json", 1346, "c432d47d12e89098b28c53b9666179dad04e0beb2755ed5632fddd29a757b00f"),
    )

    /** English: its Brains, and the English reader (EnglishBeak). */
    val english = listOf(
        hf(BRAINS_EN_REPO, "brains_int8.onnx", "$BRAINS_EN/brains_int8.onnx", 61198215, "8d74b98e8aba301c9107646ca31abbfd69ca2b7dfa6f63c0bc00e5c02cd2eb97"),
        hf(BRAINS_EN_REPO, "tokenizer.json", "$BRAINS_EN/tokenizer.json", 551326, "e2b00c7270b48c22bcb980e25cb050a34bdafcabb7095632fb218ae4d93932bb"),
        hf(BRAINS_EN_REPO, "brains.json", "$BRAINS_EN/brains.json", 101, "fed5775d484310b3ffff311052805b237761d3d0a6151ac5d8a39a4bfd77d16d"),
        hf(BEAK_EN_REPO, "reader_int8.onnx", "$BEAK_EN/reader_int8.onnx", 2873594, "d1ca62ceef4d3d8d01f51d5d26c1c9c4c5dd66f9e24f572449c82b6dfc86c318"),
        hf(BEAK_EN_REPO, "symbols.json", "$BEAK_EN/symbols.json", 387, "2eaf977851a9ead653f473947ac840ae02a0e6a5f4e80abd15bdacbaa48767a1"),
        hf(BEAK_EN_REPO, "words.tsv", "$BEAK_EN/words.tsv", 726932, "569b25dbea7e9506380e0ddb4e71dc977ee05f5e77d5fb4dc1be4a9fe2c3413b"),
    )

    /** The models for [langs]. */
    fun models(langs: BrainLanguages) = shared + (if (langs.hebrew) hebrew else emptyList()) + (if (langs.english) english else emptyList())

    fun totalBytes(langs: BrainLanguages) = models(langs).sumOf { it.size }

    /** What's still to download for [langs] (adding a language to a downloaded brain is only its Brains and reader). */
    fun missingBytes(langs: BrainLanguages) = models(langs).filterNot { done(it) }.sumOf { it.size }

    fun file(name: String) = File(dir, name)
    private fun okMark(m: Model) = File(dir, m.name + ".ok")

    private fun done(m: Model) = okMark(m).exists() && File(dir, m.name).length() == m.size

    /** Everything [langs] needs downloaded, checked and ready to load. */
    fun ready(langs: BrainLanguages) = models(langs).all { done(it) }

    /** Enough for the brain to wake: the shared models and at least one language. */
    fun ready() = shared.all { done(it) } && (hasHebrew() || hasEnglish())

    fun hasHebrew() = hebrew.all { done(it) }
    fun hasEnglish() = english.all { done(it) }

    /** The languages to offer: the parent's pick, else what's already downloaded (before the choice existed, that
     *  was Hebrew), else the game's language. */
    fun languagesFor(picked: BrainLanguages?, game: Language): BrainLanguages = picked ?: when {
        hasHebrew() && hasEnglish() -> BrainLanguages.BOTH
        hasHebrew() -> BrainLanguages.HEBREW
        hasEnglish() -> BrainLanguages.ENGLISH
        game == Language.ENGLISH -> BrainLanguages.ENGLISH
        else -> BrainLanguages.HEBREW
    }

    // (what the running download counts its progress against)
    @Volatile private var downloading = shared

    fun downloadedBytes() = downloading.sumOf { m -> if (done(m)) m.size else File(dir, m.name + ".part").length() }

    /** Download progress (0..1) while [download] runs, else null. */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress

    /** Downloads what [langs] needs, and frees a language's models that the parent no longer picked. */
    private val downloadLock = kotlinx.coroutines.sync.Mutex()

    suspend fun download(langs: BrainLanguages) = downloadLock.withLock { withContext(Dispatchers.IO) {
        try {
            downloading = models(langs)
            _progress.value = downloadedBytes().toFloat() / totalBytes(langs)
            for (m in downloading) if (!done(m)) ensure(m)
            for (m in (if (langs.hebrew) emptyList() else hebrew) + (if (langs.english) emptyList() else english)) {
                File(dir, m.name).delete()
                okMark(m).delete()
            }
            File(dir, "whisper").deleteRecursively()  // the ears before kol-stream (1.6 GB)
            File(dir, "brains").deleteRecursively()  // the one Brains for both languages, before 0.9.6 (72 MB)
            // Gemma and RenikudPlus, before Puki's own models (4 GB)
            for (old in listOf("gemma.litertlm", "voice/renikud_int8.onnx")) {
                File(dir, old).delete()
                File(dir, "$old.ok").delete()
            }
            cacheDir.deleteRecursively()  // (LiteRT's compiled programs)
        } finally {
            _progress.value = null
        }
    } }

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
                        _progress.value = downloadedBytes().toFloat() / downloading.sumOf { it.size }
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
        /** Each language's Brains folder in [dir], and Beak's. */
        const val BRAINS_HE = "brains-he"
        const val BRAINS_EN = "brains-en"
        const val BEAK = "beak"
        /** The English reader's folder (EnglishBeak). */
        const val BEAK_EN = "beak-en"
        private const val BEAK_EN_REPO = "dtsj89/puki-beak-en"
        private const val BRAINS_HE_REPO = "dtsj89/puki-brains-he"
        private const val BRAINS_EN_REPO = "dtsj89/puki-brains-en"
        private const val BEAK_REPO = "dtsj89/puki-beak-he"
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
