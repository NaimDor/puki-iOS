package io.github.tomsadeh.puki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.R

/** The back arrow; points left in English and right in Hebrew. */
@Composable
private fun BackArrowIcon(modifier: Modifier = Modifier) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Image(
        painter = painterResource(R.drawable.ic_ui_back),
        contentDescription = null,
        modifier = modifier
            .size(28.dp)
            .graphicsLayer(scaleX = if (isRtl) -1f else 1f)
    )
}

/** The way out of every screen: looks 64dp (it shouldn't compete with the game), and takes a
 *  100dp touch, which spills over its layout size. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { MutableInteractionSource() }
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .indication(press, LocalIndication.current)
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { BackArrowIcon() }
        Box(modifier = Modifier.requiredSize(100.dp).clickable(press, indication = null, onClick = onBack))
    }
}
