package io.github.tomsadeh.puki.ui.myday.dress

import io.github.tomsadeh.puki.ui.components.hintGlow
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.ui.common.LocalGender
import io.github.tomsadeh.puki.ui.myday.drawToddler
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.idleBob
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.absoluteOffset
import io.github.tomsadeh.puki.ui.myday.PukiCorner
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.GameTopBar
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClothingItem(val id: String, val name: BilingualText, @DrawableRes val icon: Int, val order: Int)

data class DressState(
    val clothes: List<ClothingItem> = listOf(
        ClothingItem("shirt", BilingualText("חולצה", "Shirt"), R.drawable.ic_clothing_shirt, 0),
        ClothingItem("pants", BilingualText("מכנסיים", "Pants"), R.drawable.ic_clothing_pants, 1),
        ClothingItem("socks", BilingualText("גרביים", "Socks"), R.drawable.ic_clothing_socks, 2),
        ClothingItem("shoes", BilingualText("נעליים", "Shoes"), R.drawable.ic_clothing_shoes, 3)
    ),
    val worn: Set<String> = emptySet()
) {
    val isComplete get() = worn.size == clothes.size
    /** What to suggest next; she can pick anything. */
    val next get() = clothes.firstOrNull { it.id !in worn }
}

class GettingDressedViewModel(application: Application) : AndroidViewModel(application) {
    private val session = GameSession("dress", (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(DressState())
    val state: StateFlow<DressState> = _state.asStateFlow()

    /** She can dress in any order; only shoes wait for socks. False when they have to wait. */
    fun onClothingTapped(clothingId: String): Boolean {
        val current = _state.value
        if (clothingId == "shoes" && "socks" !in current.worn) {
            session.slip()
            return false
        }
        val updated = current.copy(worn = current.worn + clothingId)
        _state.value = updated
        session.star()
        if (updated.isComplete) session.finish(updated.clothes.size)
        return true
    }
}

@Composable
fun GettingDressedScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: GettingDressedViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = LocalCompanion.current
    RoundPrompt(0, "בהתלבשות: בוחרים בגדים מהארון ומתלבשים.", "Getting dressed: picking clothes from the wardrobe.", "voice_prompt_get_dressed")
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()

    val childReaction = rememberReactionState()
    var showCard by remember { mutableStateOf(false) }

    // Stuck? Suggest a piece after a quiet moment; after a while, it wiggles
    val help = rememberHelp(state.worn.size, active = !state.isComplete) {
        state.next?.let { voicePlayer.play("voice_clothing_${it.id}", language) }
    }

    // All dressed: she hops, and then the end card
    LaunchedEffect(state.isComplete) {
        if (!state.isComplete) return@LaunchedEffect
        voicePlayer.play("voice_dress_done", language) {
            companion?.react("התלבשו מכף רגל ועד ראש, עד הנעליים!", "The child got dressed from head to toe, shoes and all!")
        }
        launch { childReaction.celebrate() }
        delay(3000)
        showCard = true
    }

    GameBackground(
            scene = Scene.HOME
    ) {
        Column(
            modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GameTopBar(onBack = onBack, roundIndex = state.worn.size, totalRounds = state.clothes.size, starsEarned = state.worn.size)

            // The child, dressed as far as we've got, and the open wardrobe (clear of Puki's corner)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = PukiCorner),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DressedChild(worn = state.worn, reaction = childReaction, modifier = Modifier.weight(1f).fillMaxHeight())
                Wardrobe(modifier = Modifier.width(430.dp).fillMaxHeight()) { s ->
                    state.clothes.forEach { item ->
                        if (item.id !in state.worn) {
                            val spot = WARDROBE_SPOTS.getValue(item.id)
                            val reaction = rememberReactionState(item.id)
                            val next = item.id == state.next?.id
                            Image(
                                painter = painterResource(item.icon),
                                contentDescription = item.name.resolve(language),
                                modifier = Modifier
                                    .align(AbsoluteAlignment.TopLeft)
                                    .absoluteOffset(spot.x.dp * s, spot.y.dp * s)
                                    .idleBob(item.order, if (next) 1.4f else 0.4f)  // what comes next moves a little more
                                    .reaction(reaction)
                                    .hintGlow(help.showHint && next)
                                    .size(spot.size.dp * s)
                                    .graphicsLayer { rotationZ = spot.tilt }
                                    .clickable {
                                        if (viewModel.onClothingTapped(item.id)) {
                                            sfxPlayer.play(Sfx.SUCCESS, 0.6f)
                                            voicePlayer.play("voice_clothing_${item.id}", language)
                                            scope.launch { childReaction.hop() }
                                        } else {
                                            // Shoes wait for socks: a puzzled look, and the socks
                                            sfxPlayer.play(Sfx.WRONG_GENTLE)
                                            voicePlayer.play("voice_clothing_socks", language)
                                            help.missed()
                                            scope.launch { childReaction.oops() }
                                            scope.launch { reaction.shake() }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        ConfettiOverlay(show = state.isComplete)
        if (showCard) {
            RoundCompleteSheet(starsEarned = state.worn.size, onContinue = onPlayAgain, onHome = onBack)
        }
    }
}


/** The big child, drawn with whatever's been put on so far (each piece grows into place). */
@Composable
private fun DressedChild(worn: Set<String>, reaction: ReactionState, modifier: Modifier = Modifier) {
    val girl = LocalGender.current == Gender.GIRL
    val ids = listOf("shirt", "pants", "socks", "shoes")
    val shown = ids.associateWith { id ->
        animateFloatAsState(
            targetValue = if (id in worn) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
            label = "worn_$id"
        ).value
    }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(modifier = Modifier.reaction(reaction, jumpHeight = 24.dp).size(side)) { drawToddler(girl, shown + ("underwear" to 1f)) }
    }
}

/** Where each piece waits in the wardrobe: offset and size in dp, and a little tilt. */
private data class WardrobeSpot(val x: Float, val y: Float, val size: Float, val tilt: Float)

private val WARDROBE_SPOTS = mapOf(
    "shirt" to WardrobeSpot(55f, 70f, 140f, -4f),
    "pants" to WardrobeSpot(225f, 70f, 140f, 5f),
    "socks" to WardrobeSpot(150f, 262f, 120f, 8f),
    "shoes" to WardrobeSpot(140f, 390f, 130f, 0f),
)

/** An open wardrobe: a rail with hangers, a shelf, and the floor for shoes. Drawn 430 × 540 dp,
 *  smaller where that doesn't fit (a phone on its side); [content] gets the scale for its spots. */
@Composable
private fun Wardrobe(modifier: Modifier = Modifier, content: @Composable BoxScope.(Float) -> Unit) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val s = minOf(1f, maxWidth / 430.dp, maxHeight / 540.dp)
        Box(modifier = Modifier.size(430.dp * s, 540.dp * s)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val wood = Color(0xFFB07A45)
                val inside = Color(0xFFFFF1DC)
                drawRoundRect(wood, cornerRadius = CornerRadius(w * 0.04f))
                drawRoundRect(inside, Offset(w * 0.05f, h * 0.05f), Size(w * 0.9f, h * 0.9f), CornerRadius(w * 0.02f))
                // The rail and two hangers
                drawLine(Color(0xFF9E9E9E), Offset(w * 0.07f, h * 0.12f), Offset(w * 0.93f, h * 0.12f), strokeWidth = w * 0.015f)
                for (cx in listOf(w * 0.29f, w * 0.69f)) {
                    drawArc(Color(0xFF757575), 200f, 140f, false, Offset(cx - w * 0.03f, h * 0.085f), Size(w * 0.06f, h * 0.05f),
                        style = Stroke(width = w * 0.01f))
                    val hanger = Path().apply {
                        moveTo(cx, h * 0.13f); lineTo(cx - w * 0.13f, h * 0.18f); lineTo(cx + w * 0.13f, h * 0.18f); close()
                    }
                    drawPath(hanger, Color(0xFF8D6E63), style = Stroke(width = w * 0.012f))
                }
                // The shelf
                drawRect(wood, Offset(w * 0.05f, h * 0.68f), Size(w * 0.9f, h * 0.025f))
                drawRect(Color.Black.copy(alpha = 0.08f), Offset(w * 0.05f, h * 0.705f), Size(w * 0.9f, h * 0.02f))
                // Open doors, seen edge-on at the sides
                drawRect(wood.copy(alpha = 0.9f), Offset(-w * 0.0f, h * 0.02f), Size(w * 0.03f, h * 0.96f))
                drawRect(wood.copy(alpha = 0.9f), Offset(w * 0.97f, h * 0.02f), Size(w * 0.03f, h * 0.96f))
            }
            content(s)
        }
    }
}
