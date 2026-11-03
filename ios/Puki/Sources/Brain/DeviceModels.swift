import Foundation
import CryptoKit
import Combine

/**
 * The models the device brain needs, downloaded once from where their makers publish them (Hugging Face, and
 * BlueTTS's GitHub for the voice style), checked against SHA-256, and kept in the app's own storage (not backed
 * up). Downloads resume where they stopped. Each language has its own Brains and reader: the parent picks Hebrew,
 * English or both (BrainLanguages).
 */
final class DeviceModels: ObservableObject {
    let dir: URL

    struct Model { let url: String; let name: String; let size: Int64; let sha256: String }

    private static func hf(_ repo: String, _ path: String, _ name: String, _ size: Int64, _ sha: String) -> Model {
        Model(url: "https://huggingface.co/\(repo)/resolve/main/\(path)", name: name, size: size, sha256: sha)
    }

    static let earsDir = "ears"
    static let brainsHeDir = "brains-he"
    static let brainsEnDir = "brains-en"
    static let beakDir = "beak"
    static let beakEnDir = "beak-en"
    private static let earsRepo = "dtsj89/puki-ears"
    private static let beakEnRepo = "dtsj89/puki-beak-en"
    private static let brainsHeRepo = "dtsj89/puki-brains-he"
    private static let brainsEnRepo = "dtsj89/puki-brains-en"
    private static let beakRepo = "dtsj89/puki-beak-he"
    private static let syrinxRepo = "dtsj89/puki-syrinx"

    /** What every language needs: the voice and the ears. */
    let shared: [Model] = [
        hf(syrinxRepo, "duration_predictor_style.onnx", "voice/duration_predictor_style.onnx", 1508749, "3d0347cfc09234b78a20d5fa50b5c402d338fb99825f40ada8c70301edbb60ea"),
        hf(syrinxRepo, "text_encoder.onnx", "voice/text_encoder.onnx", 27419694, "f971aa25315b93efff1850b39fe293aa93ae85df69fabf5e5022318cc632973c"),
        hf(syrinxRepo, "vector_estimator.onnx", "voice/vector_estimator.onnx", 34535720, "e4ae775c1287a8d441a26c4d0b6961a941762032690bb061757ae26afccfb82f"),
        hf(syrinxRepo, "vocoder.onnx", "voice/vocoder.onnx", 35518078, "525c2c3ea5b10d2f9c57f7ac1093a568d3e3351d7efeec087cf89202eeac3e81"),
        hf(syrinxRepo, "uncond.npz", "voice/uncond.npz", 52732, "1c71c2b2836fd97f4c56eb21e258227650bbe0dcd906f64e2bba58e6ac09e76b"),
        hf(syrinxRepo, "stats.npz", "voice/stats.npz", 1920, "20d44fae010d46b859ee6238609780eb7bd5bf82320bad17b826a5dcfa4ed658"),
        hf(syrinxRepo, "vocab.json", "voice/vocab.json", 3157, "13fba5e735222fcf30d9c062d898982a1ba86d481f11560fbbdf6e0af79417b3"),
        hf(syrinxRepo, "tts.json", "voice/tts.json", 9260, "862d44194197de4873eccc8bfdab1835d15f98e469a90489df6da8f92a48c7ea"),
        Model(url: "https://raw.githubusercontent.com/maxmelichov/BlueTTS/0e38dbf08ed53f85863d1eab092bd9572c53a503/voices/noa.json",
              name: "voice/noa.json", size: 288898, sha256: "c233f066a7d505306892509649bc80abeb7d0a565687f143e277ec93874a47c8"),
        hf(earsRepo, "encoder.onnx", "\(earsDir)/encoder.onnx", 357955315, "4193fc7e9a39bb33e3d1fbc645db39f9a917d6996aa0d4f9aba5080b0c77dd3d"),
        hf(earsRepo, "step.onnx", "\(earsDir)/step.onnx", 5535895, "438b04f5eb825103fdb9f03fc06a4f91827aac8ef3d92837c4f20db4ec834bba"),
        hf(earsRepo, "tokens.txt", "\(earsDir)/tokens.txt", 17060, "1dfdd2c55ec357b9b2dd575b2dcf4c729774155097416fa5d7c3f3565f4f16ae"),
    ]

    /** Hebrew: its Brains, and Beak. */
    let hebrew: [Model] = [
        hf(brainsHeRepo, "brains_int8.onnx", "\(brainsHeDir)/brains_int8.onnx", 61198215, "a4412965e18e3d335d64dc19fb4222dba889f7e0e7dec0ef4db1813976ba48ce"),
        hf(brainsHeRepo, "tokenizer.json", "\(brainsHeDir)/tokenizer.json", 700924, "9cdd53bc43bbbf9ada1840137c7996ee2aa0352dfccc156c342ef9696dce7acf"),
        hf(brainsHeRepo, "brains.json", "\(brainsHeDir)/brains.json", 101, "fed5775d484310b3ffff311052805b237761d3d0a6151ac5d8a39a4bfd77d16d"),
        hf(beakRepo, "beak_int8.onnx", "\(beakDir)/beak_int8.onnx", 2545483, "1d4f77719d21a5ec6cdd7710066ba9b081b39fdcc4052ca1eed2236996f0702d"),
        hf(beakRepo, "vocab.json", "\(beakDir)/vocab.json", 1346, "c432d47d12e89098b28c53b9666179dad04e0beb2755ed5632fddd29a757b00f"),
    ]

    /** English: its Brains, and the English reader (EnglishBeak). */
    let english: [Model] = [
        hf(brainsEnRepo, "brains_int8.onnx", "\(brainsEnDir)/brains_int8.onnx", 61198215, "8d74b98e8aba301c9107646ca31abbfd69ca2b7dfa6f63c0bc00e5c02cd2eb97"),
        hf(brainsEnRepo, "tokenizer.json", "\(brainsEnDir)/tokenizer.json", 551326, "e2b00c7270b48c22bcb980e25cb050a34bdafcabb7095632fb218ae4d93932bb"),
        hf(brainsEnRepo, "brains.json", "\(brainsEnDir)/brains.json", 101, "fed5775d484310b3ffff311052805b237761d3d0a6151ac5d8a39a4bfd77d16d"),
        hf(beakEnRepo, "reader_int8.onnx", "\(beakEnDir)/reader_int8.onnx", 2873594, "d1ca62ceef4d3d8d01f51d5d26c1c9c4c5dd66f9e24f572449c82b6dfc86c318"),
        hf(beakEnRepo, "symbols.json", "\(beakEnDir)/symbols.json", 387, "2eaf977851a9ead653f473947ac840ae02a0e6a5f4e80abd15bdacbaa48767a1"),
        hf(beakEnRepo, "words.tsv", "\(beakEnDir)/words.tsv", 726932, "569b25dbea7e9506380e0ddb4e71dc977ee05f5e77d5fb4dc1be4a9fe2c3413b"),
    ]

    init() {
        dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("brain")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var u = dir
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? u.setResourceValues(values)
    }

    /** The models for `langs`. */
    func models(_ langs: BrainLanguages) -> [Model] { shared + (langs.hebrew ? hebrew : []) + (langs.english ? english : []) }

    func totalBytes(_ langs: BrainLanguages) -> Int64 { models(langs).reduce(0) { $0 + $1.size } }

    /** What's still to download for `langs`. */
    func missingBytes(_ langs: BrainLanguages) -> Int64 { models(langs).filter { !done($0) }.reduce(0) { $0 + $1.size } }

    func file(_ name: String) -> URL { dir.appendingPathComponent(name) }
    private func okMark(_ m: Model) -> URL { dir.appendingPathComponent(m.name + ".ok") }
    private func length(_ u: URL) -> Int64 { ((try? FileManager.default.attributesOfItem(atPath: u.path)[.size]) as? NSNumber)?.int64Value ?? 0 }

    private func done(_ m: Model) -> Bool { FileManager.default.fileExists(atPath: okMark(m).path) && length(file(m.name)) == m.size }

    func ready(_ langs: BrainLanguages) -> Bool { models(langs).allSatisfy(done) }

    /** Enough for the brain to wake: the shared models and at least one language. */
    func ready() -> Bool { shared.allSatisfy(done) && (hasHebrew() || hasEnglish()) }

    func hasHebrew() -> Bool { hebrew.allSatisfy(done) }
    func hasEnglish() -> Bool { english.allSatisfy(done) }

    /** The languages to offer: the parent's pick, else what's already downloaded, else the game's language. */
    func languagesFor(_ picked: BrainLanguages?, game: Language) -> BrainLanguages {
        if let picked { return picked }
        if hasHebrew() && hasEnglish() { return .both }
        if hasHebrew() { return .hebrew }
        if hasEnglish() { return .english }
        return game == .english ? .english : .hebrew
    }

    private var downloading: [Model] = []

    func downloadedBytes() -> Int64 {
        downloading.reduce(0) { $0 + (done($1) ? $1.size : length(file($1.name + ".part"))) }
    }

    /** Download progress (0..1) while `download` runs, else nil. */
    @Published private(set) var progress: Float?
    private let downloadLock = NSLock()

    private func setProgress(_ p: Float?) async { await MainActor.run { self.progress = p } }

    struct DownloadError: LocalizedError { let errorDescription: String? }

    /** Downloads what `langs` needs, and frees a language's models that the parent no longer picked. */
    func download(_ langs: BrainLanguages) async throws {
        guard downloadLock.try() else { return }  // (one download at a time)
        defer { downloadLock.unlock() }
        downloading = models(langs)
        let total = Float(totalBytes(langs))
        await setProgress(Float(downloadedBytes()) / total)
        defer { DispatchQueue.main.async { self.progress = nil } }
        for m in downloading where !done(m) { try await ensure(m) }
        for m in (langs.hebrew ? [] : hebrew) + (langs.english ? [] : english) {
            try? FileManager.default.removeItem(at: file(m.name))
            try? FileManager.default.removeItem(at: okMark(m))
        }
    }

    /** Frees the space again (parent settings). */
    func delete() {
        try? FileManager.default.removeItem(at: dir)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    }

    private func ensure(_ m: Model) async throws {
        let dest = file(m.name)
        if length(dest) == m.size && FileManager.default.fileExists(atPath: okMark(m).path) { return }
        try FileManager.default.createDirectory(at: dest.deletingLastPathComponent(), withIntermediateDirectories: true)
        let part = file(m.name + ".part")
        // Retry dropped connections, but give up after 20 tries that get nowhere
        var stuck = 0
        while length(part) < m.size {
            try Task.checkCancellation()
            let before = length(part)
            do { try await fetch(m, part) } catch { if stuck >= 20 { throw error } }
            if length(part) > before { stuck = 0 } else {
                stuck += 1
                if stuck > 20 { throw DownloadError(errorDescription: "\(m.name): the download keeps stopping") }
            }
            if length(part) < m.size { try await Task.sleep(nanoseconds: 3_000_000_000) }
        }
        if try sha256(part) != m.sha256 {
            try? FileManager.default.removeItem(at: part)
            throw DownloadError(errorDescription: "\(m.name) came down damaged; try again")
        }
        try? FileManager.default.removeItem(at: dest)
        try FileManager.default.moveItem(at: part, to: dest)
        try m.sha256.write(to: okMark(m), atomically: true, encoding: .utf8)
    }

    private func fetch(_ m: Model, _ part: URL) async throws {
        let have = length(part)
        var req = URLRequest(url: URL(string: m.url)!, timeoutInterval: 30)
        if have > 0 { req.setValue("bytes=\(have)-", forHTTPHeaderField: "Range") }
        let (bytes, response) = try await URLSession.shared.bytes(for: req)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200 || code == 206 else { throw DownloadError(errorDescription: "\(m.name): HTTP \(code)") }
        if code == 200 || !FileManager.default.fileExists(atPath: part.path) {
            FileManager.default.createFile(atPath: part.path, contents: nil)
        }
        let h = try FileHandle(forWritingTo: part)
        defer { try? h.close() }
        if code == 206 { try h.seekToEnd() } else { try h.truncate(atOffset: 0) }
        var buf = [UInt8]()
        buf.reserveCapacity(1 << 20)
        var last = DispatchTime.now().uptimeNanoseconds
        let total = Float(downloading.reduce(0) { $0 + $1.size })
        for try await b in bytes {
            buf.append(b)
            if buf.count >= 1 << 20 {
                try h.write(contentsOf: buf)
                buf.removeAll(keepingCapacity: true)
                let now = DispatchTime.now().uptimeNanoseconds
                if now - last > 500_000_000 {
                    last = now
                    let p = Float(downloadedBytes()) / total
                    await setProgress(p)
                }
            }
        }
        if !buf.isEmpty { try h.write(contentsOf: buf) }
    }

    private func sha256(_ u: URL) throws -> String {
        let h = try FileHandle(forReadingFrom: u)
        defer { try? h.close() }
        var hasher = SHA256()
        while let chunk = try h.read(upToCount: 4 << 20), !chunk.isEmpty { hasher.update(data: chunk) }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /** Whether settings offer the download. */
    static let offered = true

    /** Devices that can run it: about 3 GB of memory or more (every iPhone and iPad is 64-bit ARM). */
    static func capable() -> Bool { ProcessInfo.processInfo.physicalMemory >= 2_500_000_000 }
}
