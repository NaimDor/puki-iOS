package io.github.tomsadeh.puki.brain

import ai.onnxruntime.OrtEnvironment
import android.util.Log
import io.github.tomsadeh.puki.brain.voice.BlueVoice
import io.github.tomsadeh.puki.brain.voice.Renikud
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Puki's brain on this device: answers the same requests as the PC brain (see [Brain]) with
 * Gemma ([Mind]), the BlueTTS voice ([BlueVoice]) and the same personality ([Persona]).
 * It hears with kol-stream ([KolEars], loaded in the background once the rest is ready).
 * For now it speaks Hebrew only: English lines fall back to the recorded ones.
 */
class LocalBrain(private val models: DeviceModels) : Brain {
    private val persona = Persona()
    private var mind: Mind? = null
    private var voice: BlueVoice? = null
    private var ears: KolEars? = null
    private val earsLock = Any()  // around loading the ears (see ears())
    @Volatile private var state = "asleep"

    // Two locks: [lifecycle] for waking and closing (quick), [work] around the models while they
    // answer (slow), so switching Puki off never waits on the main thread for Gemma to finish.
    // [generation] tells a wake that finishes after a close to throw its models away.
    private val lifecycle = Any()
    private val work = Any()
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
            var m: Mind? = null
            try {
                val env = OrtEnvironment.getEnvironment()
                v = BlueVoice(env, models.file("voice"), models.file("voice/noa.json"), Renikud(env, models.file("voice/renikud_int8.onnx")))
                v.speak("שלום")  // warms up the voice before the first real line
                models.cacheDir.mkdirs()
                m = Mind(models.file("gemma.litertlm"), models.cacheDir).also { it.load() }
                synchronized(lifecycle) {
                    if (gen != generation) error("switched off while loading")
                    synchronized(work) { voice = v; mind = m }
                    state = "ready"
                }
                // The ears last (about 17 s on a slow tablet), so Puki is ready sooner; a listen
                // that comes before they're loaded waits for them
                runCatching { synchronized(earsLock) { if (gen == generation) ears() } }
                    .onFailure { Log.e(TAG, "the ears didn't load: ${it.message}") }
            } catch (e: Throwable) {
                Log.e(TAG, "the device brain didn't load: ${e.message}")
                runCatching { m?.close() }
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
        synchronized(work) {
            runCatching { mind?.close() }
            runCatching { voice?.close() }
            mind = null
            voice = null
        }
        synchronized(earsLock) {
            runCatching { ears?.close() }
            ears = null
        }
    }

    @Volatile private var last: Said? = null
    private var heardNow = ""  // what she said, for the line that answers it

    override fun lastSaid(): Said? = last

    override fun request(method: String, path: String, body: ByteArray?, timeoutMs: Int, contentType: String): ByteArray? {
        last = null
        heardNow = ""
        val route = path.substringBefore('?')
        val query = parseQuery(path.substringAfter('?', ""))
        if (method == "GET" && route == "/health") return JSONObject().put("brain", state).toString().toByteArray()
        if (route == "/wake") { wake(); return ByteArray(0) }
        return try {
            synchronized(work) {
                // Read inside the lock: close() may have just freed them
                val mind = mind ?: return null
                val voice = voice ?: return null
                persona.setChild(query["name"]?.firstOrNull() ?: "", query["gender"]?.firstOrNull() ?: "")
                when (route) {
                    "/say" -> {
                        val req = JSONObject(String(body ?: ByteArray(0)))
                        val lang = if (req.optString("lang") == "en") "en" else "he"
                        reply(line(mind, req.optString("situation").take(300), lang), lang, voice)
                    }
                    "/speak" -> {
                        val req = JSONObject(String(body ?: ByteArray(0)))
                        val lang = if (req.optString("lang") == "en") "en" else "he"
                        val text = req.optString("text").take(200).trim()
                        // 0, NaN or a huge number would break the voice
                        val speed = req.optDouble("speed", 0.92).let { if (it.isFinite()) it.coerceIn(0.5, 1.5) else 0.92 }.toFloat()
                        if (text.isEmpty()) ByteArray(0) else reply(text, lang, voice, speed)
                    }
                    "/look" -> {
                        val lang = if (query["lang"]?.firstOrNull() == "en") "en" else "he"
                        var text: String? = null
                        repeat(3) {
                            if (text == null) text = persona.clean(mind.look(persona.systemPrompt(lang), persona.lookPrompt(lang), body ?: ByteArray(0)), lang)
                                ?.takeIf { Safety.replyOk(it) }
                        }
                        reply(text, lang, voice)
                    }
                    "/story" -> story(mind, JSONObject(String(body ?: ByteArray(0))))
                    "/repeat" -> {
                        // Say it with Puki: did she say ?target= (or a related ?alt=)? Just listening, no Gemma
                        val lang = if (query["lang"]?.firstOrNull() == "en") "en" else "he"
                        val target = query["target"]?.firstOrNull()?.take(40) ?: ""
                        val alts = (query["alt"] ?: emptyList()).filter { it.isNotEmpty() }.map { it.take(40) }.take(4)
                        val heard = ears().transcribe(body ?: ByteArray(0))
                        val match = persona.wordMatch(heard, target, alts)
                        JSONObject().put("heard", heard).put("match", match).toString().toByteArray()
                    }
                    "/hear" -> {
                        // She answered a game out loud: just the words (the game decides which answer, SpokenAnswer)
                        // (?hint= is for the PC's Whisper; these ears don't take hints)
                        val heard = ears().transcribe(body ?: ByteArray(0))
                        JSONObject().put("heard", heard).toString().toByteArray()
                    }
                    "/listen" -> {
                        // She talked to Puki (optionally answering ?context=, what he just asked)
                        val lang = if (query["lang"]?.firstOrNull() == "en") "en" else "he"
                        val heard = ears().transcribe(body ?: ByteArray(0))
                        heardNow = heard
                        // Hurt, danger, big questions and the like get a fixed, kind reply (Safety)
                        guardedReply(mind, heard, lang, query["game"]?.firstOrNull()?.take(40) ?: "")?.let { (cat, text) ->
                            Log.i(TAG, "heard something careful: [$cat]")  // (not what she said)
                            return@synchronized reply(text, lang, voice)
                        }
                        var situation = persona.listenSituation(heard, lang)
                        val context = query["context"]?.firstOrNull()?.take(200) ?: ""
                        if (context.isNotEmpty()) situation = "$context $situation"
                        reply(line(mind, situation, lang, listening = true), lang, voice)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$route failed: ${e.javaClass.simpleName}")  // (not the message: it may quote her)
            null
        }
    }

    /** The ears, loaded now if the background load in [wake] hasn't yet (or failed). */
    private fun ears(): KolEars = synchronized(earsLock) {
        ears ?: KolEars(OrtEnvironment.getEnvironment(), models.file(DeviceModels.EARS)).also { ears = it }
    }

    /** A cleaned line from Gemma about [situation], or null (three tries, like the PC). */
    private fun line(mind: Mind, situation: String, lang: String, listening: Boolean = false): String? {
        repeat(3) {
            val system = persona.systemPrompt(lang) + Safety.systemExtra(lang, persona.girl)
            val raw = mind.chat(system, persona.examples(lang), persona.situationPrompt(situation, lang, listening))
            persona.clean(raw, lang)?.takeIf { Safety.replyOk(it) }?.let { persona.said(it); return it }
            Log.i(TAG, "a line didn't pass the filter; trying again")  // (not what it said: it may be about her)
        }
        return null
    }

    /** (category, fixed reply) when what the child said needs a careful answer, else null. */
    private fun guardedReply(mind: Mind, heard: String, lang: String, game: String = ""): Pair<String, String>? {
        return Safety.guardedReply(heard, lang, persona.girl, game,
            gate = { Safety.gateCategory(mind.digit(Safety.classifyPrompt(it))) },
            onError = { Log.e(TAG, "safety gate failed: ${it.javaClass.simpleName}; answering with the neutral line") })
    }

    /** [text] spoken (empty when there's nothing to say; null when it can't be voiced here). */
    private fun reply(text: String?, lang: String, voice: BlueVoice, speed: Float = 0.92f): ByteArray? {
        if (text.isNullOrBlank()) return ByteArray(0)
        if (lang == "en") return ByteArray(0)  // no English voice here yet: the game uses its recorded line
        last = Said(text, heardNow)
        return voice.speak(persona.forVoice(text), speed)
    }

    private fun story(mind: Mind, req: JSONObject): ByteArray? {
        val lang = if (req.optString("lang") == "en") "en" else "he"
        val iconsJson = req.optJSONObject("icons") ?: return null
        // The pictures the story may use (the app sends about 20); capped, it all goes into the prompt
        val icons = iconsJson.keys().asSequence().take(40).associate { it.take(60) to iconsJson.getString(it).take(60) }
        if (icons.isEmpty()) return null
        val played = req.optString("played").take(300).ifEmpty { if (lang == "he") "משחקים" else "games" }
        val schema = JSONObject().put("type", "object").put("required", JSONArray().put("sentences")).put("properties", JSONObject()
            .put("sentences", JSONObject().put("type", "array").put("minItems", 4).put("maxItems", 7).put("items", JSONObject()
                .put("type", "object").put("required", JSONArray().put("text").put("icon")).put("properties", JSONObject()
                    .put("text", JSONObject().put("type", "string"))
                    .put("icon", JSONObject().put("type", "string").put("enum", JSONArray(icons.keys.toList()))))))).toString()
        repeat(2) {
            val raw = mind.json(persona.systemPrompt(lang), persona.storyPrompt(played, icons, lang), schema)
            val items = runCatching { JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1)).getJSONArray("sentences") }.getOrNull()
            if (items != null) {
                val out = JSONArray()
                for (i in 0 until items.length()) {
                    val it = items.getJSONObject(i)
                    val text = persona.clean(it.optString("text"), lang)?.takeIf { t -> Safety.replyOk(t) }
                    val icon = it.optString("icon")
                    if (text != null && icon in icons) out.put(JSONObject().put("text", text).put("icon", icon))
                }
                if (out.length() >= 3) {
                    val last = out.getJSONObject(out.length() - 1).getString("icon")
                    out.put(JSONObject().put("text", persona.storyClosing(lang)).put("icon", if ("ic_bedtime_moon" in icons) "ic_bedtime_moon" else last))
                    return JSONObject().put("sentences", out).toString().toByteArray()
                }
            }
            Log.i(TAG, "a story didn't come out right; trying again")
        }
        return ByteArray(0)
    }

    private fun parseQuery(q: String): Map<String, List<String>> =
        q.split('&').filter { '=' in it }.groupBy({ URLDecoder.decode(it.substringBefore('='), "UTF-8") }, { URLDecoder.decode(it.substringAfter('='), "UTF-8") })

    private companion object {
        const val TAG = "PukiBrain"
    }
}
