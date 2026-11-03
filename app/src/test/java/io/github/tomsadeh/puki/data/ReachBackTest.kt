package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.content.Moment
import io.github.tomsadeh.puki.data.content.ReachBack
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Reaching back against the PC brain's words (reach.json, from brain/memory.py on the trainer's reach_back rows). */
class ReachBackTest {
    private val rows = JSONArray(javaClass.classLoader!!.getResource("reach.json")!!.readText())
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }.toSet()

    @Test
    fun contentWordsAreThePcBrains() {
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            assertEquals(r.getString("input"), strings(r.getJSONArray("input_words")), ReachBack.contentWords(r.getString("input")))
            assertEquals(r.getString("earlier"), strings(r.getJSONArray("earlier_words")), ReachBack.contentWords(r.getString("earlier").substringAfter(":")))
            assertEquals(r.getString("line"), strings(r.getJSONArray("line_words")), ReachBack.contentWords(r.getString("line")))
        }
    }

    @Test
    fun anEarlierMomentOfTheSameGameIsFound() {
        val today = LocalDate.of(2026, 11, 3)
        fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = at(0, 18)
        val history = listOf(
            Moment(at(1, 10), "במשחק הצללים מצאו של מי הצל: תרנגולת!", "In the shadow game they found whose shadow it was: the chicken!", "animals"),
            Moment(at(0, 9), "במשחק הצבעים בחרו שחור, אבל חיפשנו חום.", "x", "colors"),
            Moment(at(2, 9), "ספרו 5 פרפרים!", "The child counted 5 butterflies!", "counting"),
        )
        val shadow = ReachBack.related("במשחק הצללים מצאו של מי הצל: ברווז!", "animals", history, now, today)
        assertNotNull(shadow)
        assertEquals("אתמול", shadow!!.first.first)
        assertEquals("קודם היום", ReachBack.related("במשחק הצבעים בחרו חום, אבל חיפשנו ירוק.", "colors", history, now, today)!!.first.first)
        assertEquals("לפני יומיים", ReachBack.related("ספרו 3 דגים!", "counting", history, now, today)!!.first.first)
        // Another game, or just now: nothing to reach back to
        assertNull(ReachBack.related("במשחק הצללים מצאו של מי הצל: ברווז!", "peekaboo", history, now, today))
        assertNull(ReachBack.related("במשחק הצללים מצאו של מי הצל: ברווז!", "animals", history, at(1, 10) + 60_000, today.minusDays(1)))
    }

    @Test
    fun theLineMustNameTheEarlierThing() {
        val input = "במשחק הצבעים בחרו חום, אבל חיפשנו ירוק."
        val earlier = "אתמול: במשחק הצבעים בחרו שחור, אבל חיפשנו חום."
        assert(ReachBack.namesEarlier("כמו אתמול עם שחור!", input, earlier))
        assert(!ReachBack.namesEarlier("יש, ירוק!", input, earlier))
    }
}
