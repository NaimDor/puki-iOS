package io.github.tomsadeh.puki.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The content files fit together: a typo here is a game that quietly can't be won. */
class ContentIntegrityTest {
    private val ALL_FOODS = (1..3).flatMap { foodsForLevel(it) }.distinctBy { it.id }

    @Test
    fun everyAnimalHasItsFoodOnTheList() {
        val foods = ALL_FOODS.map { it.id }.toSet()
        for (a in allAnimals) assertTrue("${a.id} eats ${a.foodId}", a.foodId in foods)
    }

    @Test
    fun idsAreUnique() {
        assertEquals(allAnimals.size, allAnimals.map { it.id }.toSet().size)
        assertEquals(ALL_FOODS.size, ALL_FOODS.map { it.id }.toSet().size)
    }

    @Test
    fun aFeminineAnimalIsARealAnimal() {
        val ids = allAnimals.map { it.id }.toSet()
        for (id in FEMININE_ANIMALS) assertTrue(id, id in ids)
    }
}
