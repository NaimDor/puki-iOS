package io.github.tomsadeh.puki.navigation

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.hub.HubScreen
import io.github.tomsadeh.puki.ui.animals.AnimalGardenScreen
import io.github.tomsadeh.puki.ui.colors.ColorSplashScreen
import io.github.tomsadeh.puki.ui.shapes.ShapePlaygroundScreen
import io.github.tomsadeh.puki.ui.counting.CountingGardenScreen
import io.github.tomsadeh.puki.ui.myday.kitchen.CakeScreen
import io.github.tomsadeh.puki.ui.music.DanceScreen
import io.github.tomsadeh.puki.ui.myday.garage.CarWashScreen
import io.github.tomsadeh.puki.ui.music.MusicMeadowScreen
import io.github.tomsadeh.puki.ui.words.WordGardenScreen
import io.github.tomsadeh.puki.ui.echo.EchoScreen
import io.github.tomsadeh.puki.ui.peekaboo.PeekabooScreen
import io.github.tomsadeh.puki.ui.say.SayItScreen
import io.github.tomsadeh.puki.ui.myday.MyDayScreen
import io.github.tomsadeh.puki.ui.myday.bath.BathTimeScreen
import io.github.tomsadeh.puki.ui.myday.brush.BrushyBrushyScreen
import io.github.tomsadeh.puki.ui.myday.tidy.TidyUpScreen
import io.github.tomsadeh.puki.ui.myday.dress.GettingDressedScreen
import io.github.tomsadeh.puki.ui.myday.table.TableTimeScreen
import io.github.tomsadeh.puki.ui.myday.bedtime.BedtimeRoutineScreen
import io.github.tomsadeh.puki.ui.canvas.FreeCanvasScreen
import io.github.tomsadeh.puki.ui.stickers.StickerAlbumScreen
import io.github.tomsadeh.puki.ui.settings.SettingsScreen

object Routes {
    const val HUB = "hub"
    const val ANIMALS = "animals"
    const val COLORS = "colors"
    const val SHAPES = "shapes"
    const val COUNTING = "counting"
    const val MUSIC = "music"
    const val DANCE = "dance"
    const val WORDS = "words"
    const val SAY = "say"
    const val PEEKABOO = "peekaboo"
    const val ECHO = "echo"
    const val MY_DAY = "myday"
    const val MY_DAY_BATH = "myday/bath"
    const val MY_DAY_BRUSH = "myday/brush"
    const val MY_DAY_TIDY = "myday/tidy"
    const val MY_DAY_DRESS = "myday/dress"
    const val MY_DAY_TABLE = "myday/table"
    const val MY_DAY_BEDTIME = "myday/bedtime"
    const val MY_DAY_KITCHEN = "myday/kitchen"
    const val MY_DAY_GARAGE = "myday/garage"
    const val CANVAS = "canvas"
    const val STICKERS = "stickers"
    const val SETTINGS = "settings"
}

private const val NAV_WINDOW_MS = 400L

@Composable
fun PukiNavGraph(navController: NavHostController) {
    val sfxPlayer = LocalSfxPlayer.current
    // One navigation at a time: a toddler's double tap lands on the next screen, so a second tap
    // inside this window (a back that would skip a level, say) is ignored
    val lastNavAt = remember { longArrayOf(0L) }
    val settled: () -> Boolean = {
        val now = SystemClock.uptimeMillis()
        (now - lastNavAt[0] >= NAV_WINDOW_MS).also { if (it) lastNavAt[0] = now }
    }

    // Safe back navigation — prevents double-tap from popping past the hub
    val safeBack: () -> Unit = {
        if (navController.currentBackStackEntry?.destination?.route != Routes.HUB && settled()) {
            sfxPlayer.play(Sfx.POP)
            navController.popBackStack()
        }
    }

    // Safe navigate — prevents duplicate destinations from rapid tapping
    val safeNavigate: (String) -> Unit = { route ->
        if (navController.currentBackStackEntry?.destination?.route != route && settled()) {
            sfxPlayer.play(Sfx.WHOOSH, 0.4f)
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    // "Continue" on a game's end card: the same game again, as a fresh screen (so a fresh game)
    val playAgain: (String) -> () -> Unit = { route ->
        {
            if (navController.currentBackStackEntry?.destination?.route == route) {
                navController.navigate(route) { popUpTo(route) { inclusive = true } }
            }
        }
    }

    // Puki's brain hears which game she's in (safety word lists allow a game its own words)
    val companion = io.github.tomsadeh.puki.ui.common.LocalCompanion.current
    androidx.compose.runtime.LaunchedEffect(navController, companion) {
        navController.currentBackStackEntryFlow.collect { companion?.game = it.destination.route ?: "" }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HUB,
        // Quick, and with one live screen at a time (the default is a 700 ms crossfade of both)
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { fadeIn(tween(150)) },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Routes.HUB) {
            HubScreen(
                onIslandClick = safeNavigate,
                onSettingsClick = { safeNavigate(Routes.SETTINGS) },
                onStickersClick = { safeNavigate(Routes.STICKERS) }
            )
        }
        composable(Routes.ANIMALS) {
            AnimalGardenScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.ANIMALS))
        }
        composable(Routes.COLORS) {
            ColorSplashScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.COLORS))
        }
        composable(Routes.SHAPES) {
            ShapePlaygroundScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.SHAPES))
        }
        composable(Routes.COUNTING) {
            CountingGardenScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.COUNTING))
        }
        composable(Routes.MUSIC) {
            MusicMeadowScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MUSIC), onDance = { safeNavigate(Routes.DANCE) })
        }
        composable(Routes.DANCE) {
            DanceScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.DANCE))
        }
        composable(Routes.WORDS) {
            WordGardenScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.WORDS))
        }
        composable(Routes.SAY) {
            SayItScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.SAY))
        }
        composable(Routes.PEEKABOO) {
            PeekabooScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.PEEKABOO))
        }
        composable(Routes.ECHO) {
            EchoScreen(onBack = safeBack)
        }
        composable(Routes.MY_DAY) {
            MyDayScreen(
                onRoomClick = safeNavigate,
                onBack = safeBack
            )
        }
        composable(Routes.MY_DAY_BATH) {
            BathTimeScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_BATH))
        }
        composable(Routes.MY_DAY_BRUSH) {
            BrushyBrushyScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_BRUSH))
        }
        composable(Routes.MY_DAY_TIDY) {
            TidyUpScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_TIDY))
        }
        composable(Routes.MY_DAY_DRESS) {
            GettingDressedScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_DRESS))
        }
        composable(Routes.MY_DAY_TABLE) {
            TableTimeScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_TABLE))
        }
        composable(Routes.MY_DAY_KITCHEN) {
            CakeScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_KITCHEN))
        }
        composable(Routes.MY_DAY_GARAGE) {
            CarWashScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_GARAGE))
        }
        composable(Routes.MY_DAY_BEDTIME) {
            BedtimeRoutineScreen(onBack = safeBack, onPlayAgain = playAgain(Routes.MY_DAY_BEDTIME))
        }
        composable(Routes.CANVAS) {
            FreeCanvasScreen(onBack = safeBack)
        }
        composable(Routes.STICKERS) {
            StickerAlbumScreen(onBack = safeBack)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = safeBack)
        }
    }

    // The system back gesture goes the same way; on the hub it does nothing (only the Quit hold leaves)
    BackHandler { safeBack() }
}
