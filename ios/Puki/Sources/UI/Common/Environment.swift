import SwiftUI

/** Everything the game shares (MainActivity's players, PukiApplication's repositories, the brain). */
@MainActor
final class AppModel: ObservableObject {
    let sfx = SfxPlayer()
    let music = MusicPlayer()
    let voice = VoicePlayer()
    let companion: Companion
    let settingsRepository = SettingsRepository()
    let progressRepository = ProgressRepository()
    let deviceModels = DeviceModels()
    private(set) lazy var localBrain = LocalBrain(models: deviceModels)
    private var brainMade = false
    /** The device brain was put to sleep while the game was away; the game wakes it on its return. */
    var brainSlept = false

    init() {
        companion = Companion(voicePlayer: voice)
        Self.keepOffBackups()
        #if DEBUG
        // Screenshot mode (debug builds only): `-PukiScreenshot <route> -PukiLang en|he` skips the welcome screen
        let d = UserDefaults.standard
        if TestLaunch.uiTest {
            settingsRepository.updateLanguage(d.string(forKey: "PukiLang") == "en" ? .english : .hebrew)
            settingsRepository.updateGender(d.string(forKey: "PukiGender") == "boy" ? .boy : .girl)
            settingsRepository.updateTimeLimit(d.integer(forKey: "PukiTimeLimit"))
            settingsRepository.updateCompanionEnabled(true)
            settingsRepository.updateAnswersOutLoud(false)  // (no microphone prompt in the pictures)
            settingsRepository.setOnboardingDone()
        }
        #endif
    }

    /**
     * The brain, the lines with the child's name and what Puki remembers live in Application Support: kept out of
     * iCloud and computer backups, as on Android, so they never leave the device.
     */
    private static func keepOffBackups() {
        let fm = FileManager.default
        guard var dir = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else { return }
        try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? dir.setResourceValues(values)
    }

    var brain: LocalBrain {
        brainMade = true
        return localBrain
    }

    /** Gives the device brain's models back while the game is away (off the main thread). */
    func sleepBrain() {
        guard brainMade else { return }
        brainSlept = true
        let b = localBrain
        Task.detached { b.close() }
    }
}

// The game's CompositionLocals, as SwiftUI environment values

private struct LanguageKey: EnvironmentKey { static let defaultValue = Language.hebrew }
private struct GenderKey: EnvironmentKey { static let defaultValue = Gender.girl }
private struct PausedKey: EnvironmentKey { static let defaultValue = false }
private struct MoodKey: EnvironmentKey { static let defaultValue: ReactionState? = nil }
private struct OpenAlbumKey: EnvironmentKey { static let defaultValue: (() -> Void)? = nil }
private struct AmbientHeldKey: EnvironmentKey { static let defaultValue = false }
private struct SettingsKey: EnvironmentKey { static let defaultValue = AppSettings() }

extension EnvironmentValues {
    var language: Language { get { self[LanguageKey.self] } set { self[LanguageKey.self] = newValue } }
    var gender: Gender { get { self[GenderKey.self] } set { self[GenderKey.self] = newValue } }
    /** True while the game is paused under the time limit's goodbye: no nudges, no listening. */
    var gamePaused: Bool { get { self[PausedKey.self] } set { self[PausedKey.self] = newValue } }
    /** Puki in the corner of the current game: confetti makes him hop, a miss makes him tilt his head. */
    var pukiMood: ReactionState? { get { self[MoodKey.self] } set { self[MoodKey.self] = newValue } }
    /** Opens the sticker album, from an end card that's just given her a sticker. */
    var openAlbum: (() -> Void)? { get { self[OpenAlbumKey.self] } set { self[OpenAlbumKey.self] = newValue } }
    /** The idle motion stands still (under an end card). */
    var ambientHeld: Bool { get { self[AmbientHeldKey.self] } set { self[AmbientHeldKey.self] = newValue } }
    var appSettings: AppSettings { get { self[SettingsKey.self] } set { self[SettingsKey.self] = newValue } }
}

extension BilingualText {
    func callAsFunction(_ language: Language, _ gender: Gender = .boy) -> String { resolve(language, gender) }
}

/** Under this many points tall the screen is a phone on its side: games have to drop what's optional. */
let compactHeight: CGFloat = 440

/** True on a short screen (an iPhone on its side). */
@MainActor func isCompact() -> Bool {
    let b = UIScreen.main.bounds
    return min(b.width, b.height) < compactHeight
}

/** Waits until the game isn't paused (returns at once if it isn't). */
@MainActor func awaitUnpaused(_ paused: () -> Bool) async {
    while paused() && !Task.isCancelled { await delay(100) }
}

// The theme

enum PukiColors {
    static let skyBlue = Color(argb: 0xFF87CEEB)
    static let grassGreen = Color(argb: 0xFF7EC850)
    static let sunYellow = Color(argb: 0xFFFFD700)
    static let coralPink = Color(argb: 0xFFFF7F7F)
    static let softPurple = Color(argb: 0xFFB39DDB)
    static let warmOrange = Color(argb: 0xFFFFB74D)
    static let cloudWhite = Color(argb: 0xFFF5F5F5)
    static let earthBrown = Color(argb: 0xFF8D6E63)
}

enum PukiFont {
    static func rounded(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font { .system(size: size, weight: weight, design: .rounded) }
    static let headlineLarge = rounded(36, .bold)
    static let headlineMedium = rounded(28, .bold)
    static let headlineSmall = rounded(24, .regular)
    static let titleLarge = rounded(24, .semibold)
    static let titleMedium = rounded(16, .medium)
    static let bodyLarge = rounded(20)
    static let bodyMedium = rounded(14)
    static let labelLarge = rounded(18, .medium)
}

extension View {
    /** Soft dark halo so white text stays readable on light backgrounds. */
    func textHalo() -> some View { shadow(color: .black.opacity(0.33), radius: 4, x: 0, y: 2) }
}
