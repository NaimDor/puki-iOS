package io.github.tomsadeh.puki.ui.silly

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.idleBob

/**
 * Puki's corner, the parrot island with his brain on: the games he plays with his own brain. Say it with
 * Puki, and Silly Puki (later the story and the caring game). Without the brain the island opens Say it
 * with Puki directly, as before.
 */
@Composable
fun PukiCornerScreen(onBack: () -> Unit, onSay: () -> Unit, onSilly: () -> Unit) {
    val language = LocalLanguage.current
    GameBackground(scene = Scene.PLAYGROUND) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(onBack = onBack, roundIndex = 0, totalRounds = 0, starsEarned = null)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Door(R.drawable.ic_companion_parrot_talk, null, BilingualText("מדברים עם פוקי", "Say it with Puki").resolve(language), Color(0xFF81D4FA), onSay)
                Door(R.drawable.ic_companion_parrot, "?!", BilingualText("פוקי המבולבל", "Silly Puki").resolve(language), Color(0xFFFFCC80), onSilly)
            }
        }
    }
}

@Composable
private fun Door(picture: Int, badge: String?, label: String, color: Color, onTap: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    ) {
        Box(
            modifier = Modifier.size(240.dp).idleBob(amount = 0.6f).shadow(10.dp, CircleShape).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(picture), contentDescription = label, modifier = Modifier.size(170.dp))
            if (badge != null) Text(badge, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 52.dp))
        }
        Spacer(Modifier.height(14.dp))
        // (for the grown-up reading along)
        Text(label, style = MaterialTheme.typography.titleLarge, color = EarthBrown)
    }
}
