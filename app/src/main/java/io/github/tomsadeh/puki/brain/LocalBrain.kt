package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import io.github.tomsadeh.puki.brain.voice.Beak
import io.github.tomsadeh.puki.brain.voice.BlueVoice
import io.github.tomsadeh.puki.brain.voice.VoiceText
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.concurrent.thread

/**
 * Puki's brain on this device, all of it Puki's own models but the voice: answers the game's requests
 * (see [Brain]) with Brains (the small model that writes his lines, [Brains]), Beak
 * (which reads them, [Beak]), the BlueTTS voice ([BlueVoice]) and the same filter ([Persona], [Safety]).
 * It hears with kol-stream ([KolEars], loaded in the background once the rest is ready). It speaks
 * Hebrew only for now: English lines fall back to the recorded ones. Drawings and bedtime stories aren't
 * answered here yet (the game plays its own lines): drawings wait for Puki's eyes.
 */
class LocalBrain(private val models: DeviceModels) : Brain {
    private val persona = Persona()
    private var brains: Brains? = null
    private var beak: Beak? = null
    private var voice: BlueVoice? = null
    private var ears: KolEars? = null
    private val earsLock = Any()  // around loading the ears (see ears())
    @Volatile private var state = "asleep"

    // Two locks: [lifecycle] for waking and closing (quick), [work] around the models while they
    // answer (slow), so switching Puki off never waits on the main thread for a line to finish.
    // [generation] tells a wake that finishes after a close to throw its models away.
    private val lifecycle = Any()
    private val work = ReentrantLock()
    @Volatile private var generation = 0

    /** Loads the models (in the background; the game polls /health until "ready"). */
    fun wake() {
        val gen: Int
        synchronized(lifecycle) {
            if (state != "asleep" || !models.ready()) return
            state = "waking"
            gen = generation
        }
        thread(name = "puki-brain") {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)  // (the game comes first)
            var v: BlueVoice? = null
            var k: Beak? = null
            var b: Brains? = null
            try {
                val env = OrtEnvironment.getEnvironment()
                v = BlueVoice(env, models.file("voice"), models.file("voice/noa.json"), null, threads = 4)
                k = Beak(env, models.file(DeviceModels.BEAK))
                v.voice(k.phonemize("שלום", girl = true))  // warms up the voice before the first real line
                b = Brains(env, models.file(DeviceModels.BRAINS))
                synchronized(lifecycle) {
                    if (gen != generation) error("switched off while loading")
                    work.withLock { voice = v; beak = k; brains = b }
                    state = "ready"
                }
                // The ears last (about 17 s on a slow tablet), so Puki is ready sooner; a listen
                // that comes before they're loaded waits for them
                runCatching { synchronized(earsLock) { if (gen == generation) ears() } }
                    .onFailure { Log.e(TAG, "the ears didn't load: ${it.message}") }
            } catch (e: Throwable) {
                Log.e(TAG, "the device brain didn't load: ${e.message}")
                runCatching { b?.close() }
                runCatching { k?.close() }
                runCatching { v?.close() }
                synchronized(lifecycle) { if (gen == generation) state = "asleep" }
            }
        }
    }

    /** Frees the models' memory. Waits for an answer in progress, so call it off the main thread. */
    fun close() {
        synchronized(lifecycle) {
            generation++
            state = "asleep"
        }
        work.withLock {
            runCatching { brains?.close() }
            runCatching { beak?.close() }
            runCatching { voice?.close() }
            brains = null
            beak = null
            voice = null
        }
        synchronized(earsLock) {
            runCatching { ears?.close() }
            ears = null
        }
    }

    // Per thread, so one request can't read or clear another's: what Puki said (the caller reads it
    // right after its request), and what she said, for the line that answers it
    private val last = ThreadLocal<Said?>()
    private val heardNow = ThreadLocal<String>()

    override fun lastSaid(): Said? = last.get()

    override fun request(method: String, path: String, body: ByteArray?, timeoutMs: Int, contentType: String): ByteArray? {
        last.set(null)
        heardNow.set("")
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L  // the game stops waiting then
        val route = path.substringBefore('?')
        val query = parseQuery(path.substringAfter('?', ""))
        if (method == "GET" && route == "/health") return JSONObject().put("brain", state).toString().toByteArray()
        if (route == "/wake") { wake(); return ByteArray(0) }
        // Only the ears: no waiting behind a line being written (Say It's word check is quick)
        if (route == "/repeat" || route == "/hear") return if (state == "ready") listenOnly(route, query, body) else null
        return try {
            // A line in progress holds the models; past the game's deadline, it has given up anyway
            if (!work.tryLock(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) return null
            try {
                // Read inside the lock: close() may have just freed them
                val brains = brains ?: return null
                val beak = beak ?: return null
                val voice = voice ?: return null
                persona.setChild(query["name"]?.firstOrNull() ?: "", query["gender"]?.firstOrNull() ?: "")
                when (route) {
                    "/say" -> {
                        val req = JSONObject(String(body ?: ByteArray(0)))
                        val lang = if (req.optString("lang") == "en") "en" else "he"
                        val situation = req.optString("situation").take(300)
                        // A tap on Puki comes as the scene with "say something short and cute" (Companion.chat)
                        val task = if (situation.startsWith("לחצו על התוכי") || situation.startsWith("The child tapped")) "tap" else "moment"
                        reply(line(brains, task, situation, lang), lang, beak, voice)
                    }
                    "/speak" -> {
                        val req = JSONObject(String(body ?: ByteArray(0)))
                        val lang = if (req.optString("lang") == "en") "en" else "he"
                        val text = req.optString("text").take(200).trim()
                        // 0, NaN or a huge number would break the voice
                        val speed = req.optDouble("speed", 0.92).let { if (it.isFinite()) it.coerceIn(0.5, 1.5) else 0.92 }.toFloat()
                        if (text.isEmpty()) ByteArray(0) else reply(text, lang, beak, voice, speed)
                    }
                    "/listen" -> {
                        // She talked to Puki: the careful reply if it needs one (Safety), else his answer
                        val lang = if (query["lang"]?.firstOrNull() == "en") "en" else "he"
                        val heard = ears().transcribe(body ?: ByteArray(0))
                        heardNow.set(heard)
                        val guarded = Safety.guardedReply(heard, lang, persona.girl, query["game"]?.firstOrNull()?.take(40) ?: "",
                            gate = { Safety.gateCategory(brains.gate(it, lang, persona.girl), Safety.THRESHOLD) },
                            onError = { Log.e(TAG, "safety gate failed: ${it.javaClass.simpleName}; answering with the neutral line") })
                        if (guarded != null) {
                            Log.i(TAG, "heard something careful: [${guarded.first}]")  // (not what she said)
                            reply(guarded.second, lang, beak, voice)
                        } else {
                            reply(line(brains, "talk", heard.take(200), lang), lang, beak, voice)
                        }
                    }
                    else -> null  // (/look and /story: not here yet; the game plays its own lines)
                }
            } finally {
                work.unlock()
            }
        } catch (e: Exception) {
            Log.e(TAG, "$route failed: ${e.javaClass.simpleName}")  // (not the message: it may quote her)
            null
        }
    }

    /** The requests that only need the ears: Say It's word check, and an answer said out loud. */
    private fun listenOnly(route: String, query: Map<String, List<String>>, body: ByteArray?): ByteArray? = try {
        val heard = ears().transcribe(body ?: ByteArray(0))
        if (route == "/repeat") {
            // Say it with Puki: did she say ?target= (or a related ?alt=)?
            val target = query["target"]?.firstOrNull()?.take(40) ?: ""
            val alts = (query["alt"] ?: emptyList()).filter { it.isNotEmpty() }.map { it.take(40) }.take(4)
            JSONObject().put("heard", heard).put("match", persona.wordMatch(heard, target, alts)).toString().toByteArray()
        } else {
            // She answered a game out loud: just the words (the game decides which answer, SpokenAnswer)
            JSONObject().put("heard", heard).toString().toByteArray()
        }
    } catch (e: Exception) {
        Log.e(TAG, "$route failed: ${e.javaClass.simpleName}")
        null
    }

    /** The ears, loaded now if the background load in [wake] hasn't yet (or failed). */
    private fun ears(): KolEars = synchronized(earsLock) {
        ears ?: KolEars(OrtEnvironment.getEnvironment(), models.file(DeviceModels.EARS)).also { ears = it }
    }

    /** A line from Brains for [input] that passes Puki's filter, or null (three tries, like the PC). */
    private fun line(brains: Brains, task: String, input: String, lang: String): String? {
        repeat(3) {
            val raw = brains.line(task, lang, persona.girl, input) ?: return@repeat
            persona.clean(withName(raw), lang)?.takeIf { Safety.replyOk(it) }?.let { persona.said(it); return it }
            Log.i(TAG, "a line didn't pass the filter; trying again")  // (not what it said: it may be about her)
        }
        return null
    }

    /** Brains writes "@name" where the child's name goes (as it was trained); with no name, it goes. */
    private fun withName(raw: String): String {
        if (persona.name.isNotEmpty()) return raw.replace("@name", persona.name)
        return raw.replace(Regex(",?\\s*@name\\s*,?"), " ").replace(Regex("\\s+([!?.,])"), "$1").replace(Regex("\\s+"), " ").trim()
    }

    /** [text] spoken (empty when there's nothing to say; null when it can't be voiced here). */
    private fun reply(text: String?, lang: String, beak: Beak, voice: BlueVoice, speed: Float = 0.92f): ByteArray? {
        if (text.isNullOrBlank()) return ByteArray(0)
        if (lang == "en") return ByteArray(0)  // no English voice here yet: the game uses its recorded line
        last.set(Said(text, heardNow.get() ?: ""))
        val prepared = VoiceText.prepareTextForSynthesis(persona.forVoice(text))
        if (prepared.none { it in '֐'..'׿' }) return ByteArray(0)  // nothing Hebrew to say
        return voice.speakPhonemes(beak.phonemize(prepared, persona.girl), speed)
    }

    private fun parseQuery(q: String): Map<String, List<String>> =
        q.split('&').filter { '=' in it }.groupBy({ URLDecoder.decode(it.substringBefore('='), "UTF-8") }, { URLDecoder.decode(it.substringAfter('='), "UTF-8") })

    private companion object {
        const val TAG = "PukiBrain"
    }
}
