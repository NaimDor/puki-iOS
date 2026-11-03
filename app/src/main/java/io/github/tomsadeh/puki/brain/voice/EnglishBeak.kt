package io.github.tomsadeh.puki.brain.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer

/**
 * Puki's English reading model (train/en_read_train.py, 2.7M parameters): English to the phonemes the voice
 * was trained on (espeak's, en-us). Every word Puki says is in the table, read by espeak inside his own
 * lines (the small words weak: "a" ɐ, "to" tə); the model reads the rest, like the child's name: each letter
 * gets three output slots, and the CTC reading drops repeats and blanks. [dir] holds reader_int8.onnx,
 * symbols.json and words.tsv.
 */
class EnglishBeak(private val env: OrtEnvironment, dir: File, threads: Int = 2) {
    private val session: OrtSession = env.createSession(File(dir, "reader_int8.onnx").path, OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    })
    private val letters: String
    private val maxLetters: Int
    private val symbols: Array<String>
    private val table: Map<String, String>

    init {
        val s = JSONObject(File(dir, "symbols.json").readText())
        letters = s.getString("letters")
        maxLetters = s.getInt("max_letters")
        symbols = s.getJSONArray("symbols").let { a -> Array(a.length()) { a.getString(it) } }
        table = File(dir, "words.tsv").readLines().mapNotNull { l ->
            val tab = l.indexOf('\t')
            if (tab > 0) l.substring(0, tab) to l.substring(tab + 1) else null
        }.toMap()
    }

    /** [text] read aloud as IPA, words joined by spaces, punctuation kept where it was (as espeak writes it). */
    fun phonemize(text: String): String {
        val out = StringBuilder()
        for (m in TOKEN.findAll(spellNumbers(text))) {
            val tok = m.value
            when {
                tok.isBlank() -> out.append(' ')
                WORD.matches(tok) -> tok.lowercase().let { out.append(table[it] ?: read(it)) }
                else -> out.append(tok)
            }
        }
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** One word the table doesn't have, read by the model. */
    fun read(word: String): String {
        val w = word.filter { it in letters }.take(maxLetters)
        if (w.isEmpty()) return ""
        val ids = LongArray(w.length + 2) { 1L }  // (1 marks the word's edges)
        for (i in w.indices) ids[i + 1] = (letters.indexOf(w[i]) + 2).toLong()
        val logp: Array<FloatArray>
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
            session.run(mapOf("ids" to input)).use { r ->
                @Suppress("UNCHECKED_CAST")
                logp = (r.get("logp").get().value as Array<Array<FloatArray>>)[0]
            }
        }
        val sb = StringBuilder()
        var prev = 0
        for (slot in logp) {
            var best = 0
            for (k in slot.indices) if (slot[k] > slot[best]) best = k
            if (best != prev && best != 0) sb.append(symbols[best])
            prev = best
        }
        return sb.toString()
    }

    fun close() = runCatching { session.close() }

    private companion object {
        val WORD = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")
        val TOKEN = Regex("[A-Za-z]+(?:'[A-Za-z]+)?|[^\\sA-Za-z]+|\\s+")
        val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")

        /** The numbers Puki says (he counts up to ten or so) as words; anything bigger is read digit by digit. */
        fun spellNumbers(text: String) = Regex("\\d+").replace(text) { m ->
            m.value.toIntOrNull()?.takeIf { it < NUMBER_WORDS.size }?.let { NUMBER_WORDS[it] }
                ?: m.value.map { NUMBER_WORDS[it - '0'] }.joinToString(" ")
        }
    }
}
