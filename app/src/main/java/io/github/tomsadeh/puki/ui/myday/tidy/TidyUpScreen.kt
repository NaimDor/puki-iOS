package io.github.tomsadeh.puki.ui.myday.tidy

import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.idleBob
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.BoxWithConstraints
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.GameTopBar
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

data class TidyItem(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val homeId: String, val tidied: Boolean = false)
data class TidyHome(val id: String, val name: BilingualText, @DrawableRes val icon: Int)

/** Everything that can end up on the floor, each with its home in the room. */
private val ALL_TOYS = listOf(
    TidyItem("teddy", BilingualText("דובי", "Teddy"), R.drawable.ic_tidy_teddy, "shelf"),
    TidyItem("bunny", BilingualText("ארנב", "Bunny"), R.drawable.ic_tidy_bunny, "shelf"),
    TidyItem("book", BilingualText("ספר", "Book"), R.drawable.ic_tidy_book, "bookcase"),
    TidyItem("storybook", BilingualText("ספר סיפורים", "Story book"), R.drawable.ic_tidy_storybook, "bookcase"),
    TidyItem("shirt", BilingualText("חולצה", "Shirt"), R.drawable.ic_clothing_shirt, "hamper"),
    TidyItem("socks", BilingualText("גרביים", "Socks"), R.drawable.ic_clothing_socks, "hamper"),
    TidyItem("pants", BilingualText("מכנסיים", "Pants"), R.drawable.ic_clothing_pants, "hamper"),
    TidyItem("ball", BilingualText("כדור", "Ball"), R.drawable.ic_tidy_ball, "toybox"),
    TidyItem("car", BilingualText("מכונית", "Car"), R.drawable.ic_tidy_car, "toybox"),
    TidyItem("blocks", BilingualText("קוביות", "Blocks"), R.drawable.ic_tidy_blocks, "toybox"),
)

/** Today's mess: 3 or 4 toys, each with a home of its own, so each home shows what came back to it. */
private fun todaysToys() = ALL_TOYS.groupBy { it.homeId }.values.shuffled().take(Random.nextInt(3, 5)).map { it.random() }

data class TidyState(
    val items: List<TidyItem> = todaysToys(),
    val homes: List<TidyHome> = listOf(
        TidyHome("shelf", BilingualText("מדף", "Shelf"), R.drawable.ic_tidy_shelf),
        TidyHome("bookcase", BilingualText("ארון ספרים", "Bookcase"), R.drawable.ic_tidy_bookcase),
        TidyHome("hamper", BilingualText("סל כביסה", "Hamper"), R.drawable.ic_tidy_hamper),
        TidyHome("toybox", BilingualText("ארגז צעצועים", "Toy Box"), R.drawable.ic_tidy_toybox)
    ),
    val isComplete: Boolean = false
)

class TidyUpViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("tidy", (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(TidyState())
    val state: StateFlow<TidyState> = _state.asStateFlow()

    fun onItemDropped(itemId: String, homeId: String): Boolean {
        val item = _state.value.items.find { it.id == itemId } ?: return false
        if (item.tidied) return false
        if (item.homeId != homeId) {
            session.slip()
            return false
        }
        val updated = _state.value.items.map { if (it.id == itemId) it.copy(tidied = true) else it }
        val complete = updated.all { it.tidied }
        _state.value = _state.value.copy(items = updated, isComplete = complete)
        session.star()
        if (complete) session.finish(updated.size)
        return true
    }
}

@Composable
fun TidyUpScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: TidyUpViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = LocalCompanion.current
    RoundPrompt(0, "בסידור החדר: מחזירים כל צעצוע למקום שלו.", "Tidying up: putting every toy back where it belongs.", "voice_prompt_tidy_up")
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val dragDropState = remember { DragDropState() }
    val scope = rememberCoroutineScope()
    val done = state.items.count { it.tidied }
    val next = state.items.firstOrNull { !it.tidied }
    val homeReactions = remember { state.homes.associate { it.id to ReactionState() } }
    val itemReactions = remember { state.items.associate { it.id to ReactionState() } }
    var showCard by remember { mutableStateOf(false) }
    val help = rememberHelp(done, active = !state.isComplete) { voicePlayer.play("voice_prompt_tidy_up", language) }
    val floorSpots = remember { FLOOR_SPOTS.shuffled() }  // the toys lie somewhere else every time

    // All put away: every home hops, and then the end card
    LaunchedEffect(state.isComplete) {
        if (!state.isComplete) return@LaunchedEffect
        voicePlayer.play("voice_tidy_done", language) {
            companion?.react("סידרו את כל החדר, כל צעצוע במקום!", "The child tidied the whole room, every toy in its place!")
        }
        homeReactions.values.forEachIndexed { i, r -> launch { delay(i * 120L); r.celebrate() } }
        delay(3200)
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

                // The room: homes where they belong, toys scattered on the floor
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val w = maxWidth
                    val h = maxHeight
                    val toySize = minOf(130.dp, h * 0.27f)  // (the floor is short on a phone)
                    state.homes.forEach { home ->
                        val spot = HOME_SPOTS.getValue(home.id)
                        val inside = state.items.firstOrNull { it.homeId == home.id && it.tidied }
                        DropZone(
                            zoneId = home.id,
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset(w * spot.x, h * spot.y)
                                .size(w * spot.w, h * spot.h)
                                .hintGlow(help.showHint && next?.homeId == home.id)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().reaction(homeReactions.getValue(home.id), jumpHeight = 14.dp)) {
                                Image(painter = painterResource(home.icon), contentDescription = home.name.resolve(language),
                                    alignment = Alignment.BottomCenter,  // furniture stands on the floor
                                    modifier = Modifier.fillMaxSize())
                                if (inside != null) {
                                    // Put away: it shows in its home
                                    Image(
                                        painter = painterResource(inside.icon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(AbsoluteAlignment.TopLeft)
                                            .absoluteOffset(w * spot.w * spot.inX, h * spot.h * spot.inY)
                                            .popIn(key = inside.id)
                                            .size(w * spot.w * spot.inSize)
                                    )
                                }
                            }
                        }
                    }
                    state.items.forEachIndexed { i, item ->
                        if (!item.tidied) {
                            val spot = floorSpots[i]
                            DragTarget(
                                itemId = item.id,
                                modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * spot.first, h * spot.second),
                                tapToZone = item.homeId,
                                onDragEnd = { dropId ->
                                    if (dropId != null) {
                                        val correct = viewModel.onItemDropped(item.id, dropId)
                                        if (correct) {
                                            sfxPlayer.play(Sfx.DROP)
                                            voicePlayer.play("voice_tidy_${item.id}", language)
                                            scope.launch { homeReactions[item.homeId]?.celebrate() }
                                            if (done + 1 == 2 && state.items.size == 4) companion?.react("בסידור החדר: חצי מהצעצועים כבר במקום.", "Tidying up: half the toys are back in their places.")
                                        } else {
                                            sfxPlayer.play(Sfx.WRONG_GENTLE)
                                            voicePlayer.playSequence(listOf("voice_tidy_${item.id}", "voice_prompt_try_again"), language)
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
                                        .size(toySize)
                                        .graphicsLayer { rotationZ = spot.third }
                                )
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


/** Where a home stands in the room (fractions of the play area), its size, and where a
 *  put-away item shows inside it (fractions of the home). */
private data class HomeSpot(val x: Float, val y: Float, val w: Float, val h: Float,
                            val inX: Float, val inY: Float, val inSize: Float)

private val HOME_SPOTS = mapOf(
    "bookcase" to HomeSpot(0.03f, 0.06f, 0.20f, 0.68f, 0.30f, 0.34f, 0.40f),  // standing on the floor
    "shelf" to HomeSpot(0.33f, 0.02f, 0.26f, 0.30f, 0.32f, -0.30f, 0.36f),
    "toybox" to HomeSpot(0.56f, 0.42f, 0.18f, 0.36f, 0.25f, 0.20f, 0.50f),
    "hamper" to HomeSpot(0.79f, 0.36f, 0.17f, 0.42f, 0.22f, 0.30f, 0.55f),
)

/** Where a toy can lie on the floor (fractions of the play area) and how it's tilted. */
private val FLOOR_SPOTS = listOf(
    Triple(0.29f, 0.66f, -14f),
    Triple(0.66f, 0.74f, 22f),   // by the toy box
    Triple(0.46f, 0.72f, 9f),
    Triple(0.16f, 0.72f, 0f),    // in front of the bookcase (and clear of Puki's corners)
)
