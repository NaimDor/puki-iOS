package io.github.tomsadeh.puki.ui.myday.bedtime

import io.github.tomsadeh.puki.ui.common.LocalMusicPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Pictures the story can use, with a label that tells Gemma what each one shows. */
private val STORY_ICONS = linkedMapOf(
    "ic_animal_cow" to BilingualText("פרה", "cow"), "ic_animal_dog" to BilingualText("כלב", "dog"), "ic_animal_cat" to BilingualText("חתול", "cat"),
    "ic_animal_elephant" to BilingualText("פיל", "elephant"), "ic_animal_lion" to BilingualText("אריה", "lion"), "ic_animal_rabbit" to BilingualText("ארנב", "rabbit"),
    "ic_animal_duck" to BilingualText("ברווז", "duck"), "ic_animal_sheep" to BilingualText("כבשה", "sheep"), "ic_animal_monkey" to BilingualText("קוף", "monkey"),
    "ic_animal_giraffe" to BilingualText("ג׳ירפה", "giraffe"), "ic_animal_penguin" to BilingualText("פינגווין", "penguin"), "ic_animal_horse" to BilingualText("סוס", "horse"),
    "ic_bedtime_moon" to BilingualText("ירח", "moon"), "ic_sticker_star" to BilingualText("כוכב", "star"), "ic_sticker_sun" to BilingualText("שמש", "sun"),
    "ic_sticker_rainbow" to BilingualText("קשת", "rainbow"), "ic_sticker_flower" to BilingualText("פרח", "flower"), "ic_sticker_butterfly" to BilingualText("פרפר", "butterfly"),
    "ic_sticker_heart" to BilingualText("לב", "heart"), "ic_tidy_teddy" to BilingualText("דובי", "teddy bear"), "ic_word_house" to BilingualText("בית", "house"),
    "ic_food_banana" to BilingualText("בננה", "banana"), "ic_count_apple" to BilingualText("תפוח", "apple"), "ic_tidy_ball" to BilingualText("כדור", "ball"),
    "ic_myday_bathtub" to BilingualText("אמבטיה", "bathtub"), "ic_bedtime_zzz" to BilingualText("שינה", "sleep"),
)

/**
 * After the bedtime routine, פוקי tells a short calm story about the child's day,
 * one sentence at a time with a matching picture. Shows nothing if the brain isn't there.
 */
@Composable
fun BedtimeStory(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val companion = LocalCompanion.current
    val language = LocalLanguage.current
    val musicPlayer = LocalMusicPlayer.current
    val context = LocalContext.current
    var sentences by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var current by remember { mutableIntStateOf(-1) }
    val done by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        delay(2500)  // let "good night" land first
        fun res(name: String) = context.resources.getIdentifier(name, "drawable", context.packageName)
        val story = companion?.story(STORY_ICONS.filterKeys { res(it) != 0 }.mapValues { it.value.resolve(language) })
        val items = story.orEmpty().map { (text, icon) -> text to res(icon) }.filter { it.second != 0 }
        if (companion == null || items.isEmpty()) {
            delay(3500)  // no story tonight: a quiet moment with her tucked in
            done()
            return@LaunchedEffect
        }
        sentences = items
        musicPlayer.duck(true)  // quieter music under the story
        coroutineScope {
            // Fetch the next sentence's audio while the current one plays
            var next = async { companion.fetchSpeech(items[0].first) }
            for (i in items.indices) {
                val audio = next.await()
                if (i + 1 < items.size) next = async { companion.fetchSpeech(items[i + 1].first) }
                current = i
                if (audio != null) companion.playAndWait(audio) else delay(2500)
                delay(600)
            }
        }
        delay(1500)
        musicPlayer.duck(false)
        current = -1
        delay(900)  // (the fade out)
        done()
    }

    AnimatedVisibility(visible = current in sentences.indices, enter = fadeIn(tween(900)), exit = fadeOut(tween(900))) {
        // (fading out, it keeps the last sentence)
        val last = current.takeIf { it >= 0 } ?: sentences.lastIndex
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xF51A237E)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Crossfade(targetState = last, animationSpec = tween(900), label = "story_picture") { i ->
                    Image(
                        painter = painterResource(sentences[i].second),
                        contentDescription = null,
                        modifier = Modifier.size(260.dp)
                    )
                }
                Text(
                    text = sentences[last].first,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp, start = 48.dp, end = 48.dp)
                )
            }
        }
    }
}
