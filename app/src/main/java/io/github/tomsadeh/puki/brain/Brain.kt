package io.github.tomsadeh.puki.brain


/**
 * Where Puki's thinking happens: on this device ([LocalBrain]). The game asks it for things by
 * path, so it doesn't care how the answer is made:
 *
 *  - GET  /health           {"brain": "ready" | "waking" | "asleep"}
 *  - POST /wake             start loading the models
 *  - POST /say              {"situation", "lang"} -> a line about it, spoken (audio)
 *  - POST /speak            {"text", "lang", "speed"} -> that text, spoken (audio)
 *  - POST /look?lang=       a PNG drawing -> a line about it, spoken (audio)
 *  - POST /listen?lang=&context=   a WAV of the child -> a spoken answer (audio)
 *  - POST /repeat?lang=&target=&alt=   a WAV -> {"heard", "match"} (Say it with Puki)
 *  - POST /hear?lang=&hint=        a WAV -> {"heard"} (answering a game out loud, see [SpokenAnswer])
 *  - POST /story            {"played", "icons", "lang"} -> {"sentences": [{"text", "icon"}]}
 *
 * Every POST also carries ?name=&gender= (the child, from the app's settings).
 */
/** What a brain last said aloud, and what it heard the child say just before (for reports). */
data class Said(val text: String, val heard: String)

interface Brain {
    /** The response body (audio or JSON), empty for "nothing to say", or null if it failed. */
    fun request(method: String, path: String, body: ByteArray?, timeoutMs: Int, contentType: String): ByteArray?

    /** The line this thread's last request had Puki say, or null if it said nothing (a parent may report it). */
    fun lastSaid(): Said? = null
}
