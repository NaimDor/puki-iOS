package io.github.tomsadeh.puki.ui.animals.components

import io.github.tomsadeh.puki.ui.components.idleBreathing
import io.github.tomsadeh.puki.ui.components.reaction
import io.github.tomsadeh.puki.ui.components.rememberReactionState
import io.github.tomsadeh.puki.ui.components.ReactionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.ui.components.popIn
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.data.content.AnimalDef
import androidx.compose.foundation.Image
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.WigglingItem
import kotlinx.coroutines.launch

@Composable
fun AnimalDisplay(
    animal: AnimalDef,
    modifier: Modifier = Modifier,
    reaction: ReactionState = rememberReactionState(animal.id)
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    var isBouncing by remember { mutableStateOf(false) }

    val bounceScale by animateFloatAsState(
        targetValue = if (isBouncing) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "bounce"
    )

    WigglingItem(enabled = !isBouncing) {
        Column(
            modifier = modifier
                .popIn(key = animal.id)  // each round's animal arrives
                .graphicsLayer {
                    scaleX = bounceScale
                    scaleY = bounceScale
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        scope.launch {
                            isBouncing = true
                            voicePlayer.playSequence(
                                listOf("voice_animal_${animal.id}", "voice_sound_${animal.id}"),
                                language
                            ) {
                                isBouncing = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = animal.drawableRes),
                    contentDescription = animal.name.en,
                    modifier = Modifier
                        .size(280.dp)
                        .reaction(reaction)
                        .idleBreathing()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Name label with background pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.93f))
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                Text(
                    text = animal.name.resolve(language),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }
}
