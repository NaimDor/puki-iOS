import SwiftUI

/*
 * Names for the UI tests (ios/PukiUITests). A tagged thing becomes one accessibility element with a fixed
 * identifier, which the tests find and touch where it is on screen, as a finger would. Nothing changes for the
 * child. The names the tests know:
 *   route          (value: the screen's route, label: he|en)    rounds, stars (value: "2/5", "3")
 *   island.<id>    back    continue  home  album                  ic_ui_settings  ic_ui_canvas  starCounter
 *   piece.<id>     zone.<id>    tool    choice    play    scrub    hold    door    room.<route>    tap
 *   paper  paint  brush    parentTarget (value: the number in words)  parentNumber.<n>    timeLimit
 *   onboarding.<he|en|girl|boy|play>    toggle.<icon>    settingsBack
 */
extension View {
    /** One element the tests find by `id` and touch in the middle. */
    func testID(_ id: String) -> some View {
        accessibilityElement(children: .ignore).accessibilityIdentifier(id)
    }

    /** What debug builds tell the tests besides (which instrument the tune wants next); release builds say nothing. */
    @ViewBuilder func testValue(_ value: String) -> some View {
        #if DEBUG
        accessibilityValue(value)
        #else
        self
        #endif
    }
}

#if DEBUG
/**
 * Debug builds only, for the UI tests and the store pictures:
 *   -PukiFresh 1          starts as if just installed (settings and progress wiped)
 *   -PukiUITest 1         skips the welcome screen, no time limit, Puki on, no listening for answers
 *   -PukiTimeLimit <min>  with -PukiUITest: that time limit instead
 *   -PukiLang en|he, -PukiGender boy|girl, -PukiScreenshot <route> (opens that screen)
 */
enum TestLaunch {
    static var fresh: Bool { UserDefaults.standard.bool(forKey: "PukiFresh") }
    static var uiTest: Bool { UserDefaults.standard.bool(forKey: "PukiUITest") || UserDefaults.standard.string(forKey: "PukiScreenshot") != nil }

    /** Before anything reads them: the settings and progress of a new install. */
    static func wipeIfFresh() {
        guard fresh else { return }
        for suite in ["settings", "progress"] { UserDefaults.standard.removePersistentDomain(forName: suite) }
    }
}
#endif

/** Where the tests read which screen is up and in which language: an invisible point in the corner. */
struct RouteMarker: View {
    let route: String
    @Environment(\.language) private var language
    var body: some View {
        Color.clear
            .frame(width: 1, height: 1)
            .accessibilityElement()
            .accessibilityIdentifier("route")
            .accessibilityLabel(language == .hebrew ? "he" : "en")
            .accessibilityValue(route)
            .allowsHitTesting(false)
            #if DEBUG
            .background(TouchTrace())
            #endif
    }
}

#if DEBUG
/** Where the last touch landed, for working out a touch that did nothing. */
private struct TouchTrace: View {
    @ObservedObject private var hub = TouchHub.shared
    var body: some View {
        Color.clear
            .frame(width: 1, height: 1)
            .accessibilityElement()
            .accessibilityIdentifier("touch")
            .accessibilityValue(hub.trace)
            .allowsHitTesting(false)
    }
}
#endif
