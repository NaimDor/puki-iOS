import Foundation

/** One thing the child did today, in the words the brain learned for the bedtime recap. */
struct DayEvent: Hashable, Codable {
    let he: String
    let en: String
    init(_ he: String, _ en: String) { self.he = he; self.en = en }
}

/**
 * The bedtime recap: what the child did today (DayLog), said as train/games.py day_events says it, and the
 * check a recap must pass (events_named): it names at least two of the day's things.
 */
enum RecapContent {
    private static func the(_ word: String) -> String { word.hasPrefix("ה") ? word : "ה\(word)" }

    static func fed(_ animal: AnimalDef) -> DayEvent? {
        guard let food = level3Foods.first(where: { $0.id == animal.foodId }) else { return nil }
        return DayEvent("האכלת את \(the(animal.name.he)) ב\(food.name.he)", "you fed the \(animal.name.en.lowercased()) some \(food.name.en.lowercased())")
    }

    static func found(_ animal: AnimalDef) -> DayEvent { DayEvent("מצאת את \(the(animal.name.he)) במחבואים", "you found the \(animal.name.en.lowercased()) hiding") }
    static func color(_ color: ColorDef) -> DayEvent { DayEvent("מצאת את הצבע \(color.name.he)", "you found the color \(color.name.en.lowercased())") }
    static func shape(_ shape: ShapeDef) -> DayEvent { DayEvent("שמת את \(the(shape.name.he)) במקום", "you put the \(shape.name.en.lowercased()) in its place") }
    static func counted(_ round: CountingRound) -> DayEvent {
        DayEvent("ספרת \(round.targetNumber) \(round.itemName.he)", "you counted \(round.targetNumber) \(round.itemName.en)")
    }
    static func said(_ word: BilingualText) -> DayEvent { DayEvent("אמרת עם פוקי: \(word.he)", "you said \"\(word.en.lowercased())\" with Puki") }
    static func drew(_ color: ColorDef?) -> DayEvent {
        guard let color else { return DayEvent("ציירת ציור", "you drew a picture") }
        return DayEvent("ציירת ב\(color.name.he)", "you drew with \(color.name.en.lowercased())")
    }
    static func sticker(_ name: BilingualText) -> DayEvent { DayEvent("קיבלת מדבקה של \(name.he)", "you got a \(name.en.lowercased()) sticker") }

    /** The My Day games that are one thing all along, by their island (GameSession) id. */
    static let fixed: [String: DayEvent] = [
        "dance": DayEvent("רקדת למוזיקה", "you danced to the music"),
        "kitchen": DayEvent("אפית עוגה", "you baked a cake"),
        "garage": DayEvent("שטפת את המכונית", "you washed the car"),
        "tidy": DayEvent("סידרת את הצעצועים", "you tidied up the toys"),
        "table": DayEvent("ערכת את השולחן", "you set the table"),
    ]

    /** The recap's input for `events` (the brain learned two to five). */
    static func input(english: Bool, events: [DayEvent]) -> String {
        english ? "Bedtime recap. Today: " + events.map(\.en).joined(separator: "; ") + "."
            : "סיכום לפני השינה. היום: " + events.map(\.he).joined(separator: "; ") + "."
    }

    // Words every event shares, which say nothing about which one a line names (games._COMMON)
    private static let common: Set<String> = ["את", "היום", "מצאת", "found", "עם", "פוקי", "במקום", "מקום", "הצבע", "צבע", "מדבקה", "של", "you", "the", "some",
        "with", "puki", "its", "place", "color", "sticker", "today", "said", "got"]
    private static let words = KRegex("[\\p{L}\\p{N}_']+")

    private static func forms(_ word: String) -> Set<String> {
        var out: Set<String> = [word]
        for k in 1...2 where word.count - k >= 3 && word.take(k).allSatisfy({ "והבלמש".contains($0) }) { out.insert(word.drop(k)) }
        return out
    }

    private static func eventWords(_ input: String) -> [Set<String>] {
        input.substringAfter(":").trimEnd(".").components(separatedBy: ";").map { e in
            Set(words.findAll(e.lowercased()).filter { !common.contains($0) && $0.count >= 3 }.flatMap { forms($0) })
        }
    }

    /** Like `eventsNamed`, but a word two of the events share names neither. */
    static func eventsNamedApart(_ line: String, _ input: String) -> Int {
        let events = eventWords(input)
        var counts: [String: Int] = [:]
        for e in events { for w in e { counts[w, default: 0] += 1 } }
        let shared = Set(counts.filter { $0.value > 1 }.keys)
        let seen = Set(words.findAll(line.lowercased()).flatMap { forms($0) })
        return events.filter { !$0.subtracting(shared).isDisjoint(with: seen) }.count
    }

    /** How many of `input`'s events `line` names, by a word only that event has (games.events_named). */
    static func eventsNamed(_ line: String, _ input: String) -> Int {
        let seen = Set(words.findAll(line.lowercased()).flatMap { forms($0) })
        return eventWords(input).filter { !$0.isDisjoint(with: seen) }.count
    }
}
