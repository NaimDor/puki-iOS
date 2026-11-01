package io.github.tomsadeh.puki.ui.myday

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.navigation.Routes
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.components.BackArrowIcon
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.SceneBackdrop
import io.github.tomsadeh.puki.ui.components.OfflineParrot
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.popIn

data class RoomDef(
    val route: String,
    val name: BilingualText,
    @DrawableRes val icon: Int,
    val color: Color
)

private val rooms = listOf(
    RoomDef(Routes.MY_DAY_BATH, BilingualText("אמבטיה", "Bath Time"), R.drawable.ic_myday_bathtub, Color(0xFF81D4FA)),
    RoomDef(Routes.MY_DAY_BRUSH, BilingualText("צחצוח שיניים", "Brushy Brushy"), R.drawable.ic_myday_toothbrush, Color(0xFF80CBC4)),
    RoomDef(Routes.MY_DAY_TIDY, BilingualText("סידור חדר", "Tidy Up"), R.drawable.ic_tidy_teddy, Color(0xFFFFCC80)),
    RoomDef(Routes.MY_DAY_DRESS, BilingualText("להתלבש", "Getting Dressed"), R.drawable.ic_clothing_shirt, Color(0xFFEF9A9A)),
    RoomDef(Routes.MY_DAY_TABLE, BilingualText("ארוחה", "Table Time"), R.drawable.ic_table_plate, Color(0xFFA5D6A7)),
    RoomDef(Routes.MY_DAY_BEDTIME, BilingualText("שינה", "Bedtime"), R.drawable.ic_bedtime_moon, Color(0xFF7E57C2)),
    RoomDef(Routes.MY_DAY_KITCHEN, BilingualText("מטבח", "Kitchen"), R.drawable.ic_myday_cake, Color(0xFFFFAB91)),
    RoomDef(Routes.MY_DAY_GARAGE, BilingualText("שטיפת מכונית", "Car Wash"), R.drawable.ic_myday_car, Color(0xFF90A4AE))
)

// Upstairs: toys, dressing, teeth and bed; downstairs: the bath, the table, the kitchen and the garage
private val upstairs = listOf(rooms[2], rooms[3], rooms[1], rooms[5])
private val downstairs = listOf(rooms[0], rooms[4], rooms[6], rooms[7])

// The house occupies this part of the screen (fractions of width / height)
private const val HOUSE_LEFT = 0.12f
private const val HOUSE_RIGHT = 0.88f
private const val WALL_TOP = 0.30f
private const val WALL_BOTTOM = 0.94f
private const val ATTIC_Y = 0.18f
private const val ATTIC_R = 0.06f

@Composable
fun MyDayScreen(
    onRoomClick: (String) -> Unit,
    onBack: () -> Unit
) {
    // Read only by the smoke's own layer: the house is drawn once, not every frame
    val smoke = rememberInfiniteTransition(label = "smoke").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "smoke_rise"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        SceneBackdrop(Scene.GARDEN)
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawSmoke(smoke.value) }
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawHouse() }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Puki peeks out of the round attic window
            val atticSize = maxHeight * ATTIC_R * 2
            OfflineParrot(
                modifier = Modifier
                    .offset(maxWidth / 2 - atticSize / 2, maxHeight * ATTIC_Y - atticSize / 2)
                    .size(atticSize)
                    .padding(atticSize * 0.1f),
                size = atticSize * 0.8f
            )

            // Rooms, laid out inside the house walls as a cutaway, two floors
            val left = maxWidth * HOUSE_LEFT
            val top = maxHeight * WALL_TOP
            val width = maxWidth * (HOUSE_RIGHT - HOUSE_LEFT)
            val height = maxHeight * (WALL_BOTTOM - WALL_TOP)
            Column(
                modifier = Modifier
                    .offset(left, top)
                    .size(width, height)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                listOf(upstairs, downstairs).forEachIndexed { f, floor ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        floor.forEachIndexed { i, room ->
                            RoomButton(
                                room = room,
                                index = f * 4 + i,
                                onClick = { onRoomClick(room.route) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(64.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(EarthBrown.copy(alpha = 0.85f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            BackArrowIcon()
        }
    }
}

/** Little clouds puffing out of the chimney ([smoke] 0..1 moves them up), behind the house. */
private fun DrawScope.drawSmoke(smoke: Float) {
    val w = size.width
    val h = size.height
    val chimneyX = w * HOUSE_RIGHT - w * 0.14f
    for (k in 0 until 3) {
        val t = (smoke + k / 3f) % 1f
        val puff = Offset(chimneyX + w * 0.025f + kotlin.math.sin(t * 6f) * w * 0.012f + t * w * 0.03f, h * (0.05f - t * 0.1f))
        drawCircle(Color.White.copy(alpha = 0.8f * (1f - t)), h * (0.018f + t * 0.03f), puff)
    }
}

/** Bushes, a tiled roof with its chimney, and the walls the rooms sit in. */
private fun DrawScope.drawHouse() {
    val w = size.width
    val h = size.height
    val l = w * HOUSE_LEFT
    val r = w * HOUSE_RIGHT
    val top = h * WALL_TOP
    val bottom = h * WALL_BOTTOM

    // Chimney behind the roof
    val chimneyX = r - w * 0.14f
    drawRect(Color(0xFF8D6E63), Offset(chimneyX, h * 0.06f), Size(w * 0.05f, h * 0.18f))
    drawRect(Color(0xFF6D4C41), Offset(chimneyX - w * 0.005f, h * 0.05f), Size(w * 0.06f, h * 0.025f))

    // Roof, with rows of round tiles
    val roofL = l - w * 0.04f
    val roofR = r + w * 0.04f
    val roofBase = top + h * 0.01f
    val roof = Path().apply {
        moveTo(roofL, roofBase)
        lineTo(w / 2, h * 0.04f)
        lineTo(roofR, roofBase)
        close()
    }
    drawPath(roof, Color(0xFFE53935))
    clipPath(roof) {
        val tile = h * 0.045f
        var y = h * 0.04f + tile
        var row = 0
        while (y < roofBase + tile) {
            var x = if (row % 2 == 0) 0f else tile
            while (x < w) {
                drawArc(Color(0xFFC62828), 0f, 180f, false, Offset(x - tile, y - tile), Size(tile * 2, tile * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                x += tile * 2
            }
            y += tile
            row++
        }
    }
    val eave = Path().apply {
        moveTo(roofL, roofBase)
        lineTo(roofR, roofBase)
        lineTo(r + w * 0.02f, top - h * 0.015f)
        lineTo(l - w * 0.02f, top - h * 0.015f)
        close()
    }
    drawPath(eave, Color(0xFFB71C1C))

    // Round attic window (Puki sits in it)
    val attic = Offset(w / 2, h * ATTIC_Y)
    drawCircle(Color(0xFFFFF8E1), h * (ATTIC_R + 0.012f), attic)
    drawCircle(Color(0xFFB3E5FC), h * ATTIC_R, attic)

    // Walls with a floor between the two stories
    drawRoundRect(Color(0x33000000), Offset(l + 8f, top + 10f), Size(r - l, bottom - top), CornerRadius(16f))
    drawRoundRect(Color(0xFFFFE0B2), Offset(l, top), Size(r - l, bottom - top), CornerRadius(16f))
    drawRect(Color(0xFFBC8F5F), Offset(l, (top + bottom) / 2 - h * 0.01f), Size(r - l, h * 0.02f))

    // Round bushes at the corners of the house
    for ((x, s) in listOf(l - w * 0.02f to 1f, l + w * 0.04f to 0.75f, r + w * 0.02f to 1f, r - w * 0.04f to 0.75f)) {
        val c = Offset(x, bottom - h * 0.01f)
        val rad = h * 0.06f * s
        drawCircle(Color(0xFF43A047), rad, c)
        drawCircle(Color(0xFF66BB6A), rad * 0.8f, c + Offset(-rad * 0.6f, -rad * 0.3f))
        drawCircle(Color(0xFF66BB6A), rad * 0.7f, c + Offset(rad * 0.6f, -rad * 0.25f))
        drawCircle(Color(0xFFF48FB1), rad * 0.12f, c + Offset(-rad * 0.3f, -rad * 0.55f))
        drawCircle(Color(0xFFFFF59D), rad * 0.12f, c + Offset(rad * 0.4f, -rad * 0.1f))
    }
}

/** The inside of a room: patterned wallpaper, a wooden floor and a skirting board. */
private fun DrawScope.drawRoomInterior(color: Color, pattern: Int) {
    val w = size.width
    val h = size.height
    val floorTop = h * 0.78f
    drawRect(Color.White)  // opaque, so the button's shadow doesn't show through
    drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.9f)), endY = floorTop))
    val ink = Color.White.copy(alpha = 0.35f)
    clipRect(bottom = floorTop) {
        when (pattern % 3) {
            0 -> {  // polka dots
                val step = 36.dp.toPx()
                var y = step / 2
                var row = 0
                while (y < floorTop) {
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < w) { drawCircle(ink, 5.dp.toPx(), Offset(x, y)); x += step }
                    y += step * 0.8f
                    row++
                }
            }
            1 -> {  // stripes
                var x = 0f
                val step = 34.dp.toPx()
                while (x < w) { drawRect(ink, Offset(x, 0f), Size(step * 0.4f, floorTop)); x += step }
            }
            else -> {  // little stars
                val step = 44.dp.toPx()
                var y = step / 2
                var row = 0
                while (y < floorTop) {
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < w) {
                        val s = 6.dp.toPx()
                        drawLine(ink, Offset(x - s, y), Offset(x + s, y), 3.dp.toPx())
                        drawLine(ink, Offset(x, y - s), Offset(x, y + s), 3.dp.toPx())
                        x += step
                    }
                    y += step * 0.8f
                    row++
                }
            }
        }
    }
    // Floorboards and skirting
    drawRect(Color(0xFFD7A86E), Offset(0f, floorTop), Size(w, h - floorTop))
    val plank = Color(0xFFBF8A55)
    var px = w * 0.17f
    while (px < w) { drawLine(plank, Offset(px, floorTop), Offset(px, h), 2.dp.toPx()); px += w * 0.25f }
    drawRect(Color.White, Offset(0f, floorTop - 4.dp.toPx()), Size(w, 5.dp.toPx()))
}

@Composable
private fun RoomButton(room: RoomDef, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val language = LocalLanguage.current
    val shape = RoundedCornerShape(24.dp)

    BoxWithConstraints(
        modifier = modifier
            .popIn(index)
            .shadow(6.dp, shape)
            .clip(shape)
            .drawBehind { drawRoomInterior(room.color, index) }
            .border(BorderStroke(6.dp, Color.White), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // On a short screen (a phone on its side) the picture and the label shrink to fit the room
        val name = room.name.resolve(language)
        val iconSize = minOf(124.dp, maxHeight - 72.dp).coerceAtLeast(40.dp)
        val baseSize = MaterialTheme.typography.labelLarge.fontSize
        val fitSize = with(LocalDensity.current) { ((maxWidth - 52.dp) / (name.length * 0.5f)).toSp() }
        Image(
            painter = painterResource(room.icon),
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = 36.dp)
                .size(iconSize)
                .idleBob(index, amount = 1.3f)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(shadow = null),
            fontSize = if (fitSize < baseSize) fitSize else baseSize,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFF8E1))
                .border(BorderStroke(2.dp, Color(0xFFBC8F5F)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}
