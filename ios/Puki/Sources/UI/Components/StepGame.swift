import SwiftUI

private let partyMs = 1800
private let quietStepMs = 1200

/**
 * The skeleton of a game of a few steps (washing the car, brushing Teddy's teeth, baking, going to bed): which
 * step it's on, the stars, the party after a step, and the end.
 */
@MainActor
final class StepGame<S: Equatable>: ObservableObject {
    @Published private(set) var step: S
    @Published private(set) var stars = 0
    @Published private(set) var celebrate = false
    /** Right after a step, before the next one starts. */
    @Published private(set) var betweenSteps = false
    @Published private(set) var sessionComplete = false
    private let steps: Int
    private(set) var session: GameSession?
    private var sfx: SfxPlayer?

    init(first: S, steps: Int) {
        step = first
        self.steps = steps
    }

    /** The game's bookkeeping, once the screen has the app (call from .task). */
    func attach(_ app: AppModel, island: String) {
        if session != nil { return }
        session = GameSession(island, app.progressRepository)
        sfx = app.sfx
    }

    /** A step done: a star, a little party (`party` false for a quiet chime), and on to `next`. */
    func done(_ next: S?, party: Bool = true) {
        stars += 1
        session?.star()
        sfx?.play(party ? .success : .starCollect, volume: party ? 1 : 0.6)
        celebrate = party
        betweenSteps = true
        Task { @MainActor in
            await delay(party ? partyMs : quietStepMs)
            celebrate = false
            betweenSteps = false
            if let next { step = next }
        }
    }

    /** The game's over: the end card. */
    func finish() {
        session?.finish(rounds: steps)
        sessionComplete = true
    }
}

/** A tool on a round plate, for a step that starts with a tap (the hose, the toothpaste...). */
struct ToolButton<Content: View>: View {
    let size: CGFloat
    let hint: Bool
    var color = Color(argb: 0xFFE1F5FE)
    var enabled = true
    let onClick: () -> Void
    @ViewBuilder let content: () -> Content
    var body: some View {
        ZStack { content() }
            .frame(width: size, height: size)
            .background(Circle().fill(color).shadow(color: .black.opacity(0.25), radius: 8, y: 4))
            .clipShape(Circle())
            .clickable(enabled, action: onClick)
            .hintGlow(hint)
            .idleBob(1)
            .popIn()
    }
}
