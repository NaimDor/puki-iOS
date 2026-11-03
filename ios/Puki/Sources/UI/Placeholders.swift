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

struct BathTimeScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "bath", onBack: onBack) } }
struct BrushyBrushyScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "brush", onBack: onBack) } }
struct TidyUpScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "tidy", onBack: onBack) } }
struct GettingDressedScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "dress", onBack: onBack) } }
struct TableTimeScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "table", onBack: onBack) } }
struct CakeScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "cake", onBack: onBack) } }
struct CarWashScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "carwash", onBack: onBack) } }
struct BedtimeRoutineScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "bedtime", onBack: onBack) } }
struct FreeCanvasScreen: View { let onBack: () -> Void; var body: some View { Stub(name: "canvas", onBack: onBack) } }
struct SettingsScreen: View { let onBack: () -> Void; var body: some View { Stub(name: "settings", onBack: onBack) } }
struct OnboardingScreen: View { let settings: AppSettings; var body: some View { Text("welcome") } }
