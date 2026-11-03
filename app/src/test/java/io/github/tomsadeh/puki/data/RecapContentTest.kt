package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.content.DayEvent
import io.github.tomsadeh.puki.data.content.RecapContent
import io.github.tomsadeh.puki.data.content.allAnimals
import io.github.tomsadeh.puki.data.content.hebrewLetters
import io.github.tomsadeh.puki.data.content.englishLetters
import io.github.tomsadeh.puki.data.content.level1CountingRounds
import io.github.tomsadeh.puki.data.content.level2CountingRounds
import io.github.tomsadeh.puki.data.content.level3CountingRounds
import io.github.tomsadeh.puki.data.content.level3Colors
import io.github.tomsadeh.puki.data.content.level3Shapes
import io.github.tomsadeh.puki.ui.stickers.allStickers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bedtime recap against the trainer's (recap.json, written from train/games.py day_events and events_named). */
class RecapContentTest {
    private val fixture = JSONObject(javaClass.classLoader!!.getResource("recap.json")!!.readText())

    /** Every event the app can record, the way it records them. */
    private val possible: Set<DayEvent> = buildSet {
        allAnimals.forEach { a -> RecapContent.fed(a)?.let(::add); add(RecapContent.found(a)) }
        level3Colors.forEach { add(RecapContent.color(it)); add(RecapContent.drew(it)) }
        add(RecapContent.drew(null))
        level3Shapes.forEach { add(RecapContent.shape(it)) }
        (level1CountingRounds + level2CountingRounds + level3CountingRounds).forEach { add(RecapContent.counted(it)) }
        (hebrewLetters + englishLetters).forEach { add(RecapContent.said(it.exampleWord)) }
        allStickers.forEach { add(RecapContent.sticker(it.name)) }
        addAll(RecapContent.FIXED.values)
    }

    @Test
    fun theTrainersEventsAreWordedTheSame() {
        val events = fixture.getJSONArray("events")
        // The trainer's days also have the teddy's teeth, bath and clothes, which the app's games aren't
        val notOurs = setOf("צחצחת שיניים לדובי", "רחצת את הדובי באמבטיה", "הלבשת את הדובי")
        for (i in 0 until events.length()) {
            val e = DayEvent(events.getJSONArray(i).getString(0), events.getJSONArray(i).getString(1))
            if (e.he in notOurs) continue
            assertTrue("not as trained: $e", e in possible)
        }
    }

    @Test
    fun theCheckAgreesWithTheTrainer() {
        val checks = fixture.getJSONArray("named")
        for (i in 0 until checks.length()) {
            val c = checks.getJSONObject(i)
            assertEquals(c.toString(), c.getInt("named"), RecapContent.eventsNamed(c.getString("line"), c.getString("input")))
        }
    }
}
