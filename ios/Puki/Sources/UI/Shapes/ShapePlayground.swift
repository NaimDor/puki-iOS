import SwiftUI

/** The corner Puki sits in; games keep their pieces clear of it. */
let pukiCorner: CGFloat = 130

struct ShapeRoundState {
    var picture = ShapePicture.house
    var targetShape = level1Shapes[0]
    var shapeChoices = level1Shapes
    var roundIndex = 0
    var totalRounds = 4
    var roundComplete = false
    var sessionComplete = false
    var totalStarsEarned = 0
    var isLoading = true
}

@MainActor
final class ShapePlaygroundViewModel: ObservableObject {
    @Published private(set) var state = ShapeRoundState()
    private(set) var session: GameSession!
    private var allShapes = level1Shapes
    private var rounds: [ShapeDef] = []
    private let picture = ShapePicture.allCases.randomElement()!
    private var level = 1

    func start(_ app: AppModel) {
        guard session == nil else { return }
        session = GameSession("shapes", app.progressRepository)
        level = session.level()
        allShapes = shapesForLevel(level)  // later levels add shapes to the first four
        rounds = picture.slots.map { slot in allShapes.first { $0.id == slot.shapeId }! }  // a piece for each place in the picture
        startRound(0)
    }

    private func startRound(_ index: Int) {
        if index >= rounds.count {
            state.sessionComplete = true
            session.finish(rounds: rounds.count)
            return
        }
        let target = rounds[index]
        state = ShapeRoundState(picture: picture, targetShape: target, shapeChoices: pickChoices(target, allShapes, level == 1 ? 3 : 4) { $0.id },
                                roundIndex: index, totalRounds: rounds.count, totalStarsEarned: state.totalStarsEarned, isLoading: false)
    }

    /** True on the right shape, false on a wrong one, nil once the round is already won. */
    func onShapeDropped(_ id: String) -> Bool? {
        if state.roundComplete { return nil }
        let correct = id == state.targetShape.id
        if correct {
            state.roundComplete = true
            state.totalStarsEarned += 1
            session.star()
            DayLog.record(RecapContent.shape(state.targetShape))
        } else {
            session.slip()
        }
        return correct
    }

    func nextRound() { startRound(state.roundIndex + 1) }
}

private let pictureZones: Set<String> = ["target", "slot"]
private let allShapesById = Dictionary(uniqueKeysWithValues: level3Shapes.map { ($0.id, $0) })

struct ShapePlaygroundScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gender) private var gender
    @Environment(\.layoutDirection) private var dir
    @StateObject private var vm = ShapePlaygroundViewModel()
    @StateObject private var dd = DragDropState()
    @StateObject private var round = RoundFlow()
    @StateObject private var help = Help()
    @StateObject private var reactions = ReactionBank()
    @StateObject private var targetReaction = ReactionState()
    @State private var sessionSeed = newSessionSeed()
    @State private var alive: CGFloat = 0  // the finished picture comes alive: 0 → 1
    @State private var showCard = false

    var body: some View {
        let state = vm.state
        GameBackground(scene: .playground) {
            if !state.isLoading {
                VStack(spacing: 0) {
                    GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.totalRounds, starsEarned: state.totalStarsEarned)
                    PromptBubble(text: BilingualText("תזיז את ה\(state.targetShape.name.he)!", "Drag the \(state.targetShape.name.en)!",
                                                     heGirl: "תזיזי את ה\(state.targetShape.name.he)!")(language, gender))
                    // The picture in the middle, its next place an empty hole; the pieces lie around it
                    GeometryReader { g in
                        let side = min(g.size.height, g.size.width * 0.44)
                        let pieceSize = min(190, g.size.height / 2.2)
                        let picture = state.picture
                        let done = state.roundIndex + (state.roundComplete || state.sessionComplete ? 1 : 0)
                        let slot = picture.slots[min(state.roundIndex, picture.slots.count - 1)]
                        let whole = g.size.width + g.size.height
                        ZStack {
                            Tweened(alive) { a in
                                ZStack(alignment: .topLeading) {
                                    DropZone(zoneId: "target") {
                                        DrawCanvas { d in drawPicture(d, picture, done, a) }.frame(width: side, height: side)
                                    }
                                    .reaction(targetReaction)
                                    // The place itself, for a tap to fly to
                                    DropZone(zoneId: "slot") { Color.clear.frame(width: side * slot.size, height: side * slot.size) }
                                        .offset(x: side * (slot.x - slot.size / 2), y: side * (slot.y - slot.size / 2))
                                }
                                .frame(width: side, height: side)
                                .environment(\.layoutDirection, .leftToRight)
                                // The finished picture is off: the car drives away, the rocket takes off
                                .offset(x: picture == .car ? -a * a * whole : 0, y: picture == .rocket ? -a * a * whole : 0)
                            }
                            // (clear of Puki's corner)
                            Scatter(seed: sessionSeed + state.roundIndex, aroundCenter: true, rtl: dir == .rightToLeft) {
                                ForEach(Array(state.shapeChoices.enumerated()), id: \.element.id) { index, shape in
                                    shapePiece(state, shape, index, pieceSize)
                                }
                            }
                            .padding(.horizontal, pukiCorner * 0.6)
                            .id(state.roundIndex)
                        }
                        .frame(width: g.size.width, height: g.size.height)
                    }
                }
                .padding(16)
                ConfettiOverlay(show: round.won || state.sessionComplete)
                if showCard { RoundCompleteSheet(starsEarned: state.totalStarsEarned, onContinue: onPlayAgain, onHome: onBack) }
            }
        }
        .environmentObject(dd)
        .task { round.companion = app.companion; vm.start(app) }
        .onChange(of: state.roundIndex) { _, r in reactions.reset(for: r) }
        .helpLoop(help, key: state.roundIndex, active: !round.won && !state.sessionComplete && !state.isLoading, onAssisted: { vm.session?.slip() }) {
            app.voice.playSequence(["voice_prompt_drag_it", "voice_shape_\(vm.state.targetShape.id)"], language)
        }
        .roundPrompt(state.isLoading ? -1 : state.roundIndex, "במשחק הצורות: מחפשים \(state.targetShape.name.he).",
                     "In the shape game: we're looking for the \(state.targetShape.name.en.lowercased()).",
                     keys: state.isLoading ? [] : ["voice_prompt_drag_it", "voice_shape_\(state.targetShape.id)"])
        .task(id: state.sessionComplete) {
            guard state.sessionComplete else { return }
            app.sfx.play(.whoosh)
            Task { await targetReaction.celebrate() }  // (the house can't drive off, so it jumps for joy)
            withAnimation(.easeInOut(duration: 1.7)) { alive = 1 }
            await delay(2200)
            showCard = true
        }
    }

    private func shapePiece(_ state: ShapeRoundState, _ shape: ShapeDef, _ index: Int, _ pieceSize: CGFloat) -> some View {
        let right = shape.id == state.targetShape.id
        let r = reactions(shape.id)
        return DragTarget(itemId: shape.id,
                          onDragEnd: { dropped(shape, $0, r) },
                          onDragStart: { app.voice.play("voice_shape_\(shape.id)", language) },
                          tapToZone: "slot",  // a tap tries it in the picture
                          landed: { pictureZones.contains($0) && right }) {
            DrawCanvas { d in drawShapeFilled(d, shape, d.center, d.size.minDimension * 0.4) }
                .frame(width: pieceSize, height: pieceSize)
                .hintGlow(help.showHint && right)
                .reaction(r)
                .idleBob(index)
                .popIn(index, key: state.roundIndex)
        }
        .popAway((state.roundComplete || state.sessionComplete) && !right, index: index)
    }

    private func dropped(_ shape: ShapeDef, _ zone: String?, _ r: ReactionState) {
        guard let zone, pictureZones.contains(zone) else { return }
        let target = vm.state.targetShape
        let correct = vm.onShapeDropped(shape.id)
        if correct == true {
            app.sfx.play(.drop)
            app.sfx.play(.success, volume: 0.7)
            app.companion.react("במשחק הצורות שמו את ה\(shape.name.he) במקום הנכון!", "In the shape game the child put the \(shape.name.en.lowercased()) in the right place!") {
                app.voice.playPraise(language)
            }
            Task { await targetReaction.hop() }
            round.win(quietMs: 2000, talkingMs: 3400) { vm.nextRound() }
        } else if correct == false {
            app.sfx.play(.wrongGentle)
            app.companion.react("במשחק הצורות ניסו לשים \(shape.name.he) במקום של \(target.name.he).",
                                "In the shape game the child tried to put the \(shape.name.en.lowercased()) where the \(target.name.en.lowercased()) goes.") {
                app.voice.play("voice_prompt_try_again", language)
            }
            help.missed()
            Task { await r.shake() }
        }
    }
}

/** A chunky toy piece: a shadow, a top-lit fill and a white rim. */
func drawShapeFilled(_ d: DrawScope, _ shape: ShapeDef, _ c: CGPoint, _ r: CGFloat) {
    let fill = Brush.verticalGradient([lerp(shape.color, .white, 0.3), shape.color, lerp(shape.color, .black, 0.15)], startY: c.y - r, endY: c.y + r)
    let path = shapePath(shape, c, r)
    d.drawPath(shapePath(shape, c + CGPoint(0, r * 0.05), r), Color.black.copy(alpha: 0.15))
    d.drawPath(path, fill)
    d.drawPath(path, Color.white.copy(alpha: 0.8), style: Stroke(width: r * 0.05))
}

/** The empty place for `shape`: a dark hole with a dashed rim, which reads whatever the piece's color. */
private func drawSlotHole(_ d: DrawScope, _ shape: ShapeDef, _ c: CGPoint, _ r: CGFloat) {
    let path = shapePath(shape, c, r)
    d.drawPath(path, Color(argb: 0x664E342E))
    d.ctx.stroke(path, with: .color(Color(argb: 0xCC4E342E)), style: StrokeStyle(lineWidth: r * 0.07, dash: [r * 0.2, r * 0.14]))
}

/** The picture: its base, the `done` places filled, the next one a hole, and later ones not yet shown. */
private func drawPicture(_ d: DrawScope, _ picture: ShapePicture, _ done: Int, _ alive: CGFloat) {
    let w = d.size.width
    switch picture {
    case .house:
        d.drawOval(Color(argb: 0xFFA5D6A7), CGPoint(w * 0.02, w * 0.86), CGSize(w * 0.96, w * 0.13))
    case .car:
        let body = Color(argb: 0xFF26A69A)
        d.drawOval(Color.black.copy(alpha: 0.12), CGPoint(w * 0.08, w * 0.85), CGSize(w * 0.84, w * 0.08))
        d.drawRoundRect(body, CGPoint(w * 0.3, w * 0.18), CGSize(w * 0.4, w * 0.34), w * 0.07)
        d.drawRoundRect(body, CGPoint(w * 0.08, w * 0.44), CGSize(w * 0.84, w * 0.3), w * 0.08)
        d.drawCircle(Color(argb: 0xFFFFF59D), w * 0.035, CGPoint(w * 0.13, w * 0.54))
    case .rocket:
        for s in [-1.0, 1.0] as [CGFloat] {
            d.drawPath(Path { p in
                p.moveTo(w * (0.5 + s * 0.15), w * 0.58); p.lineTo(w * (0.5 + s * 0.33), w * 0.86); p.lineTo(w * (0.5 + s * 0.15), w * 0.8); p.close()
            }, Color(argb: 0xFFEF5350))
        }
        d.drawRoundRect(Color(argb: 0xFFECEFF1), CGPoint(w * 0.33, w * 0.24), CGSize(w * 0.34, w * 0.64), w * 0.1)
        if alive > 0 {
            d.drawOval(Color(argb: 0xFFFF9800), CGPoint(w * 0.4, w * 0.93), CGSize(w * 0.2, w * 0.3))
            d.drawOval(Color(argb: 0xFFFFEE58), CGPoint(w * 0.45, w * 0.93), CGSize(w * 0.1, w * 0.18))
        }
    }
    for (i, slot) in picture.slots.enumerated() where i <= done {
        let shape = allShapesById[slot.shapeId]!
        let c = CGPoint(w * slot.x, w * slot.y)
        let r = w * slot.size / 2
        if i < done { drawShapeFilled(d, shape, c, r) } else { drawSlotHole(d, shape, c, r) }
    }
}

/** The outline of `shape`. Square sits flat, diamond stands on a point. */
func shapePath(_ shape: ShapeDef, _ c: CGPoint, _ r: CGFloat) -> Path {
    switch shape.id {
    case "circle": return Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
    case "square":
        let h = r * 0.78
        return Path(CGRect(x: c.x - h, y: c.y - h, width: 2 * h, height: 2 * h))
    case "diamond":
        return Path { p in p.moveTo(c.x, c.y - r); p.lineTo(c.x + r * 0.72, c.y); p.lineTo(c.x, c.y + r); p.lineTo(c.x - r * 0.72, c.y); p.close() }
    case "heart":
        return Path { p in
            p.moveTo(c.x, c.y + r * 0.9)
            p.cubicTo(c.x - r * 1.35, c.y + r * 0.05, c.x - r * 0.8, c.y - r * 1.1, c.x, c.y - r * 0.42)
            p.cubicTo(c.x + r * 0.8, c.y - r * 1.1, c.x + r * 1.35, c.y + r * 0.05, c.x, c.y + r * 0.9)
            p.close()
        }
    case "star":
        return starPath(c + CGPoint(0, r * 0.08), r)
    default:
        return polygonPath(c, r, shape.sides)
    }
}

func polygonPath(_ center: CGPoint, _ radius: CGFloat, _ sides: Int) -> Path {
    Path { p in
        let step = 360 / CGFloat(sides)
        for i in 0..<sides {
            let a = (step * CGFloat(i) - 90) * .pi / 180
            let pt = CGPoint(center.x + cos(a) * radius, center.y + sin(a) * radius)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
    }
}
