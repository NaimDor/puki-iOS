package io.github.tomsadeh.puki.ui.myday.bedtime

import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.DayLog
import io.github.tomsadeh.puki.data.content.RecapContent
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import kotlinx.coroutines.delay

/**
 * After the bedtime routine, פוקי tells the child about her day: his brain retells two or three of the things
 * she did today (DayLog), calm and in the past tense, under the moon. A line that names fewer than two of them
 * doesn't pass (RecapContent.eventsNamed); with no brain, or fewer than two things today, a quiet moment instead.
 */
@Composable
fun BedtimeStory(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val companion = LocalCompanion.current
    val language = LocalLanguage.current
    val musicPlayer = LocalMusicPlayer.current
    var showing by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf("") }
    val done by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        delay(2500)  // let "good night" land first
        val events = DayLog.today().takeLast(5)  // (the brain learned days of two to five things)
        if (companion == null || !companion.isReady || events.size < 2) {
            delay(3500)  // no recap tonight: a quiet moment with her tucked in
            done()
            return@LaunchedEffect
        }
        musicPlayer.duck(true)  // quieter music under the recap
        showing = true
        said = companion.line("recap", RecapContent.input(language == Language.ENGLISH, events)) ?: ""
        delay(if (said.isEmpty()) 1500 else 3000)
        musicPlayer.duck(false)
        showing = false
        delay(900)  // (the fade out)
        done()
    }

    AnimatedVisibility(visible = showing, enter = fadeIn(tween(900)), exit = fadeOut(tween(900))) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xF51A237E)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(painter = painterResource(R.drawable.ic_bedtime_moon), contentDescription = null, modifier = Modifier.size(220.dp))
                // What he said, for the grown-up reading along
                Text(
                    text = said,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, start = 48.dp, end = 48.dp)
                )
            }
        }
    }
}
