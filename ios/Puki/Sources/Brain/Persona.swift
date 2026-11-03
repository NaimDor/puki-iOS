import Foundation

/**
 * Who Puki is and how he talks, ported from the PC brain (brain/persona.py) so the device brain sounds the same:
 * the filter every line passes (short, warm, no "wrong", no compliments about the child), Hebrew words the voice
 * misreads, and the generous word check for Say it with Puki. Keep the two in step.
 */
final class Persona {
    private let companion: String
    private let companionEn: String
    private(set) var name = ""   // the child's name (nikud stripped), from the app's settings
    private(set) var girl = true

    init(companion: String = "פוקי", companionEn: String = "Puki") {
        self.companion = companion
        self.companionEn = companionEn
    }

    func setChild(_ name: String, gender: String) {
        self.name = Self.stripNikud(name.trimmed()).take(30)
        girl = gender != "boy"
    }

    private var child: String { name.ifEmpty(girl ? "הילדה" : "הילד") }

    func child(_ lang: String) -> String { lang == "en" ? name.ifEmpty(girl ? "the little girl" : "the little boy") : child }

    private var recent: [String] = []

    func situationPrompt(_ situation: String, lang: String, listening: Bool = false) -> String {
        let kind = Self.foodWords.contains { situation.contains($0) } ? "food" : "other"
        let opener = listening ? "" : (Self.openers[lang]![kind]!.randomElement() ?? "")
        var sb = situation
        if lang == "he" {
            if !opener.isEmpty { sb += "\n(תתחיל את התשובה ב-'\(opener)!')" }
            if !recent.isEmpty { sb += "\n(אל תחזור על מה שאמרת קודם: " + recent.joined(separator: " / ") + ")" }
        } else {
            if !opener.isEmpty { sb += "\n(Start your reply with '\(opener)!')" }
            if !recent.isEmpty { sb += "\n(Don't repeat what you said before: " + recent.joined(separator: " / ") + ")" }
        }
        return sb
    }

    func said(_ line: String) {
        recent.append(line)
        while recent.count > 6 { recent.removeFirst() }
    }

    func storyClosing(_ lang: String) -> String {
        lang == "en" ? "Good night\(name.isEmpty ? "" : ", \(name)")." : "לילה טוב\(name.isEmpty ? "" : ", \(name)")."
    }

    /** What to tell the model when the child talked to Puki (`heard` is what speech recognition got). */
    func listenSituation(_ heard: String, lang: String) -> String {
        if lang == "en" {
            let c = child(lang).capitalizedFirst
            let (they, them) = girl ? ("she", "her") : ("he", "him")
            if heard.isEmpty { return "\(c) talked to you, but the words weren't clear. Say something sweet back." }
            return "\(c) said to you: \"\(heard)\". (A toddler is talking, so it may sound a bit off; for example " +
                "something like '\(companionEn)' is probably your name.) Answer what \(they) said directly: if " +
                "\(they) said hello or called you, say hello back to \(them); if something was mentioned (an animal, " +
                "a color, a shape), say something about it. If it doesn't make sense, say something sweet."
        }
        if heard.isEmpty { return "\(child) \(girl ? "דיברה" : "דיבר") אל התוכי, אבל לא שמענו מילים ברורות. תגיד משהו מתוק בחזרה." }
        return "\(child) \(girl ? "אמרה" : "אמר") לך: \"\(heard)\". " +
            "(הדיבור של פעוטה, אז אולי זה נשמע קצת לא מדויק; למשל אם שמענו משהו כמו '\(Self.stripNikud(companion))' " +
            "זה כנראה השם שלך.) תענה ישירות על מה \(girl ? "שהיא" : "שהוא") אמר\(girl ? "ה" : ""): " +
            "אם \(girl ? "היא" : "הוא") \(girl ? "אמרה" : "אמר") שלום או \(girl ? "קראה" : "קרא") לך, תגיד שלום בחזרה; " +
            "אם הוזכר משהו (חיה, צבע, צורה), תגיד עליו משהו. אם לא מובן, תגיד משהו מתוק."
    }

    // ---- The filter every line passes ----

    /** A safe line, or nil. */
    func clean(_ raw: String, lang: String) -> String? {
        var text = KRegex("<[^>]+>").replace(raw, "")
        text = text.replacingOccurrences(of: "’", with: "'").replacingOccurrences(of: "‘", with: "'")
            .replacingOccurrences(of: "“", with: "\"").replacingOccurrences(of: "”", with: "\"")
        text = KRegex("\\s*[—–]\\s*").replace(text, lang == "en" ? ", " : " – ")
        text = text.trimmed().trimmingCharacters(in: CharacterSet(charactersIn: "\"")).trimmed()
        text = text.components(separatedBy: .newlines).first?.trimmed() ?? ""
        text = dropCompliments(text)
        if text.isEmpty || text.ktLength > 90 || KRegex("\\s+").split(text).count > 14 { return nil }
        // In English the child's name may stay in Hebrew letters (the voice reads it in Hebrew)
        let check = (lang == "en" && !name.isEmpty) ? text.replacingOccurrences(of: name, with: "Name") : text
        if !(lang == "en" ? Self.englishOk : Self.hebrewOk).matches(check) { return nil }
        // Whole words only ("רע" is bad, "רעבה" — hungry — is fine); Hebrew prefixes like ו/ה allowed
        let words = KRegex("[\\p{L}\\p{N}_']+").findAll(text.lowercased())
        if words.contains(where: { w in Self.banned.contains { b in !b.contains(" ") && (w == b || w.trimStart("והבלמש") == b) } }) { return nil }
        if Self.banned.contains(where: { $0.contains(" ") && text.lowercased().contains($0) }) { return nil }
        return text
    }

    private func dropCompliments(_ text: String) -> String {
        let sentences = KRegex("[^!?.]+[!?.]*").findAll(text).map { $0.trimmed() }.filter { !$0.isEmpty }
        let kept = sentences.filter { !Self.compliment.containsMatchIn($0) }
        return kept.isEmpty ? text : kept.joined(separator: " ")
    }

    /** What the voice actually reads: no nikud, misread words swapped. */
    func forVoice(_ text: String) -> String {
        var t = Self.stripNikud(text)
        for (word, instead) in Self.misread {
            t = KRegex("(?<![א-ת])([והבכלמש]?)\(word)(?![א-ת])").replace(t) { ($0[1] ?? "") + instead }
        }
        return t
    }

    // ---- Say it with Puki: did the child say the word? ----

    /** "word", "close" (a near miss or a related word like the animal's sound), or "tried". */
    func wordMatch(_ heard: String, target: String, alts: [String] = []) -> String {
        let ws = Self.words(heard)
        let goal = Self.words(target).joined()
        if ws.isEmpty || goal.isEmpty { return "tried" }
        let joined = ws.joined()
        if ws.contains(goal) || joined.contains(goal) { return "word" }
        if alts.map({ Self.words($0).joined() }).contains(where: { !$0.isEmpty && joined.contains($0) }) { return "close" }
        for w in ws {
            if Self.similarity(w, goal) >= 0.6 { return "close" }
            if w.first == goal.first && w.count <= goal.count { return "close" }  // the first sound ("פה" for "פרה")
        }
        return "tried"
    }

    private static let hebrewOk = KRegex("^[\\u0590-\\u05FF\\s,.!?'\"׳״\\-–]+$")
    private static let englishOk = KRegex("^[A-Za-z\\s,.!?'\\-]+$")
    private static let banned = ["טעות", "רע", "טיפש", "טיפשה", "מכוער", "מכוערת", "לא נכון",
        "wrong", "bad", "stupid", "dumb", "ugly", "idiot", "hate", "not right", "mistake"]
    private static let compliment = KRegex(
        "^(את|אתה|you'?re|you are)\\s+(כל כך |ממש |כזאת |כזה |מאוד |so |really |such an? )*" +
        "(מקסימה|מקסים|מתוקה|מתוק|חכמה|חכם|מדהימה|מדהים|מיוחדת|מיוחד|טובה|טוב|יפה|נהדרת|נהדר|" +
        "עושה (יופי|טוב|כל כך טוב)|sweet|smart|amazing|special|great|lovely|clever|wonderful|awesome|" +
        "brilliant|beautiful|the best|doing (great|so well|amazing))(?![\\p{L}\\p{N}_])", ignoreCase: true)
    private static let foodWords = ["האכיל", "אוכל", "fed ", "food"]
    private static let openers: [String: [String: [String]]] = [
        "he": ["food": ["יאמי", "מממ", "אמממ"], "other": ["הופ", "יש", "אוהו", "יופי", "טה-דה", ""]],
        "en": ["food": ["Yum", "Mmm", "Munch munch"], "other": ["Hooray", "Yay", "Ooh", "Ta-da", "Woohoo", ""]],
    ]
    /** Hebrew words the voice reads wrong, and what Puki says instead (as on the PC). */
    static let misread: [(String, String)] = [("הידד", "יש")]

    private static let nikud = KRegex("[\\u0591-\\u05C7]")
    static func stripNikud(_ s: String) -> String { nikud.replace(s, "") }

    private static let finals = Array("ךםןףץ")
    private static let plain = Array("כמנפצ")

    /** The words of `text`, plain: no nikud or punctuation, lower case, final letters as regular ones. */
    static func words(_ text: String) -> [String] {
        let t = String(stripNikud(text).lowercased().map { c in finals.firstIndex(of: c).map { plain[$0] } ?? c })
        let cleaned = KRegex("[^\\p{L}\\p{N}_\\s]").replace(t.precomposedStringWithCanonicalMapping, " ")
        return KRegex("\\s+").split(cleaned).filter { !$0.isEmpty }
    }

    /** Python's difflib.SequenceMatcher(None, a, b).ratio(), for short words. */
    static func similarity(_ a: String, _ b: String) -> Double {
        if a.isEmpty && b.isEmpty { return 1 }
        let x = Array(a), y = Array(b)
        return 2.0 * Double(matches(x[...], y[...])) / Double(x.count + y.count)
    }

    private static func matches(_ a: ArraySlice<Character>, _ b: ArraySlice<Character>) -> Int {
        // Longest common block, then recurse on both sides (Ratcliff/Obershelp, like difflib)
        var bestI = a.startIndex, bestJ = b.startIndex, bestK = 0
        for i in a.indices {
            for j in b.indices {
                var k = 0
                while i + k < a.endIndex && j + k < b.endIndex && a[i + k] == b[j + k] { k += 1 }
                if k > bestK { bestI = i; bestJ = j; bestK = k }
            }
        }
        if bestK == 0 { return 0 }
        return bestK + matches(a[a.startIndex..<bestI], b[b.startIndex..<bestJ]) +
            matches(a[(bestI + bestK)...], b[(bestJ + bestK)...])
    }
}
