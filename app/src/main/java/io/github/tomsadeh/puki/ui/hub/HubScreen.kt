package io.github.tomsadeh.puki.ui.hub

import androidx.compose.foundation.clickable
import io.github.tomsadeh.puki.ui.components.LongPressGate
import androidx.compose.runtime.collectAsState
import io.github.tomsadeh.puki.BuildConfig
import io.github.tomsadeh.puki.update.Updater
import io.github.tomsadeh.puki.ui.common.LocalAppSettings
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.tomsadeh.puki.ui.hub.components.HUB_CLOCK_MS
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.components.OfflineParrot
import io.github.tomsadeh.puki.ui.components.SparkleLayer
import io.github.tomsadeh.puki.ui.components.rememberTouchSparkles
import io.github.tomsadeh.puki.ui.components.touchSparkles
import io.github.tomsadeh.puki.ui.components.idleBob
import io.github.tomsadeh.puki.ui.hub.components.HubBackground
import io.github.tomsadeh.puki.ui.hub.components.IslandButton
import io.github.tomsadeh.puki.ui.hub.components.IslandHeight
import io.github.tomsadeh.puki.ui.hub.components.IslandWidth
import io.github.tomsadeh.puki.ui.hub.components.StarCounter

private var greetedThisLaunch = false

/** How long the hub waits before Puki calls out an island. */
private const val IDLE_CALL_MS = 30_000L

@Composable
fun HubScreen(
    onIslandClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onStickersClick: () -> Unit
) {
    val hubViewModel: HubViewModel = viewModel()
    val progress by hubViewModel.progress.collectAsStateWithLifecycle()
    val voicePlayer = LocalVoicePlayer.current
    val sfxPlayer = LocalSfxPlayer.current
    val language = LocalLanguage.current

    // Look for a game update now and then (at most once a day); a dot on the settings button shows one
    val context = LocalContext.current
    val update by Updater.available.collectAsState()
    LaunchedEffect(Unit) { if (BuildConfig.SELF_UPDATE) Updater.checkDaily(context) }

    // Say hello (by name, if the personal lines exist) the first time the hub opens
    LaunchedEffect(Unit) {
        if (!greetedThisLaunch) {
            greetedThisLaunch = true
            delay(600)
            voicePlayer.play(voicePlayer.personalKey("voice_hello", language), language)
        }
    }

    // One clock for all the islands, read only by their layers
    val clock = rememberInfiniteTransition(label = "hub_clock").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HUB_CLOCK_MS, easing = LinearEasing)),
        label = "hub_tick"
    )
    var called by remember { mutableStateOf<String?>(null) }

    val sparkles = rememberTouchSparkles()
    Box(modifier = Modifier.fillMaxSize().touchSparkles(sparkles)) {
        HubBackground()

        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
            val width = maxWidth
            val height = maxHeight
            // The islands a parent left on the map (all of them unless some were hidden in settings)
            val hidden = LocalAppSettings.current.hiddenIslands
            val islands = allIslands.filter { it.id !in hidden }.ifEmpty { allIslands }

            // Left a while with nothing touched, Puki calls out an island, which hops (a few times, no more)
            LaunchedEffect(islands) {
                repeat(3) {
                    delay(IDLE_CALL_MS)
                    if (!voicePlayer.isPlaying()) {
                        val island = islands.random()
                        called = island.id
                        voicePlayer.play("voice_island_${island.id}", language)
                        delay(1500)
                        called = null
                    }
                }
            }

            // Dotted sea route linking the islands in order, like a treasure map
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                val points = islands.map { Offset(size.width * it.fractionalX, size.height * it.fractionalY + 40.dp.toPx()) }
                if (points.size < 2) return@Canvas
                val route = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val a = points[i - 1]
                        val b = points[i]
                        // Bow each leg slightly so the route meanders
                        val bend = if (i % 2 == 0) 1f else -1f
                        val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                        val ctrl = mid + Offset(-(b.y - a.y), b.x - a.x) * (0.18f * bend)
                        quadraticBezierTo(ctrl.x, ctrl.y, b.x, b.y)
                    }
                }
                drawPath(
                    route,
                    Color.White.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 22.dp.toPx()))
                    )
                )
            }

            // On a phone's short screen the islands would pile up: smaller there (tablets keep full size)
            val screenScale = (height / 560.dp).coerceIn(0.6f, 1f)
            // The map is the same in both languages (the bottom row reads right to left in the
            // order the route visits it), so absolute: a plain offset would mirror it in Hebrew.
            // Islands nearer the horizon are drawn a little smaller, for depth
            islands.forEach { island ->
                val index = allIslands.indexOf(island)  // (each keeps its own palm side)
                val depthScale = (0.82f + 0.35f * ((island.fractionalY - 0.42f) / 0.38f).coerceIn(0f, 1f)) * screenScale
                IslandButton(
                    island = island,
                    onTap = {
                        sfxPlayer.play(Sfx.POP)
                        voicePlayer.stop()  // (her tap comes before Puki's hello)
                        voicePlayer.play("voice_island_${island.id}", language)
                    },
                    onClick = { onIslandClick(island.route) },
                    clock = clock,
                    palmOnLeft = index % 2 == 0,
                    called = called == island.id,
                    modifier = Modifier
                        .absoluteOffset(
                            x = width * island.fractionalX - IslandWidth / 2,
                            y = height * island.fractionalY - IslandHeight / 2
                        )
                        .graphicsLayer {
                            scaleX = depthScale
                            scaleY = depthScale
                        }
                )
            }

            // Puki on the far hill: he says hello with her, and a tap makes him talk
            val pukiSize = 110.dp * screenScale
            OfflineParrot(
                modifier = Modifier.absoluteOffset(x = width * 0.095f, y = height * 0.305f - pukiSize),
                size = pukiSize
            )
        }

        StarCounter(
            stars = progress.totalStars,
            onClick = onStickersClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // Settings — top end, a 2-second hold so the child doesn't open it by accident
        CornerButton(
            icon = R.drawable.ic_ui_settings,
            onHold = onSettingsClick,
            dot = update != null,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Quit — bottom start, a 2-second hold too (toddlers double-tap everything)
        val activity = LocalContext.current as? Activity
        CornerButton(
            icon = R.drawable.ic_ui_exit,
            onHold = { activity?.finishAndRemoveTask() },
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // Free canvas — bottom end, a big bright button: this one's for her
        CornerButton(
            icon = R.drawable.ic_ui_canvas,
            onClick = { onIslandClick("canvas") },
            modifier = Modifier.align(Alignment.BottomEnd)
        )

        SparkleLayer(sparkles)
    }
}

/** A round corner button: big and bright for her. With [onHold] it's for grown-ups, small and
 *  faded: it needs a 2-second hold (a ring fills up around it), so a toddler's taps and double
 *  taps do nothing. */
@Composable
private fun CornerButton(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onHold: (() -> Unit)? = null,
    dot: Boolean = false
) {
  Box(modifier = modifier) {
    val forGrownUps = onHold != null
    val circle = Modifier
        .size(if (forGrownUps) 52.dp else 100.dp)
        .shadow(if (forGrownUps) 2.dp else 8.dp, CircleShape)
        .clip(CircleShape)
        .background(if (forGrownUps) Color(0xFFCFD8DC) else Color(0xFFFFE082))
    val face: @Composable (Modifier) -> Unit = { m ->
        Box(modifier = m, contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(if (forGrownUps) 30.dp else 72.dp)
            )
        }
    }
    if (onHold != null) {
        // The gap between the gate and the button leaves room for the ring to show
        LongPressGate(onUnlocked = onHold, modifier = Modifier.padding(10.dp)) {
            face(Modifier.padding(6.dp).alpha(0.6f).then(circle))
        }
    } else {
        face(Modifier.padding(12.dp).idleBob().then(circle).clickable(onClick = onClick))
    }
    if (dot) {
        // Something for the grown-up (a game update), small enough not to tempt the child
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
        )
    }
  }
}
