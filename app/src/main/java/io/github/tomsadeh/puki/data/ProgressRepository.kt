package io.github.tomsadeh.puki.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.tomsadeh.puki.data.models.GameProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

class ProgressRepository(private val context: Context) {

    companion object {
        const val MAX_LEVEL = 3

        /** The rule behind [recordSession]: the new (level, easy-session streak). */
        fun nextLevel(level: Int, streak: Int, rounds: Int, mistakes: Int): Pair<Int, Int> = when {
            mistakes <= rounds / 2 ->  // easy: at most one slip every other round
                if (streak + 1 >= 2 && level < MAX_LEVEL) (level + 1) to 0 else level to streak + 1
            mistakes >= rounds * 2 -> maxOf(1, level - 1) to 0  // two misses a round
            else -> level to 0
        }
    }

    private object Keys {
        val TOTAL_STARS = intPreferencesKey("total_stars")
    }

    private fun difficultyKey(island: String) = intPreferencesKey("difficulty_$island")
    private fun streakKey(island: String) = intPreferencesKey("clean_streak_$island")

    val progress: Flow<GameProgress> = context.progressDataStore.data.map { prefs ->
        GameProgress(
            totalStars = prefs[Keys.TOTAL_STARS] ?: 0
        )
    }

    suspend fun addStars(count: Int) {
        context.progressDataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_STARS] ?: 0
            prefs[Keys.TOTAL_STARS] = current + count
        }
    }


    /** A game's level, 1 (easiest) to [MAX_LEVEL]. */
    suspend fun getDifficulty(island: String): Int {
        val prefs = context.progressDataStore.data.first()
        return (prefs[difficultyKey(island)] ?: 1).coerceIn(1, MAX_LEVEL)
    }

    /**
     * After a session of [rounds] rounds with [mistakes] wrong answers: two easy sessions in a
     * row move the game up a level, and a hard one moves it back down, so it grows with the
     * child without getting stuck too hard. Returns the new level.
     */
    suspend fun recordSession(island: String, rounds: Int, mistakes: Int): Int {
        var level = 1
        context.progressDataStore.edit { prefs ->
            val (next, streak) = nextLevel(
                (prefs[difficultyKey(island)] ?: 1).coerceIn(1, MAX_LEVEL), prefs[streakKey(island)] ?: 0, rounds, mistakes
            )
            level = next
            prefs[difficultyKey(island)] = level
            prefs[streakKey(island)] = streak
        }
        return level
    }

}
