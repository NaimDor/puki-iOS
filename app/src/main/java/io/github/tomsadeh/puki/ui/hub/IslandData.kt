package io.github.tomsadeh.puki.ui.hub

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.navigation.Routes
import io.github.tomsadeh.puki.theme.CoralPink
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.theme.GrassGreen
import io.github.tomsadeh.puki.theme.SoftPurple
import io.github.tomsadeh.puki.theme.SunYellow
import io.github.tomsadeh.puki.theme.WarmOrange

data class IslandDef(
    val id: String,
    val name: BilingualText,
    val route: String,
    val color: Color,
    val emoji: String,
    @DrawableRes val drawableRes: Int = 0,
    val fractionalX: Float,
    val fractionalY: Float
)

val allIslands = listOf(
    IslandDef(
        id = "animals",
        name = BilingualText("גן החיות", "Animal Garden"),
        route = Routes.ANIMALS,
        color = GrassGreen,
        emoji = "\uD83D\uDC04",
        drawableRes = R.drawable.ic_island_animals,
        fractionalX = 0.14f,
        fractionalY = 0.50f
    ),
    IslandDef(
        id = "colors",
        name = BilingualText("צבעים", "Color Splash"),
        route = Routes.COLORS,
        color = CoralPink,
        emoji = "\uD83C\uDFA8",
        drawableRes = R.drawable.ic_island_colors,
        fractionalX = 0.33f,
        fractionalY = 0.42f
    ),
    IslandDef(
        id = "shapes",
        name = BilingualText("צורות", "Shapes"),
        route = Routes.SHAPES,
        color = SoftPurple,
        emoji = "\uD83D\uDD37",
        drawableRes = R.drawable.ic_island_shapes,
        fractionalX = 0.52f,
        fractionalY = 0.53f
    ),
    IslandDef(
        id = "counting",
        name = BilingualText("ספירה", "Counting"),
        route = Routes.COUNTING,
        color = SunYellow,
        emoji = "\uD83C\uDF3B",
        drawableRes = R.drawable.ic_island_counting,
        fractionalX = 0.71f,
        fractionalY = 0.43f
    ),
    IslandDef(
        id = "peekaboo",
        name = BilingualText("מחבואים", "Peekaboo"),
        route = Routes.PEEKABOO,
        color = Color(0xFFFFCC80),
        emoji = "👀",
        drawableRes = R.drawable.ic_island_peekaboo,
        fractionalX = 0.88f,
        fractionalY = 0.37f
    ),
    IslandDef(
        id = "music",
        name = BilingualText("מוזיקה", "Music"),
        route = Routes.MUSIC,
        color = WarmOrange,
        emoji = "\uD83C\uDFB5",
        drawableRes = R.drawable.ic_island_music,
        fractionalX = 0.91f,
        fractionalY = 0.635f
    ),
    IslandDef(
        id = "words",
        name = BilingualText("מילים", "Words"),
        route = Routes.WORDS,
        color = Color(0xFF9FA8DA),
        emoji = "\uD83D\uDCD6",
        drawableRes = R.drawable.ic_island_words,
        fractionalX = 0.77f,
        fractionalY = 0.81f
    ),
    // The bottom row, right to left in the order the sea route visits it
    IslandDef(
        id = "echo",
        name = BilingualText("תוכי מחקה", "Echo Puki"),
        route = Routes.ECHO,
        color = Color(0xFFCE93D8),
        emoji = "\uD83E\uDD9C",
        drawableRes = R.drawable.ic_island_echo,
        fractionalX = 0.58f,
        fractionalY = 0.80f
    ),
    IslandDef(
        id = "myday",
        name = BilingualText("היום שלי", "My Day"),
        route = Routes.MY_DAY,
        color = EarthBrown,
        emoji = "\uD83C\uDFE0",
        drawableRes = R.drawable.ic_island_myday,
        fractionalX = 0.385f,
        fractionalY = 0.80f
    ),
    IslandDef(
        id = "say",
        name = BilingualText("מדברים עם פוקי", "Say it with Puki"),
        route = Routes.SAY,
        color = Color(0xFF81D4FA),
        emoji = "\uD83E\uDD9C",
        drawableRes = R.drawable.ic_companion_parrot_talk,
        fractionalX = 0.205f,  // (clear of the Drawing button in the corner)
        fractionalY = 0.80f
    )
)
