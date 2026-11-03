package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.content.Moment
import io.github.tomsadeh.puki.data.content.ReachBack
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun anEarlierMomentOfThisSessionIsFound() {
        val now = 10_000_000L
        fun minutesAgo(m: Int) = now - m * 60_000L
        val history = listOf(
            Moment(minutesAgo(12), "במשחק הצללים מצאו של מי הצל: תרנגולת!", "In the shadow game they found whose shadow it was: the chicken!", "animals"),
            Moment(minutesAgo(9), "במשחק הצבעים בחרו שחור, אבל חיפשנו חום.", "x", "colors"),
            Moment(minutesAgo(6), "ספרו 5 פרפרים!", "The child counted 5 butterflies!", "counting"),
            Moment(minutesAgo(1), "ספרו 4 תפוחים!", "The child counted 4 apples!", "counting"),
        )
        assertEquals("קודם היום", ReachBack.related("במשחק הצללים מצאו של מי הצל: ברווז!", "animals", history, now)!!.first.first)
        assertNotNull(ReachBack.related("במשחק הצבעים בחרו חום, אבל חיפשנו ירוק.", "colors", history, now))
        // The butterflies, not the apples of a minute ago (that's "just now")
        assertEquals("ספרו 5 פרפרים!", ReachBack.related("ספרו 3 דגים!", "counting", history, now)!!.second.he)
        assertNull(ReachBack.related("במשחק הצללים מצאו של מי הצל: ברווז!", "peekaboo", history, now))
    }

    @Test
    fun theLineMustNameTheEarlierThing() {
        val input = "במשחק הצבעים בחרו חום, אבל חיפשנו ירוק."
        val earlier = "אתמול: במשחק הצבעים בחרו שחור, אבל חיפשנו חום."
        assert(ReachBack.namesEarlier("כמו אתמול עם שחור!", input, earlier))
        assert(!ReachBack.namesEarlier("יש, ירוק!", input, earlier))
    }
}
