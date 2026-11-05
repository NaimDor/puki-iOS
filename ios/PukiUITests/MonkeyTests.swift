import XCTest

/**
 * A toddler let loose: random taps, drags and holds all over the game for a few minutes, mostly on the things
 * that answer a touch. The game must never close, and never be stuck somewhere with no way back.
 */
final class MonkeyTests: PukiUITestCase {
    func testToddlerMonkey_Hebrew() { monkey("he", minutes: 4) }
    func testToddlerMonkey_English() { monkey("en", minutes: 3) }

    private func monkey(_ lang: String, minutes: Double) {
        g.launch(lang: lang)
        let end = Date().addingTimeInterval(minutes * 60)
        var visited: Set<String> = []
        var since = Date()
        var lastRoute = g.route
        while Date() < end {
            XCTAssertEqual(g.app.state, .runningForeground, "the game closed")
            if g.app.state != .runningForeground { return }
            let all = g.nodes()
            let route = all.first { $0.id == "route" }?.value
            if let route { visited.insert(route) }
            if route != lastRoute { lastRoute = route; since = Date() }
            let shown = g.visible(all).filter { $0.id != "ic_ui_settings" && $0.id != "route" && $0.id != "touch" }
            // A while on one screen: home, like a parent would (and check the way back works)
            if Date().timeIntervalSince(since) > 40, route != "hub" {
                if let back = shown.first(where: { $0.id == "back" }) ?? shown.first(where: { $0.id == "home" }) {
                    g.tap(back); g.pause(0.8); since = Date(); continue
                }
            }
            switch Int.random(in: 0..<10) {
            case 0...5:
                if let n = shown.randomElement() { g.tap(n) }
            case 6:
                let s = g.screen
                g.tap(CGPoint(x: CGFloat.random(in: s.minX + 20..<s.maxX - 20), y: CGFloat.random(in: s.minY + 20..<s.maxY - 20)))
            case 7, 8:
                let s = g.screen
                let a = shown.randomElement().map { CGPoint(x: $0.frame.midX, y: $0.frame.midY) }
                    ?? CGPoint(x: s.midX, y: s.midY)
                g.drag(a, CGPoint(x: CGFloat.random(in: s.minX + 20..<s.maxX - 20), y: CGFloat.random(in: s.minY + 20..<s.maxY - 20)), holdFirst: 0.05)
            default:
                if let n = shown.randomElement() { g.hold(n, Double.random(in: 0.3...1.5)) }
            }
            g.pause(0.25)
        }
        XCTAssertEqual(g.app.state, .runningForeground, "the game closed")
        print("monkey (\(lang)) visited: \(visited.sorted().joined(separator: ", "))")
        XCTAssertGreaterThan(visited.count, 3, "the monkey never got past a few screens")
    }
}
