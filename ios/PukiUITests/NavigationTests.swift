import XCTest

/** Every island, room, door and corner opens, shows its screen and leads back, in both languages. */
final class NavigationTests: PukiUITestCase {
    /** Each island on the map and the screen it opens. */
    static let islands: [(String, String)] = [
        ("animals", "animals"), ("colors", "colors"), ("shapes", "shapes"), ("counting", "counting"),
        ("peekaboo", "peekaboo"), ("music", "music/doors"), ("words", "words"), ("echo", "echo"),
        ("myday", "myday"), ("say", "corner"),
    ]

    func testEveryIslandOpensAndLeadsBack_Hebrew() { everyIsland("he") }
    func testEveryIslandOpensAndLeadsBack_English() { everyIsland("en") }

    private func everyIsland(_ lang: String) {
        g.launch(lang: lang)
        XCTAssertEqual(g.route, "hub")
        XCTAssertEqual(g.language, lang)
        let shown = Set(g.nodes(prefix: "island.").map(\.id))
        XCTAssertEqual(shown, Set(Self.islands.map { "island.\($0.0)" }), "the map doesn't show every island")
        for (island, route) in Self.islands {
            g.tap("island.\(island)")
            XCTAssertTrue(g.waitRoute(route), "island \(island) didn't open \(route) (on \(g.route ?? "nothing"))")
            g.pause(2.5)  // it settles in: the prompt, Puki
            g.shot("\(lang) \(route)")
            XCTAssertEqual(g.route, route, "\(route) didn't stay up")
            back(to: "hub")
        }
        // The big canvas button
        g.tap("ic_ui_canvas")
        XCTAssertTrue(g.waitRoute("canvas"))
        back(to: "hub")
        // The star counter opens the sticker album
        g.tap("starCounter")
        XCTAssertTrue(g.waitRoute("stickers"))
        back(to: "hub")
    }

    func testEveryMyDayRoomOpens() {
        g.launch(route: "myday")
        let rooms = ["myday/bath", "myday/brush", "myday/tidy", "myday/dress", "myday/table", "myday/bedtime", "myday/kitchen", "myday/garage"]
        for room in rooms {
            // The house shows its rooms on pages: look on this one, else turn
            var found = g.waitNode("room.\(room)", 3)
            if found == nil, let turn = g.visible().first(where: { $0.id == "tap" }) {
                g.tap(turn)
                found = g.waitNode("room.\(room)", 3)
            }
            guard let tile = found else { XCTFail("no door to \(room) in the house"); continue }
            g.tap(tile)
            XCTAssertTrue(g.waitRoute(room), "\(room) didn't open")
            g.pause(2)
            g.shot(room)
            back(to: "myday")
        }
        back(to: "hub")
    }

    func testMusicIslandDoors() {
        g.launch(route: "music/doors")
        let doors = g.visible().filter { $0.id == "door" }.sorted { $0.frame.minX < $1.frame.minX }
        XCTAssertEqual(doors.count, 2, "the music island should offer instruments and dance")
        var opened: Set<String> = []
        for i in 0..<doors.count {
            let door = g.visible().filter { $0.id == "door" }.sorted { $0.frame.minX < $1.frame.minX }[i]
            g.tap(door)
            XCTAssertTrue(g.waitFor { ["music", "dance"].contains(self.g.route ?? "") }, "a music door didn't open")
            opened.insert(g.route ?? "")
            back(to: "music/doors")
        }
        XCTAssertEqual(opened, ["music", "dance"])
    }

    func testPukisCornerOpensWithoutHisBrain() {
        // Without the downloaded brain, the corner is Say it with Puki
        g.launch(route: "corner")
        g.pause(5)
        XCTAssertEqual(g.route, "corner")
        back(to: "hub")
    }

    func testEchoPukiListensAndAnswers() {
        g.launch(route: "echo")
        guard let mic = g.waitNode("hold") else { return XCTFail("no Puki to hold") }
        g.hold(mic, 2.5)
        g.pause(4)  // he plays it back, higher and faster
        g.hold(mic, 1.5)
        g.pause(3)
        XCTAssertEqual(g.route, "echo")
        back(to: "hub")
    }

    func testStickerAlbumPages() {
        g.launch(route: "stickers")
        for _ in 0..<3 {
            if let turn = g.visible().first(where: { $0.id == "tap" }) { g.tap(turn); g.pause(1) }
        }
        XCTAssertEqual(g.route, "stickers")
        back(to: "hub")
    }

    func testDrawingStudio() {
        g.launch(route: "canvas")
        guard let paper = g.waitNode("paper") else { return XCTFail("no paper") }
        let f = paper.frame
        func stroke(_ a: CGFloat, _ b: CGFloat, _ c: CGFloat, _ d: CGFloat) {
            g.drag(CGPoint(x: f.minX + f.width * a, y: f.minY + f.height * b), CGPoint(x: f.minX + f.width * c, y: f.minY + f.height * d), holdFirst: 0.05)
        }
        stroke(0.2, 0.3, 0.8, 0.4)
        // Every paint, then every brush, with a line each
        for (i, pot) in g.nodes(prefix: "paint").enumerated() {
            g.tap(pot)
            stroke(0.15, 0.2 + CGFloat(i % 6) * 0.1, 0.85, 0.25 + CGFloat(i % 6) * 0.1)
        }
        for (i, brush) in g.nodes(prefix: "brush").enumerated() {
            g.tap(brush)
            stroke(0.3, 0.8 - CGFloat(i % 5) * 0.1, 0.7, 0.75 - CGFloat(i % 5) * 0.1)
        }
        g.shot("drawing")
        XCTAssertEqual(g.route, "canvas")
        back(to: "hub")
    }
}
