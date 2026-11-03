package io.github.tomsadeh.puki.ui.silly

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.data.content.StoryContent
import io.github.tomsadeh.puki.data.content.allAnimals
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.common.awaitUnpaused
import io.github.tomsadeh.puki.ui.common.rememberGamePaused
import io.github.tomsadeh.puki.ui.components.GameBackground
import io.github.tomsadeh.puki.ui.components.GameTopBar
import io.github.tomsadeh.puki.ui.components.RoundCompleteSheet
import io.github.tomsadeh.puki.ui.components.Scene
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.components.popIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** The places and things the app has pictures for (the sea, the forest, the playground and a balloon wait for art). */
private val PLACE_ART = mapOf("farm" to R.drawable.ic_island_animals, "hill" to R.drawable.ic_word_mountain, "garden" to R.drawable.ic_island_counting)
private val THING_ART = mapOf("banana" to R.drawable.ic_food_banana, "apple" to R.drawable.ic_count_apple, "ball" to R.drawable.ic_tidy_ball,
    "flower" to R.drawable.ic_sticker_flower, "star" to R.drawable.ic_sticker_star, "rainbow" to R.drawable.ic_sticker_rainbow,
    "cake" to R.drawable.ic_myday_cake)

/** A pick the child can make: its id, and its picture. */
private data class Pick(val id: String, val art: Int)

/**
 * The story with choices: Puki tells a little animal's story, a sentence at a time, written by his brain; the
 * child picks where it goes, who it meets and what it finds (two pictures each; with no pick, one is chosen for
 * her), and the next sentence goes there. A sentence must name what she picked. Five steps, then the hero goes
 * home to sleep. Only with the brain: a door in Puki's corner.
 */
@Composable
fun StoryGameScreen(onBack: () -> Unit, onPlayAgain: () -> Unit = onBack) {
    val language = LocalLanguage.current
    val english = language == Language.ENGLISH
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val paused = rememberGamePaused()
    val animals = remember { allAnimals.associateBy { it.id } }
    val hero = remember { animals.getValue(StoryContent.HEROES.filter { it in animals }.random()) }
    var step by remember { mutableStateOf(0) }
    var sentence by remember { mutableStateOf("") }
    var picture by remember { mutableStateOf(hero.drawableRes) }
    var choices by remember { mutableStateOf<List<Pick>>(emptyList()) }
    var tap by remember { mutableStateOf<CompletableDeferred<String>?>(null) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (companion?.awaitReady() != true) { onBack(); return@LaunchedEffect }
        val soFar = mutableListOf<String>()
        val offered = mutableMapOf<Int, List<String>>()  // (what she didn't pick mustn't turn up in the story)
        var place = PLACE_ART.keys.random()
        var friend = animals.getValue(StoryContent.FRIENDS.filter { it != hero.id && it in animals }.random())
        var thing = THING_ART.keys.random()
        for (s in 0 until StoryContent.STEPS) {
            step = s
            awaitUnpaused(paused)
            // The child picks where it goes, who it meets, what it finds
            val options = when (s) {
                1 -> PLACE_ART.keys.shuffled().take(2).map { Pick(it, PLACE_ART.getValue(it)) }
                2 -> StoryContent.FRIENDS.filter { it != hero.id && it in animals }.shuffled().take(2).map { Pick(it, animals.getValue(it).drawableRes) }
                3 -> THING_ART.keys.shuffled().take(2).map { Pick(it, THING_ART.getValue(it)) }
                else -> emptyList()
            }
            if (options.isNotEmpty()) {
                offered[s] = options.map { it.id }
                val pick = CompletableDeferred<String>().also { tap = it }
                choices = options
                val id = withTimeoutOrNull(15_000) { pick.await() } ?: options.random().id
                tap = null
                choices = emptyList()
                sfxPlayer.play(Sfx.POP)
                when (s) {
                    1 -> { place = id; picture = PLACE_ART.getValue(id) }
                    2 -> { friend = animals.getValue(id); picture = friend.drawableRes }
                    3 -> { thing = id; picture = THING_ART.getValue(id) }
                }
            }
            if (s == StoryContent.STEPS - 1) picture = hero.drawableRes
            val plan = StoryContent.Plan(hero, place, friend, thing)
            val said = companion?.line("story_next", StoryContent.input(plan, english, soFar.takeLast(3), s),
                pick = StoryContent.pickWord(plan, english, s) ?: "", absent = StoryContent.notPicked(plan, english, s, offered))
            if (said == null) {
                if (s == 0) { onBack(); return@LaunchedEffect }  // (no story at all: nothing to finish)
                break  // (no sentence that names her pick: the story ends here, gently)
            }
            soFar += said
            sentence = said
            delay(700)
        }
        delay(1200)
        done = true
    }

    GameBackground(scene = Scene.TREEHOUSE) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameTopBar(onBack = onBack, roundIndex = step, totalRounds = StoryContent.STEPS, starsEarned = null)
            Column(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AnimatedContent(targetState = picture, label = "story_picture") { art ->
                    Image(painterResource(art), contentDescription = null, modifier = Modifier.popIn(key = art).idleBob(amount = 0.5f).size(220.dp))
                }
                Spacer(Modifier.height(12.dp))
                // What Puki said, for the grown-up reading along
                if (sentence.isNotEmpty()) Text(sentence, style = MaterialTheme.typography.titleLarge, color = EarthBrown, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 20.dp, vertical = 8.dp))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp), modifier = Modifier.height(140.dp)) {
                    for (c in choices) Box(
                        modifier = Modifier.size(140.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { tap?.complete(c.id) },
                        contentAlignment = Alignment.Center
                    ) { Image(painterResource(c.art), contentDescription = null, modifier = Modifier.size(100.dp)) }
                }
            }
        }
        if (done) RoundCompleteSheet(starsEarned = 1, onContinue = onPlayAgain, onHome = onBack, calm = true)
    }
}
