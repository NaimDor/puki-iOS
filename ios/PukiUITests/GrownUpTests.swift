import XCTest

/** The welcome screen, the parent check in front of the settings, the settings sticking, the time limit. */
final class GrownUpTests: PukiUITestCase {
    func testWelcomeScreenThenTheMap() {
        g.launch(fresh: true, uiTest: false)
        XCTAssertNotNil(g.waitNode("onboarding.play", 15), "no welcome screen on a new install")
        g.shot("welcome")
        g.app.buttons["onboarding.en"].tap()
        g.app.buttons["onboarding.boy"].tap()
        g.app.buttons["onboarding.play"].tap()
        XCTAssertTrue(g.waitRoute("hub", 15), "the welcome screen didn't lead to the map")
        XCTAssertEqual(g.language, "en")
        // And it's done for good: the next start goes straight to the map
        g.launch(fresh: false, uiTest: false)
        XCTAssertTrue(g.waitRoute("hub", 15))
        XCTAssertNil(g.node("onboarding.play"))
    }

    func testSettingsAreBehindAHoldAndAParentCheck() {
        g.launch()
        guard let gear = g.waitNode("ic_ui_settings") else { return XCTFail("no settings button") }
        // A toddler's tap does nothing
        g.tap(gear)
        g.pause(1.5)
        XCTAssertEqual(g.route, "hub")
        XCTAssertNil(g.node("parentTarget"))
        // A short press does nothing either
        g.hold(gear, 0.8)
        g.pause(1)
        XCTAssertNil(g.node("parentTarget"), "the parent check came after a short press")
        // The hold, then a wrong number: back to the map, and the gate shuts for a while
        g.hold(gear, 2.8)
        guard let n = g.parentTarget() else { return XCTFail("no parent check after the hold") }
        guard let wrong = g.nodes(prefix: "parentNumber.").first(where: { $0.id != "parentNumber.\(n)" }) else { return XCTFail("no numbers") }
        g.tap(wrong)
        g.pause(1.5)
        XCTAssertEqual(g.route, "hub")
        XCTAssertNil(g.node("parentTarget"), "the parent check stayed after a wrong number")
        g.hold(gear, 2.8)
        g.pause(1)
        XCTAssertNil(g.node("parentTarget"), "the gate didn't shut after a wrong number")
        // A new start (the lock lasts a minute): the hold and the right numbers open the settings
        g.launch()
        openSettings()
        g.shot("settings")
        back(to: "hub")
    }

    func testLanguageSwitchSticks() {
        g.launch(lang: "he")
        XCTAssertEqual(g.language, "he")
        openSettings()
        guard g.waitNode("toggle.ic_ui_globe") != nil else { return XCTFail("no language switch") }
        g.app.switches["toggle.ic_ui_globe"].tap()
        XCTAssertTrue(g.waitFor { self.g.language == "en" }, "the language didn't switch")
        back(to: "hub")
        XCTAssertEqual(g.language, "en")
        // After a restart, still English
        g.launch(fresh: false, uiTest: false)
        XCTAssertTrue(g.waitRoute("hub", 15))
        XCTAssertEqual(g.language, "en", "the language didn't stick")
    }

    func testTimeLimitGoodbyeAndParentClose() {
        g.launch(extra: ["-PukiTimeLimit", "1"])
        XCTAssertNotNil(g.waitNode("timeLimit", 100), "the time limit's goodbye didn't come after a minute")
        g.pause(8)  // the goodbye: evening, a yawn, a wave
        g.shot("goodbye")
        // The game can't be played under it
        if let island = g.nodes(prefix: "island.").first { g.tap(island) }
        g.pause(1.5)
        XCTAssertEqual(g.route, "hub", "the game went on under the goodbye")
        // A grown-up closes it: the hold, then the parent check
        guard let close = g.waitNode("timeLimitClose") else { return XCTFail("no way to close the goodbye") }
        g.hold(close, 2.8)
        g.passParentCheck()
        XCTAssertTrue(g.waitFor(6) { self.g.node("timeLimit") == nil }, "the goodbye didn't close")
    }
}
