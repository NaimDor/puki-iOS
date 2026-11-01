package io.github.tomsadeh.puki.ui.peekaboo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.data.content.AnimalDef
import io.github.tomsadeh.puki.data.content.allAnimals
import io.github.tomsadeh.puki.ui.common.GameSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where an animal can hide on the farm. */
enum class HidingSpot { BARN, BUSH, HAY, STUMP }

data class PeekabooState(
    val animal: AnimalDef,
    val spot: HidingSpot,
    val found: Boolean = false,  // the animal is out
    val roundIndex: Int = 0,
    val totalRounds: Int = PEEKABOO_ROUNDS,
    val stars: Int = 0,
    val sessionComplete: Boolean = false
)

/**
 * Peekaboo barn: each round an animal hides behind one of the spots, and the child finds it.
 * Nothing to get wrong: a tap on another spot only sends out a butterfly and a clue.
 */
class PeekabooViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)
    private val animals = allAnimals.shuffled().take(PEEKABOO_ROUNDS)
    private var lastSpot: HidingSpot? = null

    private val _state = MutableStateFlow(round(0, stars = 0))
    val state: StateFlow<PeekabooState> = _state.asStateFlow()

    private fun round(index: Int, stars: Int): PeekabooState {
        // Never the same spot twice in a row, so it's a real search
        val spot = HidingSpot.entries.filter { it != lastSpot }.random().also { lastSpot = it }
        return PeekabooState(animal = animals[index], spot = spot, roundIndex = index, stars = stars)
    }

    /** True if the animal was there, false if not, null once it's already out (a second tap). */
    fun onSpotTapped(spot: HidingSpot): Boolean? {
        val s = _state.value
        if (s.found || s.sessionComplete) return null
        if (spot != s.spot) return false
        _state.value = s.copy(found = true, stars = s.stars + 1)
        session.star()
        return true
    }

    fun nextRound() {
        val s = _state.value
        val next = s.roundIndex + 1
        if (next >= PEEKABOO_ROUNDS) {
            _state.value = s.copy(sessionComplete = true)
            session.finish(PEEKABOO_ROUNDS)
        } else {
            _state.value = round(next, s.stars)
        }
    }
}

internal const val PEEKABOO_ROUNDS = 8
private const val ISLAND = "peekaboo"
