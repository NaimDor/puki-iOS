import SwiftUI

/** A warm wall with soft polka dots behind the easel. */
func drawStudioWall(_ d: DrawScope) {
    d.drawRect(Brush.verticalGradient([Color(argb: 0xFFFFF1DC), Color(argb: 0xFFFFE2BF)]))
    let dot = Color(argb: 0x22FF8A65)
    let step: CGFloat = 70
    var row = 0
    var y = step / 2
    while y < d.size.height {
        var x = row % 2 == 0 ? step / 2 : step
        while x < d.size.width {
            d.drawCircle(dot, 6, CGPoint(x, y))
            x += step
        }
        y += step * 0.75
        row += 1
    }
    // Floorboards along the bottom
    let floorTop = d.size.height * 0.93
    d.drawRect(Color(argb: 0xFFD7A86E), CGPoint(0, floorTop), CGSize(d.size.width, d.size.height - floorTop))
    d.drawRect(Color(argb: 0xFFBF8A55), CGPoint(0, floorTop), CGSize(d.size.width, 4))
}

/** Very faint fibres so the page reads as paper rather than a white screen. */
func drawPaperGrain(_ d: DrawScope) {
    let fibre = Color(argb: 0x068D6E63)
    let step: CGFloat = 22
    var y = step
    var i = 0
    while y < d.size.height {
        let wobble = sin(CGFloat(i) * 1.7) * 3
        d.drawLine(fibre, CGPoint(0, y + wobble), CGPoint(d.size.width, y - wobble), 1)
        y += step
        i += 1
    }
}

func drawTape(_ d: DrawScope) {
    let tape = Color(argb: 0xAAFFF59D)
    let w: CGFloat = 110, h: CGFloat = 34
    let pieces: [(CGPoint, Double, Color)] = [
        (CGPoint(30, 26), -35, tape),
        (CGPoint(d.size.width - 30, 26), 35, Color(argb: 0xAAB3E5FC)),
        (CGPoint(30, d.size.height - 26), 35, Color(argb: 0xAAF8BBD0)),
        (CGPoint(d.size.width - 30, d.size.height - 26), -35, tape),
    ]
    for (c, angle, color) in pieces {
        d.rotate(angle, pivot: c) { r in
            r.drawRect(color, CGPoint(c.x - w / 2, c.y - h / 2), CGSize(w, h))
            // Zig-zag torn ends
            for end in [-1, 1] as [CGFloat] {
                let x = c.x + end * w / 2
                var y = c.y - h / 2
                let tooth = h / 5
                while y < c.y + h / 2 - 1 {
                    let tri = Path { p in p.moveTo(x, y); p.lineTo(x + end * tooth * 0.6, y + tooth / 2); p.lineTo(x, y + tooth); p.close() }
                    r.drawPath(tri, color)
                    y += tooth
                }
            }
        }
    }
}

func drawPaintPot(_ d: DrawScope, _ color: Color, _ lift: CGFloat) {
    let w = d.size.width, h = d.size.height
    let dark = color.scaled(0.8)
    // Shadow on the "table"
    d.drawOval(Color(argb: 0x33000000), CGPoint(w * 0.2, h * 0.88), CGSize(w * 0.6, h * 0.1))

    // A brush standing in the chosen pot
    if lift > 0.01 {
        d.rotate(18, pivot: CGPoint(w * 0.5, h * 0.45)) { r in
            let top = h * (0.42 - 0.42 * lift)
            r.drawLine(Color(argb: 0xFFA1683A), CGPoint(w * 0.5, top), CGPoint(w * 0.5, h * 0.5), w * 0.07, .round)
            r.drawRoundRect(Color(argb: 0xFFB0BEC5), CGPoint(w * 0.46, h * 0.38), CGSize(w * 0.08, h * 0.1), 3)
        }
    }

    // Pot body: a white ceramic jar with paint dribbling down and a coloured band
    let bodyTop = h * 0.36, bodyLeft = w * 0.18, bodyW = w * 0.64
    d.drawRoundRect(Color(argb: 0xFFFAFAFA), CGPoint(bodyLeft, bodyTop), CGSize(bodyW, h * 0.54), w * 0.12)
    // Paint pooled at the bottom, and drips running down from the rim
    d.drawRoundRect(color.copy(alpha: 0.35), CGPoint(bodyLeft, h * 0.76), CGSize(bodyW, h * 0.14), w * 0.12)
    for (fx, len) in [(0.3, 0.3), (0.5, 0.4), (0.68, 0.2)] as [(CGFloat, CGFloat)] {
        let x = w * fx
        d.drawLine(color, CGPoint(x, bodyTop), CGPoint(x, bodyTop + h * len), w * 0.07, .round)
        d.drawCircle(color, w * 0.05, CGPoint(x, bodyTop + h * len))
    }
    // Rim and the paint inside
    d.drawOval(Color(argb: 0xFFE0E0E0), CGPoint(w * 0.14, h * 0.27), CGSize(w * 0.72, h * 0.18))
    d.drawOval(color, CGPoint(w * 0.19, h * 0.29), CGSize(w * 0.62, h * 0.13))
    d.drawOval(dark.copy(alpha: 0.35), CGPoint(w * 0.3, h * 0.33), CGSize(w * 0.4, h * 0.06))
    // Shine
    d.drawRoundRect(Color.white.copy(alpha: 0.7), CGPoint(bodyLeft + w * 0.06, h * 0.48), CGSize(w * 0.06, h * 0.3), w * 0.03)
}

/** A paintbrush lying diagonally, its tip dipped in `color`. */
func drawPaintBrush(_ d: DrawScope, _ color: Color, fat: Bool) {
    let w = d.size.width, h = d.size.height
    d.rotate(-45, pivot: CGPoint(w / 2, h / 2)) { r in
        let cx = w / 2
        let handleW = fat ? w * 0.13 : w * 0.09
        r.drawLine(Color(argb: 0xFFA1683A), CGPoint(cx, h * 0.92), CGPoint(cx, h * 0.5), handleW, .round)
        let ferruleW = fat ? w * 0.22 : w * 0.13
        r.drawRoundRect(Color(argb: 0xFFB0BEC5), CGPoint(cx - ferruleW / 2, h * 0.38), CGSize(ferruleW, h * 0.14), 4)
        let tipW = fat ? w * 0.3 : w * 0.15
        let tip = Path { p in
            p.moveTo(cx - tipW / 2, h * 0.39)
            p.cubicTo(cx - tipW / 2, h * 0.2, cx, h * 0.12, cx, h * 0.04)
            p.cubicTo(cx, h * 0.12, cx + tipW / 2, h * 0.2, cx + tipW / 2, h * 0.39)
            p.close()
        }
        r.drawPath(tip, color)
        r.drawLine(Color.white.copy(alpha: 0.5), CGPoint(cx - tipW * 0.15, h * 0.34), CGPoint(cx - tipW * 0.05, h * 0.16), w * 0.025, .round)
    }
}

/** A pink rubber eraser with a blue paper sleeve, lying at a slant. */
func drawEraser(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.rotate(-30, pivot: CGPoint(w / 2, h / 2)) { r in
        let top = h * 0.3, bodyH = h * 0.4
        r.drawRoundRect(Color(argb: 0xFFF48FB1), CGPoint(w * 0.08, top), CGSize(w * 0.84, bodyH), w * 0.08)
        r.drawRect(Color(argb: 0xFF64B5F6), CGPoint(w * 0.5, top), CGSize(w * 0.3, bodyH))
        r.drawRoundRect(Color.white.copy(alpha: 0.45), CGPoint(w * 0.14, top + bodyH * 0.15), CGSize(w * 0.28, bodyH * 0.18), w * 0.04)
    }
}

/** A little blue camera with a big lens, for the button that saves the drawing. */
func drawCamera(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0xFF455A64), CGPoint(w * 0.32, h * 0.2), CGSize(w * 0.36, h * 0.18), w * 0.05)
    d.drawRoundRect(Color(argb: 0xFF546E7A), CGPoint(w * 0.08, h * 0.3), CGSize(w * 0.84, h * 0.54), w * 0.12)
    d.drawRoundRect(Color(argb: 0xFFFFCA28), CGPoint(w * 0.68, h * 0.36), CGSize(w * 0.14, h * 0.09), w * 0.03)
    let c = CGPoint(w * 0.5, h * 0.57)
    d.drawCircle(Color(argb: 0xFFECEFF1), w * 0.21, c)
    d.drawCircle(Color(argb: 0xFF29B6F6), w * 0.15, c)
    d.drawCircle(Color.white.copy(alpha: 0.75), w * 0.05, c - CGPoint(w * 0.05, w * 0.05))
}
