import XCTest

/**
 * Drives the game the way a child does: finds things by the names the app gives them (TestID.swift) and touches
 * them where they are on screen, with real taps, drags and holds. One snapshot of the screen per look, so it
 * stays quick on a screen full of animation.
 */
final class Driver {
    let app = XCUIApplication()
    let test: XCTestCase
    /** The screen, as the last look saw it. */
    private(set) var screen = CGRect(x: 0, y: 0, width: 10_000, height: 10_000)

    init(_ test: XCTestCase) { self.test = test }

    struct Node {
        let id: String
        let frame: CGRect
        let value: String
        let label: String
    }

    // ---------- Starting ----------

    /**
     * Starts the game. `route` opens that screen straight away; `fresh` starts as if just installed; `uiTest`
     * skips the welcome screen (the language is `lang`). Extra launch arguments go in `extra`.
     */
    func launch(route: String? = nil, lang: String = "he", fresh: Bool = true, uiTest: Bool = true, extra: [String] = []) {
        app.terminate()
        var args = ["-PukiFresh", fresh ? "1" : "0"]
        if uiTest { args += ["-PukiUITest", "1", "-PukiLang", lang] }
        if let route { args += ["-PukiScreenshot", route] }
        app.launchArguments = args + extra
        XCUIDevice.shared.orientation = .landscapeLeft
        app.launch()
        XCUIDevice.shared.orientation = .landscapeLeft
        if uiTest || !fresh {
            XCTAssertTrue(waitFor { self.route != nil }, "the game didn't start")
        }
        if let route { XCTAssertTrue(waitRoute(route), "didn't open \(route) (on \(self.route ?? "nothing"))") }
    }

    // ---------- Looking ----------

    /** Everything on screen that has a name. */
    func nodes() -> [Node] {
        guard let root = try? app.snapshot() else { return [] }
        screen = root.frame
        var out: [Node] = []
        var stack: [XCUIElementSnapshot] = [root]
        while let s = stack.popLast() {
            if !s.identifier.isEmpty {
                out.append(Node(id: s.identifier, frame: s.frame, value: (s.value as? String) ?? "", label: s.label))
            }
            stack.append(contentsOf: s.children)
        }
        return out
    }

    /** The named things big enough and inside the screen (not popped away or scrolled off). */
    func visible(_ all: [Node]? = nil) -> [Node] {
        let list = all ?? nodes()
        let screen = self.screen
        return list.filter { n in
            n.frame.width >= 12 && n.frame.height >= 12 && screen.contains(CGPoint(x: n.frame.midX, y: n.frame.midY))
        }
    }

    func node(_ id: String, in all: [Node]? = nil) -> Node? { (all ?? nodes()).first { $0.id == id } }
    func nodes(prefix: String, in all: [Node]? = nil) -> [Node] { visible(all).filter { $0.id.hasPrefix(prefix) } }

    /** The screen that's up ("hub", "animals", "myday/bath" ...), nil before the game shows. */
    var route: String? { node("route")?.value }
    /** "he" or "en". */
    var language: String? { node("route")?.label }
    /** The round dots: done/total. */
    var rounds: (done: Int, total: Int)? {
        guard let v = node("rounds")?.value.split(separator: "/"), v.count == 2, let a = Int(v[0]), let b = Int(v[1]) else { return nil }
        return (a, b)
    }
    var stars: Int? { node("stars").flatMap { Int($0.value) } }

    @discardableResult
    func waitFor(_ seconds: TimeInterval = 10, _ condition: () -> Bool) -> Bool {
        let end = Date().addingTimeInterval(seconds)
        while Date() < end {
            if condition() { return true }
            pause(0.3)
        }
        return condition()
    }

    func waitRoute(_ r: String, _ seconds: TimeInterval = 10) -> Bool { waitFor(seconds) { self.route == r } }
    /** `id` on screen within `seconds`; `anySize` for the invisible markers (route, timeLimit). */
    func waitNode(_ id: String, _ seconds: TimeInterval = 10, anySize: Bool = false) -> Node? {
        var found: Node?
        waitFor(seconds) { found = (anySize ? self.nodes() : self.visible()).first { $0.id == id }; return found != nil }
        return found
    }

    func pause(_ seconds: TimeInterval) { Thread.sleep(forTimeInterval: seconds) }

    // ---------- Touching ----------

    private func point(_ p: CGPoint) -> XCUICoordinate {
        app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: p.x, dy: p.y))
    }

    func tap(_ n: Node) { tap(CGPoint(x: n.frame.midX, y: n.frame.midY)) }
    func tap(_ p: CGPoint) { point(p).tap() }

    func tap(_ id: String, _ seconds: TimeInterval = 10, file: StaticString = #filePath, line: UInt = #line) {
        guard let n = waitNode(id, seconds) else {
            XCTFail("no \(id) on \(route ?? "?")", file: file, line: line)
            return
        }
        tap(n)
    }

    /** A finger pressed on `from`, moved to `to` and lifted. */
    func drag(_ from: CGPoint, _ to: CGPoint, holdFirst: TimeInterval = 0.15) {
        point(from).press(forDuration: holdFirst, thenDragTo: point(to), withVelocity: XCUIGestureVelocity(rawValue: 600), thenHoldForDuration: 0.15)
    }
    func drag(_ a: Node, to b: Node) { drag(CGPoint(x: a.frame.midX, y: a.frame.midY), CGPoint(x: b.frame.midX, y: b.frame.midY)) }

    func hold(_ n: Node, _ seconds: TimeInterval) { point(CGPoint(x: n.frame.midX, y: n.frame.midY)).press(forDuration: seconds) }

    /** Rubbing or stirring: a stroke across `n`, on one of a few lines through it. */
    func scrub(_ n: Node) {
        let f = n.frame
        let ys: [CGFloat] = [0.3, 0.42, 0.5, 0.58, 0.7]
        let y = f.minY + f.height * ys.randomElement()!
        let ltr = Bool.random()
        let a = CGPoint(x: f.minX + f.width * (ltr ? 0.12 : 0.88), y: y)
        let b = CGPoint(x: f.minX + f.width * (ltr ? 0.88 : 0.12), y: f.minY + f.height * ys.randomElement()!)
        point(a).press(forDuration: 0.05, thenDragTo: point(b), withVelocity: XCUIGestureVelocity(rawValue: 900), thenHoldForDuration: 0)
    }

    // ---------- Playing ----------

    enum Style {
        /** Try what's there: choices, tools, pieces (tapped and dragged to each place), rubbing. */
        case tryEverything
        /** Music: start the tune, then play the instrument it wants next (a debug build says which). */
        case followTheTune
        /** Dance and Freeze: start it and watch. */
        case startAndWatch
    }

    /**
     * Plays the game that's up until its end card shows (true), or `seconds` pass (false). Wrong answers are
     * harmless in Puki (no failing states), so trying is a fair way to play: it's what a toddler does.
     */
    @discardableResult
    func playToEnd(_ style: Style = .tryEverything, seconds: TimeInterval = 300) -> Bool {
        let end = Date().addingTimeInterval(seconds)
        let route = self.route
        while Date() < end {
            let all = nodes()
            if visible(all).contains(where: { $0.id == "continue" }) { return true }
            if routeIn(all) != route { XCTFail("left \(route ?? "?") for \(routeIn(all) ?? "nothing") while playing"); return false }
            let shown = visible(all)
            switch style {
            case .followTheTune:
                if let next = shown.first(where: { $0.id == "choice" && $0.value == "next" }) { tap(next); pause(0.5); continue }
                if let play = shown.first(where: { $0.id == "play" }) { tap(play) }
                pause(0.5)
                continue
            case .startAndWatch:
                if let play = shown.first(where: { $0.id == "play" }) { tap(play) }
                pause(2)
                continue
            case .tryEverything:
                break
            }
            var moves: [() -> Void] = []
            for n in shown where ["choice", "tool", "play"].contains(n.id) { moves.append { self.tap(n) } }
            let pieces = shown.filter { $0.id.hasPrefix("piece.") }
            let zones = shown.filter { $0.id.hasPrefix("zone.") }
            for p in pieces {
                moves.append { self.tap(p) }
                for z in zones { moves.append { self.drag(p, to: z) } }
            }
            // Rubbing and stirring take many strokes
            for s in shown where s.id == "scrub" { for _ in 0..<8 { moves.append { self.scrub(s) } } }
            if moves.isEmpty { pause(0.8); continue }
            moves.randomElement()!()
            pause(0.6)
        }
        return false
    }

    private func routeIn(_ all: [Node]) -> String? { all.first { $0.id == "route" }?.value }

    // ---------- Grown-ups ----------

    /** The parent check's numbers, as the app writes them. */
    static let words: [String: Int] = {
        let he = ["אחת-עשרה", "שתים-עשרה", "שלוש-עשרה", "ארבע-עשרה", "חמש-עשרה", "שש-עשרה", "שבע-עשרה", "שמונה-עשרה", "תשע-עשרה"]
        let en = ["eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"]
        var m: [String: Int] = [:]
        for i in 0..<9 { m[he[i]] = 11 + i; m[en[i]] = 11 + i }
        return m
    }()

    /** The number the parent check asks for now. */
    func parentTarget() -> Int? {
        guard let t = waitNode("parentTarget", 5) else { return nil }
        return Driver.words[t.value]
    }

    /** Through the parent check (after the hold): the right number, twice. */
    func passParentCheck(file: StaticString = #filePath, line: UInt = #line) {
        for _ in 0..<2 {
            guard let n = parentTarget() else { XCTFail("no parent check", file: file, line: line); return }
            tap("parentNumber.\(n)")
            pause(0.8)
        }
    }

    /** A picture of the screen, kept with the test's results. */
    func shot(_ name: String) {
        let a = XCTAttachment(screenshot: app.screenshot())
        a.name = name
        a.lifetime = .keepAlways
        test.add(a)
    }
}
