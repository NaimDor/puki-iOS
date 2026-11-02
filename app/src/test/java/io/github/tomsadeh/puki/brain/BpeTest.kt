package io.github.tomsadeh.puki.brain

import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Kotlin tokenizer gives exactly what HuggingFace tokenizers gives for Brains' prompts and lines
 * (brains_bpe_cases.json, written by tokenizers itself), and decoding gives the text back.
 */
class BpeTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val bpe = Bpe(resource("brains_tokenizer.json"))

    @Test fun `same tokens as tokenizers, and back again`() {
        val cases = JSONArray(resource("brains_bpe_cases.json"))
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val text = c.getString("text")
            val want = c.getJSONArray("ids").let { a -> IntArray(a.length()) { a.getInt(it) } }
            assertArrayEquals("case $i: $text", want, bpe.encode(text))
            assertEquals("case $i", text, bpe.decode(want))
        }
    }

    @Test fun `the end token`() {
        assertEquals(0, bpe.id("<|endoftext|>"))
    }
}
