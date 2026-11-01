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

data class TidyItem(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val homeId: String, val tidied: Boolean = false)
data class TidyHome(val id: String, val name: BilingualText, @DrawableRes val icon: Int)

data class TidyState(
    val items: List<TidyItem> = listOf(
        TidyItem("teddy", BilingualText("דובי", "Teddy"), R.drawable.ic_tidy_teddy, "shelf"),
        TidyItem("book", BilingualText("ספר", "Book"), R.drawable.ic_tidy_book, "bookcase"),
        TidyItem("shirt", BilingualText("חולצה", "Shirt"), R.drawable.ic_clothing_shirt, "hamper"),
        TidyItem("ball", BilingualText("כדור", "Ball"), R.drawable.ic_tidy_ball, "toybox")
    ),
    val homes: List<TidyHome> = listOf(
        TidyHome("shelf", BilingualText("מדף", "Shelf"), R.drawable.ic_tidy_shelf),
        TidyHome("bookcase", BilingualText("ארון ספרים", "Bookcase"), R.drawable.ic_tidy_bookcase),
        TidyHome("hamper", BilingualText("סל כביסה", "Hamper"), R.drawable.ic_tidy_hamper),
        TidyHome("toybox", BilingualText("ארגז צעצועים", "Toy Box"), R.drawable.ic_tidy_toybox)
    ),
    val isComplete: Boolean = false
)

class TidyUpViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as PukiApplication).progressRepository
    private val _state = MutableStateFlow(TidyState())
    val state: StateFlow<TidyState> = _state.asStateFlow()

    fun onItemDropped(itemId: String, homeId: String): Boolean {
        val item = _state.value.items.find { it.id == itemId } ?: return false
        if (item.homeId != homeId) return false
        val updated = _state.value.items.map { if (it.id == itemId) it.copy(tidied = true) else it }
        val complete = updated.all { it.tidied }
        _state.value = _state.value.copy(items = updated, isComplete = complete)
        if (complete) viewModelScope.launch { progressRepository.addStars(1) }
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
    // Puki knows where she is (tapping him talks about this routine)
    LaunchedEffect(Unit) {
        companion?.setScene("בסידור החדר: מחזירים כל צעצוע למקום שלו.", "Tidying up: putting every toy back where it belongs.")
    }
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) companion?.react("סידרו את כל החדר, כל צעצוע במקום!", "The child tidied the whole room, every toy in its place!")
    }
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val dragDropState = remember { DragDropState() }

    LaunchedEffect(Unit) {
        delay(500)
        voicePlayer.play("voice_prompt_tidy_up", language)
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(
            scene = Scene.HOME,
            colors = listOf(Color(0xFFFFE0B2), Color(0xFFFFCC80), Color(0xFFFFB74D))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(onBack = onBack, roundIndex = state.items.count { it.tidied }, totalRounds = state.items.size)

                Spacer(modifier = Modifier.height(16.dp))

                // The room: homes where they belong, toys scattered on the floor
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val w = maxWidth
                    val h = maxHeight
                    state.homes.forEach { home ->
                        val spot = HOME_SPOTS.getValue(home.id)
                        val inside = state.items.firstOrNull { it.homeId == home.id && it.tidied }
                        DropZone(
                            zoneId = home.id,
                            modifier = Modifier
                                .align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset(w * spot.x, h * spot.y)
                                .size(w * spot.w, h * spot.h)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
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
                            val spot = FLOOR_SPOTS[i]
                            DragTarget(
                                itemId = item.id,
                                modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(w * spot.first, h * spot.second),
                                onDragEnd = { dropId ->
                                    if (dropId != null) {
                                        val correct = viewModel.onItemDropped(item.id, dropId)
                                        if (correct) {
                                            sfxPlayer.play(Sfx.DROP)
                                            voicePlayer.play("voice_tidy_${item.id}", language)
                                        } else {
                                            sfxPlayer.play(Sfx.WRONG_GENTLE)
                                        }
                                    }
                                }
                            ) {
                                Image(
                                    painter = painterResource(item.icon),
                                    contentDescription = item.name.resolve(language),
                                    modifier = Modifier
                                        .idleBob(i, 0.5f)
                                        .size(130.dp)
                                        .graphicsLayer { rotationZ = spot.third }
                                )
                            }
                        }
                    }
                }
            }

            if (state.isComplete) {
                RoundCompleteSheet(starsEarned = 1, onContinue = onPlayAgain, onHome = onBack)
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

/** Where each toy lies on the floor (fractions of the play area) and how it's tilted. */
private val FLOOR_SPOTS = listOf(
    Triple(0.27f, 0.64f, -14f),  // teddy
    Triple(0.79f, 0.74f, 22f),   // book, in front of the hamper (not its home)
    Triple(0.43f, 0.72f, 9f),    // shirt
    Triple(0.07f, 0.72f, 0f),    // ball, in front of the bookcase
)
