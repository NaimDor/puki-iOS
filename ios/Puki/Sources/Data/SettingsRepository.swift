import Foundation
import Combine

/** The parent's settings, kept in UserDefaults (DataStore's "settings" on Android, same keys). */
@MainActor
final class SettingsRepository: ObservableObject {
    @Published private(set) var settings = AppSettings()
    private let d: UserDefaults

    private enum Keys {
        static let language = "language", gender = "gender", soundEnabled = "sound_enabled",
            musicEnabled = "music_enabled", musicVolume = "music_volume", voiceVolume = "voice_volume",
            sfxVolume = "sfx_volume", timeLimit = "time_limit_minutes", childName = "child_name",
            onboardingDone = "onboarding_done", companionEnabled = "companion_enabled",
            brainOnDevice = "brain_on_device", brainLanguages = "brain_languages",
            rememberBetween = "remember_between", answersOutLoud = "answers_out_loud",
            playedMs = "played_ms", lastSeen = "play_last_seen", timeUp = "time_up",
            hiddenIslands = "hidden_islands"
    }

    init(defaults: UserDefaults = UserDefaults(suiteName: "settings") ?? .standard) {
        d = defaults
        reload()
    }

    private func bool(_ k: String, _ def: Bool) -> Bool { d.object(forKey: k) == nil ? def : d.bool(forKey: k) }
    private func float(_ k: String, _ def: Float) -> Float { d.object(forKey: k) == nil ? def : d.float(forKey: k) }

    private func reload() {
        settings = AppSettings(
            language: Language(rawValue: d.string(forKey: Keys.language) ?? "") ?? .hebrew,
            gender: Gender(rawValue: d.string(forKey: Keys.gender) ?? "") ?? .girl,
            soundEnabled: bool(Keys.soundEnabled, true),
            musicEnabled: bool(Keys.musicEnabled, true),
            musicVolume: float(Keys.musicVolume, 1),
            voiceVolume: float(Keys.voiceVolume, 1),
            sfxVolume: float(Keys.sfxVolume, 1),
            timeLimitMinutes: d.integer(forKey: Keys.timeLimit),
            childName: d.string(forKey: Keys.childName) ?? "",
            onboardingDone: bool(Keys.onboardingDone, false),
            loaded: true,
            companionEnabled: bool(Keys.companionEnabled, true),
            brainOnDevice: bool(Keys.brainOnDevice, false),
            brainLanguages: d.string(forKey: Keys.brainLanguages).flatMap { BrainLanguages(rawValue: $0) },
            rememberBetween: bool(Keys.rememberBetween, false),
            answersOutLoud: bool(Keys.answersOutLoud, true),
            hiddenIslands: Set(d.stringArray(forKey: Keys.hiddenIslands) ?? [])
        )
    }

    private func set(_ value: Any?, _ key: String) {
        d.set(value, forKey: key)
        reload()
    }

    func updateLanguage(_ l: Language) { set(l.rawValue, Keys.language) }
    func updateSound(_ on: Bool) { set(on, Keys.soundEnabled) }
    func updateMusic(_ on: Bool) { set(on, Keys.musicEnabled) }
    func updateVolumes(music: Float? = nil, voice: Float? = nil, sfx: Float? = nil) {
        if let music { d.set(min(max(music, 0), 1), forKey: Keys.musicVolume) }
        if let voice { d.set(min(max(voice, 0), 1), forKey: Keys.voiceVolume) }
        if let sfx { d.set(min(max(sfx, 0), 1), forKey: Keys.sfxVolume) }
        reload()
    }
    func updateTimeLimit(_ minutes: Int) { set(minutes, Keys.timeLimit) }
    func updateChildName(_ name: String) {
        set(String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(30)), Keys.childName)
    }
    func setOnboardingDone() { set(true, Keys.onboardingDone) }
    func updateCompanionEnabled(_ on: Bool) { set(on, Keys.companionEnabled) }
    func updateGender(_ g: Gender) { set(g.rawValue, Keys.gender) }
    func updateHiddenIslands(_ ids: Set<String>) { set(Array(ids).sorted(), Keys.hiddenIslands) }
    func updateAnswersOutLoud(_ on: Bool) { set(on, Keys.answersOutLoud) }
    func updateBrainOnDevice(_ on: Bool) { set(on, Keys.brainOnDevice) }
    func updateRememberBetween(_ on: Bool) { set(on, Keys.rememberBetween) }
    func updateBrainLanguages(_ l: BrainLanguages) { set(l.rawValue, Keys.brainLanguages) }

    /** The time limit's clock: time played, when the game was last on screen, and whether time was up. */
    struct PlayClock { var playedMs: Int64; var lastSeen: Int64; var timeUp: Bool }

    func playClock() -> PlayClock {
        PlayClock(playedMs: (d.object(forKey: Keys.playedMs) as? NSNumber)?.int64Value ?? 0,
                  lastSeen: (d.object(forKey: Keys.lastSeen) as? NSNumber)?.int64Value ?? 0,
                  timeUp: d.bool(forKey: Keys.timeUp))
    }

    func savePlayClock(_ c: PlayClock) {
        d.set(NSNumber(value: c.playedMs), forKey: Keys.playedMs)
        d.set(NSNumber(value: c.lastSeen), forKey: Keys.lastSeen)
        d.set(c.timeUp, forKey: Keys.timeUp)
    }
}

func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
