import SwiftUI
import CoreText
import UIKit

/**
 * A page to color in: shapes drawn in a 100x100 box, back to front. A tap inside a shape fills it; the outlines are
 * drawn over the paint, like a coloring book's lines.
 */
enum CanvasPage {
    case coloring(ColoringPage)
    /** A page with a big letter (or number) to paint over; each visit brings the next one. */
    case trace(numbers: Bool)
}

struct ColoringPage {
    let id: String
    let shapes: [Path]
    init(_ id: String, _ shapes: String...) { self.id = id; self.shapes = shapes.map(svgPath) }

    /** The page's shapes placed on paper of `size`: centered, as large as fits with a margin. */
    func placed(_ size: CGSize) -> [Path] {
        let scale = min(size.width, size.height) * 0.88 / 100
        let t = CGAffineTransform(translationX: (size.width - 100 * scale) / 2, y: (size.height - 100 * scale) / 2).scaledBy(x: scale, y: scale)
        return shapes.map { $0.applying(t) }
    }
}

private func circle(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat) -> String {
    "M\(cx - r),\(cy) A\(r),\(r) 0 1,0 \(cx + r),\(cy) A\(r),\(r) 0 1,0 \(cx - r),\(cy) Z"
}

/** Blank paper first, then the pages, in the order the page button goes through them. */
let coloringPages: [CanvasPage?] = [
    nil,
    .coloring(ColoringPage(
        "fish",
        "M68,50 L94,32 L90,50 L94,68 Z",                  // tail
        "M36,34 Q48,16 62,34 Z",                          // top fin
        "M14,50 A30,19 0 1,0 74,50 A30,19 0 1,0 14,50 Z", // body
        "M40,52 Q50,44 56,54 Q48,62 40,52 Z",             // side fin
        circle(28, 46, 5),                                // eye
        circle(10, 26, 3.5), circle(16, 14, 2.5)          // bubbles
    )),
    .coloring(ColoringPage(
        "flower",
        "M48.5,52 L51.5,52 L51.5,94 L48.5,94 Z",          // stem
        "M51.5,78 Q66,62 76,68 Q66,82 51.5,82 Z",         // leaf
        "M48.5,70 Q34,56 24,62 Q34,76 48.5,76 Z",         // leaf
        circle(50, 25, 12), circle(63, 34.5, 12), circle(58, 49.5, 12),
        circle(42, 49.5, 12), circle(37, 34.5, 12),       // petals
        circle(50, 38, 9)                                 // middle
    )),
    .coloring(ColoringPage(
        "house",
        circle(86, 14, 8),                                // sun
        "M24,46 L76,46 L76,88 L24,88 Z",                  // wall
        "M16,48 L50,16 L84,48 Z",                         // roof
        "M44,64 L56,64 L56,88 L44,88 Z",                  // door
        "M29,54 L39,54 L39,64 L29,64 Z",                  // window
        "M61,54 L71,54 L71,64 L61,64 Z"                   // window
    )),
    .coloring(ColoringPage(
        "car",
        "M8,66 L14,50 L34,46 L44,34 L66,34 L78,48 L92,52 L92,68 L8,68 Z",  // body
        "M37,46 L46,38 L54,38 L54,46 Z",                  // front window
        "M58,38 L64,38 L72,46 L58,46 Z",                  // back window
        circle(28, 68, 10), circle(74, 68, 10),           // wheels
        circle(28, 68, 4), circle(74, 68, 4),             // hubs
        circle(88, 58, 2.5)                               // light
    )),
    .trace(numbers: false),
    .trace(numbers: true),
]

/** The few SVG path commands the pages use (M, L, Q, A, Z, absolute). */
func svgPath(_ d: String) -> Path {
    let re = try! NSRegularExpression(pattern: "[MLQAZ]|-?[0-9]*\\.?[0-9]+")
    let tokens = re.matches(in: d, range: NSRange(d.startIndex..., in: d)).map { String(d[Range($0.range, in: d)!]) }
    var p = Path()
    var i = 0
    var cmd = ""
    var cur = CGPoint.zero, start = CGPoint.zero
    func num() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i]) ?? 0) }
    while i < tokens.count {
        let t = tokens[i]
        if let ch = t.first, ch.isLetter {
            cmd = t
            i += 1
            if cmd == "Z" { p.closeSubpath(); cur = start }
            continue
        }
        switch cmd {
        case "M":
            cur = CGPoint(num(), num()); p.move(to: cur); start = cur; cmd = "L"
        case "L":
            cur = CGPoint(num(), num()); p.addLine(to: cur)
        case "Q":
            let c = CGPoint(num(), num()); cur = CGPoint(num(), num()); p.addQuadCurve(to: cur, control: c)
        case "A":
            let rx = num(), ry = num(), rot = num(), large = num() != 0, sweep = num() != 0
            let end = CGPoint(num(), num())
            svgArc(&p, cur, rx, ry, rot, large, sweep, end)
            cur = end
        default:
            i += 1
        }
    }
    return p
}

/** An SVG elliptical arc from `p0` to `p1`, as short lines. */
private func svgArc(_ p: inout Path, _ p0: CGPoint, _ rx0: CGFloat, _ ry0: CGFloat, _ phiDeg: CGFloat, _ large: Bool, _ sweep: Bool, _ p1: CGPoint) {
    if p0 == p1 { return }
    var rx = abs(rx0), ry = abs(ry0)
    if rx == 0 || ry == 0 { p.addLine(to: p1); return }
    let phi = phiDeg * .pi / 180
    let cs = cos(phi), sn = sin(phi)
    let dx = (p0.x - p1.x) / 2, dy = (p0.y - p1.y) / 2
    let x1 = cs * dx + sn * dy, y1 = -sn * dx + cs * dy
    let lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
    if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
    let num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
    let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
    var coef = den == 0 ? 0 : sqrt(max(0, num / den))
    if large == sweep { coef = -coef }
    let cxp = coef * rx * y1 / ry, cyp = -coef * ry * x1 / rx
    let cx = cs * cxp - sn * cyp + (p0.x + p1.x) / 2
    let cy = sn * cxp + cs * cyp + (p0.y + p1.y) / 2
    func ang(_ ux: CGFloat, _ uy: CGFloat, _ vx: CGFloat, _ vy: CGFloat) -> CGFloat { atan2(ux * vy - uy * vx, ux * vx + uy * vy) }
    let theta1 = ang(1, 0, (x1 - cxp) / rx, (y1 - cyp) / ry)
    var dtheta = ang((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry)
    if !sweep && dtheta > 0 { dtheta -= 2 * .pi } else if sweep && dtheta < 0 { dtheta += 2 * .pi }
    let n = max(8, Int(abs(dtheta) / (.pi / 48)))
    for k in 1...n {
        let t = theta1 + dtheta * CGFloat(k) / CGFloat(n)
        p.addLine(to: CGPoint(cx + rx * cos(t) * cs - ry * sin(t) * sn, cy + rx * cos(t) * sn + ry * sin(t) * cs))
    }
}

/** `text` as a big glyph from the font, centered on paper of `size`. */
func glyphPath(_ text: String, _ size: CGSize) -> Path {
    let attr = NSAttributedString(string: text, attributes: [.font: UIFont.boldSystemFont(ofSize: size.height * 0.78)])
    let line = CTLineCreateWithAttributedString(attr)
    let out = CGMutablePath()
    for run in CTLineGetGlyphRuns(line) as! [CTRun] {
        let attrs = CTRunGetAttributes(run) as NSDictionary
        let font = attrs[kCTFontAttributeName as String] as! CTFont
        let n = CTRunGetGlyphCount(run)
        var glyphs = [CGGlyph](repeating: 0, count: n)
        var pos = [CGPoint](repeating: .zero, count: n)
        CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
        CTRunGetPositions(run, CFRange(location: 0, length: 0), &pos)
        for k in 0..<n {
            guard let g = CTFontCreatePathForGlyph(font, glyphs[k], nil) else { continue }
            // Glyphs are drawn y-up; the paper is top down
            out.addPath(g, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: pos[k].x, ty: -pos[k].y))
        }
    }
    let b = out.boundingBoxOfPath
    return Path(out).applying(CGAffineTransform(translationX: size.width / 2 - b.midX, y: size.height / 2 - b.midY))
}

/** Points on a grid `step` apart that fall inside `glyph`: painting near them is what counts. */
func samplesInside(_ glyph: Path, _ step: CGFloat) -> [CGPoint] {
    let b = glyph.boundingRect
    if b.isEmpty { return [] }
    var points: [CGPoint] = []
    var y = b.minY + step / 2
    while y < b.maxY {
        var x = b.minX + step / 2
        while x < b.maxX {
            if glyph.contains(CGPoint(x, y)) { points.append(CGPoint(x, y)) }
            x += step
        }
        y += step
    }
    return points
}

/** The letter to trace, pale (under the paint). */
func drawTraceGlyph(_ d: DrawScope, _ glyph: Path) {
    d.drawPath(glyph, Color(argb: 0xFFEDE7F6))
}

/** The letter's dashed edge (over the paint, so its shape always shows). */
func drawTraceEdge(_ d: DrawScope, _ glyph: Path) {
    let width = d.size.minDimension * 0.008
    d.drawPath(glyph, Color(argb: 0xFF9575CD), style: Stroke(width: width, dash: [width * 3, width * 2.5]))
}

/** The front-most shape under `point`, or -1. */
func shapeAt(_ shapes: [Path], _ point: CGPoint) -> Int {
    for i in shapes.indices.reversed() where shapes[i].boundingRect.contains(point) && shapes[i].contains(point) { return i }
    return -1
}

/** The page's lines (over the strokes, like a coloring book), each hidden where a shape in front covers it. */
func drawPageLines(_ d: DrawScope, _ shapes: [Path]) {
    let line = Stroke(width: d.size.minDimension * 0.009, cap: .round, join: .round)
    for (i, shape) in shapes.enumerated() {
        clippedOutside(d, shapes, i + 1) { $0.drawPath(shape, Color(argb: 0xFF4E342E), style: line) }
    }
}

/** Draws `block` everywhere except inside shapes `from` onwards. */
private func clippedOutside(_ d: DrawScope, _ shapes: [Path], _ from: Int, _ block: (DrawScope) -> Void) {
    if from >= shapes.count { block(d) } else { d.clipOut(shapes[from]) { clippedOutside($0, shapes, from + 1, block) } }
}
