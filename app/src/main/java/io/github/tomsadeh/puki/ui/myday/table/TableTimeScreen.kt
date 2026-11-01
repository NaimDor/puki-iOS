package io.github.tomsadeh.puki.ui.myday.table

import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.idleBob
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.GameTopBar
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import io.github.tomsadeh.puki.ui.myday.PukiCorner
import io.github.tomsadeh.puki.ui.components.rememberCompact
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import io.github.tomsadeh.puki.ui.common.GameSession
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
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.DragDropState
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.DragTarget
import io.github.tomsadeh.puki.ui.components.DropZone
import io.github.tomsadeh.puki.ui.components.LocalDragDropState
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PlaceSetting(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val placed: Boolean = false)

/** Who's coming to eat today. */
enum class Guest(@DrawableRes val icon: Int) {
    TEDDY(R.drawable.ic_tidy_teddy),
    PUKI(R.drawable.ic_companion_parrot),
    CAT(R.drawable.ic_animal_cat),
    RABBIT(R.drawable.ic_animal_rabbit),
}

/** What to lay: sometimes a bowl instead of the plate. */
private fun todaysSetting() = listOf(
    if (Random.nextInt(3) == 0) PlaceSetting("bowl", BilingualText("קערה", "Bowl"), R.drawable.ic_table_bowl)
    else PlaceSetting("plate", BilingualText("צלחת", "Plate"), R.drawable.ic_table_plate),
    PlaceSetting("cup", BilingualText("כוס", "Cup"), R.drawable.ic_table_cup),
    PlaceSetting("fork", BilingualText("מזלג", "Fork"), R.drawable.ic_table_fork),
    PlaceSetting("spoon", BilingualText("כפית", "Spoon"), R.drawable.ic_table_spoon)
)

data class TableState(
    val items: List<PlaceSetting> = todaysSetting(),
    val guest: Guest = Guest.entries.random(),
    val isComplete: Boolean = false
) {
    val hasBowl get() = items.any { it.id == "bowl" }
}

class TableTimeViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("table", (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state.asStateFlow()

    fun onItemPlaced(itemId: String, targetId: String): Boolean {
        if (itemId != targetId) {
            session.slip()
            return false
        }
        val updated = _state.value.items.map { if (it.id == itemId) it.copy(placed = true) else it }
        val complete = updated.all { it.placed }
        _state.value = _state.value.copy(items = updated, isComplete = complete)
        session.star()
        if (complete) session.finish(updated.size)
        return true
    }
}

@Composable
fun TableTimeScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: TableTimeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = LocalCompanion.current
    when (state.guest) {
        Guest.TEDDY -> RoundPrompt(0, "בעריכת השולחן: עורכים את השולחן לדובי, הוא כבר יושב ומחכה.", "Setting the table for Teddy, who's sitting there waiting.", "voice_prompt_table_teddy")
        Guest.PUKI -> RoundPrompt(0, "בעריכת השולחן: עורכים את השולחן בשבילך, אתה כבר יושב ומחכה.", "Setting the table for you: you're sitting there waiting.", "voice_prompt_table_puki")
        Guest.CAT -> RoundPrompt(0, "בעריכת השולחן: עורכים את השולחן לחתול, הוא כבר יושב ומחכה.", "Setting the table for the cat, who's sitting there waiting.", "voice_prompt_table_cat")
        Guest.RABBIT -> RoundPrompt(0, "בעריכת השולחן: עורכים את השולחן לארנב, הוא כבר יושב ומחכה.", "Setting the table for the rabbit, who's sitting there waiting.", "voice_prompt_table_rabbit")
    }
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val dragDropState = remember { DragDropState() }
    val scope = rememberCoroutineScope()
    val done = state.items.count { it.placed }
    val next = state.items.firstOrNull { !it.placed }
    val itemReactions = remember { state.items.associate { it.id to ReactionState() } }
    val guestReaction = remember { ReactionState() }
    var showCard by remember { mutableStateOf(false) }
    val help = rememberHelp(done, active = !state.isComplete) { voicePlayer.play("voice_prompt_set_table", language) }

    // All set: dinner's served, the guest hops for joy, and then the end card
    LaunchedEffect(state.isComplete) {
        if (!state.isComplete) return@LaunchedEffect
        voicePlayer.play("voice_table_done", language) {
            if (state.hasBowl) companion?.react("ערכו את כל השולחן: קערה, כוס, מזלג וכף!", "The child set the whole table: bowl, cup, fork and spoon!")
            else companion?.react("ערכו את כל השולחן: צלחת, כוס, מזלג וכף!", "The child set the whole table: plate, cup, fork and spoon!")
        }
        launch { guestReaction.celebrate() }
        delay(3400)
        showCard = true
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(
            scene = Scene.HOME
        ) {
            Column(
                modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(onBack = onBack, roundIndex = done, totalRounds = state.items.size, starsEarned = done)

                // The table seen from above, with the basket of things to set beside it
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val fit = minOf(1f, maxHeight / 360.dp)  // the basket is drawn for this much height
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = PukiCorner),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableScene(
                        items = state.items,
                        guest = state.guest,
                        guestReaction = guestReaction,
                        served = state.isComplete,
                        hintId = if (help.showHint) next?.id else null,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Basket(fit = fit, modifier = Modifier.width(300.dp * fit).fillMaxHeight()) {
                        state.items.forEachIndexed { i, item ->
                            if (!item.placed) {
                                val (dx, dy, tilt) = BASKET_SPOTS[i]
                                DragTarget(
                                    itemId = item.id,
                                    modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(dx.dp * fit, dy.dp * fit),
                                    tapToZone = item.id,
                                    onDragEnd = { dropId ->
                                        if (dropId != null) {
                                            val correct = viewModel.onItemPlaced(item.id, dropId)
                                            if (correct) {
                                                sfxPlayer.play(Sfx.DROP)
                                                voicePlayer.play("voice_table_${item.id}", language)
                                                if (done + 1 == 2) companion?.react("בעריכת השולחן: כבר חצי מהשולחן ערוך.", "Setting the table: half of it is set already.")
                                            } else {
                                                sfxPlayer.play(Sfx.WRONG_GENTLE)
                                                voicePlayer.playSequence(listOf("voice_table_${item.id}", "voice_prompt_try_again"), language)
                                                help.missed()
                                                scope.launch { itemReactions[item.id]?.shake() }
                                            }
                                        }
                                    }
                                ) {
                                    Image(
                                        painter = painterResource(item.icon),
                                        contentDescription = item.name.resolve(language),
                                        modifier = Modifier
                                            .reaction(itemReactions.getValue(item.id))
                                            .hintGlow(help.showHint && item.id == next?.id)
                                            .idleBob(i, 0.5f)
                                            .size(118.dp * fit)
                                            .graphicsLayer { rotationZ = tilt }
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            ConfettiOverlay(show = state.isComplete)
            if (showCard) {
                RoundCompleteSheet(starsEarned = done, onContinue = onPlayAgain, onHome = onBack)
            }
        }
    }
}


/** Where each thing goes on the table (fractions of the table), and how big its place is. */
private data class Place(val id: String, val x: Float, val y: Float, val w: Float, val h: Float)

private val PLACES = listOf(
    Place("plate", 0.50f, 0.56f, 0.34f, 0.34f),
    Place("bowl", 0.50f, 0.56f, 0.34f, 0.34f),
    Place("fork", 0.24f, 0.56f, 0.13f, 0.34f),
    Place("spoon", 0.76f, 0.56f, 0.13f, 0.34f),
    Place("cup", 0.74f, 0.20f, 0.16f, 0.16f),
)

/** The things in the basket: offset in dp and a little tilt, so they look tumbled in. */
private val BASKET_SPOTS = listOf(Triple(40f, 70f, -12f), Triple(150f, 40f, 10f), Triple(30f, 200f, 18f), Triple(150f, 190f, -8f))

/** A wooden table seen from above, with a placemat and a faint outline where each thing goes. */
@Composable
private fun TableScene(items: List<PlaceSetting>, guest: Guest, guestReaction: ReactionState, served: Boolean, hintId: String?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Room above the table for the guest's head; on a short screen the table gives up less
        val above = maxHeight * (if (rememberCompact()) 0.17f else 0.2f)
        val w = minOf(maxWidth, (maxHeight - above) * 1.35f)
        val h = w / 1.35f
        val guestSize = above / 0.6f  // the head is the top part of the picture
        // The guest sits on the far side, so the table hides the rest of them
        Image(
            painter = painterResource(guest.icon),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .absoluteOffset(y = (h + above) / -2 + guestSize / 2)
                .reaction(guestReaction, jumpHeight = 14.dp)
                .idleBob(5, 0.5f)
                .size(guestSize)
        )
        Box(modifier = Modifier.align(Alignment.Center).absoluteOffset(y = above / 2).size(w, h)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Table top with wood grain
                drawRoundRect(Color(0xFFB07A45), cornerRadius = CornerRadius(size.height * 0.08f))
                drawRoundRect(Color(0xFFD4A26A), Offset(size.width * 0.015f, size.height * 0.02f),
                    Size(size.width * 0.97f, size.height * 0.96f), CornerRadius(size.height * 0.07f))
                for (k in 1..7) {
                    val y = size.height * k / 8f
                    drawLine(Color(0xFFB07A45).copy(alpha = 0.45f), Offset(size.width * 0.03f, y),
                        Offset(size.width * 0.97f, y + size.height * 0.01f * (k % 3 - 1)), strokeWidth = 3f)
                }
                // Placemat
                drawOval(Color(0xFFFFF3E0), Offset(size.width * 0.12f, size.height * 0.22f), Size(size.width * 0.76f, size.height * 0.68f))
                drawOval(Color(0xFFFFCC80), Offset(size.width * 0.12f, size.height * 0.22f), Size(size.width * 0.76f, size.height * 0.68f),
                    style = Stroke(width = size.height * 0.012f))
            }
            PLACES.forEach { place ->
                val item = items.firstOrNull { it.id == place.id } ?: return@forEach
                DropZone(
                    zoneId = place.id,
                    // From the table's top-left in both languages: the table doesn't mirror
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(w * (place.x - place.w / 2), h * (place.y - place.h / 2 * 1.35f))
                        .size(w * place.w, h * place.h * 1.35f)
                        .hintGlow(hintId == place.id)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (item.placed) {
                            Image(painter = painterResource(item.icon), contentDescription = null,
                                modifier = Modifier.popIn().fillMaxSize(0.95f))
                            if (served && place.id == "plate") Dinner(Modifier.popIn().fillMaxSize(), inBowl = false)
                            if (served && place.id == "bowl") Dinner(Modifier.popIn().fillMaxSize(), inBowl = true)
                        } else {
                            // A faint outline of what goes here
                            Image(painter = painterResource(item.icon), contentDescription = null,
                                colorFilter = ColorFilter.tint(Color(0xFF5D4037)),
                                modifier = Modifier.fillMaxSize(0.95f).alpha(0.16f))
                        }
                    }
                }
            }
        }
    }
}

/** Dinner on the plate, or lying flat inside the bowl, with steam going up. */
@Composable
private fun Dinner(modifier: Modifier, inBowl: Boolean) {
    val rise by rememberInfiniteTransition(label = "steam").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "steam_rise"
    )
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2, size.height * (if (inBowl) 0.44f else 0.5f))
        val r = size.minDimension * (if (inBowl) 0.32f else 0.27f)
        // Noodles in a heap, with peas and a slice of carrot
        scale(1f, if (inBowl) 0.36f else 1f, pivot = c) {
            drawCircle(Color(0xFFFFD54F), r, c)
            for (k in 0 until 5) {
                drawArc(Color(0xFFFFB300), 20f + k * 70f, 170f, false, c - Offset(r * (0.8f - k * 0.1f), r * (0.8f - k * 0.1f)),
                    Size(r * (1.6f - k * 0.2f), r * (1.6f - k * 0.2f)), style = Stroke(r * 0.08f, cap = StrokeCap.Round))
            }
            for ((dx, dy) in listOf(-0.4f to -0.2f, 0.3f to -0.45f, 0.45f to 0.25f, -0.15f to 0.5f)) drawCircle(Color(0xFF66BB6A), r * 0.13f, c + Offset(r * dx, r * dy))
            drawCircle(Color(0xFFFF7043), r * 0.2f, c + Offset(r * 0.05f, r * 0.02f))
        }
        for (k in 0..2) {
            val t = (rise + k / 3f) % 1f
            val x = c.x + (k - 1) * r * 0.7f
            val y = c.y - r * (1f + 1.3f * t)
            val path = Path().apply {
                moveTo(x, y + r * 0.45f)
                cubicTo(x - r * 0.2f, y + r * 0.3f, x + r * 0.2f, y + r * 0.15f, x, y)
            }
            drawPath(path, Color(0xFF90A4AE).copy(alpha = 0.8f * (1f - t)), style = Stroke(r * 0.09f, cap = StrokeCap.Round))
        }
    }
}

/** A woven basket the things are tumbled into, [fit] times its full size. */
@Composable
private fun Basket(fit: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(300.dp * fit, 360.dp * fit)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val top = size.height * 0.18f
                // Handle
                drawArc(Color(0xFFA1887F), 180f, 180f, false, Offset(size.width * 0.18f, top - size.height * 0.14f),
                    Size(size.width * 0.64f, size.height * 0.34f), style = Stroke(width = size.width * 0.04f))
                // Body, with a woven pattern
                val body = Path().apply {
                    moveTo(size.width * 0.04f, top + size.height * 0.04f)
                    lineTo(size.width * 0.96f, top + size.height * 0.04f)
                    lineTo(size.width * 0.86f, size.height * 0.98f)
                    lineTo(size.width * 0.14f, size.height * 0.98f)
                    close()
                }
                drawPath(body, Color(0xFFD7A86E))
                for (k in 1..6) {
                    val y = top + size.height * 0.04f + (size.height * 0.94f - top) * k / 7f
                    drawLine(Color(0xFFB0834F), Offset(size.width * 0.06f, y), Offset(size.width * 0.94f, y), strokeWidth = 4f)
                }
                for (k in 1..7) {
                    val x = size.width * k / 8f
                    drawLine(Color(0xFFB0834F).copy(alpha = 0.6f), Offset(x, top + size.height * 0.05f),
                        Offset(size.width * 0.5f + (x - size.width * 0.5f) * 0.8f, size.height * 0.97f), strokeWidth = 3f)
                }
                // Rim
                drawRoundRect(Color(0xFFA1887F), Offset(0f, top), Size(size.width, size.height * 0.06f), CornerRadius(size.height * 0.03f))
            }
            content()
        }
    }
}
