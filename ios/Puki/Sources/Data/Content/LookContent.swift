import Foundation

/** What the drawing studio knows about a drawing, for Puki's eyes. */
struct LookFacts {
    /** "blank", a coloring page's id ("fish", "flower", "house", "car") or "trace". */
    var page: String
    /** The letter on a page to trace (Hebrew, English). */
    var letter: (String, String)? = nil
    /** The paints used, the most used first (LookContent.colors ids). */
    var colors: [String]
    /** The stickers and how many of each (LookContent.stamps ids). */
    var stamps: [(String, Int)]
    /** How much of the page is colored: 0 a little, 1 half, 2 almost all. */
    var cover: Int
    /** The kind of strokes (LookContent.strokes), or nil. */
    var strokes: Int?
}

struct LookInput { let he, en: String; let presentHe, presentEn, absentHe, absentEn: [String] }

/**
 * Puki's eyes on a drawing, said as the brain was trained to read it, and the check its line must pass:
 * it names something that's there, and nothing that isn't.
 */
enum LookContent {
    /** id to (Hebrew forms, English). */
    static let colors = OrderedMap<String, ([String], String)>([
        ("red", (["אדום", "אדומה", "אדומים", "אדומות"], "red")), ("blue", (["כחול", "כחולה", "כחולים", "כחולות"], "blue")),
        ("yellow", (["צהוב", "צהובה", "צהובים", "צהובות"], "yellow")), ("green", (["ירוק", "ירוקה", "ירוקים", "ירוקות"], "green")),
        ("purple", (["סגול", "סגולה", "סגולים", "סגולות"], "purple")), ("orange", (["כתום", "כתומה", "כתומים", "כתומות"], "orange")),
        ("pink", (["ורוד", "ורודה", "ורודים", "ורודות"], "pink")), ("brown", (["חום", "חומה", "חומים", "חומות"], "brown")),
        ("black", (["שחור", "שחורה", "שחורים", "שחורות"], "black")),
    ])

    struct Stamp { let one, many: String; let feminine: Bool; let oneEn, manyEn: String }
    /** In the drawing studio's order (stampIcons). */
    static let stamps = OrderedMap<String, Stamp>([
        ("star", Stamp(one: "כוכב", many: "כוכבים", feminine: false, oneEn: "star", manyEn: "stars")), ("heart", Stamp(one: "לב", many: "לבבות", feminine: false, oneEn: "heart", manyEn: "hearts")),
        ("flower", Stamp(one: "פרח", many: "פרחים", feminine: false, oneEn: "flower", manyEn: "flowers")), ("butterfly", Stamp(one: "פרפר", many: "פרפרים", feminine: false, oneEn: "butterfly", manyEn: "butterflies")),
        ("ladybug", Stamp(one: "פרת משה רבנו", many: "פרות משה רבנו", feminine: true, oneEn: "ladybug", manyEn: "ladybugs")), ("apple", Stamp(one: "תפוח", many: "תפוחים", feminine: false, oneEn: "apple", manyEn: "apples")),
        ("rainbow", Stamp(one: "קשת", many: "קשתות", feminine: true, oneEn: "rainbow", manyEn: "rainbows")), ("sun", Stamp(one: "שמש", many: "שמשות", feminine: true, oneEn: "sun", manyEn: "suns")),
        ("moon", Stamp(one: "ירח", many: "ירחים", feminine: false, oneEn: "moon", manyEn: "moons")), ("unicorn", Stamp(one: "חד-קרן", many: "חדי-קרן", feminine: false, oneEn: "unicorn", manyEn: "unicorns")),
        ("bird", Stamp(one: "ציפור", many: "ציפורים", feminine: true, oneEn: "bird", manyEn: "birds")), ("rocket", Stamp(one: "טיל", many: "טילים", feminine: false, oneEn: "rocket", manyEn: "rockets")),
    ])

    /** id to (Hebrew, English, the things on it a line may name: Hebrew, English). */
    private static let pages = OrderedMap<String, (String, String, [String])>([
        ("blank", ("דף ריק", "blank paper", [])),
        ("fish", ("דף צביעה של דג", "a coloring page with a fish", ["דג", "fish"])),
        ("flower", ("דף צביעה של פרח", "a coloring page with a flower", ["פרח", "flower"])),
        ("house", ("דף צביעה של בית", "a coloring page with a house", ["בית", "house"])),
        ("car", ("דף צביעה של מכונית", "a coloring page with a car", ["מכונית", "car"])),
    ])

    /** (Hebrew, English, the words that name it). */
    static let cover: [(String, String, [String])] = [
        ("רק קצת מהדף צבוע", "just a little of the page is colored", ["פינה", "קצת", "corner", "a little", "little bit"]),
        ("חצי מהדף צבוע", "half the page is colored", ["חצי", "half"]),
        ("כמעט כל הדף צבוע", "almost the whole page is colored", ["כל הדף", "הכל", "מלא", "whole", "full", "everywhere", "all over", "all the page"]),
    ]
    static let strokes: [(String, String, [String])] = [
        ("הרבה נקודות קטנות", "lots of little dots", ["נקודות", "נקודה", "dots", "dot"]),
        ("קווים ארוכים", "long lines", ["קווים", "קו", "lines", "line"]),
        ("הרבה קשקושים עגולים", "lots of round scribbles", ["קשקושים", "קשקוש", "scribbles", "scribble"]),
        ("קווים קצרים", "short lines", ["קווים", "קו", "lines", "line"]),
    ]
    private static let heCount: [Int: (String, String)] = [2: ("שני", "שתי"), 3: ("שלושה", "שלוש")]
    private static let enCount: [Int: String] = [2: "two", 3: "three"]

    private static func hebrew(_ w: String) -> Bool { w.unicodeScalars.contains { $0.value >= 0x05D0 && $0.value <= 0x05EA } }

    static func describe(_ f: LookFacts) -> LookInput {
        let (pageHe, pageEn, pageWords): (String, String, [String]) = (f.page == "trace" && f.letter != nil)
            ? ("דף עם האות \(f.letter!.0) לצביעה", "a page with the letter \(f.letter!.1) to trace", [])
            : (pages[f.page] ?? pages["blank"]!)
        func amount(_ i: Int) -> (String, String) {
            i == 0 && f.colors.count > 1 ? ("הרבה ", "lots of ") : i > 0 ? ("קצת ", "a little ") : ("", "")
        }
        let colorsHe = f.colors.enumerated().map { amount($0.offset).0 + colors[$0.element]!.0[0] }.joined(separator: ", ")
        let colorsEn = f.colors.enumerated().map { amount($0.offset).1 + colors[$0.element]!.1 }.joined(separator: ", ")
        func stampHe(_ s: String, _ k: Int) -> String {
            let st = stamps[s]!
            if k == 1 { return st.one }
            if let c = heCount[k] { return "\(st.feminine ? c.1 : c.0) \(st.many)" }
            return "הרבה \(st.many)"
        }
        func stampEn(_ s: String, _ k: Int) -> String {
            let st = stamps[s]!
            if k == 1 { return "a \(st.oneEn)" }
            if let c = enCount[k] { return "\(c) \(st.manyEn)" }
            return "lots of \(st.manyEn)"
        }
        let stampsHe = f.stamps.map { stampHe($0.0, $0.1) }.joined(separator: ", ").ifEmpty("אין")
        let stampsEn = f.stamps.map { stampEn($0.0, $0.1) }.joined(separator: ", ").ifEmpty("none")
        let cov = cover[f.cover]
        let str = f.strokes.map { strokes[$0] }
        let he = "הציור: \(pageHe). צבעים: \(colorsHe). מדבקות: \(stampsHe). \(cov.0)." + (str.map { " \($0.0)." } ?? "")
        let en = "The drawing: \(pageEn). Colors: \(colorsEn). Stickers: \(stampsEn). \(cov.1.capitalizedFirst)." +
            (str.map { " \($0.1.capitalizedFirst)." } ?? "")
        let stamped = f.stamps.map(\.0)
        let strokeWords = str?.2 ?? []
        let otherStrokes = strokes.enumerated().filter { $0.offset != f.strokes }.flatMap { $0.element.2 }.filter { !strokeWords.contains($0) }
        let presentHe = f.colors.flatMap { colors[$0]!.0 } + stamped.flatMap { [stamps[$0]!.one, stamps[$0]!.many] } +
            pageWords.take(1) + (cov.2 + strokeWords).filter(hebrew)
        let presentEn = f.colors.map { colors[$0]!.1 } + stamped.flatMap { [stamps[$0]!.oneEn, stamps[$0]!.manyEn] } +
            pageWords.drop(1) + (cov.2 + strokeWords).filter { !hebrew($0) }
        let otherPages = pages.entries.filter { !$0.1.2.isEmpty && $0.0 != f.page }
        let absentHe = colors.entries.filter { !f.colors.contains($0.0) }.flatMap { $0.1.0 } +
            stamps.entries.filter { !stamped.contains($0.0) }.flatMap { [$0.1.one, $0.1.many] } +
            otherPages.map { $0.1.2[0] } + otherStrokes.filter(hebrew)
        let absentEn = colors.entries.filter { !f.colors.contains($0.0) }.map { $0.1.1 } +
            stamps.entries.filter { !stamped.contains($0.0) }.flatMap { [$0.1.oneEn, $0.1.manyEn] } +
            otherPages.map { $0.1.2[1] } + otherStrokes.filter { !hebrew($0) }
        return LookInput(he: he, en: en, presentHe: presentHe, presentEn: presentEn,
                         absentHe: absentHe.filter { !presentHe.contains($0) }, absentEn: absentEn.filter { !presentEn.contains($0) })
    }

    private static let word = KRegex("[\\p{L}\\p{N}_'-]+")

    /** A word with up to two Hebrew prefix letters taken off (וציירת -> ציירת), as games._forms. */
    private static func forms(_ w: String) -> Set<String> {
        var out: Set<String> = [w]
        for k in 1...2 where w.count - k >= 3 && w.take(k).allSatisfy({ "והבלמש".contains($0) }) { out.insert(w.drop(k)) }
        return out
    }

    /** Whether `line` names something in `present` and nothing in `absent` (games.look_ok). */
    static func ok(_ line: String, present: [String], absent: [String]) -> Bool {
        let text = " " + line.lowercased() + " "
        let words = Set(word.findAll(text).flatMap { forms($0) })
        func has(_ w: String) -> Bool { let l = w.lowercased(); return words.contains(l) || (l.contains(" ") && text.contains(l)) }
        return present.contains(where: has) && !absent.contains(where: has)
    }
}
