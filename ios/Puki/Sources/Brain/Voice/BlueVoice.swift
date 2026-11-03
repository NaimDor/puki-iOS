import Foundation
import Compression
#if canImport(OnnxRuntimeBindings)
import OnnxRuntimeBindings

/**
 * Puki's voice on this device: BlueTTS (github.com/maxmelichov/BlueTTS, MIT) with the Noa voice, ported from
 * blue_onnx's BlueTTS.synthesize. `modelDir` holds the BlueTTS ONNX bundle (duration_predictor_style,
 * text_encoder, vector_estimator, vocoder, uncond.npz, stats.npz, vocab.json, tts.json); `voiceStyle` is noa.json.
 */
final class BlueVoice {
    let sampleRate: Int
    private let baseChunkSize, compressFactor, latentDim: Int
    private let charToId: [UInt32: Int]
    private let padId: Int
    private let styleTtl: [Float], styleDp: [Float]
    private let ttlShape: [Int], dpShape: [Int]
    private let uText: [Float]  // [1, 256, 1]: classifier-free guidance's "no text"
    private let uRef: [Float]   // [1, 50, 256]: and "no speaker"
    private let latentMean: [Float], latentStd: [Float]
    private let normalizerScale: Float
    private let dp, textEnc, vectorEst, vocoder: ORTSession
    private let lock = NSLock()

    static let cfgScale: Float = 4
    /** BlueTTS's own default: 5 steps, the first 3 guided. */
    static let steps = 5
    static let guidedSteps = 3

    init(modelDir: URL, voiceStyle: URL, threads: Int = 2) throws {
        func json(_ name: String) throws -> [String: Any] {
            guard let o = try JSONSerialization.jsonObject(with: Data(contentsOf: modelDir.appendingPathComponent(name))) as? [String: Any] else {
                throw Ort.Failure(what: name)
            }
            return o
        }
        let cfg = try json("tts.json")
        guard let ae = cfg["ae"] as? [String: Any], let ttl = cfg["ttl"] as? [String: Any],
              let sr = ae["sample_rate"] as? Int, let bcs = ae["base_chunk_size"] as? Int,
              let ccf = ttl["chunk_compress_factor"] as? Int, let ld = ttl["latent_dim"] as? Int else { throw Ort.Failure(what: "tts.json") }
        sampleRate = sr; baseChunkSize = bcs; compressFactor = ccf; latentDim = ld

        let vocab = try json("vocab.json")
        padId = vocab["pad_id"] as? Int ?? 0
        var c2i: [UInt32: Int] = [:]
        for (k, v) in (vocab["char_to_id"] as? [String: Int]) ?? [:] {
            let sc = Array(k.unicodeScalars)
            if sc.count == 1 { c2i[sc[0].value] = v }
        }
        charToId = c2i

        guard let style = try JSONSerialization.jsonObject(with: Data(contentsOf: voiceStyle)) as? [String: Any] else { throw Ort.Failure(what: "style") }
        func styleOf(_ key: String) throws -> ([Float], [Int]) {
            guard let o = style[key] as? [String: Any], let dims = o["dims"] as? [Int], let data = o["data"] else { throw Ort.Failure(what: key) }
            var flat: [Float] = []
            func walk(_ v: Any) {
                if let a = v as? [Any] { a.forEach(walk) } else if let n = v as? NSNumber { flat.append(n.floatValue) }
            }
            walk(data)
            return (flat, dims)
        }
        (styleTtl, ttlShape) = try styleOf("style_ttl")
        (styleDp, dpShape) = try styleOf("style_dp")

        let uncond = try readNpz(modelDir.appendingPathComponent("uncond.npz"))
        guard let ut = uncond["u_text"], let ur = uncond["u_ref"] else { throw Ort.Failure(what: "uncond.npz") }
        uText = ut; uRef = ur
        let stats = try readNpz(modelDir.appendingPathComponent("stats.npz"))
        guard let mean = stats["mean"], let std = stats["std"] else { throw Ort.Failure(what: "stats.npz") }
        latentMean = mean; latentStd = std
        if let s = stats["normalizer_scale"]?.first, s != 0 { normalizerScale = s } else { normalizerScale = 1 }

        dp = try Ort.session(modelDir.appendingPathComponent("duration_predictor_style.onnx"), threads: threads)
        textEnc = try Ort.session(modelDir.appendingPathComponent("text_encoder.onnx"), threads: threads)
        vectorEst = try Ort.session(modelDir.appendingPathComponent("vector_estimator.onnx"), threads: threads)
        vocoder = try Ort.session(modelDir.appendingPathComponent("vocoder.onnx"), threads: threads)
    }

    /** `phonemes` (IPA, as Beak writes them) as a 16-bit mono WAV with a short silence at the end, or nil. */
    func speakPhonemes(_ phonemes: String, speed: Float = 0.92, totalStep: Int = BlueVoice.steps) throws -> Data? {
        guard let samples = try voice(phonemes, speed: speed, totalStep: totalStep) else { return nil }
        return wav(samples + [Float](repeating: 0, count: Int(Double(sampleRate) * 0.15)))
    }

    /** `phonemes` spoken: chunked, voiced, peak-limited. */
    func voice(_ phonemes: String, speed: Float = 0.92, totalStep: Int = BlueVoice.steps) throws -> [Float]? {
        lock.lock(); defer { lock.unlock() }
        var out: [Float] = []
        for chunk in VoiceText.chunkText(phonemes) { out += try infer(chunk, speed, totalStep) }
        if out.isEmpty { return nil }
        // limit_peak: the vocoder sometimes overshoots ±1, so scale down (never up)
        let peak = out.map(abs).max() ?? 0
        if peak > 0.95 { for i in out.indices { out[i] *= 0.95 / peak } }
        return out
    }

    private static func gaussian() -> Float {
        // Box-Muller
        let u1 = max(Float.random(in: 0..<1), 1e-7), u2 = Float.random(in: 0..<1)
        return (-2 * logf(u1)).squareRoot() * cosf(2 * .pi * u2)
    }

    /** TextToSpeech._infer for one chunk, with classifier-free guidance (scale 4). */
    private func infer(_ chunk: String, _ speed: Float, _ totalStep: Int) throws -> [Float] {
        let ids = VoiceText.encode(chunk, charToId: charToId, padId: padId)
        let nTxt = ids.count
        let textIds = try Ort.int64s(ids.map { Int64($0) }, [1, nTxt])
        let textMask = try Ort.floats([Float](repeating: 1, count: nTxt), [1, 1, nTxt])
        let sTtl = try Ort.floats(styleTtl, ttlShape)
        let sDp = try Ort.floats(styleDp, dpShape)

        let durOut = try Ort.run(dp, ["text_ids": textIds, "style_dp": sDp, "text_mask": textMask])
        let duration = (try Ort.floatsOf(Ort.first(dp, durOut)))[0] / max(speed, 1e-6)
        let textEmb = try Ort.floatsOf(Ort.first(textEnc, Ort.run(textEnc, ["text_ids": textIds, "style_ttl": sTtl, "text_mask": textMask])))
        let tEmb = try Ort.floats(textEmb, [1, 256, nTxt])

        // sample_noisy_latent
        let frame = baseChunkSize * compressFactor
        let wavLen = Int(Double(duration) * Double(sampleRate))  // float32 duration, float64 product, as numpy does
        let latLen = max(0, (wavLen + frame - 1) / frame)
        let ch = latentDim * compressFactor
        var xt = (0..<(ch * latLen)).map { _ in Self.gaussian() }
        let latentMask = try Ort.floats([Float](repeating: 1, count: latLen), [1, 1, latLen])
        let total = try Ort.floats([Float(totalStep)], [1])
        let uTxt = try Ort.floats(uText, [1, 256, 1])
        let uRf = try Ort.floats(uRef, [1, 50, 256])
        let uMask = try Ort.floats([1], [1, 1, 1])

        for step in 0..<totalStep {
            let x = try Ort.floats(xt, [1, ch, latLen])
            let cur = try Ort.floats([Float(step)], [1])
            let vCond = try Ort.floatsOf(Ort.first(vectorEst, Ort.run(vectorEst, ["noisy_latent": x, "text_emb": tEmb, "style_ttl": sTtl, "text_mask": textMask,
                "latent_mask": latentMask, "current_step": cur, "total_step": total])))
            if step >= Self.guidedSteps {
                xt = vCond  // (the graph takes the step itself: unguided, its answer is the next latent)
                continue
            }
            let vUncond = try Ort.floatsOf(Ort.first(vectorEst, Ort.run(vectorEst, ["noisy_latent": x, "text_emb": uTxt, "style_ttl": uRf, "text_mask": uMask,
                "latent_mask": latentMask, "current_step": cur, "total_step": total])))
            xt = (0..<xt.count).map { vUncond[$0] + Self.cfgScale * (vCond[$0] - vUncond[$0]) }
        }

        // _prepare_vocoder_latent: de-normalize, then (24*6, T) -> (24, T*6)
        var z = [Float](repeating: 0, count: xt.count)
        for c in 0..<ch {
            for t in 0..<latLen {
                let v = (xt[c * latLen + t] / normalizerScale) * latentStd[c] + latentMean[c]
                let l = c / compressFactor, k = c % compressFactor
                z[l * latLen * compressFactor + t * compressFactor + k] = v
            }
        }
        let latent = try Ort.floats(z, [1, latentDim, latLen * compressFactor])
        var w = try Ort.floatsOf(Ort.first(vocoder, Ort.run(vocoder, ["latent": latent])))
        if w.count > 2 * frame { w = Array(w[frame..<(w.count - frame)]) }
        return w
    }

    func wav(_ samples: [Float]) -> Data {
        var d = Data(capacity: samples.count * 2)
        for s in samples {
            var v = Int16(Int(s.coerceIn(-1, 1) * 32767)).littleEndian
            d.append(Data(bytes: &v, count: 2))
        }
        return wavBytes(d, rate: sampleRate)
    }
}
#endif

/** The float32 arrays in a NumPy .npz (a zip of .npy files), flattened. */
func readNpz(_ url: URL) throws -> [String: [Float]] {
    var out: [String: [Float]] = [:]
    for (name, bytes) in try readZip(Data(contentsOf: url)) {
        let b = [UInt8](bytes)
        guard b.count > 10 else { continue }
        let major = Int(b[6])
        let headerLen = major == 1 ? Int(b[8]) | Int(b[9]) << 8 : Int(b[8]) | Int(b[9]) << 8 | Int(b[10]) << 16 | Int(b[11]) << 24
        let hStart = major == 1 ? 10 : 12
        let start = hStart + headerLen
        let header = String(decoding: b[hStart..<min(b.count, start)], as: UTF8.self)
        guard header.contains("'<f4'"), start <= b.count else { continue }
        let n = (b.count - start) / 4
        var f = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let o = start + i * 4
            f[i] = Float(bitPattern: UInt32(b[o]) | UInt32(b[o + 1]) << 8 | UInt32(b[o + 2]) << 16 | UInt32(b[o + 3]) << 24)
        }
        out[name.removeSuffix(".npy")] = f
    }
    return out
}

/** The files in a zip (stored or deflated), by name. */
func readZip(_ data: Data) throws -> [(String, Data)] {
    let b = [UInt8](data)
    func u16(_ o: Int) -> Int { Int(b[o]) | Int(b[o + 1]) << 8 }
    func u32(_ o: Int) -> Int { Int(b[o]) | Int(b[o + 1]) << 8 | Int(b[o + 2]) << 16 | Int(b[o + 3]) << 24 }
    // The central directory, from the end record
    var eocd = -1
    var i = b.count - 22
    while i >= max(0, b.count - 65_557) { if u32(i) == 0x06054b50 { eocd = i; break }; i -= 1 }
    guard eocd >= 0 else { throw NSError(domain: "zip", code: 1) }
    let count = u16(eocd + 10)
    var p = u32(eocd + 16)
    var out: [(String, Data)] = []
    for _ in 0..<count {
        guard p + 46 <= b.count, u32(p) == 0x02014b50 else { break }
        let method = u16(p + 10)
        let csize = u32(p + 20), usize = u32(p + 24)
        let nlen = u16(p + 28), elen = u16(p + 30), clen = u16(p + 32)
        let local = u32(p + 42)
        let name = String(decoding: b[(p + 46)..<(p + 46 + nlen)], as: UTF8.self)
        p += 46 + nlen + elen + clen
        let dataStart = local + 30 + u16(local + 26) + u16(local + 28)
        guard dataStart + csize <= b.count else { continue }
        let raw = Data(b[dataStart..<(dataStart + csize)])
        if method == 0 {
            out.append((name, raw))
        } else if method == 8 {
            var dst = [UInt8](repeating: 0, count: usize)
            let n = raw.withUnsafeBytes { src in
                compression_decode_buffer(&dst, usize, src.bindMemory(to: UInt8.self).baseAddress!, csize, nil, COMPRESSION_ZLIB)
            }
            if n == usize { out.append((name, Data(dst))) }
        }
    }
    return out
}
