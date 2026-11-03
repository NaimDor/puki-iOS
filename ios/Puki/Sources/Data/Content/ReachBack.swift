import Foundation

/** A game moment Puki remembers (MomentMemory): when, in which game (its screen), and what happened. */
struct Moment: Hashable {
    let t: Int64
    let he: String
    let en: String
    var game: String = ""
}

/**
 * Puki reaching back ("like the cow yesterday!"): which earlier moment a new one links to, said as the brain
 * learned it, and the check his line must pass: it names the earlier thing.
 */
enum ReachBack {
    private static let stop: Set<String> = ["במשחק", "הילדה", "הילד", "בחרו", "נכון", "מחפשים", "מחכה", "שוב", "אבל", "חיפשנו", "הצבע", "צבע",
        "האכילה", "האכיל", "אכילה", "אכיל", "ציירה", "צייר", "מצאו", "מצאה", "מצא", "לאוכל", "אוכל",
        "the", "child", "game", "picked", "that", "right", "looking", "for", "again", "waiting", "and", "with",
        "fed", "drew", "found", "food",
        "אתה", "הנה", "איזה", "הזה", "הזאת", "שלה", "שלו", "איפה", "כמה", "אני", "רוצה", "רוצים", "עוד", "היום",
        "you", "your", "this", "what", "said", "she", "her", "his", "want", "more", "look", "here", "there"]
    private static let prefixes = "הובלשמכ"
    private static let finals: [Character: Character] = ["ך": "כ", "ם": "מ", "ן": "נ", "ף": "פ", "ץ": "צ"]
    private static let stopForms: Set<String> = Set(stop.flatMap { forms($0) })

    /** The forms a word may come in, so "הפילים" meets "פיל" and "dogs" meets "dog" (memory._forms). */
    static func forms(_ word: String) -> Set<String> {
        // (niqqud off: by scalars, as a mark joins its letter in one Character)
        let scalars = word.lowercased().unicodeScalars.filter { !($0.value >= 0x0591 && $0.value <= 0x05C7) }
        let w = String(String.UnicodeScalarView(scalars).map { finals[Character(String($0))] ?? Character(String($0)) })
        var bases: Set<String> = [w]
        for n in 1...2 where w.count - n >= 2 && w.take(n).allSatisfy({ prefixes.contains($0) }) { bases.insert(w.drop(n)) }
        var out = Set<String>()
        for b in bases {
            out.insert(b)
            for end in ["ימ", "ות", "ה", "es", "s"] where b.hasSuffix(end) && b.count - end.count >= 2 { out.insert(String(b.dropLast(end.count))) }
        }
        return out
    }

    /** What a moment was about ("פיל", "בננה"), without the game's name before the colon or the words every moment has. */
    static func contentWords(_ text: String, ignore: Set<String> = []) -> Set<String> {
        var out = Set<String>()
        let body = String(text.substringAfter(":").map { ($0.isLetter || $0.isNumber) ? $0 : " " })
        for w in body.split(separator: " ").map(String.init) where w.count >= 3 {
            let f = forms(w)
            if f.contains(where: { stopForms.contains($0) || ignore.contains($0) }) { continue }
            out.formUnion(f)
        }
        return out
    }

    private static let nonLetters = KRegex("[^\\p{L}]+")

    /** The moment with what it was about (and its numbers) taken out: two moments of one kind share it. */
    private static func skeleton(_ text: String) -> String {
        let words = contentWords(text)
        return nonLetters.split(text).filter { !$0.isEmpty && forms($0).isDisjoint(with: words) }.joined(separator: " ")
    }

    /**
     * An earlier moment to reach back to for `he` in `game`: a few minutes back at least, in the same
     * game, and of the same kind or about the same thing; with when it was ("קודם היום", "אתמול", "לפני יומיים").
     */
    static func related(_ he: String, game: String, history: [Moment], nowMs: Int64, today: Date = Date(),
                        ignore: Set<String> = []) -> ((String, String), Moment)? {
        if game.isEmpty { return nil }
        let words = contentWords(he, ignore: ignore)
        let shape = skeleton(he)
        let cal = Calendar.current
        for m in history.reversed() {
            if m.t > nowMs - gapMs || m.he == he || m.game != game { continue }
            if !(!words.isDisjoint(with: contentWords(m.he, ignore: ignore)) || skeleton(m.he) == shape) { continue }
            let day = cal.startOfDay(for: Date(timeIntervalSince1970: Double(m.t) / 1000))
            let days = cal.dateComponents([.day], from: day, to: cal.startOfDay(for: today)).day ?? 99
            switch days {
            case 0: return (("קודם היום", "earlier today"), m)
            case 1: return (("אתמול", "yesterday"), m)
            case 2: return (("לפני יומיים", "2 days ago"), m)
            default: continue
            }
        }
        return nil
    }

    private static let nonWord = KRegex("[^\\p{L}\\p{N}]+")

    /** Whether `line` says `word` (in any of its forms: "להר" says "הר", "dogs" says "dog"). */
    static func says(_ line: String, _ word: String) -> Bool {
        let want = Set(word.split(separator: " ").flatMap { forms(String($0)) })
        return nonWord.split(line).filter { !$0.isEmpty }.contains { !forms($0).isDisjoint(with: want) }
    }

    /** Whether `line` names what only the earlier moment had: build.py's check. */
    static func namesEarlier(_ line: String, input: String, earlier: String) -> Bool {
        let only = contentWords(earlier.substringAfter(":")).subtracting(contentWords(input))
        return !contentWords(line).isDisjoint(with: only)
    }

    /** How long ago a moment must be to be "before" (not the one just now). */
    static let gapMs: Int64 = 4 * 60_000
}
