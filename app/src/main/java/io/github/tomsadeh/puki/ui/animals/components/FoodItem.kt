package io.github.tomsadeh.puki.ui.animals.components

import androidx.compose.ui.unit.Dp
import io.github.tomsadeh.puki.ui.components.hintGlow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.data.content.FoodDef
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.components.DragTarget

private val BowlWood = Color(0xFFB98352)
private val BowlInside = Color(0xFF8D6240)
private val BowlRim = Color(0xFFD7A86E)

@Composable
fun FoodItem(
    food: FoodDef,
    onDropped: (dropTargetId: String?) -> Unit,
    modifier: Modifier = Modifier,
    hint: Boolean = false,  // wiggle and glow: this is the one
    tapToZone: String? = null,  // a tap sends it there (the hungry animal)
    landed: (dropTargetId: String) -> Boolean = { false },  // the animal took it: it goes in, not home
    plateSize: Dp = 165.dp  // smaller on a phone's short screen
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    DragTarget(
        itemId = food.id,
        onDragEnd = onDropped,
        onDragStart = { voicePlayer.play("voice_food_${food.id}", language) },
        tapToZone = tapToZone,
        landed = landed,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            // In a feed bowl, so it reads as something to pick up
            Box(
                modifier = Modifier
                    .hintGlow(hint)
                    .idleBob(food.id.length)
                    .size(plateSize)
                    .drawWithContent {
                        val w = size.width
                        val h = size.height
                        drawOval(Color.Black.copy(alpha = 0.14f), Offset(w * 0.08f, h * 0.84f), Size(w * 0.84f, h * 0.14f))
                        drawOval(BowlInside, Offset(w * 0.06f, h * 0.48f), Size(w * 0.88f, h * 0.26f))
                        drawContent()
                        val front = Path().apply {
                            moveTo(w * 0.04f, h * 0.6f)
                            quadraticBezierTo(w * 0.5f, h * 0.76f, w * 0.96f, h * 0.6f)
                            quadraticBezierTo(w * 0.9f, h * 0.92f, w * 0.72f, h * 0.92f)
                            lineTo(w * 0.28f, h * 0.92f)
                            quadraticBezierTo(w * 0.1f, h * 0.92f, w * 0.04f, h * 0.6f)
                            close()
                        }
                        drawPath(front, BowlWood)
                        drawArc(BowlRim, 18f, 144f, false, Offset(w * 0.04f, h * 0.46f), Size(w * 0.92f, h * 0.28f), style = Stroke(h * 0.03f, cap = StrokeCap.Round))
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Image(
                    painter = painterResource(id = food.drawableRes),
                    contentDescription = food.name.en,
                    modifier = Modifier.size(plateSize * 0.74f)
                )
            }
            Text(
                text = food.name.resolve(language),
                fontSize = 16.sp,
                color = Color(0xFF33691E),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}
