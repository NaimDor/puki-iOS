import Foundation

enum Language: String, CaseIterable, Codable {
    case hebrew = "HEBREW", english = "ENGLISH"
}

enum BrainLanguages: String, CaseIterable, Codable {
    case hebrew = "HEBREW", english = "ENGLISH", both = "BOTH"
    var hebrew: Bool { self != .english }
    var english: Bool { self != .hebrew }
}

enum Gender: String, CaseIterable, Codable {
    case boy = "BOY", girl = "GIRL"
}

/**
 * Text in both languages. `he` is the default Hebrew form; lines that address
 * the child directly can add `heGirl` for the feminine form.
 */
struct BilingualText: Hashable, Codable {
    let he: String
    let en: String
    var heGirl: String? = nil

    init(_ he: String, _ en: String, heGirl: String? = nil) {
        self.he = he; self.en = en; self.heGirl = heGirl
    }
    init(he: String, en: String, heGirl: String? = nil) {
        self.he = he; self.en = en; self.heGirl = heGirl
    }

    func resolve(_ language: Language, _ gender: Gender = .boy) -> String {
        switch language {
        case .hebrew: return (gender == .girl && heGirl != nil) ? heGirl! : he
        case .english: return en
        }
    }
}

struct AppSettings: Equatable {
    var language: Language = .hebrew
    var gender: Gender = .girl
    var soundEnabled = true
    var musicEnabled = true
    /** Volumes, 0..1: music, voices (recorded lines and Puki), sound effects. */
    var musicVolume: Float = 1
    var voiceVolume: Float = 1
    var sfxVolume: Float = 1
    var timeLimitMinutes = 0  // 0 = no limit
    var childName = ""
    var onboardingDone = false
    /** False until the stored settings have been read (so the welcome screen doesn't flash). */
    var loaded = false
    var companionEnabled = true
    /** Puki thinks on this device (models downloaded); off, he plays his recorded lines. */
    var brainOnDevice = false
    /** Which languages the parent picked for Puki's brain; nil until they pick. */
    var brainLanguages: BrainLanguages? = nil
    /** "Puki remembers between sessions" (DayLog, MomentMemory). Off: nothing outlives the session. */
    var rememberBetween = false
    /** After a game's question, Puki listens a few seconds for the answer said out loud. */
    var answersOutLoud = true
    /** Islands a parent took off the map (fewer choices at two), by id. */
    var hiddenIslands: Set<String> = []
}

struct GameProgress: Equatable {
    var totalStars = 0
    var rewardedStars = 0  // up to here her stickers have had their moment on an end card
    var albumSeenStars = 0 // the stars she had when she last opened the album
}

/** How parents reach Puki's makers. */
enum Contact {
    static let email = "hello@pukiparrot.com"
    static let reportEmail = "hello+report@pukiparrot.com"
}

/** [count] things to pick from, in a random order: [target] and others from [pool], no doubles. */
func pickChoices<T, ID: Hashable>(_ target: T, _ pool: [T], _ count: Int, id: (T) -> ID) -> [T] {
    var seen = Set<ID>([id(target)])
    var others: [T] = []
    for p in pool.shuffled() where !seen.contains(id(p)) {
        seen.insert(id(p)); others.append(p)
    }
    return ([target] + others.prefix(max(0, count - 1))).shuffled()
}
