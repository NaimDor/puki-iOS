package io.github.tomsadeh.puki.ui.components

import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalPukiMood
import io.github.tomsadeh.puki.data.models.BilingualText
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.R

/**
 * What every play screen stands on: the [scene] (or nothing, for a game that draws its own), the
 * game, Puki in the corner and the sparkles under her finger.
 */
@Composable
fun GameBackground(
    modifier: Modifier = Modifier,
    /** False where the game shows Puki itself (Say it with Puki). */
    withCompanion: Boolean = true,
    /** An illustrated backdrop; without one, the game draws its own. */
    scene: Scene? = null,
    content: @Composable () -> Unit
) {
    val sparkles = rememberTouchSparkles()
    CompositionLocalProvider(LocalPukiMood provides remember { ReactionState() }) {
    Box(modifier = modifier.fillMaxSize().touchSparkles(sparkles)) {
        if (scene != null) SceneBackdrop(scene)

        content()

        // פוקי the parrot, when the companion server is reachable
        if (withCompanion) CompanionParrot(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        SparkleLayer(sparkles)
    }
    }
}

@Composable
fun GameTopBar(
    onBack: () -> Unit,
    roundIndex: Int,
    totalRounds: Int,
    starsEarned: Int? = null,  // the routines have dots but no stars to count
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BackButton(onBack)

        // Round dots
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(totalRounds) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == roundIndex) 18.dp else 14.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                i < roundIndex -> Color(0xFFFFD700)
                                i == roundIndex -> Color.White
                                else -> Color.Black.copy(alpha = 0.18f)
                            }
                        )
                )
            }
        }

        // Stars
        if (starsEarned == null) Spacer(modifier = Modifier.width(64.dp)) else Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ui_star),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Text("$starsEarned", fontSize = 20.sp, color = Color.White)
            }
        }
    }
}

/**
 * Starts a round: tells Puki what's on screen ([scene] in Hebrew, [sceneEn] in English), then,
 * after a breath, says the round's prompt ([voiceKeys], one after another).
 */
@Composable
fun RoundPrompt(round: Int, scene: String, sceneEn: String, vararg voiceKeys: String) {
    val companion = LocalCompanion.current
    val voicePlayer = LocalVoicePlayer.current
    val language = LocalLanguage.current
    LaunchedEffect(round) {
        companion?.setScene(scene, sceneEn)
        delay(500)
        voicePlayer.playSequence(voiceKeys.toList(), language)
    }
}
