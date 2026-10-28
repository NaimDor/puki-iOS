package io.github.tomsadeh.puki.brain

import java.text.Normalizer

/**
 * Who Puki is and how he talks, ported from the PC brain (brain/persona.py, and brain.py's prompts) so the device brain
 * sounds the same: the instructions and examples Gemma gets, the filter every line passes
 * (short, warm, no "wrong", no compliments about the child), Hebrew words the voice misreads,
 * and the generous word check for Say it with Puki. Keep the two in step.
 */
class Persona(
    private val companion: String = "פוקי",
    private val companionEn: String = "Puki",
) {
    var name = ""   // the child's name (nikud stripped), from the app's settings
        private set
    var girl = true
        private set

    fun setChild(name: String, gender: String) {
        this.name = stripNikud(name.trim()).take(30)
        girl = gender != "boy"
    }

    private val child get() = name.ifEmpty { if (girl) "הילדה" else "הילד" }

    fun child(lang: String) = if (lang == "en") name.ifEmpty { if (girl) "the little girl" else "the little boy" } else child

    fun systemPrompt(lang: String): String {
        if (lang == "en") {
            val nameNote = if (HEBREW_LETTER.containsMatchIn(name))
                " If you say ${if (girl) "her" else "his"} name, write it exactly like this: $name." else ""
            return "You are $companionEn, a happy, colorful parrot who plays games with ${child(lang)}, a 2-year-old " +
                "${if (girl) "girl" else "boy"} who can't read yet. You speak very simple English: ONE or TWO very " +
                "short sentences (at most 10 words total). Always warm and encouraging; never say 'no' or " +
                "'wrong' - if there was a mistake, gently cheer them on to try again. Now and then add a fun " +
                "sound that fits: 'Yum!' or 'Mmm!' only when someone eats, otherwise 'Hop!', 'Wow!' or 'Yay!'. " +
                "Don't repeat a word twice, and end on something about what happened. Don't compliment the " +
                "child (no 'you're so smart', 'you're amazing'); talk about the thing itself: the animal, the " +
                "color, the food. No emoji, no other languages, no questions they must answer." +
                "$nameNote Reply with only what the parrot says."
        }
        val you = if (girl) "בלשון נקבה" else "בלשון זכר"
        return "אתה $companion, תוכי צבעוני ושמח שמשחק עם $child, " +
            "${if (girl) "ילדה" else "ילד"} בת שנתיים שעוד לא יודעת לקרוא. " +
            "אתה מדבר בעברית פשוטה מאוד, משפט אחד או שניים קצרים (עד 10 מילים בסך הכול), " +
            "ופונה ${if (girl) "אליה" else "אליו"} $you. " +
            "אתה תמיד חם ומעודד. לעולם לא אומר 'לא' או 'טעות'. " +
            "אם הייתה טעות, אתה מעודד בעדינות לנסות שוב. " +
            "לפעמים אתה מוסיף קול מצחיק שמתאים למצב: 'יאמי!' או 'מממ!' רק כשמישהו אוכל, " +
            "ובשאר המקרים 'הופ!', 'וואו!' או 'יש!'. אל תחזור על אותה מילה פעמיים, " +
            "ואל תוסיף קריאה בסוף המשפט (כמו 'איזה כיף!'); תסיים במשהו על מה שקרה. " +
            "אל תחמיא ${if (girl) "לה" else "לו"} על ${if (girl) "עצמה" else "עצמו"} " +
            "(בלי 'את מקסימה', 'את חכמה', 'את מתוקה' וכדומה); תגיב על הדבר עצמו: החיה, הצבע, האוכל. " +
            "בלי אימוג'ים, בלי אנגלית ובלי שאלות שצריך לענות עליהן. כתוב רק את מה שהתוכי אומר."
    }

    /** A few examples, so the model copies the style (short, warm, playful). */
    fun examples(lang: String): List<Pair<String, String>> {
        val n = if (name.isNotEmpty()) " $name" else ""
        if (lang == "en") return listOf(
            "The child fed the cow grass. That's right." to "Yum! The cow loves grass! She's so happy!",
            "The child tapped blue, but we were looking for red." to "Ooh, blue! Let's find red!",
            "The child counted three butterflies." to "One, two, three! Three butterflies flying!",
            "The child tapped green. That's right." to "Yay! Green like the grass!",
            "The child said to you: \"hi Puki\"." to "Hi$n! I'm so happy you came!",
        )
        return if (girl) listOf(
            "הילדה האכילה את הפרה בעשב. זה נכון." to "מממ! הפרה אוהבת עשב! היא ממש שמחה!",
            "הילדה לחצה על כחול, אבל חיפשנו אדום." to "אוו, כחול! בואי נמצא אדום!",
            "הילדה ספרה שלושה פרפרים." to "אחת, שתיים, שלוש! שלושה פרפרים עפים!",
            "הילדה לחצה על הצבע הירוק. זה נכון." to "יש! ירוק כמו העשב!",
            "הילדה אמרה לך: \"היי פוקי\"." to "היי$n! אני כל כך שמח שבאת!",
        ) else listOf(
            "הילד האכיל את הפרה בעשב. זה נכון." to "מממ! הפרה אוהבת עשב! היא ממש שמחה!",
            "הילד לחץ על כחול, אבל חיפשנו אדום." to "אוו, כחול! בוא נמצא אדום!",
            "הילד ספר שלושה פרפרים." to "אחת, שתיים, שלוש! שלושה פרפרים עפים!",
            "הילד לחץ על הצבע הירוק. זה נכון." to "יש! ירוק כמו העשב!",
            "הילד אמר לך: \"היי פוקי\"." to "היי$n! אני כל כך שמח שבאת!",
        )
    }

    // ---- What Gemma is asked: the situation, a suggested opening sound, and lines not to repeat ----

    private val recent = ArrayDeque<String>()

    fun situationPrompt(situation: String, lang: String, listening: Boolean = false): String {
        val kind = if (FOOD_WORDS.any { it in situation }) "food" else "other"
        // A suggested opening sound keeps game lines varied; not for answering the child, where a
        // forced "Hooray!" can land on "I fell down"
        val opener = if (listening) "" else OPENERS.getValue(lang).getValue(kind).random()
        val sb = StringBuilder(situation)
        if (lang == "he") {
            if (opener.isNotEmpty()) sb.append("\n(תתחיל את התשובה ב-'$opener!')")
            if (recent.isNotEmpty()) sb.append("\n(אל תחזור על מה שאמרת קודם: ").append(recent.joinToString(" / ")).append(")")
        } else {
            if (opener.isNotEmpty()) sb.append("\n(Start your reply with '$opener!')")
            if (recent.isNotEmpty()) sb.append("\n(Don't repeat what you said before: ").append(recent.joinToString(" / ")).append(")")
        }
        return sb.toString()
    }

    fun said(line: String) {
        recent.addLast(line)
        while (recent.size > 6) recent.removeFirst()
    }

    fun lookPrompt(lang: String): String =
        if (lang == "en") "${child(lang).replaceFirstChar { it.uppercase() }} just drew this picture (a 2-year-old's drawing, probably scribbles). " +
            "In one or two short English sentences, say what you see: colors, lines, shapes, maybe a playful " +
            "guess at what it is. Be delighted. Never say it doesn't look like something."
        else "$child ${if (girl) "ציירה" else "צייר"} עכשיו את הציור הזה (ציור של פעוטה בת שנתיים, כנראה קשקושים). " +
            "תגיב במשפט אחד או שניים קצרים: תגיד מה אתה רואה (צבעים, קווים, צורות, אולי ניחוש שובב מה זה) " +
            "בהתלהבות. לעולם אל תגיד שזה לא דומה למשהו."

    fun storyPrompt(played: String, icons: Map<String, String>, lang: String): String {
        val names = icons.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        return if (lang == "en") {
            val c = child(lang)
            "Tell $c a very short, calm bedtime story in simple English (5-6 sentences, each at most " +
                "10 simple words). ${c.replaceFirstChar { it.uppercase() }} is the hero. Today $c played with: $played. Use one or two " +
                "of those things. Slow, warm and sleepy, ending with everyone falling asleep. No questions. " +
                "For each sentence, choose the picture (icon) that fits it best from this list:\n$names\nAnswer as JSON."
        } else {
            "ספר ל$child סיפור לפני השינה קצר ורגוע מאוד (5-6 משפטים, כל משפט עד 10 מילים פשוטות). " +
                "$child ${if (girl) "היא הגיבורה" else "הוא הגיבור"}. היום $child ${if (girl) "שיחקה" else "שיחק"} עם: $played. " +
                "תשתמש בדבר אחד או שניים מהם. איטי, חם ומנומנם, ובסוף כולם נרדמים. בלי שאלות. " +
                "לכל משפט בחר את התמונה (icon) שהכי מתאימה לו מהרשימה הזאת:\n$names\nתענה ב-JSON."
        }
    }

    fun storyClosing(lang: String) =
        if (lang == "en") "Good night${if (name.isNotEmpty()) ", $name" else ""}." else "לילה טוב${if (name.isNotEmpty()) ", $name" else ""}."

    /** What to tell Gemma when the child talked to Puki ([heard] is what speech recognition got). */
    fun listenSituation(heard: String, lang: String): String {
        if (lang == "en") {
            val c = child(lang).replaceFirstChar { it.uppercase() }
            val (they, them) = if (girl) "she" to "her" else "he" to "him"
            if (heard.isEmpty()) return "$c talked to you, but the words weren't clear. Say something sweet back."
            return "$c said to you: \"$heard\". (A toddler is talking, so it may sound a bit off; for example " +
                "something like '$companionEn' is probably your name.) Answer what $they said directly: if " +
                "$they said hello or called you, say hello back to $them; if something was mentioned (an animal, " +
                "a color, a shape), say something about it. If it doesn't make sense, say something sweet."
        }
        if (heard.isEmpty()) return "$child ${if (girl) "דיברה" else "דיבר"} אל התוכי, אבל לא שמענו מילים ברורות. תגיד משהו מתוק בחזרה."
        return "$child ${if (girl) "אמרה" else "אמר"} לך: \"$heard\". " +
            "(הדיבור של פעוטה, אז אולי זה נשמע קצת לא מדויק; למשל אם שמענו משהו כמו '${stripNikud(companion)}' " +
            "זה כנראה השם שלך.) תענה ישירות על מה ${if (girl) "שהיא" else "שהוא"} אמר${if (girl) "ה" else ""}: " +
            "אם ${if (girl) "היא" else "הוא"} ${if (girl) "אמרה" else "אמר"} שלום או ${if (girl) "קראה" else "קרא"} לך, תגיד שלום בחזרה; " +
            "אם הוזכר משהו (חיה, צבע, צורה), תגיד עליו משהו. אם לא מובן, תגיד משהו מתוק."
    }

    // ---- The filter every line passes ----

    /** A safe line, or null. */
    fun clean(raw: String, lang: String): String? {
        var text = Regex("<[^>]+>").replace(raw, "")
        text = text.replace("’", "'").replace("‘", "'").replace("“", "\"").replace("”", "\"")
        text = Regex("\\s*[—–]\\s*").replace(text, if (lang == "en") ", " else " – ")
        text = text.trim().trim('"').trim()
        text = text.lineSequence().firstOrNull()?.trim() ?: ""
        text = dropCompliments(text)
        if (text.isEmpty() || text.length > 90 || text.split(Regex("\\s+")).size > 14) return null
        // In English the child's name may stay in Hebrew letters (the voice reads it in Hebrew)
        val check = if (lang == "en" && name.isNotEmpty()) text.replace(name, "Name") else text
        if (!(if (lang == "en") ENGLISH_OK else HEBREW_OK).matches(check)) return null
        // Whole words only ("רע" is bad, "רעבה" — hungry — is fine); Hebrew prefixes like ו/ה allowed
        // Letters, digits and _ in any script (Python's \w), spelled out: Android's regex has no (?U)
        val words = Regex("[\\p{L}\\p{N}_']+").findAll(text.lowercase()).map { it.value }.toList()
        if (words.any { w -> BANNED.any { b -> ' ' !in b && (w == b || w.trimStart(*"והבלמש".toCharArray()) == b) } }) return null
        if (BANNED.any { b -> ' ' in b && b in text.lowercase() }) return null
        return text
    }

    private fun dropCompliments(text: String): String {
        val sentences = Regex("[^!?.]+[!?.]*").findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        val kept = sentences.filter { !COMPLIMENT.containsMatchIn(it) }
        return if (kept.isNotEmpty()) kept.joinToString(" ") else text
    }

    /** What the voice actually reads: no nikud, misread words swapped (the PC's speak()). */
    fun forVoice(text: String): String {
        var t = Regex("[֑-ׇ]").replace(text, "")
        for ((word, instead) in MISREAD) {
            // Whole words, also with a one-letter prefix ("והידד" -> "ויש")
            t = Regex("(?<![א-ת])([והבכלמש]?)$word(?![א-ת])").replace(t) { it.groupValues[1] + instead }
        }
        return t
    }

    // ---- Say it with Puki: did the child say the word? ----

    /** "word", "close" (a near miss or a related word like the animal's sound), or "tried". */
    fun wordMatch(heard: String, target: String, alts: List<String> = emptyList()): String {
        val words = words(heard)
        val goal = words(target).joinToString("")
        if (words.isEmpty() || goal.isEmpty()) return "tried"
        val joined = words.joinToString("")
        if (goal in words || goal in joined) return "word"  // also "הכלב", "the dog"
        if (alts.map { words(it).joinToString("") }.any { it.isNotEmpty() && it in joined }) return "close"
        for (w in words) {
            if (similarity(w, goal) >= 0.6) return "close"
            if (w[0] == goal[0] && w.length <= goal.length) return "close"  // the first sound ("פה" for "פרה")
        }
        return "tried"
    }

    companion object {
        private val HEBREW_LETTER = Regex("[א-ת]")
        private val HEBREW_OK = Regex("^[֐-׿\\s,.!?'\"׳״\\-–]+$")
        private val ENGLISH_OK = Regex("^[A-Za-z\\s,.!?'\\-]+$")
        private val BANNED = listOf("טעות", "רע", "טיפש", "טיפשה", "מכוער", "מכוערת", "לא נכון",
            "wrong", "bad", "stupid", "dumb", "ugly", "idiot", "hate", "not right", "mistake")
        private val COMPLIMENT = Regex(
            "^(את|אתה|you'?re|you are)\\s+(כל כך |ממש |כזאת |כזה |מאוד |so |really |such an? )*" +
                "(מקסימה|מקסים|מתוקה|מתוק|חכמה|חכם|מדהימה|מדהים|מיוחדת|מיוחד|טובה|טוב|יפה|נהדרת|נהדר|" +
                "עושה (יופי|טוב|כל כך טוב)|sweet|smart|amazing|special|great|lovely|clever|wonderful|awesome|" +
                "brilliant|beautiful|the best|doing (great|so well|amazing))(?![\\p{L}\\p{N}_])",  // a word boundary, in any script
            RegexOption.IGNORE_CASE,
        )
        private val FOOD_WORDS = listOf("האכיל", "אוכל", "fed ", "food")
        private val OPENERS = mapOf(
            "he" to mapOf("food" to listOf("יאמי", "מממ", "אמממ"), "other" to listOf("הופ", "יש", "אוהו", "יופי", "טה-דה", "")),
            "en" to mapOf("food" to listOf("Yum", "Mmm", "Munch munch"), "other" to listOf("Hooray", "Yay", "Ooh", "Ta-da", "Woohoo", "")),
        )
        /** Hebrew words the voice reads wrong, and what Puki says instead (as on the PC). */
        val MISREAD = mapOf("הידד" to "יש")  // comes out as "אהדה"

        fun stripNikud(s: String) = Regex("[֑-ׇ]").replace(s, "")

        private const val FINALS = "ךםןףץ"
        private const val PLAIN = "כמנפצ"

        /** The words of [text], plain: no nikud or punctuation, lower case, final letters as regular ones. */
        internal fun words(text: String): List<String> {
            val t = stripNikud(text).lowercase().map { c -> FINALS.indexOf(c).let { if (it >= 0) PLAIN[it] else c } }.joinToString("")
            return Regex("[^\\p{L}\\p{N}_\\s]").replace(Normalizer.normalize(t, Normalizer.Form.NFC), " ")
                .split(Regex("\\s+")).filter { it.isNotEmpty() }
        }

        /** Python's difflib.SequenceMatcher(None, a, b).ratio(), for short words. */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() && b.isEmpty()) return 1.0
            return 2.0 * matches(a, b) / (a.length + b.length)
        }

        private fun matches(a: String, b: String): Int {
            // Longest common block, then recurse on both sides (Ratcliff/Obershelp, like difflib)
            var bestI = 0; var bestJ = 0; var bestK = 0
            for (i in a.indices) for (j in b.indices) {
                var k = 0
                while (i + k < a.length && j + k < b.length && a[i + k] == b[j + k]) k++
                if (k > bestK) { bestI = i; bestJ = j; bestK = k }
            }
            if (bestK == 0) return 0
            return bestK + matches(a.substring(0, bestI), b.substring(0, bestJ)) +
                matches(a.substring(bestI + bestK), b.substring(bestJ + bestK))
        }
    }
}
