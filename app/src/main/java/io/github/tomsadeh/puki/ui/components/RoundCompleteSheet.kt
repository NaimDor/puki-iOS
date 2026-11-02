package io.github.tomsadeh.puki.ui.components

import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.requiredSize
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import io.github.tomsadeh.puki.ui.stickers.nextSticker
import io.github.tomsadeh.puki.ui.stickers.StickerDef
import io.github.tomsadeh.puki.ui.common.LocalOpenAlbum
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.audio.reactOr
import io.github.tomsadeh.puki.PukiApplication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CARD_HEIGHT = 440.dp

@Composable
fun RoundCompleteSheet(
    starsEarned: Int,
    onContinue: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    calm: Boolean = false  // bedtime: no party, a darker room
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val companion = LocalCompanion.current
    val openAlbum = LocalOpenAlbum.current
    val appContext = LocalContext.current.applicationContext
    var showContent by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    // The scene waits under the card, so its frames go to the rays and the stars
    val clock = LocalAmbientClock.current
    DisposableEffect(clock) {
        clock.hold()
        onDispose { clock.release() }
    }
    var earned by remember { mutableStateOf<StickerDef?>(null) }
    var stickerUp by remember { mutableStateOf(false) }
    // The buttons wake once her sticker is safe in the album (or a moment in, with none): an early
    // tap can't leave before she sees what she earned, now that it's counted as given
    var buttonsLive by remember { mutableStateOf(false) }
    val flight = remember { Animatable(0f) }
    val door = rememberReactionState()
    var screenCentre by remember { mutableStateOf(Offset.Zero) }
    var doorCentre by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        delay(200)
        val repository = (appContext as PukiApplication).progressRepository
        val progress = repository.progress.first()
        // One sticker to a card: if she's crossed two lines, the next one waits for the next card
        val sticker = nextSticker(progress.rewardedStars, progress.totalStars)
        repository.markRewarded(sticker?.starsRequired ?: progress.totalStars)
        earned = sticker
        showContent = true
        showConfetti = !calm
        voicePlayer.play(voicePlayer.personalKey("voice_round_done", language), language)
        if (sticker == null) {
            delay(1500)
            buttonsLive = true
            return@LaunchedEffect
        }

        // Once her stars have landed: the sticker, big, then away into the album
        delay(1300L + starsEarned.coerceAtLeast(1) * 380L)
        withTimeoutOrNull(3000) { voicePlayer.speaking.first { !it } }
        stickerUp = true
        if (calm) {
            voicePlayer.play("voice_sticker_${sticker.id}", language)
        } else {
            sfxPlayer.play(Sfx.SUCCESS)
            showConfetti = true
            companion.reactOr("קיבלו עכשיו מדבקה חדשה של ${sticker.name.he}!", "The child just got a new ${sticker.name.en.lowercase()} sticker!") {
                voicePlayer.playSequence(listOf("voice_sticker_new", "voice_sticker_${sticker.id}"), language)
            }
        }
        delay(1400)
        withTimeoutOrNull(4000) { voicePlayer.speaking.first { !it } }
        delay(if (calm) 900 else 300)
        flight.animateTo(1f, tween(if (calm) 1100 else 700, easing = FastOutSlowInEasing))
        stickerUp = false
        if (!calm) sfxPlayer.play(Sfx.STAR_COLLECT, 0.8f)
        buttonsLive = true
        door.celebrate()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { screenCentre = it.boundsInRoot().center }
            .background(Color.Black.copy(alpha = if (calm) 0.6f else 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        if (showContent && !calm) SunRays(side = maxOf(maxWidth, maxHeight) * 1.1f, modifier = Modifier.popIn())

        // The card keeps its shape on a short screen: scaled as a whole, so buttons stay round
        val fit = (maxHeight * 0.96f / CARD_HEIGHT).coerceAtMost(1f)
        if (showContent) Column(
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .graphicsLayer { scaleX = fit; scaleY = fit }
                .popIn()
                .shadow(16.dp, RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF5), CloudWhite)))
                .border(8.dp, Color(0xFFFFE082), RoundedCornerShape(40.dp))
                .padding(horizontal = 56.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = BilingualText("כל הכבוד!", "Great job!").resolve(language),
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF5D4037)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // The stars land one by one, each with a twinkle
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(starsEarned.coerceAtLeast(1)) { i ->
                    LandingStar(index = i, size = if (starsEarned > 3) 72.dp else 104.dp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                RoundButton(
                    color = Color(0xFF66BB6A),
                    label = BilingualText("המשך", "Continue"),
                    index = 0,
                    pulse = buttonsLive,
                    onClick = { if (buttonsLive) onContinue() }
                ) { NextArrow() }
                RoundButton(
                    color = Color(0xFFFFCA28),
                    label = BilingualText("בית", "Home"),
                    index = 1,
                    pulse = false,
                    onClick = { if (buttonsLive) onHome() }
                ) {
                    Image(painter = painterResource(R.drawable.ic_word_house), contentDescription = null, modifier = Modifier.size(72.dp))
                }
                // Where the new sticker goes, and the way to go and see it
                if (earned != null && openAlbum != null) RoundButton(
                    color = Color(0xFFB39DDB),
                    label = BilingualText("אלבום", "Album"),
                    index = 2,
                    pulse = false,
                    onClick = { if (buttonsLive) openAlbum() },
                    modifier = Modifier
                        .onGloballyPositioned { doorCentre = it.boundsInRoot().center }
                        .reaction(door, jumpHeight = 18.dp)
                ) {
                    Image(painter = painterResource(R.drawable.ic_ui_stickers), contentDescription = null, modifier = Modifier.size(76.dp))
                }
            }
        }

        val sticker = earned
        if (stickerUp && sticker != null) {
            val big = minOf(maxHeight * 0.62f, 320.dp)
            BigSticker(
                sticker,
                modifier = Modifier
                    .size(big)
                    .graphicsLayer {
                        val t = flight.value
                        translationX = (doorCentre.x - screenCentre.x) * t
                        translationY = (doorCentre.y - screenCentre.y) * t
                        val shrink = 1f - t * 0.82f
                        scaleX = shrink
                        scaleY = shrink
                    }
            )
        }

        ConfettiOverlay(show = showConfetti, onFinished = { showConfetti = false })
    }
}

/** A new sticker, shown big: it pops up over the card with a little jump. */
@Composable
private fun BigSticker(sticker: StickerDef, modifier: Modifier = Modifier) {
    val jump = rememberReactionState()
    LaunchedEffect(Unit) { jump.celebrate() }
    Box(
        modifier = modifier
            .popIn()
            .reaction(jump, jumpHeight = 24.dp)
            .graphicsLayer { rotationZ = -5f }
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .border(10.dp, Color(0xFFFFE082), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(sticker.icon), contentDescription = null, modifier = Modifier.fillMaxSize(0.76f))
    }
}

/** Soft rays turning slowly behind the card. */
@Composable
private fun SunRays(side: Dp, modifier: Modifier = Modifier) {
    val turn by rememberInfiniteTransition(label = "rays").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)),
        label = "rays_turn"
    )
    // Drawn once; the layer does the turning (only its angle changes each frame)
    Spacer(modifier = modifier.requiredSize(side).graphicsLayer {
        rotationZ = turn
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.maxDimension
        val glow = Brush.radialGradient(listOf(Color(0x66FFF59D), Color.Transparent), center = c, radius = size.minDimension * 0.6f)
        val rayBrush = Brush.radialGradient(listOf(Color(0x40FFFFFF), Color.Transparent), center = c, radius = r * 0.55f)
        val rays = Path().apply {
            val n = 16
            for (i in 0 until n step 2) {
                val a0 = Math.toRadians((i * 360.0 / n)).toFloat()
                val a1 = Math.toRadians(((i + 1) * 360.0 / n)).toFloat()
                moveTo(c.x, c.y)
                lineTo(c.x + kotlin.math.cos(a0) * r, c.y + kotlin.math.sin(a0) * r)
                lineTo(c.x + kotlin.math.cos(a1) * r, c.y + kotlin.math.sin(a1) * r)
                close()
            }
        }
        onDrawBehind {
            drawCircle(glow, size.minDimension * 0.6f, c)
            drawPath(rays, rayBrush)
        }
    })
}

/** A star that drops in spinning, squashes as it lands, and plays its chime. */
@Composable
private fun LandingStar(index: Int, size: Dp) {
    val sfxPlayer = LocalSfxPlayer.current
    val scale = remember { Animatable(0f) }
    val spin = remember { Animatable(-200f) }
    val reaction = rememberReactionState()
    LaunchedEffect(Unit) {
        delay(450L + index * 380L)
        sfxPlayer.play(Sfx.STAR_COLLECT, 0.8f)
        coroutineScope {
            launch { spin.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        }
        reaction.celebrate()
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_ui_star),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .reaction(reaction, jumpHeight = 14.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = spin.value
                    alpha = scale.value.coerceIn(0f, 1f)
                }
        )
    }
}

/** A big round button with a picture, so it can be used without reading; the label is for grown-ups. */
@Composable
private fun RoundButton(
    color: Color,
    label: BilingualText,
    index: Int,
    pulse: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val language = LocalLanguage.current
    val sfxPlayer = LocalSfxPlayer.current
    val beat: State<Float> = if (pulse) rememberInfiniteTransition(label = "button_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "beat"
    ) else remember { mutableFloatStateOf(1f) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .popIn(index + 2)
                .graphicsLayer { scaleX = beat.value; scaleY = beat.value }
                .size(124.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.2f), color)))
                .border(6.dp, Color.White, CircleShape)
                .clickable {
                    sfxPlayer.play(Sfx.POP)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) { icon() }
        Text(
            text = label.resolve(language),
            style = MaterialTheme.typography.titleMedium.copy(shadow = null),
            color = Color(0xFF8D6E63),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** A chunky white arrow pointing onwards (it flips for right-to-left). */
@Composable
internal fun NextArrow() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = Modifier.size(64.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f }) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(Color.White, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), w * 0.17f, StrokeCap.Round)
        val head = Path().apply {
            moveTo(w * 0.5f, h * 0.2f)
            lineTo(w * 0.8f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.8f)
        }
        drawPath(head, Color.White, style = stroke)
    }
}
