package io.github.tomsadeh.puki.ui.silly

import io.github.tomsadeh.puki.ui.components.TalkingParrotImage
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.rememberHelp
import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.audio.reactOr
import io.github.tomsadeh.puki.data.content.CareContent
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.common.awaitUnpaused
import io.github.tomsadeh.puki.ui.common.rememberGamePaused
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The patients and gifts the app has pictures for (the doll, and blankets, bandages and soup, wait for art). */
private val PATIENT_ART = mapOf("puki" to R.drawable.ic_companion_parrot, "teddy" to R.drawable.ic_tidy_teddy, "bunny" to R.drawable.ic_animal_rabbit)  // (the toy bunny's paws read as two tails this big)
private val GIFT_ART = mapOf("hat" to R.drawable.ic_clothing_hat, "teddy" to R.drawable.ic_tidy_teddy, "water" to R.drawable.ic_table_cup,
    "apple" to R.drawable.ic_count_apple, "ball" to R.drawable.ic_tidy_ball, "book" to R.drawable.ic_tidy_book,
    "hug" to R.drawable.ic_sticker_heart, "song" to R.drawable.ic_ui_music_note)

/** One round: who needs help, with what, and three gifts to choose from (at least one helps). */
data class CareRound(val patient: CareContent.Patient, val problem: CareContent.Problem, val gifts: List<String>)

data class CareState(val rounds: List<CareRound> = emptyList(), val roundIndex: Int = 0, val starsEarned: Int = 0, val sessionComplete: Boolean = false) {
    val round get() = rounds.getOrNull(roundIndex)
}

class CareGameViewModel(application: Application) : AndroidViewModel(application) {
    private val session = GameSession("care", (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(CareState())
    val state: StateFlow<CareState> = _state.asStateFlow()

    init {
        val patients = CareContent.PATIENTS.filter { it.id in PATIENT_ART }
        val rounds = mutableListOf<CareRound>()
        for (problem in CareContent.PROBLEMS.shuffled()) {
            if (rounds.size >= ROUNDS) break
            val patient = patients.filter { it != rounds.lastOrNull()?.patient }.random()  // (a different friend each round)
            val helpful = GIFT_ART.keys.filter { CareContent.helps(patient, problem, it) }
            val other = GIFT_ART.keys.filter { !CareContent.helps(patient, problem, it) && !(patient.id == "teddy" && it == "teddy") }
            if (helpful.isEmpty() || other.size < 2) continue
            rounds += CareRound(patient, problem, (listOf(helpful.random()) + other.shuffled().take(2)).shuffled())
        }
        _state.value = CareState(rounds = rounds)
    }

    fun award() {
        _state.value = _state.value.copy(starsEarned = _state.value.starsEarned + 1)
        session.star()
    }

    fun next() {
        val s = _state.value
        if (s.roundIndex + 1 >= s.rounds.size) session.finish(s.rounds.size)
        _state.value = if (s.roundIndex + 1 >= s.rounds.size) s.copy(sessionComplete = true) else s.copy(roundIndex = s.roundIndex + 1)
    }

    companion object {
        const val ROUNDS = 4
    }
}

/**
 * The caring game: Puki, the teddy or the bunny needs a little help (cold, thirsty, scared of the thunder), Puki's
 * brain says what's wrong, and the child gives them something. It helps, and they feel better; or it's nice but
 * doesn't help yet, and she tries another (never a "no"). Every line is the brain's, from the trainer's sentences
 * (CareContent). Only with the brain: it's a door in Puki's corner.
 */
@Composable
fun CareGameScreen(onBack: () -> Unit, onPlayAgain: () -> Unit = onBack, viewModel: CareGameViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val english = language == Language.ENGLISH
    val companion = LocalCompanion.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val paused = rememberGamePaused()
    val patientReaction = rememberReactionState(state.roundIndex)
    var given by remember { mutableStateOf(setOf<String>()) }
    var choosing by remember { mutableStateOf(false) }
    var tap by remember { mutableStateOf<CompletableDeferred<String>?>(null) }
    var confetti by remember { mutableStateOf(false) }
    var problemLine by remember { mutableStateOf("") }  // (what's wrong, said again when she's quiet)
    val speaking by (companion?.speaking ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()

    var ready by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        ready = companion?.awaitReady() == true
        if (ready != true) onBack()
    }

    val round = state.round
    // Quiet for a while: Puki says what's wrong again; two gifts that don't help, and one that does glows
    val help = rememberHelp(state.roundIndex, active = choosing, missesCounted = false) {
        if (problemLine.isNotEmpty()) scope.launch { companion?.speakAndWait(problemLine) }
    }
    LaunchedEffect(state.roundIndex, ready) {
        if (round == null || ready != true) return@LaunchedEffect
        given = emptySet()
        problemLine = ""
        val who = round.patient.he ?: "פוקי"
        companion?.setScene("במשחק הטיפול: ${CareContent.le(who)} צריך עזרה.", "In the caring game: ${round.patient.en ?: "Puki"} needs help.")
        delay(700)
        awaitUnpaused(paused)
        problemLine = companion?.line("care", CareContent.input(english, round.patient, round.problem, null)) ?: ""
        while (true) {
            awaitUnpaused(paused)
            val pick = CompletableDeferred<String>().also { tap = it }
            choosing = true
            val gift = pick.await()
            choosing = false
            tap = null
            given = given + gift
            sfxPlayer.play(Sfx.POP)
            scope.launch { patientReaction.hop() }
            val helped = CareContent.helps(round.patient, round.problem, gift)
            if (!helped) help.missed()
            if (helped) {
                sfxPlayer.play(Sfx.SUCCESS)
                confetti = true
                viewModel.award()
                scope.launch { patientReaction.celebrate() }
            }
            companion?.line("care", CareContent.input(english, round.patient, round.problem, gift))
            if (helped || given.size >= round.gifts.size) break
        }
        delay(900)
        confetti = false
        viewModel.next()
    }

    LaunchedEffect(state.sessionComplete) {
        if (state.sessionComplete) companion.reactOr("עזרו לכולם במשחק הטיפול!", "The child helped everyone in the caring game!") { voicePlayer.playPraise(language) }
    }

    GameBackground(scene = Scene.HOME, withCompanion = round?.patient?.id != "puki") {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(onBack = onBack, roundIndex = state.roundIndex, totalRounds = state.rounds.size, starsEarned = state.starsEarned)
            if (round != null) BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val big = minOf(300.dp, maxHeight - 150.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val patientModifier = Modifier.popIn(key = state.roundIndex).idleBob(amount = 0.5f).reaction(patientReaction).size(big)
                    if (round.patient.id == "puki") TalkingParrotImage(talking = speaking, modifier = patientModifier)
                    else Image(painterResource(PATIENT_ART.getValue(round.patient.id)), contentDescription = round.patient.en, modifier = patientModifier)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        for (gift in round.gifts) {
                            val gone = gift in given
                            val helps = CareContent.helps(round.patient, round.problem, gift)
                            Box(
                                modifier = Modifier
                                    .hintGlow(help.showHint && helps && !gone)
                                    .noticeTouches(help)
                                    .size(110.dp)
                                    .alpha(if (gone) 0.25f else 1f)
                                    .shadow(6.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = choosing && !gone) { tap?.complete(gift) },
                                contentAlignment = Alignment.Center
                            ) { Image(painterResource(GIFT_ART.getValue(gift)), contentDescription = CareContent.ITEMS[gift]?.second, modifier = Modifier.size(78.dp)) }
                        }
                    }
                }
            }
        }
        ConfettiOverlay(show = confetti)
        if (state.sessionComplete) RoundCompleteSheet(starsEarned = state.starsEarned, onContinue = onPlayAgain, onHome = onBack)
    }
}
