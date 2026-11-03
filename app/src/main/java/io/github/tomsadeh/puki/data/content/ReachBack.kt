package io.github.tomsadeh.puki.data.content

/** A game moment Puki remembers (MomentMemory): when, in which game (its screen), and what happened, in both languages. */
data class Moment(val t: Long, val he: String, val en: String, val game: String = "")

/**
 * Puki reaching back ("like the cow yesterday!"): which earlier moment a new one links to, said as the brain
 * learned it (train/build.py: the same game, "קודם היום"; only within this session, by the parent's wish),
 * and the check his line must pass: it names the earlier thing. The words are the PC brain's
 * (brain/memory.py _content_words); ReachBackTest checks them against it.
 */
object ReachBack {
    // The words every game moment has, which say nothing about what it was about (memory._STOP)
    private val STOP = setOf("במשחק", "הילדה", "הילד", "בחרו", "נכון", "מחפשים", "מחכה", "שוב", "אבל", "חיפשנו", "הצבע", "צבע",
        "האכילה", "האכיל", "אכילה", "אכיל", "ציירה", "צייר", "מצאו", "מצאה", "מצא", "לאוכל", "אוכל",
        "the", "child", "game", "picked", "that", "right", "looking", "for", "again", "waiting", "and", "with",
        "fed", "drew", "found", "food",
        "אתה", "הנה", "איזה", "הזה", "הזאת", "שלה", "שלו", "איפה", "כמה", "אני", "רוצה", "רוצים", "עוד", "היום",
        "you", "your", "this", "what", "said", "she", "her", "his", "want", "more", "look", "here", "there")
    private const val PREFIXES = "הובלשמכ"
    private val FINALS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')
    private val STOP_FORMS by lazy { STOP.flatMap { forms(it) }.toSet() }

    /** The forms a word may come in, so "הפילים" meets "פיל" and "dogs" meets "dog" (memory._forms). */
    fun forms(word: String): Set<String> {
        val w = word.lowercase().filter { it !in '֑'..'ׇ' }.map { FINALS[it] ?: it }.joinToString("")
        val bases = mutableSetOf(w)
        for (n in 1..2) if (w.length - n >= 2 && w.take(n).all { it in PREFIXES }) bases += w.drop(n)
        val out = mutableSetOf<String>()
        for (b in bases) {
            out += b
            for (end in listOf("ימ", "ות", "ה", "es", "s")) if (b.endsWith(end) && b.length - end.length >= 2) out += b.dropLast(end.length)
        }
        return out
    }

    /** What a moment was about ("פיל", "בננה"), without the game's name before the colon or the words every moment has. */
    fun contentWords(text: String, ignore: Set<String> = emptySet()): Set<String> {
        val out = mutableSetOf<String>()
        val body = text.substringAfter(":").map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        for (w in body.split(' ').filter { it.isNotEmpty() }) {
            if (w.length < 3) continue
            val f = forms(w)
            if (f.any { it in STOP_FORMS || it in ignore }) continue
            out += f
        }
        return out
    }

    /** The moment with what it was about (and its numbers) taken out: two moments of one kind share it ("whose shadow: the duck"). */
    private fun skeleton(text: String): String {
        val words = contentWords(text)
        return text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() && forms(it).none { f -> f in words } }.joinToString(" ")
    }

    /**
     * An earlier moment to reach back to for [he] in [game] (its screen): a few minutes back at least, in the same
     * game, and of the same kind or about the same thing; with when it was, as the brain learned it ("קודם היום",
     * "אתמול", "לפני יומיים"). Null when there's none. (The history is this session's, unless the parent turned on
     * "Puki remembers between sessions": MomentMemory.)
     */
    fun related(he: String, game: String, history: List<Moment>, nowMs: Long, today: java.time.LocalDate,
                ignore: Set<String> = emptySet()): Pair<Pair<String, String>, Moment>? {
        if (game.isEmpty()) return null
        val words = contentWords(he, ignore)
        val shape = skeleton(he)
        for (m in history.asReversed()) {
            if (m.t > nowMs - GAP_MS || m.he == he || m.game != game) continue
            if (!(words.any { it in contentWords(m.he, ignore) } || skeleton(m.he) == shape)) continue
            val day = java.time.Instant.ofEpochMilli(m.t).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            return when (java.time.temporal.ChronoUnit.DAYS.between(day, today)) {
                0L -> "קודם היום" to "earlier today"
                1L -> "אתמול" to "yesterday"
                2L -> "לפני יומיים" to "2 days ago"
                else -> continue
            } to m
        }
        return null
    }

    /** Whether [line] names what only the earlier moment had (the horse, not "hiding", which both have): build.py's check. */
    fun namesEarlier(line: String, input: String, earlier: String): Boolean {
        val only = contentWords(earlier.substringAfter(":")) - contentWords(input)
        return contentWords(line).any { it in only }
    }

    /** How long ago a moment must be to be "before" (not the one just now). */
    const val GAP_MS = 4 * 60_000L
}
