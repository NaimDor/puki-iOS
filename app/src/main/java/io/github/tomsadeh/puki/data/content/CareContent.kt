package io.github.tomsadeh.puki.data.content

/**
 * The caring game: someone needs a little help (Puki himself, the teddy, the doll, the bunny), the child gives
 * them something, and it helps or it doesn't yet. The sentences are the brain's, word for word
 * (train/games.py care_inputs); CareStoryContentTest checks them against the trainer's.
 */
object CareContent {
    /** Who needs help: Hebrew and English names (null for Puki himself), and the Hebrew gender. */
    class Patient(val id: String, val he: String?, val en: String?, val feminine: Boolean = false)
    val PATIENTS = listOf(Patient("puki", null, null), Patient("teddy", "דובי", "the teddy bear"),
        Patient("doll", "הבובה", "the doll", feminine = true), Patient("bunny", "הארנב", "the bunny"))

    /** What's wrong, for a him, a her and Puki himself ({le}: "to X", {X}: X), and what helps. */
    class Problem(val id: String, val heM: String, val heF: String, val heYou: String, val en: String, val enYou: String, val helps: Set<String>)
    val PROBLEMS = listOf(
        Problem("cold", "{le} קר", "{le} קר", "לך קר", "{X} is cold", "you are cold", setOf("blanket", "hug", "soup", "hat")),
        Problem("sneezy", "{X} מתעטש", "{X} מתעטשת", "אתה מתעטש", "{X} keeps sneezing", "you keep sneezing", setOf("tissue", "soup")),
        Problem("bump", "{le} יש מכה ברגל", "{le} יש מכה ברגל", "לך יש מכה ברגל", "{X} has a bump on the leg", "you have a bump on your leg", setOf("bandage", "kiss", "hug")),
        Problem("thunder", "{X} מפחד מהרעם", "{X} מפחדת מהרעם", "אתה מפחד מהרעם", "{X} is scared of the thunder", "you are scared of the thunder", setOf("hug", "song", "blanket", "teddy")),
        Problem("dark", "{X} מפחד מהחושך", "{X} מפחדת מהחושך", "אתה מפחד מהחושך", "{X} is scared of the dark", "you are scared of the dark", setOf("nightlight", "hug", "teddy")),
        Problem("thirsty", "{X} צמא", "{X} צמאה", "אתה צמא", "{X} is thirsty", "you are thirsty", setOf("water")),
        Problem("hungry", "{X} רעב", "{X} רעבה", "אתה רעב", "{X} is hungry", "you are hungry", setOf("soup", "apple")),
        Problem("sleepy", "{X} עייף", "{X} עייפה", "אתה עייף", "{X} is sleepy", "you are sleepy", setOf("blanket", "song", "pillow")),
        Problem("play", "{X} רוצה לשחק", "{X} רוצה לשחק", "אתה רוצה לשחק", "{X} wants to play", "you want to play", setOf("ball", "song", "book")),
        Problem("hot", "{le} חם מהשמש", "{le} חם מהשמש", "לך חם מהשמש", "{X} is hot from the sun", "you are hot from the sun", setOf("hat", "water")),
        Problem("sad", "{X} עצוב", "{X} עצובה", "אתה עצוב", "{X} is sad", "you are sad", setOf("hug", "kiss", "song", "ball")),
    )

    /** The gifts: Hebrew, English. */
    val ITEMS = linkedMapOf("blanket" to ("שמיכה" to "a blanket"), "hug" to ("חיבוק" to "a hug"), "bandage" to ("פלסטר" to "a bandage"),
        "soup" to ("מרק חם" to "warm soup"), "hat" to ("כובע" to "a hat"), "tissue" to ("טישו" to "a tissue"),
        "kiss" to ("נשיקה" to "a kiss"), "song" to ("שיר" to "a song"), "teddy" to ("דובי" to "a teddy bear"),
        "nightlight" to ("מנורת לילה" to "a night light"), "water" to ("מים" to "water"), "apple" to ("תפוח" to "an apple"),
        "pillow" to ("כרית" to "a pillow"), "ball" to ("כדור" to "a ball"), "book" to ("ספר" to "a book"))

    /** Hebrew "to the ...": ל and the word, folding the article (הבובה: לבובה). */
    fun le(word: String) = "ל" + (if (word.startsWith("ה")) word.drop(1) else word)

    /** Whether [item] helps [patient] with [problem] (the teddy doesn't comfort himself). */
    fun helps(patient: Patient, problem: Problem, item: String) = item in problem.helps && !(patient.id == "teddy" && item == "teddy")

    enum class Outcome { START, HELPS, NOT_YET }

    /** The brain's input: the problem, and nothing given yet ([item] null), or the gift and whether it helped. */
    fun input(english: Boolean, patient: Patient, problem: Problem, item: String?): String {
        val he: String
        val en: String
        val gaveHe: String
        val betterHe: String
        val betterEn: String
        val toEn: String
        if (patient.he != null && patient.en != null) {
            he = (if (patient.feminine) problem.heF else problem.heM).replace("{X}", patient.he).replace("{le}", le(patient.he))
            en = problem.en.replace("{X}", patient.en)
            gaveHe = "נתנו ${le(patient.he)}"
            betterHe = if (patient.feminine) "עכשיו היא מרגישה טוב יותר" else "עכשיו הוא מרגיש טוב יותר"
            betterEn = if (patient.feminine) "now she feels better" else "now he feels better"
            toEn = if (patient.feminine) "her" else "him"
        } else {
            he = problem.heYou
            en = problem.enYou
            gaveHe = "נתנו לך"; betterHe = "עכשיו אתה מרגיש טוב יותר"; betterEn = "now you feel better"; toEn = "you"
        }
        val enCap = en.replaceFirstChar { it.uppercase() }
        if (item == null) return if (english) "Caring game: $enCap. Nothing given yet." else "משחק הטיפול: $he. עוד לא נתנו כלום."
        val (ih, ie) = ITEMS.getValue(item)
        return if (helps(patient, problem, item)) {
            if (english) "Caring game: $enCap. The child gave $toEn: $ie. That helps, $betterEn." else "משחק הטיפול: $he. $gaveHe: $ih. זה עוזר, $betterHe."
        } else {
            if (english) "Caring game: $enCap. The child gave $toEn: $ie. That's nice, but it doesn't help yet."
            else "משחק הטיפול: $he. $gaveHe: $ih. זה נחמד, אבל זה עוד לא עוזר."
        }
    }
}
