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
import io.github.tomsadeh.puki.ui.components.popIn
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
    StickerDef("unicorn", BilingualText("חד קרן", "Unicorn"), R.drawable.ic_sticker_unicorn, 100)
)

class StickerAlbumViewModel(application: Application) : AndroidViewModel(application) {
    val progress = (application as PukiApplication).progressRepository.progress
}

private val stickerRims = listOf(
    Color(0xFFFFCDD2), Color(0xFFBBDEFB), Color(0xFFFFF9C4), Color(0xFFC8E6C9), Color(0xFFE1BEE7), Color(0xFFFFE0B2)
)

private const val PER_PAGE = 9
private const val COLUMNS = 3

@Composable
fun StickerAlbumScreen(onBack: () -> Unit, viewModel: StickerAlbumViewModel = viewModel()) {
    val companion = LocalCompanion.current
    LaunchedEffect(Unit) { companion?.wake() }
    val progress by viewModel.progress.collectAsStateWithLifecycle(GameProgress())

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawTable() }

        // The album lies open on the table: two pages of three by three
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 40.dp, end = 40.dp, top = 92.dp, bottom = 20.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawAlbum() }
            val pageInset = 40.dp
            val cell = min((maxWidth / 2 - pageInset * 2) / COLUMNS, (maxHeight - pageInset * 2) / 3)
            // One clock for every sticker's wiggle (each starts at its own moment)
            val wiggle = rememberInfiniteTransition(label = "sticker_wiggle").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
                label = "wiggle_phase"
            )
            Row(modifier = Modifier.fillMaxSize()) {
                listOf(allStickers.take(PER_PAGE), allStickers.drop(PER_PAGE)).forEachIndexed { page, stickers ->
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
                                    val index = page * PER_PAGE + row * COLUMNS + col
                                    StickerSlot(sticker, unlocked = progress.totalStars >= sticker.starsRequired, index = index, size = cell, wiggle = wiggle)
                                }
                            }
                        }
                        // Keep the rows the same height on the shorter page
                        repeat(3 - (stickers.size + COLUMNS - 1) / COLUMNS) { Box(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        BackButton(onBack, Modifier.align(Alignment.TopStart).padding(16.dp))
        StarCounter(stars = progress.totalStars, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp))
    }
}

/** One place in the album: a stuck-on sticker that wiggles now and then, or a dashed outline waiting for it. */
@Composable
private fun StickerSlot(sticker: StickerDef, unlocked: Boolean, index: Int, size: Dp, wiggle: State<Float>) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val reaction = rememberReactionState(sticker.id)
    // Stuck on a little crooked, the way a child sticks them
    val tilt = ((index * 37) % 13 - 6).toFloat()

    if (unlocked) {
        Box(
            modifier = Modifier
                .size(size)
                .padding(size * 0.06f)
                .popIn(index)
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
        Box(modifier = Modifier.size(size).padding(size * 0.06f), contentAlignment = Alignment.Center) {
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
