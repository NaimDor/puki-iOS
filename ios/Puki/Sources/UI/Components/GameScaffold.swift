import SwiftUI

/**
 * What every play screen stands on: the `scene` (or nothing, for a game that draws its own), the game, Puki in
 * the corner and the sparkles under her finger.
 */
struct GameBackground<Content: View>: View {
    /** False where the game shows Puki itself (Say it with Puki). */
    var withCompanion = true
    /** An illustrated backdrop; without one, the game draws its own. */
    var scene: Scene? = nil
    @ViewBuilder let content: () -> Content
    @StateObject private var mood = ReactionState()

    var body: some View {
        ZStack {
            if let scene {
                SceneBackdrop(scene: scene)
                    .id(scene)
                    .transition(.opacity.animation(.easeInOut(duration: 0.5)))  // (one game can move between places)
            }
            content()
            // פוקי the parrot
            if withCompanion {
                CompanionParrot()
                    .padding(12)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            }
            SparkleLayer()
        }
        .environment(\.pukiMood, mood)
    }
}

struct GameTopBar: View {
    let onBack: () -> Void
    let roundIndex: Int
    let totalRounds: Int
    var starsEarned: Int? = nil  // the routines have dots but no stars to count
    @StateObject private var starHop = ReactionState()
    @State private var settled = false

    var body: some View {
        HStack {
            BackButton(onBack: onBack)
            Spacer()
            // Round dots
            HStack(spacing: 8) {
                ForEach(0..<max(0, totalRounds), id: \.self) { i in
                    Circle()
                        .fill(i < roundIndex ? Color(argb: 0xFFFFD700) : i == roundIndex ? Color.white : Color.black.opacity(0.18))
                        .frame(width: i == roundIndex ? 18 : 14, height: i == roundIndex ? 18 : 14)
                        .animation(.easeInOut(duration: 0.25), value: roundIndex)
                }
            }
            .testID("rounds").accessibilityValue("\(roundIndex)/\(totalRounds)")
            Spacer()
            // Stars; the star hops each time the count goes up
            if let stars = starsEarned {
                HStack(spacing: 6) {
                    Pic("ic_ui_star").frame(width: 26, height: 26).reaction(starHop, jumpHeight: 10)
                    Text("\(stars)")
                        .font(PukiFont.rounded(20, .medium))
                        .foregroundColor(.white)
                        .contentTransition(.numericText())
                        .animation(.spring, value: stars)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Capsule().fill(Color.black.opacity(0.22)))
                .testID("stars").accessibilityValue("\(stars)")
            } else {
                Spacer().frame(width: 64)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .onChange(of: starsEarned) { _, _ in Task { await starHop.hop() } }
    }
}

/**
 * Starts a round: tells Puki what's on screen (`scene` in Hebrew, `sceneEn` in English), then, after a breath,
 * says the round's prompt (`voiceKeys`, one after another).
 */
struct RoundPrompt: ViewModifier {
    let round: AnyHashable
    let scene: String
    let sceneEn: String
    let voiceKeys: [String]
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language

    func body(content: Content) -> some View {
        content.task(id: round) {
            app.companion.setScene(scene, sceneEn)
            guard await delay(500) else { return }
            app.voice.playSequence(voiceKeys, language)
        }
    }
}

extension View {
    func roundPrompt(_ round: AnyHashable, _ scene: String, _ sceneEn: String, _ voiceKeys: String...) -> some View {
        modifier(RoundPrompt(round: round, scene: scene, sceneEn: sceneEn, voiceKeys: voiceKeys))
    }
    func roundPrompt(_ round: AnyHashable, _ scene: String, _ sceneEn: String, keys: [String]) -> some View {
        modifier(RoundPrompt(round: round, scene: scene, sceneEn: sceneEn, voiceKeys: keys))
    }
}

/** The back arrow; points left in English and right in Hebrew. */
private struct BackArrowIcon: View {
    @Environment(\.layoutDirection) private var dir
    var body: some View {
        Pic("ic_ui_back")
            .frame(width: 28, height: 28)
            .scaleEffect(x: dir == .rightToLeft ? -1 : 1, y: 1)
    }
}

/** The way out of every screen: looks 64pt (it shouldn't compete with the game), and takes a 100pt touch. */
struct BackButton: View {
    let onBack: () -> Void
    var body: some View {
        Button(action: onBack) {
            ZStack {
                Circle().fill(Color.black.opacity(0.22)).frame(width: 64, height: 64)
                BackArrowIcon()
            }
            .frame(width: 100, height: 100)
            .contentShape(Rectangle())
        }
        .buttonStyle(SquishButtonStyle())
        .frame(width: 64, height: 64)
        .testID("back")
    }
}

/**
 * Lets the child answer the round's question out loud instead of tapping: once the question has been asked and
 * everyone is quiet, Puki listens for a few seconds. If she said one of `options`, `onAnswer` gets its index,
 * just as if she'd tapped it; after a wrong one Puki listens once more.
 */
struct AnswerOutLoud: ViewModifier {
    let key: AnyHashable
    let options: [[String]]
    let active: Bool
    var askedAfterMs = 800
    let onAnswer: (Int) -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.gamePaused) private var paused

    struct K: Hashable { let key: AnyHashable; let active: Bool; let paused: Bool }

    func body(content: Content) -> some View {
        content.task(id: K(key: key, active: active, paused: paused)) {
            if !active || paused { return }
            let companion = app.companion
            for tries in 0..<2 {
                guard await delay(tries == 0 ? askedAfterMs : 800) else { return }
                var quiet = 0
                while quiet < 3 {
                    guard await delay(150) else { return }
                    quiet = (app.voice.isPlaying() || companion.speaking) ? 0 : quiet + 1
                }
                guard let heard = await companion.listenForAnswer(options.flatMap { $0 }), !Task.isCancelled else { return }
                let picked = SpokenAnswer.pick(heard, options)
                if picked < 0 { return }
                onAnswer(picked)
            }
        }
    }
}

extension View {
    func answerOutLoud(_ key: AnyHashable, options: [[String]], active: Bool, askedAfterMs: Int = 800, onAnswer: @escaping (Int) -> Void) -> some View {
        modifier(AnswerOutLoud(key: key, options: options, active: active, askedAfterMs: askedAfterMs, onAnswer: onAnswer))
    }
}
