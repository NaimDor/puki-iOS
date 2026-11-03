import Foundation

/**
 * BlueTTS's text handling, ported from blue_onnx (Python) for what Puki says: short Hebrew lines with punctuation,
 * no digits. Each step keeps the Python name, so the two can be compared side by side.
 */
enum VoiceText {
    private static let heb = "\\u0590-\\u05FF"

    // ---- text_norm.prepare_text_for_synthesis, the Hebrew-sentence part ----

    static func prepareTextForSynthesis(_ text: String) -> String {
        var t = normalizeCommonText(text)
        t = KRegex("(?<=[\(heb)])[\"״](?=[\(heb)])").replace(t, "")           // strip_hebrew_abbreviation_quotes
        t = KRegex("(?<=[גצז])'(?=[\(heb)])").replace(t, "׳")                // normalize_phonetic_geresh
        t = expandDialogueQuotes(t)
        t = KRegex("(?<=[\(heb)])[-–—‑]+(?=[\(heb)])").replace(t, "")         // strip_hebrew_inword_hyphens
        return stripSilentSeparatorTokens(t)
    }

    private static func normalizeCommonText(_ text: String) -> String {
        var t = KRegex("(^|\\s)#{1,6}\\s*").replace(text, "$1")
        t = KRegex("\\s*[(\\[{]\\s*").replace(t, ", ")
        t = KRegex("\\s*[)\\]}]\\s*").replace(t, ", ")
        t = t.replacingOccurrences(of: "…", with: ",")
        t = KRegex("(?<!\\d)\\.{2,}(?!\\d)").replace(t, ",")
        t = KRegex("!+").replace(t, "!")
        t = KRegex("\\?+").replace(t, "?")
        t = KRegex(",(?:\\s*,)+").replace(t, ",")
        t = KRegex("\\s*[,;:]+\\s*(?=[.!?])").replace(t, "")
        return KRegex("(?<=[.!?])\\s*[,;:]+\\s*").replace(t, " ")
    }

    private static func expandDialogueQuotes(_ text: String) -> String {
        var t = KRegex("(?<=\\S)\\s*[\"“„”]\\s*$").replace(text, ".")
        t = KRegex("\\s*:?\\s*[\"“„”]\\s*(?=\\S)").replace(t, ", ")
        return KRegex("(?<=\\S)\\s*[\"“„”]").replace(t, ", ")
    }

    private static func stripSilentSeparatorTokens(_ text: String) -> String {
        var t = KRegex("(?<=[\(heb)])[-–—‑]+(?=[A-Za-z0-9])").replace(text, " ")
        t = KRegex("(?<=[A-Za-z0-9])[-–—‑]+(?=[\(heb)])").replace(t, " ")
        t = KRegex("(?<![A-Za-z])\\s*[-–—‑]+\\s*(?![A-Za-z])").replace(t, " ")
        t = KRegex("(?<!\\d)\\s*:+\\s*(?!\\d)").replace(t, " ")
        return KRegex("\\s+").replace(t, " ").trimmed()
    }

    // ---- blue_onnx helpers ----

    private static let langTag = KRegex("</?[\\p{L}\\p{N}_]+>")

    static func stripLangTags(_ s: String) -> String { KRegex("\\s+").replace(langTag.replace(s, ""), " ").trimmed() }

    /** chunk_text: sentences packed into chunks of at most `maxLen` characters. */
    static func chunkText(_ text: String, maxLen: Int = 300) -> [String] {
        var chunks: [String] = []
        for paragraph in KRegex("\\n\\s*\\n+").split(text.trimmed()).map({ $0.trimmed() }).filter({ !$0.isEmpty }) {
            var current = ""
            for sentence in KRegex("(?<=[.!?])\\s+").split(paragraph) {
                if current.ktLength + sentence.ktLength + 1 <= maxLen {
                    current += (current.isEmpty ? "" : " ") + sentence
                } else {
                    if !current.isEmpty { chunks.append(current.trimmed()) }
                    current = sentence
                }
            }
            if !current.isEmpty { chunks.append(current.trimmed()) }
        }
        return chunks
    }

    // ---- UnicodeProcessor: phonemes to token ids ----

    private static let replacements: [(String, String)] = [
        ("–", "-"), ("‑", "-"), ("—", "-"), ("_", " "), ("“", "\""), ("”", "\""),
        ("‘", "'"), ("’", "'"), ("´", "'"), ("`", "'"), ("[", " "), ("]", " "),
        ("|", " "), ("/", " "), ("#", " "), ("→", " "), ("←", " "),
    ]

    /** UnicodeProcessor._preprocess_text, then _encode with `charToId` (by code point; unknowns become `padId`). */
    static func encode(_ phonemes: String, charToId: [UInt32: Int], padId: Int) -> [Int] {
        var t = stripLangTags(phonemes).decomposedStringWithCompatibilityMapping
        for (k, v) in replacements { t = t.replacingOccurrences(of: k, with: v) }
        t = KRegex("[♥☆♡©\\\\]").replace(t, "")
        t = t.replacingOccurrences(of: "@", with: " at ").replacingOccurrences(of: "e.g.,", with: "for example, ").replacingOccurrences(of: "i.e.,", with: "that is, ")
        for p in [",", ".", "!", "?", ";", ":", "'"] { t = t.replacingOccurrences(of: " \(p)", with: p) }
        while t.contains("\"\"") { t = t.replacingOccurrences(of: "\"\"", with: "\"") }
        while t.contains("''") { t = t.replacingOccurrences(of: "''", with: "'") }
        while t.contains("``") { t = t.replacingOccurrences(of: "``", with: "`") }
        t = KRegex("\\s+").replace(t, " ").trimmed()
        if !KRegex("[.!?;:,'\"')\\]}…。」』】〉》›»]$").containsMatchIn(t) { t += "." }
        return t.unicodeScalars.map { charToId[$0.value] ?? padId }
    }
}
