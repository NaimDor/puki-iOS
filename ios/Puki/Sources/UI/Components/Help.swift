import SwiftUI

/** How long a quiet moment lasts before Puki asks again. */
private let quietMs: Int64 = 8_000

/**
 * Help for a toddler who's stuck: notices quiet moments and misses, and says when to show the answer.
 * One per screen, reset for each round (see `.helpLoop`).
 */
@MainActor
final class Help: ObservableObject {
    /** True once the right answer should wiggle and glow. */
    @Published private(set) var showHint = false
    private var lastOwnTouch = uptimeMs()
    private var misses = 0
    var onMiss: () -> Void = {}
    var onAssisted: () -> Void = {}
    var missesCounted = true
    private var roundKey: AnyHashable?

    var lastTouch: Int64 { max(lastOwnTouch, TouchHub.shared.lastTouch) }

    /** A new round: no hint, no misses. */
    func reset(for key: AnyHashable) {
        if roundKey == key { return }
        roundKey = key
        showHint = false
        misses = 0
        lastOwnTouch = uptimeMs()
    }

    /** The answer's shown: once a round, that's the game helping (`assisted`: it counts against the level). */
    func hint(assisted: Bool = true) {
        if showHint { return }
        showHint = true
        if assisted { onAssisted() }
    }

    func touched() { lastOwnTouch = uptimeMs() }

    /** A wrong try: after two, show the answer. */
    func missed() {
        touched()
        onMiss()
        misses += 1
        if misses >= 2 { hint(assisted: !missesCounted) }
    }

    /** The loop for one round: after a quiet moment, `nudge`; after a second one, the hint. Stops after three. */
    func run(nudge: @escaping () -> Void) async {
        touched()  // the clock starts when the round does
        var nudges = 0
        while nudges < 3 {
            guard await delay(500) else { return }
            if uptimeMs() - lastTouch >= quietMs {
                nudges += 1
                touched()
                nudge()
                if nudges >= 2 { hint() }
            }
        }
    }
}

private struct HelpLoop: ViewModifier {
    @ObservedObject var help: Help
    let key: AnyHashable
    let active: Bool
    let missesCounted: Bool
    let onAssisted: () -> Void
    let onNudge: () -> Void
    @Environment(\.pukiMood) private var mood
    @Environment(\.gamePaused) private var paused

    struct LoopKey: Hashable { let key: AnyHashable; let active: Bool; let paused: Bool }

    func body(content: Content) -> some View {
        content
            .onAppear { configure() }
            .task(id: LoopKey(key: key, active: active, paused: paused)) {
                configure()
                if !active || paused { return }
                await help.run(nudge: onNudge)
            }
    }

    private func configure() {
        help.reset(for: key)
        help.onMiss = { Task { await mood?.oops() } }
        help.onAssisted = onAssisted
        help.missesCounted = missesCounted
    }
}

extension View {
    /**
     * Help for this round (`key`): after a quiet moment, `onNudge` (repeat the question); after a second one,
     * or two misses, `help.showHint`. `active` pauses it; `onAssisted` hears when the answer is shown.
     */
    func helpLoop(_ help: Help, key: AnyHashable, active: Bool = true, missesCounted: Bool = true,
                  onAssisted: @escaping () -> Void = {}, onNudge: @escaping () -> Void) -> some View {
        modifier(HelpLoop(help: help, key: key, active: active, missesCounted: missesCounted, onAssisted: onAssisted, onNudge: onNudge))
    }

    /** The answer gently wiggling and glowing, when `on`. */
    @ViewBuilder func hintGlow(_ on: Bool) -> some View {
        if on { modifier(HintGlow()) } else { self }
    }
}

private struct HintGlow: ViewModifier {
    func body(content: Content) -> some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
            let phase = CGFloat(tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 0.9) / 0.9)
            let a = phase * 2 * .pi
            let pulse = 0.5 + 0.5 * sin(a)
            content
                .background(
                    GeometryReader { g in
                        let r = g.size.maxDimension * (0.7 + 0.06 * pulse)
                        Circle()
                            .fill(RadialGradient(stops: [
                                .init(color: Color(argb: 0xFFFFEB3B).opacity(0.9), location: 0),
                                .init(color: Color(argb: 0xFFFFEB3B).opacity(Double(0.75 + 0.2 * pulse)), location: 0.72),
                                .init(color: .clear, location: 1),
                            ], center: .center, startRadius: 0, endRadius: r))
                            .frame(width: r * 2, height: r * 2)
                            .position(x: g.size.width / 2, y: g.size.height / 2)
                    }
                    .allowsHitTesting(false)
                )
                .scaleEffect(1 + 0.06 * sin(a))
                .rotationEffect(.degrees(Double(sin(a * 2) * 7)))
        }
    }
}
