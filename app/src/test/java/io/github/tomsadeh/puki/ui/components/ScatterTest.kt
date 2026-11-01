package io.github.tomsadeh.puki.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScatterTest {
    @Test
    fun theFirstOneStaysOutOfPukisCorner() {
        for (seed in 0 until 200) assertTrue(scatterCenters(3, seed, aroundCenter = false, flat = false)[0].second < 0.5f)
    }

    @Test
    fun theyStayInOrderAcross() {
        for (seed in 0 until 200) {
            val xs = scatterCenters(4, seed, aroundCenter = false, flat = false).map { it.first }
            assertEquals(xs.sorted(), xs)
        }
    }

    @Test
    fun aShortAreaHasThemInALine() {
        assertTrue(scatterCenters(3, 1, aroundCenter = false, flat = true).all { it.second == 0.5f })
        val around = scatterCenters(4, 1, aroundCenter = true, flat = true)
        assertEquals(4, around.map { it.first }.toSet().size)  // none on top of another
        assertTrue(around.all { it.first < 0.3f || it.first > 0.7f })  // the target's place stays free
    }
}
