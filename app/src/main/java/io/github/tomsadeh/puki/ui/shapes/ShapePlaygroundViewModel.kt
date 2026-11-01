package io.github.tomsadeh.puki.ui.shapes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.data.content.ShapeDef
import io.github.tomsadeh.puki.data.content.level1Shapes
import io.github.tomsadeh.puki.data.content.shapesForLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShapeRoundState(
    val targetShape: ShapeDef = level1Shapes[0],
    val shapeChoices: List<ShapeDef> = level1Shapes,
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val roundComplete: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val isLoading: Boolean = false
)

class ShapePlaygroundViewModel(application: Application) : AndroidViewModel(application) {

    private val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)
    private var allShapes = level1Shapes
    private var rounds = emptyList<ShapeDef>()
    private var level = 1

    private val _state = MutableStateFlow(ShapeRoundState(isLoading = true))
    val state: StateFlow<ShapeRoundState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            level = session.level()
            allShapes = shapesForLevel(level)  // later levels add shapes to the first four
            rounds = allShapes.shuffled().take(4)
            startRound(0)
        }
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            session.finish(rounds.size)
            return
        }
        val target = rounds[index]
        val choiceCount = if (level == 1) 3 else 4
        _state.value = ShapeRoundState(
            targetShape = target,
            shapeChoices = (listOf(target) + allShapes.filter { it.id != target.id }.shuffled().take(choiceCount - 1)).shuffled(),
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    /** True on the right shape, false on a wrong one, null once the round is already won. */
    fun onShapeDropped(shapeId: String): Boolean? {
        if (_state.value.roundComplete) return null
        val isCorrect = shapeId == _state.value.targetShape.id
        if (isCorrect) {
            val newStars = _state.value.totalStarsEarned + 1
            _state.value = _state.value.copy(roundComplete = true, totalStarsEarned = newStars)
            session.star()
        } else {
            session.slip()
        }
        return isCorrect
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}

private const val ISLAND = "shapes"
