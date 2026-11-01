package io.github.tomsadeh.puki.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoicesTest {
    private val pool = listOf("a", "b", "c", "d", "e")

    @Test
    fun theRightOneIsAlwaysThereAndNothingTwice() {
        repeat(200) {
            val picks = pickChoices("c", pool, 3) { it }
            assertEquals(3, picks.size)
            assertTrue("c" in picks)
            assertEquals(3, picks.toSet().size)
        }
    }

    @Test
    fun aSmallPoolGivesWhatItHas() {
        assertEquals(setOf("a", "b"), pickChoices("a", listOf("a", "b"), 4) { it }.toSet())
    }

    @Test
    fun theRightOneMovesAround() {
        val places = List(200) { pickChoices("c", pool, 3) { it }.indexOf("c") }.toSet()
        assertEquals(setOf(0, 1, 2), places)
    }
}
