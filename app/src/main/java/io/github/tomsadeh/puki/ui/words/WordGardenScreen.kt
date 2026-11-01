package io.github.tomsadeh.puki.ui.words

import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberRoundFlow
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import io.github.tomsadeh.puki.ui.components.popAway
import io.github.tomsadeh.puki.ui.myday.PukiCorner
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
import io.github.tomsadeh.puki.data.content.letterLookalikes
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
import io.github.tomsadeh.puki.audio.reactOr
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.pickChoices
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

/** Level 1 stays with the first letters of the alphabet. */
private const val FAMILIAR_LETTERS = 6

class WordGardenViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(WordState())
    val state: StateFlow<WordState> = _state.asStateFlow()
    private var letters = emptyList<LetterDef>()  // the letters this session draws on
    private var rounds = emptyList<LetterDef>()   // this session's letters
    private var choices = 2  // blocks to pick from: 2, then 3, then 4
    private var lookalikes = false  // the top level puts letters that look alike side by side

    fun initialize(language: Language) {
        val alphabet = when (language) {
            Language.HEBREW -> hebrewLetters
            Language.ENGLISH -> englishLetters
        }
        viewModelScope.launch {
            val level = session.level()
            choices = level + 1
            lookalikes = level >= 3
            letters = if (level == 1) alphabet.take(FAMILIAR_LETTERS) else alphabet
            // A short session of letters from anywhere in the alphabet, not always from א
            rounds = letters.shuffled().take(4)
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
        _state.value = WordState(
            targetLetter = target,
            letterChoices = lookalike(target).let { twin ->
                if (twin == null) pickChoices(target, letters, choices) { it.id }
                else (pickChoices(target, letters - twin, choices - 1) { it.id } + twin).shuffled()
            },
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned
        )
    }

    private fun lookalike(target: LetterDef): LetterDef? =
        if (lookalikes) letters.firstOrNull { it.id == letterLookalikes[target.id] } else null

    /** True if right, false if not, null while the round is already won (a toddler's second tap). */
    fun onLetterTapped(letterId: String): Boolean? {
        if (_state.value.showSuccess || _state.value.sessionComplete) return null
        val isCorrect = letterId == _state.value.targetLetter.id
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
    val round = rememberRoundFlow()
    val pictureReaction = rememberReactionState(state.roundIndex)

    LaunchedEffect(language) {
        viewModel.initialize(language)
    }

    // Stuck? Say the letter again after a quiet moment; after misses, the right block wiggles
    val help = rememberHelp(state.roundIndex, active = !round.won && state.letterChoices.isNotEmpty(), onAssisted = viewModel.session::slip) {
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
            scene = Scene.GARDEN
    ) {
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                onBack = onBack,
                roundIndex = state.roundIndex,
                totalRounds = state.totalRounds,
                starsEarned = state.totalStarsEarned
            )

            // The picture on its sign, and the blocks beside it: side by side, the blocks always
            // have room (above each other, a phone's short screen left them none at all)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val pictureSize = minOf(maxHeight * 0.62f, 170.dp)
            val blockSize = minOf(maxHeight * 0.62f, 160.dp)
            Row(
                modifier = Modifier.fillMaxSize().padding(start = PukiCorner),
                verticalAlignment = Alignment.CenterVertically
            ) {
            if (state.letterChoices.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.reaction(pictureReaction, jumpHeight = 20.dp)) {
                    WordSign(size = pictureSize, modifier = Modifier.popIn(key = state.roundIndex)) {
                    Image(
                        painter = painterResource(state.targetLetter.exampleIcon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(pictureSize * 0.82f)
                            .clickable {
                                val letter = state.targetLetter
                                val fallback = { voicePlayer.play("voice_letter_${letter.id}", language) }
                                if (companion != null) {
                                    companion.react("במשחק האותיות לחצו על התמונה של ${letter.exampleWord.he}, שמתחילה באות ${letter.hebrewLetter}.", "In the letter game the child tapped the picture of ${letter.exampleWord.en.lowercase()}, which starts with ${letter.englishLetter}.", fallback)
                                } else fallback()
                            }
                    )
                    }
                    Text(
                        state.targetLetter.exampleWord.resolve(language),
                        style = MaterialTheme.typography.titleLarge,
                        color = LetterInk
                    )
                    Spacer(Modifier.height(8.dp))
                    PromptBubble(
                        BilingualText("תמצא את: ${state.targetLetter.hebrewLetter}", "Find: ${state.targetLetter.englishLetter}", heGirl = "תמצאי את: ${state.targetLetter.hebrewLetter}").localized()
                    )
                }
            }

            // Letter choices
            Scatter(seed = sessionSeed + state.roundIndex, modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                            .popAway(round.won && letter.id != state.targetLetter.id, index)
                            .idleBob(index)
                            .reaction(letterReaction)
                            .hintGlow(help.showHint && letter.id == state.targetLetter.id)
                            .size(blockSize)
                            .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = scatterTilt(sessionSeed + state.roundIndex, index) }
                            .drawBehind { drawWoodBlock(block) }
                            .clickable(interactionSource = interactionSource, indication = null) {
                                val correct = viewModel.onLetterTapped(letter.id) ?: return@clickable
                                if (correct) {
                                    sfxPlayer.play(Sfx.SUCCESS)
                                    companion.reactOr("מצאו את האות ${letter.hebrewLetter}, כמו ${state.targetLetter.exampleWord.he}!", "The child found the letter ${letter.englishLetter}, like ${state.targetLetter.exampleWord.en.lowercase()}!") { voicePlayer.playPraise(language) }
                                    scope.launch { letterReaction.celebrate() }
                                    scope.launch { pictureReaction.celebrate() }
                                    round.win(2000, 3400) {
                                        viewModel.nextRound()
                                    }
                                } else {
                                    sfxPlayer.play(Sfx.WRONG_GENTLE)
                                    companion.reactOr("בחרו את האות ${letter.hebrewLetter}, אבל חיפשנו את ${state.targetLetter.hebrewLetter}.", "The child picked the letter ${letter.englishLetter}, but we were looking for ${state.targetLetter.englishLetter}.") { voicePlayer.playSequence(listOf("voice_letter_${letter.id}", "voice_prompt_try_again"), language) }
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
                        Text(
                            displayLetter,
                            fontSize = with(LocalDensity.current) { (blockSize * 0.52f).toSp() },
                            color = LetterInk,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = blockSize * 0.06f)
                        )
                    }
                }
            }
            }
            }
        }

        ConfettiOverlay(show = round.won)

        if (state.sessionComplete) {
            RoundCompleteSheet(starsEarned = state.totalStarsEarned, onContinue = onPlayAgain, onHome = onBack)
        }
    }
}

private const val ISLAND = "words"
