import SwiftUI

@main
struct PukiApp: App {
    @StateObject private var app = AppModel()

    init() {
        AudioSetup.configure()
    }

    var body: some SwiftUI.Scene {
        WindowGroup {
            RootView(settingsRepo: app.settingsRepository)
                .environmentObject(app)
                .statusBarHidden(true)
                .persistentSystemOverlays(.hidden)
                .preferredColorScheme(.light)
        }
    }
}

/** An hour away from the game starts a new time-limit allowance. */
private let restForNewAllowanceMs: Int64 = 60 * 60 * 1000
/** A parent closing the goodbye gives this much more play before the next one. */
private let extensionMs: Int64 = 10 * 60 * 1000
/** How long the game can be away before the device brain gives its memory back. */
private let brainRestMs = 2 * 60 * 1000

/** The top of the game (PukiApp.kt): settings to players, the brain choice, the time limit, onboarding or the map. */
struct RootView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @ObservedObject var settingsRepo: SettingsRepository
    @State private var showTimeLimit = false
    @State private var playedMs: Int64 = 0
    @State private var clockLoaded = false
    @State private var onScreen = true
    @State private var limitSeen: Int?
    @State private var started = false

    var body: some View {
        let settings = settingsRepo.settings
        let gamePaused = showTimeLimit || !onScreen
        ZStack {
            Color(argb: 0xFF87CEEB).ignoresSafeArea()
            if settings.loaded && !settings.onboardingDone {
                OnboardingScreen(settings: settings)
            } else if settings.loaded {
                NavRoot()
            }
            if showTimeLimit {
                TimeLimitOverlay {
                    showTimeLimit = false
                    // A little more time, not a whole new allowance
                    playedMs = max(0, Int64(settings.timeLimitMinutes) * 60_000 - extensionMs)
                    saveClock()
                }
            }
            TouchWatcherInstaller().frame(width: 0, height: 0)
        }
        .environment(\.language, settings.language)
        .environment(\.gender, settings.gender)
        .environment(\.appSettings, settings)
        .environment(\.gamePaused, gamePaused)
        .environment(\.layoutDirection, settings.language == .hebrew ? .rightToLeft : .leftToRight)
        .onAppear { startOnce(settings) }
        .onChange(of: settings) { old, new in apply(old: old, new: new) }
        .onChange(of: scenePhase) { _, phase in
            let active = phase == .active
            if active == onScreen { return }
            onScreen = active
            app.music.foreground = active
            app.voice.foreground = active
            app.companion.backgrounded = !active
            if active { app.music.resume() } else { app.music.pause(); app.voice.stop() }
        }
        .onChange(of: gamePaused) { _, p in app.companion.paused = p }
        .task(id: onScreen) { await brainRest() }
        .task(id: TimerKey(limit: settings.timeLimitMinutes, onScreen: onScreen, loaded: clockLoaded)) { await timeLimitLoop(settings) }
    }

    struct TimerKey: Hashable { let limit: Int; let onScreen: Bool; let loaded: Bool }

    private func startOnce(_ settings: AppSettings) {
        guard !started else { return }
        started = true
        app.sfx.preloadAll()
        app.music.playTrack("music_hub")
        apply(old: nil, new: settings)
        // The time limit's clock: the saved one, unless the game rested an hour
        let clock = app.settingsRepository.playClock()
        let rested = nowMs() - clock.lastSeen > restForNewAllowanceMs
        playedMs = rested ? 0 : clock.playedMs
        showTimeLimit = !rested && clock.timeUp
        clockLoaded = true
        limitChanged(settings)
    }

    private func apply(old: AppSettings?, new s: AppSettings) {
        app.music.level = s.musicVolume
        app.voice.level = s.voiceVolume
        app.sfx.level = s.sfxVolume
        app.sfx.enabled = s.soundEnabled
        app.voice.enabled = s.soundEnabled
        app.companion.enabled = s.companionEnabled
        app.companion.language = s.language
        app.companion.answersOutLoud = s.answersOutLoud
        app.voice.gender = s.gender
        app.music.setEnabled(s.musicEnabled)
        if old?.childName != s.childName || old?.gender != s.gender { app.companion.setChild(s.childName, girl: s.gender == .girl) }
        // The parent's choice: what Puki knows of her play stays in the session, or is kept on the device
        if old?.rememberBetween != s.rememberBetween {
            let keep = s.rememberBetween
            Task.detached { DayLog.keep(keep); MomentMemory.keep(keep) }
        }
        if old?.brainOnDevice != s.brainOnDevice { chooseBrain(s.brainOnDevice) }
        if clockLoaded { limitChanged(s) }
    }

    /** Whether Puki thinks: on this device once its models are downloaded, else his recorded lines. */
    private func chooseBrain(_ on: Bool) {
        let ready = on && app.deviceModels.ready()
        let brain: Brain? = ready ? app.brain : nil
        if brain !== app.companion.brain {
            if brain == nil { app.sleepBrain() }
            app.companion.brain = brain
            app.companion.recheck()
        }
    }

    /** A parent changing the limit starts a new allowance (not the settings being read at start). */
    private func limitChanged(_ s: AppSettings) {
        if s.timeLimitMinutes <= 0 && showTimeLimit {
            showTimeLimit = false  // the limit was turned off since time was up
            saveClock()
        }
        if let seen = limitSeen, seen != s.timeLimitMinutes {
            playedMs = 0
            showTimeLimit = false
            saveClock()
        }
        limitSeen = s.timeLimitMinutes
    }

    private func saveClock() {
        app.settingsRepository.savePlayClock(.init(playedMs: playedMs, lastSeen: nowMs(), timeUp: showTimeLimit))
    }

    /** Counts only the time the game is on screen; saved, so closing the app doesn't start a new allowance. */
    private func timeLimitLoop(_ s: AppSettings) async {
        let limit = settingsRepo.settings.timeLimitMinutes
        if !clockLoaded || limit <= 0 { return }
        if !onScreen { saveClock(); return }  // when it went away: the hour's rest counts from here
        var ticks = 0
        while !Task.isCancelled {
            guard await delay(5_000) else { return }
            if !showTimeLimit {
                playedMs += 5_000
                if playedMs >= Int64(limit) * 60_000 { showTimeLimit = true }
            }
            ticks += 1
            if ticks % 6 == 0 { saveClock() }
        }
    }

    /** A couple of minutes away and the device brain gives its memory back; back on screen, it's woken again. */
    private func brainRest() async {
        if onScreen {
            if app.brainSlept {
                app.brainSlept = false
                app.companion.recheck()
            }
            return
        }
        if app.companion.brain == nil { return }
        guard await delay(brainRestMs) else { return }
        app.sleepBrain()
    }
}
