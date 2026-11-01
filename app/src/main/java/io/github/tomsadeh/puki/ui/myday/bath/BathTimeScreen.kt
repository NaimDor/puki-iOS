package io.github.tomsadeh.puki.ui.myday.bath

import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.data.models.Gender
import io.github.tomsadeh.puki.ui.common.LocalGender
import io.github.tomsadeh.puki.ui.myday.aboveWater
import io.github.tomsadeh.puki.ui.myday.drawMudSplat
import io.github.tomsadeh.puki.ui.myday.drawSoapBubbles
import io.github.tomsadeh.puki.ui.myday.drawToddler
import io.github.tomsadeh.puki.ui.myday.drawTub
import io.github.tomsadeh.puki.ui.components.popIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.absoluteOffset
import io.github.tomsadeh.puki.ui.myday.PukiCorner
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.GameTopBar
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.tomsadeh.puki.audio.reactOr
import io.github.tomsadeh.puki.ui.myday.drawWaterDrop
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.ui.components.rememberStepGame
import io.github.tomsadeh.puki.ui.components.rememberHelp
import io.github.tomsadeh.puki.ui.components.noticeTouches
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.ToolButton
import io.github.tomsadeh.puki.ui.components.RoundPrompt
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

data class BodyPart(val id: String, val name: BilingualText, val cleaned: Boolean = false)

data class BathState(
    val bodyParts: List<BodyPart> = listOf(
        BodyPart("arms", BilingualText("ידיים", "Arms")),
        BodyPart("legs", BilingualText("רגליים", "Legs")),
        BodyPart("tummy", BilingualText("בטן", "Tummy")),
        BodyPart("face", BilingualText("פנים", "Face"))
    ),
    val allClean: Boolean = false
)

private enum class BathStep { SOAP, RINSE, DRY }

class BathTimeViewModel(application: Application) : AndroidViewModel(application) {
    val session = GameSession("bath", (application as PukiApplication).progressRepository, viewModelScope)
    private val _state = MutableStateFlow(BathState())
    val state: StateFlow<BathState> = _state.asStateFlow()

    fun onSoapDropped(bodyPartId: String) {
        val current = _state.value
        val updated = current.bodyParts.map {
            if (it.id == bodyPartId) it.copy(cleaned = true) else it
        }
        _state.value = current.copy(bodyParts = updated, allClean = updated.all { it.cleaned })
    }
}

/** Bath time: soap off the mud, rinse under the shower, dry with the towel, and all clean. */
@Composable
fun BathTimeScreen(
    onBack: () -> Unit,
    onPlayAgain: () -> Unit = onBack,  // "Continue" on the end card: this game again, from the start
    viewModel: BathTimeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = LocalCompanion.current
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val dragDropState = remember { DragDropState() }

    val game = rememberStepGame(BathStep.SOAP, BathStep.entries.size, viewModel.session)
    val step = game.step
    var rinsing by remember { mutableStateOf(false) }
    var drying by remember { mutableStateOf(false) }
    var dried by remember { mutableStateOf(false) }
    val busy = game.betweenSteps || rinsing || drying || dried
    val nextSpot = SPOTS.firstOrNull { spot -> state.bodyParts.none { it.id == spot.partId && it.cleaned } }

    val help = rememberHelp(step, active = !busy) {
        voicePlayer.play(
            when (step) {
                BathStep.SOAP -> "voice_prompt_bath"
                BathStep.RINSE -> "voice_bath_rinse"
                BathStep.DRY -> "voice_bath_dry"
            },
            language
        )
    }
    when (step) {
        BathStep.SOAP -> RoundPrompt(0, "באמבטיה: מסבנים את הבוץ מהפנים, מהידיים, מהבטן ומהרגליים, ואז שוטפים במקלחת.", "At bath time: soaping the mud off the face, arms, tummy and legs, then a rinse in the shower.", "voice_prompt_bath")
        BathStep.RINSE -> RoundPrompt(1, "באמבטיה: שוטפים את הסבון במקלחת.", "At bath time: rinsing the soap off in the shower.", "voice_bath_rinse")
        BathStep.DRY -> RoundPrompt(2, "באמבטיה: מתנגבים במגבת.", "At bath time: drying off with the towel.", "voice_bath_dry")
    }
    LaunchedEffect(state.allClean) { if (state.allClean) game.done(BathStep.RINSE) }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        GameBackground(scene = Scene.BATH) {
            Column(
                modifier = Modifier.fillMaxSize().noticeTouches(help).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GameTopBar(onBack = onBack, roundIndex = step.ordinal, totalRounds = BathStep.entries.size, starsEarned = game.stars)

                // The child in the tub, with the soap, the shower or the towel beside it
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val tool = minOf(maxHeight * 0.42f, 150.dp).coerceAtLeast(100.dp)
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = PukiCorner),  // clear of Puki
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BathScene(
                            parts = state.bodyParts,
                            rinsing = rinsing,
                            washed = rinsing || step == BathStep.DRY,
                            wet = step == BathStep.DRY && !dried,
                            hintZone = if (step == BathStep.SOAP && help.showHint) nextSpot?.zoneId else null,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        Box(modifier = Modifier.width(tool * 1.4f), contentAlignment = Alignment.Center) {
                            if (step == BathStep.SOAP && !state.allClean) DragTarget(
                                itemId = "soap",
                                // A tap soaps the next muddy spot
                                tapToZone = nextSpot?.zoneId,
                                onDragEnd = { dropId ->
                                    val partId = dropId?.removeSuffix("2")  // each arm and leg is its own spot
                                    if (partId != null && state.bodyParts.any { it.id == partId }) {
                                        viewModel.onSoapDropped(partId)
                                        sfxPlayer.play(Sfx.POP)
                                        voicePlayer.play("voice_body_$partId", language)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier.hintGlow(help.showHint).idleBob(0).size(tool).shadow(8.dp, CircleShape).clip(CircleShape).background(Color(0xFFFFF9C4)),
                                    contentAlignment = Alignment.Center
                                ) { Image(painter = painterResource(R.drawable.ic_myday_soap), contentDescription = null, modifier = Modifier.size(tool * 0.68f)) }
                            }
                            if (step == BathStep.RINSE && !rinsing && !game.betweenSteps) ToolButton(
                                size = tool,
                                hint = help.showHint,
                                color = Color(0xFF29B6F6),
                                onClick = {
                                    sfxPlayer.play(Sfx.WHOOSH)
                                    rinsing = true
                                    scope.launch {
                                        delay(2200)  // let the water pour first
                                        rinsing = false
                                        game.done(BathStep.DRY)
                                    }
                                }
                            ) { Image(painter = painterResource(R.drawable.ic_myday_shower), contentDescription = null, modifier = Modifier.size(tool * 0.64f)) }
                            if (step == BathStep.DRY && !busy) ToolButton(
                                size = tool,
                                hint = help.showHint,
                                color = Color(0xFFFCE4EC),
                                onClick = {
                                    sfxPlayer.play(Sfx.WHOOSH, 0.6f)
                                    drying = true
                                    dried = true
                                    scope.launch {
                                        delay(900)
                                        voicePlayer.play("voice_bath_clean", language) {
                                            companion.reactOr("באמבטיה סיבנו את כל הבוץ, שטפו במקלחת והתנגבו. עכשיו הכול נקי!", "At bath time the child soaped off all the mud, rinsed in the shower and dried off. All clean!") { }
                                        }
                                        game.done(null)
                                        delay(2800)
                                        game.finish()
                                    }
                                }
                            ) { Canvas(modifier = Modifier.size(tool * 0.66f)) { drawTowel() } }
                        }
                    }
                }
            }

            ConfettiOverlay(show = game.celebrate)
            if (game.sessionComplete) {
                RoundCompleteSheet(starsEarned = game.stars, onContinue = onPlayAgain, onHome = onBack)
            }
        }
    }
}


/** Where each muddy spot sits on the child (fractions of the picture), and its radius. */
private data class Spot(val zoneId: String, val partId: String, val x: Float, val y: Float, val r: Float)

private val SPOTS = listOf(
    Spot("face", "face", 0.565f, 0.295f, 0.045f),  // a cheek, clear of the eyes
    Spot("arms", "arms", 0.31f, 0.52f, 0.05f),
    Spot("arms2", "arms", 0.69f, 0.52f, 0.05f),
    Spot("tummy", "tummy", 0.5f, 0.5f, 0.06f),
    Spot("legs", "legs", 0.435f, 0.74f, 0.045f),
    Spot("legs2", "legs", 0.565f, 0.74f, 0.045f),
)

/** The child standing in the tub: mud on each spot until it's soaped, then bubbles. */
@Composable
private fun BathScene(parts: List<BodyPart>, rinsing: Boolean, washed: Boolean, wet: Boolean, hintZone: String?, modifier: Modifier = Modifier) {
    val girl = LocalGender.current == Gender.GIRL
    // Animations are read only inside the moving layer's drawing, so nothing recomposes per frame
    val wave = rememberInfiniteTransition(label = "bath").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "wave")
    // The shower's water only runs while rinsing
    val fall = if (rinsing) rememberInfiniteTransition(label = "shower").animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "fall") else null
    // The rinse washes the bubbles away
    val bubblesLeft = animateFloatAsState(if (washed) 0f else 1f, tween(1400, delayMillis = 300), label = "bubbles")
    val dropsLeft = animateFloatAsState(if (wet) 1f else 0f, tween(800), label = "drops")
    // The mud fades as it's soaped off
    val mud = SPOTS.map { spot ->
        animateFloatAsState(if (parts.any { it.id == spot.partId && it.cleaned }) 0f else 1f, tween(500), label = "mud_${spot.zoneId}")
    }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(modifier = Modifier.size(side)) {
            // Still: the child and the mud left on her (redrawn only when a spot is soaped)
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                aboveWater { drawToddler(girl, mapOf("underwear" to 1f)) }
                SPOTS.forEachIndexed { i, spot -> drawMudSplat(Offset(spot.x, spot.y), spot.r, seed = i * 7 + 3, amount = mud[i].value) }
            }
            // Moving: the bubbles, the water and the shower
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                SPOTS.forEach { spot ->
                    if (parts.any { it.id == spot.partId && it.cleaned }) drawSoapBubbles(Offset(spot.x, spot.y), spot.r, wave.value, bubblesLeft.value)
                }
                // (not on her face: there, a drop reads as a tear)
                if (dropsLeft.value > 0f) SPOTS.filter { it.partId != "face" }.forEach { spot -> drawWaterDrop(Offset(spot.x, spot.y), dropsLeft.value) }
                drawTub(wave.value, fall?.value ?: 0f, rinsing)
            }
            // Where the soap goes: a little bigger than the mud, so it's easy to hit
            SPOTS.forEach { spot ->
                val r = spot.r * 1.5f
                DropZone(
                    zoneId = spot.zoneId,
                    modifier = Modifier
                        // From the picture's top-left corner in both languages (the picture doesn't mirror)
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(x = side * (spot.x - r), y = side * (spot.y - r))
                        .size(side * r * 2)
                        .hintGlow(hintZone == spot.zoneId)
                ) {}
            }
        }
    }
}

/** A folded towel, for the last step. */
private fun DrawScope.drawTowel() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFFF48FB1), Offset(0f, h * 0.2f), Size(w, h * 0.62f), CornerRadius(h * 0.08f))
    drawRoundRect(Color(0xFFEC407A), Offset(0f, h * 0.7f), Size(w, h * 0.12f), CornerRadius(h * 0.05f))
    for (k in 0..2) drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.06f, h * (0.32f + k * 0.13f)), Offset(w * 0.94f, h * (0.32f + k * 0.13f)), h * 0.035f)
}
