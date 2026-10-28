package io.github.tomsadeh.puki.audio

import android.content.Context
import android.os.Build
import io.github.tomsadeh.puki.brain.Brain
import io.github.tomsadeh.puki.brain.HttpBrain
import io.github.tomsadeh.puki.data.models.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import io.github.tomsadeh.puki.brain.PinnedTls
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

enum class CompanionStatus { OFF, WAKING, READY }

/**
 * How a try at saying a word went (Say it with Puki): nothing heard, another word, a near miss,
 * the word itself, or SPOKE: the child talked but the word couldn't be checked (PC off).
 */
enum class WordTry { SILENT, TRIED, CLOSE, WORD, SPOKE }

/** Result of checking the PC from parent settings. */
/** WRONG_PC: something answered at the address, but not with the paired PC's certificate. */
enum class Connection { OK, WRONG_CODE, LOCKED_OUT, NOT_FOUND, WRONG_PC }

/**
 * פוקי the parrot, whose words come from the "brain" server on a PC at home
 * (brain/ in this repo): Gemma writes a short line about what just happened and
 * BlueTTS voices it. Only reachable on the home network; whenever it isn't
 * (PC off, away from home, slow reply) callers fall back to the recorded lines.
 */
class Companion(
    private val context: Context,
    private val voicePlayer: VoicePlayer,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow(CompanionStatus.OFF)
    val status: StateFlow<CompanionStatus> = _status

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    /** Lines for the game's moments that a parent approved on the PC, kept here with their audio. */
    private val pack = LinePack(context.filesDir) { path -> pcBrain.request("GET", path, null, 8000, "") }
    private var packSynced = 0L

    /** Brings the approved lines up to date, at most once an hour (the PC answers even asleep). */
    @Volatile private var packSyncing = false

    private fun syncPack() {
        if (onDevice || packSyncing || System.currentTimeMillis() - packSynced < 60 * 60 * 1000L) return
        packSyncing = true
        scope.launch(Dispatchers.IO) {
            try {
                // Only a sync that worked waits the hour; a failed one is tried again at the next game
                if (runCatching { pack.sync() }.getOrDefault(false)) packSynced = System.currentTimeMillis()
            } finally {
                packSyncing = false
            }
        }
    }

    /** True while the parrot listens on its own (after asking a question). */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    var enabled = true
        set(value) {
            field = value
            if (!value) _status.value = CompanionStatus.OFF
        }
    var address = ""
    /** The pairing code shown on the PC; sent with every request. */
    var code = ""
    /** The PC's certificate fingerprint (from the QR code, or learned on first use; see [PinnedTls]). */
    var pin = ""
    /** Saves a fingerprint learned on first use (set by the app, to keep it in the settings). */
    var onPinLearned: (String) -> Unit = {}
    var language = Language.HEBREW
    /** The parent lets the games listen for answers said out loud (see [listenForAnswer]). */
    var answersOutLoud = true
    /** The time limit's goodbye is up: the game underneath may not listen (or start talking). */
    var paused = false

    /** What's on screen right now, so the parrot has something to talk about when tapped. */
    private var scene = ""

    /** A line Puki said, kept for the parent's "What Puki said" card (מלשין לרנינג). */
    data class SaidLine(
        val id: Long,
        val said: String,
        val heard: String,
        val situation: String,
        val game: String,
        val lang: String,
        val reported: Boolean = false
    )

    private val _recentLines = MutableStateFlow<List<SaidLine>>(emptyList())

    /** The last lines Puki said this session (memory only), newest last. */
    val recentLines: StateFlow<List<SaidLine>> = _recentLines

    /**
     * A parent says [line] wasn't right. Kept on the tablet (reports.jsonl) and sent to the PC
     * when there is one (its dashboard keeps them too): each becomes a case to fix Puki by.
     */
    fun report(line: SaidLine) {
        if (line.reported) return
        _recentLines.value = _recentLines.value.map { if (it.id == line.id) it.copy(reported = true) else it }
        val json = JSONObject().put("said", line.said).put("heard", line.heard).put("situation", line.situation)
            .put("game", line.game).put("lang", line.lang).put("source", "tablet")
            .put("t", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        scope.launch(Dispatchers.IO) {
            runCatching { File(context.filesDir, "reports.jsonl").appendText(json.toString() + "\n") }
            if (code.isNotBlank()) {
                runCatching { pcBrain.request("POST", "/report", json.toString().toByteArray(Charsets.UTF_8), 5000, "application/json; charset=utf-8") }
            }
        }
    }

    /**
     * The text of a report a parent may send to Puki's makers: what Puki said and what came
     * before, in the parent's language, with the child's name replaced. They see and can edit it
     * before their own mail app sends it.
     */
    fun shareText(line: SaidLine): String {
        val noNikud = { s: String -> io.github.tomsadeh.puki.brain.Persona.stripNikud(s) }
        val placeholder = pick("[שם]", "[name]")
        val hide = { s: String ->
            var t = noNikud(s)
            val name = noNikud(childName).trim()
            if (name.length > 1) t = t.replace(name, placeholder, ignoreCase = true)
            t
        }
        val before = line.heard.ifBlank { line.situation }
        return buildString {
            appendLine(pick("פוקי אמר:", "Puki said:") + " " + hide(line.said))
            if (before.isNotBlank()) appendLine(pick("לפני זה:", "Before that:") + " " + hide(before))
            if (line.game.isNotBlank()) appendLine(pick("משחק:", "Game:") + " " + line.game)
            appendLine(pick("שפה:", "Language:") + " " + line.lang)
            appendLine(pick("גרסה:", "Version:") + " " + io.github.tomsadeh.puki.BuildConfig.VERSION_NAME)
            appendLine()
            append(pick("מה לא היה בסדר? (לא חובה)", "What wasn't right? (optional)"))
        }
    }

    private fun recordSaid(route: String, body: ByteArray?) {
        val said = brain.lastSaid() ?: return
        val situation = when (route) {
            "/say" -> runCatching { JSONObject(String(body ?: ByteArray(0), Charsets.UTF_8)).optString("situation") }.getOrDefault("")
            "/look" -> pick("ציור", "a drawing")
            else -> ""
        }
        val line = SaidLine(System.nanoTime(), said.text, said.heard, situation, game, lang())
        _recentLines.value = (_recentLines.value + line).takeLast(20)
    }

    /** The screen the child is on (its route), sent with what she says: the brain's safety
     *  word lists let a game use its own words (a knife in the kitchen). */
    var game = ""

    fun setScene(he: String, en: String) {
        scene = pick(he, en)
    }

    private var childName = ""
    private var childGirl = true
    private val voiceDir = File(context.filesDir, "voice")

    /** Who's playing, from the app's settings. Sent with every request. */
    fun setChild(name: String, girl: Boolean) {
        childName = name.trim()
        childGirl = girl
        refreshNameLines()
    }

    /** What the child did this session, for the bedtime story. */
    private val today = ArrayDeque<String>()

    val isReady get() = _status.value == CompanionStatus.READY

    private var wakeJob: Job? = null
    private val recorder = MicRecorder()

    /** The brain changed (PC or device): forget the old status and look again. */
    fun recheck() {
        wakeJob?.cancel()
        _status.value = CompanionStatus.OFF
        wake()
    }

    /** Checks for the server and wakes the brain if needed. Cheap to call on every game screen. */
    fun wake() {
        if (isReady && enabled) syncPack()  // lines a parent approved while the brain stayed awake
        if (!enabled || wakeJob?.isActive == true || isReady) return
        if (address.isBlank() && !isEmulator() && !onDevice) return  // not set up yet
        wakeJob = scope.launch(Dispatchers.IO) {
            val state = health() ?: run { _status.value = CompanionStatus.OFF; return@launch }
            syncPack()
            if (state == "ready") {
                _status.value = CompanionStatus.READY
                refreshNameLines()
                return@launch
            }
            request("POST", "/wake", timeoutMs = 3000)
            _status.value = CompanionStatus.WAKING
            repeat(60) {
                delay(2000)
                when (health()) {
                    "ready" -> { _status.value = CompanionStatus.READY; refreshNameLines(); return@launch }
                    null -> { _status.value = CompanionStatus.OFF; return@launch }
                }
            }
            _status.value = CompanionStatus.OFF
        }
    }

    /**
     * Has the parrot say something about what happened ([he] or [en], in the app's language);
     * runs [fallback] instead if it's not available or doesn't answer in time.
     */
    fun react(he: String, en: String, fallback: () -> Unit = {}) = react(pick(he, en), fallback)

    private fun react(situation: String, fallback: () -> Unit) {
        remember(situation)
        // A line the parent approved for this moment plays at once, with no wait for the brain;
        // when the brain is awake, half the time it writes a fresh one instead
        val approved = pack.pick(situation, lang(), childGirl)
        if (!isReady) {
            if (approved != null) playApproved(situation, approved) else fallback()
            return
        }
        if (approved != null && kotlin.random.Random.nextBoolean()) {
            playApproved(situation, approved)
            return
        }
        scope.launch {
            if (!say(situation)) {
                if (approved != null) playApproved(situation, approved) else fallback()
            }
        }
    }

    /** Plays an approved pack line, and lists it on the report card like any other. */
    private fun playApproved(situation: String, approved: Pair<LinePack.Line, File>) {
        val (line, file) = approved
        _recentLines.value = (_recentLines.value + SaidLine(System.nanoTime(), line.text, "", situation, game, lang())).takeLast(20)
        _speaking.value = true
        voicePlayer.playFile(file)
        scope.launch {
            delay(200)
            while (voicePlayer.isPlaying()) delay(120)
            _speaking.value = false
        }
    }

    /** The child tapped the parrot. */
    fun chat() = react(
        "לחצו על התוכי כדי שידבר. $scene תגיד משהו חמוד וקצר על זה.",
        "The child tapped you so you'd talk. $scene Say something short and cute about it."
    )

    /** The child held the parrot and talked; [wav] is the recording. Whisper on the PC listens. */
    fun listen(wav: ByteArray, context: String = "") {
        if (!isReady || wav.size < 16000) return  // under half a second: nothing to send
        scope.launch { play(postListen(wav, context)) }
    }

    /** The parrot looks at the child's drawing ([png]) and reacts. Returns false if it couldn't. */
    suspend fun look(png: ByteArray): Boolean {
        if (!isReady) return false
        remember(pick("ציירה ציור", "drew a picture"))
        val audio = withContext(Dispatchers.IO) {
            request("POST", "/look?lang=${lang()}", body = png, contentType = "image/png", timeoutMs = 20000)
        }
        return play(audio)
    }

    /** Says [text] in the parrot's voice and waits until it's done. False if it couldn't. */
    suspend fun speakAndWait(text: String, speed: Double = 0.92): Boolean {
        if (!isReady) return false
        val audio = withContext(Dispatchers.IO) {
            val body = JSONObject().put("text", text).put("lang", lang()).put("speed", speed).toString()
            request("POST", "/speak", body.toByteArray(Charsets.UTF_8), timeoutMs = 8000)
        }
        if (!play(audio)) return false
        awaitQuiet()
        return true
    }

    /** Fetches the audio for [text] without playing it (to prefetch story sentences). */
    suspend fun fetchSpeech(text: String, speed: Double = 0.85): ByteArray? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("text", text).put("lang", lang()).put("speed", speed).toString()
        request("POST", "/speak", body.toByteArray(Charsets.UTF_8), timeoutMs = 8000)?.takeIf { it.isNotEmpty() }
    }

    /** Plays prefetched audio and waits until it's done. */
    suspend fun playAndWait(audio: ByteArray) {
        if (play(audio)) awaitQuiet()
    }

    /**
     * A short bedtime story about today: a list of (sentence, drawable name), the drawable
     * picked from [icons] (name to label). Null if the brain isn't available.
     */
    suspend fun story(icons: Map<String, String>): List<Pair<String, String>>? {
        if (!isReady) return null
        val played = today.joinToString(", ").ifEmpty { if (language == Language.ENGLISH) "games" else "משחקים" }
        val bytes = withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("played", played)
                .put("lang", lang())
                .put("icons", JSONObject(icons as Map<*, *>))
                .toString()
            request("POST", "/story", body.toByteArray(Charsets.UTF_8), timeoutMs = 30000)
        } ?: return null
        if (bytes.isEmpty()) return null
        return runCatching {
            val arr = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("sentences")
            (0 until arr.length()).map { arr.getJSONObject(it).let { s -> s.getString("text") to s.getString("icon") } }
        }.getOrNull()
    }

    /**
     * Asks [question] out loud, then listens for a few seconds without any button.
     * If the child made a sound, runs [onHeard] and lets the parrot answer what it heard,
     * with [context] (what was asked) so the answer fits. Returns true if the child answered.
     */
    suspend fun askAndListen(question: String, context: String, onHeard: () -> Unit): Boolean {
        if (!isReady || !hasMic()) return false
        if (!speakAndWait(question)) return false
        val wav = record(LISTEN_MILLIS) ?: return false
        if (!hasSpeech(wav)) return false
        onHeard()
        val reply = postListen(wav, context)
        delay(900)  // let the animal's answer finish first
        if (play(reply)) awaitQuiet()
        return true
    }

    /**
     * Say it with Puki: listens for a few seconds while the child says [word]. Whether they
     * spoke is decided here on the tablet, so tries count even with the PC off; when the PC is
     * there, it also says whether the word was heard ([alsoCounts]: related words, like the
     * animal's sound). Returns null if there's no microphone permission.
     */
    suspend fun listenForWord(word: String, alsoCounts: List<String>): WordTry? {
        if (!hasMic()) return null
        val wav = record(WORD_LISTEN_MILLIS) ?: return null
        if (!hasSpeech(wav)) return WordTry.SILENT
        if (!isReady) return WordTry.SPOKE
        val path = "/repeat?lang=${lang()}&target=" + URLEncoder.encode(word, "UTF-8") +
            alsoCounts.joinToString("") { "&alt=" + URLEncoder.encode(it, "UTF-8") }
        val body = withContext(Dispatchers.IO) {
            request("POST", path, body = wav, contentType = "audio/wav", timeoutMs = 8000)
        } ?: return WordTry.SPOKE
        return when (runCatching { JSONObject(String(body)).getString("match") }.getOrNull()) {
            "word" -> WordTry.WORD
            "close" -> WordTry.CLOSE
            else -> WordTry.TRIED
        }
    }

    /**
     * A game asked a question: listens a few seconds for the answer said out loud, and returns
     * what was heard ("" for silence or nothing clear), for [io.github.tomsadeh.puki.brain.SpokenAnswer]
     * to match against the answers on screen ([hints], which Whisper then expects). Null when it
     * can't listen: no microphone, the parent's setting off, or no brain awake to hear it.
     */
    suspend fun listenForAnswer(hints: List<String>): String? {
        if (!answersOutLoud || !isReady || !hasMic()) return null
        val wav = record(ANSWER_LISTEN_MILLIS) ?: return null
        if (!hasSpeech(wav)) return ""
        val path = "/hear?lang=${lang()}" + hints.take(16).joinToString("") { "&hint=" + URLEncoder.encode(it, "UTF-8") }
        val body = withContext(Dispatchers.IO) {
            request("POST", path, body = wav, contentType = "audio/wav", timeoutMs = 8000)
        } ?: return ""
        return runCatching { JSONObject(String(body)).optString("heard") }.getOrDefault("")
    }

    /**
     * Records up to [millis] of the microphone (the parrot glows meanwhile), stopping early once
     * the child has spoken and gone quiet ([listenDone]), or null if it couldn't start. However
     * this ends, the microphone goes off: also when the screen closes or the round moves on
     * mid-listen, which cancels the waiting.
     */
    private suspend fun record(millis: Long): ByteArray? {
        if (paused) return null
        _listening.value = true
        try {
            if (!recorder.start()) return null
            val start = System.currentTimeMillis()
            var lastLoud = -1L
            while (true) {
                delay(50)
                val t = System.currentTimeMillis() - start
                if (recorder.level > SPEECH_LEVEL) lastLoud = t
                if (listenDone(t, lastLoud, millis)) {
                    // (only how long, never what: for tuning the stop rule)
                    android.util.Log.i("PukiListen", "listened ${t} ms of $millis, ${if (lastLoud < 0) "no speech" else "stopped ${t - lastLoud} ms after speech"}")
                    break
                }
            }
            return recorder.stop()
        } finally {
            if (recorder.isRecording) recorder.stop()
            _listening.value = false
        }
    }

    private fun hasMic() = hasMicPermission(context)

    /** Rough voice-activity check: a few 50 ms windows clearly louder than room noise. */
    private fun hasSpeech(wav: ByteArray): Boolean {
        if (wav.size <= 44) return false
        val pcm = ByteBuffer.wrap(wav, 44, wav.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val window = MicRecorder.RATE / 20
        var loud = 0
        var i = 0
        while (i + window <= pcm.limit()) {
            var sum = 0.0
            for (j in i until i + window) sum += pcm.get(j).toDouble() * pcm.get(j)
            if (sqrt(sum / window) > 1200) loud++
            i += window
        }
        return loud >= 3
    }

    private suspend fun postListen(wav: ByteArray, context: String): ByteArray? = withContext(Dispatchers.IO) {
        val ctx = if (context.isEmpty()) "" else "&context=" + URLEncoder.encode(context, "UTF-8")
        val where = if (game.isEmpty()) "" else "&game=" + URLEncoder.encode(game, "UTF-8")
        request("POST", "/listen?lang=${lang()}$ctx$where", body = wav, contentType = "audio/wav", timeoutMs = 15000)
    }

    private suspend fun say(situation: String): Boolean {
        val audio = withContext(Dispatchers.IO) {
            val body = JSONObject().put("situation", situation).put("lang", lang()).toString()
            request("POST", "/say", body.toByteArray(Charsets.UTF_8), timeoutMs = 6000)
        }
        return play(audio)
    }

    private suspend fun play(audio: ByteArray?): Boolean {
        if (audio == null) {
            // The brain may have gone to sleep after being idle; wake it for next time
            _status.value = CompanionStatus.OFF
            wake()
            return false
        }
        if (audio.isEmpty()) return false  // no safe line this time
        // The PC sends OGG; the device brain makes WAV
        val ext = if (audio.size > 4 && String(audio, 0, 4, Charsets.US_ASCII) == "RIFF") "wav" else "ogg"
        // A line cut off by another voice never reaches the delete below: clear those first (they
        // may say the child's name, and nothing Puki said is kept)
        val file = withContext(Dispatchers.IO) {
            context.cacheDir.listFiles { f -> f.name.startsWith("companion_") }?.forEach { it.delete() }
            File(context.cacheDir, "companion_${System.nanoTime()}.$ext").apply { writeBytes(audio) }
        }
        _speaking.value = true
        voicePlayer.playFile(file) { file.delete() }
        // Done talking when the line ends or another voice line cuts in
        scope.launch {
            delay(200)
            while (voicePlayer.isPlaying()) delay(120)
            _speaking.value = false
        }
        return true
    }

    private suspend fun awaitQuiet() {
        delay(250)
        withTimeoutOrNull(30_000) { while (voicePlayer.isPlaying()) delay(100) }
    }

    /**
     * Has the parrot record the lines that say the child's name ("כל הכבוד, ___!") and keeps
     * them on the tablet, so they also play when the PC isn't reachable. Redone when the
     * name or gender changes; removed when the name is cleared.
     */
    private fun refreshNameLines() {
        val wanted = if (childName.isBlank()) "" else "$childName|$childGirl|$NAME_LINES_VERSION"
        val marker = File(voiceDir, "name_lines.txt")
        val have = runCatching { marker.readText() }.getOrDefault("")
        if (wanted == have) return
        if (wanted.isEmpty()) {
            voiceDir.listFiles()?.forEach { it.delete() }
            return
        }
        if (!isReady) {
            wake()  // records them as soon as the brain is up
            return
        }
        scope.launch(Dispatchers.IO) {
            voiceDir.mkdirs()
            voiceDir.listFiles()?.forEach { it.delete() }
            var all = true
            for ((key, he, en) in nameLines(childName, childGirl)) {
                for ((lang, text) in listOf("he" to he, "en" to en)) {
                    val body = JSONObject().put("text", text).put("lang", lang).toString()
                    val audio = request("POST", "/speak", body.toByteArray(Charsets.UTF_8), timeoutMs = 10000)
                    if (audio != null && audio.isNotEmpty()) File(voiceDir, "${key}_$lang.ogg").writeBytes(audio) else all = false
                }
            }
            if (all) marker.writeText(wanted)
        }
    }

    private fun nameLines(n: String, girl: Boolean) = listOf(
        Triple("voice_praise_name_1", "כל הכבוד, $n!", "Great job, $n!"),
        Triple("voice_praise_name_2", if (girl) "$n, את אלופה!" else "$n, אתה אלוף!", "$n, you're a champion!"),
        Triple("voice_praise_name_3", "יופי, $n!", "Yay, $n!"),
        Triple("voice_praise_name_4", "וואו $n, איזה יופי!", "Wow, $n!"),
        Triple("voice_hello_name", "שלום $n! לאן נשוט היום?", "Hello $n! Where shall we sail today?"),
        Triple("voice_round_done_name", if (girl) "$n, את סיימת! כל הכבוד!" else "$n, אתה סיימת! כל הכבוד!", "$n, all done! Great job!"),
    )

    private fun remember(situation: String) {
        today.addLast(situation.take(80))
        while (today.size > 12) today.removeFirst()
    }

    private fun lang() = if (language == Language.ENGLISH) "en" else "he"

    private fun pick(he: String, en: String) = if (language == Language.ENGLISH) en else he

    private fun baseUrl(address: String = this.address): String {
        // The Android emulator reaches the PC it runs on at 10.0.2.2
        val host = if (isEmulator()) "10.0.2.2:8790" else address.trim()
        return "https://$host"
    }

    /** For parent settings: can the PC at [address] be reached with [code] (and is it the one in [pin])? */
    suspend fun checkConnection(address: String, code: String, pin: String): Connection = withContext(Dispatchers.IO) {
        var seen = ""
        val result = runCatching {
            val conn = PinnedTls.open(baseUrl(address) + "/health", pin) { seen = it }
            conn.connectTimeout = 2000
            conn.readTimeout = 4000  // a wrong code is answered after a deliberate 1 s delay
            conn.setRequestProperty("X-Puki-Code", code.trim())
            try {
                when (conn.responseCode) {
                    200 -> Connection.OK
                    401 -> Connection.WRONG_CODE
                    429 -> Connection.LOCKED_OUT
                    else -> Connection.NOT_FOUND
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { e ->
            if (generateSequence(e) { it.cause }.any { it is PinnedTls.WrongPc }) Connection.WRONG_PC else Connection.NOT_FOUND
        }
        if (result == Connection.OK && pin.isEmpty() && seen.isNotEmpty()) onPinLearned(seen)
        result
    }

    private fun isEmulator() = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone")

    private fun health(): String? {
        val bytes = request("GET", "/health", timeoutMs = 1500) ?: return null
        return runCatching { JSONObject(String(bytes)).getString("brain") }.getOrNull()
    }

    /** The PC's brain over the home network, or this device's own (see [Brain]). */
    private val pcBrain = HttpBrain({ baseUrl() }, { code }, { pin }, { onPinLearned(it) })
    var deviceBrain: Brain? = null
    /** Think on this device instead of the PC (parent settings; needs [deviceBrain]). */
    var onDevice = false
    private val brain: Brain get() = deviceBrain?.takeIf { onDevice } ?: pcBrain

    /** Returns the response body (empty for 204), or null on any failure. */
    private fun request(
        method: String,
        path: String,
        body: ByteArray? = null,
        timeoutMs: Int,
        contentType: String = "application/json; charset=utf-8"
    ): ByteArray? {
        val full = if (method == "POST") {
            val sep = if ("?" in path) "&" else "?"
            path + sep + "name=" + URLEncoder.encode(childName, "UTF-8") + "&gender=" + (if (childGirl) "girl" else "boy")
        } else path
        val result = brain.request(method, full, body, timeoutMs, contentType)
        val route = path.substringBefore('?')
        if (result != null && result.isNotEmpty() && route in setOf("/say", "/listen", "/look")) recordSaid(route, body)
        return result
    }

    internal companion object {
        const val LISTEN_MILLIS = 3500L
        const val WORD_LISTEN_MILLIS = 4000L  // toddlers take a moment to start
        const val ANSWER_LISTEN_MILLIS = 4000L

        /** The recorder's level for "someone is talking" (as [hasSpeech]'s, about 1200 of 32768). */
        const val SPEECH_LEVEL = 0.15f
        /** Quiet this long after speaking means she's done (toddlers pause mid-phrase, so not less). */
        const val QUIET_AFTER_SPEECH_MILLIS = 800L

        /**
         * Whether a listen [t] ms in can stop: at [max], or once she has spoken ([lastLoud], -1 if
         * not yet) and been quiet since for [QUIET_AFTER_SPEECH_MILLIS]. Before she speaks it
         * waits the whole time: toddlers take a moment to start.
         */
        fun listenDone(t: Long, lastLoud: Long, max: Long): Boolean =
            t >= max || (lastLoud >= 0 && t - lastLoud >= QUIET_AFTER_SPEECH_MILLIS)
        const val NAME_LINES_VERSION = 1
    }
}
