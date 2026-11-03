import Foundation

/**
 * The story with choices: Puki tells a little animal's story one sentence at a time, and the child picks where
 * it goes, who it meets and what it finds. The sentences sent to the brain are the trainer's, word for word.
 */
enum StoryContent {
    static let heroes = ["cow", "dog", "cat", "elephant", "lion", "rabbit", "duck", "sheep", "monkey", "giraffe", "penguin", "horse"]
    static let friends = ["dog", "cat", "duck", "sheep", "monkey", "rabbit", "penguin", "horse", "elephant", "cow"]
    /** id to (Hebrew, English). */
    static let places = OrderedMap<String, (String, String)>([("sea", ("הים", "the sea")), ("forest", ("היער", "the forest")), ("garden", ("הגינה", "the garden")),
        ("farm", ("החווה", "the farm")), ("playground", ("הגן", "the playground")), ("hill", ("ההר", "the hill"))])
    static let things = OrderedMap<String, (String, String)>([("banana", ("בננה", "a banana")), ("apple", ("תפוח", "an apple")), ("ball", ("כדור", "a ball")),
        ("flower", ("פרח", "a flower")), ("star", ("כוכב", "a star")), ("rainbow", ("קשת", "a rainbow")),
        ("balloon", ("בלון", "a balloon")), ("cake", ("עוגה", "a cake"))])

    static let steps = 5  // the start, a place, a friend, a thing, the end

    struct Plan { let hero: AnimalDef; let place: String; let friend: AnimalDef; let thing: String }

    private static func she(_ a: AnimalDef) -> Bool { feminineAnimals.contains(a.id) }

    /** What the child picked at `step`, as the brain learned it. */
    static func stepText(_ plan: Plan, english: Bool, step: Int) -> String {
        if !english {
            switch step {
            case 0: return "הסיפור מתחיל"
            case 1: return "בחרו ללכת \(CareContent.le(places[plan.place]!.0))"
            case 2: return "בחרו חבר: \(plan.friend.name.he)"
            case 3: return "בחרו: \(things[plan.thing]!.0)"
            default: return "הסוף, " + (she(plan.hero) ? "הגיבורה חוזרת" : "הגיבור חוזר") + " הביתה לישון"
            }
        }
        switch step {
        case 0: return "the story begins"
        case 1: return "the child picked going to \(places[plan.place]!.1)"
        case 2: return "the child picked a friend: a \(plan.friend.name.en.lowercased())"
        case 3: return "the child picked: \(things[plan.thing]!.1)"
        default: return "the end, the hero goes home to sleep"
        }
    }

    static func hero(_ plan: Plan, english: Bool) -> String {
        if !english {
            return (she(plan.hero) ? "הגיבורה: " : "הגיבור: ") + plan.hero.name.he + (she(plan.hero) ? " קטנה" : " קטן")
        }
        return "Hero: a little " + plan.hero.name.en.lowercased()
    }

    /** The brain's input for `step`: the hero, the story so far and what was picked now. */
    static func input(_ plan: Plan, english: Bool, soFar: [String], step: Int) -> String {
        if !english {
            let past = soFar.isEmpty ? "" : " עד עכשיו: \(soFar.joined(separator: " "))"
            return "סיפור עם בחירות. \(hero(plan, english: false)).\(past) עכשיו: \(stepText(plan, english: false, step: step))."
        }
        let past = soFar.isEmpty ? "" : " So far: \(soFar.joined(separator: " "))"
        return "Story with choices. \(hero(plan, english: true)).\(past) Now: \(stepText(plan, english: true, step: step))."
    }

    /** What a sentence must not name: the places, friends and things the child didn't pick, from the steps so far. */
    static func notPicked(_ plan: Plan, english: Bool, step: Int, offered: [Int: [String]]) -> [String] {
        guard step >= 1 else { return [] }
        return (1...min(step, 3)).flatMap { s -> [String] in
            let picked = s == 1 ? plan.place : s == 2 ? plan.friend.id : plan.thing
            return (offered[s] ?? []).filter { $0 != picked }.compactMap { id -> String? in
                switch s {
                case 1: return places[id].map { english ? $0.1.removePrefix("the ") : $0.0.removePrefix("ה") }
                case 2: return allAnimals.first(where: { $0.id == id }).map { english ? $0.name.en.lowercased() : $0.name.he }
                default: return things[id].map { english ? $0.1.substringAfter(" ") : $0.0 }
                }
            }
        }
    }

    /** What a step's sentence must name (the place, the friend, the thing picked), or nil. */
    static func pickWord(_ plan: Plan, english: Bool, step: Int) -> String? {
        switch step {
        case 1: let p = places[plan.place]!; return english ? p.1.removePrefix("the ") : p.0.removePrefix("ה")
        case 2: return english ? plan.friend.name.en.lowercased() : plan.friend.name.he
        case 3: let t = things[plan.thing]!; return english ? t.1.substringAfter(" ") : t.0
        default: return nil
        }
    }
}
