import Foundation
#if canImport(OnnxRuntimeBindings)
import OnnxRuntimeBindings

/**
 * Puki's own small model (train/: pretrained, then fine-tuned on Puki's lines), through ONNX Runtime with a KV
 * cache: it writes one line for a task and input, in the format it was trained on (train/fmt.py), and answers the
 * safety gate with the probabilities of its digits. `dir` holds brains_int8.onnx, tokenizer.json and brains.json.
 */
final class Brains {
    private let session: ORTSession
    private let bpe: Bpe
    private let layers, kvHeads, headDim, eot, context: Int
    private let digits: [String: Int]  // the gate's answers, " 0".." 3", each one token

    init(dir: URL, threads: Int = 4) throws {
        session = try Ort.session(dir.appendingPathComponent("brains_int8.onnx"), threads: threads)
        bpe = try Bpe(json: Data(contentsOf: dir.appendingPathComponent("tokenizer.json")))
        guard let cfg = try JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("brains.json"))) as? [String: Any],
              let l = cfg["layers"] as? Int, let k = cfg["kv_heads"] as? Int, let h = cfg["head_dim"] as? Int,
              let e = cfg["eot"] as? Int, let c = cfg["context"] as? Int else { throw Ort.Failure(what: "brains.json") }
        layers = l; kvHeads = k; headDim = h; eot = e; context = c
        var d: [String: Int] = [:]
        for ch in "0123" {
            let ids = bpe.encode(" \(ch)")
            guard ids.count == 1 else { throw Ort.Failure(what: "gate digits") }
            d[String(ch)] = ids[0]
        }
        digits = d
    }

    /** The prompt, as train/fmt.py writes it (with the earlier moment, for reach_back). */
    private func prompt(_ task: String, _ lang: String, _ girl: Bool, _ input: String, _ earlier: String?) -> String {
        "lang: \(lang)\nchild: \(girl ? "girl" : "boy")\ntask: \(task)\ninput: \(input)\n" +
            ((earlier ?? "").isEmpty ? "" : "earlier: \(earlier!)\n") + "puki:"
    }

    /** Puki's line for `input` (nil if it came out empty): temperature 0.5, top-p 0.9. */
    func line(task: String, lang: String, girl: Bool, input: String, maxNew: Int = 48, earlier: String? = nil) throws -> String? {
        var out: [Int] = []
        try generate(bpe.encode(prompt(task, lang, girl, input, earlier)), maxNew) { logits in
            let t = Self.sample(logits, 0.5, 0.9)
            if t == eot { return nil }
            out.append(t)
            return t
        }
        let s = bpe.decode(out).trimmed()
        return s.isEmpty ? nil : s
    }

    /** The gate's probabilities for "0".."3" (Safety.gateCategory): what she said, ordinary or careful. */
    func gate(_ heard: String, lang: String, girl: Bool) throws -> [String: Double] {
        var probs: [String: Double] = [:]
        try generate(bpe.encode(prompt("gate", lang, girl, heard, nil)), 1) { logits in
            let p = Self.softmax(logits, 1)
            for (d, id) in digits { probs[d] = Double(p[id]) }
            return nil
        }
        return probs
    }

    /** Runs the prompt, then a token at a time: `next` gets the logits and gives the token to go on with, or nil to stop. */
    private func generate(_ ids: [Int], _ maxNew: Int, _ next: ([Float]) -> Int?) throws {
        let keep = context - maxNew
        var input = ids.count > keep ? Array(ids.suffix(keep)) : ids
        let empty = [layers, kvHeads, 0, headDim]
        var pastK = try Ort.floats([], empty)
        var pastV = try Ort.floats([], empty)
        for _ in 0..<maxNew {
            let idsT = try Ort.int64s(input.map { Int64($0) }, [1, input.count])
            let r = try session.run(withInputs: ["ids": idsT, "past_k": pastK, "past_v": pastV],
                                    outputNames: ["logits", "next_k", "next_v"], runOptions: nil)
            guard let k = r["next_k"], let v = r["next_v"], let lg = r["logits"] else { throw Ort.Failure(what: "brains output") }
            pastK = k; pastV = v
            // (the model hands back the last position's logits only)
            guard let t = next(try Ort.floatsOf(lg)) else { return }
            input = [t]
        }
    }

    private static func softmax(_ logits: [Float], _ temperature: Float) -> [Float] {
        let mx = logits.max() ?? 0
        var p = logits.map { expf(($0 - mx) / temperature) }
        let sum = p.reduce(0, +)
        for i in p.indices { p[i] /= sum }
        return p
    }

    private static let ln1e4 = Float(log(1e-4))

    /** A token from the top-p share of the distribution at `temperature` (only tokens near the likeliest are sorted). */
    private static func sample(_ logits: [Float], _ temperature: Float, _ topP: Float) -> Int {
        var mx = -Float.infinity
        for l in logits where l > mx { mx = l }
        let floor = mx + temperature * ln1e4
        var ids: [Int] = [], p: [Float] = []
        var sum: Float = 0
        for (i, l) in logits.enumerated() where l >= floor {
            ids.append(i)
            let e = expf((l - mx) / temperature)
            p.append(e)
            sum += e
        }
        let order = p.indices.sorted { p[$0] > p[$1] }
        var mass: Float = 0
        var cut = order.count
        for (j, o) in order.enumerated() {
            mass += p[o] / sum
            if mass >= topP { cut = j + 1; break }
        }
        var x = Float.random(in: 0..<1) * mass
        for j in 0..<cut {
            x -= p[order[j]] / sum
            if x <= 0 { return ids[order[j]] }
        }
        return ids[order[cut - 1]]
    }
}
#endif
