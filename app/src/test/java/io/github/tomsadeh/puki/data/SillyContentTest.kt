package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.content.SillyContent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Silly Puki's sentences against the trainer's (silly.json, written from train/games.py and build.py). */
class SillyContentTest {
    private val fixture = JSONObject(javaClass.classLoader!!.getResource("silly.json")!!.readText())

    @Test
    fun inputsAreTheOnesTheBrainLearned() {
        val want = fixture.getJSONArray("inputs").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .map { listOf(it.getString("group"), it.getString("he"), it.getString("en"), it.getString("truth_he"), it.getString("truth_en")) }.toSet()
        val got = SillyContent.all.map { listOf(it.group, it.he, it.en, it.truthHe, it.truthEn) }.toSet()
        assertEquals(want - got, emptySet<List<String>>())
        assertEquals(got - want, emptySet<List<String>>())
    }

    @Test
    fun truthCheckAgreesWithTheTrainer() {
        val cases = fixture.getJSONArray("said_truth")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            // The one place the app is stricter than the trainer: a feminine or plural color says the color too
            // ("בננה צהובה" says "צהוב"), so a silly line can't "get wrong" the very color it should
            val stricter = c.getString("line") == "בננה צהובה!" && c.getString("truth") == "צהוב"
            assertEquals(c.toString(), c.getBoolean("said") || stricter, SillyContent.saysTruth(c.getString("line"), c.getString("truth")))
        }
    }

    @Test
    fun answersAreWordedAsTrained() {
        val trained = fixture.getJSONArray("fix").let { a -> (0 until a.length()).map { a.getJSONArray(it).getString(1) } }.toSet()
        val made = listOf(
            SillyContent.fixInput(false, "הפרה עושה מיאו!", "מו", SillyContent.Answer.RIGHT),
            SillyContent.fixInput(false, "הפרה עושה מיאו!", "מו", SillyContent.Answer.NOTHING),
            SillyContent.fixInput(true, "The cow says meow!", "moo", SillyContent.Answer.RIGHT),
            SillyContent.fixInput(true, "The cow says meow!", "moo", SillyContent.Answer.NOTHING),
        )
        for (m in made) assert(m in trained) { "not as trained: $m" }
    }
}
