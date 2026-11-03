import SwiftUI

/*
 * The toddler of My Day (getting dressed, bath time), drawn to fill a square: a big round head, a round tummy and
 * chubby arms and legs, with hair for a girl or a boy. Her clothes are drawn on the same body, so they fit.
 * Everything is in fractions of the square, and the picture doesn't mirror in Hebrew.
 */

private let skin = Color(argb: 0xFFFFD1A6)
private let skinShade = Color(argb: 0xFFF5B98A)
private let hairColor = Color(argb: 0xFF8D5524)
private let ink = Color(argb: 0xFF4E342E)

// The body's joints
private let headC = CGPoint(0.5, 0.25)
private let headR: CGFloat = 0.135
private let shoulderL = CGPoint(0.385, 0.43), shoulderR = CGPoint(0.615, 0.43)
private let handL = CGPoint(0.25, 0.6), handR = CGPoint(0.75, 0.6)
private let hipL = CGPoint(0.445, 0.64), hipR = CGPoint(0.555, 0.64)
private let ankleL = CGPoint(0.425, 0.855), ankleR = CGPoint(0.575, 0.855)
private let armW: CGFloat = 0.075
private let legW: CGFloat = 0.092

extension DrawScope {
    /** A point in fractions of the (square) picture. */
    func p(_ o: CGPoint) -> CGPoint { CGPoint(o.x * size.width, o.y * size.width) }
    /** A length in fractions of the picture. */
    func u(_ f: CGFloat) -> CGFloat { f * size.width }
}

private func mix(_ a: CGPoint, _ b: CGPoint, _ t: CGFloat) -> CGPoint { a + (b - a) * t }

/**
 * The child. `worn` says how far each piece of clothing has appeared (0 = not on, 1 = on), so a piece can grow
 * into place as it's put on. "pajamas" is both pajama pieces at once.
 */
func drawToddler(_ d: DrawScope, girl: Bool, worn: [String: CGFloat] = [:]) {
    func on(_ id: String) -> CGFloat { worn[id] ?? 0 }
    d.drawOval(Color.black.copy(alpha: 0.10), CGPoint(d.u(0.3), d.u(0.9)), CGSize(d.u(0.4), d.u(0.05)))
    if girl { drawPigtails(d) }
    drawBody(d)
    drawHead(d, girl)
    piece(d, on("underwear"), CGPoint(0.5, 0.63), drawUnderwear)
    piece(d, on("pants"), CGPoint(0.5, 0.72), drawPants)
    piece(d, on("shorts"), CGPoint(0.5, 0.66), drawShorts)
    piece(d, on("pajama_pants"), CGPoint(0.5, 0.72), drawPajamaPants)
    piece(d, on("shirt"), CGPoint(0.5, 0.5), drawShirt)
    piece(d, on("pajama_top"), CGPoint(0.5, 0.5), drawPajamaTop)
    piece(d, on("raincoat"), CGPoint(0.5, 0.55), drawRaincoat)
    piece(d, on("socks"), CGPoint(0.5, 0.87), drawSocks)
    piece(d, on("shoes"), CGPoint(0.5, 0.88), drawShoes)
    piece(d, on("boots"), CGPoint(0.5, 0.86), drawBoots)
    piece(d, on("slippers"), CGPoint(0.5, 0.88), drawSlippers)
    piece(d, on("hat"), CGPoint(0.5, 0.15), drawSunHat)
    piece(d, on("pajamas"), CGPoint(0.5, 0.6)) { drawPajamaPants($0); drawPajamaTop($0) }
}

/** Just her head, eyes shut, for a child asleep on a pillow: in the same square as drawToddler. */
func drawSleepingHead(_ d: DrawScope, girl: Bool) {
    if girl { drawPigtails(d) }
    drawHead(d, girl, asleep: true)
}

/** A piece of clothing growing into place around `center` as `t` goes 0 → 1. */
private func piece(_ d: DrawScope, _ t: CGFloat, _ center: CGPoint, _ draw: (DrawScope) -> Void) {
    if t <= 0 { return }
    let s = 0.6 + 0.4 * min(t, 1.15)
    d.scale(s, s, pivot: d.p(center), draw)
}

private func limb(_ d: DrawScope, _ from: CGPoint, _ to: CGPoint, _ width: CGFloat, _ color: Color) {
    d.drawLine(color, d.p(from), d.p(to), d.u(width), .round)
}

private func drawBody(_ d: DrawScope) {
    for (s, h) in [(shoulderL, handL), (shoulderR, handR)] {
        limb(d, s, h, armW, skin)
        d.drawCircle(skin, d.u(0.043), d.p(h))
        d.drawCircle(skinShade.copy(alpha: 0.5), d.u(0.043), d.p(h), style: Stroke(width: d.u(0.006)))
    }
    for (hip, ankle, dir) in [(hipL, ankleL, -1.0), (hipR, ankleR, 1.0)] as [(CGPoint, CGPoint, CGFloat)] {
        limb(d, hip, ankle, legW, skin)
        d.drawOval(skin, d.p(ankle) + CGPoint(d.u(-0.055 + dir * 0.012), d.u(-0.005)), CGSize(d.u(0.11), d.u(0.055)))
    }
    d.drawRoundRect(skin, CGPoint(d.u(0.36), d.u(0.37)), CGSize(d.u(0.28), d.u(0.31)), d.u(0.12))
    d.drawCircle(skinShade, d.u(0.008), d.p(CGPoint(0.5, 0.58)))
}

private func drawHead(_ d: DrawScope, _ girl: Bool, asleep: Bool = false) {
    let c = d.p(headC)
    let r = d.u(headR)
    for dx in [-1.0, 1.0] as [CGFloat] { d.drawCircle(skin, d.u(0.026), c + CGPoint(dx * r * 0.98, d.u(0.012))) }
    d.drawCircle(skin, r, c)
    let hair = Path { p in
        p.moveTo(c.x - r * 1.02, c.y + r * 0.05)
        p.cubicTo(c.x - r * 1.12, c.y - r * 1.42, c.x + r * 1.12, c.y - r * 1.42, c.x + r * 1.02, c.y + r * 0.05)
        p.quadraticBezierTo(c.x + r * 0.85, c.y - r * 0.45, c.x + r * 0.35, c.y - r * 0.42)
        p.quadraticBezierTo(c.x + r * 0.15, c.y - r * 0.2, c.x - r * 0.05, c.y - r * 0.45)
        p.quadraticBezierTo(c.x - r * 0.35, c.y - r * 0.25, c.x - r * 0.55, c.y - r * 0.45)
        p.quadraticBezierTo(c.x - r * 0.9, c.y - r * 0.4, c.x - r * 1.02, c.y + r * 0.05)
        p.close()
    }
    d.drawPath(hair, hairColor)
    d.drawArc(Color.white.copy(alpha: 0.18), startAngle: 200, sweepAngle: 60, useCenter: false, c - CGPoint(r * 0.8, r * 0.85), CGSize(r * 1.6, r * 1.2),
              style: Stroke(width: r * 0.1, cap: .round))
    if girl {
        d.drawCircle(Color(argb: 0xFFEC407A), r * 0.13, c + CGPoint(r * 0.55, -r * 0.62))
        d.drawCircle(Color.white.copy(alpha: 0.5), r * 0.05, c + CGPoint(r * 0.51, -r * 0.66))
    }
    for dx in [-0.36, 0.36] as [CGFloat] {
        let e = c + CGPoint(dx * r, r * 0.05)
        if asleep {
            d.drawArc(ink, startAngle: 20, sweepAngle: 140, useCenter: false, e - CGPoint(r * 0.14, r * 0.08), CGSize(r * 0.28, r * 0.16),
                      style: Stroke(width: r * 0.07, cap: .round))
            continue
        }
        d.drawOval(ink, e - CGPoint(r * 0.1, r * 0.13), CGSize(r * 0.2, r * 0.26))
        d.drawCircle(.white, r * 0.05, e + CGPoint(-r * 0.03, -r * 0.05))
    }
    for dx in [-0.6, 0.6] as [CGFloat] { d.drawOval(Color(argb: 0x66FF8A80), c + CGPoint(dx * r - r * 0.15, r * 0.3), CGSize(r * 0.3, r * 0.18)) }
    d.drawArc(ink, startAngle: 20, sweepAngle: 140, useCenter: false, c + CGPoint(-r * 0.28, r * 0.12), CGSize(r * 0.56, r * 0.42),
              style: Stroke(width: r * 0.08, cap: .round))
}

private func drawPigtails(_ d: DrawScope) {
    let c = d.p(headC)
    let r = d.u(headR)
    for dx in [-1.0, 1.0] as [CGFloat] {
        d.drawCircle(hairColor, r * 0.38, c + CGPoint(dx * r * 1.12, -r * 0.25))
        d.drawCircle(Color(argb: 0xFFEC407A), r * 0.12, c + CGPoint(dx * r * 0.88, -r * 0.35))
    }
}

// ---------- Clothes ----------

private func drawUnderwear(_ d: DrawScope) {
    let shape = Path { p in
        p.moveTo(d.u(0.37), d.u(0.585)); p.lineTo(d.u(0.63), d.u(0.585)); p.lineTo(d.u(0.625), d.u(0.675))
        p.quadraticBezierTo(d.u(0.565), d.u(0.66), d.u(0.53), d.u(0.7)); p.lineTo(d.u(0.47), d.u(0.7))
        p.quadraticBezierTo(d.u(0.435), d.u(0.66), d.u(0.375), d.u(0.675)); p.close()
    }
    d.drawPath(shape, Color(argb: 0xFF81D4FA))
    d.drawRoundRect(Color(argb: 0xFF29B6F6), CGPoint(d.u(0.365), d.u(0.578)), CGSize(d.u(0.27), d.u(0.025)), d.u(0.012))
    for (x, y) in [(0.43, 0.635), (0.5, 0.65), (0.57, 0.635)] as [(CGFloat, CGFloat)] { d.drawCircle(.white, d.u(0.01), d.p(CGPoint(x, y))) }
}

private let pajamaColor = Color(argb: 0xFFB39DDB)

private func drawPajamaPants(_ d: DrawScope) {
    for (hip, ankle) in [(hipL, ankleL), (hipR, ankleR)] { limb(d, hip, mix(hip, ankle, 0.95), legW + 0.03, pajamaColor) }
    d.drawRoundRect(pajamaColor, CGPoint(d.u(0.35), d.u(0.56)), CGSize(d.u(0.3), d.u(0.135)), d.u(0.06))
    for (x, y) in [(0.42, 0.76), (0.58, 0.8)] as [(CGFloat, CGFloat)] { d.drawPath(starPath(d.p(CGPoint(x, y)), d.u(0.016)), Color(argb: 0xFFFFF59D)) }
}

private func drawPajamaTop(_ d: DrawScope) {
    let dark = lerp(pajamaColor, .black, 0.18)
    for (s, h) in [(shoulderL, handL), (shoulderR, handR)] { limb(d, s, mix(s, h, 0.88), armW + 0.03, pajamaColor) }
    d.drawRoundRect(pajamaColor, CGPoint(d.u(0.35), d.u(0.375)), CGSize(d.u(0.3), d.u(0.27)), d.u(0.09))
    d.drawArc(dark, startAngle: 0, sweepAngle: 180, useCenter: false, CGPoint(d.u(0.45), d.u(0.36)), CGSize(d.u(0.1), d.u(0.05)), style: Stroke(width: d.u(0.012), cap: .round))
    for y in [0.45, 0.53, 0.61] as [CGFloat] { d.drawCircle(Color(argb: 0xFFFFF59D), d.u(0.012), d.p(CGPoint(0.5, y))) }
    for (x, y) in [(0.41, 0.5), (0.59, 0.57)] as [(CGFloat, CGFloat)] { d.drawPath(starPath(d.p(CGPoint(x, y)), d.u(0.016)), Color(argb: 0xFFFFF59D)) }
}

/** A yellow raincoat down to her knees: long sleeves, big buttons, the hood resting behind her neck. */
private func drawRaincoat(_ d: DrawScope) {
    let color = Color(argb: 0xFFFFCA28), dark = Color(argb: 0xFFF9A825)
    for (s, h) in [(shoulderL, handL), (shoulderR, handR)] { limb(d, s, mix(s, h, 0.88), armW + 0.034, color) }
    let coat = Path { p in
        p.moveTo(d.u(0.42), d.u(0.375)); p.lineTo(d.u(0.58), d.u(0.375))
        p.quadraticBezierTo(d.u(0.66), d.u(0.385), d.u(0.665), d.u(0.47)); p.lineTo(d.u(0.695), d.u(0.71))
        p.quadraticBezierTo(d.u(0.7), d.u(0.75), d.u(0.66), d.u(0.75)); p.lineTo(d.u(0.34), d.u(0.75))
        p.quadraticBezierTo(d.u(0.3), d.u(0.75), d.u(0.305), d.u(0.71)); p.lineTo(d.u(0.335), d.u(0.47))
        p.quadraticBezierTo(d.u(0.34), d.u(0.385), d.u(0.42), d.u(0.375)); p.close()
    }
    d.drawPath(coat, color)
    d.drawLine(dark, CGPoint(d.u(0.32), d.u(0.725)), CGPoint(d.u(0.68), d.u(0.725)), d.u(0.014), .round)
    d.drawLine(dark, CGPoint(d.u(0.5), d.u(0.41)), CGPoint(d.u(0.5), d.u(0.74)), d.u(0.012), .round)
    for y in [0.46, 0.54, 0.62] as [CGFloat] {
        d.drawCircle(Color(argb: 0xFF42A5F5), d.u(0.017), d.p(CGPoint(0.53, y)))
        d.drawCircle(Color.white.copy(alpha: 0.5), d.u(0.006), d.p(CGPoint(0.525, y - 0.005)))
    }
    for x in [0.36, 0.56] as [CGFloat] { d.drawRoundRect(dark, CGPoint(d.u(x), d.u(0.635)), CGSize(d.u(0.08), d.u(0.012)), d.u(0.006)) }
    d.drawArc(dark, startAngle: 0, sweepAngle: 180, useCenter: false, CGPoint(d.u(0.415), d.u(0.325)), CGSize(d.u(0.17), d.u(0.095)), style: Stroke(width: d.u(0.03), cap: .round))
    d.drawLine(Color.white.copy(alpha: 0.45), CGPoint(d.u(0.365), d.u(0.47)), CGPoint(d.u(0.35), d.u(0.6)), d.u(0.012), .round)
}

private func drawShirt(_ d: DrawScope) {
    let color = Color(argb: 0xFF42A5F5)
    let dark = lerp(color, .black, 0.2)
    for (s, h) in [(shoulderL, handL), (shoulderR, handR)] { limb(d, s, mix(s, h, 0.42), armW + 0.03, color) }
    d.drawRoundRect(color, CGPoint(d.u(0.35), d.u(0.375)), CGSize(d.u(0.3), d.u(0.25)), d.u(0.09))
    d.drawRoundRect(dark, CGPoint(d.u(0.35), d.u(0.595)), CGSize(d.u(0.3), d.u(0.03)), d.u(0.015))
    d.drawArc(dark, startAngle: 0, sweepAngle: 180, useCenter: false, CGPoint(d.u(0.45), d.u(0.36)), CGSize(d.u(0.1), d.u(0.05)), style: Stroke(width: d.u(0.012), cap: .round))
    d.drawPath(starPath(d.p(CGPoint(0.5, 0.5)), d.u(0.045)), Color(argb: 0xFFFFEE58))
}

private func drawPants(_ d: DrawScope) {
    let color = Color(argb: 0xFF1E88E5), light = Color(argb: 0xFF64B5F6)
    for (hip, ankle) in [(hipL, ankleL), (hipR, ankleR)] {
        let cuff = mix(hip, ankle, 0.86)
        limb(d, hip, cuff, legW + 0.022, color)
        d.drawLine(light, d.p(cuff) - CGPoint(d.u(0.05), 0), d.p(cuff) + CGPoint(d.u(0.05), 0), d.u(0.016), .round)
    }
    d.drawRoundRect(color, CGPoint(d.u(0.37), d.u(0.58)), CGSize(d.u(0.26), d.u(0.11)), d.u(0.04))
    d.drawRoundRect(Color(argb: 0xFF1565C0), CGPoint(d.u(0.365), d.u(0.578)), CGSize(d.u(0.27), d.u(0.028)), d.u(0.014))
    d.drawCircle(Color(argb: 0xFFFFCA28), d.u(0.011), d.p(CGPoint(0.5, 0.592)))
}

private func drawShorts(_ d: DrawScope) {
    let color = Color(argb: 0xFFFF8A65), light = Color(argb: 0xFFFFCCBC)
    for (hip, ankle) in [(hipL, ankleL), (hipR, ankleR)] {
        let cuff = mix(hip, ankle, 0.42)
        limb(d, hip, cuff, legW + 0.03, color)
        d.drawLine(light, d.p(cuff) - CGPoint(d.u(0.052), 0), d.p(cuff) + CGPoint(d.u(0.052), 0), d.u(0.016), .round)
    }
    d.drawRoundRect(color, CGPoint(d.u(0.365), d.u(0.58)), CGSize(d.u(0.27), d.u(0.11)), d.u(0.04))
    d.drawRoundRect(Color(argb: 0xFFF4511E), CGPoint(d.u(0.365), d.u(0.578)), CGSize(d.u(0.27), d.u(0.028)), d.u(0.014))
    d.drawCircle(Color(argb: 0xFFFFCA28), d.u(0.011), d.p(CGPoint(0.5, 0.592)))
}

private func drawSocks(_ d: DrawScope) {
    for (ankle, dir) in [(ankleL, -1.0), (ankleR, 1.0)] as [(CGPoint, CGFloat)] {
        let a = d.p(ankle)
        d.drawRoundRect(.white, a - CGPoint(d.u(0.05), d.u(0.06)), CGSize(d.u(0.1), d.u(0.075)), d.u(0.03))
        for k in 0...1 { d.drawRect(Color(argb: 0xFFEF5350), a + CGPoint(d.u(-0.05), d.u(-0.05 + CGFloat(k) * 0.025)), CGSize(d.u(0.1), d.u(0.011))) }
        d.drawOval(.white, a + CGPoint(d.u(-0.06 + dir * 0.012), d.u(-0.01)), CGSize(d.u(0.12), d.u(0.064)))
        d.drawOval(Color(argb: 0xFFEF5350), a + CGPoint(d.u(dir * 0.03), 0), CGSize(d.u(0.035), d.u(0.05)))
    }
}

private func drawShoes(_ d: DrawScope) {
    for (ankle, dir) in [(ankleL, -1.0), (ankleR, 1.0)] as [(CGPoint, CGFloat)] {
        let a = d.p(ankle)
        let left = a.x + d.u(-0.068 + dir * 0.014)
        let w = d.u(0.136)
        let top = Path { p in
            p.moveTo(left, a.y + d.u(0.045)); p.lineTo(left, a.y)
            p.quadraticBezierTo(left + w * 0.5, a.y - d.u(0.04), left + w, a.y + d.u(0.015)); p.lineTo(left + w, a.y + d.u(0.045)); p.close()
        }
        d.drawPath(top, Color(argb: 0xFFE53935))
        d.drawRoundRect(.white, CGPoint(left - d.u(0.004), a.y + d.u(0.038)), CGSize(w + d.u(0.008), d.u(0.024)), d.u(0.012))
        for k in 0...1 {
            let lx = left + w * (0.35 + CGFloat(k) * 0.18)
            d.drawLine(.white, CGPoint(lx, a.y - d.u(0.008)), CGPoint(lx + w * 0.1, a.y + d.u(0.012)), d.u(0.008), .round)
        }
    }
}

private func drawBoots(_ d: DrawScope) {
    let color = Color(argb: 0xFF66BB6A), dark = Color(argb: 0xFF388E3C)
    for (ankle, dir) in [(ankleL, -1.0), (ankleR, 1.0)] as [(CGPoint, CGFloat)] {
        let a = d.p(ankle)
        let left = a.x + d.u(-0.068 + dir * 0.014)
        let w = d.u(0.136)
        d.drawRoundRect(color, a + CGPoint(d.u(-0.056), d.u(-0.085)), CGSize(d.u(0.112), d.u(0.125)), d.u(0.02))
        d.drawOval(color, CGPoint(left, a.y - d.u(0.012)), CGSize(w, d.u(0.066)))
        d.drawRoundRect(dark, CGPoint(left - d.u(0.004), a.y + d.u(0.04)), CGSize(w + d.u(0.008), d.u(0.022)), d.u(0.011))
        d.drawRoundRect(Color(argb: 0xFFA5D6A7), a + CGPoint(d.u(-0.06), d.u(-0.095)), CGSize(d.u(0.12), d.u(0.024)), d.u(0.012))
        d.drawLine(Color.white.copy(alpha: 0.45), a + CGPoint(d.u(-0.034), d.u(-0.06)), a + CGPoint(d.u(-0.034), d.u(-0.012)), d.u(0.01), .round)
    }
}

private func drawSlippers(_ d: DrawScope) {
    for (ankle, dir) in [(ankleL, -1.0), (ankleR, 1.0)] as [(CGPoint, CGFloat)] {
        let a = d.p(ankle)
        let left = a.x + d.u(-0.068 + dir * 0.014)
        let w = d.u(0.136)
        d.drawOval(Color(argb: 0xFFF48FB1), CGPoint(left, a.y - d.u(0.012)), CGSize(w, d.u(0.07)))
        d.drawRoundRect(Color(argb: 0xFFEC407A), CGPoint(left, a.y + d.u(0.04)), CGSize(w, d.u(0.018)), d.u(0.009))
        d.drawCircle(.white, d.u(0.024), CGPoint(left + w * 0.5, a.y + d.u(0.008)))
        d.drawCircle(Color(argb: 0xFFFCE4EC), d.u(0.01), CGPoint(left + w * 0.5 - d.u(0.006), a.y + d.u(0.003)))
    }
}

/** A straw sun hat with a wide brim and a red ribbon, on top of her hair. */
private func drawSunHat(_ d: DrawScope) {
    let straw = Color(argb: 0xFFFFE082), dark = Color(argb: 0xFFFFCA28)
    d.drawOval(straw, CGPoint(d.u(0.285), d.u(0.125)), CGSize(d.u(0.43), d.u(0.075)))
    d.drawArc(dark, startAngle: 20, sweepAngle: 140, useCenter: false, CGPoint(d.u(0.3), d.u(0.125)), CGSize(d.u(0.4), d.u(0.07)), style: Stroke(width: d.u(0.008), cap: .round))
    let crown = Path { p in p.moveTo(d.u(0.385), d.u(0.16)); p.cubicTo(d.u(0.385), d.u(0.005), d.u(0.615), d.u(0.005), d.u(0.615), d.u(0.16)); p.close() }
    d.drawPath(crown, straw)
    d.drawRoundRect(Color(argb: 0xFFEF5350), CGPoint(d.u(0.383), d.u(0.125)), CGSize(d.u(0.234), d.u(0.036)), d.u(0.012))
    d.drawArc(Color.white.copy(alpha: 0.5), startAngle: 200, sweepAngle: 60, useCenter: false, CGPoint(d.u(0.41), d.u(0.06)), CGSize(d.u(0.13), d.u(0.09)), style: Stroke(width: d.u(0.01), cap: .round))
}

// ---------- Bath time ----------

/** A splat of mud at `c` (fractions), `amount` of it left. Lumpy, with a drip running down. */
func drawMudSplat(_ d: DrawScope, _ c: CGPoint, _ r: CGFloat, _ seed: Int, amount: CGFloat = 1) {
    if amount <= 0 { return }
    let center = d.p(c)
    let rr = d.u(r)
    let mud = Color(argb: 0xFF6D4C41).copy(alpha: Double(0.92 * amount))
    var rnd = SeededRandom(seed)
    d.drawCircle(mud, rr * 0.62, center)
    for k in 0..<6 {
        let a = CGFloat(k) / 6 * 2 * .pi + rnd.nextFloat()
        let dd = rr * (0.45 + rnd.nextFloat() * 0.25)
        d.drawCircle(mud, rr * (0.22 + rnd.nextFloat() * 0.16), center + CGPoint(cos(a) * dd, sin(a) * dd))
    }
    d.drawLine(mud, center + CGPoint(rr * 0.15, rr * 0.3), center + CGPoint(rr * 0.15, rr * 1.0), rr * 0.22, .round)
    d.drawCircle(Color(argb: 0xFF8D6E63).copy(alpha: Double(0.8 * amount)), rr * 0.2, center + CGPoint(-rr * 0.25, -rr * 0.22))
}

/** A drop of water left on her after the rinse, at `c` (fractions), `left` of it left. */
func drawWaterDrop(_ d: DrawScope, _ c: CGPoint, _ left: CGFloat) {
    let center = d.p(c)
    let r = d.u(0.022) * (0.5 + 0.5 * left)
    let drop = Path { p in
        p.moveTo(center.x, center.y - r * 1.8)
        p.cubicTo(center.x + r * 1.2, center.y - r * 0.2, center.x + r, center.y + r, center.x, center.y + r)
        p.cubicTo(center.x - r, center.y + r, center.x - r * 1.2, center.y - r * 0.2, center.x, center.y - r * 1.8)
        p.close()
    }
    d.drawPath(drop, Color(argb: 0xFF4FC3F7).copy(alpha: Double(left)))
    d.drawCircle(Color.white.copy(alpha: Double(0.7 * left)), r * 0.3, center + CGPoint(-r * 0.3, -r * 0.1))
}

/** Soap bubbles where the mud was, drifting up a little with `wave`; `left` fades them as the rinse washes them off. */
func drawSoapBubbles(_ d: DrawScope, _ c: CGPoint, _ r: CGFloat, _ wave: CGFloat, left: CGFloat = 1) {
    if left <= 0 { return }
    let center = d.p(c)
    let rr = d.u(r) * 1.45
    let bubbles: [(CGFloat, CGFloat, CGFloat)] = [(-0.4, -0.1, 0.34), (0.3, -0.35, 0.26), (0.32, 0.28, 0.34), (-0.18, 0.42, 0.22), (0, -0.05, 0.18)]
    for (i, b) in bubbles.enumerated() {
        let (dx, dy, br) = b
        let bc = center + CGPoint(dx * rr, dy * rr - (wave + CGFloat(i) * 0.2).truncatingRemainder(dividingBy: 1) * rr * 0.18)
        d.drawCircle(Color.white.copy(alpha: Double(0.75 * left)), br * rr, bc)
        d.drawCircle(Color(argb: 0xFF81D4FA).copy(alpha: Double(left)), br * rr, bc, style: Stroke(width: d.u(0.004)))
        d.drawCircle(Color.white.copy(alpha: Double(left)), br * rr * 0.32, bc + CGPoint(-br * rr * 0.35, -br * rr * 0.35))
    }
}

/** The tub in front of the child's legs, with water, foam, a rubber duck, and the shower head above. */
func drawTub(_ d: DrawScope, wave: CGFloat, fall: CGFloat, rinsing: Bool) {
    let w = d.size.width
    let top = w * 0.8
    let pipe = Color(argb: 0xFF90A4AE)
    d.drawLine(pipe, CGPoint(w * 0.8, -w * 0.05), CGPoint(w * 0.8, w * 0.012), w * 0.022, .round)
    d.drawLine(pipe, CGPoint(w * 0.8, w * 0.012), CGPoint(w * 0.5, w * 0.012), w * 0.022, .round)
    d.drawPath(Path { p in p.moveTo(w * 0.42, w * 0.075); p.quadraticBezierTo(w * 0.5, -w * 0.02, w * 0.58, w * 0.075); p.close() }, Color(argb: 0xFFB0BEC5))
    d.drawLine(Color(argb: 0xFF78909C), CGPoint(w * 0.415, w * 0.075), CGPoint(w * 0.585, w * 0.075), w * 0.016, .round)
    d.drawLine(Color.white.copy(alpha: 0.6), CGPoint(w * 0.455, w * 0.05), CGPoint(w * 0.48, w * 0.035), w * 0.008, .round)
    if rinsing {
        // A fan of water from the head down over the child to the tub
        for i in 0..<48 {
            let spread = CGFloat(i * 37 % 100) / 100 - 0.5
            let t = (fall + CGFloat(i) * 0.113).truncatingRemainder(dividingBy: 1)
            let x = w * (0.5 + spread * 0.15 + spread * 0.42 * t)
            let y = w * 0.08 + t * w * 0.74
            d.drawLine(Color(argb: 0xCC4FC3F7), CGPoint(x, y), CGPoint(x, y + w * 0.04), w * 0.011, .round)
        }
    }
    let water = Path { p in
        p.moveTo(w * 0.09, top + w * 0.02)
        var x = w * 0.09
        while x <= w * 0.91 {
            p.lineTo(x, top + w * 0.02 + sin((x / w * 6 + wave) * 2 * .pi) * w * 0.007)
            x += w * 0.02
        }
        p.lineTo(w * 0.91, w * 0.92); p.lineTo(w * 0.09, w * 0.92); p.close()
    }
    d.drawPath(water, Color(argb: 0xFF81D4FA).copy(alpha: 0.9))
    for i in 0..<11 { d.drawCircle(.white, w * (0.02 + 0.009 * CGFloat(i % 3)), CGPoint(w * (0.11 + CGFloat(i) * 0.078), top + w * 0.022)) }
    let bob = sin(wave * 2 * .pi) * w * 0.006
    let duck = CGPoint(w * 0.2, top + bob)
    d.drawOval(Color(argb: 0xFFFFD54F), duck + CGPoint(-w * 0.045, -w * 0.025), CGSize(w * 0.09, w * 0.05))
    d.drawCircle(Color(argb: 0xFFFFD54F), w * 0.025, duck + CGPoint(w * 0.025, -w * 0.04))
    d.drawOval(Color(argb: 0xFFFF8F00), duck + CGPoint(w * 0.043, -w * 0.045), CGSize(w * 0.025, w * 0.014))
    d.drawCircle(ink, w * 0.005, duck + CGPoint(w * 0.03, -w * 0.047))
    let body = Path { p in
        p.moveTo(w * 0.05, top + w * 0.03); p.lineTo(w * 0.95, top + w * 0.03)
        p.quadraticBezierTo(w * 0.94, top + w * 0.17, w * 0.82, top + w * 0.175); p.lineTo(w * 0.18, top + w * 0.175)
        p.quadraticBezierTo(w * 0.06, top + w * 0.17, w * 0.05, top + w * 0.03); p.close()
    }
    d.drawPath(body, Brush.verticalGradient([.white, Color(argb: 0xFFE3F2FD)], startY: top, endY: top + w * 0.18))
    d.drawRoundRect(Color(argb: 0xFFCFD8DC), CGPoint(w * 0.035, top + w * 0.012), CGSize(w * 0.93, w * 0.03), w * 0.015)
    d.drawLine(Color.white.copy(alpha: 0.8), CGPoint(w * 0.15, top + w * 0.065), CGPoint(w * 0.4, top + w * 0.065), w * 0.01, .round)
    for fx in [0.2, 0.8] as [CGFloat] { d.drawOval(Color(argb: 0xFFFFC107), CGPoint(w * (fx - 0.035), top + w * 0.165), CGSize(w * 0.07, w * 0.035)) }
}

/** Wraps `draw` so only what's above the tub's water line shows (the legs go into the water). */
func aboveWater(_ d: DrawScope, _ draw: (DrawScope) -> Void) {
    d.clip(Path(CGRect(x: 0, y: 0, width: d.size.width, height: d.size.width * 0.83)), draw)
}
