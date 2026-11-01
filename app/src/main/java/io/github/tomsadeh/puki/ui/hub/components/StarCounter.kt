package io.github.tomsadeh.puki.ui.hub.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.R

/** Her stars; with [onClick], also the door to the album where they go: one big tap target. */
@Composable
fun StarCounter(stars: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    val door = remember { MutableInteractionSource() }
    Box(
        modifier = if (onClick == null) modifier else modifier
            .sizeIn(minWidth = 160.dp, minHeight = 100.dp)
            .clickable(door, indication = null, onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
    Row(
        modifier = Modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Color(0xFFFFF8E1))
            .border(BorderStroke(3.dp, Color(0xFFFFC107)), shape)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onClick != null) {
            Image(
                painter = painterResource(R.drawable.ic_ui_stickers),
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Image(
            painter = painterResource(R.drawable.ic_ui_star),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        AnimatedContent(
            targetState = stars,
            transitionSpec = {
                (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
            },
            label = "star_count"
        ) { count ->
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium.copy(shadow = null),
                color = Color(0xFF6D4C41)
            )
        }
    }
}
}
