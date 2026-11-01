package io.github.tomsadeh.puki.ui.counting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.data.content.CountingRound
import io.github.tomsadeh.puki.data.content.countingRoundsForLevel
import io.github.tomsadeh.puki.data.content.level1CountingRounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Counting each thing, then (from level 2) answering "how many?", then the round is done. */
enum class CountingPhase { COUNT, HOW_MANY, DONE }

data class CountingState(
    val currentRound: CountingRound = level1CountingRounds[0],
    /** For each thing in the garden, the number it got when tapped (0: not counted yet). */
    val numbers: List<Int> = List(currentRound.targetNumber) { 0 },
    val phase: CountingPhase = CountingPhase.COUNT,
    /** The numbers to choose from for "how many?", smallest first. */
    val choices: List<Int> = emptyList(),
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val isLoading: Boolean = false
) {
    val currentCount: Int get() = numbers.count { it > 0 }
    val roundComplete: Boolean get() = phase == CountingPhase.DONE
}

class CountingGardenViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)
    private var rounds = emptyList<CountingRound>()
    private var level = 1

    private val _state = MutableStateFlow(CountingState(isLoading = true))
    val state: StateFlow<CountingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            level = session.level()
            // Bigger numbers at higher levels, with one easier round from the level below
            rounds = if (level == 1) level1CountingRounds.shuffled().take(4)
                else (countingRoundsForLevel(level).shuffled().take(3) + countingRoundsForLevel(level - 1).shuffled().take(1)).shuffled()
            startRound(0)
        }
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            session.finish(rounds.size)
            return
        }
        val round = rounds[index]
        _state.value = CountingState(
            currentRound = round,
            numbers = List(round.targetNumber) { 0 },
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    /**
     * The child touched thing [index]: its number (1-based), or 0 if it was already counted or
     * counting is over. Counting the last one finishes the round, or asks "how many?" from level 2.
     */
    fun onItemTapped(index: Int): Int {
        val current = _state.value
        if (current.phase != CountingPhase.COUNT || current.numbers.getOrNull(index) != 0) return 0
        val number = current.currentCount + 1
        val numbers = current.numbers.toMutableList().also { it[index] = number }
        val target = current.currentRound.targetNumber
        _state.value = when {
            number < target -> current.copy(numbers = numbers)
            level >= 2 -> current.copy(numbers = numbers, phase = CountingPhase.HOW_MANY, choices = choicesFor(target))
            else -> complete(current.copy(numbers = numbers))
        }
        return number
    }

    /** "How many?" answered with [number]: true if right (the round is done). */
    fun onAnswer(number: Int): Boolean {
        val current = _state.value
        if (current.phase != CountingPhase.HOW_MANY) return false
        if (number != current.currentRound.targetNumber) {
            session.slip()
            return false
        }
        _state.value = complete(current)
        return true
    }

    private fun complete(state: CountingState): CountingState {
        session.star()
        return state.copy(phase = CountingPhase.DONE, totalStarsEarned = state.totalStarsEarned + 1)
    }

    /** The answer and two other numbers close to it, smallest first. */
    private fun choicesFor(target: Int): List<Int> {
        val near = ((target - 2)..(target + 2)).filter { it in 1..10 && it != target }.shuffled().take(2)
        return (near + target).sorted()
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}

private const val ISLAND = "counting"
