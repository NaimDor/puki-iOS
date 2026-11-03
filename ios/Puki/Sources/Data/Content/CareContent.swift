import Foundation

/**
 * The caring game: someone needs a little help (Puki himself, the teddy, the doll, the bunny), the child gives
 * them something, and it helps or it doesn't yet. The sentences are the brain's, word for word.
 */
enum CareContent {
    /** Who needs help: Hebrew and English names (nil for Puki himself), and the Hebrew gender. */
    struct Patient: Hashable { let id: String; let he: String?; let en: String?; var feminine = false }
    static let patients = [Patient(id: "puki", he: nil, en: nil), Patient(id: "teddy", he: "דובי", en: "the teddy bear"),
        Patient(id: "doll", he: "הבובה", en: "the doll", feminine: true), Patient(id: "bunny", he: "הארנב", en: "the bunny")]

    /** What's wrong, for a him, a her and Puki himself ({le}: "to X", {X}: X), and what helps. */
    struct Problem: Hashable { let id, heM, heF, heYou, en, enYou: String; let helps: Set<String> }
    static let problems = [
        Problem(id: "cold", heM: "{le} קר", heF: "{le} קר", heYou: "לך קר", en: "{X} is cold", enYou: "you are cold", helps: ["blanket", "hug", "soup", "hat"]),
        Problem(id: "sneezy", heM: "{X} מתעטש", heF: "{X} מתעטשת", heYou: "אתה מתעטש", en: "{X} keeps sneezing", enYou: "you keep sneezing", helps: ["tissue", "soup"]),
        Problem(id: "bump", heM: "{le} יש מכה ברגל", heF: "{le} יש מכה ברגל", heYou: "לך יש מכה ברגל", en: "{X} has a bump on the leg", enYou: "you have a bump on your leg", helps: ["bandage", "kiss", "hug"]),
        Problem(id: "thunder", heM: "{X} מפחד מהרעם", heF: "{X} מפחדת מהרעם", heYou: "אתה מפחד מהרעם", en: "{X} is scared of the thunder", enYou: "you are scared of the thunder", helps: ["hug", "song", "blanket", "teddy"]),
        Problem(id: "dark", heM: "{X} מפחד מהחושך", heF: "{X} מפחדת מהחושך", heYou: "אתה מפחד מהחושך", en: "{X} is scared of the dark", enYou: "you are scared of the dark", helps: ["nightlight", "hug", "teddy"]),
        Problem(id: "thirsty", heM: "{X} צמא", heF: "{X} צמאה", heYou: "אתה צמא", en: "{X} is thirsty", enYou: "you are thirsty", helps: ["water"]),
        Problem(id: "hungry", heM: "{X} רעב", heF: "{X} רעבה", heYou: "אתה רעב", en: "{X} is hungry", enYou: "you are hungry", helps: ["soup", "apple"]),
        Problem(id: "sleepy", heM: "{X} עייף", heF: "{X} עייפה", heYou: "אתה עייף", en: "{X} is sleepy", enYou: "you are sleepy", helps: ["blanket", "song", "pillow"]),
        Problem(id: "play", heM: "{X} רוצה לשחק", heF: "{X} רוצה לשחק", heYou: "אתה רוצה לשחק", en: "{X} wants to play", enYou: "you want to play", helps: ["ball", "song", "book"]),
        Problem(id: "hot", heM: "{le} חם מהשמש", heF: "{le} חם מהשמש", heYou: "לך חם מהשמש", en: "{X} is hot from the sun", enYou: "you are hot from the sun", helps: ["hat", "water"]),
        Problem(id: "sad", heM: "{X} עצוב", heF: "{X} עצובה", heYou: "אתה עצוב", en: "{X} is sad", enYou: "you are sad", helps: ["hug", "kiss", "song", "ball"]),
    ]

    /** The gifts: Hebrew, English. */
    static let items = OrderedMap<String, (String, String)>([("blanket", ("שמיכה", "a blanket")), ("hug", ("חיבוק", "a hug")), ("bandage", ("פלסטר", "a bandage")),
        ("soup", ("מרק חם", "warm soup")), ("hat", ("כובע", "a hat")), ("tissue", ("טישו", "a tissue")),
        ("kiss", ("נשיקה", "a kiss")), ("song", ("שיר", "a song")), ("teddy", ("דובי", "a teddy bear")),
        ("nightlight", ("מנורת לילה", "a night light")), ("water", ("מים", "water")), ("apple", ("תפוח", "an apple")),
        ("pillow", ("כרית", "a pillow")), ("ball", ("כדור", "a ball")), ("book", ("ספר", "a book"))])

    /** Hebrew "to the ...": ל and the word, folding the article (הבובה: לבובה). */
    static func le(_ word: String) -> String { "ל" + (word.hasPrefix("ה") ? word.drop(1) : word) }

    /** Whether `item` helps `patient` with `problem` (the teddy doesn't comfort himself). */
    static func helps(_ patient: Patient, _ problem: Problem, _ item: String) -> Bool {
        problem.helps.contains(item) && !(patient.id == "teddy" && item == "teddy")
    }

    enum Outcome { case start, helps, notYet }

    /** The brain's input: the problem, and nothing given yet (`item` nil), or the gift and whether it helped. */
    static func input(english: Bool, patient: Patient, problem: Problem, item: String?) -> String {
        let he, en, gaveHe, betterHe, betterEn, toEn: String
        if let pHe = patient.he, let pEn = patient.en {
            he = (patient.feminine ? problem.heF : problem.heM).replacingOccurrences(of: "{X}", with: pHe).replacingOccurrences(of: "{le}", with: le(pHe))
            en = problem.en.replacingOccurrences(of: "{X}", with: pEn)
            gaveHe = "נתנו \(le(pHe))"
            betterHe = patient.feminine ? "עכשיו היא מרגישה טוב יותר" : "עכשיו הוא מרגיש טוב יותר"
            betterEn = patient.feminine ? "now she feels better" : "now he feels better"
            toEn = patient.feminine ? "her" : "him"
        } else {
            he = problem.heYou
            en = problem.enYou
            gaveHe = "נתנו לך"; betterHe = "עכשיו אתה מרגיש טוב יותר"; betterEn = "now you feel better"; toEn = "you"
        }
        let enCap = en.capitalizedFirst
        guard let item else { return english ? "Caring game: \(enCap). Nothing given yet." : "משחק הטיפול: \(he). עוד לא נתנו כלום." }
        let (ih, ie) = items[item]!
        if helps(patient, problem, item) {
            return english ? "Caring game: \(enCap). The child gave \(toEn): \(ie). That helps, \(betterEn)." : "משחק הטיפול: \(he). \(gaveHe): \(ih). זה עוזר, \(betterHe)."
        }
        return english ? "Caring game: \(enCap). The child gave \(toEn): \(ie). That's nice, but it doesn't help yet."
            : "משחק הטיפול: \(he). \(gaveHe): \(ih). זה נחמד, אבל זה עוד לא עוזר."
    }
}
