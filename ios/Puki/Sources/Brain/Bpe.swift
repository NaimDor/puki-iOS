import Foundation

/**
 * The byte-level BPE of Puki's own model (Brains), from its tokenizer.json: the one HuggingFace
 * tokenizers writes for a ByteLevel pre-tokenizer and BPE (GPT-2's scheme). Text is split by GPT-2's
 * pattern, each piece's UTF-8 bytes become printable characters, and merges join them by rank.
 */
final class Bpe {
    private let vocab: [String: Int]
    private let tokens: [String]
    private let ranks: [String: Int]  // "a b" -> rank (lower merges first)
    private var cache: [String: [Int]] = [:]
    private let lock = NSLock()

    init(json: Data) throws {
        guard let root = try JSONSerialization.jsonObject(with: json) as? [String: Any],
              let model = root["model"] as? [String: Any],
              let v = model["vocab"] as? [String: Int],
              let merges = model["merges"] as? [Any] else { throw NSError(domain: "Bpe", code: 1) }
        vocab = v
        var arr = [String](repeating: "", count: v.count)
        for (t, i) in v where i < arr.count { arr[i] = t }
        tokens = arr
        var r = [String: Int](minimumCapacity: merges.count * 2)
        for (i, m) in merges.enumerated() {
            if let pair = m as? [String], pair.count == 2 { r[pair[0] + " " + pair[1]] = i } else if let s = m as? String { r[s] = i }
        }
        ranks = r
    }

    func id(_ token: String) -> Int? { vocab[token] }

    func encode(_ text: String) -> [Int] {
        var out: [Int] = []
        for piece in Self.split.findAll(text) {
            let word = String(String.UnicodeScalarView(piece.utf8.map { Self.byteToChar[Int($0)] }))
            lock.lock()
            let hit = cache[word]
            lock.unlock()
            if let hit { out += hit; continue }
            let ids = bpe(word)
            lock.lock(); cache[word] = ids; lock.unlock()
            out += ids
        }
        return out
    }

    func decode(_ ids: [Int]) -> String {
        var bytes: [UInt8] = []
        for id in ids where id >= 0 && id < tokens.count {
            for s in tokens[id].unicodeScalars { if let b = Self.charToByte[s] { bytes.append(b) } }
        }
        return String(decoding: bytes, as: UTF8.self)
    }

    private func bpe(_ word: String) -> [Int] {
        var parts = word.unicodeScalars.map { String($0) }
        while parts.count > 1 {
            var best = -1
            var bestRank = Int.max
            for i in 0..<(parts.count - 1) {
                if let r = ranks[parts[i] + " " + parts[i + 1]], r < bestRank { bestRank = r; best = i }
            }
            if best < 0 { break }
            let a = parts[best], b = parts[best + 1]
            var merged: [String] = []
            merged.reserveCapacity(parts.count)
            var i = 0
            while i < parts.count {
                if i < parts.count - 1 && parts[i] == a && parts[i + 1] == b { merged.append(a + b); i += 2 } else { merged.append(parts[i]); i += 1 }
            }
            parts = merged
        }
        return parts.map { vocab[$0] ?? 0 }
    }

    // GPT-2's split, as tokenizers' ByteLevel pre-tokenizer (use_regex) has it
    private static let split = KRegex("'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")

    // Every byte as a printable character (GPT-2's bytes_to_unicode)
    private static let byteToChar: [Unicode.Scalar] = {
        let printable = Set(Array(33...126) + Array(161...172) + Array(174...255))
        var extra = 0
        return (0..<256).map { b in
            if printable.contains(b) { return Unicode.Scalar(UInt32(b))! }
            defer { extra += 1 }
            return Unicode.Scalar(UInt32(256 + extra))!
        }
    }()
    private static let charToByte: [Unicode.Scalar: UInt8] = {
        var m: [Unicode.Scalar: UInt8] = [:]
        for (i, c) in byteToChar.enumerated() { m[c] = UInt8(i) }
        return m
    }()
}
