package io.github.tomsadeh.puki.ui.stickers

import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import io.github.tomsadeh.puki.ui.components.rememberCompact
import io.github.tomsadeh.puki.ui.components.NextArrow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.State
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.GameProgress
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.BackButton
import io.github.tomsadeh.puki.ui.components.SparkleLayer
import io.github.tomsadeh.puki.ui.components.rememberTouchSparkles
import io.github.tomsadeh.puki.ui.components.touchSparkles
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import io.github.tomsadeh.puki.ui.hub.components.StarCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

data class StickerDef(
    val id: String,
    val name: BilingualText,
    @DrawableRes val icon: Int,
    val starsRequired: Int
)

val allStickers = listOf(
    StickerDef("cow", BilingualText("פרה", "Cow"), R.drawable.ic_animal_cow, 0),
    StickerDef("dog", BilingualText("כלב", "Dog"), R.drawable.ic_animal_dog, 2),
    StickerDef("cat", BilingualText("חתול", "Cat"), R.drawable.ic_animal_cat, 4),
    StickerDef("rainbow", BilingualText("קשת", "Rainbow"), R.drawable.ic_sticker_rainbow, 6),
    StickerDef("star", BilingualText("כוכב", "Star"), R.drawable.ic_sticker_star, 8),
    StickerDef("flower", BilingualText("פרח", "Flower"), R.drawable.ic_sticker_flower, 10),
    StickerDef("butterfly", BilingualText("פרפר", "Butterfly"), R.drawable.ic_sticker_butterfly, 14),
    StickerDef("heart", BilingualText("לב", "Heart"), R.drawable.ic_sticker_heart, 18),
    StickerDef("music", BilingualText("מוזיקה", "Music"), R.drawable.ic_sticker_music, 22),
    StickerDef("sun", BilingualText("שמש", "Sun"), R.drawable.ic_sticker_sun, 26),
    StickerDef("moon", BilingualText("ירח", "Moon"), R.drawable.ic_sticker_moon, 30),
    StickerDef("rocket", BilingualText("טיל", "Rocket"), R.drawable.ic_sticker_rocket, 40),
    StickerDef("crown", BilingualText("כתר", "Crown"), R.drawable.ic_sticker_crown, 50),
    StickerDef("trophy", BilingualText("גביע", "Trophy"), R.drawable.ic_sticker_trophy, 60),
    StickerDef("diamond", BilingualText("יהלום", "Diamond"), R.drawable.ic_sticker_diamond, 80),
    StickerDef("unicorn", BilingualText("חד קרן", "Unicorn"), R.drawable.ic_sticker_unicorn, 100),
    StickerDef("duck", BilingualText("ברווז", "Duck"), R.drawable.ic_animal_duck, 120),
    StickerDef("horse", BilingualText("סוס", "Horse"), R.drawable.ic_animal_horse, 140),
    StickerDef("sheep", BilingualText("כבשה", "Sheep"), R.drawable.ic_animal_sheep, 160),
    StickerDef("pig", BilingualText("חזיר", "Pig"), R.drawable.ic_animal_pig, 180),
    StickerDef("rabbit", BilingualText("ארנב", "Rabbit"), R.drawable.ic_animal_rabbit, 200),
    StickerDef("chicken", BilingualText("תרנגולת", "Chicken"), R.drawable.ic_animal_chicken, 230),
    StickerDef("goat", BilingualText("עז", "Goat"), R.drawable.ic_animal_goat, 260),
    StickerDef("monkey", BilingualText("קוף", "Monkey"), R.drawable.ic_animal_monkey, 290),
    StickerDef("lion", BilingualText("אריה", "Lion"), R.drawable.ic_animal_lion, 320),
    StickerDef("elephant", BilingualText("פיל", "Elephant"), R.drawable.ic_animal_elephant, 350),
    StickerDef("giraffe", BilingualText("ג׳ירפה", "Giraffe"), R.drawable.ic_animal_giraffe, 380),
    StickerDef("penguin", BilingualText("פינגווין", "Penguin"), R.drawable.ic_animal_penguin, 420)
)

/** The next sticker to celebrate: the first line crossed since [rewarded], if she's reached one. */
fun nextSticker(rewarded: Int, stars: Int): StickerDef? =
    allStickers.firstOrNull { it.starsRequired > rewarded && it.starsRequired <= stars }

class StickerAlbumViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PukiApplication).progressRepository
    val progress = repository.progress

    /** Where things stood when she opened the album; from here on, what she has counts as seen. */
    suspend fun open(): GameProgress = repository.progress.first().also { repository.markAlbumSeen(it.totalStars) }
}

private val stickerRims = listOf(
    Color(0xFFFFCDD2), Color(0xFFBBDEFB), Color(0xFFFFF9C4), Color(0xFFC8E6C9), Color(0xFFE1BEE7), Color(0xFFFFE0B2)
)

private const val PER_PAGE = 9
private const val COLUMNS = 3

@Composable
fun StickerAlbumScreen(onBack: () -> Unit, viewModel: StickerAlbumViewModel = viewModel()) {
    val companion = LocalCompanion.current
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val progress by viewModel.progress.collectAsStateWithLifecycle(GameProgress())
    val spreads = remember { allStickers.chunked(PER_PAGE * 2) }
    var opened by remember { mutableStateOf<GameProgress?>(null) }
    var spread by remember { mutableStateOf(0) }
    val waiting = remember { mutableStateListOf<String>() }  // the new stickers still to arrive
    LaunchedEffect(Unit) {
        companion?.wake()
        val then = viewModel.open()
        val fresh = allStickers.filter { it.starsRequired > then.albumSeenStars && it.starsRequired <= then.totalStars }
        waiting.addAll(fresh.map { it.id })
        // Open at the page with something new on it
        fresh.firstOrNull()?.let { new -> spread = spreads.indexOfFirst { new in it } }
        opened = then
        if (fresh.isEmpty()) voicePlayer.play("voice_album_hello", language)
    }

    val compact = rememberCompact()
    val sparkles = rememberTouchSparkles()
    Box(modifier = Modifier.fillMaxSize().touchSparkles(sparkles)) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawTable() }

        // The album lies open on the table: two pages of three by three
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (compact) Modifier.padding(start = 96.dp, end = 96.dp, top = 12.dp, bottom = 12.dp)
                    else Modifier.padding(start = 40.dp, end = 40.dp, top = 92.dp, bottom = 20.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawAlbum() }
            val pageInset = if (compact) 16.dp else 40.dp
            val cell = min((maxWidth / 2 - pageInset * 2) / COLUMNS, (maxHeight - pageInset * 2) / 3)
            // One clock for every sticker's wiggle (each starts at its own moment)
            val wiggle = rememberInfiniteTransition(label = "sticker_wiggle").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
                label = "wiggle_phase"
            )
            if (opened != null) AnimatedContent(
                targetState = spread,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "album_spread"
            ) { shown ->
                Row(modifier = Modifier.fillMaxSize()) {
                    spreads[shown].chunked(PER_PAGE).forEachIndexed { page, stickers ->
                        Column(
                            modifier = Modifier.weight(1f).padding(pageInset),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            stickers.chunked(COLUMNS).forEachIndexed { row, rowStickers ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowStickers.forEachIndexed { col, sticker ->
                                        val index = shown * PER_PAGE * 2 + page * PER_PAGE + row * COLUMNS + col
                                        StickerSlot(
                                            sticker,
                                            unlocked = progress.totalStars >= sticker.starsRequired,
                                            index = index,
                                            size = cell,
                                            wiggle = wiggle,
                                            // New ones come last, one after another
                                            arrival = waiting.indexOf(sticker.id).takeIf { it >= 0 },
                                            onArrived = { waiting.remove(sticker.id) }
                                        )
                                    }
                                }
                            }
                            // Keep the rows the same height on the shorter page
                            repeat(3 - (stickers.size + COLUMNS - 1) / COLUMNS) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // Turning the pages, on the album's two edges
        if (spread < spreads.lastIndex) PageTurn(forward = true, Modifier.align(Alignment.CenterEnd).padding(4.dp)) {
            sfxPlayer.play(Sfx.WHOOSH, 0.4f)
            spread++
        }
        if (spread > 0) PageTurn(forward = false, Modifier.align(Alignment.CenterStart).padding(4.dp)) {
            sfxPlayer.play(Sfx.WHOOSH, 0.4f)
            spread--
        }

        BackButton(onBack, Modifier.align(Alignment.TopStart).padding(if (compact) 8.dp else 16.dp))
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        StarCounter(
            stars = progress.totalStars,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (compact) 6.dp else 20.dp)
                .graphicsLayer {
                    if (compact) {  // (it sits in the album's margin there)
                        transformOrigin = TransformOrigin(if (rtl) 0f else 1f, 0f)
                        scaleX = 0.72f
                        scaleY = 0.72f
                    }
                }
        )
        SparkleLayer(sparkles)
    }
}

/** A big round arrow for turning the album's pages. */
@Composable
private fun PageTurn(forward: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(72.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFFFFCA28))
            .border(4.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .graphicsLayer { if (!forward) scaleX = -1f },
        contentAlignment = Alignment.Center
    ) { NextArrow() }
}

/** One place in the album: a stuck-on sticker that wiggles now and then, or a dashed outline waiting for it. */
@Composable
private fun StickerSlot(
    sticker: StickerDef,
    unlocked: Boolean,
    index: Int,
    size: Dp,
    wiggle: State<Float>,
    arrival: Int?,  // a new sticker's place in the queue of new ones; null for one she already has
    onArrived: () -> Unit
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val reaction = rememberReactionState(sticker.id)
    // Stuck on a little crooked, the way a child sticks them
    val tilt = ((index * 37) % 13 - 6).toFloat()
    // A new sticker waits its turn as an outline, then lands with a pop
    var landed by remember(sticker.id) { mutableStateOf(arrival == null) }
    val landing = remember(sticker.id) { Animatable(if (arrival == null) 1f else 0f) }
    LaunchedEffect(arrival) {
        if (arrival == null) return@LaunchedEffect
        delay(800L + arrival * 1500L)
        landed = true
        sfxPlayer.play(Sfx.STAR_COLLECT, 0.8f)
        voicePlayer.play("voice_sticker_${sticker.id}", language)
        landing.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
        reaction.celebrate()
        onArrived()
    }

    if (unlocked && landed) {
        Box(
            modifier = Modifier
                .size(size)
                .padding(size * 0.06f)
                .graphicsLayer {
                    val s = landing.value
                    scaleX = s
                    scaleY = s
                }
                .reaction(reaction, jumpHeight = 28.dp)
                .stickerWiggle(index, tilt, wiggle)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(6.dp, stickerRims[index % stickerRims.size], CircleShape)
                .clickable {
                    sfxPlayer.play(Sfx.POP)
                    scope.launch { reaction.celebrate() }
                    val fallback = { voicePlayer.play("voice_sticker_${sticker.id}", language) }
                    if (companion != null) {
                        companion.react("באלבום המדבקות לחצו על המדבקה של ${sticker.name.he}. תגיד עליה משהו קטן ומצחיק.", "In the sticker album the child tapped the ${sticker.name.en.lowercase()} sticker. Say something small and funny about it.", fallback)
                    } else fallback()
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(sticker.icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.78f)
            )
            // A glossy sheen across the top
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    Color.White.copy(alpha = 0.35f), 200f, 70f, false,
                    topLeft = Offset(this.size.width * 0.12f, this.size.height * 0.12f),
                    size = Size(this.size.width * 0.76f, this.size.height * 0.76f),
                    style = Stroke(this.size.width * 0.05f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .padding(size * 0.06f)
                .reaction(reaction)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    // Not yet: it wiggles, and there's what's needed
                    sfxPlayer.play(Sfx.POP)
                    scope.launch { reaction.shake() }
                    voicePlayer.play("voice_more_stars", language)
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(Color(0x14000000))
                drawCircle(
                    Color(0x55A1887F),
                    style = Stroke(4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())))
                )
            }
            // A faint silhouette of what's coming
            Image(
                painter = painterResource(sticker.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color(0xFFBCAAA4)),
                modifier = Modifier.fillMaxSize(0.62f).alpha(0.45f)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painter = painterResource(R.drawable.ic_ui_star), contentDescription = null, modifier = Modifier.size(20.dp))
                Text("${sticker.starsRequired}", style = MaterialTheme.typography.labelLarge.copy(shadow = null), color = EarthBrown)
            }
        }
    }
}

/** Every few seconds the sticker gives a quick happy wiggle; neighbours take turns. */
/** Stuck on crooked by [tilt], with a little wiggle now and then from the album's shared [wiggle]
 *  clock (one layer for both). */
private fun Modifier.stickerWiggle(index: Int, tilt: Float, wiggle: State<Float>): Modifier =
    graphicsLayer {
        val phase = (wiggle.value + index * 0.137f) % 1f
        rotationZ = tilt
        if (phase < 0.16f) {
            val k = phase / 0.16f
            val fade = 1f - k
            rotationZ = tilt + sin(k * 4 * PI.toFloat()) * 9f * fade
            val s = 1f + sin(k * PI.toFloat()) * 0.08f
            scaleX = s
            scaleY = s
        }
    }

// ---------- Drawing the table and album ----------

private fun DrawScope.drawTable() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFD7A86E), Color(0xFFC08A55))))
    val grain = Color(0x14000000)
    var y = 30.dp.toPx()
    var i = 0
    while (y < size.height) {
        val wave = sin(i * 1.3f) * 6.dp.toPx()
        drawLine(grain, Offset(0f, y + wave), Offset(size.width, y - wave), 2.dp.toPx())
        y += 46.dp.toPx()
        i++
    }
}

private fun DrawScope.drawAlbum() {
    val w = size.width
    val h = size.height
    // Cover peeking out around the pages
    drawRoundRect(Color(0x33000000), Offset(6.dp.toPx(), 10.dp.toPx()), size, CornerRadius(28.dp.toPx()))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9C6ADE), Color(0xFF7E4FC4))), cornerRadius = CornerRadius(28.dp.toPx()))
    val inset = 14.dp.toPx()
    // Page edges stacked underneath
    for (k in 3 downTo 1) {
        drawRoundRect(
            Color(0xFFEDE3D1),
            Offset(inset, inset + k * 2.dp.toPx()),
            Size(w - inset * 2, h - inset * 2),
            CornerRadius(14.dp.toPx())
        )
    }
    drawRoundRect(Color(0xFFFFFAF0), Offset(inset, inset), Size(w - inset * 2, h - inset * 2), CornerRadius(14.dp.toPx()))
    // Shade into the spine
    val spine = w / 2
    val shade = 60.dp.toPx()
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color(0x22000000), Color.Transparent), startX = spine - shade, endX = spine + shade),
        Offset(spine - shade, inset), Size(shade * 2, h - inset * 2)
    )
    // Little dotted guide lines on each page
    val dot = Color(0x18A1887F)
    val step = 26.dp.toPx()
    var y = inset + step
    while (y < h - inset) {
        var x = inset + step
        while (x < w - inset) {
            if (kotlin.math.abs(x - spine) > shade) drawCircle(dot, 2.dp.toPx(), Offset(x, y))
            x += step
        }
        y += step
    }
    // Spiral rings down the spine
    val ringGap = 44.dp.toPx()
    var ry = inset + ringGap / 2
    while (ry < h - inset) {
        drawRoundRect(Color(0xFF9E9E9E), Offset(spine - 14.dp.toPx(), ry - 5.dp.toPx()), Size(28.dp.toPx(), 10.dp.toPx()), CornerRadius(5.dp.toPx()))
        drawRoundRect(Color(0xFFE0E0E0), Offset(spine - 14.dp.toPx(), ry - 5.dp.toPx()), Size(28.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        ry += ringGap
    }
}
