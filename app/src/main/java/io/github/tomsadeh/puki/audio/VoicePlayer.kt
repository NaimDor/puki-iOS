package io.github.tomsadeh.puki.audio

import android.content.Context
import android.media.MediaPlayer
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.data.models.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class VoicePlayer(private val context: Context) {

    /** Lines the parrot recorded onto the tablet (e.g. with the child's name). */
    private val personalDir = java.io.File(context.filesDir, "voice")

    private var mediaPlayer: MediaPlayer? = null
    /** A line is loading (prepareAsync) and will start by itself: busy, as if playing. */
    private var preparing = false
    var enabled = true

    private val _speaking = MutableStateFlow(false)
    /** A line is playing (or the next in a row is on its way): Puki's beak moves with this. */
    val speaking: StateFlow<Boolean> = _speaking

    var gender: Gender = Gender.GIRL

    /** The parent's volume for voices, 0..1: the recorded lines and Puki (parent settings). */
    var level = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            runCatching { mediaPlayer?.setVolume(field, field) }
        }

    /**
     * Lines waiting for the one playing to finish, so a new line never cuts the last one off
     * mid-word. Only a couple wait: when a child taps fast, the oldest waiting line is skipped
     * (its onComplete still runs, so whatever was waiting on it carries on).
     */
    private val waiting = ArrayDeque<Pair<() -> Unit, (() -> Unit)?>>()

    private fun enqueue(start: () -> Unit, onComplete: (() -> Unit)?) {
        waiting.addLast(start to onComplete)
        while (waiting.size > MAX_WAITING) waiting.removeFirst().second?.invoke()
    }

    /** The line playing now's caller, told when it ends, or when stop() cuts it short. */
    private var playingDone: (() -> Unit)? = null

    /** The game is on screen (MainActivity); away, lines are dropped rather than spoken to an empty room. */
    var foreground = true

    /** While held (the microphone listening), new lines wait their turn instead of playing. */
    private var held = false

    fun hold() {
        held = true
    }

    /** Let lines play again: the first one waiting starts now. */
    fun unhold() {
        held = false
        if (!isPlaying()) waiting.removeFirstOrNull()?.first?.invoke()
    }

    /** A line ended: tell its caller, then start the next waiting one (unless the caller just did). */
    private fun finished(onComplete: (() -> Unit)?) {
        mediaPlayer = null
        preparing = false
        playingDone = null
        onComplete?.invoke()
        if (mediaPlayer == null) waiting.removeFirstOrNull()?.first?.invoke()
        _speaking.value = mediaPlayer != null
    }

    fun play(voiceKey: String, language: Language, onComplete: (() -> Unit)? = null) {
        if (!enabled || !foreground) {  // (away from the screen: dropped, but its caller still hears it's done)
            onComplete?.invoke()
            return
        }
        if (held || isPlaying()) {
            enqueue({ play(voiceKey, language, onComplete) }, onComplete)
            return
        }
        releaseStale()
        personalFile(voiceKey, language)?.let {
            playFile(it, onComplete)
            return
        }
        val resId = resolve(voiceKey, language)
        if (resId == 0) {
            onComplete?.invoke()
            return
        }
        startAsync(onComplete) { player ->
            context.resources.openRawResourceFd(resId).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        }
    }

    /**
     * Loads a line off the main thread (prepareAsync) and starts it when it's ready, so a tap's
     * animation doesn't wait for the decoder. Until then the player counts as busy.
     */
    private fun startAsync(onComplete: (() -> Unit)?, source: (MediaPlayer) -> Unit) {
        val player = MediaPlayer()
        runCatching {
            source(player)
            player.setVolume(level, level)
            player.setOnCompletionListener {
                it.release()
                if (mediaPlayer === it) finished(onComplete)
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (mediaPlayer === mp) finished(onComplete)
                true
            }
            player.setOnPreparedListener {
                if (mediaPlayer !== it) return@setOnPreparedListener  // stopped while loading
                preparing = false
                it.start()
                _speaking.value = true
            }
            mediaPlayer = player
            playingDone = onComplete
            preparing = true
            player.prepareAsync()
        }.onFailure {
            player.release()  // a broken or half-written file: don't leak the player
            if (mediaPlayer === player || mediaPlayer == null) finished(onComplete)
        }
    }

    /** Plays an audio file (e.g. a companion line from the brain server). */
    fun playFile(file: java.io.File, onComplete: (() -> Unit)? = null) {
        if (!enabled || !foreground) {  // (away from the screen: dropped, but its caller still hears it's done)
            onComplete?.invoke()
            return
        }
        if (held || isPlaying()) {
            enqueue({ playFile(file, onComplete) }, onComplete)
            return
        }
        releaseStale()
        startAsync(onComplete) { it.setDataSource(file.absolutePath) }
    }

    fun playSequence(voiceKeys: List<String>, language: Language, onAllComplete: (() -> Unit)? = null) {
        if (voiceKeys.isEmpty()) {
            onAllComplete?.invoke()
            return
        }
        play(voiceKeys.first(), language) {
            playSequence(voiceKeys.drop(1), language, onAllComplete)
        }
    }

    /**
     * A random praise line for a right answer. Half the time it's one that says
     * the child's name, when those personal lines were generated (they're optional).
     */
    fun praiseKey(language: Language): String? {
        val named = (1..4).map { "voice_praise_name_$it" }.filter { has(it, language) }
        val plain = (listOf("voice_prompt_great_job", "voice_prompt_well_done") + (1..5).map { "voice_praise_$it" })
            .filter { has(it, language) }
        return if (named.isNotEmpty() && (plain.isEmpty() || Random.nextBoolean())) named.random() else plain.randomOrNull()
    }

    /** Plays a random praise line, see [praiseKey]. */
    fun playPraise(language: Language, onComplete: (() -> Unit)? = null) {
        val key = praiseKey(language)
        if (key == null) onComplete?.invoke() else play(key, language, onComplete)
    }

    /** The personal version of a line (e.g. voice_hello_name) if it exists, otherwise the generic one. */
    fun personalKey(voiceKey: String, language: Language): String =
        "${voiceKey}_name".takeIf { has(it, language) } ?: voiceKey

    private fun has(voiceKey: String, language: Language) =
        personalFile(voiceKey, language) != null || resolve(voiceKey, language) != 0

    private fun personalFile(voiceKey: String, language: Language): java.io.File? {
        val suffix = if (language == Language.HEBREW) "_he" else "_en"
        val name = "$voiceKey$suffix.ogg"
        return if (name in personalNames()) java.io.File(personalDir, name) else null
    }

    // Which personal lines exist, listed once and again only when the folder changes (a praise
    // pick asks about a dozen names, and each would be a file check on the main thread)
    private var personalListed = -1L
    private var personalSet = emptySet<String>()
    private fun personalNames(): Set<String> {
        val stamp = personalDir.lastModified()
        if (stamp != personalListed) {
            personalSet = personalDir.list()?.toSet() ?: emptySet()
            personalListed = stamp
        }
        return personalSet
    }

    // For Hebrew, try the gendered variant first (e.g. voice_prompt_bath_he_f),
    // then fall back to the base one (voice_prompt_bath_he)
    private fun resolve(voiceKey: String, language: Language): Int {
        val suffix = when (language) {
            Language.HEBREW -> "_he"
            Language.ENGLISH -> "_en"
        }
        val genderSuffix = when (gender) {
            Gender.GIRL -> "_f"
            Gender.BOY -> "_m"
        }
        val gendered = rawId("$voiceKey$suffix$genderSuffix")
        if (gendered != 0) return gendered
        return rawId("$voiceKey$suffix")
    }

    // getIdentifier is slow (a praise pick asks about a dozen names); the answers never change
    private val ids = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private fun rawId(name: String): Int =
        ids.getOrPut(name) { context.resources.getIdentifier(name, "raw", context.packageName) }

    fun isPlaying(): Boolean = preparing || runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    /** A player that's no longer playing but never said it finished: let it go (keeps the queue moving). */
    private fun releaseStale() {
        mediaPlayer?.let { runCatching { it.release() } }
        mediaPlayer = null
        preparing = false
        _speaking.value = false
    }

    /**
     * Silence now: the line playing and everything waiting (e.g. before the microphone listens).
     * Their callers are still told, as when a fast tap skips a waiting line, so whatever waits on
     * a line (Say It's next word) carries on instead of waiting forever.
     */
    fun stop() {
        val cut = listOfNotNull(playingDone) + waiting.mapNotNull { it.second }
        waiting.clear()
        playingDone = null
        mediaPlayer?.let { runCatching { if (it.isPlaying) it.stop() }; runCatching { it.release() } }
        mediaPlayer = null
        preparing = false
        _speaking.value = false
        cut.forEach { it() }
    }

    fun release() {
        stop()
    }
}

private const val MAX_WAITING = 3
