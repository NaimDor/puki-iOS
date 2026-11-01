package io.github.tomsadeh.puki.ui.animals

import io.github.tomsadeh.puki.data.content.FEMININE_ANIMALS
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberRoundFlow
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import io.github.tomsadeh.puki.ui.components.AnswerOutLoud
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.theme.GrassGreen
import io.github.tomsadeh.puki.ui.animals.components.AnimalChoice
import io.github.tomsadeh.puki.ui.animals.components.AnimalDisplay
import io.github.tomsadeh.puki.ui.animals.components.ShadowStage
import io.github.tomsadeh.puki.data.content.AnimalDef
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.animals.components.FoodItem
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.DragDropState
import io.github.tomsadeh.puki.ui.components.DropZone
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.LocalDragDropState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import io.github.tomsadeh.puki.audio.reactOr
import kotlinx.coroutines.launch

/** Animals whose Hebrew name is feminine ("מה הפרה אומרת?"). */
@Composable
fun AnimalGardenScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    animalViewModel: AnimalGardenViewModel = viewModel()
) {
    val state by animalViewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val dragDropState = remember { DragDropState() }

    val round = rememberRoundFlow()
    val animalReaction = rememberReactionState(state.roundIndex)

    if (state.isLoading) return

    val shadowRound = state.kind == AnimalRoundKind.SHADOW
    val choiceReactions = remember(state.roundIndex) { state.animalChoices.associate { it.id to ReactionState() } }

    // Stuck? Ask again after a quiet moment; after misses, the right food (or animal) wiggles
    val help = rememberHelp(state.roundIndex, active = !state.roundComplete) {
        if (shadowRound) voicePlayer.play("voice_prompt_whose_shadow", language)
        else voicePlayer.playSequence(listOf("voice_prompt_whos_hungry", "voice_animal_${state.currentAnimal.id}"), language)
    }

    if (shadowRound) RoundPrompt(
        state.roundIndex,
        scene = "במשחק החיות: מנחשים של מי הצל.",
        sceneEn = "In the animal game: guessing whose shadow this is.",
        "voice_prompt_whose_shadow"
    ) else RoundPrompt(
        state.roundIndex,
        scene = "במשחק החיות: ${state.currentAnimal.name.he} מחכה לאוכל.",
        sceneEn = "In the animal game: the ${state.currentAnimal.name.en.lowercase()} is waiting for food.",
        "voice_prompt_whos_hungry", "voice_animal_${state.currentAnimal.id}"
    )

    // A shadow round: the child put [animal] on the shadow
    fun onAnimalDropped(animal: AnimalDef, dropTargetId: String?) {
        if (dropTargetId != SHADOW_ZONE) {
            sfxPlayer.play(Sfx.WRONG_GENTLE)
            return
        }
        val target = state.currentAnimal
        val correct = animalViewModel.onAnimalDropped(animal.id) ?: return  // (the round's already won)
        if (correct) {
            sfxPlayer.play(Sfx.SUCCESS)
            scope.launch { animalReaction.celebrate() }
            // The shadow turns into the animal, which says its name and its sound
            voicePlayer.playSequence(listOf("voice_animal_${target.id}", "voice_sound_${target.id}"), language) {
                companion.reactOr("במשחק הצללים מצאו של מי הצל: ${target.name.he}!", "In the shadow game the child found whose shadow it was: the ${target.name.en.lowercase()}!") { voicePlayer.playPraise(language) }
            }
            round.win(3600, 4600) {
                animalViewModel.nextRound()
            }
        } else {
            sfxPlayer.play(Sfx.WRONG_GENTLE)
            help.missed()
            scope.launch { choiceReactions[animal.id]?.shake() }
            voicePlayer.playSequence(listOf("voice_animal_${animal.id}", "voice_prompt_try_again"), language)
        }
    }

    // "Whose shadow?" "פרה!": naming an animal puts it on the shadow
    if (shadowRound) AnswerOutLoud(state.roundIndex, state.animalChoices.map { listOf(it.name.resolve(language)) }, active = !state.roundComplete) { i ->
        state.animalChoices.getOrNull(i)?.let { onAnimalDropped(it, SHADOW_ZONE) }
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(
            scene = Scene.FARM
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .noticeTouches(help)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(
                    onBack = onBack,
                    roundIndex = state.roundIndex,
                    totalRounds = state.totalRounds,
                    starsEarned = state.totalStarsEarned
                )

                Spacer(modifier = Modifier.height(8.dp))

                // The animal with its drop zone, and the food tray: under it on a tablet; beside it
                // on a phone's short screen, where under it would leave the animal tiny
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val compact = maxHeight < 440.dp
                    val animal: @Composable (Modifier) -> Unit = { m ->
                        Box(modifier = m, contentAlignment = Alignment.Center) {
                    if (shadowRound) DropZone(zoneId = SHADOW_ZONE, highlightColor = GrassGreen) {
                        ShadowStage(animal = state.currentAnimal, revealed = state.roundComplete, reaction = animalReaction, size = if (compact) 200.dp else 280.dp)
                    } else DropZone(
                        zoneId = state.currentAnimal.id,
                        highlightColor = GrassGreen
                    ) {
                        AnimalDisplay(animal = state.currentAnimal, reaction = animalReaction)
                    }
                }
                    }
                    val tray: @Composable () -> Unit = {
                // (by its full name: the Column's own AnimatedVisibility can't be called from here)
                androidx.compose.animation.AnimatedVisibility(
                    visible = !state.roundComplete,
                    enter = fadeIn() + scaleIn()
                ) {
                    if (shadowRound) Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        state.animalChoices.forEachIndexed { i, animal ->
                            AnimalChoice(
                                animal = animal,
                                onDropped = { onAnimalDropped(animal, it) },
                                tapToZone = SHADOW_ZONE,
                                index = i,
                                reaction = choiceReactions.getValue(animal.id),
                                hint = help.showHint && animal.id == state.currentAnimal.id,
                                size = if (compact) 130.dp else 165.dp
                            )
                        }
                    } else Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        state.foodChoices.forEach { food ->
                            FoodItem(
                                food = food,
                                plateSize = if (compact) 130.dp else 165.dp,
                                hint = help.showHint && food.id == state.currentAnimal.foodId,
                                tapToZone = state.currentAnimal.id,
                                landed = { it == state.currentAnimal.id && food.id == state.currentAnimal.foodId },
                                onDropped = { dropTargetId ->
                                    if (dropTargetId == state.currentAnimal.id) {
                                        val correct = animalViewModel.onFoodDropped(food.id)
                                        if (correct == true) {
                                            sfxPlayer.play(Sfx.SUCCESS)
                                            companion.reactOr("נתנו ל${state.currentAnimal.name.he} ${food.name.he}. זה האוכל הנכון!", "The child fed the ${state.currentAnimal.name.en.lowercase()} ${food.name.en.lowercase()}. That's the right food!") { voicePlayer.playPraise(language) }
                                            scope.launch {
                                                // Swallowed first, then a munch and a happy hop
                                                delay(220)
                                                animalReaction.munch()
                                                animalReaction.celebrate()
                                            }
                                            round.win(2400, 3600) {
                                                // Every other round, Puki asks what the animal says and listens for an answer
                                                val animal = state.currentAnimal
                                                if (companion?.isReady == true && state.roundIndex % 2 == 0) {
                                                    val says = if (animal.id in FEMININE_ANIMALS) "אומרת" else "אומר"
                                                    val question = if (language == Language.ENGLISH) "What does the ${animal.name.en.lowercase()} say?"
                                                        else "מה ה${animal.name.he} $says?"
                                                    companion.askAndListen(question, if (language == Language.ENGLISH) "You asked what the ${animal.name.en.lowercase()} says, and this is the answer." else "שאלת מה ה${animal.name.he} $says, וזו התשובה.") {
                                                        voicePlayer.play("voice_sound_${animal.id}", language)
                                                        scope.launch { animalReaction.celebrate() }
                                                    }
                                                }
                                                animalViewModel.nextRound()
                                            }
                                        } else if (correct == false) {
                                            sfxPlayer.play(Sfx.WRONG_GENTLE)
                                            // The animal shakes its head, makes its sound and says what it eats: a little fact, not a "no"
                                            val animal = state.currentAnimal
                                            val teach = { voicePlayer.playSequence(listOf("voice_sound_${animal.id}", "voice_eats_${animal.id}"), language) }
                                            companion.reactOr("ניסו לתת ל${animal.name.he} ${food.name.he}, אבל זה לא האוכל שלו.", "The child tried to give the ${animal.name.en.lowercase()} ${food.name.en.lowercase()}, but that's not its food.", teach)
                                            help.missed()
                                            scope.launch { animalReaction.shake() }
                                        }
                                    } else {
                                        sfxPlayer.play(Sfx.WRONG_GENTLE)
                                    }
                                }
                            )
                        }
                    }
                }
                    }
                    if (compact) {
                        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            animal(Modifier.weight(1f).fillMaxHeight())
                            tray()
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            animal(Modifier.weight(1f))
                            tray()
                        }
                    }
                }
            }

            ConfettiOverlay(show = round.won)

            if (state.sessionComplete) {
                RoundCompleteSheet(
                    starsEarned = state.totalStarsEarned,
                    onContinue = onPlayAgain,
                    onHome = onBack
                )
            }
        }
    }
}

/** Where the animals go in a shadow round. */
private const val SHADOW_ZONE = "shadow"
