import Foundation
#if canImport(OnnxRuntimeBindings)
import OnnxRuntimeBindings

/**
 * Puki's ears on the device: kol-stream (EmbeddingGemma 2's audio encoder with a small reader and a transducer;
 * Hebrew and English; Apache 2.0) through ONNX Runtime. `dir` holds encoder.onnx, step.onnx and tokens.txt.
 */
final class KolEars {
    private let enc: ORTSession
    private let step: ORTSession
    private let pieces: [String]
    private let lock = NSLock()

    static let front = 160, frame = 320, hop = 160, minFrames = 16, dj = 512, block = 16, maxPerRow = 3

    init(dir: URL, threads: Int = 4) throws {
        // Both come int8 and already optimized, so they load as they are
        enc = try Ort.session(dir.appendingPathComponent("encoder.onnx"), threads: threads, optimize: false)
        step = try Ort.session(dir.appendingPathComponent("step.onnx"), threads: threads, optimize: false)
        pieces = try String(contentsOf: dir.appendingPathComponent("tokens.txt"), encoding: .utf8).components(separatedBy: "\n")
    }

    /** What was said in `wav` (16 kHz mono 16-bit, as the app records), "" if nothing. */
    func transcribe(wav: Data) throws -> String { try transcribe(pcm16(wav)) }

    func transcribe(_ audio: [Float]) throws -> String {
        lock.lock(); defer { lock.unlock() }
        let samples = Self.louder(audio)
        // Frames as Gemma's feature extractor cuts them: 160 zeros in front, 320 samples every 160
        let m = (Self.front + samples.count - Self.frame - 1) / Self.hop + 1
        if m < Self.minFrames { return "" }
        var frames = [Float](repeating: 0, count: m * Self.frame)
        for i in 0..<m {
            for j in 0..<Self.frame {
                let s = i * Self.hop + j - Self.front
                if s >= 0 && s < samples.count { frames[i * Self.frame + j] = samples[s] }
            }
        }
        let x = try Ort.floats(frames, [1, m, Self.frame])
        let out = try Ort.first(enc, Ort.run(enc, ["frames": x]))
        let e = try Ort.floatsOf(out)
        let t = try Ort.shapeOf(out)[1]
        return decode(try greedy(e, t))
    }

    /** search.greedy_fast: score `block` rows at once and jump to the first row that writes something. */
    private func greedy(_ e: [Float], _ t: Int) throws -> [Int] {
        var ctx = [0, 0]
        var out: [Int] = []
        var row = 0, atRow = 0
        while row < t {
            let n = atRow == 0 ? min(Self.block, t - row) : 1
            let best = try argmaxRows(e, row, n, ctx)
            let j = atRow == 0 ? (best.firstIndex { $0 != 0 } ?? -1) : (best[0] != 0 ? 0 : -1)
            if j < 0 {
                row += atRow == 0 ? n : 1
                atRow = 0
                continue
            }
            row += j
            out.append(best[j])
            ctx[0] = ctx[1]; ctx[1] = best[j]
            atRow += 1
            if atRow >= Self.maxPerRow { row += 1; atRow = 0 }
        }
        return out
    }

    private func argmaxRows(_ e: [Float], _ row: Int, _ n: Int, _ ctx: [Int]) throws -> [Int] {
        let rows = try Ort.floats(Array(e[(row * Self.dj)..<((row + n) * Self.dj)]), [n, Self.dj])
        let last = try Ort.int64s([Int64(ctx[0]), Int64(ctx[1])], [1, 2])
        let b = try Ort.floatsOf(Ort.first(step, Ort.run(step, ["e": rows, "ctx": last])))
        let v = b.count / n
        return (0..<n).map { i in
            var k = 0
            var top = b[i * v]
            for c in 1..<v where b[i * v + c] > top { top = b[i * v + c]; k = c }
            return k
        }
    }

    /** Sentencepiece pieces back to text ("▁" starts a word; <0x..> pieces are UTF-8 bytes). */
    private func decode(_ ids: [Int]) -> String {
        var bytes: [UInt8] = []
        for id in ids {
            guard let p = pieces.getOrNull(id) else { continue }
            if p.count == 6 && p.hasPrefix("<0x") && p.hasSuffix(">"), let b = UInt8(p.dropFirst(3).prefix(2), radix: 16) {
                bytes.append(b)
            } else if !(p.hasPrefix("<") && p.hasSuffix(">")) {
                bytes += Array(p.replacingOccurrences(of: "▁", with: " ").utf8)
            }
        }
        return String(decoding: bytes, as: UTF8.self).trimmed()
    }

    /** model.level_gain: quiet input is raised towards the training level, at most 10x, never lowered. */
    static func louder(_ x: [Float]) -> [Float] {
        let n = x.count / 512
        if n < 4 { return x }
        let e: [Float] = (0..<n).map { f in
            var s = 0.0
            for i in 0..<512 { let v = Double(x[f * 512 + i]); s += v * v }
            return Float((s / 512).squareRoot())
        }
        let sorted = e.sorted()
        let median = n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        let loud = e.filter { $0 >= median }
        let rms = Float((loud.reduce(0.0) { $0 + Double($1 * $1) } / Double(loud.count)).squareRoot())
        if rms <= 1e-4 || rms >= 0.06 { return x }
        let g = min(10, 0.1 / rms)
        return x.map { ($0 * g).coerceIn(-1, 1) }
    }
}
#endif

/** The samples of a 16 kHz mono 16-bit WAV, as -1..1. */
func pcm16(_ wav: Data) -> [Float] {
    let b = [UInt8](wav)
    var start = 44
    if b.count > 20 {
        for i in 12..<min(b.count - 8, 200) where b[i] == 0x64 && b[i + 1] == 0x61 && b[i + 2] == 0x74 && b[i + 3] == 0x61 {
            start = i + 8
            break
        }
    }
    if b.count <= start { return [] }
    let n = (b.count - start) / 2
    var out = [Float](repeating: 0, count: n)
    for i in 0..<n {
        let v = Int16(bitPattern: UInt16(b[start + 2 * i]) | (UInt16(b[start + 2 * i + 1]) << 8))
        out[i] = Float(v) / 32768
    }
    return out
}
