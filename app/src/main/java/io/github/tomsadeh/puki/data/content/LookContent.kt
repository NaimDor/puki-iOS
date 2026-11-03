package io.github.tomsadeh.puki.data.content

/**
 * Puki's eyes on a drawing: what the drawing studio knows about it (the page, the paints, the stickers, how
 * much is colored, the kind of strokes), said as the brain was trained to read it (train/games.py look_inputs),
 * and the check its line must pass (look_ok): it names something that's there, and nothing that isn't. So
 * Puki never guesses what a scribble shows. LookContentTest checks both against the trainer's.
 */
data class LookFacts(
    /** "blank", a coloring page's id ("fish", "flower", "house", "car") or "trace". */
    val page: String,
    /** The letter on a page to trace (Hebrew, English). */
    val letter: Pair<String, String>? = null,
    /** The paints used, the most used first (LookContent.COLORS ids). */
    val colors: List<String>,
    /** The stickers and how many of each (LookContent.STAMPS ids). */
    val stamps: List<Pair<String, Int>>,
    /** How much of the page is colored: 0 a little, 1 half, 2 almost all. */
    val cover: Int,
    /** The kind of strokes (LookContent.STROKES), or null. */
    val strokes: Int?,
)

data class LookInput(val he: String, val en: String, val presentHe: List<String>, val presentEn: List<String>,
                     val absentHe: List<String>, val absentEn: List<String>)

object LookContent {
    /** id to (Hebrew forms, English). */
    val COLORS = linkedMapOf(
        "red" to (listOf("אדום", "אדומה", "אדומים", "אדומות") to "red"), "blue" to (listOf("כחול", "כחולה", "כחולים", "כחולות") to "blue"),
        "yellow" to (listOf("צהוב", "צהובה", "צהובים", "צהובות") to "yellow"), "green" to (listOf("ירוק", "ירוקה", "ירוקים", "ירוקות") to "green"),
        "purple" to (listOf("סגול", "סגולה", "סגולים", "סגולות") to "purple"), "orange" to (listOf("כתום", "כתומה", "כתומים", "כתומות") to "orange"),
        "pink" to (listOf("ורוד", "ורודה", "ורודים", "ורודות") to "pink"), "brown" to (listOf("חום", "חומה", "חומים", "חומות") to "brown"),
        "black" to (listOf("שחור", "שחורה", "שחורים", "שחורות") to "black"),
    )

    class Stamp(val one: String, val many: String, val feminine: Boolean, val oneEn: String, val manyEn: String)
    /** In the drawing studio's order (stampIcons). */
    val STAMPS = linkedMapOf(
        "star" to Stamp("כוכב", "כוכבים", false, "star", "stars"), "heart" to Stamp("לב", "לבבות", false, "heart", "hearts"),
        "flower" to Stamp("פרח", "פרחים", false, "flower", "flowers"), "butterfly" to Stamp("פרפר", "פרפרים", false, "butterfly", "butterflies"),
        "ladybug" to Stamp("פרת משה רבנו", "פרות משה רבנו", true, "ladybug", "ladybugs"), "apple" to Stamp("תפוח", "תפוחים", false, "apple", "apples"),
        "rainbow" to Stamp("קשת", "קשתות", true, "rainbow", "rainbows"), "sun" to Stamp("שמש", "שמשות", true, "sun", "suns"),
        "moon" to Stamp("ירח", "ירחים", false, "moon", "moons"), "unicorn" to Stamp("חד-קרן", "חדי-קרן", false, "unicorn", "unicorns"),
        "bird" to Stamp("ציפור", "ציפורים", true, "bird", "birds"), "rocket" to Stamp("טיל", "טילים", false, "rocket", "rockets"),
    )

    /** id to (Hebrew, English, the things on it a line may name: Hebrew, English). */
    private val PAGES = linkedMapOf(
        "blank" to Triple("דף ריק", "blank paper", emptyList()),
        "fish" to Triple("דף צביעה של דג", "a coloring page with a fish", listOf("דג", "fish")),
        "flower" to Triple("דף צביעה של פרח", "a coloring page with a flower", listOf("פרח", "flower")),
        "house" to Triple("דף צביעה של בית", "a coloring page with a house", listOf("בית", "house")),
        "car" to Triple("דף צביעה של מכונית", "a coloring page with a car", listOf("מכונית", "car")),
    )

    /** (Hebrew, English, the words that name it). */
    val COVER = listOf(
        Triple("רק קצת מהדף צבוע", "just a little of the page is colored", listOf("פינה", "קצת", "corner", "a little", "little bit")),
        Triple("חצי מהדף צבוע", "half the page is colored", listOf("חצי", "half")),
        Triple("כמעט כל הדף צבוע", "almost the whole page is colored", listOf("כל הדף", "הכל", "מלא", "whole", "full", "everywhere", "all over", "all the page")),
    )
    val STROKES = listOf(
        Triple("הרבה נקודות קטנות", "lots of little dots", listOf("נקודות", "נקודה", "dots", "dot")),
        Triple("קווים ארוכים", "long lines", listOf("קווים", "קו", "lines", "line")),
        Triple("הרבה קשקושים עגולים", "lots of round scribbles", listOf("קשקושים", "קשקוש", "scribbles", "scribble")),
        Triple("קווים קצרים", "short lines", listOf("קווים", "קו", "lines", "line")),
    )
    private val HE_COUNT = mapOf(2 to ("שני" to "שתי"), 3 to ("שלושה" to "שלוש"))
    private val EN_COUNT = mapOf(2 to "two", 3 to "three")

    private fun hebrew(w: String) = w.any { it in 'א'..'ת' }

    fun describe(f: LookFacts): LookInput {
        val (pageHe, pageEn, pageWords) = if (f.page == "trace" && f.letter != null)
            Triple("דף עם האות ${f.letter.first} לצביעה", "a page with the letter ${f.letter.second} to trace", emptyList())
        else PAGES[f.page] ?: PAGES.getValue("blank")
        fun amount(i: Int) = if (i == 0 && f.colors.size > 1) "הרבה " to "lots of " else if (i > 0) "קצת " to "a little " else "" to ""
        val colorsHe = f.colors.withIndex().joinToString(", ") { (i, c) -> amount(i).first + COLORS.getValue(c).first[0] }
        val colorsEn = f.colors.withIndex().joinToString(", ") { (i, c) -> amount(i).second + COLORS.getValue(c).second }
        // (more than three of one sticker: "lots of", as a toddler stamps; the trainer's drawings had up to three)
        fun stampHe(s: String, k: Int) = STAMPS.getValue(s).let { st ->
            when { k == 1 -> st.one; k in HE_COUNT -> "${HE_COUNT.getValue(k).let { if (st.feminine) it.second else it.first }} ${st.many}"; else -> "הרבה ${st.many}" }
        }
        fun stampEn(s: String, k: Int) = STAMPS.getValue(s).let { st ->
            when { k == 1 -> "a ${st.oneEn}"; k in EN_COUNT -> "${EN_COUNT.getValue(k)} ${st.manyEn}"; else -> "lots of ${st.manyEn}" }
        }
        val stampsHe = f.stamps.joinToString(", ") { (s, k) -> stampHe(s, k) }.ifEmpty { "אין" }
        val stampsEn = f.stamps.joinToString(", ") { (s, k) -> stampEn(s, k) }.ifEmpty { "none" }
        val cover = COVER[f.cover]
        val strokes = f.strokes?.let { STROKES[it] }
        val he = "הציור: $pageHe. צבעים: $colorsHe. מדבקות: $stampsHe. ${cover.first}." + (strokes?.let { " ${it.first}." } ?: "")
        val en = "The drawing: $pageEn. Colors: $colorsEn. Stickers: $stampsEn. ${cover.second.replaceFirstChar { it.uppercase() }}." +
            (strokes?.let { " ${it.second.replaceFirstChar { c -> c.uppercase() }}." } ?: "")
        val stamped = f.stamps.map { it.first }
        val strokeWords = strokes?.third ?: emptyList()
        val otherStrokes = STROKES.filter { it !== strokes }.flatMap { it.third }.filter { it !in strokeWords }
        val presentHe = f.colors.flatMap { COLORS.getValue(it).first } + stamped.flatMap { STAMPS.getValue(it).let { s -> listOf(s.one, s.many) } } +
            pageWords.take(1) + (cover.third + strokeWords).filter(::hebrew)
        val presentEn = f.colors.map { COLORS.getValue(it).second } + stamped.flatMap { STAMPS.getValue(it).let { s -> listOf(s.oneEn, s.manyEn) } } +
            pageWords.drop(1) + (cover.third + strokeWords).filterNot(::hebrew)
        val absentHe = COLORS.filterKeys { it !in f.colors }.values.flatMap { it.first } +
            STAMPS.filterKeys { it !in stamped }.values.flatMap { listOf(it.one, it.many) } +
            PAGES.filter { (k, p) -> p.third.isNotEmpty() && k != f.page }.map { it.value.third[0] } + otherStrokes.filter(::hebrew)
        val absentEn = COLORS.filterKeys { it !in f.colors }.values.map { it.second } +
            STAMPS.filterKeys { it !in stamped }.values.flatMap { listOf(it.oneEn, it.manyEn) } +
            PAGES.filter { (k, p) -> p.third.isNotEmpty() && k != f.page }.map { it.value.third[1] } + otherStrokes.filterNot(::hebrew)
        return LookInput(he, en, presentHe, presentEn, absentHe.filter { it !in presentHe }, absentEn.filter { it !in presentEn })
    }

    private val WORD = Regex("[\\p{L}\\p{N}_'-]+")

    /** A word with up to two Hebrew prefix letters taken off (וציירת -> ציירת), as games._forms. */
    private fun forms(word: String): Set<String> {
        val out = mutableSetOf(word)
        for (k in 1..2) if (word.length - k >= 3 && word.take(k).all { it in "והבלמש" }) out += word.drop(k)
        return out
    }

    /** Whether [line] names something in [present] and nothing in [absent] (games.look_ok). */
    fun ok(line: String, present: List<String>, absent: List<String>): Boolean {
        val text = " " + line.lowercase() + " "
        val words = WORD.findAll(text).flatMap { forms(it.value) }.toSet()
        fun has(w: String) = w.lowercase().let { it in words || (" " in it && it in text) }
        return present.any(::has) && absent.none(::has)
    }
}
