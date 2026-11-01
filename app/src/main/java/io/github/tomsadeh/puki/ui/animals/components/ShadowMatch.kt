package io.github.tomsadeh.puki.ui.animals.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.data.content.AnimalDef
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.DragTarget
import io.github.tomsadeh.puki.ui.components.ReactionState
import io.github.tomsadeh.puki.ui.components.hintGlow
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.idleBreathing
import io.github.tomsadeh.puki.ui.components.reaction

/** The animal's shadow, and the animal itself once the child found it. */
@Composable
fun ShadowStage(animal: AnimalDef, revealed: Boolean, reaction: ReactionState, size: Dp = 280.dp) {
    Box(modifier = Modifier.size(size + 40.dp), contentAlignment = Alignment.Center) {
        // (keyed on the animal too: a new round's shadow fades in, the old animal doesn't jump to it)
        Crossfade(targetState = animal to revealed, animationSpec = tween(500), label = "shadow_reveal") { (who, shown) ->
            Image(
                painter = painterResource(who.drawableRes),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .reaction(reaction)
                    .idleBreathing(),
                // Just the outline's shape, in a soft dark grey: a shadow, not a black hole
                colorFilter = if (shown) null else ColorFilter.tint(Color(0xFF455A64).copy(alpha = 0.82f))
            )
        }
    }
}

/** An animal to drag onto the shadow (or tap: it flies there by itself). */
@Composable
fun AnimalChoice(
    animal: AnimalDef,
    onDropped: (dropTargetId: String?) -> Unit,
    tapToZone: String,
    index: Int,
    reaction: ReactionState,
    hint: Boolean = false,
    size: Dp = 165.dp
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    DragTarget(
        itemId = animal.id,
        onDragEnd = onDropped,
        onDragStart = { voicePlayer.play("voice_animal_${animal.id}", language) },
        tapToZone = tapToZone
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .reaction(reaction, jumpHeight = 24.dp)
                .hintGlow(hint)
                .idleBob(index)
                .size(size)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
                .border(6.dp, Color(0xFFC8E6C9), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(animal.drawableRes),
                contentDescription = null,
                modifier = Modifier.size(size * 0.8f)
            )
        }
    }
}
