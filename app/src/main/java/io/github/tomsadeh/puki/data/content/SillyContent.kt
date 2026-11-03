package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import io.github.tomsadeh.puki.R

/**
 * Silly Puki: Puki says something wrong about a picture on purpose, the child puts him right, and he laughs
 * and says the real answer. The brain was trained on these exact sentences (train/games.py silly_inputs and
 * silly_fix_input, built from this app's content), so they're built here word for word; SillyContentTest
 * checks them against the trainer's.
 */
enum class SillyKind { SOUND, FOOD, COLOR, SHAPE, COUNT }

/** What the picture shows: an animal, a thing of one color, a shape, or some things to count. */
sealed interface SillyPicture {
    data class Animal(val animal: AnimalDef) : SillyPicture
    data class Thing(@DrawableRes val res: Int) : SillyPicture
    data class Shape(val shape: ShapeDef) : SillyPicture
    data class Count(val round: CountingRound) : SillyPicture
}

data class SillyItem(
    val group: String,
    val kind: SillyKind,
    /** The brain's input, in Hebrew and English. */
    val he: String,
    val en: String,
    /** The real answer (a sound, a food, a color, a shape, a number). */
    val truthHe: String,
    val truthEn: String,
    /** Null when the app has no picture for it (the brain knows it; the game can't show it). */
    val picture: SillyPicture?,
    /** The recorded line that names the picture, and the one that says the real answer. */
    val nameKey: String?,
    val truthKey: String,
    /** The real answer's id, for the tap choices (an animal for a sound, else the food, color, shape or number). */
    val answerId: String,
)

object SillyContent {
    /** Animal sounds a 2-year-old knows in both languages (games.SOUNDS). */
    val SOUNDS = mapOf("cow" to ("מו" to "moo"), "dog" to ("הב הב" to "woof"), "cat" to ("מיאו" to "meow"), "sheep" to ("מה" to "baa"),
        "duck" to ("גע גע" to "quack"), "pig" to ("אוינק" to "oink"), "horse" to ("היהי" to "neigh"))

    /** Things of one color everyone knows (games.COLORED): Hebrew, English, feminine, the color; and the picture, if the app has one. */
    private class Colored(val he: String, val en: String, val she: Boolean, val color: String, @DrawableRes val res: Int?, val nameKey: String?)
    private val COLORED = listOf(
        Colored("בננה", "banana", true, "yellow", R.drawable.ic_food_banana, "voice_food_banana"),
        Colored("תות", "strawberry", false, "red", null, null),
        Colored("עגבנייה", "tomato", true, "red", null, null),
        Colored("דשא", "grass", false, "green", R.drawable.ic_food_grass, "voice_food_grass"),
        Colored("מלפפון", "cucumber", false, "green", null, null),
        Colored("שמיים", "sky", false, "blue", null, null),
        Colored("שמש", "sun", true, "yellow", R.drawable.ic_sticker_sun, null),
        Colored("גזר", "carrot", false, "orange", R.drawable.ic_food_carrot, "voice_food_carrot"),
        Colored("תפוז", "orange", false, "orange", null, null),
        Colored("שלג", "snow", false, "white", null, null),
        Colored("חלב", "milk", false, "white", null, null),
        Colored("שוקולד", "chocolate", false, "brown", null, null),
        Colored("חציל", "eggplant", false, "purple", null, null),
    )
    private val THE = setOf("sky", "sun", "grass", "snow", "milk")
    private val PLAIN_SHAPES = listOf("circle", "square", "triangle", "star", "heart")

    /** A number as Puki may say it (games.NUMBER_WORDS): the digit, or the word, Hebrew masculine and feminine. */
    val NUMBER_WORDS: Map<String, List<String>> = listOf(
        emptyList(), listOf("אחד", "אחת", "one"), listOf("שניים", "שתיים", "שני", "שתי", "two"), listOf("שלושה", "שלוש", "three"),
        listOf("ארבעה", "ארבע", "four"), listOf("חמישה", "חמש", "five"), listOf("שישה", "שש", "six"), listOf("שבעה", "שבע", "seven"),
        listOf("שמונה", "eight"), listOf("תשעה", "תשע", "nine"), listOf("עשרה", "עשר", "ten"),
    ).withIndex().associate { (n, w) -> n.toString() to w }

    private fun a(word: String) = (if (word[0] in "aeiou") "an " else "a ") + word

    private fun item(group: String, kind: SillyKind, picHe: String, picEn: String, she: Boolean, aboutHe: Pair<String, String>, aboutEn: String,
                     truthHe: String, truthEn: String, picture: SillyPicture?, nameKey: String?, truthKey: String, answerId: String) =
        SillyItem(group, kind,
            "בתמונה: $picHe. תגיד טעות מצחיקה אחת בכוונה, על ${if (she) aboutHe.second else aboutHe.first} (באמת: $truthHe).",
            "Picture: $picEn. Make one silly mistake on purpose, about its $aboutEn (really: $truthEn).",
            truthHe, truthEn, picture, nameKey, truthKey, answerId)

    /** Every picture and mistake the brain was trained on; the game plays the ones with a picture. */
    val all: List<SillyItem> by lazy {
        val foods = level3Foods.associateBy { it.id }
        val colors = level3Colors.associateBy { it.id }
        val out = mutableListOf<SillyItem>()
        for (an in allAnimals.distinctBy { it.id }) {
            val she = an.id in FEMININE_ANIMALS
            SOUNDS[an.id]?.let { (he, en) ->
                out += item("sound:${an.id}", SillyKind.SOUND, an.name.he, a(an.name.en.lowercase()), she, "הקול שלו" to "הקול שלה", "sound",
                    he, en, SillyPicture.Animal(an), "voice_animal_${an.id}", "voice_sound_${an.id}", an.id)
            }
            foods[an.foodId]?.let { f ->
                out += item("food:${an.id}", SillyKind.FOOD, an.name.he, a(an.name.en.lowercase()), she, "מה שהוא אוכל" to "מה שהיא אוכלת", "food",
                    f.name.he, f.name.en.lowercase(), SillyPicture.Animal(an), "voice_animal_${an.id}", "voice_food_${f.id}", f.id)
            }
        }
        for (c in COLORED) {
            val col = colors.getValue(c.color)
            out += item("color:${c.en}", SillyKind.COLOR, c.he, if (c.en in THE) "the ${c.en}" else a(c.en), c.she, "הצבע שלו" to "הצבע שלה", "color",
                col.name.he, col.name.en.lowercase(), c.res?.let { SillyPicture.Thing(it) }, c.nameKey, "voice_color_${col.id}", col.id)
        }
        for (s in level3Shapes.filter { it.id in PLAIN_SHAPES }) {
            out += item("shape:${s.id}", SillyKind.SHAPE, s.name.he, a(s.name.en.lowercase()), false, "הצורה שלו" to "הצורה שלו", "shape",
                s.name.he, s.name.en.lowercase(), SillyPicture.Shape(s), "voice_shape_${s.id}", "voice_shape_${s.id}", s.id)
        }
        for (r in (level1CountingRounds + level2CountingRounds + level3CountingRounds).filter { it.targetNumber > 1 }) {
            val n = r.targetNumber
            val group = "count:$n:${r.itemName.en}"
            if (out.none { it.group == group }) out += SillyItem(group, SillyKind.COUNT,
                "בתמונה: $n ${r.itemName.he}. תגיד טעות מצחיקה אחת בכוונה, בכמה יש (באמת: $n).",
                "Picture: $n ${r.itemName.en}. Make one silly mistake on purpose, about how many (really: $n).",
                "$n", "$n", SillyPicture.Count(r), null, "voice_number_$n", "$n")
        }
        out
    }

    /** The ones the game can show. */
    val playable get() = all.filter { it.picture != null }

    enum class Answer { RIGHT, OTHER, NOTHING }

    /**
     * The input for Puki's next line (silly_fix): his silly [line], the real answer, and what the child did:
     * said or tapped the real answer, said something else ([heard]), or nothing.
     */
    fun fixInput(english: Boolean, line: String, truth: String, answer: Answer, heard: String = ""): String {
        val said = heard.trim().split(Regex("\\s+")).take(4).joinToString(" ").ifEmpty { "…" }
        return if (!english) "פוקי אמר בצחוק: \"$line\" (באמת: $truth). " + when (answer) {
            Answer.RIGHT -> "ענו לו: \"$truth!\". זה נכון!"
            Answer.OTHER -> "ענו לו: \"$said\"."
            Answer.NOTHING -> "ענו לו: כלום, רק צחקו."
        } else "Puki said as a joke: \"$line\" (really: $truth). " + when (answer) {
            Answer.RIGHT -> "The child answered: \"$truth!\". That's right!"
            Answer.OTHER -> "The child answered: \"$said\"."
            Answer.NOTHING -> "The child answered: nothing, just laughed."
        }
    }

    private val WORDS = Regex("[\\p{L}\\p{N}_']+")  // (Python's \w: letters, digits and _, in any script)

    /** Whether [line] says the real answer (build.said_truth): as a whole word or phrase, a number also as a word,
     *  a Hebrew word also behind a one-letter prefix (ו, ה, ב, ל, מ, ש). */
    fun saysTruth(line: String, truth: String): Boolean {
        val words = WORDS.findAll(line.lowercase()).map { it.value }.toList()
        for (alt in listOf(truth) + (NUMBER_WORDS[truth] ?: emptyList())) {
            val t = WORDS.findAll(alt.lowercase()).map { it.value }.toList()
            if (t.isEmpty()) continue
            for (i in words.indices) {
                val span = words.subList(i, minOf(i + t.size, words.size))
                if (span == t || span.map { w -> w.trimStart { it in "והבלמש" } } == t) return true
            }
        }
        return false
    }
}
