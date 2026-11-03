import SwiftUI

/**
 * One clock for all the idle motion on screen: a TimelineView ticking 30 times a second (plenty for slow idle
 * motion), standing still while `ambientHeld` is on (under an end card).
 */
enum Ambient {
    /** How far through a loop of `period` seconds, 0..1. */
    static func loop(_ t: Double, _ period: Double) -> CGFloat { CGFloat((t / period).truncatingRemainder(dividingBy: 1)) }
    /** A smooth -1..1 wave of `period` seconds. */
    static func wave(_ t: Double, _ period: Double, _ phase: CGFloat = 0) -> CGFloat { sin(2 * .pi * (loop(t, period) + phase)) }
}

/** The ambient clock's time, for views that pose themselves from it. */
struct AmbientTimeline<Content: View>: View {
    @Environment(\.ambientHeld) private var held
    let content: (Double) -> Content
    init(@ViewBuilder _ content: @escaping (Double) -> Content) { self.content = content }
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: held)) { tl in
            content(tl.date.timeIntervalSinceReferenceDate)
        }
    }
}

/** Gentle idle motion (a slow bob and breath); `index` staggers neighbours so they don't move in lockstep. */
struct IdleBob: ViewModifier {
    var index = 0
    var amount: CGFloat = 1
    func body(content: Content) -> some View {
        AmbientTimeline { t in
            let phase = 2 * .pi * (Ambient.loop(t, Double(2200 + index * 170) / 1000) + CGFloat(index) * 0.23)
            let breath = 1 + sin(phase * 2) * 0.025 * amount
            content
                .scaleEffect(x: breath, y: 2 - breath)
                .rotationEffect(.degrees(Double(sin(phase) * 1.5 * amount)))
                .offset(y: sin(phase) * 6 * amount)
        }
    }
}

/** A slow, gentle breathing loop so characters look alive while waiting. */
struct IdleBreathing: ViewModifier {
    var periodMillis = 2400
    func body(content: Content) -> some View {
        AmbientTimeline { t in
            let breath = (1 - cos(2 * .pi * Ambient.loop(t, Double(periodMillis) / 1000))) / 2
            content.scaleEffect(x: 1 - 0.015 * breath, y: 1 + 0.035 * breath, anchor: .bottom)
        }
    }
}

/** Gently wiggles to attract attention. */
struct Wiggle: ViewModifier {
    var enabled = true
    func body(content: Content) -> some View {
        if enabled {
            AmbientTimeline { t in content.rotationEffect(.degrees(Double(Ambient.wave(t, 1.2) * 3))) }
        } else {
            content
        }
    }
}

extension View {
    func idleBob(_ index: Int = 0, amount: CGFloat = 1) -> some View { modifier(IdleBob(index: index, amount: amount)) }
    func idleBreathing(_ periodMillis: Int = 2400) -> some View { modifier(IdleBreathing(periodMillis: periodMillis)) }
    func wiggling(_ enabled: Bool = true) -> some View { modifier(Wiggle(enabled: enabled)) }
}

/** Gently wiggles to attract attention. */
struct WigglingItem<Content: View>: View {
    var enabled = true
    @ViewBuilder let content: () -> Content
    var body: some View { content().wiggling(enabled) }
}
