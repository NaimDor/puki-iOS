import Foundation
import os
#if canImport(OnnxRuntimeBindings)
import OnnxRuntimeBindings
#endif

private let log = Logger(subsystem: "io.github.naimdor.puki", category: "PukiBrain")

/**
 * Puki's brain on this device, all of it Puki's own models but the voice: answers the game's requests (see Brain)
 * with Brains (one per language), the readers (Beak for Hebrew, EnglishBeak for English), the BlueTTS voice
 * (BlueVoice) and the same filter (Persona, Safety). It hears with kol-stream (KolEars, loaded in the background
 * once the rest is ready). A language that wasn't downloaded falls back to the game's recorded lines.
 */
final class LocalBrain: Brain {
    private let models: DeviceModels
    private let persona = Persona()
    private var state = "asleep"
    private let lifecycle = NSLock()
    private let work = NSLock()
    private let earsLock = NSLock()
    private var generation = 0

    #if canImport(OnnxRuntimeBindings)
    private var brains: Brains?  // one language's at a time (see brainsFor())
    private var brainsLang = ""
    private var beak: Beak?
    private var beakEn: EnglishBeak?
    private var voice: BlueVoice?
    private var ears: KolEars?
    #endif

    init(models: DeviceModels) { self.models = models }

    /** Loads the models (in the background; the game polls /health until "ready"). */
    func wake() {
        #if canImport(OnnxRuntimeBindings)
        lifecycle.lock()
        if state != "asleep" || !models.ready() { lifecycle.unlock(); return }
        state = "waking"
        let gen = generation
        lifecycle.unlock()
        Thread.detachNewThread { [self] in
            Thread.current.qualityOfService = .utility  // (the game comes first)
            do {
                let v = try BlueVoice(modelDir: models.file("voice"), voiceStyle: models.file("voice/noa.json"), threads: 4)
                let k = models.hasHebrew() ? try Beak(dir: models.file(DeviceModels.beakDir)) : nil
                // Warms up the voice before the first real line, with whichever reader there is
                if let k { _ = try? v.voice(try k.phonemize("שלום", girl: true)) } else if models.hasEnglish() {
                    let e = try EnglishBeak(dir: models.file(DeviceModels.beakEnDir))
                    beakEn = e
                    _ = try? v.voice(e.phonemize("hello"))
                }
                let bl = models.hasHebrew() ? "he" : "en"
                let b = try Brains(dir: models.file(brainsDir(bl)))
                lifecycle.lock()
                if gen != generation { lifecycle.unlock(); throw Ort.Failure(what: "switched off while loading") }
                work.lock(); voice = v; beak = k; brains = b; brainsLang = bl; work.unlock()
                state = "ready"
                lifecycle.unlock()
                // The ears last, so Puki is ready sooner; a listen that comes before they're loaded waits for them
                earsLock.lock()
                if gen == generation, ears == nil { ears = try? KolEars(dir: models.file(DeviceModels.earsDir)) }
                earsLock.unlock()
            } catch {
                log.error("the device brain didn't load: \(String(describing: type(of: error)), privacy: .public)")
                lifecycle.lock(); if gen == generation { state = "asleep" }; lifecycle.unlock()
            }
        }
        #endif
    }

    /** Frees the models' memory. Waits for an answer in progress, so call it off the main thread. */
    func close() {
        lifecycle.lock(); generation += 1; state = "asleep"; lifecycle.unlock()
        #if canImport(OnnxRuntimeBindings)
        work.lock()
        brains = nil; brainsLang = ""; beak = nil; beakEn = nil; voice = nil
        work.unlock()
        earsLock.lock(); ears = nil; earsLock.unlock()
        #endif
    }

    func request(_ method: String, _ path: String, body: Data?, timeoutMs: Int, contentType: String) -> BrainResponse? {
        let route = path.substringBefore("?")
        let query = Self.parseQuery(path.contains("?") ? path.substringAfter("?") : "")
        if method == "GET" && route == "/health" {
            return BrainResponse(body: (try? JSONSerialization.data(withJSONObject: ["brain": state])) ?? Data(), said: nil)
        }
        if route == "/wake" { wake(); return BrainResponse(body: Data(), said: nil) }
        #if canImport(OnnxRuntimeBindings)
        // Only the ears: no waiting behind a line being written
        if route == "/repeat" || route == "/hear" { return state == "ready" ? listenOnly(route, query, body) : nil }
        // A line in progress holds the models; past the game's deadline, it has given up anyway
        guard work.lock(before: Date().addingTimeInterval(Double(timeoutMs) / 1000)) else { return nil }
        defer { work.unlock() }
        guard brains != nil, let voice else { return nil }
        persona.setChild(query["name"]?.first ?? "", gender: query["gender"]?.first ?? "")
        let req = (body.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }) ?? [:]
        func str(_ k: String) -> String { req[k] as? String ?? "" }
        do {
            switch route {
            case "/say":
                let lang = str("lang") == "en" ? "en" : "he"
                if !speaks(lang) { return BrainResponse(body: Data(), said: nil) }
                let situation = str("situation").take(300)
                // A tap on Puki comes as the scene with "say something short and cute" (Companion.chat)
                let task = situation.hasPrefix(Companion.tapHe) || situation.hasPrefix(Companion.tapEn) ? "tap" : "moment"
                guard let b = brainsFor(lang) else { return nil }
                return try reply(line(b, task, situation, lang), lang, voice, heard: "")
            case "/line":
                let lang = str("lang") == "en" ? "en" : "he"
                if !speaks(lang) { return BrainResponse(body: Data(), said: nil) }
                let task = str("task")
                guard Self.gameTasks.contains(task) else { return nil }
                let truth = str("truth"), mustSay = str("mustSay")
                let present = req["present"] as? [String] ?? [], absent = req["absent"] as? [String] ?? []
                let input = str("input").take(400), earlier = str("earlier").take(300), pick = str("pick")
                let accept: (String) -> Bool
                if !pick.isEmpty || (task == "story_next" && !absent.isEmpty) {
                    accept = { l in (pick.isEmpty || ReachBack.says(l, pick)) && !absent.contains { ReachBack.says(l, $0) } }
                } else if task == "reach_back" {
                    accept = { l in ReachBack.namesEarlier(l, input: input, earlier: earlier) }
                } else if task == "recap" {
                    accept = { l in RecapContent.eventsNamedApart(l, input) >= 2 }
                } else if !present.isEmpty {
                    accept = { l in LookContent.ok(l, present: present, absent: absent) }
                } else if truth.isEmpty || mustSay.isEmpty {
                    accept = { _ in true }
                } else if mustSay == "yes" {
                    accept = { l in SillyContent.saysTruth(l, truth) }
                } else {
                    accept = { l in !SillyContent.saysTruth(l, truth) }
                }
                guard let b = brainsFor(lang) else { return nil }
                // (and never a question: she can't be asked to answer)
                return try reply(line(b, task, input, lang, tries: 4, accept: { !$0.contains("?") && accept($0) }, earlier: earlier), lang, voice, heard: "")
            case "/speak":
                let lang = str("lang") == "en" ? "en" : "he"
                if !speaks(lang) { return BrainResponse(body: Data(), said: nil) }
                let text = str("text").take(200).trimmed()
                let raw = (req["speed"] as? NSNumber)?.doubleValue ?? 0.92
                let speed = Float(raw.isFinite ? raw.coerceIn(0.5, 1.5) : 0.92)
                if text.isEmpty { return BrainResponse(body: Data(), said: nil) }
                return try reply(text, lang, voice, speed: speed, heard: "")
            case "/listen":
                // She talked to Puki: the careful reply if it needs one (Safety), else his answer
                let lang = query["lang"]?.first == "en" ? "en" : "he"
                if !speaks(lang) { return BrainResponse(body: Data(), said: nil) }
                guard let b = brainsFor(lang) else { return nil }
                let heard = try earsNow().transcribe(wav: body ?? Data())
                let guarded = Safety.guardedReply(heard, lang: lang, girl: persona.girl, game: (query["game"]?.first ?? "").take(40),
                    gate: { Safety.gateCategory(try b.gate($0, lang: lang, girl: self.persona.girl), threshold: Safety.threshold) },
                    onError: { e in log.error("safety gate failed: \(String(describing: type(of: e)), privacy: .public); answering with the neutral line") })
                if let guarded {
                    log.info("heard something careful: [\(guarded.0, privacy: .public)]")  // (not what she said)
                    return try reply(guarded.1, lang, voice, heard: heard)
                }
                return try reply(line(b, "talk", heard.take(200), lang), lang, voice, heard: heard)
            default:
                return nil
            }
        } catch {
            log.error("\(route, privacy: .public) failed: \(String(describing: type(of: error)), privacy: .public)")  // (not the message: it may quote her)
            return nil
        }
        #else
        return nil
        #endif
    }

    #if canImport(OnnxRuntimeBindings)
    /** The requests that only need the ears: Say It's word check, and an answer said out loud. */
    private func listenOnly(_ route: String, _ query: [String: [String]], _ body: Data?) -> BrainResponse? {
        do {
            let heard = try earsNow().transcribe(wav: body ?? Data())
            var o: [String: Any] = ["heard": heard]
            if route == "/repeat" {
                let target = (query["target"]?.first ?? "").take(40)
                let alts = (query["alt"] ?? []).filter { !$0.isEmpty }.map { $0.take(40) }.take(4)
                o["match"] = persona.wordMatch(heard, target: target, alts: alts)
            }
            return BrainResponse(body: try JSONSerialization.data(withJSONObject: o), said: nil)
        } catch {
            log.error("\(route, privacy: .public) failed: \(String(describing: type(of: error)), privacy: .public)")
            return nil
        }
    }

    /** The ears, loaded now if the background load in wake() hasn't yet (or failed). */
    private func earsNow() throws -> KolEars {
        earsLock.lock(); defer { earsLock.unlock() }
        if let ears { return ears }
        let e = try KolEars(dir: models.file(DeviceModels.earsDir))
        ears = e
        return e
    }

    /** A line from Brains for `input` that passes Puki's filter (and the game's `accept`), or nil. */
    private func line(_ brains: Brains, _ task: String, _ input: String, _ lang: String, tries: Int = 3,
                      accept: (String) -> Bool = { _ in true }, earlier: String = "") throws -> String? {
        for _ in 0..<tries {
            guard let raw = try brains.line(task: task, lang: lang, girl: persona.girl, input: input, earlier: earlier) else { continue }
            if let l = persona.clean(withName(raw), lang: lang), Safety.replyOk(l), !Self.soundWords.containsMatchIn(l), accept(l) {
                persona.said(l)
                return l
            }
            log.info("a line didn't pass the filter; trying again")
        }
        return nil
    }

    /** Brains writes "@name" where the child's name goes; with no name, it goes. */
    private func withName(_ raw: String) -> String {
        if !persona.name.isEmpty { return raw.replacingOccurrences(of: "@name", with: persona.name) }
        let a = KRegex(",?\\s*@name\\s*,?").replace(raw, " ")
        return KRegex("\\s+").replace(KRegex("\\s+([!?.,])").replace(a, "$1"), " ").trimmed()
    }

    /** `text` spoken (empty when there's nothing to say; nil when it can't be voiced here). */
    private func reply(_ text: String?, _ lang: String, _ voice: BlueVoice, speed: Float = 0.92, heard: String) throws -> BrainResponse? {
        guard let text, !text.trimmed().isEmpty else { return BrainResponse(body: Data(), said: nil) }
        let said = Said(text: text, heard: heard)
        if lang == "en" {
            guard let reader = englishReader() else { return BrainResponse(body: Data(), said: nil) }
            let ipa = reader.phonemize(text) { [self] hebrew in (try? hebrewReader()?.phonemize(hebrew, girl: persona.girl)) ?? "" }
            if ipa.trimmed().isEmpty { return BrainResponse(body: Data(), said: nil) }
            return try voice.speakPhonemes(ipa, speed: speed).map { BrainResponse(body: $0, said: said) }
        }
        guard let beak = hebrewReader() else { return BrainResponse(body: Data(), said: nil) }
        let prepared = VoiceText.prepareTextForSynthesis(persona.forVoice(text))
        if !prepared.unicodeScalars.contains(where: { $0.value >= 0x0590 && $0.value <= 0x05FF }) { return BrainResponse(body: Data(), said: nil) }
        return try voice.speakPhonemes(beak.phonemize(prepared, girl: persona.girl), speed: speed).map { BrainResponse(body: $0, said: said) }
    }

    private func brainsDir(_ lang: String) -> String { lang == "en" ? DeviceModels.brainsEnDir : DeviceModels.brainsHeDir }

    /** `lang`'s Brains (called inside `work`): the other language's is freed first, so only one is in memory. */
    private func brainsFor(_ lang: String) -> Brains? {
        if brainsLang == lang, let brains { return brains }
        if !speaks(lang) { return nil }
        brains = nil
        brainsLang = ""
        do {
            let b = try Brains(dir: models.file(brainsDir(lang)))
            brains = b
            brainsLang = lang
            return b
        } catch {
            log.error("Brains (\(lang, privacy: .public)) didn't load")
            return nil
        }
    }

    /** Beak, loaded now if Hebrew was added after the brain woke. */
    private func hebrewReader() -> Beak? {
        if let beak { return beak }
        if !models.hasHebrew() { return nil }
        beak = try? Beak(dir: models.file(DeviceModels.beakDir))
        return beak
    }

    /** The English reader, loaded the first time it's needed. */
    private func englishReader() -> EnglishBeak? {
        if let beakEn { return beakEn }
        if !models.hasEnglish() { return nil }
        beakEn = try? EnglishBeak(dir: models.file(DeviceModels.beakEnDir))
        return beakEn
    }
    #endif

    /** Whether Puki can voice `lang` here: its Brains and reader were downloaded. */
    private func speaks(_ lang: String) -> Bool { lang == "en" ? models.hasEnglish() : models.hasHebrew() }

    private static func parseQuery(_ q: String) -> [String: [String]] {
        var out: [String: [String]] = [:]
        for part in q.split(separator: "&") where part.contains("=") {
            let s = String(part)
            let k = s.substringBefore("=").removingPercentEncoding ?? ""
            let v = s.substringAfter("=").replacingOccurrences(of: "+", with: " ").removingPercentEncoding ?? ""
            out[k, default: []].append(v)
        }
        return out
    }

    /** Puki imitating sounds ("קוואק!", "squawk", "דינג") doesn't fit the rest of the game: such a line is written again. */
    private static let soundWords = KRegex("קוואק|squawk|דינג", ignoreCase: true)
    /** The tasks a game may ask for by name (/line); moment, tap and talk have their own routes. */
    private static let gameTasks: Set<String> = ["silly", "silly_fix", "look", "recap", "reach_back", "care", "story_next"]
}
