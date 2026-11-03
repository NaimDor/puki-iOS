import SwiftUI

private enum Bit { case paper, dot, star, ribbon }

/** One piece of confetti. Positions are in screen heights so the burst looks the same on any screen. */
private struct Piece {
    let x0, y0, vx, vy, delay, spin, flip, phase, size: CGFloat
    let kind: Bit
    let color: Color
}

private let confettiColors = [
    Color(argb: 0xFFFF6B6B), Color(argb: 0xFF4ECDC4), Color(argb: 0xFFFFE66D),
    Color(argb: 0xFF95E1D3), Color(argb: 0xFFF38181), Color(argb: 0xFFAA96DA),
    Color(argb: 0xFFFCBF49), Color(argb: 0xFF2EC4B6), Color(argb: 0xFFFF8FAB),
]

private let duration: CGFloat = 3.4
private let gravity: CGFloat = 1.5    // heights / s²
private let drag: CGFloat = 0.7       // seconds; air slows the pieces and they flutter down

/** A party: two confetti cannons fire from the bottom corners, then paper, stars and ribbons flutter down. */
struct ConfettiOverlay: View {
    let show: Bool
    var onFinished: () -> Void = {}
    var body: some View {
        if show { ConfettiRun(onFinished: onFinished) }
    }
}

private struct ConfettiRun: View {
    let onFinished: () -> Void
    @State private var pieces = makePieces()
    @State private var start = Date()
    @Environment(\.pukiMood) private var mood

    var body: some View {
        TimelineView(.animation) { tl in
            let now = CGFloat(tl.date.timeIntervalSince(start))
            DrawCanvas { d in
                let fade = now < duration - 0.8 ? 1 : ((duration - now) / 0.8).coerceIn(0, 1)
                for p in pieces { drawPiece(d, p, now - p.delay, fade) }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
        .task {
            start = Date()
            Task { await mood?.celebrate() }  // Puki's happy too
            guard await delay(Int(duration * 1000)) else { return }
            onFinished()
        }
    }
}

private func makePieces() -> [Piece] {
    var r = SystemRandomNumberGenerator()
    func f() -> CGFloat { CGFloat.random(in: 0..<1, using: &r) }
    var list: [Piece] = []
    func piece(_ x0: CGFloat, _ y0: CGFloat, _ angleDeg: CGFloat, _ speed: CGFloat, _ delay: CGFloat) {
        let a = angleDeg * .pi / 180
        let k = Int.random(in: 0..<10, using: &r)
        let kind: Bit = k <= 4 ? .paper : k <= 6 ? .dot : k <= 8 ? .star : .ribbon
        list.append(Piece(x0: x0, y0: y0, vx: cos(a) * speed, vy: sin(a) * speed, delay: delay,
                          spin: f() * 500 - 250, flip: 4 + f() * 8, phase: f() * 6.28,
                          size: kind == .star ? 9 + f() * 7 : 6 + f() * 6, kind: kind,
                          color: confettiColors[Int.random(in: 0..<confettiColors.count, using: &r)]))
    }
    // Two cannons, aimed up and inwards (about 90 pieces)
    for _ in 0..<34 { piece(-0.02, 1.02, -62 + f() * 26 - 13, 1.7 + f() * 1.1, f() * 0.25) }
    for _ in 0..<34 { piece(1.02, 1.02, -118 + f() * 26 - 13, 1.7 + f() * 1.1, f() * 0.25) }
    // And a gentle shower from above
    for _ in 0..<24 { piece(f(), -0.05, 90, 0.1 + f() * 0.2, 0.3 + f() * 0.9) }
    return list
}

private func drawPiece(_ d: DrawScope, _ p: Piece, _ t: CGFloat, _ fade: CGFloat) {
    if t <= 0 { return }
    let h = d.size.height
    // Motion with air drag: v(t) = gτ + (v0 - gτ)·e^(-t/τ)
    let decay = 1 - exp(-t / drag)
    let terminal = gravity * drag
    let flutter = sin(t * 3 + p.phase) * 0.025 * min(t, 1)
    let x = p.x0 * d.size.width + (p.vx * drag * decay + flutter) * h
    let y = p.y0 * h + (terminal * t + (p.vy - terminal) * drag * decay) * h
    if y > h + 40 || x < -40 || x > d.size.width + 40 { return }
    let c = CGPoint(x, y)
    let s = p.size
    let color = p.color.copy(alpha: Double(fade))
    let turn = cos(t * p.flip + p.phase)
    d.rotate(Double(p.phase * 57 + p.spin * t), pivot: c) { r in
        switch p.kind {
        case .paper: r.scale(turn, 1, pivot: c) { $0.drawRect(color, CGPoint(x - s / 2, y - s * 0.35), CGSize(s, s * 0.7)) }
        case .dot: r.drawCircle(color, s * 0.45, c)
        case .star: r.scale(0.6 + 0.4 * abs(turn), 1, pivot: c) { $0.drawPath(starPath(c, s * 0.6), color) }
        case .ribbon: r.scale(1, turn, pivot: c) { $0.drawRoundRect(color, CGPoint(x - s * 0.18, y - s * 1.2), CGSize(s * 0.36, s * 2.4), s * 0.18) }
        }
    }
}
