package io.github.tomsadeh.puki.brain.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer
import java.text.Normalizer

/**
 * Puki's reading model (train/read_train.py, 2.3M parameters): Hebrew letters to IPA, for a girl or a
 * boy. It predicts a consonant, a vowel and stress for each letter, as RenikudPlus does ([Renikud]), and
 * the same rules make the phonemes; trained on Puki's own reviewed lines, it reads them better. [dir]
 * holds beak_int8.onnx and vocab.json (the consonants, vowels and which a letter may take).
 */
class Beak(private val env: OrtEnvironment, dir: File, threads: Int = 2) {
    private val session: OrtSession = env.createSession(File(dir, "beak_int8.onnx").path, OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    })
    private val consonants: Map<Int, String>
    private val vowels: Map<Int, String>
    private val allowed: Map<Char, IntArray>
    private val geresh: Map<Char, String>

    init {
        val v = JSONObject(File(dir, "vocab.json").readText())
        fun strings(key: String) = v.getJSONObject(key).let { o -> o.keys().asSequence().associate { it.toInt() to o.getString(it) } }
        consonants = strings("consonants")
        vowels = strings("vowels")
        allowed = v.getJSONObject("allowed").let { o -> o.keys().asSequence().associate { k -> k[0] to o.getJSONArray(k).let { a -> IntArray(a.length()) { a.getInt(it) } } } }
        geresh = v.getJSONObject("geresh").let { o -> o.keys().asSequence().associate { it[0] to o.getString(it) } }
    }

    /** [text] read aloud as IPA (non-Hebrew characters pass through, as Renikud leaves them). */
    fun phonemize(text: String, girl: Boolean): String {
        val t = clean(text)
        if (t.isEmpty()) return ""
        val ids = LongArray(t.length + 1)
        ids[0] = (if (girl) GIRL else BOY).toLong()
        for (i in t.indices) ids[i + 1] = (INDEX[t[i]] ?: UNK).toLong()
        val consonantLogits: Array<FloatArray>
        val vowelLogits: Array<FloatArray>
        val stressLogits: FloatArray
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
            session.run(mapOf("ids" to input)).use { r ->
                @Suppress("UNCHECKED_CAST")
                consonantLogits = (r.get("consonant").get().value as Array<Array<FloatArray>>)[0]
                @Suppress("UNCHECKED_CAST")
                vowelLogits = (r.get("vowel").get().value as Array<Array<FloatArray>>)[0]
                @Suppress("UNCHECKED_CAST")
                stressLogits = (r.get("stress").get().value as Array<FloatArray>)[0]
            }
        }
        val cons = IntArray(t.length) { i ->
            val options = allowed[t[i]]
            if (options == null) argmax(consonantLogits[i]) else options.maxBy { consonantLogits[i][it] }
        }
        val vowel = IntArray(t.length) { i -> argmax(vowelLogits[i]) }
        // In each word, the vowel-bearing letter the model is surest of carries the stress
        val stressed = BooleanArray(t.length)
        for (word in Regex("\\S+").findAll(t)) {
            word.range.filter { vowel[it] != NOTHING }.maxByOrNull { stressLogits[it] }?.let { stressed[it] = true }
        }
        val out = StringBuilder()
        for (i in t.indices) {
            val ch = t[i]
            if (ch !in 'א'..'ת') {
                if (ch != '\'' && ch != '"') out.append(ch)
                continue
            }
            var c = consonants[cons[i]] ?: "∅"
            if (ch in geresh && i + 1 < t.length && t[i + 1] == '\'') c = geresh.getValue(ch)
            val v = if (vowel[i] == NOTHING) "" else vowels[vowel[i]] ?: ""
            val mark = if (stressed[i] && v.isNotEmpty()) "ˈ" else ""
            val final = i + 1 >= t.length || !t[i + 1].isLetter()
            if (ch == 'ח' && final && v == "a") {
                out.append(mark).append("aχ")  // (a furtive patah: the vowel before the χ)
                continue
            }
            if (c != "∅") out.append(c)
            out.append(mark).append(v)
        }
        return out.toString()
    }

    fun close() = session.close()

    private companion object {
        // As train/read_label.py: padding, space, the letters, punctuation; then unknown, girl, boy
        val ALPHABET = "\u0000 " + ('א'..'ת').joinToString("") + ".,!?:;-'\"()"
        val INDEX = ALPHABET.withIndex().associate { (i, c) -> c to i }
        val UNK = ALPHABET.length
        val GIRL = ALPHABET.length + 1
        val BOY = ALPHABET.length + 2
        const val NOTHING = 0

        /** As train/read_tags.clean: one kind of geresh and gershayim, decomposed, single spaces. */
        fun clean(text: String): String {
            val g = Regex("[״\"]").replace(Regex("[׳'`´]").replace(text, "'"), "\"")
            return Normalizer.normalize(g, Normalizer.Form.NFD).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        }

        fun argmax(a: FloatArray): Int {
            var best = 0
            for (i in 1 until a.size) if (a[i] > a[best]) best = i
            return best
        }
    }
}
