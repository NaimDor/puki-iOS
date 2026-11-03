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

struct AnimalGardenScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "animals", onBack: onBack) } }
struct ColorSplashScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "colors", onBack: onBack) } }
struct ShapePlaygroundScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "shapes", onBack: onBack) } }
struct CountingGardenScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "counting", onBack: onBack) } }
struct MusicMeadowScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "music", onBack: onBack) } }
struct DanceScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "dance", onBack: onBack) } }
struct SpinningDiscoBall: View { var body: some View { Circle().fill(Color.gray) } }
struct WordGardenScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "words", onBack: onBack) } }
struct SayItScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "say", onBack: onBack) } }
struct SillyPukiScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "silly", onBack: onBack) } }
struct CareGameScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "care", onBack: onBack) } }
struct StoryGameScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "story", onBack: onBack) } }
struct PeekabooScreen: View { let onBack: () -> Void; let onPlayAgain: () -> Void; var body: some View { Stub(name: "peekaboo", onBack: onBack) } }
struct EchoScreen: View { let onBack: () -> Void; var body: some View { Stub(name: "echo", onBack: onBack) } }
struct MyDayScreen: View { let onRoomClick: (Route) -> Void; let onBack: () -> Void; var body: some View { Stub(name: "myday", onBack: onBack) } }
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
