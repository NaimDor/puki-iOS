package io.github.tomsadeh.puki.ui.myday.table

import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.popIn
import io.github.tomsadeh.puki.ui.components.idleBob
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.tomsadeh.puki.ui.components.MyDayTopBar
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

data class PlaceSetting(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val placed: Boolean = false)

data class TableState(
    val items: List<PlaceSetting> = listOf(
        PlaceSetting("plate", BilingualText("צלחת", "Plate"), R.drawable.ic_table_plate),
        PlaceSetting("cup", BilingualText("כוס", "Cup"), R.drawable.ic_table_cup),
        PlaceSetting("fork", BilingualText("מזלג", "Fork"), R.drawable.ic_table_fork),
        PlaceSetting("spoon", BilingualText("כפית", "Spoon"), R.drawable.ic_table_spoon)
    ),
    val isComplete: Boolean = false
)

class TableTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as PukiApplication).progressRepository
    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state.asStateFlow()

    fun onItemPlaced(itemId: String, targetId: String): Boolean {
        if (itemId != targetId) return false
        val updated = _state.value.items.map { if (it.id == itemId) it.copy(placed = true) else it }
        val complete = updated.all { it.placed }
        _state.value = _state.value.copy(items = updated, isComplete = complete)
        if (complete) viewModelScope.launch { progressRepository.addStars(1) }
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
    // Puki knows where she is (tapping him talks about this routine)
    LaunchedEffect(Unit) {
        companion?.setScene("בעריכת השולחן: שמים צלחת, כוס, מזלג וכף.", "Setting the table: plate, cup, fork and spoon.")
    }
    LaunchedEffect(state.isComplete) {
        if (state.isComplete) companion?.react("ערכו את כל השולחן: צלחת, כוס, מזלג וכף!", "The child set the whole table: plate, cup, fork and spoon!")
    }
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val dragDropState = remember { DragDropState() }

    LaunchedEffect(Unit) {
        delay(500)
        voicePlayer.play("voice_prompt_set_table", language)
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(
            scene = Scene.HOME,
            colors = listOf(Color(0xFFC8E6C9), Color(0xFFA5D6A7), Color(0xFF81C784))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MyDayTopBar(BilingualText("ארוחה", "Table Time"), onBack)

                Spacer(modifier = Modifier.height(16.dp))

                // The table seen from above, with the basket of things to set beside it
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableScene(items = state.items, modifier = Modifier.weight(1f).fillMaxHeight())
                    Basket(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                        state.items.forEachIndexed { i, item ->
                            if (!item.placed) {
                                val (dx, dy, tilt) = BASKET_SPOTS[i]
                                DragTarget(
                                    itemId = item.id,
                                    modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(dx.dp, dy.dp),
                                    onDragEnd = { dropId ->
                                        if (dropId != null) {
                                            val correct = viewModel.onItemPlaced(item.id, dropId)
                                            if (correct) {
                                                sfxPlayer.play(Sfx.DROP)
                                                voicePlayer.play("voice_table_${item.id}", language)
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
                                            .size(118.dp)
                                            .graphicsLayer { rotationZ = tilt }
                                    )
                                }
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


/** Where each thing goes on the table (fractions of the table), and how big its place is. */
private data class Place(val id: String, val x: Float, val y: Float, val w: Float, val h: Float)

private val PLACES = listOf(
    Place("plate", 0.50f, 0.56f, 0.34f, 0.34f),
    Place("fork", 0.24f, 0.56f, 0.13f, 0.34f),
    Place("spoon", 0.76f, 0.56f, 0.13f, 0.34f),
    Place("cup", 0.74f, 0.20f, 0.16f, 0.16f),
)

/** The things in the basket: offset in dp and a little tilt, so they look tumbled in. */
private val BASKET_SPOTS = listOf(Triple(40f, 70f, -12f), Triple(150f, 40f, 10f), Triple(30f, 200f, 18f), Triple(150f, 190f, -8f))

/** A wooden table seen from above, with a placemat and a faint outline where each thing goes. */
@Composable
private fun TableScene(items: List<PlaceSetting>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * 1.35f)
        val h = w / 1.35f
        Box(modifier = Modifier.size(w, h)) {
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
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (item.placed) {
                            Image(painter = painterResource(item.icon), contentDescription = null,
                                modifier = Modifier.popIn().fillMaxSize(0.95f))
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

/** A woven basket the things are tumbled into. */
@Composable
private fun Basket(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(300.dp, 360.dp)) {
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
