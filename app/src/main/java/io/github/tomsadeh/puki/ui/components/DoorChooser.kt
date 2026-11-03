package io.github.tomsadeh.puki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalLanguage

/** One way into an island: a big round door with a picture, and its name under it for the grown-up. */
class Door(val label: BilingualText, val color: Color, val onTap: () -> Unit, val picture: @Composable BoxScope.() -> Unit)

/** A door whose picture is a drawable, with an optional [badge] in its corner ("?!"). */
fun imageDoor(label: BilingualText, color: Color, drawable: Int, badge: String? = null, onTap: () -> Unit) = Door(label, color, onTap) {
    Image(painterResource(drawable), contentDescription = null, modifier = Modifier.size(170.dp))
    if (badge != null) Text(badge, fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100),
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 34.dp, end = 52.dp))
}

/**
 * An island with more than one game in it opens here: a door for each (Puki's corner: Say it with Puki and
 * Silly Puki; Music: the instruments and the dance), so every game is a door away and none hides inside another.
 */
@Composable
fun DoorChooserScreen(onBack: () -> Unit, scene: Scene, doors: List<Door>) {
    val language = LocalLanguage.current
    GameBackground(scene = scene) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(onBack = onBack, roundIndex = 0, totalRounds = 0, starsEarned = null)
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (door in doors) Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = door.onTap)
                ) {
                    Box(
                        modifier = Modifier.size(240.dp).idleBob(amount = 0.6f).shadow(10.dp, CircleShape).clip(CircleShape).background(door.color),
                        contentAlignment = Alignment.Center,
                        content = door.picture
                    )
                    Spacer(Modifier.height(14.dp))
                    // On a tag, like the islands' names, so it reads on any scene (the stage's floor is brown too)
                    Text(door.label.resolve(language), style = MaterialTheme.typography.titleLarge, color = EarthBrown,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.92f))
                            .padding(horizontal = 18.dp, vertical = 6.dp))
                }
            }
        }
    }
}
