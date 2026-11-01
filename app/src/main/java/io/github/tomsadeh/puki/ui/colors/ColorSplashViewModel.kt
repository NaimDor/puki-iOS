package io.github.tomsadeh.puki.ui.colors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.pickChoices
import io.github.tomsadeh.puki.data.content.ColorDef
import io.github.tomsadeh.puki.data.content.colorsForLevel
import io.github.tomsadeh.puki.data.content.level1Colors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ColorRoundState(
    val targetColor: ColorDef = level1Colors[0],
    val colorChoices: List<ColorDef> = level1Colors,
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val showSuccess: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val isLoading: Boolean = false
)

class ColorSplashViewModel(application: Application) : AndroidViewModel(application) {

    val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)
    private var allColors = level1Colors
    private var rounds = emptyList<ColorDef>()
    private var level = 1

    private val _state = MutableStateFlow(ColorRoundState(isLoading = true))
    val state: StateFlow<ColorRoundState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            level = session.level()
            allColors = colorsForLevel(level)  // later levels add colors to the first four
            rounds = allColors.shuffled().take(4)
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
        _state.value = ColorRoundState(
            targetColor = target,
            colorChoices = pickChoices(target, allColors, choiceCount) { it.id },
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    /** True if right, false if not, null while the round is already won (a toddler's second tap). */
    fun onColorTapped(colorId: String): Boolean? {
        if (_state.value.showSuccess || _state.value.sessionComplete) return null
        val isCorrect = colorId == _state.value.targetColor.id
        if (isCorrect) {
            val newStars = _state.value.totalStarsEarned + 1
            _state.value = _state.value.copy(showSuccess = true, totalStarsEarned = newStars)
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

private const val ISLAND = "colors"
