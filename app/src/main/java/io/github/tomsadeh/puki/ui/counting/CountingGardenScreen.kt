package io.github.tomsadeh.puki.ui.counting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import io.github.tomsadeh.puki.ui.myday.PukiCorner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.AnswerOutLoud
import io.github.tomsadeh.puki.data.content.numberWords
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.components.Scatter
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberRoundFlow
import io.github.tomsadeh.puki.ui.components.rememberSessionSeed
import kotlinx.coroutines.delay
import io.github.tomsadeh.puki.audio.reactOr
import kotlinx.coroutines.launch

/**
 * Counting: one touch, one number. Each thing in the garden counts once, hops and keeps its
 * number. At the end the last number is said again (that's how many there are), and from level 2
 * the child answers "how many?" herself, from three numbers.
 */
@Composable
fun CountingGardenScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: CountingGardenViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionSeed = rememberSessionSeed()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val flow = rememberRoundFlow()

    if (state.isLoading) return

    val round = state.currentRound
    val target = round.targetNumber
    val itemReactions = remember(state.roundIndex) { List(target) { ReactionState() } }

    RoundPrompt(
        state.roundIndex,
        scene = "במשחק הספירה: סופרים ${round.itemName.he}, נוגעים בכל אחד.",
        sceneEn = "In the counting game: we're counting ${round.itemName.en.lowercase()}, touching each one.",
        "voice_prompt_count_along"
    )

    // Stuck? Ask again after a quiet moment; then the next thing to count (or the answer) glows
    val help = rememberHelp(state.roundIndex to state.phase, active = !flow.won && !state.roundComplete, onAssisted = viewModel.session::slip) {
        voicePlayer.play(if (state.phase == CountingPhase.HOW_MANY) "voice_prompt_how_many" else "voice_prompt_count_along", language)
    }

    // All the things hop one after another, the number is said once more, and on to the next round
    fun celebrate() {
        sfxPlayer.play(Sfx.SUCCESS)
        itemReactions.forEachIndexed { i, r -> scope.launch { delay(i * 90L); r.celebrate() } }
        val counted = "ספרו $target ${round.itemName.he}!"
        val countedEn = "The child counted $target ${round.itemName.en.lowercase()}!"
        voicePlayer.play("voice_number_$target", language) {
            companion.reactOr(counted, countedEn) { voicePlayer.playPraise(language) }
        }
        flow.win(3000, 4200) {
            viewModel.nextRound()
        }
    }

    // Counting done, from level 2: "how many?" once the last number has been said
    LaunchedEffect(state.roundIndex, state.phase) {
        if (state.phase == CountingPhase.HOW_MANY) {
            delay(1100)
            voicePlayer.play("voice_prompt_how_many", language)
        }
    }

    // "How many?": a number card picked, by a tap or by saying the number
    val cardReactions = remember(state.roundIndex, state.choices) { state.choices.associateWith { ReactionState() } }
    fun answer(n: Int) {
        val cardReaction = cardReactions[n] ?: return
        if (viewModel.onAnswer(n)) {
            scope.launch { cardReaction.celebrate() }
            celebrate()
        } else {
            sfxPlayer.play(Sfx.WRONG_GENTLE)
            help.missed()
            scope.launch { cardReaction.shake() }
            voicePlayer.playSequence(listOf("voice_number_$n", "voice_prompt_try_again"), language)
        }
    }
    AnswerOutLoud(
        state.roundIndex to state.phase,
        state.choices.map { numberWords(it, language == Language.ENGLISH) },
        active = state.phase == CountingPhase.HOW_MANY && !flow.won && !state.roundComplete,
        askedAfterMs = 1900
    ) { i -> state.choices.getOrNull(i)?.let { answer(it) } }

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

            // The things in their place, and beside them the count or "how many?". Side by side,
            // sized from the room there is: a phone on its side has very little height
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val few = target <= 5
                val itemSize = minOf(maxHeight * (if (few) 0.44f else 0.34f), if (few) 120.dp else 88.dp)
                val sunSize = minOf(maxHeight * 0.34f, 96.dp)
                val cardSize = minOf((maxHeight - 24.dp) / 3, 104.dp)
                val nextToCount = state.numbers.indexOfFirst { it == 0 }
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = PukiCorner),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .shadow(8.dp, RoundedCornerShape(36.dp))
                            .clip(RoundedCornerShape(36.dp))
                            .drawBehind { drawHabitat(round.habitat) }
                            .border(BorderStroke(6.dp, Color.White), RoundedCornerShape(36.dp))
                    ) {
                        Scatter(seed = sessionSeed + state.roundIndex, modifier = Modifier.fillMaxSize().padding(14.dp)) {
                            repeat(target) { i ->
                                CountedThing(
                                    icon = round.itemIcon,
                                    number = state.numbers[i],
                                    size = itemSize,
                                    index = i,
                                    roundIndex = state.roundIndex,
                                    reaction = itemReactions[i],
                                    hint = help.showHint && state.phase == CountingPhase.COUNT && i == nextToCount,
                                    onTap = {
                                        val number = viewModel.onItemTapped(i)
                                        if (number == 0) {
                                            // Already counted: a little wiggle, no new number
                                            if (state.phase == CountingPhase.COUNT) scope.launch { itemReactions[i].shake() }
                                            return@CountedThing
                                        }
                                        help.touched()
                                        sfxPlayer.play(Sfx.POP)
                                        scope.launch { itemReactions[i].celebrate() }
                                        if (viewModel.state.value.roundComplete) {
                                            // Level 1: the last one counted is the answer
                                            // (on a timer: a held Puki stops the line, and its callback would never come)
                                            voicePlayer.play("voice_number_$number", language)
                                            scope.launch { delay(900); celebrate() }
                                        } else {
                                            voicePlayer.play("voice_number_$number", language)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.width(maxOf(cardSize, sunSize * 1.5f)).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        if (state.phase == CountingPhase.HOW_MANY) {
                            state.choices.forEachIndexed { i, n ->
                                NumberCard(
                                    number = n,
                                    size = cardSize,
                                    index = i,
                                    reaction = cardReactions.getValue(n),
                                    hint = help.showHint && n == target,
                                    onTap = { answer(n) }
                                )
                            }
                        } else {
                            // The count so far, in a sun
                            Box(
                                modifier = Modifier
                                    .popIn(key = state.currentCount)
                                    .size(sunSize)
                                    .drawBehind { drawSunRays(size.width / 2) }
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFCA28)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${state.currentCount}",
                                    fontSize = with(LocalDensity.current) { (sunSize * 0.58f).toSp() },
                                    color = Color(0xFF5D4037),
                                    style = MaterialTheme.typography.headlineLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        ConfettiOverlay(show = flow.won)

        if (state.sessionComplete) {
            RoundCompleteSheet(
                starsEarned = state.totalStarsEarned,
                onContinue = onPlayAgain,
                onHome = onBack
            )
        }
    }
}

/** One thing in the garden: smaller and still until counted, then full size with its number. */
@Composable
private fun CountedThing(
    icon: Int,
    number: Int,
    size: Dp,
    index: Int,
    roundIndex: Int,
    reaction: ReactionState,
    hint: Boolean,
    onTap: () -> Unit
) {
    val counted = number > 0
    val scale by animateFloatAsState(
        targetValue = if (counted) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.35f, stiffness = 300f),
        label = "thing_scale_$index"
    )
    Box(
        modifier = Modifier
            .popIn(index, roundIndex)
            .size(size)
            .reaction(reaction)
            .hintGlow(hint)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (counted) Modifier.idleBob(index, 0.6f) else Modifier)
        )
        if (counted) {
            // Its number, in a little white badge at the top corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .popIn(key = number)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(BorderStroke(3.dp, Color(0xFFFFCA28)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", fontSize = 24.sp, color = Color(0xFF5D4037), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** A big number to answer "how many?" with. */
@Composable
private fun NumberCard(number: Int, size: Dp, index: Int, reaction: ReactionState, hint: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .popIn(index)
            .reaction(reaction, jumpHeight = 24.dp)
            .hintGlow(hint)
            .size(size)
            .shadow(8.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(BorderStroke(5.dp, Color(0xFF8BC34A)), RoundedCornerShape(28.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            fontSize = with(LocalDensity.current) { (size * 0.58f).toSp() },
            color = Color(0xFF5D4037),
            style = MaterialTheme.typography.headlineLarge
        )
    }
}
