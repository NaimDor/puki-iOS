import SwiftUI

// Six pots on one side, three more under the brushes on the other
private let leftColors = [Color(argb: 0xFFEF5350), Color(argb: 0xFF42A5F5), Color(argb: 0xFFFFEE58),
                          Color(argb: 0xFF66BB6A), Color(argb: 0xFFAB47BC), Color(argb: 0xFFFF9800)]
private let rightColors = [Color(argb: 0xFFF48FB1), Color(argb: 0xFF8D6E63), Color(argb: 0xFF424242)]
private let allColors = leftColors + rightColors
/** Each pot's color by name, for Puki's eyes (LookContent). */
private let colorIds = ["red", "blue", "yellow", "green", "purple", "orange", "pink", "brown", "black"]

/** Thin and fat brushes, as stroke widths in points. */
private let brushWidths: [CGFloat] = [10, 28]

/** The eraser is fatter than the fat brush: rubbing out is for big mistakes. */
private let eraserWidth: CGFloat = 44

/** The stamp tool's stickers: each tap puts down the next one. */
private let stampIcons = ["ic_sticker_star", "ic_sticker_heart", "ic_sticker_flower", "ic_sticker_butterfly", "ic_stamp_ladybug", "ic_count_apple",
                          "ic_sticker_rainbow", "ic_sticker_sun", "ic_sticker_moon", "ic_sticker_unicorn", "ic_count_bird", "ic_sticker_rocket"]

private let paperColor = Color(argb: 0xFFFFFDF6)
private let highlight = Color(argb: 0xFFFFD54F)
private let stampSize: CGFloat = 96

/** What the voice calls a page on the shelf. */
private func pageVoice(_ page: CanvasPage?) -> String {
    switch page {
    case nil: return "voice_page_blank"
    case .coloring(let p): return "voice_page_\(p.id)"
    case .trace(let numbers): return numbers ? "voice_page_numbers" : "voice_page_letters"
    }
}

/** The studio's stickers by name, for Puki's eyes (LookContent.stamps, in the same order as stampIcons). */
private func stampId(_ icon: String) -> String? {
    guard let i = stampIcons.firstIndex(of: icon), i < LookContent.stamps.keys.count else { return nil }
    return LookContent.stamps.keys[i]
}

/** The drawing on the easel: it belongs to the app's visit, so leaving and coming back finds it still there. */
@MainActor final class DrawingModel: ObservableObject {
    static let shared = DrawingModel()
    @Published var selectedColor = leftColors[0]
    @Published var brushWidth = brushWidths[1]
    @Published var stamping = false  // the stamp tool instead of a brush
    @Published var erasing = false  // the eraser instead of a brush
    @Published var stamps: [Stamp] = []
    @Published var nextStamp = 0
    let stampOrder = stampIcons.shuffled()
    @Published var pageIndex = 0  // 0: blank paper
    @Published var fills: [Int: Color] = [:]
    @Published var letterTurn = -1
    @Published var numberTurn = -1
    @Published var strokeCount = 0
    var lastStroke: DrawPath?
    @Published var marks = 0  // strokes, stamps and fills so far: what Puki looks out for
    var lastLooked = 0
    let art = ArtLayer()
    // What Puki's eyes go by (LookContent): which stickers, and each stroke's shape (the colors come off the paper)
    var stickers: [String: Int] = [:]
    var strokeShapes: [StrokeShape] = []

    func noted(_ stroke: DrawPath) {
        lastStroke = stroke
        strokeCount += 1
        marks += 1
        if stroke.erase { return }
        strokeShapes.append(StrokeShape.of(stroke))
    }

    func clearStrokes() {
        art.clearStrokes()
        lastStroke = nil
        strokeCount = 0
        strokeShapes = []
    }
}

/** The stroke being drawn right now: the only thing redrawn as the finger moves. */
private final class LiveStroke: ObservableObject {
    @Published var points: [CGPoint] = []
}

private enum SideItem: Hashable { case pot(Int), brush(Int), stamp, eraser, wipe }

@MainActor struct FreeCanvasScreen: View {
    let onBack: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @ObservedObject private var drawing = DrawingModel.shared
    @ObservedObject private var art = DrawingModel.shared.art
    @StateObject private var live = LiveStroke()
    @State private var canvasSize = CGSize.zero
    @State private var dragging = false
    @State private var lastStampAt = CGPoint.zero
    @State private var rubbedTo = 0  // how much of the eraser's path has gone into the paint
    @State private var stampPop: CGFloat = 1
    @State private var sweep = Clip(0.35)
    @State private var flash = false  // a camera flash over the paper when it's saved
    @State private var looking = false
    @State private var shelfOpen = false
    // Tracing
    @State private var traceGlyph: Path?
    @State private var traceSamples: [CGPoint] = []
    @State private var covered: [Bool] = []
    @State private var traced = false
    @State private var tracedParty = false

    private var brushSize: CGFloat { drawing.erasing ? eraserWidth : drawing.brushWidth }
    private var page: CanvasPage? { coloringPages[drawing.pageIndex] }
    private var pageShapes: [Path] {
        if case .coloring(let p) = page, canvasSize.width > 0 { return p.placed(canvasSize) }
        return []
    }
    private var letters: [LetterDef] { language == .english ? englishLetters : hebrewLetters }
    private var traceText: String? {
        guard case .trace(let numbers) = page else { return nil }
        if numbers { return "\(max(0, drawing.numberTurn) % 9 + 1)" }
        let l = letters[max(0, drawing.letterTurn) % letters.count]
        return language == .english ? l.englishLetter : l.hebrewLetter
    }

    var body: some View {
        GeometryReader { g in
            // On a phone the pots and tools stand two by two, with the buttons side by side above them; sized from the
            // rows that's left them
            let compact = isCompact()
            let across = compact ? 2 : 1
            let rows: CGFloat = compact ? 4 : 7
            let buttons: CGFloat = compact ? 64 : 128
            let unit = min((g.size.height - 32 - buttons) / (rows * 1.06), 92)
            let sideWidth = compact ? unit * 2.3 + 12 : 136
            ZStack {
                DrawCanvas { d in drawStudioWall(d) }.ignoresSafeArea()
                HStack(spacing: 0) {
                    // One side: back, the paint pots, and the camera that saves the drawing
                    VStack {
                        Spacer(minLength: 0)
                        if compact {
                            HStack(spacing: 10) { BackButton(onBack: onBack); saveButton }
                        } else { BackButton(onBack: onBack) }
                        Spacer(minLength: 0)
                        toolRows((0..<leftColors.count).map { SideItem.pot($0) }, across, unit, compact)
                        if !compact { Spacer(minLength: 0); saveButton }
                        Spacer(minLength: 0)
                    }
                    .frame(width: sideWidth)
                    .padding(.vertical, 16)

                    easel
                        .padding(.top, 30).padding(.bottom, 22)

                    // The other side: the shelf of pages, brushes, stamps, the eraser, three more pots, and the wipe at the far end
                    VStack {
                        Spacer(minLength: 0)
                        Button { shelfOpen = true; app.sfx.play(.whoosh, volume: 0.4) } label: {
                            PagePicture(page: page, language: language, size: 50)
                                .frame(width: 64, height: 64)
                                .background(RoundedRectangle(cornerRadius: 12).fill(paperColor).shadow(color: .black.opacity(0.25), radius: 4, y: 2))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(highlight, lineWidth: 3))
                        }
                        .buttonStyle(.plain)
                        Spacer(minLength: 0)
                        let tools: [SideItem] = [.brush(0), .brush(1), .stamp, .eraser] + (0..<rightColors.count).map { SideItem.pot(leftColors.count + $0) }
                        toolRows(compact ? tools + [.wipe] : tools, across, unit, compact)
                        if !compact { Spacer(minLength: 0); HoldToWipe(size: 60) { wipe() } }
                        Spacer(minLength: 0)
                    }
                    .frame(width: sideWidth)
                    .padding(.vertical, 16)
                }
                .padding(.horizontal, 8)

                if shelfOpen {
                    PageShelf(current: drawing.pageIndex, language: language, width: g.size.width, onPick: choosePage) { shelfOpen = false }
                }
                ConfettiOverlay(show: tracedParty)
            }
        }
        .onAppear {
            app.companion.wake()
            app.voice.play("voice_canvas_hello", language)
        }
        .onChange(of: canvasSize) { _, s in
            art.ensure(s)
            repaintFills()
            updateTrace()
        }
        .onChange(of: drawing.pageIndex) { _, _ in repaintFills(); updateTrace() }
        .onChange(of: traceText) { _, _ in updateTrace() }
        .onChange(of: drawing.strokeCount) { _, _ in checkTrace() }
    }

    // ---------- The easel ----------

    private var easel: some View {
        ZStack {
            ZStack {
                ZStack {
                    paperColor
                    // The paper, with the letter to trace
                    DrawCanvas { d in
                        drawPaperGrain(d)
                        if let gl = traceGlyph { drawTraceGlyph(d, gl) }
                    }
                    // The paint, the page's lines and the stickers, in a layer of their own
                    Tweened(stampPop) { pop in
                        DrawCanvas { [version = art.version] d in
                            _ = version
                            drawArtwork(d, art, pageShapes, drawing.stamps, pop: pop)
                        }
                    }
                    LiveStrokeView(live: live, color: drawing.selectedColor, width: brushSize, hidden: drawing.erasing)
                    if let gl = traceGlyph { DrawCanvas { d in drawTraceEdge(d, gl) }.allowsHitTesting(false) }
                    // The white sweep that wipes the page
                    if sweep.isRunning {
                        TimelineView(.animation) { tl in
                            let v = sweep.value(tl.date)
                            DrawCanvas { d in d.drawRect(paperColor, .zero, CGSize(d.size.width * v, d.size.height)) }
                        }
                    }
                    // The camera flash when the drawing is saved
                    Color.white.opacity(flash ? 0.85 : 0)
                        .animation(flash ? .linear(duration: 0.08) : .easeOut(duration: 0.5), value: flash)
                        .allowsHitTesting(false)
                }
                .background(GeometryReader { pg in Color.clear.onAppear { canvasSize = pg.size }.onChange(of: pg.size) { _, s in canvasSize = s } })
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .shadow(color: .black.opacity(0.2), radius: 3, y: 1)
                .contentShape(Rectangle())
                .gesture(paperGesture)
                .testID("paper")
                .padding(18)
                .background(RoundedRectangle(cornerRadius: 22).fill(LinearGradient(colors: [Color(argb: 0xFFC8935B), Color(argb: 0xFFAE7642)], startPoint: .top, endPoint: .bottom))
                    .shadow(color: .black.opacity(0.3), radius: 8, y: 4))
                // Strips of tape holding the paper's corners
                DrawCanvas { d in drawTape(d) }.allowsHitTesting(false)
            }
            .environment(\.layoutDirection, .leftToRight)
            // Puki looks at the drawing: on tap, or by itself after a pause in drawing
            VStack {
                PukiLooks(companion: app.companion, drawn: drawing.marks, looking: looking, lastLooked: drawing.lastLooked, onLook: lookNow)
                    .offset(y: -24)
                Spacer()
            }
        }
    }

    /** A tap stamps a sticker, rubs out a spot, fills the shape it lands in, or leaves a dot of paint; a drag paints. */
    private var paperGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !dragging {
                    if hypot(v.translation.width, v.translation.height) < 8 { return }
                    dragging = true
                    dragStart(v.startLocation)
                }
                drag(v.location)
            }
            .onEnded { v in
                if dragging { dragEnd() } else { tap(v.location) }
                dragging = false
            }
    }

    private func tap(_ offset: CGPoint) {
        let shape = shapeAt(pageShapes, offset)
        if drawing.stamping { stampAt(offset) }
        else if drawing.erasing {
            eraseStampsAt(offset)
            addStroke(DrawPath([offset], paperColor, brushSize, erase: true))
        } else if shape >= 0 {
            drawing.fills[shape] = drawing.selectedColor
            drawing.marks += 1
            repaintFills()  // the strokes already there stay on top
            app.sfx.play(.pop, volume: 0.8)
        } else {
            addStroke(DrawPath([offset], drawing.selectedColor, brushSize))
        }
    }

    private func dragStart(_ offset: CGPoint) {
        if drawing.stamping {
            stampAt(offset)
            lastStampAt = offset
        } else {
            live.points = [offset]
            rubbedTo = 0
            if drawing.erasing {
                eraseStampsAt(offset)
                art.paint(DrawPath([offset], paperColor, brushSize, erase: true))
            }
        }
    }

    private func drag(_ p: CGPoint) {
        if drawing.stamping {
            // A trail of stickers, a little apart
            if hypot(p.x - lastStampAt.x, p.y - lastStampAt.y) > stampSize * 0.8 {
                stampAt(p)
                lastStampAt = p
            }
        } else if drawing.erasing {
            // Rubbed out of the paint as the finger moves, a few points at a time
            eraseStampsAt(p)
            live.points.append(p)
            if live.points.count - rubbedTo >= 3 { rub() }
        } else {
            live.points.append(p)
            // A long scribble goes into the paint in pieces, so the live stroke stays short
            if live.points.count >= livePoints {
                let last = live.points.last!
                addStroke(DrawPath(live.points, drawing.selectedColor, brushSize))
                live.points = [last]
            }
        }
    }

    private func dragEnd() {
        if drawing.erasing {
            rub()
            if !live.points.isEmpty { drawing.noted(DrawPath(live.points, paperColor, brushSize, erase: true)) }
        } else if live.points.count > 1 {
            addStroke(DrawPath(live.points, drawing.selectedColor, brushSize))
        }
        live.points = []
    }

    private func rub() {
        let pts = live.points
        if pts.count < 2 || rubbedTo >= pts.count { return }
        let piece = Array(pts[max(0, rubbedTo - 1)...])
        rubbedTo = pts.count
        art.paint(DrawPath(piece, paperColor, brushSize, erase: true))
    }

    private func addStroke(_ stroke: DrawPath) {
        art.paint(stroke)
        drawing.noted(stroke)
    }

    private func repaintFills() {
        art.paintFills(pageShapes, drawing.fills, paper: paperColor)
    }

    private func stampAt(_ offset: CGPoint) {
        let icon = drawing.stampOrder[drawing.nextStamp % drawing.stampOrder.count]
        drawing.nextStamp += 1
        drawing.stamps.append(Stamp(icon: icon, center: offset, size: stampSize, rotation: (offset.x * 7 + offset.y * 13).truncatingRemainder(dividingBy: 30) - 15))
        if let id = stampId(icon) { drawing.stickers[id, default: 0] += 1 }
        drawing.marks += 1
        if drawing.stamps.count > looseStamps {
            art.paint(drawing.stamps.removeFirst())
        }
        app.sfx.play(.pop, volume: 0.7)
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t) { stampPop = 0.5 }
        DispatchQueue.main.async { withAnimation(.spring(response: 0.25, dampingFraction: 0.4)) { stampPop = 1 } }
    }

    /** The eraser at `at` lifts off any sticker it touches (they sit on top of the paint). */
    private func eraseStampsAt(_ at: CGPoint) {
        let gone = drawing.stamps.filter { hypot($0.center.x - at.x, $0.center.y - at.y) < $0.size * 0.45 + brushSize / 2 }
        if gone.isEmpty { return }
        drawing.stamps.removeAll { s in gone.contains { $0.center == s.center && $0.icon == s.icon } }
        for s in gone { if let id = stampId(s.icon) { drawing.stickers[id] = max(0, (drawing.stickers[id] ?? 1) - 1) } }
    }

    /** A white sweep across the paper, and the page is clean behind it. */
    private func wipe() {
        if sweep.isRunning { return }
        app.sfx.play(.whoosh)
        sweep.run()
        Task {
            await delay(350)
            drawing.clearStrokes()
            drawing.marks = 0
            drawing.stamps = []
            drawing.stickers = [:]
            drawing.fills = [:]
            live.points = []
            repaintFills()
        }
    }

    private func pickColor(_ color: Color) {
        drawing.selectedColor = color
        drawing.erasing = false  // a pot of paint means painting again
        app.sfx.play(.pop, volume: 0.6)
    }

    /** Takes page `index` off the shelf (its paint goes with it; the strokes stay, except on a page to trace). */
    private func choosePage(_ index: Int) {
        shelfOpen = false
        let p = coloringPages[index]
        var isTrace = false
        if case .trace = p { isTrace = true }
        if index == drawing.pageIndex && !isTrace { return }
        drawing.pageIndex = index
        if case .trace(let numbers) = p {
            if numbers { drawing.numberTurn += 1 } else { drawing.letterTurn += 1 }
            drawing.clearStrokes()  // a clean sheet to trace on
        }
        drawing.fills = [:]
        repaintFills()
        app.sfx.play(.whoosh, volume: 0.6)
        app.voice.play(pageVoice(p), language)
    }

    private func saveNow() {
        if canvasSize.width == 0 || flash { return }
        flash = true
        app.sfx.play(.success, volume: 0.7)
        // As it looks on the easel: the paper and the letter to trace under the artwork
        let glyph = traceGlyph, shapes = pageShapes, stamps = drawing.stamps, art = art
        let image = snapshot(canvasSize, maxSide: 2048, paper: { d in if let gl = glyph { drawTraceGlyph(d, gl) } },
                             over: { d in if let gl = glyph { drawTraceEdge(d, gl) } }) { d in drawArtwork(d, art, shapes, stamps) }
        if let image, let png = UIImage(cgImage: image).pngData() { saveDrawing(png) }
        Task {
            await delay(150)
            flash = false
        }
    }

    // ---------- Tracing: the next letter (or number) each time its page comes round ----------

    private func updateTrace() {
        let glyph: Path? = (traceText == nil || canvasSize.width == 0) ? nil : glyphPath(traceText!, canvasSize)
        traceGlyph = glyph
        traceSamples = glyph.map { samplesInside($0, 14) } ?? []
        covered = Array(repeating: false, count: traceSamples.count)
        traced = false
    }

    /** After each stroke: which bits of the letter it painted, and the party once most of it is. */
    private func checkTrace() {
        if traceGlyph == nil || traced { return }
        if drawing.strokeCount == 0 { covered = Array(repeating: false, count: traceSamples.count); return }
        guard let stroke = drawing.lastStroke, !stroke.erase else { return }
        let reach = stroke.strokeWidth / 2 + 14 * 0.6
        for (i, s) in traceSamples.enumerated() where !covered[i] {
            if stroke.path.contains(where: { hypot($0.x - s.x, $0.y - s.y) < reach }) { covered[i] = true }
        }
        if !traceSamples.isEmpty && CGFloat(covered.filter { $0 }.count) >= CGFloat(traceSamples.count) * 0.6 {
            traced = true
            app.sfx.play(.success)
            tracedParty = true
            if case .trace(let numbers) = page {
                app.voice.play(numbers ? "voice_number_\(max(0, drawing.numberTurn) % 9 + 1)" : "voice_letter_\(letters[max(0, drawing.letterTurn) % letters.count].id)", language)
            }
            Task {
                await delay(2200)
                tracedParty = false
            }
        }
    }

    // ---------- Puki's eyes ----------

    private func lookNow() {
        if looking || canvasSize.width == 0 { return }
        looking = true
        drawing.lastLooked = drawing.marks
        // What's on the paper, as the studio knows it: Puki says only that, never what it might show
        let shapes = pageShapes, stamps = drawing.stamps, art = art
        let image = snapshot(canvasSize) { d in drawArtwork(d, art, shapes, stamps) }
        let p = page
        let turn = max(0, drawing.letterTurn)
        var letter: (String, String)?
        if case .trace(false) = p { letter = (hebrewLetters[turn % hebrewLetters.count].hebrewLetter, englishLetters[turn % englishLetters.count].englishLetter) }
        let pageName: String
        if case .coloring(let cp) = p { pageName = cp.id } else if letter != nil { pageName = "trace" } else { pageName = "blank" }
        let stickers = drawing.stickers.filter { $0.value > 0 }.sorted { $0.value > $1.value }.prefix(3).map { ($0.key, $0.value) }
        let strokes = StrokeShape.kindOf(drawing.strokeShapes, paperWidth: canvasSize.width)
        let english = language == .english
        Task {
            let pixels = image.flatMap { Pixels($0) }
            let cover = pixels.map(coverOf) ?? 0
            let painted = pixels.map(colorsOf) ?? []
            let facts = LookFacts(page: pageName, letter: letter, colors: painted, stamps: Array(stickers),
                                  cover: cover < 0.2 ? 0 : cover < 0.55 ? 1 : 2, strokes: strokes)
            if !facts.colors.isEmpty {  // (the brain learned drawings with paint on them)
                DayLog.record(RecapContent.drew(level3Colors.first { $0.id == facts.colors[0] }))
                let x = LookContent.describe(facts)
                _ = await app.companion.line("look", input: english ? x.en : x.he,
                                             present: english ? x.presentEn : x.presentHe, absent: english ? x.absentEn : x.absentHe)
            }
            looking = false
        }
    }

    // ---------- The sides ----------

    private var saveButton: some View {
        Button(action: saveNow) {
            DrawCanvas { d in drawCamera(d) }.frame(width: 46, height: 46)
                .frame(width: 64, height: 64)
                .background(Circle().fill(Color(argb: 0xFF81D4FA)).shadow(color: .black.opacity(0.25), radius: 5, y: 2))
        }
        .buttonStyle(.plain)
    }

    /** `items` in rows of `across`, evenly down what's left of a side column. */
    private func toolRows(_ items: [SideItem], _ across: Int, _ unit: CGFloat, _ compact: Bool) -> some View {
        let rows = stride(from: 0, to: items.count, by: across).map { Array(items[$0..<min($0 + across, items.count)]) }
        return VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                Spacer(minLength: 0)
                HStack(spacing: 6) { ForEach(row, id: \.self) { sideItem($0, unit, compact) } }
            }
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder private func sideItem(_ item: SideItem, _ unit: CGFloat, _ compact: Bool) -> some View {
        switch item {
        case .pot(let k):
            let color = allColors[k]
            PaintPot(color: color, selected: color == drawing.selectedColor, index: k < leftColors.count ? k : k + 1, height: unit) { pickColor(color) }
        case .brush(let i):
            ToolPlate(selected: !drawing.stamping && !drawing.erasing && drawing.brushWidth == brushWidths[i], index: i, size: unit) {
                drawing.brushWidth = brushWidths[i]
                drawing.stamping = false
                drawing.erasing = false
                app.sfx.play(.pop, volume: 0.6)
            } content: {
                DrawCanvas { [color = drawing.selectedColor] d in drawPaintBrush(d, color, fat: i == 1) }.frame(width: unit * 0.84, height: unit * 0.84)
            }
        case .stamp:
            ToolPlate(selected: drawing.stamping, index: 2, size: unit) {
                drawing.stamping = true
                drawing.erasing = false
                app.sfx.play(.pop, volume: 0.6)
            } content: {
                Pic(drawing.stampOrder[drawing.nextStamp % drawing.stampOrder.count]).frame(width: unit * 0.64, height: unit * 0.64)
            }
        case .eraser:
            ToolPlate(selected: drawing.erasing, index: 3, size: unit) {
                drawing.erasing = true
                drawing.stamping = false
                app.sfx.play(.pop, volume: 0.6)
            } content: {
                DrawCanvas { d in drawEraser(d) }.frame(width: unit * 0.7, height: unit * 0.7)
            }
        case .wipe:
            HoldToWipe(size: compact ? unit : 60) { wipe() }
        }
    }
}

private struct LiveStrokeView: View {
    @ObservedObject var live: LiveStroke
    let color: Color
    let width: CGFloat
    let hidden: Bool
    var body: some View {
        DrawCanvas { d in
            if !hidden && !live.points.isEmpty { drawStroke(d, DrawPath(live.points, color, width)) }
        }
        .allowsHitTesting(false)
    }
}

/** Puki over the easel, once there's something to see: a tap and he looks; he looks by himself after a pause too. */
private struct PukiLooks: View {
    @ObservedObject var companion: Companion
    let drawn: Int
    let looking: Bool
    let lastLooked: Int
    let onLook: () -> Void

    var body: some View {
        let ready = companion.isReady
        ZStack {
            if ready && drawn > 0 {
                AmbientTimeline { t in
                    let period = looking ? 0.6 : 2.4
                    let k = (1 - cos(2 * .pi * Ambient.loop(t, period))) / 2
                    let bob = 1 + k * (looking ? 0.12 : 0.04)
                    Button(action: onLook) {
                        Pic("ic_companion_parrot").frame(width: 70, height: 70)
                            .frame(width: 84, height: 84)
                            .background(Circle().fill(Color(argb: 0xFFFFF8E1)).shadow(color: .black.opacity(0.3), radius: 6, y: 3))
                    }
                    .buttonStyle(.plain)
                    .scaleEffect(bob)
                }
            }
        }
        .frame(height: 84)
        .task(id: "\(drawn)-\(ready)") {
            if drawn > 0 && drawn != lastLooked && ready {
                if await delay(7000) { onLook() }
            }
        }
    }
}

/** A page's own little picture: its lines, its first letter or number, or plain paper. */
private struct PagePicture: View {
    let page: CanvasPage?
    let language: Language
    let size: CGFloat
    var body: some View {
        switch page {
        case .coloring(let p):
            DrawCanvas { d in drawPageLines(d, p.placed(d.size)) }.frame(width: size, height: size)
        case .trace(let numbers):
            Text(numbers ? "1" : language == .english ? "A" : "א")
                .font(.system(size: size * 0.72, weight: .bold)).foregroundColor(Color(argb: 0xFF9575CD))
        case nil:
            Color.clear.frame(width: size, height: size)
        }
    }
}

/** The pages laid out to choose from; a tap outside puts the shelf away. */
private struct PageShelf: View {
    let current: Int
    let language: Language
    let width: CGFloat
    let onPick: (Int) -> Void
    let onClose: () -> Void
    var body: some View {
        let thumb = ((width - 80) / CGFloat(coloringPages.count) - 10).coerceIn(96, 136)
        ZStack {
            Color.black.opacity(0.4).ignoresSafeArea().contentShape(Rectangle()).onTapGesture(perform: onClose)
            HStack(spacing: 10) {
                ForEach(0..<coloringPages.count, id: \.self) { i in
                    Button { onPick(i) } label: {
                        PagePicture(page: coloringPages[i], language: language, size: thumb * 0.82)
                            .frame(width: thumb, height: thumb)
                            .background(RoundedRectangle(cornerRadius: 10).fill(paperColor).shadow(color: .black.opacity(0.2), radius: 3, y: 2))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(highlight, lineWidth: i == current ? 5 : 0))
                    }
                    .buttonStyle(.plain)
                    .popIn(i)
                }
            }
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 24).fill(LinearGradient(colors: [Color(argb: 0xFFC8935B), Color(argb: 0xFFAE7642)], startPoint: .top, endPoint: .bottom))
                .shadow(color: .black.opacity(0.3), radius: 8, y: 4))
        }
    }
}

/** The bin: held down for a second it wobbles harder and harder and then wipes; a tap only shakes it. */
private struct HoldToWipe: View {
    let size: CGFloat
    let onWipe: () -> Void
    @State private var pressedAt: Date?
    @State private var hold: Task<Void, Never>?
    @State private var released: CGFloat = 0  // where it was let go, easing back to rest

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: pressedAt == nil)) { tl in
            let k = pressedAt.map { CGFloat(min(1, tl.date.timeIntervalSince($0) / 1.0)) } ?? 0
            Tweened(released) { r in
                let v = max(k, r)
                ZStack {
                    Circle().fill(Color(argb: 0xFFFF8A80)).shadow(color: .black.opacity(0.25), radius: 5, y: 2)
                    Pic("ic_stamp_trash").frame(width: size * 0.6, height: size * 0.6)
                    if v > 0 {
                        Circle().trim(from: 0, to: v).stroke(Color.white, lineWidth: 5).rotationEffect(.degrees(-90)).padding(2.5)
                    }
                }
                .frame(width: size, height: size)
                .scaleEffect(1 + v * 0.22)
                .rotationEffect(.degrees(Double(sin(v * 34) * 16 * v)))
            }
        }
        .contentShape(Circle())
        .gesture(DragGesture(minimumDistance: 0)
            .onChanged { _ in
                if pressedAt != nil { return }
                pressedAt = Date()
                hold = Task {
                    if await delay(1000) { onWipe() }
                }
            }
            .onEnded { _ in
                hold?.cancel()
                if let at = pressedAt {
                    var t = Transaction()
                    t.disablesAnimations = true
                    withTransaction(t) { released = CGFloat(min(1, Date().timeIntervalSince(at))) }
                    withAnimation(.easeOut(duration: 0.18)) { released = 0 }
                }
                pressedAt = nil
            })
    }
}

/** A pot of paint. The chosen one lifts up, wobbles and has a brush standing in it. */
private struct PaintPot: View {
    let color: Color
    let selected: Bool
    let index: Int
    let height: CGFloat
    let onClick: () -> Void
    var body: some View {
        Tweened(selected ? 1 : 0) { lift in
            DrawCanvas { d in drawPaintPot(d, color, lift) }
                .frame(width: height * (100.0 / 92.0), height: height)
                .scaleEffect(1 + lift * 0.14)
                .offset(y: -lift * 10)
        }
        .animation(.spring(response: 0.31, dampingFraction: 0.4), value: selected)
        .idleBob(index, amount: selected ? 1.4 : 0)
        .contentShape(Rectangle())
        .onTapGesture(perform: onClick)
        .testID("paint")
    }
}

/** A round plate for a brush; the chosen one is ringed in yellow and lifted. */
private struct ToolPlate<Content: View>: View {
    let selected: Bool
    let index: Int
    let size: CGFloat
    let onClick: () -> Void
    @ViewBuilder let content: () -> Content
    var body: some View {
        Tweened(selected ? 1 : 0) { lift in
            // The spring overshoots; colours must stay within 0..1
            let shown = Double(lift.coerceIn(0, 1))
            ZStack { content() }
                .frame(width: size, height: size)
                .background(Circle().fill(Color.white).shadow(color: .black.opacity(0.25), radius: selected ? 8 : 3, y: 2))
                .overlay(Circle().stroke(highlight.opacity(shown), lineWidth: 6).padding(3))
                .scaleEffect(1 + lift * 0.12)
        }
        .animation(.spring(response: 0.31, dampingFraction: 0.4), value: selected)
        .idleBob(index + 9, amount: selected ? 1.2 : 0)
        .contentShape(Circle())
        .onTapGesture(perform: onClick)
        .testID("brush")
    }
}

/** How much of the paper isn't paper any more (0..1): a grid of samples off the snapshot. */
private func coverOf(_ b: Pixels) -> CGFloat {
    let paper = paperColor.rgb255
    var painted = 0
    for y in 0..<40 {
        for x in 0..<60 {
            let c = b.rgb(x * (b.width - 1) / 59, y * (b.height - 1) / 39)
            if abs(c.0 - paper.0) + abs(c.1 - paper.1) + abs(c.2 - paper.2) > 60 { painted += 1 }
        }
    }
    return CGFloat(painted) / 2400
}

/**
 * The pots' colors on the paper now, the most first (up to four, each at least a sliver of what's painted): off the
 * snapshot, so paint rubbed out isn't named, and a page's own outlines aren't either.
 */
private func colorsOf(_ b: Pixels) -> [String] {
    let pots = zip(colorIds, allColors.map(\.rgb255)).map { ($0, $1) }
    var counts: [String: Int] = [:]
    for y in 0..<60 {
        for x in 0..<90 {
            let c = b.rgb(x * (b.width - 1) / 89, y * (b.height - 1) / 59)
            func d(_ o: (Int, Int, Int)) -> Int { abs(c.0 - o.0) + abs(c.1 - o.1) + abs(c.2 - o.2) }
            let near = pots.min { d($0.1) < d($1.1) }!
            if d(near.1) < 50 { counts[near.0, default: 0] += 1 }
        }
    }
    let total = max(1, counts.values.reduce(0, +))
    return counts.filter { $0.value * 50 >= total }.sorted { $0.value > $1.value }.prefix(4).map(\.key)
}

/** A stroke's length (points), how much it turned on the way (radians) and its width: what kind of marks she makes. */
struct StrokeShape {
    let length: CGFloat, turning: CGFloat, width: CGFloat

    static func of(_ stroke: DrawPath) -> StrokeShape {
        var length: CGFloat = 0, turning: CGFloat = 0
        var heading: CGFloat?
        let p = stroke.path
        if p.count > 1 {
            for i in 1..<p.count {
                let dx = p[i].x - p[i - 1].x, dy = p[i].y - p[i - 1].y
                let dist = hypot(dx, dy)
                if dist < 2 { continue }
                length += dist
                let h = atan2(dy, dx)
                if let last = heading {
                    var t = h - last
                    while t > .pi { t -= 2 * .pi }
                    while t < -.pi { t += 2 * .pi }
                    turning += abs(t)
                }
                heading = h
            }
        }
        return StrokeShape(length: length, turning: turning, width: stroke.strokeWidth)
    }

    /** LookContent.strokes: 0 dots, 1 long lines, 2 round scribbles, 3 short lines; nil with no strokes. */
    static func kindOf(_ shapes: [StrokeShape], paperWidth: CGFloat) -> Int? {
        if shapes.isEmpty { return nil }
        func median(_ f: (StrokeShape) -> CGFloat) -> CGFloat { shapes.map(f).sorted()[shapes.count / 2] }
        if median(\.length) < median(\.width) * 1.5 { return 0 }
        if median(\.turning) > 2 * .pi { return 2 }
        if median(\.length) > paperWidth * 0.35 { return 1 }
        return 3
    }
}
