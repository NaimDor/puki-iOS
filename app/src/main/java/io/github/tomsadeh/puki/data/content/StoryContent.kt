package io.github.tomsadeh.puki.data.content

/**
 * The story with choices: Puki tells a little animal's story one sentence at a time, and the child picks where
 * it goes, who it meets and what it finds. The sentences sent to the brain are the trainer's, word for word
 * (train/games.py story_steps, story_hero, story_input); CareStoryContentTest checks them.
 */
object StoryContent {
    val HEROES = listOf("cow", "dog", "cat", "elephant", "lion", "rabbit", "duck", "sheep", "monkey", "giraffe", "penguin", "horse")
    val FRIENDS = listOf("dog", "cat", "duck", "sheep", "monkey", "rabbit", "penguin", "horse", "elephant", "cow")
    /** id to (Hebrew, English). */
    val PLACES = linkedMapOf("sea" to ("הים" to "the sea"), "forest" to ("היער" to "the forest"), "garden" to ("הגינה" to "the garden"),
        "farm" to ("החווה" to "the farm"), "playground" to ("הגן" to "the playground"), "hill" to ("ההר" to "the hill"))
    val THINGS = linkedMapOf("banana" to ("בננה" to "a banana"), "apple" to ("תפוח" to "an apple"), "ball" to ("כדור" to "a ball"),
        "flower" to ("פרח" to "a flower"), "star" to ("כוכב" to "a star"), "rainbow" to ("קשת" to "a rainbow"),
        "balloon" to ("בלון" to "a balloon"), "cake" to ("עוגה" to "a cake"))

    const val STEPS = 5  // the start, a place, a friend, a thing, the end

    class Plan(val hero: AnimalDef, val place: String, val friend: AnimalDef, val thing: String)

    private fun she(a: AnimalDef) = a.id in FEMININE_ANIMALS

    /** What the child picked at [step], as the brain learned it. */
    fun stepText(plan: Plan, english: Boolean, step: Int): String = if (!english) when (step) {
        0 -> "הסיפור מתחיל"
        1 -> "בחרו ללכת ${CareContent.le(PLACES.getValue(plan.place).first)}"
        2 -> "בחרו חבר: ${plan.friend.name.he}"
        3 -> "בחרו: ${THINGS.getValue(plan.thing).first}"
        else -> "הסוף, " + (if (she(plan.hero)) "הגיבורה חוזרת" else "הגיבור חוזר") + " הביתה לישון"
    } else when (step) {
        0 -> "the story begins"
        1 -> "the child picked going to ${PLACES.getValue(plan.place).second}"
        2 -> "the child picked a friend: a ${plan.friend.name.en.lowercase()}"
        3 -> "the child picked: ${THINGS.getValue(plan.thing).second}"
        else -> "the end, the hero goes home to sleep"
    }

    fun hero(plan: Plan, english: Boolean): String = if (!english)
        (if (she(plan.hero)) "הגיבורה: " else "הגיבור: ") + plan.hero.name.he + (if (she(plan.hero)) " קטנה" else " קטן")
    else "Hero: a little " + plan.hero.name.en.lowercase()

    /** The brain's input for [step]: the hero, the story so far and what was picked now. */
    fun input(plan: Plan, english: Boolean, soFar: List<String>, step: Int): String = if (!english) {
        val past = if (soFar.isEmpty()) "" else " עד עכשיו: ${soFar.joinToString(" ")}"
        "סיפור עם בחירות. ${hero(plan, false)}.$past עכשיו: ${stepText(plan, false, step)}."
    } else {
        val past = if (soFar.isEmpty()) "" else " So far: ${soFar.joinToString(" ")}"
        "Story with choices. ${hero(plan, true)}.$past Now: ${stepText(plan, true, step)}."
    }

    /** What a sentence must not name: the places, friends and things the child didn't pick (a story about the garden
     *  when she picked the farm), from the steps so far. */
    fun notPicked(plan: Plan, english: Boolean, step: Int, offered: Map<Int, List<String>>): List<String> =
        (1..minOf(step, 3)).flatMap { s ->
            offered[s].orEmpty().filter { it != when (s) { 1 -> plan.place; 2 -> plan.friend.id; else -> plan.thing } }.mapNotNull { id ->
                when (s) {
                    1 -> PLACES[id]?.let { if (english) it.second.removePrefix("the ") else it.first.removePrefix("ה") }
                    2 -> allAnimals.firstOrNull { it.id == id }?.let { if (english) it.name.en.lowercase() else it.name.he }
                    else -> THINGS[id]?.let { if (english) it.second.substringAfter(' ') else it.first }
                }
            }
        }

    /** What a step's sentence must name (the place, the friend, the thing picked), or null (the start and the end are free). */
    fun pickWord(plan: Plan, english: Boolean, step: Int): String? = when (step) {
        1 -> PLACES.getValue(plan.place).let { if (english) it.second.removePrefix("the ") else it.first.removePrefix("ה") }
        2 -> if (english) plan.friend.name.en.lowercase() else plan.friend.name.he
        3 -> THINGS.getValue(plan.thing).let { if (english) it.second.substringAfter(' ') else it.first }
        else -> null
    }
}
