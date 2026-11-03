import SwiftUI

/** Illustrated backdrops for the games, drawn in code (no image files). */
enum Scene: CaseIterable { case farm, garden, playground, stage, home, sunset, bath, savanna, jungle, ice, treehouse }

private struct Palette { let skyTop, skyBottom, hillBack, hillFront, accent: Color }

private func paletteFor(_ scene: Scene) -> Palette {
    switch scene {
    case .farm: return Palette(skyTop: Color(argb: 0xFF64B5F6), skyBottom: Color(argb: 0xFFBBDEFB), hillBack: Color(argb: 0xFF9CCC65), hillFront: Color(argb: 0xFF66BB6A), accent: Color(argb: 0xFFFFF176))
    case .garden: return Palette(skyTop: Color(argb: 0xFF81D4FA), skyBottom: Color(argb: 0xFFE1F5FE), hillBack: Color(argb: 0xFFAED581), hillFront: Color(argb: 0xFF7CB342), accent: Color(argb: 0xFFF48FB1))
    // Pale, so the only strong colors on the screen are the ones she's looking for
    case .playground: return Palette(skyTop: Color(argb: 0xFFD6EAF8), skyBottom: Color(argb: 0xFFF4FAFE), hillBack: Color(argb: 0xFFE3EFD3), hillFront: Color(argb: 0xFFD3E5BD), accent: .white)
    case .savanna: return Palette(skyTop: Color(argb: 0xFFFFCC80), skyBottom: Color(argb: 0xFFFFF3E0), hillBack: Color(argb: 0xFFE9CD8C), hillFront: Color(argb: 0xFFDDB966), accent: .white)
    case .jungle: return Palette(skyTop: Color(argb: 0xFF80CBC4), skyBottom: Color(argb: 0xFFE0F2F1), hillBack: Color(argb: 0xFF66BB6A), hillFront: Color(argb: 0xFF43A047), accent: Color(argb: 0xFFFF8A65))
    case .ice: return Palette(skyTop: Color(argb: 0xFF90CAF9), skyBottom: Color(argb: 0xFFE3F2FD), hillBack: Color(argb: 0xFFE1F5FE), hillFront: Color(argb: 0xFFF5FBFF), accent: .white)
    case .sunset: return Palette(skyTop: Color(argb: 0xFFFFB74D), skyBottom: Color(argb: 0xFFFFF59D), hillBack: Color(argb: 0xFFFFCC80), hillFront: Color(argb: 0xFFA5D6A7), accent: .white)
    default: return Palette(skyTop: Color(argb: 0xFF81D4FA), skyBottom: Color(argb: 0xFFE1F5FE), hillBack: Color(argb: 0xFFAED581), hillFront: Color(argb: 0xFF7CB342), accent: Color(argb: 0xFFFFF176))
    }
}

private let cloudSeconds = 60.0
private let swaySeconds = 6.0
private let clouds: [(CGFloat, CGFloat, CGFloat)] = [(0.1, 0.14, 1), (0.45, 0.08, 0.7), (0.7, 0.26, 0.85)]

struct SceneBackdrop: View {
    let scene: Scene
    var body: some View {
        let rooms = scene == .home || scene == .bath || scene == .treehouse
        ZStack {
            // What never moves, drawn once in a layer of its own
            DrawCanvas { d in
                switch scene {
                case .stage: drawStage(d)
                case .home: drawRoom(d, wall: Color(argb: 0xFFFFF3E0), stripe: Color(argb: 0xFFFFE0B2), floor: Color(argb: 0xFFD7A86E))
                case .bath: drawRoom(d, wall: Color(argb: 0xFFE1F5FE), stripe: Color(argb: 0xFFB3E5FC), floor: Color(argb: 0xFFB0BEC5), tiles: true)
                case .treehouse: drawTreehouse(d)
                default: drawOutdoorsStill(d, scene, paletteFor(scene))
                }
            }
            .drawingGroup()
            // What moves (clouds, grass, spotlights); the rooms don't move at all
            if !rooms { SceneMotion(scene: scene) }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }
}

private struct SceneMotion: View {
    let scene: Scene
    var body: some View {
        AmbientTimeline { t in
            DrawCanvas { d in
                if scene == .stage {
                    drawSpotlights(d, Ambient.wave(t, swaySeconds))
                    return
                }
                let pal = paletteFor(scene)
                for (i, c) in clouds.enumerated() {
                    let (fx, fy, s) = c
                    let x = ((fx + Ambient.loop(t, cloudSeconds) * (0.6 + CGFloat(i) * 0.25)).truncatingRemainder(dividingBy: 1.2) - 0.1) * d.size.width
                    drawCloud(d, CGPoint(x, d.size.height * fy), d.size.height * 0.06 * s)
                }
                let sway = (Ambient.wave(t, swaySeconds) * 4).rounded() / 4
                drawOutdoorsMotion(d, scene, pal, sway)
            }
        }
    }
}

private func hillY(_ x: CGFloat, _ w: CGFloat, _ base: CGFloat, _ amp: CGFloat, _ freq: CGFloat, _ phase: CGFloat) -> CGFloat {
    base - sin((x / w * freq + phase) * .pi) * amp
}

private func hillPath(_ size: CGSize, _ base: CGFloat, _ amp: CGFloat, _ freq: CGFloat, _ phase: CGFloat) -> Path {
    let w = size.width
    return Path { p in
        p.moveTo(0, size.height)
        var x: CGFloat = 0
        while x <= w {
            p.lineTo(x, hillY(x, w, base, amp, freq, phase))
            x += 16
        }
        p.lineTo(w, hillY(w, w, base, amp, freq, phase))
        p.lineTo(w, size.height)
        p.close()
    }
}

private func drawOutdoorsStill(_ d: DrawScope, _ scene: Scene, _ p: Palette) {
    let w = d.size.width, h = d.size.height
    d.drawRect(Brush.verticalGradient([p.skyTop, p.skyBottom], endY: h * 0.75))
    // Sun with a soft glow
    let sun = CGPoint(w * 0.86, h * 0.17)
    d.drawCircle(Brush.radialGradient([Color(argb: 0x88FFF59D), .clear], center: sun, radius: h * 0.2), h * 0.2, sun)
    d.drawCircle(Color(argb: 0xFFFFE082), h * 0.075, sun)
    d.drawCircle(Color(argb: 0xFFFFF59D), h * 0.055, sun)
    if scene == .ice { drawIcePeaks(d) }
    // Two rows of hills
    d.drawPath(hillPath(d.size, h * 0.66, h * 0.05, 1.3, 0.2), p.hillBack)
    d.drawPath(hillPath(d.size, h * 0.76, h * 0.035, 2.1, 0.65), p.hillFront)
    switch scene {
    case .savanna:
        drawAcacia(d, CGPoint(w * 0.1, h * 0.74), h * 0.42)
        drawAcacia(d, CGPoint(w * 0.9, h * 0.7), h * 0.3)
    case .jungle: drawJungleLeaves(d)
    default: break
    }
}

/** A flat-topped savanna tree, its foot at `foot`. */
private func drawAcacia(_ d: DrawScope, _ foot: CGPoint, _ height: CGFloat) {
    let bark = Color(argb: 0xFF8D6E63)
    let top = foot - CGPoint(0, height * 0.8)
    d.drawLine(bark, foot, top, height * 0.06, .round)
    for s in [-1.0, 1.0] as [CGFloat] { d.drawLine(bark, foot - CGPoint(0, height * 0.5), top + CGPoint(s * height * 0.3, 0), height * 0.04, .round) }
    d.drawOval(Color(argb: 0xFF9CAF5A), top - CGPoint(height * 0.56, height * 0.2), CGSize(height * 1.12, height * 0.26))
    d.drawOval(Color(argb: 0xFFB2C276), top - CGPoint(height * 0.32, height * 0.25), CGSize(height * 0.64, height * 0.16))
}

/** Vines and big leaves hanging into the picture at the sides. */
private func drawJungleLeaves(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let vine = Color(argb: 0xFF558B2F)
    for (fx, len) in [(0.05, 0.5), (0.14, 0.32), (0.86, 0.34), (0.95, 0.54)] as [(CGFloat, CGFloat)] {
        d.drawLine(vine, CGPoint(w * fx, 0), CGPoint(w * fx, h * len), h * 0.008)
        var y = h * 0.06
        var side: CGFloat = 1
        while y < h * len {
            let leaf = Path { p in
                p.moveTo(w * fx, y)
                p.quadraticBezierTo(w * fx + side * h * 0.05, y - h * 0.035, w * fx + side * h * 0.1, y + h * 0.01)
                p.quadraticBezierTo(w * fx + side * h * 0.05, y + h * 0.04, w * fx, y)
                p.close()
            }
            d.drawPath(leaf, side > 0 ? Color(argb: 0xFF388E3C) : Color(argb: 0xFF2E7D32))
            y += h * 0.07
            side = -side
        }
    }
}

/** Snowy peaks far away, behind the ice. */
private func drawIcePeaks(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    for (fx, peak) in [(0.14, 0.3), (0.34, 0.42), (0.66, 0.36), (0.88, 0.28)] as [(CGFloat, CGFloat)] {
        let rock = Path { p in p.moveTo(w * (fx - 0.17), h * 0.72); p.lineTo(w * fx, h * peak); p.lineTo(w * (fx + 0.17), h * 0.72); p.close() }
        d.drawPath(rock, Color(argb: 0xFFB3D4EA))
        let cap = Path { p in
            p.moveTo(w * fx, h * peak)
            p.lineTo(w * (fx + 0.05), h * (peak + 0.11)); p.lineTo(w * fx, h * (peak + 0.08)); p.lineTo(w * (fx - 0.05), h * (peak + 0.11))
            p.close()
        }
        d.drawPath(cap, .white)
    }
}

private func drawOutdoorsMotion(_ d: DrawScope, _ scene: Scene, _ p: Palette, _ sway: CGFloat) {
    let w = d.size.width, h = d.size.height
    if scene == .ice { return }  // (nothing grows on the ice)
    // Grass tufts and flowers along the front hill
    let tuft = p.hillFront.scaled(r: 0.8, g: 0.85, b: 0.8)
    for i in 0..<14 {
        let fi = CGFloat(i)
        let x = w * (fi + 0.5) / 14 + sin(fi * 2.7) * w * 0.02
        let y = hillY(x, w, h * 0.76, h * 0.035, 2.1, 0.65) + h * (0.03 + 0.15 * CGFloat((i * 37) % 10) / 10)
        if y > h * 0.97 { continue }
        for b in -1...1 {
            d.drawLine(tuft, CGPoint(x + CGFloat(b) * 5, y), CGPoint(x + CGFloat(b) * 9 + sway * 3, y - h * 0.022), 4)
        }
        if i % 3 == 1 && scene != .farm && scene != .savanna {
            let fc = i % 2 == 0 ? p.accent : Color.white
            for k in 0..<5 {
                let a = CGFloat(k) * 2 * .pi / 5
                d.drawCircle(fc, h * 0.008, CGPoint(x + 14 + sin(a) * h * 0.009, y - h * 0.03 + cos(a) * h * 0.009))
            }
            d.drawCircle(Color(argb: 0xFFFFC107), h * 0.006, CGPoint(x + 14, y - h * 0.03))
        }
    }
    if scene == .farm { drawFence(d, h * 0.74) }
}

private func drawCloud(_ d: DrawScope, _ c: CGPoint, _ r: CGFloat) {
    let cloud = Color.white.copy(alpha: 0.92)
    d.drawCircle(cloud, r, c)
    d.drawCircle(cloud, r * 0.75, c + CGPoint(-r * 1.0, r * 0.25))
    d.drawCircle(cloud, r * 0.8, c + CGPoint(r * 1.05, r * 0.2))
    d.drawRoundRect(cloud, CGPoint(c.x - r * 1.6, c.y + r * 0.1), CGSize(r * 3.3, r * 0.85), r * 0.45)
}

private func drawFence(_ d: DrawScope, _ y: CGFloat) {
    let wood = Color(argb: 0xFFFFF8E1), shade = Color(argb: 0xFFD7CCC8)
    let postW = d.size.height * 0.022, postH = d.size.height * 0.09
    var x = d.size.width * 0.02
    d.drawRect(shade, CGPoint(0, y - postH * 0.7), CGSize(d.size.width, postW * 0.6))
    d.drawRect(shade, CGPoint(0, y - postH * 0.3), CGSize(d.size.width, postW * 0.6))
    while x < d.size.width {
        d.drawRoundRect(wood, CGPoint(x, y - postH), CGSize(postW, postH), postW / 2)
        x += d.size.width / 18
    }
}

private func drawSpotlights(_ d: DrawScope, _ sway: CGFloat) {
    let w = d.size.width, h = d.size.height
    let beam = Path { p in
        p.moveTo(-w * 0.02, 0); p.lineTo(w * 0.02, 0)
        p.lineTo(w * 0.16, h * 0.8); p.lineTo(-w * 0.16, h * 0.8); p.close()
    }
    for (i, fx) in [0.3, 0.7].enumerated() {
        d.translate(w * CGFloat(fx) + sway * w * 0.03 * (i == 0 ? 1 : -1), 0) { s in
            s.ctx.fill(beam, with: .linearGradient(Gradient(colors: [Color(argb: 0x55FFF9C4), Color(argb: 0x10FFF9C4)]),
                                                   startPoint: .zero, endPoint: CGPoint(0, h * 0.8)))
        }
    }
}

private func drawStage(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRect(Brush.verticalGradient([Color(argb: 0xFF4A148C), Color(argb: 0xFF7B1FA2), Color(argb: 0xFFAB47BC)]))
    // Wooden floor
    d.drawRect(Color(argb: 0xFFBF8A5A), CGPoint(0, h * 0.78), CGSize(w, h * 0.22))
    for i in 1...3 { d.drawLine(Color(argb: 0xFF9C6B40), CGPoint(0, h * (0.78 + CGFloat(i) * 0.055)), CGPoint(w, h * (0.78 + CGFloat(i) * 0.055)), 3) }
    d.drawRect(Color(argb: 0xFF8D5A32), CGPoint(0, h * 0.775), CGSize(w, h * 0.012))
    // Curtains
    let red = Color(argb: 0xFFE53935), dark = Color(argb: 0xFFB71C1C)
    for side in 0...1 {
        let x0 = side == 0 ? 0 : w * 0.88
        d.drawRect(red, CGPoint(x0, 0), CGSize(w * 0.12, h))
        for k in 0...3 {
            let x = x0 + w * 0.12 * (CGFloat(k) + 0.5) / 4
            d.drawLine(dark, CGPoint(x, 0), CGPoint(x, h), w * 0.006)
        }
    }
    // Valance with scallops
    d.drawRect(red, .zero, CGSize(w, h * 0.07))
    let scallops = 14
    for k in 0..<scallops {
        let cx = w * (CGFloat(k) + 0.5) / CGFloat(scallops)
        d.drawCircle(red, w / CGFloat(scallops) / 2, CGPoint(cx, h * 0.07))
    }
    d.drawRect(Color(argb: 0xFFFFD54F), CGPoint(0, h * 0.065), CGSize(w, h * 0.008))
}

/** A cosy room up a tree: wooden planks, a round window onto the leaves, and a rug. */
private func drawTreehouse(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let floor = h * 0.78
    d.drawRect(Color(argb: 0xFFD9A873), .zero, CGSize(w, floor))
    var y = h * 0.11
    while y < floor {
        d.drawLine(Color(argb: 0xFFB98352), CGPoint(0, y), CGPoint(w, y), h * 0.006)
        y += h * 0.11
    }
    d.drawRect(Color(argb: 0xFFA9774A), CGPoint(0, floor), CGSize(w, h - floor))
    d.drawRect(Color(argb: 0xFF8D6240), CGPoint(0, floor), CGSize(w, h * 0.014))
    // The window, full of leaves
    let r = h * 0.2
    let c = CGPoint(max(w * 0.12, r * 1.25), h * 0.36)
    d.drawCircle(Color(argb: 0xFF8D6240), r * 1.12, c)
    d.drawCircle(Color(argb: 0xFFB3E5FC), r, c)
    for (dx, dy, s) in [(-0.5, 0.5, 0.55), (0.45, 0.55, 0.6), (0, 0.75, 0.5), (0.62, -0.2, 0.38)] as [(CGFloat, CGFloat, CGFloat)] {
        d.drawCircle(Color(argb: 0xFF66BB6A), r * s, c + CGPoint(r * dx * 0.8, r * dy * 0.8))
    }
    d.drawLine(Color(argb: 0xFF8D6240), c - CGPoint(r, 0), c + CGPoint(r, 0), h * 0.012)
    d.drawLine(Color(argb: 0xFF8D6240), c - CGPoint(0, r), c + CGPoint(0, r), h * 0.012)
    // A rug under Puki and a little shelf
    d.drawOval(Color(argb: 0xFFEF9A9A), CGPoint(w * 0.28, h * 0.84), CGSize(w * 0.44, h * 0.12))
    d.drawOval(Color(argb: 0xFFFFCDD2), CGPoint(w * 0.32, h * 0.855), CGSize(w * 0.36, h * 0.09))
    d.drawRoundRect(Color(argb: 0xFF8D6240), CGPoint(w * 0.72, h * 0.34), CGSize(w * 0.2, h * 0.02), h * 0.01)
    for (i, color) in [Color(argb: 0xFFEF5350), Color(argb: 0xFF42A5F5), Color(argb: 0xFFFFCA28)].enumerated() {
        let fi = CGFloat(i)
        d.drawRoundRect(color, CGPoint(w * (0.75 + fi * 0.04), h * (0.26 - CGFloat(i % 2) * 0.02)), CGSize(w * 0.03, h * (0.08 + CGFloat(i % 2) * 0.02)), h * 0.006)
    }
}

private func drawRoom(_ d: DrawScope, wall: Color, stripe: Color, floor: Color, tiles: Bool = false) {
    let w = d.size.width, h = d.size.height
    d.drawRect(wall)
    if tiles {
        let s = h * 0.09
        var y: CGFloat = 0
        while y < h * 0.74 {
            var x: CGFloat = 0
            while x < w {
                d.drawRect(stripe, CGPoint(x + 2, y + 2), CGSize(s - 4, s - 4))
                x += s
            }
            y += s
        }
    } else {
        var x: CGFloat = 0
        while x < w {
            d.drawRect(stripe, CGPoint(x, 0), CGSize(w * 0.025, h * 0.74))
            x += w * 0.06
        }
    }
    // Floor and skirting
    d.drawRect(floor, CGPoint(0, h * 0.74), CGSize(w, h * 0.26))
    let plank = floor.scaled(0.85)
    for i in 1...3 { d.drawLine(plank, CGPoint(0, h * (0.74 + CGFloat(i) * 0.065)), CGPoint(w, h * (0.74 + CGFloat(i) * 0.065)), 3) }
    d.drawRect(Color.white, CGPoint(0, h * 0.725), CGSize(w, h * 0.018))
}
