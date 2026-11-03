import Foundation

/**
 * Which of a round's answers the child said out loud ("אדום!" instead of tapping the red balloon). The brain only
 * hears the words (POST /hear); the game decides here.
 */
enum SpokenAnswer {
    /**
     * The index in `options` of the answer said in `heard`, or -1. Each option is the words that count for it.
     * When several answers are said, the last one wins. A near miss counts only when clearly closest to one answer.
     */
    static func pick(_ heard: String, _ options: [[String]]) -> Int {
        let words = Persona.words(heard)
        if words.isEmpty || options.isEmpty { return -1 }
        let goals = options.map { forms in forms.map { Persona.words($0).joined() }.filter { !$0.isEmpty } }
        for j in words.indices.reversed() {
            var here = [words[j]]
            if j + 1 < words.count { here.append(words[j] + words[j + 1]) }
            let exact = goals.indices.filter { i in goals[i].contains { g in here.contains(g) } }
            if exact.count == 1 { return exact[0] }
            let said = goals.indices.filter { i in goals[i].contains { g in here.contains { sameWord($0, g) } } }
            if said.count == 1 { return said[0] }
            if said.count > 1 { return -1 }  // the same word fits two answers: better not guess
        }
        return nearMiss(words, goals)
    }

    private static let prefixes = "הובלשמכ"

    /** `w` is `goal`, perhaps with a prefix or two and a feminine "ה" or a plural "s". */
    private static func sameWord(_ w: String, _ goal: String) -> Bool {
        var forms = [w]
        if w.hasSuffix("ה") && !goal.hasSuffix("ה") { forms.append(String(w.dropLast())) }
        if w.hasSuffix("s") && !goal.hasSuffix("s") { forms.append(String(w.dropLast())) }
        return forms.contains { f in
            f == goal || ((1...2).contains(f.count - goal.count) && f.hasSuffix(goal) && f.dropLast(goal.count).allSatisfy { prefixes.contains($0) })
        }
    }

    private static func nearMiss(_ words: [String], _ goals: [[String]]) -> Int {
        let scores: [Double] = goals.map { forms in
            words.map { w in forms.map { g in g.count < 3 ? 0 : Persona.similarity(w, g) }.max() ?? 0 }.max() ?? 0
        }
        guard let best = scores.indices.max(by: { scores[$0] < scores[$1] }) else { return -1 }
        let second = scores.enumerated().filter { $0.offset != best }.map(\.element).max() ?? 0
        return scores[best] >= 0.75 && scores[best] - second >= 0.15 ? best : -1
    }
}
