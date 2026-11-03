import AVFoundation
import Combine

/** The recorded voice lines (and Puki's own, from his brain), one at a time, never cut off mid-word. */
@MainActor
final class VoicePlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    /** Lines the parrot recorded onto the device (with the child's name). */
    static var personalDir: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("voice")
    }

    private var player: AVAudioPlayer?
    var enabled = true

    /** A line is playing (or the next in a row is on its way): Puki's beak moves with this. */
    @Published private(set) var speaking = false

    var gender: Gender = .girl

    /** The parent's volume for voices, 0..1. */
    var level: Float = 1 {
        didSet { level = level.coerceIn(0, 1); player?.volume = level }
    }

    /** Lines waiting for the one playing to finish. Only a couple wait: on fast taps the oldest is skipped. */
    private var waiting: [(start: () -> Void, done: (() -> Void)?)] = []
    private static let maxWaiting = 3

    private func enqueue(_ start: @escaping () -> Void, _ done: (() -> Void)?) {
        waiting.append((start, done))
        while waiting.count > Self.maxWaiting { waiting.removeFirst().done?() }
    }

    /** The line playing now's caller, told when it ends, or when stop() cuts it short. */
    private var playingDone: (() -> Void)?

    /** The game is on screen; away, lines are dropped rather than spoken to an empty room. */
    var foreground = true

    /** While held (the microphone listening), new lines wait their turn instead of playing. */
    private var held = false

    func hold() { held = true }

    /** Let lines play again: the first one waiting starts now. */
    func unhold() {
        held = false
        if !isPlaying(), !waiting.isEmpty { waiting.removeFirst().start() }
    }

    private func finished(_ done: (() -> Void)?) {
        player = nil
        playingDone = nil
        done?()
        if player == nil, !waiting.isEmpty { waiting.removeFirst().start() }
        speaking = player != nil
    }

    func play(_ key: String, _ language: Language, onComplete: (() -> Void)? = nil) {
        if !enabled || !foreground { onComplete?(); return }
        if held || isPlaying() {
            enqueue({ [weak self] in self?.play(key, language, onComplete: onComplete) }, onComplete)
            return
        }
        releaseStale()
        if let file = personalFile(key, language) { start(url: file, onComplete); return }
        guard let url = resolve(key, language) else { onComplete?(); return }
        start(url: url, onComplete)
    }

    private func start(url: URL? = nil, data: Data? = nil, _ onComplete: (() -> Void)?) {
        let p: AVAudioPlayer?
        if let url { p = try? AVAudioPlayer(contentsOf: url) } else if let data { p = try? AVAudioPlayer(data: data) } else { p = nil }
        guard let p else { finished(onComplete); return }
        p.delegate = self
        p.volume = level
        player = p
        playingDone = onComplete
        if p.play() { speaking = true } else { finished(onComplete) }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ p: AVAudioPlayer, successfully flag: Bool) {
        MainActor.assumeIsolated {
            if self.player === p { self.finished(self.playingDone) }
        }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ p: AVAudioPlayer, error: Error?) {
        MainActor.assumeIsolated {
            if self.player === p { self.finished(self.playingDone) }
        }
    }

    /** Plays audio bytes (a line from Puki's brain, WAV). */
    func playData(_ data: Data, onComplete: (() -> Void)? = nil) {
        if !enabled || !foreground { onComplete?(); return }
        if held || isPlaying() {
            enqueue({ [weak self] in self?.playData(data, onComplete: onComplete) }, onComplete)
            return
        }
        releaseStale()
        start(data: data, onComplete)
    }

    func playSequence(_ keys: [String], _ language: Language, onAllComplete: (() -> Void)? = nil) {
        guard let first = keys.first else { onAllComplete?(); return }
        play(first, language) { [weak self] in
            self?.playSequence(Array(keys.dropFirst()), language, onAllComplete: onAllComplete)
        }
    }

    /** A random praise line; half the time one that says the child's name, when those exist. */
    func praiseKey(_ language: Language) -> String? {
        let named = (1...4).map { "voice_praise_name_\($0)" }.filter { has($0, language) }
        let plain = (["voice_prompt_great_job", "voice_prompt_well_done"] + (1...5).map { "voice_praise_\($0)" }).filter { has($0, language) }
        return (!named.isEmpty && (plain.isEmpty || Bool.random())) ? named.randomElement() : plain.randomElement()
    }

    func playPraise(_ language: Language, onComplete: (() -> Void)? = nil) {
        if let key = praiseKey(language) { play(key, language, onComplete: onComplete) } else { onComplete?() }
    }

    /** The personal version of a line (e.g. voice_hello_name) if it exists, otherwise the generic one. */
    func personalKey(_ key: String, _ language: Language) -> String {
        has("\(key)_name", language) ? "\(key)_name" : key
    }

    func has(_ key: String, _ language: Language) -> Bool {
        personalFile(key, language) != nil || resolve(key, language) != nil
    }

    private var personalListed: Date?
    private var personalSet = Set<String>()
    private func personalNames() -> Set<String> {
        let dir = Self.personalDir
        let stamp = (try? FileManager.default.attributesOfItem(atPath: dir.path)[.modificationDate]) as? Date
        if stamp != personalListed {
            personalSet = Set((try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? [])
            personalListed = stamp
        }
        return personalSet
    }

    private func personalFile(_ key: String, _ language: Language) -> URL? {
        let name = "\(key)\(language == .hebrew ? "_he" : "_en").wav"
        return personalNames().contains(name) ? Self.personalDir.appendingPathComponent(name) : nil
    }

    // For Hebrew, the gendered variant first (voice_prompt_bath_he_f), then the base one (voice_prompt_bath_he)
    private var ids: [String: URL?] = [:]
    private func resolve(_ key: String, _ language: Language) -> URL? {
        let suffix = language == .hebrew ? "_he" : "_en"
        let g = gender == .girl ? "_f" : "_m"
        return raw("\(key)\(suffix)\(g)") ?? raw("\(key)\(suffix)")
    }
    private func raw(_ name: String) -> URL? {
        if let hit = ids[name] { return hit }
        let u = audioURL(name)
        ids[name] = u
        return u
    }

    func isPlaying() -> Bool { player?.isPlaying == true }

    private func releaseStale() {
        player?.stop()
        player = nil
        speaking = false
    }

    /** Silence now: the line playing and everything waiting. Their callers are still told. */
    func stop() {
        let cut = [playingDone].compactMap { $0 } + waiting.compactMap { $0.done }
        waiting.removeAll()
        playingDone = nil
        player?.stop()
        player = nil
        speaking = false
        cut.forEach { $0() }
    }

    func release() { stop() }
}

extension VoicePlayer {
    /** Plays a line and returns when it's done (or after `timeoutMs`, so a lost line can't freeze a game). */
    func say(_ key: String, _ language: Language, timeoutMs: Int = 15_000) async {
        await waitFor(timeoutMs) { done in self.play(key, language, onComplete: done) }
    }

    func praise(_ language: Language, timeoutMs: Int = 15_000) async {
        await waitFor(timeoutMs) { done in self.playPraise(language, onComplete: done) }
    }

    func playSequenceAndWait(_ keys: [String], _ language: Language, timeoutMs: Int = 20_000) async {
        await waitFor(timeoutMs) { done in self.playSequence(keys, language, onAllComplete: done) }
    }

    private func waitFor(_ timeoutMs: Int, _ start: (@escaping () -> Void) -> Void) async {
        await withTaskCancellationHandler {
            await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in
                var finished = false
                let finish = {
                    if finished { return }
                    finished = true
                    c.resume()
                }
                start { finish() }
                DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(timeoutMs)) { finish() }
            }
        } onCancel: {
            Task { @MainActor in self.stop() }
        }
    }
}
