package io.github.tomsadeh.puki.ui.words

import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberSessionSeed
import io.github.tomsadeh.puki.ui.components.scatterTilt
import io.github.tomsadeh.puki.ui.components.Scatter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.PromptBubble
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.LetterDef
import io.github.tomsadeh.puki.data.content.englishLetters
import io.github.tomsadeh.puki.data.content.hebrewLetters
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.localized
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Toy-block colors for the letters. */
private val LETTER_BLOCKS = listOf(Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFA726), Color(0xFFAB47BC))

data class WordState(
    val targetLetter: LetterDef = hebrewLetters[0],
    val letterChoices: List<LetterDef> = emptyList(),
    val roundIndex: Int = 0,
    val totalRounds: Int = 6,
    val showSuccess: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0
)

class WordGardenViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as PukiApplication).progressRepository
    private val _state = MutableStateFlow(WordState())
    val state: StateFlow<WordState> = _state.asStateFlow()
    private var letters = emptyList<LetterDef>()  // the whole alphabet, for the other blocks
    private var rounds = emptyList<LetterDef>()   // this session's letters
    private var choices = 2  // blocks to pick from: 2 at first, 3 from level 2
    private var mistakes = 0

    fun initialize(language: Language) {
        letters = when (language) {
            Language.HEBREW -> hebrewLetters
            Language.ENGLISH -> englishLetters
        }
        viewModelScope.launch {
            choices = if (progressRepository.getDifficulty(ISLAND) == 1) 2 else 3
            // A short session of letters from anywhere in the alphabet, not always from א
            rounds = letters.shuffled().take(4)
            startRound(0)
        }
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            viewModelScope.launch { progressRepository.recordSession(ISLAND, rounds.size, mistakes) }
            return
        }
        val target = rounds[index]
        val distractors = letters.filter { it.id != target.id }.shuffled().take(choices - 1)
        _state.value = WordState(
            targetLetter = target,
            letterChoices = (listOf(target) + distractors).shuffled(),
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned
        )
    }

    /** True if right, false if not, null while the round is already won (a toddler's second tap). */
    fun onLetterTapped(letterId: String): Boolean? {
        if (_state.value.showSuccess || _state.value.sessionComplete) return null
        val isCorrect = letterId == _state.value.targetLetter.id
        if (isCorrect) {
            val newStars = _state.value.totalStarsEarned + 1
            _state.value = _state.value.copy(showSuccess = true, totalStarsEarned = newStars)
            viewModelScope.launch { progressRepository.addStars(1) }
        } else {
            mistakes++
        }
        return isCorrect
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}

@Composable
fun WordGardenScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: WordGardenViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionSeed = rememberSessionSeed()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    var showRoundSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(language) {
        viewModel.initialize(language)
    }

    // Stuck? Say the letter again after a quiet moment; after misses, the right block wiggles
    val help = rememberHelp(state.roundIndex, active = !showRoundSuccess && state.letterChoices.isNotEmpty()) {
        voicePlayer.play("voice_letter_${state.targetLetter.id}", language)
    }

    LaunchedEffect(state.roundIndex, state.letterChoices.isNotEmpty()) {
        companion?.setScene("במשחק האותיות: מחפשים את האות ${state.targetLetter.hebrewLetter}, כמו ${state.targetLetter.exampleWord.he}.", "In the letter game: we're looking for the letter ${state.targetLetter.englishLetter}, like ${state.targetLetter.exampleWord.en.lowercase()}.")
        if (state.letterChoices.isNotEmpty()) {
            delay(500)
            voicePlayer.play("voice_letter_${state.targetLetter.id}", language)
        }
    }

    GameBackground(
            scene = Scene.GARDEN,
        colors = listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9), Color(0xFFA5D6A7))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                onBack = onBack,
                roundIndex = state.roundIndex,
                totalRounds = state.totalRounds,
                starsEarned = state.totalStarsEarned
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Example picture
            if (state.letterChoices.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(state.targetLetter.exampleIcon),
                        contentDescription = null,
                        modifier = Modifier
                            .popIn(key = state.roundIndex)
                            .size(170.dp)
                            .shadow(8.dp, RoundedCornerShape(32.dp))
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White)
                            .padding(20.dp)
                            .clickable {
                                val letter = state.targetLetter
                                val fallback = { voicePlayer.play("voice_letter_${letter.id}", language) }
                                if (companion != null) {
                                    companion.react("במשחק האותיות לחצו על התמונה של ${letter.exampleWord.he}, שמתחילה באות ${letter.hebrewLetter}.", "In the letter game the child tapped the picture of ${letter.exampleWord.en.lowercase()}, which starts with ${letter.englishLetter}.", fallback)
                                } else fallback()
                            }
                    )
                    Text(
                        state.targetLetter.exampleWord.resolve(language),
                        style = MaterialTheme.typography.titleLarge,
                        color = EarthBrown
                    )
                    Spacer(Modifier.height(8.dp))
                    PromptBubble(
                        BilingualText("תמצא את: ${state.targetLetter.hebrewLetter}", "Find: ${state.targetLetter.englishLetter}", heGirl = "תמצאי את: ${state.targetLetter.hebrewLetter}").localized()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Letter choices
            Scatter(seed = sessionSeed + state.roundIndex, modifier = Modifier.weight(1f).fillMaxWidth()) {
                state.letterChoices.forEachIndexed { index, letter ->
                    val block = LETTER_BLOCKS[index % LETTER_BLOCKS.size]
                    val letterReaction = rememberReactionState(state.roundIndex, letter.id)
                    val interactionSource = remember(letter.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                        label = "letter_scale"
                    )

                    Box(
                        modifier = Modifier
                            .popIn(index, state.roundIndex)
                            .idleBob(index)
                            .reaction(letterReaction)
                            .hintGlow(help.showHint && letter.id == state.targetLetter.id)
                            .size(160.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = scatterTilt(sessionSeed + state.roundIndex, index) }
                            .shadow(10.dp, RoundedCornerShape(32.dp))
                            .clip(RoundedCornerShape(32.dp))
                            .background(Brush.verticalGradient(listOf(lerp(block, Color.White, 0.25f), block, lerp(block, Color.Black, 0.2f))))
                            .border(5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(32.dp))
                            .clickable(interactionSource = interactionSource, indication = null) {
                                val correct = viewModel.onLetterTapped(letter.id) ?: return@clickable
                                if (correct) {
                                    sfxPlayer.play(Sfx.SUCCESS)
                                    if (companion != null) companion.react("מצאו את האות ${letter.hebrewLetter}, כמו ${state.targetLetter.exampleWord.he}!", "The child found the letter ${letter.englishLetter}, like ${state.targetLetter.exampleWord.en.lowercase()}!") { voicePlayer.playPraise(language) } else voicePlayer.playPraise(language)
                                    showRoundSuccess = true
                                    scope.launch { letterReaction.celebrate() }
                                    scope.launch {
                                        delay(if (companion?.isReady == true) 3400 else 2000)
                                        showRoundSuccess = false
                                        viewModel.nextRound()
                                    }
                                } else {
                                    sfxPlayer.play(Sfx.WRONG_GENTLE)
                                    if (companion != null) companion.react("בחרו את האות ${letter.hebrewLetter}, אבל חיפשנו את ${state.targetLetter.hebrewLetter}.", "The child picked the letter ${letter.englishLetter}, but we were looking for ${state.targetLetter.englishLetter}.") { voicePlayer.playSequence(listOf("voice_letter_${letter.id}", "voice_prompt_try_again"), language) } else voicePlayer.playSequence(listOf("voice_letter_${letter.id}", "voice_prompt_try_again"), language)
                                    help.missed()
                                    scope.launch { letterReaction.shake() }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val displayLetter = when (language) {
                            Language.HEBREW -> letter.hebrewLetter
                            Language.ENGLISH -> letter.englishLetter
                        }
                        Text(displayLetter, fontSize = 88.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        ConfettiOverlay(show = showRoundSuccess)

        if (state.sessionComplete) {
            RoundCompleteSheet(starsEarned = state.totalStarsEarned, onContinue = onPlayAgain, onHome = onBack)
        }
    }
}

private const val ISLAND = "words"
