package io.github.tomsadeh.puki.ui.common

import io.github.tomsadeh.puki.data.content.RecapContent
import io.github.tomsadeh.puki.data.DayLog
import io.github.tomsadeh.puki.data.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The bookkeeping every round-based game does: its level, the slips this session, stars saved
 * as they're earned, and the session recorded at the end (which may move the level, see
 * [ProgressRepository.recordSession]).
 */
class GameSession(
    private val island: String,
    private val progress: ProgressRepository,
    private val scope: CoroutineScope
) {
    private var mistakes = 0

    /** This game's level, 1 (easiest) to [ProgressRepository.MAX_LEVEL]. */
    suspend fun level(): Int = progress.getDifficulty(island)

    fun star() {
        scope.launch { progress.addStars(1) }
    }

    fun slip() {
        mistakes++
    }

    fun finish(rounds: Int) {
        scope.launch { progress.recordSession(island, rounds, mistakes) }
        DayLog.record(RecapContent.FIXED[island])  // (the games that are one thing all along: baked a cake, danced)
    }
}
