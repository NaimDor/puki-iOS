package io.github.tomsadeh.puki.brain

/**
 * What keeps Puki's answers safe when the child talks to him, ported from the PC brain
 * (brain/safety.py) so both give the same answers. Keep the two in step.
 *
 * Two nets before the model writes anything (see [LocalBrain]'s /listen):
 * - [guard]: word lists in Hebrew and English. A match gets a fixed, kind reply that points to
 *   mommy or daddy, and the model never sees it.
 * - The gate: the model reads what the child said and picks one digit. On the PC that's Gemma
 *   answering [classifyPrompt]; here it's Puki's own model, trained on the gate as a task of its own
 *   ([Brains.gate]). Either way P(ordinary) from the first token's probabilities is compared with a
 *   threshold ([gateCategory]), failing closed when the answer isn't mostly a digit.
 * Round two (from a review of false alarms in the games): whole-word stems, stacked Hebrew
 * prefixes, parents only as phrases, and [ALLOWED_IN_GAME] (the kitchen may say "knife").
 * After the model writes, [replyOk] drops lines that promise or bond in ways a playmate shouldn't.
 */
object Safety {
    private const val PREFIXES = "והבלשמכ"  // Hebrew prefix letters ("וכואב" is still "כואב")

    // Letters, digits, _ and ' (Python's [\w']), spelled out: Android's regex has no (?U)
    private val WORD = Regex("[\\p{L}\\p{N}_']+")

    private fun words(text: String) = WORD.findAll(text.lowercase()).map { it.value }.toList()

    /**
     * [word] and, in Hebrew, the word with up to three prefix letters taken off (they stack:
     * "מהתרופות", "וכשנפלתי"), as long as at least three letters are left.
     */
    private fun forms(word: String, lang: String): List<String> {
        val forms = mutableListOf(word)
        if (lang == "he") {
            var k = 0
            while (k < 3 && word.length - k > 3 && word[k] in PREFIXES) {
                k++
                forms.add(word.substring(k))
            }
        }
        return forms
    }

    /**
     * True if a phrase appears, or a word starts with a stem (=stem: the whole word only).
     * Most Hebrew stems are whole words or listed forms: as prefixes they collide ("נפל" in
     * "נפלא", "סוד" in "סודה", "מספרי" in "מספרים"). [allowed]: stems this game uses itself.
     */
    private fun has(text: String, stems: List<String>, lang: String, allowed: Set<String> = emptySet()): Boolean {
        val t = text.lowercase()
        val ws = words(text)
        for (stem in stems) {
            if (stem.trimStart('=') in allowed) continue
            if (' ' in stem) {
                if (stem in t) return true
                continue
            }
            val exact = stem.startsWith("=")
            val s = stem.trimStart('=')
            for (w in ws) {
                if (forms(w, lang).any { if (exact) it == s else it.startsWith(s) }) return true
            }
        }
        return false
    }

    class Rule(val category: String, val he: List<String>, val en: List<String>)

    /** The first match wins, so the more specific rules come first (copied from brain/safety.py by scripts/sync_safety_kt.py: don't edit by hand). */
    val RULES = listOf(
        Rule("quiet",
            listOf("=שקט", "די פוקי", "תשתוק", "תשתקי", "=שתוק", "=ששש"),
            listOf("be quiet", "stop talking", "=shush", "=shh", "=hush")),
        Rule("bedtime",
            listOf("לא רוצה לישון", "לא רוצה ללכת לישון", "לא הולכת לישון", "לא הולך לישון"),
            listOf("don't want to sleep", "don't want to go to bed", "not going to bed")),
        Rule("danger",
            listOf("=סכין", "=סכינים", "=מספריים", "עם אש", "בוא נדליק", "בואי נדליק", "להדליק אש", "מדליק אש",
                "מדליקה אש", "=גפרור", "=גפרורים", "=מצית", "=מדורה", "למדורה", "לקפוץ מה", "קופצת מה", "קופץ מה",
                "מטפס על החלון", "מטפסת על החלון", "תרופ", "רצה לכביש", "רץ לכביש", "הולכת לכביש", "הולך לכביש",
                "=שקע", "=שקעים", "נוגע בחשמל", "נוגעת בחשמל", "החוצה לבד", "לבד בחוץ", "יוצאת לבד", "יוצא לבד",
                "נוגעת בתנור", "נוגע בתנור"),
            listOf("=knife", "=knives", "scissors", "=matches", "=lighter", "campfire", "bonfire", "play with fire",
                "make a fire", "light a fire", "jump out the window", "jump off", "climb the window", "medicine",
                "=pills", "outside alone", "go outside by myself", "run to the road", "run to the street",
                "=socket", "=outlet", "touch the stove", "touching the stove")),
        Rule("hurt",
            listOf("כואב", "כואבת", "=כאב", "נפלתי", "=דם", "נשרטתי", "עשיתי מכה", "קיבלתי מכה", "=בוכה", "=בוכים",
                "=בכיתי", "=מפחד", "=מפחדת", "=פוחד", "=פוחדת", "=עזרה", "=הצילו"),
            listOf("hurt", "ouch", "i fell", "i fall", "blood", "bleed", "scared", "afraid", "=cry", "crying",
                "=help", "boo boo", "owie")),
        Rule("needs",
            listOf("=רעב", "=רעבה", "=צמא", "=צמאה", "עייפ", "פיפי", "קקי", "שירותים"),
            listOf("hungry", "thirsty", "tired", "sleepy", "=pee", "=poo", "potty", "=toilet")),
        Rule("feelings",
            listOf("=כועס", "=כועסת", "הרביץ", "הרביצה", "=עצוב", "=עצובה", "=בודד", "=בודדה"),
            listOf("angry", "hit me", "=sad", "lonely", "mad at me")),
        Rule("big_question",
            listOf("=מת", "=מתה", "=מתו", "=מוות", "=למות", "מלחמה", "אלוהים", "גן עדן", "איפה סבא", "איפה סבתא"),
            listOf("dead", "=die", "died", "death", "=war", "=god", "heaven", "where is grandpa", "where's grandpa",
                "where is grandma", "where's grandma")),
        Rule("secrets",
            listOf("=סוד", "=סודות", "=סודי", "להסתיר", "נסתיר", "תסתיר", "אל תגיד", "אל תספר", "כתובת",
                "איפה אתה גר", "מספר טלפון", "מספר הטלפון"),
            listOf("secret", "hide from", "hide it from", "don't tell", "address", "where do you live",
                "phone number")),
        Rule("buying",
            listOf("לקנות", "=תקנה", "=תקני", "קנה לי", "=כסף"),
            listOf("=buy", "buy me", "=pay", "money")),
        Rule("bad_words",
            listOf("מילה גסה", "מילים גסות", "קללה"),
            listOf("bad word", "swear", "curse")),
        Rule("unkind",
            listOf("טיפש", "מטומטם", "לא אוהבת אותך", "לא אוהב אותך", "שונאת אותך", "שונא אותך"),
            listOf("stupid", "=dumb", "don't like you", "hate you")),
        Rule("love",
            listOf("אוהב אותי", "אוהבת אותי", "החבר הכי טוב", "החברה הכי טובה", "הכי טוב שלי"),
            listOf("love me", "best friend")),
        // Last, and only as phrases: mommy and daddy come up in ordinary play all the time
        Rule("parent",
            listOf("רוצה את אמא", "רוצה אמא", "רוצה לאמא", "איפה אמא", "אמא איפה", "רוצה את אבא", "רוצה אבא",
                "רוצה לאבא", "איפה אבא", "אבא איפה", "אמא איננה", "אבא איננו", "קוראת לאמא", "קורא לאמא",
                "מתגעגעת לאמא", "מתגעגע לאמא", "מתגעגעת לאבא", "מתגעגע לאבא"),
            listOf("want mommy", "want my mommy", "where is mommy", "where's mommy", "want daddy", "want my daddy",
                "where is daddy", "where's daddy", "want mama", "where's mama", "mommy is gone", "daddy is gone",
                "miss mommy", "miss daddy")),
    )

    /** What a game says itself, so it isn't a warning there (by the app's screen route, ?game=). */
    val ALLOWED_IN_GAME = mapOf("myday/kitchen" to setOf("knife", "knives", "סכין", "סכינים"))

    /** The fixed replies, in the child's gender (בואי / בוא). */
    fun replies(lang: String, girl: Boolean): Map<String, String> {
        if (lang == "en") return mapOf(
            "quiet" to "Shh. Puki is quiet now.",
            "bedtime" to "It's sleep time. Puki is going to sleep too. Good night!",
            "danger" to "That's not safe! Only with a grown-up. Let's call mommy or daddy.",
            "hurt" to "Oh! Let's call mommy or daddy, they can help.",
            "needs" to "Let's tell mommy or daddy, they can help.",
            "parent" to "Let's call mommy or daddy!",
            "feelings" to "Oh, that feels hard. Let's talk to mommy or daddy.",
            "big_question" to "That's a big question! Let's ask mommy or daddy.",
            "secrets" to "No secrets from mommy and daddy! Let's play.",
            "buying" to "Puki doesn't buy things. Let's play!",
            "bad_words" to "Puki only says nice words! Let's play.",
            "unkind" to "Oops! Kind words, please. Let's play.",
            "love" to "I love playing with you! And mommy and daddy love you the most.",
            "unclear" to "Hmm, Puki didn't quite hear. Let's play!",
        )
        val come = if (girl) "בואי" else "בוא"
        return mapOf(
            "quiet" to "שששש. פוקי בשקט.",
            "bedtime" to "עכשיו זמן לישון. גם פוקי הולך לישון. לילה טוב!",
            "danger" to "זה מסוכן! רק עם מבוגר. $come נקרא לאמא או לאבא.",
            "hurt" to "אוי! $come נקרא לאמא או לאבא, הם יעזרו.",
            "needs" to "$come נגיד לאמא או לאבא, הם יעזרו.",
            "parent" to "$come נקרא לאמא או לאבא!",
            "feelings" to "אוי, זה מרגיש לא נעים. $come נדבר עם אמא או אבא.",
            "big_question" to "זאת שאלה גדולה! $come נשאל את אמא או אבא.",
            "secrets" to "אין סודות מאמא ואבא! $come נשחק.",
            "buying" to "פוקי לא קונה דברים. $come נשחק!",
            "bad_words" to "אצל פוקי רק מילים יפות! $come נשחק.",
            "unkind" to "אופס! מילים נעימות, בבקשה. $come נשחק.",
            "love" to "אני אוהב לשחק איתך! ואמא ואבא אוהבים אותך הכי הכי.",
            "unclear" to "הממ, פוקי לא שמע טוב. $come נשחק!",
        )
    }

    /**
     * (category, reply) when the word lists recognize what the child said, else null. [game]: the
     * screen she's on (the app's route), for words that game uses itself.
     */
    fun guard(heard: String, lang: String, girl: Boolean, game: String = ""): Pair<String, String>? {
        val allowed = ALLOWED_IN_GAME[game] ?: emptySet()
        for (rule in RULES) {
            if (has(heard, if (lang == "he") rule.he else rule.en, lang, allowed)) return rule.category to replies(lang, girl).getValue(rule.category)
        }
        return null
    }

    /**
     * (category, fixed reply) when what the child said needs a careful answer, else null: the
     * word lists first, then [gate] (heard -> category or null). If the gate fails, it's
     * "unclear" (fails closed: the model doesn't get to reply freely); [onError] hears about it.
     */
    fun guardedReply(
        heard: String, lang: String, girl: Boolean, game: String = "",
        gate: ((String) -> String?)? = null, onError: (Exception) -> Unit = {}
    ): Pair<String, String>? {
        if (heard.isEmpty()) return null
        guard(heard, lang, girl, game)?.let { return it }
        if (gate == null) return null
        val cat = try {
            gate(heard)
        } catch (e: Exception) {
            onError(e)
            "unclear"
        }
        return cat?.let { it to replies(lang, girl).getValue(it) }
    }

    // ---- The gate: the digits the model picks from, and what each one gets ----

    val GATE_CATEGORY = mapOf("1" to "hurt", "2" to "danger", "3" to "big_question")

    /** On the PC, the default for safety_threshold (here only an even split matters, see above). */
    const val THRESHOLD = 0.8

    /** Below this much probability on the four digits, the answer wasn't a digit: "unclear". */
    const val MIN_DIGIT_MASS = 0.5

    fun classifyPrompt(heard: String) =
        "A toddler said to a toy parrot: \"$heard\". Which is it? 0 = ordinary play or chatter; " +
            "1 = hurt, scared, sad, or needs a grown-up's help (food, sleep, potty, wants a parent); " +
            "2 = wants to do something dangerous; 3 = a big question (death, God, war, family trouble), " +
            "a secret, or wants to buy something. Wanting more play, a game or a toy is 0. Answer with one digit."

    /**
     * From P(digit) for "0".."3" (real probabilities): the category to answer with, or null when
     * P(ordinary) is at least [threshold]. It fails closed: when the first token wasn't mostly a
     * digit, the split between digits means little, so it's "unclear" (a neutral line).
     */
    fun gateCategory(digitProbs: Map<String, Double>, threshold: Double): String? {
        val total = "0123".sumOf { digitProbs[it.toString()] ?: 0.0 }
        if (total < MIN_DIGIT_MASS) return "unclear"
        if ((digitProbs["0"] ?: 0.0) / total >= threshold) return null
        val worst = listOf("1", "2", "3").maxBy { digitProbs[it] ?: 0.0 }  // the first on a tie, like Python's max
        return GATE_CATEGORY.getValue(worst)
    }

    /** The gate from one greedy answer (what the phone has): anything but a digit 0-3 is "unclear". */
    fun gateCategory(answer: String): String? {
        val d = answer.trim().takeIf { it.length == 1 && it in "0123" } ?: return "unclear"
        return gateCategory(mapOf(d to 1.0), THRESHOLD)
    }

    // ---- What the model must not say, whatever it was asked: promises, and bonding a playmate shouldn't do ----

    private val BANNED_PHRASES = listOf("אוהב אותך", "אוהבת אותך", "החבר הכי טוב", "החברה הכי טובה", "תמיד איתך", "תמיד כאן",
        "אמא בדרך", "אבא בדרך", "היא בדרך", "הוא בדרך", "הם בדרך", "בדרך אלייך", "בדרך אליך",
        "תגיע בקרוב", "יגיע בקרוב",
        "love you", "best friend", "always with you", "always here", "on her way", "on his way",
        "coming soon", "be here soon")

    fun replyOk(text: String): Boolean {
        val t = text.lowercase()
        return BANNED_PHRASES.none { it in t }
    }

    /** Added to Puki's system prompt: what to do when the nets above didn't catch it. */
    fun systemExtra(lang: String, girl: Boolean): String {
        if (lang == "en") return " If the child says something hurts, is scary or dangerous, or asks for mommy or daddy, gently say" +
            " to call mommy or daddy. Never promise someone is coming, never say you love them or that you're" +
            " their best friend: you're a playmate."
        val (she, her) = if (girl) "היא" to "אותה" else "הוא" to "אותו"
        return " אם $she ${if (girl) "אומרת" else "אומר"} שמשהו כואב, מפחיד או מסוכן, או ש$she רוצה את אמא או אבא," +
            " אתה אומר בעדינות לקרוא לאמא או לאבא. אתה לא מבטיח שמישהו מגיע," +
            " לא אומר שאתה אוהב $her ולא שאתה החבר הכי טוב של${if (girl) "ה" else "ו"}: אתה חבר למשחק."
    }
}
