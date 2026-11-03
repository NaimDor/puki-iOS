package io.github.tomsadeh.puki.ui.silly

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.audio.VoicePlayer
import io.github.tomsadeh.puki.audio.reactOr
import io.github.tomsadeh.puki.brain.SpokenAnswer
import io.github.tomsadeh.puki.data.content.SillyContent
import io.github.tomsadeh.puki.data.content.SillyItem
import io.github.tomsadeh.puki.data.content.SillyKind
import io.github.tomsadeh.puki.data.content.SillyPicture
import io.github.tomsadeh.puki.data.content.level3Colors
import io.github.tomsadeh.puki.data.content.level3Foods
import io.github.tomsadeh.puki.data.content.level3Shapes
import io.github.tomsadeh.puki.data.content.numberWords
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
import io.github.tomsadeh.puki.ui.components.TalkingParrotImage
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import io.github.tomsadeh.puki.ui.shapes.drawShapeFilled
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class SillyState(
    val items: List<SillyItem> = emptyList(),
    val roundIndex: Int = 0,
    val starsEarned: Int = 0,
    val sessionComplete: Boolean = false,
) {
    val item get() = items.getOrNull(roundIndex)
}

class SillyPukiViewModel(application: Application) : AndroidViewModel(application) {
    private val session = GameSession("silly", (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(SillyState())
    val state: StateFlow<SillyState> = _state.asStateFlow()

    init {
        // Mixed kinds: a sound, then a color, then a count…, never the same kind twice in a row
        val byKind = SillyContent.playable.groupBy { it.kind }.mapValues { it.value.shuffled().toMutableList() }
        val kinds = SillyKind.entries.shuffled()
        val picked = (0 until ROUNDS).mapNotNull { i -> byKind[kinds[i % kinds.size]]?.removeFirstOrNull() }
        _state.value = SillyState(items = picked)
    }

    fun award() {
        _state.value = _state.value.copy(starsEarned = _state.value.starsEarned + 1)
        session.star()
    }

    fun next() {
        val s = _state.value
        if (s.roundIndex + 1 >= s.items.size) session.finish(s.items.size)
        _state.value = if (s.roundIndex + 1 >= s.items.size) s.copy(sessionComplete = true) else s.copy(roundIndex = s.roundIndex + 1)
    }

    companion object {
        const val ROUNDS = 5
    }
}

/** A choice to tap: the real answer, or the wrong one Puki said. */
private data class Choice(val id: String, val right: Boolean)

/** What Puki got wrong, found in his [line] (another food, color, shape or number of the same kind), or another at random. */
private fun wrongChoice(item: SillyItem, line: String, english: Boolean): String {
    fun said(he: String, en: String) = SillyContent.saysTruth(line, if (english) en else he)
    val others: List<Pair<String, Pair<String, String>>> = when (item.kind) {
        SillyKind.FOOD -> level3Foods.filter { it.id != item.answerId }.map { it.id to (it.name.he to it.name.en.lowercase()) }
        SillyKind.COLOR -> level3Colors.filter { it.id != item.answerId }.map { it.id to (it.name.he to it.name.en.lowercase()) }
        SillyKind.SHAPE -> level3Shapes.filter { it.id != item.answerId }.map { it.id to (it.name.he to it.name.en.lowercase()) }
        SillyKind.COUNT -> (1..10).filter { "$it" != item.answerId }.map { "$it" to ("$it" to "$it") }
        SillyKind.SOUND -> emptyList()
    }
    return others.firstOrNull { (_, n) -> said(n.first, n.second) }?.first ?: others.random().first
}

private suspend fun VoicePlayer.say(key: String, language: Language) = withTimeoutOrNull(15_000) {
    suspendCancellableCoroutine { cont ->
        play(key, language) { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { stop() }
    }
}

/**
 * Silly Puki: a picture, and Puki says something wrong about it on purpose ("the cow says meow!"), written by
 * his brain. The child puts him right, out loud or by tapping the real answer, and he laughs at himself and says
 * it; a tap on his mistake, or nothing at all, gets the same warm "oops", never a "no". Each line is checked
 * before he says it: the silly one mustn't give the answer away, the correction must say it (and if no line
 * passes, the recorded answer says it). Only with the brain: the island hides the game without it.
 */
@Composable
fun SillyPukiScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,
    viewModel: SillyPukiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val english = language == Language.ENGLISH
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var talking by remember { mutableStateOf(false) }
    var confetti by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var tapped by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val listening by (companion?.listening ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val thinking by (companion?.thinking ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val pictureReaction = rememberReactionState(state.roundIndex)

    var micAsked by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micAsked = true }
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) {
        companion?.wake()
        if (!hasMic) askMic.launch(Manifest.permission.RECORD_AUDIO) else micAsked = true
    }

    val paused = rememberGamePaused()
    suspend fun say(key: String) {
        awaitUnpaused(paused)
        talking = true
        try { voicePlayer.say(key, language) } finally { talking = false }
    }

    val item = state.item
    LaunchedEffect(state.roundIndex, micAsked) {
        if (item == null || !micAsked) return@LaunchedEffect
        val truth = if (english) item.truthEn else item.truthHe
        delay(600)
        item.nameKey?.let { say(it) }
        // His brain may still be waking (the island opens the game when it's on): give it a moment
        withTimeoutOrNull(20_000) { while (companion?.isReady != true) delay(300) }
        awaitUnpaused(paused)
        val silly = companion?.line("silly", if (english) item.en else item.he, truth, mustSay = false)
        if (silly == null) {
            // No silly line this time: just the real thing, and on
            scope.launch { pictureReaction.hop() }
            say(item.truthKey)
            delay(700)
            viewModel.next()
            return@LaunchedEffect
        }
        scope.launch { pictureReaction.hop() }

        // She puts him right: out loud, or a tap on the real answer (or on his mistake)
        choices = if (item.kind == SillyKind.SOUND) emptyList()
            else listOf(Choice(item.answerId, true), Choice(wrongChoice(item, silly, english), false)).shuffled()
        val tap = CompletableDeferred<Boolean>().also { tapped = it }
        val forms = when (item.kind) {
            SillyKind.COUNT -> listOf(truth) + numberWords(item.truthEn.toInt(), english)
            else -> listOf(truth)
        }
        var heard = ""
        val listen = scope.launch {
            val h = companion.listenForAnswer(forms) ?: return@launch
            heard = h
            if (h.isNotBlank()) tap.complete(SpokenAnswer.pick(h, listOf(forms)) == 0)
        }
        val right = withTimeoutOrNull(12_000) { tap.await() }  // (toddlers take their time)
        listen.cancel()
        tapped = null
        choices = emptyList()
        val answer = when {
            right == true -> SillyContent.Answer.RIGHT
            right == false || heard.isNotBlank() -> SillyContent.Answer.OTHER
            else -> SillyContent.Answer.NOTHING
        }
        if (answer == SillyContent.Answer.RIGHT) {
            sfxPlayer.play(Sfx.SUCCESS)
            confetti = true
            viewModel.award()
            scope.launch { pictureReaction.celebrate() }
        }
        awaitUnpaused(paused)
        val fixed = companion.line("silly_fix", SillyContent.fixInput(english, silly, truth, answer, heard), truth, mustSay = true)
        // The real thing: the cow's own moo, the color's name, the number
        if (fixed == null || item.kind == SillyKind.SOUND || item.kind == SillyKind.COUNT) {
            scope.launch { pictureReaction.hop() }
            say(item.truthKey)
        }
        delay(900)
        confetti = false
        viewModel.next()
    }

    LaunchedEffect(state.sessionComplete) {
        if (state.sessionComplete) companion.reactOr("שיחקו בפוקי המבולבל ותיקנו אותו!", "The child played Silly Puki and put him right!") { voicePlayer.playPraise(language) }
    }

    GameBackground(scene = Scene.PLAYGROUND, withCompanion = false) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(onBack = onBack, roundIndex = state.roundIndex, totalRounds = state.items.size, starsEarned = state.starsEarned)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val pictureSize = minOf(300.dp, maxHeight - 110.dp)
                val hostSize = minOf(240.dp, maxHeight)
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Host(talking = talking || thinking, listening = listening, size = hostSize)
                    Spacer(Modifier.width(40.dp))
                    if (item != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .popIn(key = state.roundIndex)
                                .idleBob(amount = 0.5f)
                                .reaction(pictureReaction)
                                .size(pictureSize)
                                .shadow(10.dp, RoundedCornerShape(40.dp))
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color.White)
                                .border(if (listening) 8.dp else 0.dp, Color(0xFFFFEB3B), RoundedCornerShape(40.dp)),
                            contentAlignment = Alignment.Center
                        ) { Picture(item.picture, pictureSize * 0.74f) }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.height(96.dp)) {
                            for (c in choices) ChoiceBubble(item.kind, c.id) {
                                sfxPlayer.play(Sfx.POP)
                                tapped?.complete(c.right)
                            }
                        }
                    }
                }
            }
        }
        ConfettiOverlay(show = confetti)
        if (state.sessionComplete) RoundCompleteSheet(starsEarned = state.starsEarned, onContinue = onPlayAgain, onHome = onBack)
    }
}

/** The round's picture: the animal, the thing, the shape, or the things to count. */
@Composable
private fun Picture(picture: SillyPicture?, size: Dp) {
    when (picture) {
        is SillyPicture.Animal -> Image(painterResource(picture.animal.drawableRes), contentDescription = null, modifier = Modifier.size(size))
        is SillyPicture.Thing -> Image(painterResource(picture.res), contentDescription = null, modifier = Modifier.size(size))
        is SillyPicture.Shape -> Canvas(Modifier.size(size)) { drawShapeFilled(picture.shape, Offset(this.size.width / 2, this.size.height / 2), this.size.minDimension * 0.42f) }
        is SillyPicture.Count -> {
            val n = picture.round.targetNumber
            val perRow = if (n <= 4) n else (n + 1) / 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (row in (0 until n).chunked(perRow)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in row) Image(painterResource(picture.round.itemIcon), contentDescription = null, modifier = Modifier.size(size / (perRow + 1)))
                }
            }
        }
        null -> Unit
    }
}

/** A choice to tap: a food, a color, a shape, or a number. */
@Composable
private fun ChoiceBubble(kind: SillyKind, id: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            SillyKind.FOOD -> level3Foods.firstOrNull { it.id == id }?.let { Image(painterResource(it.drawableRes), contentDescription = null, modifier = Modifier.size(70.dp)) }
            SillyKind.COLOR -> level3Colors.firstOrNull { it.id == id }?.let { c ->
                Box(Modifier.size(70.dp).clip(CircleShape).background(c.color).border(2.dp, Color.Black.copy(alpha = 0.12f), CircleShape))
            }
            SillyKind.SHAPE -> level3Shapes.firstOrNull { it.id == id }?.let { s ->
                Canvas(Modifier.size(70.dp)) { drawShapeFilled(s, Offset(size.width / 2, size.height / 2), size.minDimension * 0.42f) }
            }
            SillyKind.COUNT -> Text(id, fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
            SillyKind.SOUND -> Unit
        }
    }
}

/** Puki hosting: bobs, talks, and glows while he listens. */
@Composable
private fun Host(talking: Boolean, listening: Boolean, size: Dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(size * 0.9f).clip(CircleShape).background(Color(0x55FFEB3B)))
        TalkingParrotImage(talking = talking, modifier = Modifier.size(size * 0.77f).idleBob(amount = if (talking) 1f else 0.5f))
    }
}
