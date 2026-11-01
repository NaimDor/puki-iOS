package io.github.tomsadeh.puki.ui.music

import android.app.Application
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.level1Animals
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.reaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** What Puki calls out when the music stops; the animals do it too, and so does the child. */
private enum class Move(val voice: String) {
    FREEZE("voice_dance_freeze"), JUMP("voice_dance_jump"), CLAP("voice_dance_clap"), SPIN("voice_dance_spin")
}

private const val STOPS = 5  // music stops in one dance

class DanceViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("dance", (application as PukiApplication).progressRepository, viewModelScope)
}

/**
 * Dance and Freeze: the animals dance while the music plays; when it stops Puki calls out a move
 * (freeze, jump, clap, spin) and everyone does it, away from the screen. Then the dancing goes on.
 */
@Composable
fun DanceScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,
    viewModel: DanceViewModel = viewModel()
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val musicPlayer = LocalMusicPlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val companion = LocalCompanion.current
    val scope = rememberCoroutineScope()

    var started by remember { mutableStateOf(false) }
    var dancing by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf<Move?>(null) }
    var stops by remember { mutableStateOf(0) }
    var celebrate by remember { mutableStateOf(false) }
    var sessionComplete by remember { mutableStateOf(false) }
    val dancers = remember { level1Animals.shuffled().take(3) }
    val reactions = remember { dancers.map { ReactionState() } }

    // The music is the game: make sure it's the lively track, and never leave it paused
    LaunchedEffect(Unit) {
        companion?.setScene("ריקוד ועצירה: רוקדים עם החיות, וכשהמוזיקה נעצרת עושים מה שפוקי אומר.", "Dance and freeze: dancing with the animals, and when the music stops doing what Puki says.")
        // The dance track (res/raw/music_dance.mp3) once there is one; until then the main theme plays on
        musicPlayer.playTrack("music_dance")
        delay(500)
        voicePlayer.play("voice_dance_intro", language)
    }
    DisposableEffect(Unit) {
        onDispose {
            musicPlayer.resume()
            musicPlayer.playTrack("music_hub")
        }
    }

    // One dance: play for a while, stop, call a move, hold it, and again
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        var last: Move? = null
        repeat(STOPS) { i ->
            dancing = true
            move = null
            musicPlayer.resume()
            delay(Random.nextLong(5000, 9000))
            dancing = false
            musicPlayer.pause()
            val next = (Move.entries - setOfNotNull(last)).random()
            last = next
            move = next
            voicePlayer.play(next.voice, language)
            delay(3800)
            stops = i + 1
            if (i < STOPS - 1) {
                voicePlayer.play("voice_dance_again", language)
                delay(900)
            }
        }
        // The end: everyone takes a bow
        move = null
        musicPlayer.resume()
        celebrate = true
        sfxPlayer.play(Sfx.SUCCESS)
        reactions.forEachIndexed { i, r -> launch { delay(i * 120L); r.celebrate() } }
        viewModel.session.star()
        voicePlayer.playPraise(language)
        delay(2600)
        celebrate = false
        viewModel.session.finish(1)
        sessionComplete = true
    }

    GameBackground(scene = Scene.STAGE) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(onBack = onBack, roundIndex = stops.coerceAtMost(STOPS - 1), totalRounds = STOPS, starsEarned = if (sessionComplete) 1 else 0)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val size = minOf(maxWidth / 3.6f, maxHeight * 0.62f)
                // Read only inside graphicsLayer, so the beat redraws the dancers without recomposing them
                val beat = rememberInfiniteTransition(label = "beat").animateFloat(
                    0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Restart), label = "beat_phase"
                )
                val spin = rememberInfiniteTransition(label = "spin").animateFloat(
                    0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin_turn"
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                    // The move being called, big and simple
                    Box(modifier = Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                        move?.let { m ->
                            Text(
                                text = moveSign(m),
                                fontSize = 64.sp,
                                modifier = Modifier.popIn(key = m)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.15f), verticalAlignment = Alignment.Bottom) {
                        dancers.forEachIndexed { i, animal ->
                            Image(
                                painter = painterResource(animal.drawableRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(size)
                                    .reaction(reactions[i])
                                    .graphicsLayer {
                                        val wave = sin(((beat.value + i * 0.33f) % 1f) * 2 * Math.PI).toFloat()
                                        transformOrigin = TransformOrigin(0.5f, 1f)
                                        when {
                                            dancing -> {  // sway and bounce to the beat
                                                rotationZ = wave * 12f
                                                translationY = -kotlin.math.abs(wave) * size.toPx() * 0.12f
                                            }
                                            move == Move.JUMP -> translationY = -kotlin.math.abs(wave) * size.toPx() * 0.3f
                                            move == Move.SPIN -> rotationY = spin.value
                                            move == Move.CLAP -> { scaleX = 1f + 0.08f * wave; scaleY = 1f - 0.06f * wave }
                                            move == Move.FREEZE -> rotationZ = if (i % 2 == 0) -10f else 10f  // a funny frozen pose
                                        }
                                    }
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        scope.launch { reactions[i].celebrate() }
                                        voicePlayer.play("voice_sound_${animal.id}", language)
                                    }
                            )
                        }
                    }
                    // Start the dance
                    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        if (!started) Box(
                            modifier = Modifier
                                .popIn()
                                .idleBob(0, 1.4f)
                                .size(130.dp)
                                .shadow(10.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFFFFCA28))
                                .border(6.dp, Color.White, CircleShape)
                                .clickable {
                                    started = true
                                    sfxPlayer.play(Sfx.POP)
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("🎶", fontSize = 64.sp) }
                    }
                }
            }
        }
        ConfettiOverlay(show = celebrate)
        if (sessionComplete) RoundCompleteSheet(starsEarned = 1, onContinue = onPlayAgain, onHome = onBack)
    }
}

/** A picture for each move, so even a child who doesn't follow the words sees what to do. */
private fun moveSign(m: Move) = when (m) {
    Move.FREEZE -> "🧊"
    Move.JUMP -> "⬆️"
    Move.CLAP -> "👏"
    Move.SPIN -> "🌀"
}

/** The disco ball on the music stage that opens Dance and Freeze. */
@Composable
fun DanceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .idleBob(2, 1.2f)
            .size(96.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFFE1BEE7))
            .border(5.dp, Color(0xFFFFCA28), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text("💃", fontSize = 52.sp) }
}
