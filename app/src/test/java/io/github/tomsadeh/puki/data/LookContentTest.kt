package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.content.LookContent
import io.github.tomsadeh.puki.data.content.LookFacts
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Puki's eyes against the trainer's (look.json, written from train/games.py look_inputs and look_ok). */
class LookContentTest {
    private val fixture = JSONObject(javaClass.classLoader!!.getResource("look.json")!!.readText())
    private val inputs = fixture.getJSONArray("inputs").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    private fun facts(x: JSONObject) = LookFacts(
        page = x.getString("page"),
        letter = x.optJSONArray("letter")?.let { it.getString(0) to it.getString(1) },
        colors = strings(x.getJSONArray("colors")),
        stamps = x.getJSONArray("stamps").let { a -> (0 until a.length()).map { a.getJSONArray(it).getString(0) to a.getJSONArray(it).getInt(1) } },
        cover = x.getInt("cover"),
        strokes = if (x.isNull("strokes")) null else x.getInt("strokes"),
    )

    @Test
    fun drawingsAreDescribedAsTrained() {
        for (x in inputs) {
            val got = LookContent.describe(facts(x))
            assertEquals(x.getString("he"), got.he)
            assertEquals(x.getString("en"), got.en)
            assertEquals(strings(x.getJSONArray("present_he")), got.presentHe)
            assertEquals(strings(x.getJSONArray("present_en")), got.presentEn)
            assertEquals(strings(x.getJSONArray("absent_he")), got.absentHe)
            assertEquals(strings(x.getJSONArray("absent_en")), got.absentEn)
        }
    }

    @Test
    fun theCheckAgreesWithTheTrainer() {
        val byGroup = inputs.associateBy { it.getString("group") }
        val checks = fixture.getJSONArray("ok")
        for (i in 0 until checks.length()) {
            val c = checks.getJSONObject(i)
            val x = byGroup.getValue(c.getString("x"))
            val lang = c.getString("lang")
            val ok = LookContent.ok(c.getString("line"), strings(x.getJSONArray("present_$lang")), strings(x.getJSONArray("absent_$lang")))
            assertEquals(c.toString(), c.getBoolean("ok"), ok)
        }
    }
}
