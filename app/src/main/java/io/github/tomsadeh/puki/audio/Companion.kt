package io.github.tomsadeh.puki.audio

import android.content.Context
import io.github.tomsadeh.puki.brain.Brain
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
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

enum class CompanionStatus { OFF, WAKING, READY }

/**
 * How a try at saying a word went (Say it with Puki): nothing heard, another word, a near miss,
 * the word itself, or SPOKE: the child talked but the word couldn't be checked (no brain awake).
 */
enum class WordTry { SILENT, TRIED, CLOSE, WORD, SPOKE }

/**
 * פוקי the parrot, whose words come from his brain on this device ([brain], see LocalBrain):
 * Brains writes a short line about what just happened and BlueTTS voices it. Whenever there's
 * no brain (not downloaded, switched off, still waking, slow reply) callers fall back to the
 * recorded lines.
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

    private val _thinking = MutableStateFlow(false)
    /** A line is on its way from the brain: thinking dots, so a slow answer isn't a silent one. */
    val thinking: StateFlow<Boolean> = _thinking

    /** True while the parrot listens on its own (after asking a question). */
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    var enabled = true
        set(value) {
            field = value
            if (!value) _status.value = CompanionStatus.OFF
        }
    var language = Language.HEBREW
    /** The parent lets the games listen for answers said out loud (see [listenForAnswer]). */
    var answersOutLoud = true
    /** The time limit's goodbye is up: the game underneath may not listen (or start talking). */
    var paused = false

    /** The game is off screen (MainActivity): no waking the brain, and a reply that comes late isn't played. */
    var backgrounded = false
        set(value) {
            field = value
            if (value) wakeJob?.cancel()
        }

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
     * A parent says [line] wasn't right: it's marked, and they can mail it to Puki's makers
     * ([shareText]). The tablet keeps nothing of it, and what she said stays out of the report.
     */
    fun report(line: SaidLine) {
        if (line.reported) return
        _recentLines.value = _recentLines.value.map { if (it.id == line.id) it.copy(reported = true) else it }
    }

    /**
     * The text of a report a parent may send to Puki's makers: what Puki said and what was
     * happening in the game just before, in the parent's language, with the child's name replaced.
     * What the child said ([SaidLine.heard]) is never in it: that stays on the card, on the tablet.
     * They see and can edit it before their own mail app sends it.
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
        val before = line.situation
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
        val said = brain?.lastSaid() ?: return
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

    init {
        // Left by older versions: reports kept with what she said (see report()), and the lines
        // a parent approved on the PC
        scope.launch(Dispatchers.IO) {
            File(context.filesDir, "reports.jsonl").delete()
            File(context.filesDir, "pack").deleteRecursively()
        }
    }

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

    /** The brain was switched on or off: forget the old status and look again. */
    fun recheck() {
        wakeJob?.cancel()
        _status.value = CompanionStatus.OFF
        wake()
    }

    /** Checks on the brain and wakes it if needed. Cheap to call on every game screen. */
    fun wake() {
        if (!enabled || backgrounded || brain == null || wakeJob?.isActive == true || isReady) return
        wakeJob = scope.launch(Dispatchers.IO) {
            val state = health() ?: run { _status.value = CompanionStatus.OFF; return@launch }
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
        // Already writing a line: no second request behind it (taps would pile up, each answered late);
        // a moment that has its own recorded line plays that instead
        if (!isReady || _thinking.value) {
            fallback()
            return
        }
        scope.launch {
            _thinking.value = true
            val said = try { say(situation) } finally { _thinking.value = false }
            if (!said) fallback()
        }
    }

    /** The child tapped the parrot. */
    fun chat() = react(
        "לחצו על התוכי כדי שידבר. $scene תגיד משהו חמוד וקצר על זה.",
        "The child tapped you so you'd talk. $scene Say something short and cute about it."
    )

    /** The child held the parrot and talked; [wav] is the recording, for the brain's ears. */
    fun listen(wav: ByteArray, context: String = "") {
        if (!isReady || wav.size < 16000) return  // under half a second: nothing to send
        scope.launch {
            _thinking.value = true
            try { play(postListen(wav, context)) } finally { _thinking.value = false }
        }
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
     * spoke is decided here, so tries count even with no brain; when the brain is awake, it
     * also says whether the word was heard ([alsoCounts]: related words, like the
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
     * to match against the answers on screen ([hints]). Null when it
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
            if (!recorder.startOffMain()) return null
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
            return recorder.stopOffMain()
        } finally {
            if (recorder.isRecording) recorder.stopOffMain()
            _listening.value = false
        }
    }

    private fun hasMic() = hasMicPermission(context)

    /** Rough voice-activity check: a few 50 ms windows clearly louder than room noise. */
    private suspend fun hasSpeech(wav: ByteArray): Boolean = withContext(Dispatchers.Default) {
        if (wav.size <= 44) return@withContext false
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
        loud >= 3
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
        if (backgrounded) return false  // (it came after she left: nobody to hear it)
        // A line cut off by another voice never reaches the delete below: clear those first (they
        // may say the child's name, and nothing Puki said is kept)
        val file = withContext(Dispatchers.IO) {
            context.cacheDir.listFiles { f -> f.name.startsWith("companion_") }?.forEach { it.delete() }
            File(context.cacheDir, "companion_${System.nanoTime()}.wav").apply { writeBytes(audio) }
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
     * them on the tablet, so they play at once, and also with the brain off. Redone when the
     * name or gender changes; removed when the name is cleared.
     */
    private fun refreshNameLines() {
        val wanted = if (childName.isBlank()) "" else "$childName|$childGirl|$NAME_LINES_VERSION"
        scope.launch(Dispatchers.IO) {
            if (wanted.isEmpty()) {  // (whatever the marker says: lines that never finished go too)
                voiceDir.listFiles()?.forEach { it.delete() }
                return@launch
            }
            val marker = File(voiceDir, "name_lines.txt")
            val have = runCatching { marker.readText() }.getOrDefault("")
            if (wanted == have) return@launch
            if (!isReady) {
                wake()  // records them as soon as the brain is up
                return@launch
            }
            voiceDir.mkdirs()
            voiceDir.listFiles()?.forEach { it.delete() }
            var all = true
            for ((key, he, en) in nameLines(childName, childGirl)) {
                for ((lang, text) in listOf("he" to he, "en" to en)) {
                    val body = JSONObject().put("text", text).put("lang", lang).toString()
                    val audio = request("POST", "/speak", body.toByteArray(Charsets.UTF_8), timeoutMs = 10000)
                    // No answer is a failure (tried again on the next wake); an empty one means the brain
                    // doesn't speak this language (the phone's, English), which more tries won't change
                    when {
                        audio == null -> all = false
                        audio.isNotEmpty() -> File(voiceDir, "${key}_$lang.ogg").writeBytes(audio)
                    }
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

    private fun health(): String? {
        val bytes = request("GET", "/health", timeoutMs = 1500) ?: return null
        return runCatching { JSONObject(String(bytes)).getString("brain") }.getOrNull()
    }

    /** This device's brain while it's switched on (parent settings, models downloaded); null: recorded lines only. */
    @Volatile var brain: Brain? = null

    /** Returns the response body (empty for "nothing to say"), or null on any failure or with no brain. */
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
        val result = brain?.request(method, full, body, timeoutMs, contentType)
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

/** Puki's line for this moment; with no companion at all, [fallback] (a recorded line). */
fun Companion?.reactOr(he: String, en: String, fallback: () -> Unit) {
    if (this != null) react(he, en, fallback) else fallback()
}
