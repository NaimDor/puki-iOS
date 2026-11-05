import XCTest

/** What every UI test shares: a driver, and the system's permission questions answered "allow". */
class PukiUITestCase: XCTestCase {
    var g: Driver!

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
        Quiescence.off
        g = Driver(self)
        addUIInterruptionMonitor(withDescription: "permissions") { alert in
            for b in ["Allow", "OK", "Allow While Using App", "Allow Full Access", "Allow Access to All Photos"] where alert.buttons[b].exists {
                alert.buttons[b].tap()
                return true
            }
            return false
        }
    }

    override func tearDown() {
        // Whatever happened, a picture of how it ended
        if g.app.state == .runningForeground { g.shot("end of \(name)") }
        XCTAssertNotEqual(g.app.state, .notRunning, "the game closed (crashed?)")
        super.tearDown()
    }

    /** Back to where the back button leads, and check it's `to`. */
    func back(to route: String, file: StaticString = #filePath, line: UInt = #line) {
        g.pause(0.6)  // (the router ignores a second navigation within 400 ms: a toddler's double tap)
        g.tap("back", file: file, line: line)
        XCTAssertTrue(g.waitRoute(route), "back didn't lead to \(route) (on \(g.route ?? "nothing"))", file: file, line: line)
    }

    /** Opens the settings from the hub: the 2-second hold, then the parent check. */
    func openSettings() {
        guard let gear = g.waitNode("ic_ui_settings") else { return XCTFail("no settings button") }
        g.hold(gear, 2.8)
        g.passParentCheck()
        XCTAssertTrue(g.waitRoute("settings"), "the settings didn't open (on \(g.route ?? "nothing"))")
    }
}
