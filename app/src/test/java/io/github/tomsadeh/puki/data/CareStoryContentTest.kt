package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.content.CareContent
import io.github.tomsadeh.puki.data.content.StoryContent
import io.github.tomsadeh.puki.data.content.allAnimals
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The caring game's and the story's sentences against the trainer's (care_story.json, from train/games.py). */
class CareStoryContentTest {
    private val fixture = JSONObject(javaClass.classLoader!!.getResource("care_story.json")!!.readText())

    @Test
    fun caringSentencesAreAsTrained() {
        val rows = fixture.getJSONArray("care")
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val patient = CareContent.PATIENTS.first { it.id == r.getString("patient") }
            val problem = CareContent.PROBLEMS.first { it.id == r.getString("problem") }
            val item = if (r.isNull("item")) null else r.getString("item")
            assertEquals(r.getString("he"), CareContent.input(false, patient, problem, item))
            assertEquals(r.getString("en"), CareContent.input(true, patient, problem, item))
        }
    }

    @Test
    fun storySentencesAreAsTrained() {
        val animals = allAnimals.associateBy { it.id }
        val rows = fixture.getJSONArray("story")
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val place = StoryContent.PLACES.entries.first { it.value.second == r.getString("place") }.key
            val thing = StoryContent.THINGS.entries.first { it.value.second == r.getString("thing") }.key
            val plan = StoryContent.Plan(animals.getValue(r.getString("hero")), place, animals.getValue(r.getString("friend")), thing)
            val n = minOf(r.getInt("so_far"), 2)
            assertEquals(r.getString("he"), StoryContent.input(plan, false, listOf("משפט אחד.", "משפט שני.").take(n), r.getInt("step")))
            assertEquals(r.getString("en"), StoryContent.input(plan, true, listOf("One sentence.", "Two sentences.").take(n), r.getInt("step")))
        }
    }
}
