package io.github.tomsadeh.puki.ui.myday.bedtime

import androidx.compose.runtime.DisposableEffect
import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.components.BackArrowIcon
import android.app.Application
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.theme.CloudWhite
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BedtimeStep(
    val id: String,
    val instruction: BilingualText,
    @DrawableRes val icon: Int,
    val actionLabel: BilingualText
)

val bedtimeSteps = listOf(
    BedtimeStep("pajamas", BilingualText("נלבש פיג'מה!", "Put on pajamas!"), R.drawable.ic_clothing_pajamas, BilingualText("ללבוש", "Wear")),
    BedtimeStep("brush", BilingualText("נצחצח שיניים!", "Brush your teeth!"), R.drawable.ic_myday_toothbrush, BilingualText("לצחצח", "Brush")),
    BedtimeStep("teddy", BilingualText("נשים את הדובי במיטה!", "Put teddy in bed!"), R.drawable.ic_tidy_teddy, BilingualText("לשים", "Place")),
    BedtimeStep("light", BilingualText("נכבה את האור!", "Turn off the light!"), R.drawable.ic_bedtime_bulb, BilingualText("לכבות", "Off")),
    BedtimeStep("stars", BilingualText("כוכבים בשמיים! לילה טוב!", "Stars in the sky! Good night!"), R.drawable.ic_ui_star, BilingualText("לילה טוב", "Good night"))
)

data class BedtimeState(
    val currentStep: Int = 0,
    val isComplete: Boolean = false
)

class BedtimeRoutineViewModel(application: Application) : AndroidViewModel(application) {
    private val progressRepository = (application as PukiApplication).progressRepository
    private val _state = MutableStateFlow(BedtimeState())
    val state: StateFlow<BedtimeState> = _state.asStateFlow()

    fun advanceStep() {
        val current = _state.value
        val nextStep = current.currentStep + 1
        if (nextStep >= bedtimeSteps.size) {
            _state.value = current.copy(isComplete = true)
            viewModelScope.launch { progressRepository.addStars(2) }
        } else {
            _state.value = current.copy(currentStep = nextStep)
        }
    }
}

@Composable
fun BedtimeRoutineScreen(onBack: () -> Unit, viewModel: BedtimeRoutineViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val companion = LocalCompanion.current  // waking now gets the story ready by the end
    // Puki knows where she is (tapping him talks about this routine), and wakes up on the way in
    LaunchedEffect(Unit) {
        companion?.setScene("בשגרת השינה: פיג'מה, צחצוח שיניים, דובי, ולכבות את האור.", "Bedtime: pajamas, brushing teeth, teddy, and lights out.")
        companion?.wake()
    }
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    // The calm night track while getting ready for bed, back to the main theme afterwards
    val musicPlayer = LocalMusicPlayer.current
    DisposableEffect(Unit) {
        musicPlayer.playTrack("music_night")
        onDispose {
            musicPlayer.duck(false)
            musicPlayer.playTrack("music_hub")
        }
    }
    val sfxPlayer = LocalSfxPlayer.current

    // Gradually darken the background as steps progress
    val darkness = state.currentStep.toFloat() / bedtimeSteps.size
    val bgColors = listOf(
        Color(0xFF5C6BC0).copy(alpha = 0.3f + darkness * 0.7f),
        Color(0xFF3949AB).copy(alpha = 0.4f + darkness * 0.6f),
        Color(0xFF1A237E).copy(alpha = 0.5f + darkness * 0.5f)
    )

    LaunchedEffect(state.currentStep) {
        if (!state.isComplete && state.currentStep < bedtimeSteps.size) {
            delay(500)
            voicePlayer.play("voice_bedtime_${bedtimeSteps[state.currentStep].id}", language)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(bgColors)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(CloudWhite.copy(alpha = 0.3f)).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { BackArrowIcon() }
                Text(BilingualText("שינה טובה", "Bedtime").resolve(language), style = MaterialTheme.typography.headlineMedium, color = CloudWhite)
                Spacer(modifier = Modifier.width(56.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress dots
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                bedtimeSteps.forEachIndexed { i, step ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(if (i == state.currentStep) 28.dp else 18.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        i < state.currentStep -> Brush.radialGradient(
                                            listOf(Color(0xFFFFE082), Color(0xFFFFD700))
                                        )
                                        i == state.currentStep -> Brush.radialGradient(
                                            listOf(CloudWhite, CloudWhite.copy(alpha = 0.8f))
                                        )
                                        else -> Brush.radialGradient(
                                            listOf(CloudWhite.copy(alpha = 0.35f), CloudWhite.copy(alpha = 0.2f))
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i < state.currentStep) Text("✓", fontSize = 11.sp, color = EarthBrown)
                        }
                        if (i == state.currentStep) {
                            Image(painter = painterResource(step.icon), contentDescription = null, modifier = Modifier.padding(top = 4.dp).size(20.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!state.isComplete && state.currentStep < bedtimeSteps.size) {
                val step = bedtimeSteps[state.currentStep]

                AnimatedContent(
                    targetState = state.currentStep,
                    transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() },
                    label = "bedtime_step"
                ) { _ ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(painter = painterResource(step.icon), contentDescription = null, modifier = Modifier.size(130.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            step.instruction.resolve(language),
                            style = MaterialTheme.typography.headlineMedium,
                            color = CloudWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action button
                Box(
                    modifier = Modifier
                        .size(width = 200.dp, height = 80.dp)
                        .shadow(8.dp, RoundedCornerShape(40.dp))
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF9575CD), Color(0xFF7E57C2))
                            )
                        )
                        .clickable {
                            sfxPlayer.play(Sfx.POP)
                            viewModel.advanceStep()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        bedtimeSteps[state.currentStep].actionLabel.resolve(language),
                        style = MaterialTheme.typography.titleLarge,
                        color = CloudWhite
                    )
                }
            }

            // Stars ceiling effect when complete
            if (state.isComplete) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(7) { Image(painter = painterResource(R.drawable.ic_ui_star), contentDescription = null, modifier = Modifier.size(36.dp)) }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            repeat(5) { Image(painter = painterResource(R.drawable.ic_myday_sparkle), contentDescription = null, modifier = Modifier.size(26.dp)) }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Image(painter = painterResource(R.drawable.ic_bedtime_moon), contentDescription = null, modifier = Modifier.size(84.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                BilingualText("לילה טוב!", "Good night!").resolve(language),
                                style = MaterialTheme.typography.headlineLarge,
                                color = CloudWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Image(painter = painterResource(R.drawable.ic_bedtime_zzz), contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.isComplete) {
            // פוקי's bedtime story, when the brain on the PC is reachable
            BedtimeStory()
        }
    }
}
