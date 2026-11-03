import SwiftUI

// Compose-style drawing on a SwiftUI Canvas, so the game's art (drawn in code) ports line for line.

typealias Offset = CGPoint
typealias Size = CGSize

extension CGPoint {
    init(_ x: CGFloat, _ y: CGFloat) { self.init(x: x, y: y) }
    static func + (a: CGPoint, b: CGPoint) -> CGPoint { CGPoint(x: a.x + b.x, y: a.y + b.y) }
    static func - (a: CGPoint, b: CGPoint) -> CGPoint { CGPoint(x: a.x - b.x, y: a.y - b.y) }
    static func * (a: CGPoint, k: CGFloat) -> CGPoint { CGPoint(x: a.x * k, y: a.y * k) }
    static prefix func - (a: CGPoint) -> CGPoint { CGPoint(x: -a.x, y: -a.y) }
    var distance: CGFloat { (x * x + y * y).squareRoot() }
}

extension CGSize {
    init(_ w: CGFloat, _ h: CGFloat) { self.init(width: w, height: h) }
    var minDimension: CGFloat { min(width, height) }
    var maxDimension: CGFloat { max(width, height) }
    var center: CGPoint { CGPoint(x: width / 2, y: height / 2) }
}

extension CGRect {
    var center: CGPoint { CGPoint(x: midX, y: midY) }
}

extension Color {
    /** Kotlin's copy(alpha =). */
    func copy(alpha: Double) -> Color { opacity(alpha) }
    private var rgba: (Double, Double, Double, Double) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        return (Double(r), Double(g), Double(b), Double(a))
    }
    /** Each channel times `k` (Kotlin's copy(red = red * k, ...)). */
    func scaled(_ k: Double) -> Color { let c = rgba; return Color(.sRGB, red: c.0 * k, green: c.1 * k, blue: c.2 * k, opacity: c.3) }
    func scaled(r: Double, g: Double, b: Double) -> Color { let c = rgba; return Color(.sRGB, red: c.0 * r, green: c.1 * g, blue: c.2 * b, opacity: c.3) }
    var red: Double { rgba.0 }
    var green: Double { rgba.1 }
    var blue: Double { rgba.2 }
    var alpha: Double { rgba.3 }
}

/** Compose's lerp for colors. */
func lerp(_ a: Color, _ b: Color, _ t: Double) -> Color {
    Color(.sRGB, red: a.red + (b.red - a.red) * t, green: a.green + (b.green - a.green) * t,
          blue: a.blue + (b.blue - a.blue) * t, opacity: a.alpha + (b.alpha - a.alpha) * t)
}

func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat { a + (b - a) * t }

/** Compose's Brush, for fills. */
enum Brush {
    case color(Color)
    case vertical([Color], startY: CGFloat?, endY: CGFloat?)
    case verticalStops([(CGFloat, Color)], startY: CGFloat?, endY: CGFloat?)
    case horizontal([Color], startX: CGFloat?, endX: CGFloat?)
    case radial([Color], center: CGPoint?, radius: CGFloat?)
    case radialStops([(CGFloat, Color)], center: CGPoint?, radius: CGFloat?)
    case linear([Color], start: CGPoint, end: CGPoint)

    static func verticalGradient(_ c: [Color], startY: CGFloat? = nil, endY: CGFloat? = nil) -> Brush { .vertical(c, startY: startY, endY: endY) }
    static func verticalGradient(stops: [(CGFloat, Color)], startY: CGFloat? = nil, endY: CGFloat? = nil) -> Brush { .verticalStops(stops, startY: startY, endY: endY) }
    static func horizontalGradient(_ c: [Color], startX: CGFloat? = nil, endX: CGFloat? = nil) -> Brush { .horizontal(c, startX: startX, endX: endX) }
    static func radialGradient(_ c: [Color], center: CGPoint? = nil, radius: CGFloat? = nil) -> Brush { .radial(c, center: center, radius: radius) }
    static func radialGradient(stops: [(CGFloat, Color)], center: CGPoint? = nil, radius: CGFloat? = nil) -> Brush { .radialStops(stops, center: center, radius: radius) }
    static func linearGradient(_ c: [Color], start: CGPoint, end: CGPoint) -> Brush { .linear(c, start: start, end: end) }

    /** The shading for a shape whose bounds are `bounds` (Compose sizes a gradient to what it fills by default). */
    func shading(_ bounds: CGRect) -> GraphicsContext.Shading {
        switch self {
        case .color(let c): return .color(c)
        case .vertical(let c, let s, let e):
            return .linearGradient(Gradient(colors: c), startPoint: CGPoint(bounds.minX, s ?? bounds.minY), endPoint: CGPoint(bounds.minX, e ?? bounds.maxY))
        case .verticalStops(let st, let s, let e):
            return .linearGradient(Gradient(stops: st.map { .init(color: $0.1, location: $0.0) }),
                                   startPoint: CGPoint(bounds.minX, s ?? bounds.minY), endPoint: CGPoint(bounds.minX, e ?? bounds.maxY))
        case .horizontal(let c, let s, let e):
            return .linearGradient(Gradient(colors: c), startPoint: CGPoint(s ?? bounds.minX, bounds.minY), endPoint: CGPoint(e ?? bounds.maxX, bounds.minY))
        case .radial(let c, let ctr, let r):
            return .radialGradient(Gradient(colors: c), center: ctr ?? bounds.center, startRadius: 0, endRadius: r ?? max(bounds.width, bounds.height) / 2)
        case .radialStops(let st, let ctr, let r):
            return .radialGradient(Gradient(stops: st.map { .init(color: $0.1, location: $0.0) }), center: ctr ?? bounds.center, startRadius: 0,
                                   endRadius: r ?? max(bounds.width, bounds.height) / 2)
        case .linear(let c, let s, let e):
            return .linearGradient(Gradient(colors: c), startPoint: s, endPoint: e)
        }
    }
}

enum StrokeCap { case butt, round, square
    var cg: CGLineCap { self == .round ? .round : self == .square ? .square : .butt }
}

/** A stroke style (Compose's Stroke). */
struct Stroke {
    var width: CGFloat
    var cap: StrokeCap = .butt
    var join: CGLineJoin = .miter
    var style: StrokeStyle { StrokeStyle(lineWidth: width, lineCap: cap.cg, lineJoin: join) }
}

/**
 * Compose's DrawScope on a SwiftUI GraphicsContext: the same calls (drawRect, drawCircle, drawLine, drawPath...)
 * so the art drawn in code reads the same on both.
 */
struct DrawScope {
    var ctx: GraphicsContext
    let size: CGSize
    var center: CGPoint { size.center }

    init(_ ctx: GraphicsContext, _ size: CGSize) { self.ctx = ctx; self.size = size }

    private func paint(_ path: Path, _ brush: Brush, _ alpha: Double, _ stroke: Stroke?) {
        var c = ctx
        if alpha < 1 { c.opacity = alpha }
        let sh = brush.shading(path.boundingRect)
        if let stroke { c.stroke(path, with: sh, style: stroke.style) } else { c.fill(path, with: sh) }
    }

    // Rectangles
    func drawRect(_ color: Color, _ topLeft: CGPoint = .zero, _ size: CGSize? = nil, alpha: Double = 1, style: Stroke? = nil) {
        drawRect(Brush.color(color), topLeft, size, alpha: alpha, style: style)
    }
    func drawRect(_ brush: Brush, _ topLeft: CGPoint = .zero, _ size: CGSize? = nil, alpha: Double = 1, style: Stroke? = nil) {
        let r = CGRect(origin: topLeft, size: size ?? self.size)
        var c = ctx
        if alpha < 1 { c.opacity = alpha }
        // (a gradient over the whole canvas when it's the whole canvas, as Compose's default)
        let sh = brush.shading(size == nil ? CGRect(origin: .zero, size: self.size) : r)
        if let style { c.stroke(Path(r), with: sh, style: style.style) } else { c.fill(Path(r), with: sh) }
    }
    func drawRoundRect(_ color: Color, _ topLeft: CGPoint, _ size: CGSize, _ radius: CGFloat, alpha: Double = 1, style: Stroke? = nil) {
        drawRoundRect(Brush.color(color), topLeft, size, radius, alpha: alpha, style: style)
    }
    func drawRoundRect(_ brush: Brush, _ topLeft: CGPoint, _ size: CGSize, _ radius: CGFloat, alpha: Double = 1, style: Stroke? = nil) {
        let r = CGRect(origin: topLeft, size: size)
        paint(Path(roundedRect: r, cornerRadius: min(radius, min(abs(size.width), abs(size.height)) / 2)), brush, alpha, style)
    }
    // Circles and ovals
    func drawCircle(_ color: Color, _ radius: CGFloat, _ center: CGPoint? = nil, alpha: Double = 1, style: Stroke? = nil) {
        drawCircle(Brush.color(color), radius, center, alpha: alpha, style: style)
    }
    func drawCircle(_ brush: Brush, _ radius: CGFloat, _ center: CGPoint? = nil, alpha: Double = 1, style: Stroke? = nil) {
        let c = center ?? self.center
        paint(Path(ellipseIn: CGRect(x: c.x - radius, y: c.y - radius, width: radius * 2, height: radius * 2)), brush, alpha, style)
    }
    func drawOval(_ color: Color, _ topLeft: CGPoint, _ size: CGSize, alpha: Double = 1, style: Stroke? = nil) {
        drawOval(Brush.color(color), topLeft, size, alpha: alpha, style: style)
    }
    func drawOval(_ brush: Brush, _ topLeft: CGPoint, _ size: CGSize, alpha: Double = 1, style: Stroke? = nil) {
        paint(Path(ellipseIn: CGRect(origin: topLeft, size: size)), brush, alpha, style)
    }
    func drawArc(_ color: Color, startAngle: Double, sweepAngle: Double, useCenter: Bool, _ topLeft: CGPoint, _ size: CGSize, alpha: Double = 1, style: Stroke? = nil) {
        // Compose's arcs go clockwise from 3 o'clock, in degrees, on the oval in topLeft/size
        let rect = CGRect(origin: topLeft, size: size)
        var p = Path()
        let t = CGAffineTransform(translationX: rect.midX, y: rect.midY).scaledBy(x: rect.width / 2, y: rect.height / 2)
        var unit = Path()
        if useCenter { unit.move(to: .zero) }
        unit.addArc(center: .zero, radius: 1, startAngle: .degrees(startAngle), endAngle: .degrees(startAngle + sweepAngle), clockwise: sweepAngle < 0)
        if useCenter { unit.closeSubpath() }
        p.addPath(unit, transform: t)
        paint(p, .color(color), alpha, style)
    }
    // Lines and paths
    func drawLine(_ color: Color, _ a: CGPoint, _ b: CGPoint, _ width: CGFloat = 1, _ cap: StrokeCap = .butt, alpha: Double = 1) {
        drawLine(Brush.color(color), a, b, width, cap, alpha: alpha)
    }
    func drawLine(_ brush: Brush, _ a: CGPoint, _ b: CGPoint, _ width: CGFloat = 1, _ cap: StrokeCap = .butt, alpha: Double = 1) {
        var p = Path()
        p.move(to: a)
        p.addLine(to: b)
        var c = ctx
        if alpha < 1 { c.opacity = alpha }
        c.stroke(p, with: brush.shading(p.boundingRect.insetBy(dx: -width, dy: -width)), style: StrokeStyle(lineWidth: width, lineCap: cap.cg))
    }
    func drawPath(_ path: Path, _ color: Color, alpha: Double = 1, style: Stroke? = nil) { paint(path, .color(color), alpha, style) }
    func drawPath(_ path: Path, _ brush: Brush, alpha: Double = 1, style: Stroke? = nil) { paint(path, brush, alpha, style) }
    func drawImage(_ name: String, _ rect: CGRect, alpha: Double = 1) {
        var c = ctx
        if alpha < 1 { c.opacity = alpha }
        c.draw(Image(name), in: rect)
    }

    // Transforms (each on a copy of the context, as Compose's withTransform blocks)
    func translate(_ dx: CGFloat, _ dy: CGFloat, _ body: (DrawScope) -> Void) {
        var c = ctx
        c.translateBy(x: dx, y: dy)
        body(DrawScope(c, size))
    }
    func rotate(_ degrees: Double, pivot: CGPoint? = nil, _ body: (DrawScope) -> Void) {
        let p = pivot ?? center
        var c = ctx
        c.translateBy(x: p.x, y: p.y)
        c.rotate(by: .degrees(degrees))
        c.translateBy(x: -p.x, y: -p.y)
        body(DrawScope(c, size))
    }
    func scale(_ sx: CGFloat, _ sy: CGFloat, pivot: CGPoint? = nil, _ body: (DrawScope) -> Void) {
        let p = pivot ?? center
        var c = ctx
        c.translateBy(x: p.x, y: p.y)
        c.scaleBy(x: sx, y: sy)
        c.translateBy(x: -p.x, y: -p.y)
        body(DrawScope(c, size))
    }
    func clip(_ path: Path, _ body: (DrawScope) -> Void) {
        var c = ctx
        c.clip(to: path)
        body(DrawScope(c, size))
    }
    func alpha(_ a: Double, _ body: (DrawScope) -> Void) {
        var c = ctx
        c.opacity *= a
        body(DrawScope(c, size))
    }
}

/** A Canvas with a DrawScope (drawBehind / Canvas in Compose). */
struct DrawCanvas: View {
    let draw: (DrawScope) -> Void
    init(_ draw: @escaping (DrawScope) -> Void) { self.draw = draw }
    var body: some View {
        Canvas { ctx, size in draw(DrawScope(ctx, size)) }
    }
}

extension Path {
    /** Compose's Path builder names. */
    mutating func moveTo(_ x: CGFloat, _ y: CGFloat) { move(to: CGPoint(x, y)) }
    mutating func lineTo(_ x: CGFloat, _ y: CGFloat) { addLine(to: CGPoint(x, y)) }
    mutating func quadraticBezierTo(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat) { addQuadCurve(to: CGPoint(x2, y2), control: CGPoint(x1, y1)) }
    mutating func cubicTo(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat, _ x3: CGFloat, _ y3: CGFloat) {
        addCurve(to: CGPoint(x3, y3), control1: CGPoint(x1, y1), control2: CGPoint(x2, y2))
    }
    mutating func close() { closeSubpath() }
    mutating func addOval(_ r: CGRect) { addEllipse(in: r) }
    mutating func addRoundRect(_ r: CGRect, _ radius: CGFloat) { addRoundedRect(in: r, cornerSize: CGSize(radius, radius)) }
    /** Compose's arcTo(rect, startAngleDegrees, sweepAngleDegrees, forceMoveTo). */
    mutating func arcTo(_ rect: CGRect, _ start: Double, _ sweep: Double, _ forceMoveTo: Bool = false) {
        let t = CGAffineTransform(translationX: rect.midX, y: rect.midY).scaledBy(x: rect.width / 2, y: rect.height / 2)
        let a0 = start * .pi / 180
        let p0 = CGPoint(cos(a0), sin(a0)).applying(t)
        if forceMoveTo || isEmpty { move(to: p0) } else { addLine(to: p0) }
        let steps = max(4, Int(abs(sweep) / 10))
        for i in 1...steps {
            let a = (start + sweep * Double(i) / Double(steps)) * .pi / 180
            addLine(to: CGPoint(cos(a), sin(a)).applying(t))
        }
    }
}

let PI = CGFloat.pi
