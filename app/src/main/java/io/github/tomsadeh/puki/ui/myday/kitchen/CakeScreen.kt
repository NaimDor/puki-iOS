package io.github.tomsadeh.puki.ui.myday.kitchen

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.components.DragDropState
import io.github.tomsadeh.puki.ui.components.DragTarget
import io.github.tomsadeh.puki.ui.components.DropZone
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.LocalDragDropState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.rememberStepGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** The cake's steps, one star each. */
private enum class CakeStep { ADD, STIR, BAKE, DECORATE }

private val ingredients = listOf("flour", "sugar", "eggs", "milk")
/** What each ingredient is called out loud. */
private fun ingredientVoice(id: String) = when (id) {
    "flour" -> "voice_cook_flour"
    "sugar" -> "voice_cook_sugar"
    "eggs" -> "voice_cook_eggs"
    else -> "voice_cook_milk"
}
private const val STIR_TURNS = 3f  // about three times round the bowl
private fun toppingsToFinish(level: Int) = when (level) {
    1 -> 3
    2 -> 4
    else -> 6
}

/** Keeps the session's bookkeeping alive past the screen's own coroutines. */
class CakeViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("kitchen", (application as PukiApplication).progressRepository, viewModelScope)
}

/**
 * Baking a cake: put the ingredients in the bowl, stir round and round, into the oven, and
 * decorate it. Nothing can go wrong; every step ends with a star.
 */
@Composable
fun CakeScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,
    viewModel: CakeViewModel = viewModel()
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val dragDropState = remember { DragDropState() }

    val level by produceState(0) { value = viewModel.session.level() }
    if (level == 0) return

    val game = rememberStepGame(CakeStep.ADD, CakeStep.entries.size, viewModel.session)
    val step = game.step
    val celebrate = game.celebrate
    var finished by remember { mutableStateOf(false) }
    val added = remember { mutableStateListOf<String>() }
    var stirred by remember { mutableFloatStateOf(0f) }  // radians stirred so far
    var spoonAngle by remember { mutableStateOf<Float?>(null) }
    var baking by remember { mutableStateOf(false) }
    val bake = remember { Animatable(0f) }
    val toppings = remember { mutableStateListOf<Topping>() }

    val help = rememberHelp(step, active = !celebrate && !baking && !finished, onAssisted = viewModel.session::slip) {
        voicePlayer.play(
            when (step) {
                CakeStep.ADD -> "voice_cook_add"
                CakeStep.STIR -> "voice_cook_stir"
                CakeStep.BAKE -> "voice_cook_oven"
                CakeStep.DECORATE -> "voice_cook_decorate"
            },
            language
        )
    }

    when (step) {
        CakeStep.ADD -> RoundPrompt(0, "במטבח: אופים עוגה, שמים בקערה קמח, סוכר, ביצים וחלב.", "In the kitchen: baking a cake, putting flour, sugar, eggs and milk in the bowl.", "voice_cook_lets_bake")
        CakeStep.STIR -> RoundPrompt(1, "במטבח: מערבבים את הבצק לעוגה.", "In the kitchen: stirring the cake batter.", "voice_cook_stir")
        CakeStep.BAKE -> RoundPrompt(2, "במטבח: מכניסים את העוגה לתנור.", "In the kitchen: putting the cake in the oven.", "voice_cook_oven")
        CakeStep.DECORATE -> RoundPrompt(3, "במטבח: מקשטים את העוגה בתותים ובנרות.", "In the kitchen: decorating the cake with strawberries and candles.", "voice_cook_decorate")
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        // At the end Puki leaves his corner and comes to taste the cake
        GameBackground(withCompanion = !finished) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawKitchen() }
            Column(
                modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(onBack = onBack, roundIndex = step.ordinal, totalRounds = CakeStep.entries.size, starsEarned = game.stars)
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val unit = minOf(maxWidth, maxHeight * 1.4f)  // things scale with the room there is
                    when (step) {
                        CakeStep.ADD -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                            DropZone(zoneId = "bowl", highlightColor = Color(0xFFFFCA28)) {
                                Canvas(modifier = Modifier.size(unit * 0.42f, unit * 0.33f)) {
                                    drawBowl(fill = added.size / 4f, smooth = 0f, swirl = 0f, spoonAngle = null)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                ingredients.forEachIndexed { i, id ->
                                    val inBowl = id in added
                                    Box(modifier = Modifier.size(unit * 0.18f)) {
                                        if (!inBowl) DragTarget(
                                            itemId = id,
                                            tapToZone = "bowl",
                                            onDragStart = { voicePlayer.play(ingredientVoice(id), language) },
                                            onDragEnd = { zone ->
                                                if (zone == "bowl" && id !in added) {
                                                    added.add(id)
                                                    sfxPlayer.play(Sfx.DROP)
                                                    help.touched()
                                                    if (added.size == ingredients.size) game.done(CakeStep.STIR)
                                                }
                                            }
                                        ) {
                                            Canvas(
                                                modifier = Modifier
                                                    .size(unit * 0.18f)
                                                    .hintGlow(help.showHint && id == ingredients.first { it !in added })
                                                    .idleBob(i)
                                            ) {
                                                when (id) {
                                                    "flour" -> drawFlour()
                                                    "sugar" -> drawSugar()
                                                    "eggs" -> drawEggs()
                                                    else -> drawMilk()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        CakeStep.STIR -> {
                            val smooth = (stirred / (STIR_TURNS * 2 * PI.toFloat())).coerceIn(0f, 1f)
                            val guide by rememberInfiniteTransition(label = "stir_guide").animateFloat(
                                0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "stir_phase"
                            )
                            Box(contentAlignment = Alignment.Center) {
                                if (smooth < 1f) Canvas(modifier = Modifier.size(unit * 0.66f, unit * 0.48f).graphicsLayer()) { drawStirGuide(guide) }  // own layer: it moves, the kitchen doesn't
                                Canvas(
                                    modifier = Modifier
                                        .size(unit * 0.5f, unit * 0.4f)
                                        .pointerInput(Unit) {
                                            var last: Float? = null
                                            detectDragGestures(
                                                onDragStart = { last = null },
                                                onDragEnd = { spoonAngle = null },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val c = Offset(size.width / 2f, size.height * BOWL_RIM)
                                                    val p = change.position - c
                                                    val a = atan2(p.y / 0.5f, p.x)  // the bowl is seen from above, squashed
                                                    last?.let { prev ->
                                                        var d = a - prev
                                                        if (d > PI) d -= (2 * PI).toFloat()
                                                        if (d < -PI) d += (2 * PI).toFloat()
                                                        val before = stirred
                                                        stirred += abs(d)
                                                        val full = STIR_TURNS * 2 * PI.toFloat()
                                                        // A soft sound each half turn, and done after three turns
                                                        if ((stirred / PI.toFloat()).toInt() > (before / PI.toFloat()).toInt() && stirred < full) sfxPlayer.play(Sfx.WHOOSH, 0.35f)
                                                        if (before < full && stirred >= full) game.done(CakeStep.BAKE)
                                                    }
                                                    last = a
                                                    spoonAngle = a
                                                }
                                            )
                                        }
                                ) { drawBowl(fill = 1f, smooth = smooth, swirl = stirred, spoonAngle = spoonAngle ?: 0.6f) }
                            }
                        }

                        CakeStep.BAKE -> Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(unit * 0.28f), contentAlignment = Alignment.Center) {
                                if (!baking) DragTarget(
                                    itemId = "pan",
                                    tapToZone = "oven",
                                    onDragEnd = { zone ->
                                        if (zone == "oven" && !baking) {
                                            baking = true
                                            sfxPlayer.play(Sfx.WHOOSH)
                                            scope.launch {
                                                bake.animateTo(1f, tween(3200, easing = LinearEasing))
                                                sfxPlayer.play(Sfx.STAR_COLLECT)
                                                voicePlayer.play("voice_cook_ready", language)
                                                delay(600)
                                                game.done(CakeStep.DECORATE)
                                            }
                                        }
                                    }
                                ) {
                                    Canvas(modifier = Modifier.size(unit * 0.28f).hintGlow(help.showHint).idleBob(1)) { drawPan() }
                                }
                            }
                            DropZone(zoneId = "oven", highlightColor = Color(0xFFFF9800)) {
                                val shake by rememberInfiniteTransition(label = "oven_shake").animateFloat(
                                    -1f, 1f, infiniteRepeatable(tween(120), RepeatMode.Reverse), label = "oven_wobble"
                                )
                                Canvas(
                                    modifier = Modifier
                                        .size(unit * 0.32f, unit * 0.34f)
                                        .graphicsLayer { rotationZ = if (baking && bake.value < 1f) shake * 0.8f else 0f }
                                ) { drawOven(baking = bake.value, open = !baking) }
                            }
                        }

                        CakeStep.DECORATE -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                            val flame by rememberInfiniteTransition(label = "flame").animateFloat(
                                0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "flame_phase"
                            )
                            Box {
                                DropZone(zoneId = "cake", highlightColor = Color(0xFFF48FB1)) {
                                    Box(modifier = Modifier.size(unit * 0.46f, unit * 0.34f).popIn()) {
                                        Canvas(modifier = Modifier.fillMaxSize()) { drawCake() }
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            toppings.forEachIndexed { i, t ->
                                                val spot = toppingSpot(i)
                                                val s = maxWidth * 0.16f
                                                Canvas(
                                                    modifier = Modifier
                                                        .offset(maxWidth * spot.x - s / 2, maxHeight * spot.y - s * 0.85f)
                                                        .size(s)
                                                        .popIn(key = i)
                                                ) { drawTopping(t, flame) }
                                            }
                                        }
                                    }
                                }
                                // Puki comes to taste it
                                if (finished) {
                                    val hop by animateFloatAsState(if (finished) 1f else 0f, tween(500), label = "puki_hop")
                                    Image(
                                        painter = painterResource(R.drawable.ic_companion_parrot),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .offset(x = unit * 0.16f)
                                            .size(unit * 0.18f)
                                            .graphicsLayer { scaleX = hop; scaleY = hop }
                                            .idleBob(3, 2f)
                                    )
                                }
                            }
                            if (!finished) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                Topping.entries.forEachIndexed { i, t ->
                                    DragTarget(
                                        itemId = t.name,
                                        tapToZone = "cake",
                                        onDragEnd = { zone ->
                                            if (zone == "cake" && !finished) {
                                                toppings.add(t)
                                                sfxPlayer.play(Sfx.POP)
                                                help.touched()
                                                if (toppings.size == toppingsToFinish(level)) {
                                                    finished = true
                                                    voicePlayer.play("voice_cook_yum", language) {
                                                        companion?.react("במטבח: אפו עוגה וקישטו אותה, ופוקי טועם!", "In the kitchen: the child baked a cake and decorated it, and Puki tastes it!") { }
                                                    }
                                                    game.done(null)
                                                    scope.launch {
                                                        delay(if (companion?.isReady == true) 7000 else 5500)  // time to admire it
                                                        game.finish()
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Canvas(modifier = Modifier.size(unit * 0.13f).idleBob(i)) { drawTopping(t, flame) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ConfettiOverlay(show = celebrate)

            if (game.sessionComplete) {
                RoundCompleteSheet(starsEarned = game.stars, onContinue = onPlayAgain, onHome = onBack)
            }
        }
    }

    // Wake Puki's brain, so he can taste the cake at the end
}
