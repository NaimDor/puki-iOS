package io.github.tomsadeh.puki.brain


/**
 * Where Puki's thinking happens: the brain server on the family PC (brain/brain.py), or this
 * device itself ([LocalBrain]). Both answer the same requests, so the rest of the game doesn't
 * care which one it talks to:
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

/**
 * The brain on the family PC, over HTTPS on the home network, with the pairing code. Only the
 * PC whose certificate matches [pin] is talked to ([PinnedTls]); with no pin yet, the
 * certificate of the first PC that accepts the code is handed to [learnPin].
 */
class HttpBrain(
    private val baseUrl: () -> String,
    private val code: () -> String,
    private val pin: () -> String,
    private val learnPin: (String) -> Unit
) : Brain {
    // Per thread: the caller reads it right after its own request, so another request can't swap it
    private val last = ThreadLocal<Said?>()

    override fun lastSaid(): Said? = last.get()

    override fun request(method: String, path: String, body: ByteArray?, timeoutMs: Int, contentType: String): ByteArray? =
        runCatching {
            last.set(null)
            val pinned = pin()
            var seen = ""
            val conn = PinnedTls.open(baseUrl() + path, pinned) { seen = it }
            conn.requestMethod = method
            conn.connectTimeout = 1500
            conn.setRequestProperty("X-Puki-Code", code().trim())
            conn.readTimeout = timeoutMs
            // How long the game will wait: the PC skips the work once nobody's waiting for it
            conn.setRequestProperty("X-Puki-Deadline-Ms", timeoutMs.toString())
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body) }
            }
            try {
                val status = conn.responseCode
                if (pinned.isEmpty() && seen.isNotEmpty() && (status == 200 || status == 204)) learnPin(seen)
                when (status) {
                    200 -> conn.inputStream.use { it.readBytes() }.also {
                        // The words of what Puki said (and what he heard), as the brain sends them
                        val text = conn.getHeaderField("X-Text")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                        val heard = conn.getHeaderField("X-Heard")?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
                        if (text.isNotBlank()) last.set(Said(text, heard))
                    }
                    204 -> ByteArray(0)
                    else -> null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
}
