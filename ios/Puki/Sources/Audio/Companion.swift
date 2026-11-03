import Foundation
import Combine

enum CompanionStatus { case off, waking, ready }

/**
 * How a try at saying a word went (Say it with Puki): nothing heard, another word, a near miss,
 * the word itself, or spoke: the child talked but the word couldn't be checked (no brain awake).
 */
enum WordTry { case silent, tried, close, word, spoke }

/**
 * פוקי the parrot, whose words come from his brain on this device (`brain`, see LocalBrain):
 * Brains writes a short line about what just happened and BlueTTS voices it. Whenever there's
 * no brain (not downloaded, switched off, still waking, slow reply) callers fall back to the
 * recorded lines.
 */
@MainActor
final class Companion: ObservableObject {
    @Published private(set) var status: CompanionStatus = .off
    @Published private(set) var speaking = false
    /** A line is on its way from the brain: thinking dots, so a slow answer isn't a silent one. */
    @Published private(set) var thinking = false
    /** True while the parrot listens on its own (after asking a question). */
    @Published private(set) var listening = false

    private let voicePlayer: VoicePlayer

    var enabled = true { didSet { if !enabled { status = .off } } }
    var language: Language = .hebrew
    /** The parent lets the games listen for answers said out loud (see `listenForAnswer`). */
    var answersOutLoud = true
    /** The time limit's goodbye is up: the game underneath may not listen (or start talking). */
    var paused = false
    /** The game is off screen: no waking the brain, and a reply that comes late isn't played. */
    var backgrounded = false { didSet { if backgrounded { wakeTask?.cancel() } } }

    /** What's on screen right now, so the parrot has something to talk about when tapped. */
    private var scene = ""

    /** A line Puki said, kept for the parent's "What Puki said" card (מלשין לרנינג). */
    struct SaidLine: Identifiable, Equatable {
        let id: UInt64
        let said: String
        let heard: String
        let situation: String
        let game: String
        let lang: String
        var reported = false
    }

    /** The last lines Puki said this session (memory only), newest last. */
    @Published private(set) var recentLines: [SaidLine] = []

    init(voicePlayer: VoicePlayer) {
        self.voicePlayer = voicePlayer
    }

    /** A parent says `line` wasn't right: it's marked, and they can mail it to Puki's makers. */
    func report(_ line: SaidLine) {
        if line.reported { return }
        recentLines = recentLines.map { $0.id == line.id ? { var c = $0; c.reported = true; return c }($0) : $0 }
    }

    /**
     * The text of a report a parent may send: what Puki said and what was happening in the game just before,
     * with the child's name replaced. What the child said is never in it.
     */
    func shareText(_ line: SaidLine) -> String {
        let placeholder = pick("[שם]", "[name]")
        let name = Persona.stripNikud(childName).trimmed()
        func hide(_ s: String) -> String {
            var t = Persona.stripNikud(s)
            if name.count > 1 { t = t.replacingOccurrences(of: name, with: placeholder, options: .caseInsensitive) }
            return t
        }
        var out = pick("פוקי אמר:", "Puki said:") + " " + hide(line.said) + "\n"
        if !line.situation.trimmed().isEmpty { out += pick("לפני זה:", "Before that:") + " " + hide(line.situation) + "\n" }
        if !line.game.trimmed().isEmpty { out += pick("משחק:", "Game:") + " " + line.game + "\n" }
        out += pick("שפה:", "Language:") + " " + line.lang + "\n"
        out += pick("גרסה:", "Version:") + " " + appVersion() + " (iOS)\n\n"
        out += pick("מה לא היה בסדר? (לא חובה)", "What wasn't right? (optional)")
        return out
    }

    private func recordSaid(_ route: String, _ body: Data?, _ said: Said?) {
        guard let said else { return }
        var situation = ""
        if let body, let o = try? JSONSerialization.jsonObject(with: body) as? [String: Any] {
            if route == "/say" { situation = o["situation"] as? String ?? "" }
            if route == "/line" { situation = o["input"] as? String ?? "" }
        }
        let line = SaidLine(id: DispatchTime.now().uptimeNanoseconds, said: said.text, heard: said.heard, situation: situation, game: game, lang: lang())
        recentLines = Array((recentLines + [line]).suffix(20))
    }

    /** The screen the child is on (its route): the brain's safety word lists let a game use its own words. */
    var game = ""

    func setScene(_ he: String, _ en: String) { scene = pick(he, en) }

    private var childName = ""
    private var childGirl = true

    /** Who's playing, from the app's settings. Sent with every request. */
    func setChild(_ name: String, girl: Bool) {
        childName = name.trimmed()
        childGirl = girl
        refreshNameLines()
    }

    var isReady: Bool { status == .ready }

    private var wakeTask: Task<Void, Never>?
    private var wakeRunning = false
    private let recorder = MicRecorder()

    /** The brain was switched on or off: forget the old status and look again. */
    func recheck() {
        wakeTask?.cancel()
        wakeRunning = false
        status = .off
        wake()
    }

    /** Waits (up to `ms`) for the brain to be ready, waking it: true once it is. */
    func awaitReady(_ ms: Int = 25_000) async -> Bool {
        if isReady { return true }
        wake()
        let end = uptimeMs() + Int64(ms)
        while !isReady {
            if status == .off && !wakeRunning { return false }
            if uptimeMs() > end || Task.isCancelled { return false }
            await delay(250)
        }
        return true
    }

    /** Checks on the brain and wakes it if needed. Cheap to call on every game screen. */
    func wake() {
        if !enabled || backgrounded || brain == nil || wakeRunning || isReady { return }
        wakeRunning = true
        wakeTask = Task { [weak self] in
            guard let self else { return }
            defer { self.wakeRunning = false }
            guard let state = await self.health() else { self.status = .off; return }
            if state == "ready" { self.status = .ready; self.refreshNameLines(); return }
            _ = await self.request("POST", "/wake", timeoutMs: 3000)
            self.status = .waking
            for _ in 0..<60 {
                guard await delay(2000) else { return }
                switch await self.health() {
                case "ready": self.status = .ready; self.refreshNameLines(); return
                case nil: self.status = .off; return
                default: break
                }
            }
            self.status = .off
        }
    }

    /**
     * Has the parrot say something about what happened (`he` or `en`, in the app's language);
     * runs `fallback` instead if it's not available or doesn't answer in time.
     */
    func react(_ he: String, _ en: String, fallback: @escaping () -> Void = {}) {
        let tap = he.hasPrefix(Self.tapHe)
        let link = tap ? nil : reachBack(he)
        if !tap { MomentMemory.record(he: he, en: en, game: game) }
        let earlier = link.map { (whenSaid, m) in language == .english ? "\(whenSaid.1): \(m.en)" : "\(whenSaid.0): \(m.he)" }
        react(situation: pick(he, en), fallback: fallback, earlier: earlier)
    }

    private var lastReachBack: Int64 = 0

    /** An earlier moment to link `he` to, about one time in three when there is one, and not twice in three minutes. */
    private func reachBack(_ he: String) -> ((String, String), Moment)? {
        let now = nowMs()
        if now - lastReachBack < 3 * 60_000 || Int.random(in: 0..<3) != 0 { return nil }
        let names = Set(childName.split(separator: " ").map(String.init).filter { $0.count >= 2 }.flatMap { ReachBack.forms($0) })
        return ReachBack.related(he, game: game, history: MomentMemory.history(), nowMs: now, ignore: names)
    }

    private func react(situation: String, fallback: @escaping () -> Void, earlier: String?) {
        if !isReady || thinking {
            wake()
            fallback()
            return
        }
        Task {
            if let earlier, await line("reach_back", input: situation, earlier: earlier) != nil {
                lastReachBack = nowMs()
                return
            }
            thinking = true
            let said = await say(situation)
            thinking = false
            if !said { fallback() }
        }
    }

    /** The child tapped the parrot. */
    func chat() {
        react("\(Self.tapHe) כדי שידבר. \(scene) תגיד משהו חמוד וקצר על זה.",
              "\(Self.tapEn) you so you'd talk. \(scene) Say something short and cute about it.")
    }

    /** The child held the parrot and talked; `wav` is the recording, for the brain's ears. */
    func listen(_ wav: Data, context: String = "") {
        if !isReady || wav.count < 16000 { return }  // under half a second: nothing to send
        Task {
            thinking = true
            let reply = await postListen(wav, context)
            _ = play(reply)
            thinking = false
        }
    }

    /** Says `text` in the parrot's voice and waits until it's done. False if it couldn't. */
    func speakAndWait(_ text: String, speed: Double = 0.92) async -> Bool {
        if !isReady { return false }
        let audio = await request("POST", "/speak", body: json(["text": text, "lang": lang(), "speed": speed]), timeoutMs: 8000)
        if !play(audio) { return false }
        await awaitQuiet()
        return true
    }

    /**
     * Puki writes and says a line for a game's own `task` (Silly Puki, a drawing), from `input` as the brain was
     * trained. Waits until he's done, and returns what he said, or nil if he couldn't: the game plays its own then.
     */
    func line(_ task: String, input: String, truth: String = "", mustSay: Bool? = nil,
              present: [String] = [], absent: [String] = [], earlier: String = "", pick: String = "") async -> String? {
        if !isReady { return nil }
        thinking = true
        let body: [String: Any] = ["task": task, "input": input, "lang": lang(), "truth": truth,
                                   "mustSay": mustSay == true ? "yes" : mustSay == false ? "no" : "",
                                   "present": present, "absent": absent, "earlier": earlier, "pick": pick]
        let r = await requestFull("POST", "/line", body: json(body), timeoutMs: 9000)
        thinking = false
        guard let text = r?.said?.text, play(r?.body) else { return nil }
        await awaitQuiet()
        return text
    }

    /** Fetches the audio for `text` without playing it (to prefetch story sentences). */
    func fetchSpeech(_ text: String, speed: Double = 0.85) async -> Data? {
        let a = await request("POST", "/speak", body: json(["text": text, "lang": lang(), "speed": speed]), timeoutMs: 8000)
        return (a?.isEmpty ?? true) ? nil : a
    }

    /** Plays prefetched audio and waits until it's done. */
    func playAndWait(_ audio: Data) async {
        if play(audio) { await awaitQuiet() }
    }

    /**
     * Asks `question` out loud, then listens for a few seconds without any button. If the child made a
     * sound, runs `onHeard` and lets the parrot answer what it heard. Returns true if the child answered.
     */
    func askAndListen(_ question: String, context: String, onHeard: () -> Void) async -> Bool {
        if !isReady || !hasMicPermission() { return false }
        if !(await speakAndWait(question)) { return false }
        guard let wav = await record(Self.listenMillis) else { return false }
        if !Self.hasSpeech(wav) { return false }
        onHeard()
        let reply = await postListen(wav, context)
        await delay(900)  // let the animal's answer finish first
        if play(reply) { await awaitQuiet() }
        return true
    }

    /**
     * Say it with Puki: listens for a few seconds while the child says `word`. Returns nil if there's no
     * microphone permission.
     */
    func listenForWord(_ word: String, alsoCounts: [String]) async -> WordTry? {
        if !hasMicPermission() { return nil }
        guard let wav = await record(Self.wordListenMillis) else { return nil }
        if !Self.hasSpeech(wav) { return .silent }
        if !isReady { return .spoke }
        let path = "/repeat?lang=\(lang())&target=" + enc(word) + alsoCounts.map { "&alt=" + enc($0) }.joined()
        guard let body = await request("POST", path, body: wav, timeoutMs: 8000, contentType: "audio/wav") else { return .spoke }
        switch (try? JSONSerialization.jsonObject(with: body) as? [String: Any])?["match"] as? String {
        case "word": return .word
        case "close": return .close
        default: return .tried
        }
    }

    /**
     * A game asked a question: listens a few seconds for the answer said out loud, and returns what was heard
     * ("" for silence or nothing clear). Nil when it can't listen.
     */
    func listenForAnswer(_ hints: [String]) async -> String? {
        if !answersOutLoud || !isReady || !hasMicPermission() { return nil }
        guard let wav = await record(Self.answerListenMillis) else { return nil }
        if !Self.hasSpeech(wav) { return "" }
        let path = "/hear?lang=\(lang())" + hints.prefix(16).map { "&hint=" + enc($0) }.joined()
        guard let body = await request("POST", path, body: wav, timeoutMs: 8000, contentType: "audio/wav") else { return "" }
        return (try? JSONSerialization.jsonObject(with: body) as? [String: Any])?["heard"] as? String ?? ""
    }

    /**
     * Records up to `millis` of the microphone (the parrot glows meanwhile), stopping early once the child has
     * spoken and gone quiet, or nil if it couldn't start. However this ends, the microphone goes off.
     */
    private func record(_ millis: Int64) async -> Data? {
        if paused { return nil }
        listening = true
        defer { listening = false }
        guard await recorder.startOffMain() else { return nil }
        let start = uptimeMs()
        var lastLoud: Int64 = -1
        while true {
            if !(await delay(50)) { _ = await recorder.stopOffMain(); return nil }
            let t = uptimeMs() - start
            if recorder.level > Self.speechLevel { lastLoud = t }
            if Self.listenDone(t, lastLoud, millis) { break }
        }
        return await recorder.stopOffMain()
    }

    /** Rough voice-activity check: a few 50 ms windows clearly louder than room noise. */
    nonisolated static func hasSpeech(_ wav: Data) -> Bool {
        guard wav.count > 44 else { return false }
        let pcm = wav.subdata(in: 44..<wav.count)
        let window = MicRecorder.rate / 20
        return pcm.withUnsafeBytes { raw -> Bool in
            let s = raw.bindMemory(to: Int16.self)
            var loud = 0, i = 0
            while i + window <= s.count {
                var sum = 0.0
                for j in i..<(i + window) { let v = Double(Int16(littleEndian: s[j])); sum += v * v }
                if (sum / Double(window)).squareRoot() > 1200 { loud += 1 }
                i += window
            }
            return loud >= 3
        }
    }

    private func postListen(_ wav: Data, _ context: String) async -> Data? {
        let ctx = context.isEmpty ? "" : "&context=" + enc(context)
        let whereGame = game.isEmpty ? "" : "&game=" + enc(game)
        return await request("POST", "/listen?lang=\(lang())\(ctx)\(whereGame)", body: wav, timeoutMs: 15000, contentType: "audio/wav")
    }

    private func say(_ situation: String) async -> Bool {
        let audio = await request("POST", "/say", body: json(["situation": situation, "lang": lang()]), timeoutMs: 6000)
        return play(audio)
    }

    @discardableResult
    private func play(_ audio: Data?) -> Bool {
        guard let audio else {
            // The brain may have gone to sleep after being idle; wake it for next time
            status = .off
            wake()
            return false
        }
        if audio.isEmpty { return false }  // no safe line this time
        if backgrounded { return false }  // (it came after she left: nobody to hear it)
        speaking = true
        voicePlayer.playData(audio)  // (from memory: nothing Puki said is written down)
        Task {
            await delay(200)
            while voicePlayer.isPlaying() { await delay(120) }
            speaking = false
        }
        return true
    }

    private func awaitQuiet() async {
        await delay(250)
        let end = uptimeMs() + 30_000
        while voicePlayer.isPlaying() && uptimeMs() < end { if !(await delay(100)) { return } }
    }

    /**
     * Has the parrot record the lines that say the child's name ("כל הכבוד, ___!") and keeps them on the
     * device, so they play at once, and also with the brain off. Redone when the name or gender changes;
     * removed when the name is cleared.
     */
    private func refreshNameLines() {
        let wanted = childName.trimmed().isEmpty ? "" : "\(childName)|\(childGirl)|\(Self.nameLinesVersion)"
        let dir = VoicePlayer.personalDir
        let fm = FileManager.default
        Task {
            if wanted.isEmpty {
                for f in (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? [] { try? fm.removeItem(at: f) }
                return
            }
            let marker = dir.appendingPathComponent("name_lines.txt")
            if (try? String(contentsOf: marker, encoding: .utf8)) == wanted { return }
            if !isReady { wake(); return }
            try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
            for f in (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? [] { try? fm.removeItem(at: f) }
            var all = true
            for (key, he, en) in nameLines(childName, childGirl) {
                for (l, text) in [("he", he), ("en", en)] {
                    let audio = await request("POST", "/speak", body: json(["text": text, "lang": l]), timeoutMs: 10000)
                    if audio == nil { all = false } else if let audio, !audio.isEmpty {
                        try? audio.write(to: dir.appendingPathComponent("\(key)_\(l).wav"), options: .completeFileProtection)
                    }
                }
            }
            if all { try? wanted.write(to: marker, atomically: true, encoding: .utf8) }
        }
    }

    private func nameLines(_ n: String, _ girl: Bool) -> [(String, String, String)] {
        [("voice_praise_name_1", "כל הכבוד, \(n)!", "Great job, \(n)!"),
         ("voice_praise_name_2", girl ? "\(n), את אלופה!" : "\(n), אתה אלוף!", "\(n), you're a champion!"),
         ("voice_praise_name_3", "יופי, \(n)!", "Yay, \(n)!"),
         ("voice_praise_name_4", "וואו \(n), איזה יופי!", "Wow, \(n)!"),
         ("voice_hello_name", "שלום \(n)! לאן נשוט היום?", "Hello \(n)! Where shall we sail today?"),
         ("voice_round_done_name", girl ? "\(n), את סיימת! כל הכבוד!" : "\(n), אתה סיימת! כל הכבוד!", "\(n), all done! Great job!")]
    }

    private func lang() -> String { language == .english ? "en" : "he" }
    private func pick(_ he: String, _ en: String) -> String { language == .english ? en : he }
    private func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "" }
    private func json(_ o: [String: Any]) -> Data { (try? JSONSerialization.data(withJSONObject: o)) ?? Data() }

    private func health() async -> String? {
        guard let bytes = await request("GET", "/health", timeoutMs: 1500) else { return nil }
        return (try? JSONSerialization.jsonObject(with: bytes) as? [String: Any])?["brain"] as? String
    }

    /** This device's brain while it's switched on (parent settings, models downloaded); nil: recorded lines only. */
    var brain: Brain?

    private func request(_ method: String, _ path: String, body: Data? = nil, timeoutMs: Int,
                         contentType: String = "application/json; charset=utf-8") async -> Data? {
        await requestFull(method, path, body: body, timeoutMs: timeoutMs, contentType: contentType)?.body
    }

    /** Returns the response (body empty for "nothing to say"), or nil on any failure or with no brain. */
    private func requestFull(_ method: String, _ path: String, body: Data? = nil, timeoutMs: Int,
                             contentType: String = "application/json; charset=utf-8") async -> BrainResponse? {
        guard let brain else { return nil }
        let full: String
        if method == "POST" {
            let sep = path.contains("?") ? "&" : "?"
            full = path + sep + "name=" + enc(childName) + "&gender=" + (childGirl ? "girl" : "boy")
        } else { full = path }
        let result = await Task.detached(priority: .userInitiated) {
            brain.request(method, full, body: body, timeoutMs: timeoutMs, contentType: contentType)
        }.value
        let route = path.substringBefore("?")
        if let result, !result.body.isEmpty, ["/say", "/listen", "/line"].contains(route) { recordSaid(route, body, result.said) }
        return result
    }

    /** How a tap on Puki begins (LocalBrain tells it from a game moment by this). */
    nonisolated static let tapHe = "לחצו על התוכי"
    nonisolated static let tapEn = "The child tapped"
    static let listenMillis: Int64 = 3500
    static let wordListenMillis: Int64 = 4000  // toddlers take a moment to start
    static let answerListenMillis: Int64 = 4000
    /** The recorder's level for "someone is talking". */
    static let speechLevel: Float = 0.15
    /** Quiet this long after speaking means she's done (toddlers pause mid-phrase, so not less). */
    static let quietAfterSpeechMillis: Int64 = 800

    /** Whether a listen `t` ms in can stop: at `max`, or once she has spoken and been quiet since. */
    nonisolated static func listenDone(_ t: Int64, _ lastLoud: Int64, _ max: Int64) -> Bool {
        t >= max || (lastLoud >= 0 && t - lastLoud >= 800)
    }
    static let nameLinesVersion = 1
}

extension Optional where Wrapped == Companion {
    /** Puki's line for this moment; with no companion at all, `fallback` (a recorded line). */
    @MainActor func reactOr(_ he: String, _ en: String, _ fallback: @escaping () -> Void) {
        if let c = self { c.react(he, en, fallback: fallback) } else { fallback() }
    }
}

func appVersion() -> String { Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?" }
