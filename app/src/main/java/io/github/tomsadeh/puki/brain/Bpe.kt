package io.github.tomsadeh.puki.brain

import org.json.JSONArray
import org.json.JSONObject

/**
 * The byte-level BPE of Puki's own model (Brains), from its tokenizer.json: the one HuggingFace
 * tokenizers writes for a ByteLevel pre-tokenizer and BPE (GPT-2's scheme). Text is split by GPT-2's
 * pattern, each piece's UTF-8 bytes become printable characters, and merges join them by rank.
 */
class Bpe(json: String) {
    private val vocab: Map<String, Int>
    private val tokens: Array<String>
    private val ranks: Map<String, Int>  // "a b" -> rank (lower merges first)
    private val cache = HashMap<String, IntArray>()

    init {
        val model = JSONObject(json).getJSONObject("model")
        val v = model.getJSONObject("vocab")
        vocab = v.keys().asSequence().associateWith { v.getInt(it) }
        tokens = Array(vocab.size) { "" }.also { arr -> vocab.forEach { (t, i) -> if (i < arr.size) arr[i] = t } }
        val merges = model.getJSONArray("merges")
        ranks = HashMap<String, Int>(merges.length() * 2).apply {
            for (i in 0 until merges.length()) {
                val m = merges.get(i)
                put(if (m is JSONArray) m.getString(0) + " " + m.getString(1) else m as String, i)
            }
        }
    }

    fun id(token: String): Int = vocab.getValue(token)

    fun encode(text: String): IntArray {
        val out = ArrayList<Int>()
        for (piece in SPLIT.findAll(text)) {
            val word = piece.value.toByteArray(Charsets.UTF_8).joinToString("") { BYTE_TO_CHAR[it.toInt() and 0xFF].toString() }
            for (id in cache.getOrPut(word) { bpe(word) }) out += id
        }
        return out.toIntArray()
    }

    fun decode(ids: IntArray): String {
        val bytes = ArrayList<Byte>()
        for (id in ids) for (c in tokens.getOrElse(id) { "" }) CHAR_TO_BYTE[c]?.let { bytes += it }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun bpe(word: String): IntArray {
        var parts = word.map { it.toString() }
        while (parts.size > 1) {
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until parts.size - 1) {
                val r = ranks[parts[i] + " " + parts[i + 1]] ?: continue
                if (r < bestRank) { bestRank = r; best = i }
            }
            if (best < 0) break
            val a = parts[best]
            val b = parts[best + 1]
            val merged = ArrayList<String>(parts.size)
            var i = 0
            while (i < parts.size) {
                if (i < parts.size - 1 && parts[i] == a && parts[i + 1] == b) { merged += a + b; i += 2 } else { merged += parts[i]; i++ }
            }
            parts = merged
        }
        return IntArray(parts.size) { vocab[parts[it]] ?: 0 }
    }

    private companion object {
        // GPT-2's split, as tokenizers' ByteLevel pre-tokenizer (use_regex) has it
        val SPLIT = Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")

        // Every byte as a printable character (GPT-2's bytes_to_unicode)
        val BYTE_TO_CHAR: CharArray = CharArray(256).also { map ->
            val printable = (33..126) + (161..172) + (174..255)
            var extra = 0
            for (b in 0 until 256) map[b] = if (b in printable) b.toChar() else (256 + extra++).toChar()
        }
        val CHAR_TO_BYTE: Map<Char, Byte> = (0 until 256).associate { BYTE_TO_CHAR[it] to it.toByte() }
    }
}
