package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

/** Where a round's things live. */
enum class Habitat { BED, TREE, POND, NIGHT, SKY }

data class CountingRound(
    val targetNumber: Int,
    val itemName: BilingualText,
    @DrawableRes val itemIcon: Int,
    val habitat: Habitat
)

val level1CountingRounds = listOf(
    CountingRound(1, BilingualText("פרח", "flower"), R.drawable.ic_sticker_flower, Habitat.BED),
    CountingRound(2, BilingualText("פרפרים", "butterflies"), R.drawable.ic_sticker_butterfly, Habitat.BED),
    CountingRound(3, BilingualText("חיפושיות", "ladybugs"), R.drawable.ic_stamp_ladybug, Habitat.BED),
    CountingRound(4, BilingualText("פרחים", "flowers"), R.drawable.ic_count_hibiscus, Habitat.BED),
    // The same numbers with other things, so level 1 isn't the same four rounds
    CountingRound(1, BilingualText("תפוח", "apple"), R.drawable.ic_count_apple, Habitat.TREE),
    CountingRound(1, BilingualText("כוכב", "star"), R.drawable.ic_sticker_star, Habitat.NIGHT),
    CountingRound(1, BilingualText("דג", "fish"), R.drawable.ic_food_fish, Habitat.POND),
    CountingRound(2, BilingualText("תפוחים", "apples"), R.drawable.ic_count_apple_green, Habitat.TREE),
    CountingRound(2, BilingualText("ציפורים", "birds"), R.drawable.ic_count_bird, Habitat.SKY),
    CountingRound(3, BilingualText("דגים", "fish"), R.drawable.ic_food_fish, Habitat.POND),
    CountingRound(3, BilingualText("לבבות", "hearts"), R.drawable.ic_sticker_heart, Habitat.SKY),
    CountingRound(4, BilingualText("כוכבים", "stars"), R.drawable.ic_sticker_star, Habitat.NIGHT),
    CountingRound(4, BilingualText("פרפרים", "butterflies"), R.drawable.ic_sticker_butterfly, Habitat.BED)
)

/** The most carrots the rabbit asks for. */
const val MOST_TO_GIVE = 5

/** A hungry rabbit asks for [wanted] carrots. */
fun giveRound(wanted: Int) = CountingRound(wanted.coerceIn(1, MOST_TO_GIVE), BilingualText("גזרים", "carrots"), R.drawable.ic_food_carrot, Habitat.BED)

val level2CountingRounds = listOf(
    CountingRound(2, BilingualText("תפוחים", "apples"), R.drawable.ic_count_apple, Habitat.TREE),
    CountingRound(3, BilingualText("כוכבים", "stars"), R.drawable.ic_sticker_star, Habitat.NIGHT),
    CountingRound(4, BilingualText("לבבות", "hearts"), R.drawable.ic_sticker_heart, Habitat.SKY),
    CountingRound(5, BilingualText("דגים", "fish"), R.drawable.ic_food_fish, Habitat.POND)
)

val level3CountingRounds = listOf(
    CountingRound(5, BilingualText("ציפורים", "birds"), R.drawable.ic_count_bird, Habitat.SKY),
    CountingRound(6, BilingualText("פרפרים", "butterflies"), R.drawable.ic_sticker_butterfly, Habitat.BED),
    CountingRound(7, BilingualText("פרחים", "flowers"), R.drawable.ic_sticker_flower, Habitat.BED),
    CountingRound(8, BilingualText("כוכבים", "stars"), R.drawable.ic_sticker_star, Habitat.NIGHT),
    CountingRound(9, BilingualText("לבבות", "hearts"), R.drawable.ic_count_heart_purple, Habitat.SKY),
    CountingRound(10, BilingualText("תפוחים", "apples"), R.drawable.ic_count_apple_green, Habitat.TREE)
)

fun countingRoundsForLevel(level: Int): List<CountingRound> = when {
    level >= 3 -> level3CountingRounds
    level >= 2 -> level2CountingRounds
    else -> level1CountingRounds
}


/** The words that count as saying [n] out loud (with the digit, as Whisper may write it). */
fun numberWords(n: Int, english: Boolean): List<String> {
    val words = if (english) NUMBERS_EN.getOrNull(n - 1).orEmpty() else NUMBERS_HE.getOrNull(n - 1).orEmpty()
    return listOf("$n") + words
}

private val NUMBERS_HE = listOf(
    listOf("אחת", "אחד"), listOf("שתיים", "שתים", "שניים", "שנים"), listOf("שלוש", "שלושה"),
    listOf("ארבע", "ארבעה"), listOf("חמש", "חמישה", "חמשה"), listOf("שש", "שישה", "ששה"),
    listOf("שבע", "שבעה"), listOf("שמונה"), listOf("תשע", "תשעה"), listOf("עשר", "עשרה")
)
private val NUMBERS_EN = listOf(
    listOf("one"), listOf("two"), listOf("three"), listOf("four"), listOf("five"),
    listOf("six"), listOf("seven"), listOf("eight"), listOf("nine"), listOf("ten")
)
