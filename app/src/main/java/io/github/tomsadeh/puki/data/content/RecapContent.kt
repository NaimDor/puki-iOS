package io.github.tomsadeh.puki.data.content

import io.github.tomsadeh.puki.data.models.BilingualText

/** One thing the child did today, in the words the brain learned for the bedtime recap. */
data class DayEvent(val he: String, val en: String)

/**
 * The bedtime recap: what the child did today (DayLog), said as train/games.py day_events says it, and the
 * check a recap must pass (events_named): it names at least two of the day's things. RecapContentTest checks
 * the wording and the check against the trainer's.
 */
object RecapContent {
    private fun the(word: String) = if (word.startsWith("ה")) word else "ה$word"

    fun fed(animal: AnimalDef): DayEvent? {
        val food = level3Foods.firstOrNull { it.id == animal.foodId } ?: return null
        return DayEvent("האכלת את ${the(animal.name.he)} ב${food.name.he}", "you fed the ${animal.name.en.lowercase()} some ${food.name.en.lowercase()}")
    }

    fun found(animal: AnimalDef) = DayEvent("מצאת את ${the(animal.name.he)} במחבואים", "you found the ${animal.name.en.lowercase()} hiding")

    fun color(color: ColorDef) = DayEvent("מצאת את הצבע ${color.name.he}", "you found the color ${color.name.en.lowercase()}")

    fun shape(shape: ShapeDef) = DayEvent("שמת את ${the(shape.name.he)} במקום", "you put the ${shape.name.en.lowercase()} in its place")

    fun counted(round: CountingRound) =
        DayEvent("ספרת ${round.targetNumber} ${round.itemName.he}", "you counted ${round.targetNumber} ${round.itemName.en}")

    fun said(word: BilingualText) = DayEvent("אמרת עם פוקי: ${word.he}", "you said \"${word.en.lowercase()}\" with Puki")

    fun drew(color: ColorDef?) = if (color == null) DayEvent("ציירת ציור", "you drew a picture")
        else DayEvent("ציירת ב${color.name.he}", "you drew with ${color.name.en.lowercase()}")

    fun sticker(name: BilingualText) = DayEvent("קיבלת מדבקה של ${name.he}", "you got a ${name.en.lowercase()} sticker")

    /** The My Day games that are one thing all along, by their island (GameSession) id. */
    val FIXED = mapOf(
        "dance" to DayEvent("רקדת למוזיקה", "you danced to the music"),
        "kitchen" to DayEvent("אפית עוגה", "you baked a cake"),
        "garage" to DayEvent("שטפת את המכונית", "you washed the car"),
        "tidy" to DayEvent("סידרת את הצעצועים", "you tidied up the toys"),
        "table" to DayEvent("ערכת את השולחן", "you set the table"),
    )

    /** The recap's input for [events] (the brain learned two to five). */
    fun input(english: Boolean, events: List<DayEvent>) =
        if (english) "Bedtime recap. Today: " + events.joinToString("; ") { it.en } + "."
        else "סיכום לפני השינה. היום: " + events.joinToString("; ") { it.he } + "."

    // Words every event shares, which say nothing about which one a line names (games._COMMON)
    private val COMMON = setOf("את", "היום", "מצאת", "found", "עם", "פוקי", "במקום", "מקום", "הצבע", "צבע", "מדבקה", "של", "you", "the", "some",
        "with", "puki", "its", "place", "color", "sticker", "today", "said", "got")
    private val WORDS = Regex("[\\p{L}\\p{N}_']+")

    private fun forms(word: String): Set<String> {
        val out = mutableSetOf(word)
        for (k in 1..2) if (word.length - k >= 3 && word.take(k).all { it in "והבלמש" }) out += word.drop(k)
        return out
    }

    /** How many of [input]'s events [line] names, by a word only that event has (games.events_named). */
    fun eventsNamed(line: String, input: String): Int {
        val events = input.substringAfter(":").trimEnd('.').split(";")
        val seen = WORDS.findAll(line.lowercase()).flatMap { forms(it.value) }.toSet()
        return events.count { e ->
            WORDS.findAll(e.lowercase()).map { it.value }.filter { it !in COMMON && it.length >= 3 }.flatMap { forms(it) }.any { it in seen }
        }
    }
}
