package io.github.tomsadeh.puki.data

import io.github.tomsadeh.puki.data.ProgressRepository.Companion.MAX_LEVEL
import io.github.tomsadeh.puki.data.ProgressRepository.Companion.nextLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** When a game gets harder or easier (5-round sessions, as the games play them). */
class LevelRuleTest {
    @Test
    fun twoEasySessionsMoveUp() {
        val (level, streak) = nextLevel(1, 0, rounds = 5, mistakes = 0)
        assertEquals(1 to 1, level to streak)
        assertEquals(2 to 0, nextLevel(level, streak, rounds = 5, mistakes = 2))  // 2 slips in 5 is still easy
    }

    @Test
    fun aMiddlingSessionResetsTheStreak() {
        assertEquals(2 to 0, nextLevel(2, 1, rounds = 5, mistakes = 4))
    }

    @Test
    fun aHardSessionMovesDown() {
        assertEquals(1 to 0, nextLevel(2, 1, rounds = 5, mistakes = 5))
    }

    @Test
    fun staysWithinTheLevels() {
        assertEquals(1 to 0, nextLevel(1, 0, rounds = 5, mistakes = 12))
        assertEquals(MAX_LEVEL, nextLevel(MAX_LEVEL, 1, rounds = 5, mistakes = 0).first)
    }

    @Test
    fun aPlayThroughGrowsAndSettles() {
        // Easy, easy (up to 2), hard (back to 1), easy, easy (2 again), easy, easy (3)
        var state = 1 to 0
        for (mistakes in listOf(0, 1, 11, 0, 0, 0, 0)) state = nextLevel(state.first, state.second, 5, mistakes)
        assertEquals(3 to 0, state)
    }
}
