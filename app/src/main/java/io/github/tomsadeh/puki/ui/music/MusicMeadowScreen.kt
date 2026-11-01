package io.github.tomsadeh.puki.ui.music

import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.PromptBubble
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberCompact
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.instruments
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.localized
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.tomsadeh.puki.audio.reactOr
import io.github.tomsadeh.puki.ui.common.GameSession
import kotlinx.coroutines.launch

enum class MusicPhase { FREE_PLAY, SHOW_PATTERN, PLAYER_TURN, SUCCESS, SESSION_COMPLETE }

data class MusicState(
    val phase: MusicPhase = MusicPhase.FREE_PLAY,
    val pattern: List<Int> = emptyList(),
    val playerInput: List<Int> = emptyList(),
    val highlightedInstrument: Int = -1,
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val totalStarsEarned: Int = 0,
    val patternLength: Int = 1,  // a one-note tune first: 1, 1, 2, 2
    val isReplay: Boolean = false,  // the same tune again, after a wrong note
    val tuneMisses: Int = 0
) {
    /** The instrument the tune wants now, or -1 when it isn't her turn. */
    val nextNote get() = if (phase == MusicPhase.PLAYER_TURN) pattern.getOrElse(playerInput.size) { -1 } else -1
}

private const val LONGEST_TUNE = 3

class MusicMeadowViewModel(application: Application) : AndroidViewModel(application) {
    private val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        // Tunes start one note longer at each level
        viewModelScope.launch {
            val level = session.level()
            _state.value = _state.value.copy(patternLength = level.coerceAtMost(2))  // tunes she can win at her age
        }
    }

    fun startSimonSays() {
        val pattern = List(_state.value.patternLength) { (0 until instruments.size).random() }
        _state.value = _state.value.copy(phase = MusicPhase.SHOW_PATTERN, pattern = pattern, playerInput = emptyList(), isReplay = false, tuneMisses = 0)
    }

    fun onShowPatternDone() {
        _state.value = _state.value.copy(phase = MusicPhase.PLAYER_TURN, highlightedInstrument = -1)
    }

    fun setHighlight(index: Int) {
        _state.value = _state.value.copy(highlightedInstrument = index)
    }

    fun onInstrumentTapped(index: Int): Boolean? {
        val current = _state.value
        if (current.phase == MusicPhase.FREE_PLAY) return null

        val newInput = current.playerInput + index
        val expectedSoFar = current.pattern.take(newInput.size)

        if (newInput != expectedSoFar) {
            // Wrong: play her the same tune again (no grown-up needed to start over)
            // The second miss makes the tune as short as what she got right, so she can always finish
            val misses = current.tuneMisses + 1
            val shorter = if (misses >= 2) current.pattern.take(current.playerInput.size.coerceAtLeast(1)) else current.pattern
            _state.value = current.copy(pattern = shorter, playerInput = emptyList(), phase = MusicPhase.SHOW_PATTERN, isReplay = true, tuneMisses = misses)
            session.slip()
            return false
        }

        if (newInput.size == current.pattern.size) {
            // Pattern complete!
            val newStars = current.totalStarsEarned + 1
            val nextRound = current.roundIndex + 1
            _state.value = current.copy(
                playerInput = newInput,
                phase = if (nextRound >= current.totalRounds) MusicPhase.SESSION_COMPLETE else MusicPhase.SUCCESS,
                totalStarsEarned = newStars,
                roundIndex = nextRound,
                patternLength = (current.patternLength + if (nextRound % 2 == 0) 1 else 0).coerceAtMost(LONGEST_TUNE)
            )
            session.star()
            if (nextRound >= current.totalRounds) session.finish(current.totalRounds)
            return true
        }

        _state.value = current.copy(playerInput = newInput)
        return null // still going
    }

    /** Straight on to the next tune. */
    fun nextRound() = startSimonSays()
}

@Composable
fun MusicMeadowScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    onDance: () -> Unit,  // the dancer on the stage opens Dance and Freeze
    viewModel: MusicMeadowViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    // How many times each instrument has been played (each time, notes float up from it)
    val plays = remember { mutableStateListOf(0, 0, 0, 0) }
    val reactions = remember { instruments.map { ReactionState() } }
    val compact = rememberCompact()
    var showCard by remember { mutableStateOf(false) }
    var xyloNote by remember { mutableIntStateOf(0) }
    fun playInstrument(index: Int) {
        plays[index] = plays[index] + 1
        when (instruments[index].id) {
            "drum" -> sfxPlayer.play(Sfx.DRUM)
            "xylophone" -> {
                // Up and down a happy scale, one bar at a time
                sfxPlayer.play(Sfx.XYLOPHONE, rate = XYLO_SCALE[xyloNote % XYLO_SCALE.size])
                xyloNote++
            }
            "tambourine" -> sfxPlayer.play(Sfx.TAMBOURINE)
            else -> sfxPlayer.play(Sfx.MARACAS)
        }
    }

    LaunchedEffect(Unit) {
        companion?.setScene("במשחק המוזיקה: יש תוף, קסילופון, תוף מרים ורעשנים.", "In the music game: there's a drum, a xylophone, a tambourine and maracas.")
        delay(500)
        voicePlayer.play("voice_music_free", language)
    }

    // Her turn and quiet: "your turn", and the next note lights up
    val help = rememberHelp(state.roundIndex to state.playerInput.size, active = state.phase == MusicPhase.PLAYER_TURN) {
        voicePlayer.play("voice_music_your_turn", language)
        val note = state.nextNote
        if (note >= 0) scope.launch {
            viewModel.setHighlight(note)
            delay(700)
            viewModel.setHighlight(-1)
        }
    }

    // Show pattern animation
    LaunchedEffect(state.phase) {
        if (state.phase == MusicPhase.PLAYER_TURN) voicePlayer.play("voice_music_your_turn", language)
        if (state.phase == MusicPhase.SUCCESS || state.phase == MusicPhase.SESSION_COMPLETE) {
            companion.reactOr("ניגנו את המנגינה בדיוק נכון!", "The child played the tune back just right!") { voicePlayer.playPraise(language) }
            reactions.forEachIndexed { i, r -> launch { delay(i * 80L); r.celebrate() } }
        }
        if (state.phase == MusicPhase.SESSION_COMPLETE) {
            delay(2600)
            showCard = true
        }
        if (state.phase == MusicPhase.SHOW_PATTERN) {
            // "Listen and remember!" first, then the pattern (after a wrong note, let "try again" finish)
            if (state.isReplay) delay(1600)
            voicePlayer.play("voice_music_listen", language)
            delay(1800)
            for (noteIndex in state.pattern) {
                viewModel.setHighlight(noteIndex)
                playInstrument(noteIndex)
                delay(600)
                viewModel.setHighlight(-1)
                delay(200)
            }
            viewModel.onShowPatternDone()
        }
    }

    GameBackground(
            scene = Scene.STAGE
    ) {
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(
                onBack = onBack,
                roundIndex = state.roundIndex,
                // (the free play before the tunes has no rounds to count)
                totalRounds = if (state.phase == MusicPhase.FREE_PLAY) 0 else state.totalRounds,
                starsEarned = state.totalStarsEarned
            )

            PromptBubble(
                when (state.phase) {
                    MusicPhase.FREE_PLAY -> BilingualText("תלחץ על הכלים ותנגן!", "Tap the instruments and play!", heGirl = "תלחצי על הכלים ותנגני!").localized()
                    MusicPhase.SHOW_PATTERN -> BilingualText("תקשיב ותזכור...", "Listen and remember...", heGirl = "תקשיבי ותזכרי...").localized()
                    MusicPhase.PLAYER_TURN -> BilingualText("עכשיו תורך!", "Your turn!").resolve(language)
                    MusicPhase.SUCCESS -> BilingualText("מצוין!", "Awesome!").resolve(language)
                    MusicPhase.SESSION_COMPLETE -> ""
                }
            )

            // The tune's notes, filling up as she plays them
            if (state.phase == MusicPhase.PLAYER_TURN) Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(state.pattern.size) { i ->
                    Box(
                        modifier = Modifier
                            .popIn(i)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (i < state.playerInput.size) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f))
                    )
                }
            }

            // The instruments stand on the stage, each in its own soft pool of light
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (compact) 90.dp else 130.dp),  // inside the curtains
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                instruments.forEachIndexed { index, instrument ->
                    val isHighlighted = state.highlightedInstrument == index
                    val interactionSource = remember(instrument.id) { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = when {
                            isHighlighted -> 1.2f
                            isPressed -> 0.88f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                        label = "inst_scale"
                    )
                    val glow by animateFloatAsState(if (isHighlighted || isPressed) 1f else 0.3f, tween(160), label = "inst_glow")

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)  // they share the stage, so none gets squeezed
                            // Not all in one line: the middle two a little higher, clear of the play button
                            .offset(y = if (index == 0 || index == instruments.lastIndex) 18.dp else (-18).dp)
                            .popIn(index)
                            .then(if (isHighlighted) Modifier else Modifier.idleBob(index, 0.7f))
                            .reaction(reactions[index], jumpHeight = 24.dp)
                            // Stuck, or a wrong note in this tune: the next note shows itself
                            .hintGlow((help.showHint || state.tuneMisses > 0) && state.nextNote == index)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .drawBehind {
                                    // A round glow that fades out, so there's no edge to see
                                    drawCircle(
                                        Brush.radialGradient(listOf(instrument.color.copy(alpha = 0.8f * glow), Color.Transparent)),
                                        radius = size.minDimension / 2
                                    )
                                }
                                .clickable(interactionSource = interactionSource, indication = null) {
                                    playInstrument(index)
                                    if (state.phase == MusicPhase.PLAYER_TURN) {
                                        val result = viewModel.onInstrumentTapped(index)
                                        help.touched()
                                        if (result == false) {
                                            sfxPlayer.play(Sfx.WRONG_GENTLE)
                                            voicePlayer.play("voice_prompt_try_again", language)
                                            help.missed()
                                            scope.launch { reactions[index].shake() }
                                        } else if (result == true) {
                                            sfxPlayer.play(Sfx.SUCCESS)
                                        }
                                    } else if (state.phase == MusicPhase.FREE_PLAY) {
                                        voicePlayer.play("voice_instrument_${instrument.id}", language)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = instrument.drawableRes),
                                contentDescription = instrument.name.resolve(language),
                                modifier = Modifier.fillMaxSize(0.8f).graphicsLayer { scaleX = scale; scaleY = scale }
                            )
                            NoteBurst(key = plays[index], color = lerp(instrument.color, Color.White, 0.35f))
                        }
                        if (!compact) Text(instrument.name.resolve(language), style = MaterialTheme.typography.titleMedium, color = CloudWhite, maxLines = 1, softWrap = false)
                    }
                }
            }

            // Start the tune game (on a short screen it floats over the stage, so the instruments keep the room)
            if (state.phase == MusicPhase.FREE_PLAY && !compact) PlayButton(130.dp) { viewModel.startSimonSays() }

            if (state.phase == MusicPhase.SUCCESS) {
                LaunchedEffect(state.roundIndex) {
                    delay(if (companion?.isReady == true) 3400 else 2400)
                    viewModel.nextRound()
                }
            }
        }

        // Dance and Freeze, waiting at the side of the stage (after the column, or it would take her touch)
        // (gone once a tune starts, so a stray tap can't walk out on it)
        if (state.phase == MusicPhase.FREE_PLAY) Box(modifier = Modifier.fillMaxSize()) {
            DanceButton(onClick = onDance, modifier = Modifier.align(Alignment.TopStart).padding(start = if (compact) 4.dp else 44.dp, top = if (compact) 92.dp else 120.dp))
        }
        if (state.phase == MusicPhase.FREE_PLAY && compact) Box(modifier = Modifier.fillMaxSize().padding(bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
            PlayButton(88.dp) { viewModel.startSimonSays() }
        }
        ConfettiOverlay(show = state.phase == MusicPhase.SUCCESS || state.phase == MusicPhase.SESSION_COMPLETE)
        if (showCard) {
            RoundCompleteSheet(starsEarned = state.totalStarsEarned, onContinue = onPlayAgain, onHome = onBack)
        }
    }
}


/** A big round play button (no reading needed), gently pulsing. */
@Composable
private fun PlayButton(side: Dp, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "start").animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "start_pulse"
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .size(side)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF81C784), Color(0xFF43A047))))
            .border(6.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(side * 0.43f)) {
            // A play triangle, nudged right so it looks centered
            val w = size.width
            val h = size.height
            val tri = Path().apply {
                moveTo(w * 0.22f, h * 0.08f)
                lineTo(w * 0.98f, h * 0.5f)
                lineTo(w * 0.22f, h * 0.92f)
                close()
            }
            drawPath(tri, Color.White)
        }
    }
}

/** The xylophone's notes, as playback rates of its one bar: a major pentatonic, up and back down. */
private val XYLO_SCALE = listOf(1f, 1.122f, 1.26f, 1.498f, 1.682f, 2f, 1.682f, 1.498f, 1.26f, 1.122f)

/** A couple of music notes that float up and fade each time [key] changes. */
@Composable
private fun NoteBurst(key: Int, color: Color) {
    if (key == 0) return
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing)) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        listOf(Triple(-0.25f, "\u266A", 0f), Triple(0.27f, "\u266B", 0.15f)).forEach { (dx, glyph, lag) ->
            Text(
                glyph,
                fontSize = 52.sp,
                color = color,
                modifier = Modifier.graphicsLayer {
                    val p = ((t.value - lag) / (1f - lag)).coerceIn(0f, 1f)
                    translationX = dx * 220.dp.toPx() + kotlin.math.sin(p * 6f) * 8.dp.toPx()
                    translationY = -40.dp.toPx() - p * 130.dp.toPx()
                    alpha = if (p <= 0f) 0f else 1f - p
                    scaleX = 0.7f + p * 0.5f
                    scaleY = 0.7f + p * 0.5f
                }
            )
        }
    }
}

private const val ISLAND = "music"
