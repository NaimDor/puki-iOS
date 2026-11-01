package io.github.tomsadeh.puki.ui.say

import io.github.tomsadeh.puki.ui.components.Scene
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.github.tomsadeh.puki.ui.components.TalkingParrotImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.audio.VoicePlayer
import io.github.tomsadeh.puki.audio.WordTry
import io.github.tomsadeh.puki.data.content.SayWord
import io.github.tomsadeh.puki.data.content.sayWords
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class SayState(
    val words: List<SayWord> = emptyList(),
    val roundIndex: Int = 0,
    val starsEarned: Int = 0,
    val sessionComplete: Boolean = false
) {
    val word get() = words.getOrNull(roundIndex)
}

class SayItViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as PukiApplication).progressRepository
    private val _state = MutableStateFlow(SayState())
    val state: StateFlow<SayState> = _state.asStateFlow()

    init {
        _state.value = SayState(words = sayWords.shuffled().take(ROUNDS))
    }

    fun award() {
        _state.value = _state.value.copy(starsEarned = _state.value.starsEarned + 1)
        viewModelScope.launch { progressRepository.addStars(1) }
    }

    fun next() {
        val s = _state.value
        _state.value = if (s.roundIndex + 1 >= s.words.size) s.copy(sessionComplete = true)
            else s.copy(roundIndex = s.roundIndex + 1)
    }

    companion object {
        const val ROUNDS = 5  // short: three tries a word adds up fast at two
        const val TRIES = 3
    }
}

/** Plays a voice line and returns when it's done. */
private suspend fun VoicePlayer.say(key: String, language: Language) =
    suspendCancellableCoroutine { cont ->
        play(key, language) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { stop() }
    }

private suspend fun VoicePlayer.praise(language: Language) =
    suspendCancellableCoroutine { cont ->
        playPraise(language) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { stop() }
    }

/**
 * Say it with Puki: a picture appears, Puki says the word, and the child says it back.
 * With the PC listening, the word itself moves the game on: another word or a near miss gets
 * the word again ("listen well" / "almost") and another go, up to three tries. A near miss
 * still earns the star in the end; Puki always closes by saying the word the right way (a
 * recast, as speech therapists do). Without the PC, the tablet can only hear that the child
 * spoke, so any try counts.
 */
@Composable
fun SayItScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: SayItViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var talking by remember { mutableStateOf(false) }
    var confetti by remember { mutableStateOf(false) }
    val listening by (companion?.listening ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val pictureReaction = rememberReactionState(state.roundIndex)

    // The microphone; a parent approves it once
    var micAsked by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micAsked = true }
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) {
        companion?.wake()
        if (!hasMic) askMic.launch(Manifest.permission.RECORD_AUDIO) else micAsked = true
    }

    suspend fun say(key: String) {
        talking = true
        try { voicePlayer.say(key, language) } finally { talking = false }
    }

    val word = state.word
    LaunchedEffect(state.roundIndex, micAsked) {
        if (word == null || !micAsked) return@LaunchedEffect
        val text = word.word.resolve(language)
        val alsoCounts = if (language == Language.ENGLISH) word.alsoCountsEn else word.alsoCountsHe
        delay(500)
        if (state.roundIndex == 0) say("voice_say_intro")
        val wordKey = "voice_say_${word.id}"
        say(wordKey)
        var last: WordTry? = null
        var nearMiss = false
        for (attempt in 0 until SayItViewModel.TRIES) {
            when (last) {
                null -> say("voice_say_your_turn")
                WordTry.SILENT -> { say("voice_say_again"); say(wordKey) }
                WordTry.CLOSE -> { say("voice_say_almost"); say(wordKey); say("voice_say_your_turn") }
                else -> { say("voice_say_listen"); say(wordKey); say("voice_say_your_turn") }
            }
            last = companion?.listenForWord(text, alsoCounts) ?: run {
                delay(3500)  // no microphone: just leave time to say it
                WordTry.SPOKE
            }
            if (last == WordTry.CLOSE) nearMiss = true
            if (last == WordTry.WORD || last == WordTry.SPOKE) break
        }
        when {
            last == WordTry.WORD -> {
                sfxPlayer.play(Sfx.SUCCESS)
                confetti = true
                viewModel.award()
                scope.launch { pictureReaction.celebrate() }
                talking = true
                try { voicePlayer.praise(language) } finally { talking = false }
            }
            last == WordTry.SPOKE || nearMiss -> {
                sfxPlayer.play(Sfx.POP)
                viewModel.award()
                scope.launch { pictureReaction.celebrate() }
                say("voice_say_nice")
            }
            else -> Unit  // no star this time, and no "no" either: Puki just says the word once more
        }
        say("voice_say_${word.id}")
        delay(900)
        confetti = false
        viewModel.next()
    }

    GameBackground(
            scene = Scene.SUNSET,
        colors = listOf(Color(0xFFFFF59D), Color(0xFFFFE082), Color(0xFFFFCC80), Color(0xFFFFB74D)),
        withCompanion = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                onBack = onBack,
                roundIndex = state.roundIndex,
                totalRounds = state.words.size,
                starsEarned = state.starsEarned
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HostParrot(talking = talking, listening = listening)
                Spacer(Modifier.width(48.dp))
                if (word != null) {
                    // The picture; tapping it says the word again
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .reaction(pictureReaction)
                                .size(300.dp)
                                .shadow(10.dp, RoundedCornerShape(40.dp))
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color.White)
                                .border(
                                    width = if (listening) 8.dp else 0.dp,
                                    color = Color(0xFFFFEB3B),
                                    shape = RoundedCornerShape(40.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { if (!listening && !talking) scope.launch { say("voice_say_${word.id}") } },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(word.drawableRes),
                                contentDescription = word.word.resolve(language),
                                modifier = Modifier.size(220.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        // For the grown-up reading along
                        Text(
                            text = word.word.resolve(language),
                            style = MaterialTheme.typography.displaySmall,
                            color = EarthBrown
                        )
                    }
                }
            }
        }

        ConfettiOverlay(show = confetti)

        if (state.sessionComplete) {
            RoundCompleteSheet(
                starsEarned = state.starsEarned,
                onContinue = onPlayAgain,
                onHome = onBack
            )
        }
    }
}

/** Puki as the game's host: bobs, moves its beak while talking, and glows while listening. */
@Composable
private fun HostParrot(talking: Boolean, listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "host")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (talking) 260 else 1400), RepeatMode.Reverse),
        label = "bob"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "pulse"
    )
    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            // Rings, like sound coming in: "I'm listening"
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (k in 0..1) {
                    val t = (pulse + k * 0.5f) % 1f
                    drawCircle(
                        color = Color(0xFFFFEB3B).copy(alpha = 0.9f * (1f - t)),
                        radius = size.minDimension * (0.32f + 0.18f * t),
                        center = Offset(size.width / 2, size.height / 2),
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
            }
        }
        TalkingParrotImage(
            talking = talking,
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    translationY = -bob * (if (talking) 12.dp.toPx() else 6.dp.toPx())
                    rotationZ = if (talking) (bob - 0.5f) * 8f else 0f
                    val grow = if (listening) 1.1f else 1f
                    scaleX = grow
                    scaleY = grow
                }
        )
    }
}
