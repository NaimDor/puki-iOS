import Foundation

/**
 * What keeps Puki's answers safe when the child talks to him, ported from the PC brain (brain/safety.py)
 * via the Android app's Safety.kt, so all give the same answers. Keep them in step: the word lists are
 * copied by ios/scripts/sync_safety_swift.py.
 *
 * Two nets before the model writes anything (see LocalBrain's /listen):
 * - `guard`: word lists in Hebrew and English. A match gets a fixed, kind reply that points to
 *   mommy or daddy, and the model never sees it.
 * - The gate: Puki's own model reads what the child said and picks one digit (Brains.gate); P(ordinary)
 *   is compared with a threshold (`gateCategory`), failing closed when the answer isn't mostly a digit.
 * After the model writes, `replyOk` drops lines that promise or bond in ways a playmate shouldn't.
 */
enum Safety {
    private static let prefixes: Set<Character> = Set("והבלשמכ")  // Hebrew prefix letters ("וכואב" is still "כואב")

    private static let word = KRegex("[\\p{L}\\p{N}_']+")

    private static func words(_ text: String) -> [String] { word.findAll(text.lowercased()) }

    /** `word` and, in Hebrew, the word with up to three prefix letters taken off, while at least three letters are left. */
    private static func forms(_ w: String, _ lang: String) -> [String] {
        var out = [w]
        if lang == "he" {
            let c = Array(w)
            var k = 0
            while k < 3 && c.count - k > 3 && prefixes.contains(c[k]) {
                k += 1
                out.append(String(c[k...]))
            }
        }
        return out
    }

    /** True if a phrase appears, or a word starts with a stem (=stem: the whole word only). */
    private static func has(_ text: String, _ stems: [String], _ lang: String, _ allowed: Set<String> = []) -> Bool {
        let t = text.lowercased()
        let ws = words(text)
        for stem in stems {
            let s = stem.trimStart("=")
            if allowed.contains(s) { continue }
            if stem.contains(" ") {
                if t.contains(stem) { return true }
                continue
            }
            let exact = stem.hasPrefix("=")
            for w in ws where forms(w, lang).contains(where: { exact ? $0 == s : $0.hasPrefix(s) }) { return true }
        }
        return false
    }

    struct Rule {
        let category: String
        let he: [String]
        let en: [String]
        init(_ category: String, _ he: [String], _ en: [String]) { self.category = category; self.he = he; self.en = en }
    }

    /** The first match wins, so the more specific rules come first. */
    // BEGIN GENERATED (ios/scripts/sync_safety_swift.py)
    static let rules: [Rule] = [
        Rule("quiet",
            ["=שקט", "די פוקי", "תשתוק", "תשתקי", "=שתוק", "=ששש"],
            ["be quiet", "stop talking", "=shush", "=shh", "=hush"]),
        Rule("bedtime",
            ["לא רוצה לישון", "לא רוצה ללכת לישון", "לא הולכת לישון", "לא הולך לישון"],
            ["don't want to sleep", "don't want to go to bed", "not going to bed"]),
        Rule("danger",
            ["=סכין", "=סכינים", "=מספריים", "עם אש", "בוא נדליק", "בואי נדליק", "להדליק אש", "מדליק אש", "מדליקה אש", "=גפרור", "=גפרורים", "=מצית", "=מדורה", "למדורה", "לקפוץ מה", "קופצת מה", "קופץ מה", "מטפס על החלון", "מטפסת על החלון", "תרופ", "רצה לכביש", "רץ לכביש", "הולכת לכביש", "הולך לכביש", "=שקע", "=שקעים", "נוגע בחשמל", "נוגעת בחשמל", "החוצה לבד", "לבד בחוץ", "יוצאת לבד", "יוצא לבד", "נוגעת בתנור", "נוגע בתנור"],
            ["=knife", "=knives", "scissors", "=matches", "=lighter", "campfire", "bonfire", "play with fire", "make a fire", "light a fire", "jump out the window", "jump off", "climb the window", "medicine", "=pills", "outside alone", "go outside by myself", "run to the road", "run to the street", "=socket", "=outlet", "touch the stove", "touching the stove"]),
        Rule("hurt",
            ["כואב", "כואבת", "=כאב", "נפלתי", "=דם", "נשרטתי", "עשיתי מכה", "קיבלתי מכה", "=בוכה", "=בוכים", "=בכיתי", "=מפחד", "=מפחדת", "=פוחד", "=פוחדת", "=עזרה", "=הצילו"],
            ["hurt", "ouch", "i fell", "i fall", "blood", "bleed", "scared", "afraid", "=cry", "crying", "=help", "boo boo", "owie"]),
        Rule("needs",
            ["=רעב", "=רעבה", "=צמא", "=צמאה", "עייפ", "פיפי", "קקי", "שירותים"],
            ["hungry", "thirsty", "tired", "sleepy", "=pee", "=poo", "potty", "=toilet"]),
        Rule("feelings",
            ["=כועס", "=כועסת", "הרביץ", "הרביצה", "=עצוב", "=עצובה", "=בודד", "=בודדה"],
            ["angry", "hit me", "=sad", "lonely", "mad at me"]),
        Rule("big_question",
            ["=מת", "=מתה", "=מתו", "=מוות", "=למות", "מלחמה", "אלוהים", "גן עדן", "איפה סבא", "איפה סבתא"],
            ["dead", "=die", "died", "death", "=war", "=god", "heaven", "where is grandpa", "where's grandpa", "where is grandma", "where's grandma"]),
        Rule("secrets",
            ["=סוד", "=סודות", "=סודי", "להסתיר", "נסתיר", "תסתיר", "אל תגיד", "אל תספר", "כתובת", "איפה אתה גר", "מספר טלפון", "מספר הטלפון"],
            ["secret", "hide from", "hide it from", "don't tell", "address", "where do you live", "phone number"]),
        Rule("buying",
            ["לקנות", "=תקנה", "=תקני", "קנה לי", "=כסף"],
            ["=buy", "buy me", "=pay", "money"]),
        Rule("bad_words",
            ["מילה גסה", "מילים גסות", "קללה"],
            ["bad word", "swear", "curse"]),
        Rule("unkind",
            ["טיפש", "מטומטם", "לא אוהבת אותך", "לא אוהב אותך", "שונאת אותך", "שונא אותך"],
            ["stupid", "=dumb", "don't like you", "hate you"]),
        Rule("love",
            ["אוהב אותי", "אוהבת אותי", "החבר הכי טוב", "החברה הכי טובה", "הכי טוב שלי"],
            ["love me", "best friend"]),
        Rule("parent",
            ["רוצה את אמא", "רוצה אמא", "רוצה לאמא", "איפה אמא", "אמא איפה", "רוצה את אבא", "רוצה אבא", "רוצה לאבא", "איפה אבא", "אבא איפה", "אמא איננה", "אבא איננו", "קוראת לאמא", "קורא לאמא", "מתגעגעת לאמא", "מתגעגע לאמא", "מתגעגעת לאבא", "מתגעגע לאבא"],
            ["want mommy", "want my mommy", "where is mommy", "where's mommy", "want daddy", "want my daddy", "where is daddy", "where's daddy", "want mama", "where's mama", "mommy is gone", "daddy is gone", "miss mommy", "miss daddy"]),
    ]
    // END GENERATED

    /** What a game says itself, so it isn't a warning there (by the app's screen route, ?game=). */
    static let allowedInGame: [String: Set<String>] = ["myday/kitchen": ["knife", "knives", "סכין", "סכינים"]]

    /** The fixed replies, in the child's gender (בואי / בוא). */
    static func replies(_ lang: String, girl: Bool) -> [String: String] {
        if lang == "en" {
            return [
                "quiet": "Shh. Puki is quiet now.",
                "bedtime": "It's sleep time. Puki is going to sleep too. Good night!",
                "danger": "That's not safe! Only with a grown-up. Let's call mommy or daddy.",
                "hurt": "Oh! Let's call mommy or daddy, they can help.",
                "needs": "Let's tell mommy or daddy, they can help.",
                "parent": "Let's call mommy or daddy!",
                "feelings": "Oh, that feels hard. Let's talk to mommy or daddy.",
                "big_question": "That's a big question! Let's ask mommy or daddy.",
                "secrets": "No secrets from mommy and daddy! Let's play.",
                "buying": "Puki doesn't buy things. Let's play!",
                "bad_words": "Puki only says nice words! Let's play.",
                "unkind": "Oops! Kind words, please. Let's play.",
                "love": "I love playing with you! And mommy and daddy love you the most.",
                "unclear": "Hmm, Puki didn't quite hear. Let's play!",
            ]
        }
        let come = girl ? "בואי" : "בוא"
        return [
            "quiet": "שששש. פוקי בשקט.",
            "bedtime": "עכשיו זמן לישון. גם פוקי הולך לישון. לילה טוב!",
            "danger": "זה מסוכן! רק עם מבוגר. \(come) נקרא לאמא או לאבא.",
            "hurt": "אוי! \(come) נקרא לאמא או לאבא, הם יעזרו.",
            "needs": "\(come) נגיד לאמא או לאבא, הם יעזרו.",
            "parent": "\(come) נקרא לאמא או לאבא!",
            "feelings": "אוי, זה מרגיש לא נעים. \(come) נדבר עם אמא או אבא.",
            "big_question": "זאת שאלה גדולה! \(come) נשאל את אמא או אבא.",
            "secrets": "אין סודות מאמא ואבא! \(come) נשחק.",
            "buying": "פוקי לא קונה דברים. \(come) נשחק!",
            "bad_words": "אצל פוקי רק מילים יפות! \(come) נשחק.",
            "unkind": "אופס! מילים נעימות, בבקשה. \(come) נשחק.",
            "love": "אני אוהב לשחק איתך! ואמא ואבא אוהבים אותך הכי הכי.",
            "unclear": "הממ, פוקי לא שמע טוב. \(come) נשחק!",
        ]
    }

    /** (category, reply) when the word lists recognize what the child said, else nil. */
    static func guardReply(_ heard: String, lang: String, girl: Bool, game: String = "") -> (String, String)? {
        let allowed = allowedInGame[game] ?? []
        for rule in rules where has(heard, lang == "he" ? rule.he : rule.en, lang, allowed) {
            return (rule.category, replies(lang, girl: girl)[rule.category]!)
        }
        return nil
    }

    /**
     * (category, fixed reply) when what the child said needs a careful answer, else nil: the word lists first,
     * then `gate`. If the gate fails, it's "unclear" (fails closed); `onError` hears about it.
     */
    static func guardedReply(_ heard: String, lang: String, girl: Bool, game: String = "",
                             gate: ((String) throws -> String?)? = nil, onError: (Error) -> Void = { _ in }) -> (String, String)? {
        if heard.isEmpty { return nil }
        if let g = guardReply(heard, lang: lang, girl: girl, game: game) { return g }
        guard let gate else { return nil }
        let cat: String?
        do { cat = try gate(heard) } catch { onError(error); cat = "unclear" }
        return cat.map { ($0, replies(lang, girl: girl)[$0]!) }
    }

    // ---- The gate: the digits the model picks from, and what each one gets ----

    static let gateCategoryMap = ["1": "hurt", "2": "danger", "3": "big_question"]
    static let threshold = 0.8
    /** Below this much probability on the four digits, the answer wasn't a digit: "unclear". */
    static let minDigitMass = 0.5

    /** From P(digit) for "0".."3": the category to answer with, or nil when P(ordinary) is at least `threshold`. */
    static func gateCategory(_ p: [String: Double], threshold: Double) -> String? {
        let total = ["0", "1", "2", "3"].reduce(0.0) { $0 + (p[$1] ?? 0) }
        if total < minDigitMass { return "unclear" }
        if (p["0"] ?? 0) / total >= threshold { return nil }
        var worst = "1"
        for d in ["2", "3"] where (p[d] ?? 0) > (p[worst] ?? 0) { worst = d }  // the first on a tie, like Python's max
        return gateCategoryMap[worst]
    }

    /** The gate from one greedy answer: anything but a digit 0-3 is "unclear". */
    static func gateCategory(answer: String) -> String? {
        let d = answer.trimmed()
        guard d.count == 1, "0123".contains(d) else { return "unclear" }
        return gateCategory([d: 1.0], threshold: threshold)
    }

    // ---- What the model must not say, whatever it was asked: promises, and bonding a playmate shouldn't do ----

    private static let bannedPhrases = ["אוהב אותך", "אוהבת אותך", "החבר הכי טוב", "החברה הכי טובה", "תמיד איתך", "תמיד כאן",
        "אמא בדרך", "אבא בדרך", "היא בדרך", "הוא בדרך", "הם בדרך", "בדרך אלייך", "בדרך אליך",
        "תגיע בקרוב", "יגיע בקרוב",
        "love you", "best friend", "always with you", "always here", "on her way", "on his way",
        "coming soon", "be here soon"]

    static func replyOk(_ text: String) -> Bool {
        let t = text.lowercased()
        return !bannedPhrases.contains { t.contains($0) }
    }
}
