import SwiftUI
import UIKit

/** A stroke of paint, or of the eraser (`erase`), which rubs out what's under it. */
final class DrawPath {
    let path: [CGPoint]
    let color: Color
    let strokeWidth: CGFloat
    let erase: Bool
    init(_ path: [CGPoint], _ color: Color, _ strokeWidth: CGFloat, erase: Bool = false) {
        self.path = path; self.color = color; self.strokeWidth = strokeWidth; self.erase = erase
    }
    /** The smoothed outline, built once (finished strokes never change), not on every frame. */
    lazy var smooth: Path = DrawPath.smoothed(path)

    /** Through the midpoints, so lines look painted, not jagged. */
    static func smoothed(_ path: [CGPoint]) -> Path {
        Path { p in
            guard let first = path.first else { return }
            p.move(to: first)
            if path.count > 2 {
                for i in 1..<(path.count - 1) {
                    let mid = CGPoint((path[i].x + path[i + 1].x) / 2, (path[i].y + path[i + 1].y) / 2)
                    p.addQuadCurve(to: mid, control: path[i])
                }
            }
            p.addLine(to: path.last!)
        }
    }
}

/** A sticker stamped on the paper. */
struct Stamp { let icon: String; let center: CGPoint; let size: CGFloat; let rotation: CGFloat }

/** How many points the stroke being drawn holds before it goes into the paint (see ArtLayer). */
let livePoints = 64

/** Past this many, the oldest stamps are stuck down into the paint, so they stop costing a redraw. */
let looseStamps = 40

/**
 * The drawing's paint as two bitmaps the size of the paper. Fills are redrawn whole from the page's shapes when one
 * changes; strokes are only ever added to, once each, so a long drawing costs no more than a short one. The eraser
 * clears both. `version` changes with every change, to redraw what shows them.
 */
final class ArtLayer: ObservableObject {
    private var fills: CGContext?
    private var strokes: CGContext?
    private(set) var fillsImage: CGImage?
    private(set) var strokesImage: CGImage?
    @Published private(set) var version = 0
    private(set) var size: CGSize = .zero
    let scale: CGFloat = 2

    /** New, empty bitmaps if the paper isn't `size`; otherwise what's there stays. */
    func ensure(_ s: CGSize) {
        if s.width <= 0 || s.height <= 0 || s == size { return }
        size = s
        fills = make()
        strokes = make()
        refresh(fills: true)
    }

    private func make() -> CGContext? {
        let c = CGContext(data: nil, width: Int(ceil(size.width * scale)), height: Int(ceil(size.height * scale)), bitsPerComponent: 8,
                          bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        // Points, top down, as on screen
        c?.translateBy(x: 0, y: size.height * scale)
        c?.scaleBy(x: scale, y: -scale)
        return c
    }

    private func refresh(fills f: Bool) {
        if f { fillsImage = fills?.makeImage() }
        strokesImage = strokes?.makeImage()
        version += 1
    }

    /** Replaces the fills: each shape painted, back to front, in its color (or the paper's). */
    func paintFills(_ shapes: [Path], _ colors: [Int: Color], paper: Color) {
        guard let c = fills else { return }
        c.clear(CGRect(origin: .zero, size: size))
        for (i, shape) in shapes.enumerated() {
            c.addPath(shape.cgPath)
            c.setFillColor(UIColor(colors[i] ?? paper).cgColor)
            c.fillPath()
        }
        refresh(fills: true)
    }

    /** Paints `stroke` onto the strokes (and, for the eraser, onto the fills as well). */
    func paint(_ stroke: DrawPath) {
        if let c = strokes { draw(stroke, on: c) }
        if stroke.erase, let c = fills { draw(stroke, on: c) }
        refresh(fills: stroke.erase)
    }

    /** Sticks a stamp down into the paint. */
    func paint(_ stamp: Stamp) {
        guard let c = strokes, let img = UIImage(named: stamp.icon) else { return }
        UIGraphicsPushContext(c)
        c.saveGState()
        c.translateBy(x: stamp.center.x, y: stamp.center.y)
        c.rotate(by: stamp.rotation * .pi / 180)
        img.draw(in: CGRect(x: -stamp.size / 2, y: -stamp.size / 2, width: stamp.size, height: stamp.size))
        c.restoreGState()
        UIGraphicsPopContext()
        refresh(fills: false)
    }

    func clearStrokes() {
        strokes?.clear(CGRect(origin: .zero, size: size))
        refresh(fills: false)
    }

    private func draw(_ stroke: DrawPath, on c: CGContext) {
        guard !stroke.path.isEmpty else { return }
        c.saveGState()
        // The eraser clears its own layer (the artwork's), so the paper underneath shows again
        c.setBlendMode(stroke.erase ? .clear : .normal)
        let color = UIColor(stroke.color).cgColor
        if stroke.path.count == 1 {
            let p = stroke.path[0], r = stroke.strokeWidth / 2
            c.setFillColor(color)
            c.fillEllipse(in: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
        } else {
            c.addPath(stroke.smooth.cgPath)
            c.setStrokeColor(color)
            c.setLineWidth(stroke.strokeWidth)
            c.setLineCap(.round)
            c.setLineJoin(.round)
            c.strokePath()
        }
        c.restoreGState()
    }
}

func drawStroke(_ d: DrawScope, _ stroke: DrawPath) {
    let pts = stroke.path
    if pts.isEmpty { return }
    if pts.count == 1 {
        d.drawCircle(stroke.color, stroke.strokeWidth / 2, pts[0])
        return
    }
    d.drawPath(stroke.smooth, stroke.color, style: Stroke(width: stroke.strokeWidth, cap: .round, join: .round))
}

func drawStamp(_ d: DrawScope, _ stamp: Stamp, grow: CGFloat = 1) {
    let pivot = stamp.center
    d.rotate(Double(stamp.rotation), pivot: pivot) { r in
        r.scale(grow, grow, pivot: pivot) { s in
            s.ctx.draw(Image(stamp.icon), in: CGRect(x: stamp.center.x - stamp.size / 2, y: stamp.center.y - stamp.size / 2, width: stamp.size, height: stamp.size))
        }
    }
}

/** The paint (from `art`), the page's lines over it, and the stickers on top; `pop` is the newest one's size. */
func drawArtwork(_ d: DrawScope, _ art: ArtLayer, _ pageShapes: [Path], _ stamps: [Stamp], pop: CGFloat = 1) {
    let rect = CGRect(origin: .zero, size: art.size)
    if let f = art.fillsImage { d.ctx.draw(Image(decorative: f, scale: art.scale), in: rect) }
    if let s = art.strokesImage { d.ctx.draw(Image(decorative: s, scale: art.scale), in: rect) }
    drawPageLines(d, pageShapes)
    for (i, stamp) in stamps.enumerated() { drawStamp(d, stamp, grow: i == stamps.count - 1 ? pop : 1) }
}

/**
 * The drawing on white paper, scaled to at most `maxSide` px (640 is plenty for Puki's eyes): `paper` under the
 * artwork, which the eraser doesn't clear, and `over` on top of it.
 */
@MainActor func snapshot(_ size: CGSize, maxSide: CGFloat = 640, paper: @escaping (DrawScope) -> Void = { _ in },
                         over: @escaping (DrawScope) -> Void = { _ in }, artwork: @escaping (DrawScope) -> Void) -> CGImage? {
    guard size.width > 0, size.height > 0 else { return nil }
    let factor = min(1, maxSide / max(size.width, size.height))
    let view = ZStack {
        Color.white
        DrawCanvas(paper)
        DrawCanvas(artwork)
        DrawCanvas(over)
    }
    .frame(width: size.width, height: size.height)
    .environment(\.layoutDirection, .leftToRight)
    let r = ImageRenderer(content: view)
    r.scale = factor
    return r.cgImage
}

/** An image's pixels as RGBA bytes, for sampling. */
struct Pixels {
    let width: Int, height: Int
    private let bytes: [UInt8]
    init?(_ image: CGImage) {
        let w = image.width, h = image.height
        width = w; height = h
        var b = [UInt8](repeating: 0, count: w * h * 4)
        let ok = b.withUnsafeMutableBytes { buf -> Bool in
            guard let c = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                    space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            c.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
            return true
        }
        if !ok { return nil }
        bytes = b
    }
    /** (r, g, b) at x, y from the top left. */
    func rgb(_ x: Int, _ y: Int) -> (Int, Int, Int) {
        let i = (y * width + x) * 4
        return (Int(bytes[i]), Int(bytes[i + 1]), Int(bytes[i + 2]))
    }
}

extension Color {
    /** 0..255 channels, for comparing with pixels. */
    var rgb255: (Int, Int, Int) { let c = rgba; return (Int((c.0 * 255).rounded()), Int((c.1 * 255).rounded()), Int((c.2 * 255).rounded())) }
}
