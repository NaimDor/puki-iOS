package io.github.tomsadeh.puki.ui.peekaboo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.AnimalDef
import io.github.tomsadeh.puki.data.content.FEMININE_ANIMALS
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.CompanionParrot
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.AnswerOutLoud
import io.github.tomsadeh.puki.data.content.allAnimals
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Peekaboo barn: an animal hides on the farm (in the barn, behind the bush, the haystack or a
 * tree stump), Puki asks who's hiding and the animal's sound gives it away. A tap on the right
 * spot brings it out; any other spot sends out a butterfly and the animal peeks and calls again.
 * If she waits, the animal peeks by itself. Eight animals, then the end card.
 */
@Composable
fun PeekabooScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,
    viewModel: PeekabooViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val animal = state.animal
    val she = animal.id in FEMININE_ANIMALS

    RoundPrompt(
        state.roundIndex,
        scene = "במשחק המחבואים: ${animal.name.he} ${if (she) "מתחבאת" else "מתחבא"} ומחפשים ${if (she) "אותה" else "אותו"}.",
        sceneEn = "In the peekaboo game: the ${animal.name.en.lowercase()} is hiding, and we're looking for it.",
        "voice_prompt_whos_hiding", "voice_sound_${animal.id}"
    )

    // The animal peeks out a little (with its sound): a clue
    var peeking by remember(state.roundIndex) { mutableStateOf(false) }
    fun peek() {
        scope.launch {
            peeking = true
            voicePlayer.play("voice_sound_${animal.id}", language)
            delay(1500)
            peeking = false
        }
    }
    val help = rememberHelp(state.roundIndex, active = !state.found) { peek() }
    var butterflyFrom by remember { mutableStateOf<HidingSpot?>(null) }
    var butterflyKey by remember { mutableIntStateOf(0) }

    // A hiding spot tapped: the animal is found, or a butterfly flies out and the animal peeks
    fun tap(spot: HidingSpot) {
        when (viewModel.onSpotTapped(spot)) {
            true -> {
                sfxPlayer.play(Sfx.SUCCESS)
                val cheer = {
                    voicePlayer.playSequence(listOf("voice_peekaboo", "voice_sound_${animal.id}", "voice_animal_${animal.id}"), language)
                }
                val where = when (spot) {
                    HidingSpot.BARN -> "ברפת"
                    HidingSpot.BUSH -> "מאחורי השיח"
                    HidingSpot.HAY -> "מאחורי ערמת החציר"
                    HidingSpot.STUMP -> "בתוך הגזע"
                }
                val whereEn = when (spot) {
                    HidingSpot.BARN -> "in the barn"
                    HidingSpot.BUSH -> "behind the bush"
                    HidingSpot.HAY -> "behind the haystack"
                    HidingSpot.STUMP -> "in the tree stump"
                }
                if (companion != null) companion.react(
                    "מצאו את ה${animal.name.he}! ${if (she) "היא התחבאה" else "הוא התחבא"} $where.",
                    "The child found the ${animal.name.en.lowercase()}! It was hiding $whereEn.",
                    cheer
                ) else cheer()
                scope.launch {
                    delay(if (companion?.isReady == true) 4000 else 3200)
                    viewModel.nextRound()
                }
            }
            false -> {
                sfxPlayer.play(Sfx.POP)
                butterflyFrom = spot
                butterflyKey++
                help.missed()
                peek()
            }
            null -> Unit
        }
    }

    // "Who's hiding?" "פרה!": naming the animal finds it; another animal's name gets a peek
    val names = remember(state.roundIndex) { listOf(animal) + allAnimals.filter { it.id != animal.id }.shuffled().take(7) }
    AnswerOutLoud(state.roundIndex, names.map { listOf(it.name.resolve(language)) }, active = !state.found, askedAfterMs = 1200) { i ->
        if (i == 0) tap(state.spot) else {
            help.missed()
            peek()
        }
    }

    // Puki flies in the sky here: in his usual corner he'd stand in front of the barn
    GameBackground(scene = Scene.FARM, withCompanion = false, colors = listOf(Color(0xFF81C784), Color(0xFF66BB6A), Color(0xFF4CAF50))) {
      Box(modifier = Modifier.fillMaxSize()) {
        CompanionParrot(modifier = Modifier.align(Alignment.TopStart).padding(top = 96.dp, start = 12.dp))
        Column(modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp)) {
            GameTopBar(onBack = onBack, roundIndex = state.roundIndex, totalRounds = state.totalRounds, starsEarned = state.stars)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                HidingSpot.entries.forEach { spot ->
                    val here = spot == state.spot
                    SpotView(
                        spot = spot,
                        animal = if (here) animal else null,
                        show = when {
                            here && state.found -> 1f
                            here && peeking -> 0.35f
                            else -> 0f
                        },
                        hint = here && help.showHint && !state.found,
                        butterflyKey = if (butterflyFrom == spot) butterflyKey else 0,
                        // The barn is the big one
                        modifier = Modifier.weight(if (spot == HidingSpot.BARN) 1.35f else 1f).fillMaxHeight(0.9f)
                    ) {
                        tap(spot)
                    }
                }
            }
        }
        if (state.sessionComplete) {
            RoundCompleteSheet(starsEarned = state.stars, onContinue = onPlayAgain, onHome = onBack)
        }
      }
    }
}

/** One hiding spot, with the animal (if it's here) [show]ing: 0 hidden, about 0.35 peeking, 1 out. */
@Composable
private fun SpotView(
    spot: HidingSpot,
    animal: AnimalDef?,
    show: Float,
    hint: Boolean,
    butterflyKey: Int,
    modifier: Modifier,
    onTap: () -> Unit
) {
    val rise by animateFloatAsState(show, spring(dampingRatio = 0.45f, stiffness = 220f), label = "rise")
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(butterflyKey) {
        if (butterflyKey == 0) return@LaunchedEffect
        for (a in listOf(6f, -5f, 4f, -2f, 0f)) wobble.animateTo(a, tween(70))
    }
    BoxWithConstraints(
        modifier = modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.BottomCenter
    ) {
        // A square-ish spot, as big as fits
        val w = minOf(maxWidth, maxHeight / 1.1f)
        Box(
            modifier = Modifier
                .size(w, w * 1.1f)
                .hintGlow(hint)
                .graphicsLayer { rotationZ = wobble.value; transformOrigin = TransformOrigin(0.5f, 1f) },
            contentAlignment = Alignment.BottomCenter
        ) {
            if (spot == HidingSpot.BARN) Barn(w, animal, rise) else Cover(spot, w, animal, rise)
            if (butterflyKey != 0) Butterfly(butterflyKey)
        }
    }
}

/** The barn: the animal stands in the doorway, behind two doors that swing open. */
@Composable
private fun Barn(w: Dp, animal: AnimalDef?, open: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) { drawBarn() }
    // The doorway: the lower middle of the barn
    Box(modifier = Modifier.size(w * 0.44f, w * 0.48f), contentAlignment = Alignment.BottomCenter) {
        if (animal != null) {
            Image(
                painter = painterResource(animal.drawableRes),
                contentDescription = null,
                modifier = Modifier
                    .size(w * 0.42f)
                    .graphicsLayer {
                        val s = 0.75f + 0.35f * open
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = -size.height * 0.08f * open
                    }
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            listOf(-1f, 1f).forEach { side ->
                // Each door turns on its outer hinge, like a real barn door
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(if (side < 0) 0f else 1f, 0.5f)
                            rotationY = side * -100f * open
                            cameraDistance = 12f * density
                        }
                ) { drawDoor() }
            }
        }
    }
}

/** A bush, the haystack or a tree stump; the animal rises from behind it. */
@Composable
private fun Cover(spot: HidingSpot, w: Dp, animal: AnimalDef?, rise: Float) {
    Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.BottomCenter) {
        if (animal != null) {
            Image(
                painter = painterResource(animal.drawableRes),
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = w * 0.4f)
                    .size(w * 0.6f)
                    .graphicsLayer {
                        translationY = (1f - rise) * size.height * 0.95f
                        alpha = if (rise < 0.03f) 0f else 1f  // fully hidden: not even a foot under the hay
                    }
            )
        }
        when (spot) {
            HidingSpot.HAY -> Image(
                painter = painterResource(R.drawable.ic_food_hay),
                contentDescription = null,
                modifier = Modifier.size(w, w * 0.6f).offset(y = w * 0.04f)
            )
            else -> Canvas(modifier = Modifier.size(w, w * 0.56f)) {
                if (spot == HidingSpot.BUSH) drawBush() else drawStump()
            }
        }
    }
}

/** A butterfly flutters up out of a spot where no one was hiding. */
@Composable
private fun Butterfly(key: Int) {
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(1600, easing = FastOutSlowInEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val p = t.value
        val x = size.width * (0.5f + 0.25f * sin(p * 3 * PI).toFloat())
        val y = size.height * (0.55f - 0.65f * p)
        val flap = 0.4f + 0.6f * kotlin.math.abs(sin(p * 18 * PI).toFloat())
        val r = size.width * 0.13f
        val fade = 1f - p * p
        for (side in listOf(-1f, 1f)) {
            // Upper and lower wing on each side, folding as they flap
            val ww = r * flap
            val left = if (side < 0) x - ww else x
            drawOval(Color(0xFFFF7043).copy(alpha = fade), Offset(left, y - r), Size(ww, r))
            drawOval(Color(0xFFFFCA28).copy(alpha = fade), Offset(left + (if (side < 0) ww * 0.25f else 0f), y - r * 0.1f), Size(ww * 0.75f, r * 0.75f))
        }
        drawLine(Color(0xFF4E342E).copy(alpha = fade), Offset(x, y - r * 0.9f), Offset(x, y + r * 0.6f), strokeWidth = r * 0.16f)
    }
}

// ---------- Drawing the farm's hiding spots ----------

private val BarnRed = Color(0xFFD84343)
private val BarnDark = Color(0xFFA62F2F)
private val Trim = Color(0xFFFFF8E1)

private fun DrawScope.drawBarn() {
    val w = size.width
    val h = size.height
    val wallTop = h * 0.42f
    // Roof, then the walls, with a white trim
    val roof = Path().apply {
        moveTo(w * 0.02f, wallTop + h * 0.02f); lineTo(w * 0.5f, h * 0.06f); lineTo(w * 0.98f, wallTop + h * 0.02f); close()
    }
    drawPath(roof, BarnDark)
    drawRoundRect(BarnRed, Offset(w * 0.08f, wallTop), Size(w * 0.84f, h - wallTop), CornerRadius(w * 0.02f))
    drawPath(roof, Trim, style = Stroke(width = w * 0.025f))
    // The hayloft window
    drawRoundRect(Trim, Offset(w * 0.42f, h * 0.22f), Size(w * 0.16f, h * 0.13f), CornerRadius(w * 0.02f))
    drawRoundRect(Color(0xFF5D4037), Offset(w * 0.44f, h * 0.24f), Size(w * 0.12f, h * 0.09f), CornerRadius(w * 0.015f))
    // The dark doorway the animal stands in
    drawRect(Color(0xFF3E2723), Offset(w * 0.28f, h - w * 0.48f), Size(w * 0.44f, w * 0.48f))
}

private fun DrawScope.drawDoor() {
    val w = size.width
    val h = size.height
    drawRect(BarnRed, Offset.Zero, size)
    val t = w * 0.09f
    drawRect(Trim, Offset.Zero, size, style = Stroke(width = t))
    drawLine(Trim, Offset(0f, 0f), Offset(w, h), strokeWidth = t)
    drawLine(Trim, Offset(w, 0f), Offset(0f, h), strokeWidth = t)
}

private fun DrawScope.drawBush() {
    val w = size.width
    val h = size.height
    val dark = Color(0xFF388E3C)
    val light = Color(0xFF66BB6A)
    listOf(0.22f to 0.62f, 0.5f to 0.45f, 0.78f to 0.62f).forEach { (fx, fy) ->
        drawCircle(dark, h * 0.42f, Offset(w * fx, h * fy))
    }
    listOf(0.3f to 0.66f, 0.55f to 0.5f, 0.74f to 0.7f).forEach { (fx, fy) ->
        drawCircle(light, h * 0.26f, Offset(w * fx, h * fy))
    }
    drawRect(dark, Offset(w * 0.08f, h * 0.7f), Size(w * 0.84f, h * 0.3f))
    // A few berries
    listOf(0.32f to 0.4f, 0.62f to 0.32f, 0.7f to 0.6f).forEach { (fx, fy) ->
        drawCircle(Color(0xFFE53935), h * 0.045f, Offset(w * fx, h * fy))
    }
}

private fun DrawScope.drawStump() {
    val w = size.width
    val h = size.height
    val bark = Color(0xFF8D6E63)
    val barkDark = Color(0xFF6D4C41)
    // The trunk, with the cut top seen as an oval (its hollow is where the animal hides)
    drawRect(bark, Offset(w * 0.14f, h * 0.22f), Size(w * 0.72f, h * 0.78f))
    drawOval(barkDark, Offset(w * 0.14f, h * 0.08f), Size(w * 0.72f, h * 0.28f))
    drawOval(Color(0xFFD7A86E), Offset(w * 0.19f, h * 0.11f), Size(w * 0.62f, h * 0.22f))
    drawOval(Color(0xFF4E342E), Offset(w * 0.3f, h * 0.15f), Size(w * 0.4f, h * 0.14f))
    // Bark lines and two little roots
    for (i in 0 until 4) {
        val x = w * (0.24f + i * 0.17f)
        drawLine(barkDark, Offset(x, h * 0.36f), Offset(x + w * 0.02f, h * 0.95f), strokeWidth = w * 0.02f)
    }
    drawOval(bark, Offset(w * 0.02f, h * 0.84f), Size(w * 0.24f, h * 0.16f))
    drawOval(bark, Offset(w * 0.74f, h * 0.84f), Size(w * 0.24f, h * 0.16f))
}
