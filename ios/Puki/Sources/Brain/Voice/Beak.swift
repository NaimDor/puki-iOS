import Foundation
#if canImport(OnnxRuntimeBindings)
import OnnxRuntimeBindings

/**
 * Puki's reading model (train/read_train.py, 2.3M parameters): Hebrew letters to IPA, for a girl or a boy. It
 * predicts a consonant, a vowel and stress for each letter. `dir` holds beak_int8.onnx and vocab.json.
 */
final class Beak {
    private let session: ORTSession
    private let consonants: [Int: String]
    private let vowels: [Int: String]
    private let allowed: [Unicode.Scalar: [Int]]
    private let geresh: [Unicode.Scalar: String]
    private let lock = NSLock()

    init(dir: URL, threads: Int = 2) throws {
        session = try Ort.session(dir.appendingPathComponent("beak_int8.onnx"), threads: threads)
        guard let v = try JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("vocab.json"))) as? [String: Any] else {
            throw Ort.Failure(what: "beak vocab")
        }
        func strings(_ key: String) -> [Int: String] {
            var out: [Int: String] = [:]
            for (k, s) in (v[key] as? [String: String]) ?? [:] { if let i = Int(k) { out[i] = s } }
            return out
        }
        consonants = strings("consonants")
        vowels = strings("vowels")
        var a: [Unicode.Scalar: [Int]] = [:]
        for (k, arr) in (v["allowed"] as? [String: [Int]]) ?? [:] { if let c = k.unicodeScalars.first { a[c] = arr } }
        allowed = a
        var g: [Unicode.Scalar: String] = [:]
        for (k, s) in (v["geresh"] as? [String: String]) ?? [:] { if let c = k.unicodeScalars.first { g[c] = s } }
        geresh = g
    }

    private static func isHebrewLetter(_ c: Unicode.Scalar) -> Bool { c.value >= 0x05D0 && c.value <= 0x05EA }
    private static func isLetter(_ c: Unicode.Scalar) -> Bool {
        switch c.properties.generalCategory {
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter: return true
        default: return false
        }
    }

    /** `text` read aloud as IPA (non-Hebrew characters pass through). */
    func phonemize(_ text: String, girl: Bool) throws -> String {
        lock.lock(); defer { lock.unlock() }
        let t = Array(Self.clean(text).unicodeScalars)
        if t.isEmpty { return "" }
        var ids = [Int64](repeating: 0, count: t.count + 1)
        ids[0] = Int64(girl ? Self.girlId : Self.boyId)
        for i in t.indices { ids[i + 1] = Int64(Self.index[t[i]] ?? Self.unk) }
        let r = try session.run(withInputs: ["ids": try Ort.int64s(ids, [1, ids.count])],
                                outputNames: ["consonant", "vowel", "stress"], runOptions: nil)
        guard let cv = r["consonant"], let vv = r["vowel"], let sv = r["stress"] else { throw Ort.Failure(what: "beak output") }
        let cFlat = try Ort.floatsOf(cv), cShape = try Ort.shapeOf(cv)
        let vFlat = try Ort.floatsOf(vv), vShape = try Ort.shapeOf(vv)
        let stress = try Ort.floatsOf(sv)
        let nc = cShape.last ?? 1, nv = vShape.last ?? 1
        let off = 0  // (row i is letter i, as the Android app reads them)
        func cLogit(_ i: Int, _ k: Int) -> Float { cFlat[(i + off) * nc + k] }
        func vLogit(_ i: Int, _ k: Int) -> Float { vFlat[(i + off) * nv + k] }
        let sOff = 0
        let cons: [Int] = t.indices.map { i in
            if let options = allowed[t[i]], !options.isEmpty { return options.max { cLogit(i, $0) < cLogit(i, $1) }! }
            return (0..<nc).max { cLogit(i, $0) < cLogit(i, $1) }!
        }
        let vowel: [Int] = t.indices.map { i in (0..<nv).max { vLogit(i, $0) < vLogit(i, $1) }! }
        // In each word, the vowel-bearing letter the model is surest of carries the stress
        var stressed = [Bool](repeating: false, count: t.count)
        var i = 0
        while i < t.count {
            if t[i].properties.isWhitespace { i += 1; continue }
            var j = i
            while j < t.count && !t[j].properties.isWhitespace { j += 1 }
            if let best = (i..<j).filter({ vowel[$0] != Self.nothing }).max(by: { stress[$0 + sOff] < stress[$1 + sOff] }) { stressed[best] = true }
            i = j
        }
        var out = ""
        for i in t.indices {
            let ch = t[i]
            if !Self.isHebrewLetter(ch) {
                if ch != "'" && ch != "\"" { out.unicodeScalars.append(ch) }
                continue
            }
            var c = consonants[cons[i]] ?? "∅"
            if let g = geresh[ch], i + 1 < t.count, t[i + 1] == "'" { c = g }
            let v = vowel[i] == Self.nothing ? "" : (vowels[vowel[i]] ?? "")
            let mark = stressed[i] && !v.isEmpty ? "ˈ" : ""
            let final = i + 1 >= t.count || !Self.isLetter(t[i + 1])
            if ch == "ח" && final && v == "a" {
                out += mark + "aχ"  // (a furtive patah: the vowel before the χ)
                continue
            }
            if c != "∅" { out += c }
            out += mark + v
        }
        return out
    }

    // As train/read_label.py: padding, space, the letters, punctuation; then unknown, girl, boy
    private static let alphabet: [Unicode.Scalar] = {
        var a: [Unicode.Scalar] = ["\u{0}", " "]
        for v in 0x05D0...0x05EA { a.append(Unicode.Scalar(UInt32(v))!) }
        a += Array(".,!?:;-'\"()".unicodeScalars)
        return a
    }()
    private static let index: [Unicode.Scalar: Int] = {
        var m: [Unicode.Scalar: Int] = [:]
        for (i, c) in alphabet.enumerated() where m[c] == nil { m[c] = i }
        return m
    }()
    private static let unk = alphabet.count
    private static let girlId = alphabet.count + 1
    private static let boyId = alphabet.count + 2
    private static let nothing = 0

    /** As train/read_tags.clean: one kind of geresh and gershayim, decomposed, single spaces. */
    static func clean(_ text: String) -> String {
        let g = KRegex("[״\"]").replace(KRegex("[׳'`´]").replace(text, "'"), "\"")
        return KRegex("\\s+").split(g.decomposedStringWithCanonicalMapping).filter { !$0.isEmpty }.joined(separator: " ")
    }
}

/**
 * Puki's English reading model (train/en_read_train.py, 2.7M parameters): English to the phonemes the voice was
 * trained on (espeak's, en-us). Every word Puki says is in the table; the model reads the rest, like the child's
 * name. `dir` holds reader_int8.onnx, symbols.json and words.tsv.
 */
final class EnglishBeak {
    private let session: ORTSession
    private let letters: [Character]
    private let maxLetters: Int
    private let symbols: [String]
    private let table: [String: String]
    private let lock = NSLock()

    init(dir: URL, threads: Int = 2) throws {
        session = try Ort.session(dir.appendingPathComponent("reader_int8.onnx"), threads: threads)
        guard let s = try JSONSerialization.jsonObject(with: Data(contentsOf: dir.appendingPathComponent("symbols.json"))) as? [String: Any],
              let l = s["letters"] as? String, let m = s["max_letters"] as? Int, let sy = s["symbols"] as? [String] else {
            throw Ort.Failure(what: "symbols.json")
        }
        letters = Array(l); maxLetters = m; symbols = sy
        var t: [String: String] = [:]
        for line in try String(contentsOf: dir.appendingPathComponent("words.tsv"), encoding: .utf8).components(separatedBy: "\n") {
            if let tab = line.firstIndex(of: "\t"), tab > line.startIndex {
                t[String(line[..<tab])] = String(line[line.index(after: tab)...])
            }
        }
        table = t
    }

    private static let word = KRegex("^[A-Za-z]+(?:'[A-Za-z]+)?$")
    /** What the voice reads as a pause or a tone (espeak keeps these). */
    private static let punct = ".,!?;:-"
    private static let token = KRegex("[A-Za-z]+(?:'[A-Za-z]+)?|[^\\sA-Za-z]+|\\s+")
    private static let numberWords = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"]

    /** The numbers Puki says as words; anything bigger is read digit by digit. */
    static func spellNumbers(_ text: String) -> String {
        KRegex("\\d+").replace(text) { g in
            let v = g[0] ?? ""
            if let n = Int(v), n < numberWords.count { return numberWords[n] }
            return v.compactMap { $0.wholeNumberValue.map { numberWords[$0] } }.joined(separator: " ")
        }
    }

    /** `text` read aloud as IPA. Hebrew letters (a name written in Hebrew) go to `hebrew`, or are left out. */
    func phonemize(_ text: String, hebrew: (String) -> String = { _ in "" }) -> String {
        let plain = KRegex("\\p{Mn}+").replace(text.decomposedStringWithCanonicalMapping, "")
        var out = ""
        for tok in Self.token.findAll(Self.spellNumbers(plain)) {
            if tok.trimmed().isEmpty { out += " "; continue }
            if Self.word.matches(tok) {
                let w = tok.lowercased()
                out += table[w] ?? read(w)
            } else if tok.unicodeScalars.contains(where: { $0.value >= 0x05D0 && $0.value <= 0x05EA }) {
                out += hebrew(String(tok.filter { c in c.unicodeScalars.allSatisfy { $0.value >= 0x05D0 && $0.value <= 0x05EA } || c == "'" || c == "׳" }))
                out += tok.filter { Self.punct.contains($0) }
            } else {
                out += tok.filter { Self.punct.contains($0) || $0.isNumber }
            }
        }
        return KRegex("\\s+").replace(out, " ").trimmed()
    }

    /** One word the table doesn't have, read by the model. */
    func read(_ word: String) -> String {
        let w = Array(word.filter { letters.contains($0) }.prefix(maxLetters))
        if w.isEmpty { return "" }
        lock.lock(); defer { lock.unlock() }
        var ids = [Int64](repeating: 1, count: w.count + 2)  // (1 marks the word's edges)
        for (i, c) in w.enumerated() { ids[i + 1] = Int64((letters.firstIndex(of: c) ?? 0) + 2) }
        guard let r = try? session.run(withInputs: ["ids": try Ort.int64s(ids, [1, ids.count])], outputNames: ["logp"], runOptions: nil),
              let lp = r["logp"], let flat = try? Ort.floatsOf(lp), let shape = try? Ort.shapeOf(lp), let k = shape.last, k > 0 else { return "" }
        var sb = ""
        var prev = 0
        for slot in 0..<(flat.count / k) {
            var best = 0
            for j in 0..<k where flat[slot * k + j] > flat[slot * k + best] { best = j }
            if best != prev && best != 0 && best < symbols.count { sb += symbols[best] }
            prev = best
        }
        return sb
    }
}
#endif
