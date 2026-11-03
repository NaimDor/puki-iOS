import Foundation

/**
 * Silly Puki: Puki says something wrong about a picture on purpose, the child puts him right, and he laughs
 * and says the real answer. The brain was trained on these exact sentences, so they're built here word for word.
 */
enum SillyKind { case sound, food, color, shape, count }

/** What the picture shows: an animal, a thing of one color, a shape, or some things to count. */
enum SillyPicture: Hashable {
    case animal(AnimalDef)
    case thing(String)
    case shape(ShapeDef)
    case count(CountingRound)
}

struct SillyItem: Hashable {
    let group: String
    let kind: SillyKind
    /** The brain's input, in Hebrew and English. */
    let he: String
    let en: String
    /** The real answer (a sound, a food, a color, a shape, a number). */
    let truthHe: String
    let truthEn: String
    /** Nil when the app has no picture for it. */
    let picture: SillyPicture?
    /** The recorded line that names the picture, and the one that says the real answer. */
    let nameKey: String?
    let truthKey: String
    /** The real answer's id, for the tap choices. */
    let answerId: String
}

enum SillyContent {
    /** Animal sounds a 2-year-old knows in both languages (games.SOUNDS). */
    static let sounds: [String: (String, String)] = ["cow": ("מו", "moo"), "dog": ("הב הב", "woof"), "cat": ("מיאו", "meow"), "sheep": ("מה", "baa"),
        "duck": ("גע גע", "quack"), "pig": ("אוינק", "oink"), "horse": ("היהי", "neigh")]

    private struct Colored { let he, en: String; let she: Bool; let color: String; let res: String?; let nameKey: String? }
    private static let colored = [
        Colored(he: "בננה", en: "banana", she: true, color: "yellow", res: "ic_food_banana", nameKey: "voice_food_banana"),
        Colored(he: "תות", en: "strawberry", she: false, color: "red", res: nil, nameKey: nil),
        Colored(he: "עגבנייה", en: "tomato", she: true, color: "red", res: nil, nameKey: nil),
        Colored(he: "דשא", en: "grass", she: false, color: "green", res: "ic_food_grass", nameKey: "voice_food_grass"),
        Colored(he: "מלפפון", en: "cucumber", she: false, color: "green", res: nil, nameKey: nil),
        Colored(he: "שמיים", en: "sky", she: false, color: "blue", res: nil, nameKey: nil),
        Colored(he: "שמש", en: "sun", she: true, color: "yellow", res: "ic_sticker_sun", nameKey: nil),
        Colored(he: "גזר", en: "carrot", she: false, color: "orange", res: "ic_food_carrot", nameKey: "voice_food_carrot"),
        Colored(he: "תפוז", en: "orange", she: false, color: "orange", res: nil, nameKey: nil),
        Colored(he: "שלג", en: "snow", she: false, color: "white", res: nil, nameKey: nil),
        Colored(he: "חלב", en: "milk", she: false, color: "white", res: nil, nameKey: nil),
        Colored(he: "שוקולד", en: "chocolate", she: false, color: "brown", res: nil, nameKey: nil),
        Colored(he: "חציל", en: "eggplant", she: false, color: "purple", res: nil, nameKey: nil),
    ]
    private static let the: Set<String> = ["sky", "sun", "grass", "snow", "milk"]
    private static let plainShapes = ["circle", "square", "triangle", "star", "heart"]

    /** A number as Puki may say it (games.NUMBER_WORDS). */
    static let numberWords: [String: [String]] = {
        let w: [[String]] = [[], ["אחד", "אחת", "one"], ["שניים", "שתיים", "שני", "שתי", "two"], ["שלושה", "שלוש", "three"],
            ["ארבעה", "ארבע", "four"], ["חמישה", "חמש", "five"], ["שישה", "שש", "six"], ["שבעה", "שבע", "seven"],
            ["שמונה", "eight"], ["תשעה", "תשע", "nine"], ["עשרה", "עשר", "ten"]]
        var out: [String: [String]] = [:]
        for (n, words) in w.enumerated() { out["\(n)"] = words }
        return out
    }()

    private static func a(_ word: String) -> String { ("aeiou".contains(word.first!) ? "an " : "a ") + word }

    private static func item(_ group: String, _ kind: SillyKind, _ picHe: String, _ picEn: String, _ she: Bool, _ aboutHe: (String, String), _ aboutEn: String,
                             _ truthHe: String, _ truthEn: String, _ picture: SillyPicture?, _ nameKey: String?, _ truthKey: String, _ answerId: String) -> SillyItem {
        SillyItem(group: group, kind: kind,
                  he: "בתמונה: \(picHe). תגיד טעות מצחיקה אחת בכוונה, על \(she ? aboutHe.1 : aboutHe.0) (באמת: \(truthHe)).",
                  en: "Picture: \(picEn). Make one silly mistake on purpose, about its \(aboutEn) (really: \(truthEn)).",
                  truthHe: truthHe, truthEn: truthEn, picture: picture, nameKey: nameKey, truthKey: truthKey, answerId: answerId)
    }

    /** Every picture and mistake the brain was trained on; the game plays the ones with a picture. */
    static let all: [SillyItem] = {
        let foods = Dictionary(level3Foods.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let cols = Dictionary(level3Colors.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        var out: [SillyItem] = []
        for an in allAnimals.distinctBy({ $0.id }) {
            let she = feminineAnimals.contains(an.id)
            if let (he, en) = sounds[an.id] {
                out.append(item("sound:\(an.id)", .sound, an.name.he, a(an.name.en.lowercased()), she, ("הקול שלו", "הקול שלה"), "sound",
                                he, en, .animal(an), "voice_animal_\(an.id)", "voice_sound_\(an.id)", an.id))
            }
            if let f = foods[an.foodId] {
                out.append(item("food:\(an.id)", .food, an.name.he, a(an.name.en.lowercased()), she, ("מה שהוא אוכל", "מה שהיא אוכלת"), "food",
                                f.name.he, f.name.en.lowercased(), .animal(an), "voice_animal_\(an.id)", "voice_food_\(f.id)", f.id))
            }
        }
        for c in colored {
            let col = cols[c.color]!
            out.append(item("color:\(c.en)", .color, c.he, the.contains(c.en) ? "the \(c.en)" : a(c.en), c.she, ("הצבע שלו", "הצבע שלה"), "color",
                            col.name.he, col.name.en.lowercased(), c.res.map { .thing($0) }, c.nameKey, "voice_color_\(col.id)", col.id))
        }
        for s in level3Shapes where plainShapes.contains(s.id) {
            out.append(item("shape:\(s.id)", .shape, s.name.he, a(s.name.en.lowercased()), false, ("הצורה שלו", "הצורה שלו"), "shape",
                            s.name.he, s.name.en.lowercased(), .shape(s), nil, "voice_shape_\(s.id)", s.id))
        }
        for r in (level1CountingRounds + level2CountingRounds + level3CountingRounds) where r.targetNumber > 1 {
            let n = r.targetNumber
            let group = "count:\(n):\(r.itemName.en)"
            if !out.contains(where: { $0.group == group }) {
                out.append(SillyItem(group: group, kind: .count,
                    he: "בתמונה: \(n) \(r.itemName.he). תגיד טעות מצחיקה אחת בכוונה, בכמה יש (באמת: \(n)).",
                    en: "Picture: \(n) \(r.itemName.en). Make one silly mistake on purpose, about how many (really: \(n)).",
                    truthHe: "\(n)", truthEn: "\(n)", picture: .count(r), nameKey: nil, truthKey: "voice_number_\(n)", answerId: "\(n)"))
            }
        }
        return out
    }()

    /** The ones the game can show. */
    static var playable: [SillyItem] { all.filter { $0.picture != nil } }

    enum Answer { case right, other, nothing }

    /** The input for Puki's next line (silly_fix): his silly `line`, the real answer, and what the child did. */
    static func fixInput(english: Bool, line: String, truth: String, answer: Answer, heard: String = "") -> String {
        let said = heard.trimmed().split(whereSeparator: { $0.isWhitespace }).prefix(4).joined(separator: " ").ifEmpty("…")
        if !english {
            let tail: String
            switch answer {
            case .right: tail = "ענו לו: \"\(truth)!\". זה נכון!"
            case .other: tail = "ענו לו: \"\(said)\"."
            case .nothing: tail = "ענו לו: כלום, רק צחקו."
            }
            return "פוקי אמר בצחוק: \"\(line)\" (באמת: \(truth)). " + tail
        }
        let tail: String
        switch answer {
        case .right: tail = "The child answered: \"\(truth)!\". That's right!"
        case .other: tail = "The child answered: \"\(said)\"."
        case .nothing: tail = "The child answered: nothing, just laughed."
        }
        return "Puki said as a joke: \"\(line)\" (really: \(truth)). " + tail
    }

    private static let words = KRegex("[\\p{L}\\p{N}_']+")

    /** Whether `line` says the real answer (build.said_truth). */
    static func saysTruth(_ line: String, _ truth: String) -> Bool {
        let ws = words.findAll(line.lowercased())
        let colorForms = LookContent.colors.values.first(where: { $0.0[0] == truth || $0.1 == truth })?.0 ?? []
        for alt in [truth] + (numberWords[truth] ?? []) + colorForms {
            let t = words.findAll(alt.lowercased())
            if t.isEmpty { continue }
            for i in ws.indices {
                let span = Array(ws[i..<min(i + t.count, ws.count)])
                if span == t || span.map({ $0.trimStart("והבלמש") }) == t { return true }
            }
        }
        return false
    }
}
