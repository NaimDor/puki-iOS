import SwiftUI

// Screens still being ported (each moves to its own file).

private struct Stub: View {
    let name: String
    let onBack: () -> Void
    var body: some View {
        GameBackground(scene: .garden) {
            VStack { GameTopBar(onBack: onBack, roundIndex: 0, totalRounds: 0); Spacer(); Text(name); Spacer() }
        }
    }
}

struct SettingsScreen: View { let onBack: () -> Void; var body: some View { Stub(name: "settings", onBack: onBack) } }
struct OnboardingScreen: View { let settings: AppSettings; var body: some View { Text("welcome") } }
