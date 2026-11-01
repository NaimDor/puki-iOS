package io.github.tomsadeh.puki.ui.animals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.data.content.AnimalDef
import io.github.tomsadeh.puki.data.content.FoodDef
import io.github.tomsadeh.puki.data.content.level1Animals
import io.github.tomsadeh.puki.data.content.level1Foods
import io.github.tomsadeh.puki.data.content.level2Animals
import io.github.tomsadeh.puki.data.content.level3Animals
import io.github.tomsadeh.puki.data.content.foodsForLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Feed the animal, or find whose shadow it is (every other round). */
enum class AnimalRoundKind { FEED, SHADOW }

data class AnimalRoundState(
    val currentAnimal: AnimalDef = level1Animals[0],
    val kind: AnimalRoundKind = AnimalRoundKind.FEED,
    val foodChoices: List<FoodDef> = emptyList(),
    /** For a shadow round: the animals to choose from, the right one among them. */
    val animalChoices: List<AnimalDef> = emptyList(),
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val roundComplete: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val showSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class AnimalGardenViewModel(application: Application) : AndroidViewModel(application) {

    private val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)

    private val _state = MutableStateFlow(AnimalRoundState(isLoading = true))
    val state: StateFlow<AnimalRoundState> = _state.asStateFlow()

    private var animals = emptyList<AnimalDef>()
    private var pool = emptyList<AnimalDef>()
    private var allFoods = level1Foods
    private var level = 1

    init {
        viewModelScope.launch {
            level = session.level()
            // New animals join the farm ones, so old friends keep coming back
            pool = when (level) {
                1 -> level1Animals
                2 -> level1Animals + level2Animals
                else -> level1Animals + level2Animals + level3Animals
            }
            animals = pool.shuffled().take(4)
            allFoods = foodsForLevel(level)
            startRound(0)
        }
    }

    private fun startRound(index: Int) {
        if (index >= animals.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            session.finish(animals.size)
            return
        }
        val animal = animals[index]
        val correctFood = allFoods.first { it.id == animal.foodId }
        val distractors = allFoods.filter { it.id != animal.foodId }.shuffled().take(if (level == 1) 1 else 2)  // two foods at first
        val foodChoices = (listOf(correctFood) + distractors).shuffled()
        // The second and fourth rounds: whose shadow is this? (two animals at first, then three)
        val kind = if (index % 2 == 1) AnimalRoundKind.SHADOW else AnimalRoundKind.FEED
        val animalChoices = if (kind == AnimalRoundKind.SHADOW)
            (listOf(animal) + pool.filter { it.id != animal.id }.shuffled().take(if (level == 1) 1 else 2)).shuffled()
            else emptyList()

        _state.value = AnimalRoundState(
            currentAnimal = animal,
            kind = kind,
            foodChoices = foodChoices,
            animalChoices = animalChoices,
            roundIndex = index,
            totalRounds = animals.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    /** True on the right food, false on a wrong one, null once the round is already won. */
    fun onFoodDropped(foodId: String): Boolean? {
        val currentState = _state.value
        if (currentState.roundComplete) return null
        val isCorrect = foodId == currentState.currentAnimal.foodId
        if (isCorrect) {
            val newStars = currentState.totalStarsEarned + 1
            _state.value = currentState.copy(
                showSuccess = true,
                roundComplete = true,
                totalStarsEarned = newStars
            )
            session.star()
        } else {
            session.slip()
        }
        return isCorrect
    }

    /** A shadow round: the child put [animalId] on the shadow. True if it's the one. */
    fun onAnimalDropped(animalId: String): Boolean? {
        val currentState = _state.value
        if (currentState.roundComplete) return null
        val isCorrect = animalId == currentState.currentAnimal.id
        if (isCorrect) {
            _state.value = currentState.copy(showSuccess = true, roundComplete = true, totalStarsEarned = currentState.totalStarsEarned + 1)
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

private const val ISLAND = "animals"
