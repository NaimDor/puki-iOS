package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

data class LetterDef(
    val id: String,
    val hebrewLetter: String,
    val englishLetter: String,
    val name: BilingualText,
    val exampleWord: BilingualText,
    @DrawableRes val exampleIcon: Int
)

val hebrewLetters = listOf(
    LetterDef("alef", "א", "A", BilingualText("אָלֶף", "Alef"), BilingualText("אריה", "Lion"), R.drawable.ic_animal_lion),
    LetterDef("bet", "ב", "B", BilingualText("בֵּית", "Bet"), BilingualText("בית", "House"), R.drawable.ic_word_house),
    LetterDef("gimel", "ג", "G", BilingualText("גִּימֶל", "Gimel"), BilingualText("גמל", "Camel"), R.drawable.ic_word_camel),
    LetterDef("dalet", "ד", "D", BilingualText("דָּלֶת", "Dalet"), BilingualText("דג", "Fish"), R.drawable.ic_food_fish),
    LetterDef("he", "ה", "H", BilingualText("הֵא", "He"), BilingualText("הר", "Mountain"), R.drawable.ic_word_mountain),
    LetterDef("vav", "ו", "V", BilingualText("וָו", "Vav"), BilingualText("ורד", "Rose"), R.drawable.ic_word_rose),
    LetterDef("het", "ח", "H", BilingualText("חֵית", "Het"), BilingualText("חתול", "Cat"), R.drawable.ic_animal_cat),
    LetterDef("tet", "ט", "T", BilingualText("טֵית", "Tet"), BilingualText("טיל", "Rocket"), R.drawable.ic_sticker_rocket),
    LetterDef("yod", "י", "Y", BilingualText("יוּד", "Yod"), BilingualText("ירח", "Moon"), R.drawable.ic_bedtime_moon),
    LetterDef("kaf", "כ", "K", BilingualText("כָּף", "Kaf"), BilingualText("כלב", "Dog"), R.drawable.ic_animal_dog),
    LetterDef("lamed", "ל", "L", BilingualText("לָמֶד", "Lamed"), BilingualText("לחם", "Bread"), R.drawable.ic_food_bread),
    LetterDef("mem", "מ", "M", BilingualText("מֵם", "Mem"), BilingualText("מכונית", "Car"), R.drawable.ic_myday_car),
    LetterDef("nun", "נ", "N", BilingualText("נוּן", "Nun"), BilingualText("נעליים", "Shoes"), R.drawable.ic_clothing_shoes),
    LetterDef("samekh", "ס", "S", BilingualText("סָמֶך", "Samekh"), BilingualText("סוס", "Horse"), R.drawable.ic_animal_horse),
    LetterDef("ayin", "ע", "A", BilingualText("עַיִן", "Ayin"), BilingualText("עז", "Goat"), R.drawable.ic_animal_goat),
    LetterDef("pe", "פ", "P", BilingualText("פֵּא", "Pe"), BilingualText("פיל", "Elephant"), R.drawable.ic_animal_elephant),
    LetterDef("qof", "ק", "Q", BilingualText("קוּף", "Qof"), BilingualText("קוף", "Monkey"), R.drawable.ic_animal_monkey),
    LetterDef("shin", "ש", "S", BilingualText("שִׁין", "Shin"), BilingualText("שמש", "Sun"), R.drawable.ic_sticker_sun),
    LetterDef("tav", "ת", "T", BilingualText("תָּו", "Tav"), BilingualText("תוף", "Drum"), R.drawable.ic_instrument_drum)
)

/** Letters easily mistaken for each other: the top level puts them side by side. */
val letterLookalikes = mapOf(
    "bet" to "kaf", "kaf" to "bet", "he" to "het", "het" to "he",
    "vav" to "yod", "yod" to "vav", "gimel" to "nun", "nun" to "gimel",
    "tet" to "samekh", "samekh" to "tet", "ayin" to "shin", "shin" to "ayin",
    "e" to "f", "f" to "e", "p" to "r", "r" to "p", "b" to "d", "d" to "b", "c" to "g", "g" to "c"
)

val englishLetters = listOf(
    LetterDef("a", "א", "A", BilingualText("אֵי", "A"), BilingualText("תפוח", "Apple"), R.drawable.ic_word_apple),
    LetterDef("b", "ב", "B", BilingualText("בִּי", "B"), BilingualText("כדור", "Ball"), R.drawable.ic_word_ball),
    LetterDef("c", "ג", "C", BilingualText("סִי", "C"), BilingualText("חתול", "Cat"), R.drawable.ic_animal_cat),
    LetterDef("d", "ד", "D", BilingualText("דִי", "D"), BilingualText("כלב", "Dog"), R.drawable.ic_animal_dog),
    LetterDef("e", "ה", "E", BilingualText("אִי", "E"), BilingualText("פיל", "Elephant"), R.drawable.ic_animal_elephant),
    LetterDef("f", "ו", "F", BilingualText("אֶף", "F"), BilingualText("דג", "Fish"), R.drawable.ic_food_fish),
    LetterDef("g", "ז", "G", BilingualText("גִ׳י", "G"), BilingualText("עז", "Goat"), R.drawable.ic_animal_goat),
    LetterDef("h", "ח", "H", BilingualText("אֵייץ׳", "H"), BilingualText("סוס", "Horse"), R.drawable.ic_animal_horse),
    LetterDef("l", "ט", "L", BilingualText("אֶל", "L"), BilingualText("אריה", "Lion"), R.drawable.ic_animal_lion),
    LetterDef("m", "י", "M", BilingualText("אֶם", "M"), BilingualText("ירח", "Moon"), R.drawable.ic_bedtime_moon),
    LetterDef("p", "כ", "P", BilingualText("פִּי", "P"), BilingualText("חזיר", "Pig"), R.drawable.ic_animal_pig),
    LetterDef("r", "ל", "R", BilingualText("אַר", "R"), BilingualText("ארנב", "Rabbit"), R.drawable.ic_animal_rabbit),
    LetterDef("s", "מ", "S", BilingualText("אֶס", "S"), BilingualText("שמש", "Sun"), R.drawable.ic_sticker_sun),
    LetterDef("t", "נ", "T", BilingualText("טִי", "T"), BilingualText("דובי", "Teddy"), R.drawable.ic_tidy_teddy)
)
