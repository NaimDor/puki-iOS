package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.content.RecapContent
import io.github.tomsadeh.puki.data.content.ReachBack
import io.github.tomsadeh.puki.data.content.StoryContent
import io.github.tomsadeh.puki.data.content.allAnimals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The checks the device puts on the brain's lines that the trainer didn't have: the story's picks, the recap's apart words. */
class DeviceChecksTest {
    private val animals = allAnimals.associateBy { it.id }
    private val plan = StoryContent.Plan(animals.getValue("cow"), "hill", animals.getValue("dog"), "banana")

    @Test
    fun aStorySentenceNamesWhatWasPicked() {
        assertEquals("הר", StoryContent.pickWord(plan, false, 1))
        assertEquals("hill", StoryContent.pickWord(plan, true, 1))
        assertEquals("dog", StoryContent.pickWord(plan, true, 2))
        assertEquals("banana", StoryContent.pickWord(plan, true, 3))
        assertNull(StoryContent.pickWord(plan, true, 0))
        assertTrue(ReachBack.says("הפרה הלכה להר הגבוה.", "הר"))
        assertTrue(ReachBack.says("The cow met two dogs.", "dog"))
        assertFalse(ReachBack.says("The cow went to the farm.", "hill"))
    }

    @Test
    fun andNothingThatWasNot() {
        val offered = mapOf(1 to listOf("hill", "garden"), 2 to listOf("dog", "cat"), 3 to listOf("banana", "cake"))
        assertEquals(listOf("גינה"), StoryContent.notPicked(plan, false, 1, offered))
        assertEquals(listOf("garden", "cat", "cake"), StoryContent.notPicked(plan, true, 3, offered))
        assertTrue(StoryContent.notPicked(plan, true, 0, offered).isEmpty())
    }

    @Test
    fun aRecapWordTwoEventsShareNamesNeither() {
        val input = "סיכום לפני השינה. היום: ספרת 3 דגים; ספרת 5 פרפרים."
        assertEquals(0, RecapContent.eventsNamedApart("ספרת יפה!", input))
        assertEquals(2, RecapContent.eventsNamedApart("ספרת דגים ופרפרים.", input))
        assertEquals(1, RecapContent.eventsNamedApart("דגים!", input))
    }
}
