import SwiftUI

/** Fraction of the screen height where the sky meets the sea. */
let horizonFraction: CGFloat = 0.30

private let sparkles: [(CGFloat, CGFloat)] = [(0.15, 0.38), (0.45, 0.34), (0.9, 0.45), (0.25, 0.66), (0.8, 0.9), (0.55, 0.95)]
private let waveRows = 7

struct HubBackground: View {
    var body: some View {
        ZStack {
            // Sky, sea and the faraway islands never move: drawn once
            DrawCanvas { d in
                let w = d.size.width, h = d.size.height
                let horizon = h * horizonFraction
                d.drawRect(Brush.verticalGradient([Color(argb: 0xFF64C8F5), Color(argb: 0xFFB3E5FC)], startY: 0, endY: horizon), .zero, CGSize(w, horizon + 2))
                drawFarIsland(d, w * 0.08, horizon, w * 0.07, h * 0.025)
                drawFarIsland(d, w * 0.58, horizon, w * 0.05, h * 0.018)
                d.drawRect(Brush.verticalGradient([Color(argb: 0xFF4FC3F7), Color(argb: 0xFF29B6F6), Color(argb: 0xFF0397D6)], startY: horizon, endY: h),
                           CGPoint(0, horizon), CGSize(w, h - horizon))
                d.drawRect(Color(argb: 0x66FFFFFF), CGPoint(0, horizon), CGSize(w, h * 0.006))
            }
            .drawingGroup()
            // What moves: sun, clouds, the boat, waves and sparkles
            AmbientTimeline { t in
                DrawCanvas { d in
                    let w = d.size.width, h = d.size.height
                    let horizon = h * horizonFraction
                    let cloudOffset = -0.15 + 1.3 * Ambient.loop(t, 70)
                    let wavePhase = Ambient.loop(t, 4)
                    let sunSpin = Double(Ambient.loop(t, 40) * 360)
                    let boatX = 1.1 - 1.2 * Ambient.loop(t, 90)
                    drawSun(d, CGPoint(w * 0.80, h * 0.13), h * 0.065, sunSpin)
                    drawCloud(d, w * cloudOffset, h * 0.10, w * 0.045)
                    drawCloud(d, w * ((cloudOffset + 0.55).truncatingRemainder(dividingBy: 1.3) - 0.15), h * 0.19, w * 0.035)
                    drawBoat(d, w * boatX, horizon + h * 0.035, h * 0.035, wavePhase)
                    // Little wave arcs drifting sideways, bigger towards the viewer
                    for row in 0..<waveRows {
                        let tt = (CGFloat(row) + 0.5) / CGFloat(waveRows)
                        let y = horizon + (h - horizon) * tt
                        let waveW = w * (0.018 + 0.022 * tt)
                        let gap = waveW * 5
                        let drift = (wavePhase + CGFloat(row) * 0.37).truncatingRemainder(dividingBy: 1) * gap * (row % 2 == 0 ? 1 : -1)
                        let bob = sin((wavePhase + CGFloat(row) * 0.2) * 2 * .pi) * h * 0.003
                        var x = -2 * gap + drift + (CGFloat(row) * 0.43).truncatingRemainder(dividingBy: 1) * gap
                        var path = Path()
                        while x < w + gap {
                            path.move(to: CGPoint(x, y + bob))
                            path.addQuadCurve(to: CGPoint(x + waveW * 0.5, y + bob), control: CGPoint(x + waveW * 0.25, y + bob - waveW * 0.3))
                            path.addQuadCurve(to: CGPoint(x + waveW, y + bob), control: CGPoint(x + waveW * 0.75, y + bob - waveW * 0.3))
                            x += gap
                        }
                        d.drawPath(path, Color.white.copy(alpha: Double(0.18 + 0.22 * tt)), style: Stroke(width: waveW * 0.12, cap: .round))
                    }
                    // Twinkling sparkles on the water
                    for (i, s) in sparkles.enumerated() {
                        let a = (sin((wavePhase + CGFloat(i) * 0.29) * 2 * .pi) + 1) / 2
                        d.drawCircle(Color.white.copy(alpha: Double(0.7 * a)), h * 0.005 * (0.5 + a), CGPoint(w * s.0, h * s.1))
                    }
                }
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

private func drawSun(_ d: DrawScope, _ center: CGPoint, _ radius: CGFloat, _ spin: Double) {
    d.drawCircle(Color(argb: 0x33FFE082), radius * 1.9, center)
    d.rotate(spin, pivot: center) { r in
        for i in 0..<12 {
            r.rotate(Double(i) * 30, pivot: center) {
                $0.drawLine(Color(argb: 0xFFFFCA28), CGPoint(center.x, center.y - radius * 1.25), CGPoint(center.x, center.y - radius * 1.6), radius * 0.16, .round)
            }
        }
    }
    d.drawCircle(Color(argb: 0xFFFFD54F), radius, center)
    d.drawCircle(Color(argb: 0xFFFFE082), radius * 0.65, center + CGPoint(-radius * 0.15, -radius * 0.15))
}

private func drawCloud(_ d: DrawScope, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat) {
    let c = Color(argb: 0xEEFFFFFF)
    d.drawCircle(c, r, CGPoint(x, y))
    d.drawCircle(c, r * 0.8, CGPoint(x - r * 0.9, y + r * 0.25))
    d.drawCircle(c, r * 0.75, CGPoint(x + r * 0.95, y + r * 0.25))
    d.drawRoundRect(c, CGPoint(x - r * 1.6, y + r * 0.2), CGSize(r * 3.2, r * 0.8), r * 0.4)
}

private func drawFarIsland(_ d: DrawScope, _ cx: CGFloat, _ horizon: CGFloat, _ halfW: CGFloat, _ height: CGFloat) {
    let p = Path { p in
        p.moveTo(cx - halfW, horizon + 1)
        p.cubicTo(cx - halfW * 0.6, horizon - height, cx + halfW * 0.4, horizon - height * 1.2, cx + halfW, horizon + 1)
        p.close()
    }
    d.drawPath(p, Color(argb: 0xFF7CB9A8))
}

private func drawBoat(_ d: DrawScope, _ x: CGFloat, _ y: CGFloat, _ s: CGFloat, _ phase: CGFloat) {
    let rock = Double(sin(phase * 2 * .pi) * 4)
    d.rotate(rock, pivot: CGPoint(x, y)) { r in
        r.translate(x, y) { t in
            t.scale(s, s, pivot: .zero) { b in
                b.drawPath(Path { p in p.moveTo(-1.1, 0); p.lineTo(1.1, 0); p.lineTo(0.75, 0.45); p.lineTo(-0.75, 0.45); p.close() }, Color(argb: 0xFFE57373))
                b.drawLine(Color(argb: 0xFF6D4C41), .zero, CGPoint(0, -1.6), 0.08)
                b.drawPath(Path { p in p.moveTo(0.06, -1.55); p.lineTo(0.9, -0.15); p.lineTo(0.06, -0.15); p.close() }, .white)
                b.drawPath(Path { p in p.moveTo(-0.06, -1.3); p.lineTo(-0.7, -0.15); p.lineTo(-0.06, -0.15); p.close() }, Color(argb: 0xFFFFF59D))
            }
        }
    }
}
