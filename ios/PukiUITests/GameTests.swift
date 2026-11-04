import XCTest

/**
 * Every game, played from its first round to its end card, then on with Continue. In Hebrew, and the drag games
 * in English too.
 */
final class GameTests: PukiUITestCase {
    /** My Day's rooms in the order a day goes: Continue leads on to the next one, and after bedtime home. */
    static let day = ["myday/dress", "myday/brush", "myday/table", "myday/tidy", "myday/kitchen", "myday/garage", "myday/bath", "myday/bedtime"]

    private func play(_ route: String, _ style: Driver.Style = .tryEverything, lang: String = "he", seconds: TimeInterval = 360) {
        g.launch(route: route, lang: lang)
        g.pause(1.5)
        let finished = g.playToEnd(style, seconds: seconds)
        g.shot("\(lang) \(route) end")
        XCTAssertTrue(finished, "\(route) didn't reach its end card in \(Int(seconds)) s (rounds \(String(describing: g.rounds)))")
        if !finished { return }
        if let stars = g.stars { XCTAssertGreaterThan(stars, 0, "\(route) ended without a star") }
        // Continue: the same game again from the start, or the next room of the day
        g.pause(2.5)  // (the end card's buttons wake up after the stars land)
        g.tap("continue")
        let next: String
        // (After bedtime it goes back where the day came from: the house, or here the map, since the test opened bedtime
        // straight from it)
        if let i = Self.day.firstIndex(of: route) { next = i + 1 < Self.day.count ? Self.day[i + 1] : "hub" } else { next = route }
        XCTAssertTrue(g.waitFor { self.g.route == next && self.g.node("continue") == nil }, "Continue didn't lead to a new \(next) (on \(g.route ?? "nothing"))")
    }

    func testAnimals() { play("animals") }
    func testAnimals_English() { play("animals", lang: "en") }
    func testColors() { play("colors") }
    func testShapes() { play("shapes") }
    func testShapes_English() { play("shapes", lang: "en") }
    func testCounting() { play("counting") }
    func testWords() { play("words") }
    func testWords_English() { play("words", lang: "en") }
    func testPeekaboo() { play("peekaboo") }
    func testMusic() { play("music", .followTheTune) }
    func testDance() { play("dance", .startAndWatch) }

    func testDress() { play("myday/dress") }
    func testBrushTeeth() { play("myday/brush") }
    func testTable() { play("myday/table") }
    func testTidy() { play("myday/tidy") }
    func testTidy_English() { play("myday/tidy", lang: "en") }
    func testCake() { play("myday/kitchen") }
    func testCarWash() { play("myday/garage") }
    func testBath() { play("myday/bath") }
    func testBedtime() { play("myday/bedtime") }

    /** The end card's other way out: home. */
    func testEndCardGoesHome() {
        g.launch(route: "colors")
        XCTAssertTrue(g.playToEnd(), "colors didn't end")
        g.pause(2.5)
        g.tap("home")
        XCTAssertTrue(g.waitRoute("hub"), "Home didn't lead to the map")
    }
}
