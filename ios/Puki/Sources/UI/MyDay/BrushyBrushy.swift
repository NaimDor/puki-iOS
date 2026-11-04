import SwiftUI

private enum BrushStep: Int, CaseIterable { case paste, scrub, rinse }

/** Who's getting their teeth brushed. */
enum Patient: CaseIterable { case teddy, lion, crocodile }

/** The teeth, as fractions of the face's (square) box: five on top, five below. The same in every mouth. */
private let teeth: [CGPoint] = (0..<10).map { i in CGPoint(0.304 + CGFloat(i % 5) * 0.098, i < 5 ? 0.595 : 0.805) }
/** Is tooth `i` dirty at this `level`? The ones at the sides come in as she gets better. */
private func dirtyTooth(_ i: Int, _ level: Int) -> Bool {
    switch level {
    case 1: return (1...3).contains(i % 5)
    case 2: return (0...3).contains(i % 5)
    default: return true
    }
}
private let toothW: CGFloat = 0.086
private let toothH: CGFloat = 0.085

/** Brushing Teddy's, a lion's or a crocodile's teeth: toothpaste on the brush, scrub every tooth, a sip of water, a big smile. */
struct BrushyBrushyScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var game = StepGame(first: BrushStep.paste, steps: BrushStep.allCases.count)
    @StateObject private var help = Help()
    @State private var level = 0
    @State private var patient = Patient.allCases.randomElement()!
    @State private var dirt: [CGFloat] = []  // 1: dirty, 0: clean
    @State private var foam: [CGPoint] = []
    @State private var brush: CGPoint?  // the brush, under the finger
    @State private var lastShake: Int64 = 0
    @State private var lastDrag: CGPoint?
    @State private var moved = false
    @State private var paste = Clip(1.3, easeInOut: true)  // the toothpaste coming out onto the brush
    @State private var swish = Clip(2.2)  // the rinse: cheeks full of water, swishing
    @State private var smile = Clip(0.7, easeInOut: true)  // the open mouth closing into a big grin
    @State private var foamFade = Clip(1.6)
    @State private var pasted = false
    @State private var rinsed = false

    var body: some View {
        let step = game.step
        GameBackground {
            DrawCanvas { d in drawBathroom(d) }.ignoresSafeArea()
            if level > 0 {
                VStack(spacing: 0) {
                    GameTopBar(onBack: onBack, roundIndex: step.rawValue, totalRounds: BrushStep.allCases.count, starsEarned: game.stars)
                    GeometryReader { g in
                        // The face as big as the screen allows (the teeth are the targets), the tool beside it
                        let faceW = min(g.size.height * 0.95, g.size.width * 0.62)
                        HStack(spacing: faceW * 0.08) {
                            face(step, faceW)
                            // The tools: the toothpaste tube, then the cup of water
                            ZStack {
                                if step == .paste {
                                    ToolButton(size: faceW * 0.34, hint: help.showHint && !paste.isRunning, enabled: !pasted, onClick: squeeze) {
                                        TimelineView(.animation(paused: !paste.isRunning)) { tl in
                                            let p = paste.value(tl.date)
                                            DrawCanvas { d in
                                                drawBrush(d, CGPoint(0.55, 0.4), pasteOn: p, scale: 1.25)
                                                drawTube(d, squeeze: p)
                                            }
                                        }
                                        .frame(width: faceW * 0.3, height: faceW * 0.3)
                                    }
                                }
                                if step == .rinse && !rinsed && !game.celebrate {
                                    ToolButton(size: faceW * 0.34, hint: help.showHint, onClick: rinse) {
                                        DrawCanvas { d in drawCup(d) }.frame(width: faceW * 0.2, height: faceW * 0.2)
                                    }
                                }
                            }
                            .frame(width: faceW * 0.36, height: faceW * 0.36)
                        }
                        .frame(width: g.size.width, height: g.size.height)
                    }
                }
                .padding(16)
            }
            ConfettiOverlay(show: game.celebrate)
            if game.sessionComplete { RoundCompleteSheet(starsEarned: game.stars, onContinue: onPlayAgain, onHome: onBack) }
        }
        .task {
            game.attach(app, island: "brush")
            level = game.session?.level() ?? 1
            dirt = (0..<teeth.count).map { dirtyTooth($0, level) ? 1 : 0 }
        }
        .onChange(of: step) { _, _ in brush = nil }  // (a new step cancels the drag)
        .helpLoop(help, key: step.rawValue, active: !game.celebrate && !game.sessionComplete && !paste.isRunning && !swish.isRunning && level > 0,
                  onAssisted: { game.session?.slip() }) {
            let key: String
            switch step {
            case .paste: key = "voice_teeth_paste"
            case .scrub: key = "voice_teeth_scrub"
            case .rinse: key = patient == .teddy ? "voice_teeth_rinse" : patient == .lion ? "voice_teeth_rinse_lion" : "voice_teeth_rinse_croc"
            }
            app.voice.play(key, language)
        }
        .modifier(BrushPrompt(step: step, patient: patient, ready: level > 0))
    }

    private func face(_ step: BrushStep, _ faceW: CGFloat) -> some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
            let now = tl.date
            let t = now.timeIntervalSinceReferenceDate
            let sw = swish.value(now), sm = smile.value(now), foamLeft = 1 - foamFade.value(now)
            let eyesShut = Ambient.loop(t, 3.6) > 0.96
            let swishing = sw > 0 && sw < 1
            let happy = brush != nil || sm > 0
            let puff = swishing ? sin(sw * .pi) : 0
            // Giggles while brushed; sways while swishing the water around
            let rot = (brush != nil ? Ambient.wave(t, 0.32) * 2.5 : 0) + sin(sw * .pi * 6) * 6 * (1 - sm)
            DrawCanvas { d in
                switch patient {
                case .teddy: drawTeddy(d, happy, eyesShut, puff, sm)
                case .lion: drawLion(d, happy, eyesShut, puff, sm)
                case .crocodile: drawCrocodile(d, happy, eyesShut, puff, sm)
                }
                if sm < 0.5 && !swishing {
                    for (i, tooth) in teeth.enumerated() { drawTooth(d, tooth, dirt.getOrNull(i) ?? 0, i) }
                    for (i, f) in foam.enumerated() { drawFoam(d, f, i, foamLeft) }
                }
                if sm > 0.5 { drawSparkles(d, Ambient.loop(t, 1.4)) }
                if let b = brush { drawBrush(d, b, pasteOn: 1, scale: 0.7) }
            }
            .rotationEffect(.degrees(Double(rot)))
        }
        .frame(width: faceW, height: faceW)
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { v in
                let p = CGPoint(v.location.x / faceW, v.location.y / faceW)
                if abs(v.translation.width) + abs(v.translation.height) > 6 { moved = true }
                guard step == .scrub, moved else { lastDrag = v.location; return }
                brush = p
                let last = lastDrag ?? v.location
                let dist = ((v.location.x - last.x) * (v.location.x - last.x) + (v.location.y - last.y) * (v.location.y - last.y)).squareRoot()
                lastDrag = v.location
                scrub(p, dist / faceW * 2.6)
            }
            .onEnded { v in
                if !moved { scrub(CGPoint(v.location.x / faceW, v.location.y / faceW), 0.34) }
                brush = nil
                lastDrag = nil
                moved = false
            })
        .testID("scrub")
        // Touches land on the drawing as it is: the face doesn't mirror in Hebrew
        .environment(\.layoutDirection, .leftToRight)
    }

    /** Brushing at `p` (fractions of the face): scrubs the teeth near it by `amount`. */
    private func scrub(_ p: CGPoint, _ amount: CGFloat) {
        guard game.step == .scrub, !dirt.allSatisfy({ $0 <= 0 }) else { return }
        var touched = false
        for (i, t) in teeth.enumerated() {
            let dx = (p.x - t.x) / (toothW * 1.3), dy = (p.y - t.y) / (toothH * 1.4)
            if dirt[i] > 0 && dx * dx + dy * dy < 1 {
                dirt[i] = max(0, dirt[i] - amount)
                touched = true
                if dirt[i] == 0 { app.sfx.play(.pop, volume: 0.7) }
            }
        }
        if !touched { return }
        let now = nowMs()
        if now - lastShake > 280 {  // a soft shh-shh while brushing
            app.sfx.play(.maracas, volume: 0.35)
            lastShake = now
        }
        if foam.count < 70 && CGFloat.random(in: 0..<1) < 0.6 {
            foam.append(p + CGPoint(CGFloat.random(in: 0..<1) * 0.05 - 0.025, CGFloat.random(in: 0..<1) * 0.04 - 0.02))
        }
        if dirt.allSatisfy({ $0 <= 0 }) { game.done(.rinse) }
    }

    private func squeeze() {
        if pasted { return }
        pasted = true
        app.sfx.play(.whoosh, volume: 0.6)
        paste.run()
        Task {
            await delay(1300)
            game.done(.scrub)
        }
    }

    private func rinse() {
        if rinsed { return }
        rinsed = true
        app.sfx.play(.drop)
        Task {
            // Swish, swish... and the foam is gone
            foamFade.run()
            swish.run()
            await delay(2200)
            app.sfx.play(.whoosh)
            foam = []
            smile.run()
            await delay(700)
            app.voice.play("voice_teeth_clean", language) {
                switch patient {
                case .teddy: app.companion.react("בצחצוח שיניים: צחצחו לדובי את כל השיניים, הוא שטף את הפה ועכשיו הוא מחייך חיוך מבריק!", "Brushing teeth: the child brushed all of Teddy's teeth, he rinsed, and now he has a sparkly smile!") {}
                case .lion: app.companion.react("בצחצוח שיניים: צחצחו לאריה את כל השיניים, הוא שטף את הפה ועכשיו הוא מחייך חיוך מבריק!", "Brushing teeth: the child brushed all of the lion's teeth, he rinsed, and now he has a sparkly smile!") {}
                case .crocodile: app.companion.react("בצחצוח שיניים: צחצחו לתנין את כל השיניים, הוא שטף את הפה ועכשיו הוא מחייך חיוך מבריק!", "Brushing teeth: the child brushed all of the crocodile's teeth, he rinsed, and now he has a sparkly smile!") {}
                }
            }
            game.done(nil)
            await delay(2200)
            game.finish()
        }
    }
}

private struct BrushPrompt: ViewModifier {
    let step: BrushStep
    let patient: Patient
    let ready: Bool
    func body(content: Content) -> some View {
        let (whoHe, whoEn, intro, rinse): (String, String, String, String)
        switch patient {
        case .teddy: (whoHe, whoEn, intro, rinse) = ("דובי", "Teddy's", "voice_teeth_intro", "voice_teeth_rinse")
        case .lion: (whoHe, whoEn, intro, rinse) = ("האריה", "the lion's", "voice_teeth_intro_lion", "voice_teeth_rinse_lion")
        case .crocodile: (whoHe, whoEn, intro, rinse) = ("התנין", "the crocodile's", "voice_teeth_intro_croc", "voice_teeth_rinse_croc")
        }
        let le = CareContent.le(whoHe)
        let shel = "של \(whoHe)"
        let theWho = whoEn.replacingOccurrences(of: "'s", with: "")
        if !ready { return AnyView(content) }
        switch step {
        case .paste: return AnyView(content.roundPrompt(0, "בצחצוח שיניים: מצחצחים \(le) את השיניים. קודם שמים משחה על המברשת.", "Brushing teeth: brushing \(whoEn) teeth. First, toothpaste on the brush.", intro))
        case .scrub: return AnyView(content.roundPrompt(1, "בצחצוח שיניים: מצחצחים כל שן \(shel) עד שהיא מבריקה.", "Brushing teeth: brushing each of \(whoEn) teeth until it sparkles.", "voice_teeth_scrub"))
        case .rinse: return AnyView(content.roundPrompt(2, "בצחצוח שיניים: נותנים \(le) מים לשטוף את הפה.", "Brushing teeth: giving \(theWho) water to rinse his mouth.", rinse))
        }
    }
}

// ---------- Drawing ----------

/** A tiled bathroom wall, and the counter with the sink. */
private func drawBathroom(_ d: DrawScope) {
    d.drawRect(Brush.verticalGradient([Color(argb: 0xFFE0F7FA), Color(argb: 0xFFB2EBF2)]))
    let tile: CGFloat = 70
    let counter = d.size.height * 0.8
    var x: CGFloat = 0
    while x < d.size.width { d.drawLine(Color(argb: 0x55FFFFFF), CGPoint(x, 0), CGPoint(x, counter), 3); x += tile }
    var y: CGFloat = 0
    while y < counter { d.drawLine(Color(argb: 0x55FFFFFF), CGPoint(0, y), CGPoint(d.size.width, y), 3); y += tile }
    d.drawRect(.white, CGPoint(0, counter), CGSize(d.size.width, d.size.height - counter))
    d.drawRect(Color(argb: 0xFF80DEEA), CGPoint(0, counter), CGSize(d.size.width, 6))
    d.drawOval(Color(argb: 0xFFE0F2F1), CGPoint(d.size.width * 0.3, counter + d.size.height * 0.04), CGSize(d.size.width * 0.4, d.size.height * 0.08))
    d.drawOval(Color(argb: 0xFFB2DFDB), CGPoint(d.size.width * 0.47, counter + d.size.height * 0.07), CGSize(d.size.width * 0.06, d.size.height * 0.02))
}

/** The two eyes at height `y`: round, shut for a blink, or happy arcs. */
func drawFaceEyes(_ d: DrawScope, _ happy: Bool, _ eyesShut: Bool, _ y: CGFloat, _ xs: [CGFloat]) {
    let w = d.size.width
    for ex in xs {
        let c = CGPoint(w * ex, w * y)
        if happy || eyesShut {
            d.drawArc(Color(argb: 0xFF3E2723), startAngle: happy ? 200 : 20, sweepAngle: 140, useCenter: false, c - CGPoint(w * 0.04, w * 0.03), CGSize(w * 0.08, w * 0.06),
                      style: Stroke(width: w * 0.014, cap: .round))
        } else {
            d.drawCircle(Color(argb: 0xFF3E2723), w * 0.04, c)
            d.drawCircle(.white, w * 0.013, c + CGPoint(-w * 0.012, -w * 0.014))
        }
    }
}

/** The mouth: wide open, shut while `puff`ing water, or grinning as `smile` goes to 1. */
func drawFaceMouth(_ d: DrawScope, _ puff: CGFloat, _ smile: CGFloat, _ lips: Color) {
    let w = d.size.width
    if puff > 0 {
        d.drawLine(lips, CGPoint(w * 0.42, w * 0.68), CGPoint(w * 0.58, w * 0.68), w * 0.018, .round)
    } else if smile >= 0.5 {
        let grin = Path { p in
            p.moveTo(w * 0.28, w * 0.6); p.quadraticBezierTo(w * 0.5, w * 0.86, w * 0.72, w * 0.6); p.quadraticBezierTo(w * 0.5, w * 0.68, w * 0.28, w * 0.6); p.close()
        }
        d.drawPath(grin, Color(argb: 0xFF6D2B2B))
        d.drawRoundRect(.white, CGPoint(w * 0.34, w * 0.635), CGSize(w * 0.32, w * 0.045), w * 0.022)
    } else {
        let h = w * 0.34 * (1 - smile * 2)
        d.drawRoundRect(Color(argb: 0xFF6D2B2B), CGPoint(w * 0.24, w * 0.7 - h / 2), CGSize(w * 0.52, h), w * 0.14)
        d.drawOval(Color(argb: 0xFFF06292), CGPoint(w * 0.36, w * 0.7 + h * 0.02), CGSize(w * 0.28, h * 0.3))
    }
}

/** Teddy's face, filling a square box. */
private func drawTeddy(_ d: DrawScope, _ happy: Bool, _ eyesShut: Bool, _ puff: CGFloat, _ smile: CGFloat) {
    let w = d.size.width
    let fur = Color(argb: 0xFFA1887F)
    for ex in [0.2, 0.8] as [CGFloat] {
        d.drawCircle(fur, w * 0.13, CGPoint(w * ex, w * 0.2))
        d.drawCircle(Color(argb: 0xFFF8BBD0), w * 0.075, CGPoint(w * ex, w * 0.2))
    }
    d.drawCircle(fur, w * 0.4, CGPoint(w * 0.5, w * 0.52))
    if puff > 0 { for cx in [0.2, 0.8] as [CGFloat] { d.drawCircle(fur, w * (0.1 + 0.07 * puff), CGPoint(w * cx, w * 0.66)) } }
    d.drawOval(Color(argb: 0xFFD7CCC8), CGPoint(w * 0.17, w * 0.42), CGSize(w * 0.66, w * 0.5))
    for cx in [0.24, 0.76] as [CGFloat] { d.drawCircle(Color(argb: 0x55F48FB1), w * (0.04 + 0.02 * puff), CGPoint(w * cx, w * 0.48)) }
    drawFaceEyes(d, happy, eyesShut, 0.36, [0.37, 0.63])
    d.drawOval(Color(argb: 0xFF4E342E), CGPoint(w * 0.445, w * 0.43), CGSize(w * 0.11, w * 0.075))
    d.drawCircle(Color.white.copy(alpha: 0.5), w * 0.012, CGPoint(w * 0.475, w * 0.45))
    drawFaceMouth(d, puff, smile, Color(argb: 0xFF6D4C41))
}

/** A lion's face: a big fluffy mane all round, ears on top of it. */
private func drawLion(_ d: DrawScope, _ happy: Bool, _ eyesShut: Bool, _ puff: CGFloat, _ smile: CGFloat) {
    let w = d.size.width
    let mane = Color(argb: 0xFFC4621C), fur = Color(argb: 0xFFFFC94A)
    let center = CGPoint(w * 0.5, w * 0.5)
    for k in 0..<14 {
        let a = CGFloat(k) * 2 * .pi / 14
        d.drawCircle(mane, w * 0.1, center + CGPoint(cos(a), sin(a)) * (w * 0.385))
    }
    d.drawCircle(Color(argb: 0xFFE07A2B), w * 0.445, center)
    for ex in [0.2, 0.8] as [CGFloat] {
        d.drawCircle(fur, w * 0.09, CGPoint(w * ex, w * 0.2))
        d.drawCircle(mane, w * 0.05, CGPoint(w * ex, w * 0.2))
    }
    d.drawCircle(fur, w * 0.39, CGPoint(w * 0.5, w * 0.52))
    if puff > 0 { for cx in [0.2, 0.8] as [CGFloat] { d.drawCircle(fur, w * (0.1 + 0.07 * puff), CGPoint(w * cx, w * 0.66)) } }
    d.drawOval(Color(argb: 0xFFFFF1C9), CGPoint(w * 0.17, w * 0.42), CGSize(w * 0.66, w * 0.5))
    for cx in [0.24, 0.76] as [CGFloat] { d.drawCircle(Color(argb: 0x55FF7F8F), w * (0.04 + 0.02 * puff), CGPoint(w * cx, w * 0.48)) }
    drawFaceEyes(d, happy, eyesShut, 0.36, [0.37, 0.63])
    let nose = Path { p in
        p.moveTo(w * 0.445, w * 0.43); p.quadraticBezierTo(w * 0.5, w * 0.41, w * 0.555, w * 0.43)
        p.quadraticBezierTo(w * 0.52, w * 0.505, w * 0.5, w * 0.505); p.quadraticBezierTo(w * 0.48, w * 0.505, w * 0.445, w * 0.43); p.close()
    }
    d.drawPath(nose, Color(argb: 0xFF7A4320))
    drawFaceMouth(d, puff, smile, Color(argb: 0xFF7A4320))
}

/** A crocodile, head on: eyes on two bumps on top, a long snout, a big pale jaw. */
private func drawCrocodile(_ d: DrawScope, _ happy: Bool, _ eyesShut: Bool, _ puff: CGFloat, _ smile: CGFloat) {
    let w = d.size.width
    let skinC = Color(argb: 0xFF66BB6A), dark = Color(argb: 0xFF43A047)
    for x in [0.38, 0.5, 0.62] as [CGFloat] {
        d.drawPath(Path { p in p.moveTo(w * (x - 0.07), w * 0.2); p.quadraticBezierTo(w * x, w * 0.02, w * (x + 0.07), w * 0.2); p.close() }, dark)
    }
    for y in [0.36, 0.52, 0.68, 0.84] as [CGFloat] { for side in [-1.0, 1.0] as [CGFloat] { d.drawCircle(dark, w * 0.06, CGPoint(w * (0.5 + side * 0.4), w * y)) } }
    for ex in [0.29, 0.71] as [CGFloat] {
        d.drawCircle(skinC, w * 0.14, CGPoint(w * ex, w * 0.2))
        d.drawCircle(.white, w * 0.085, CGPoint(w * ex, w * 0.19))
    }
    d.drawRoundRect(skinC, CGPoint(w * 0.1, w * 0.2), CGSize(w * 0.8, w * 0.76), w * 0.27)
    if puff > 0 { for cx in [0.14, 0.86] as [CGFloat] { d.drawCircle(skinC, w * (0.1 + 0.06 * puff), CGPoint(w * cx, w * 0.66)) } }
    d.drawRoundRect(Color(argb: 0xFFDCEDC8), CGPoint(w * 0.17, w * 0.5), CGSize(w * 0.66, w * 0.42), w * 0.2)
    drawFaceEyes(d, happy, eyesShut, 0.19, [0.29, 0.71])
    d.drawRoundRect(dark, CGPoint(w * 0.2, w * 0.34), CGSize(w * 0.6, w * 0.18), w * 0.09)
    for nx in [0.42, 0.58] as [CGFloat] { d.drawOval(Color(argb: 0xFF2E7D32), CGPoint(w * (nx - 0.03), w * 0.4), CGSize(w * 0.06, w * 0.05)) }
    d.drawCircle(skinC, w * 0.016, CGPoint(w * 0.28, w * 0.41))
    d.drawCircle(skinC, w * 0.016, CGPoint(w * 0.72, w * 0.41))
    drawFaceMouth(d, puff, smile, Color(argb: 0xFF2E7D32))
}

/** One tooth, with `dirt` (0..1) of fuzz and a crumb on it; a glint once clean. */
private func drawTooth(_ d: DrawScope, _ at: CGPoint, _ dirt: CGFloat, _ seed: Int) {
    let w = d.size.width
    let c = CGPoint(at.x * w, at.y * w)
    let tw = w * toothW, th = w * toothH
    let top = seed < 5
    d.drawRoundRect(.white, c - CGPoint(tw / 2, th / 2), CGSize(tw, th), tw * (top ? 0.25 : 0.35))
    if dirt <= 0 {
        d.drawLine(Color(argb: 0xFFB3E5FC), c + CGPoint(-tw * 0.2, -th * 0.25), c + CGPoint(-tw * 0.2, th * 0.1), tw * 0.12, .round)
        return
    }
    var rnd = SeededRandom(seed)
    let plaque = Color(argb: 0xFFFFD54F).copy(alpha: Double(0.9 * dirt))
    d.drawCircle(plaque, tw * 0.32, c + CGPoint((rnd.nextFloat() - 0.5) * tw * 0.3, (rnd.nextFloat() - 0.5) * th * 0.3))
    d.drawCircle(plaque, tw * 0.2, c + CGPoint((rnd.nextFloat() - 0.5) * tw * 0.5, (rnd.nextFloat() - 0.5) * th * 0.5))
    let crumb = [Color(argb: 0xFF8D6E63), Color(argb: 0xFF66BB6A), Color(argb: 0xFFEF5350)][seed % 3]
    d.drawCircle(crumb.copy(alpha: Double(dirt)), tw * 0.12, c + CGPoint(tw * 0.18, th * (top ? 0.22 : -0.22)))
}

/** A toothbrush with its head at `at` (fractions of the box's width), `pasteOn` (0..1) of toothpaste on it. */
private func drawBrush(_ d: DrawScope, _ at: CGPoint, pasteOn: CGFloat, scale: CGFloat) {
    let w = d.size.width
    let c = CGPoint(at.x * w, at.y * w)
    let s = w * 0.12 * scale
    d.rotate(-25, pivot: c) { r in
        r.drawRoundRect(Color(argb: 0xFF42A5F5), c + CGPoint(-s * 0.4, s * 0.15), CGSize(s * 3.2, s * 0.35), s * 0.18)
        r.drawRoundRect(Color(argb: 0xFF1E88E5), c + CGPoint(-s * 0.5, -s * 0.05), CGSize(s * 1.1, s * 0.3), s * 0.1)
        for k in 0..<5 { r.drawLine(.white, c + CGPoint(-s * 0.4 + CGFloat(k) * s * 0.22, -s * 0.05), c + CGPoint(-s * 0.4 + CGFloat(k) * s * 0.22, -s * 0.4), s * 0.12, .round) }
        if pasteOn > 0 {
            let len = s * pasteOn
            r.drawLine(.white, c + CGPoint(-s * 0.45, -s * 0.5), c + CGPoint(-s * 0.45 + len, -s * 0.5), s * 0.28, .round)
            r.drawLine(Color(argb: 0xFF80CBC4), c + CGPoint(-s * 0.45, -s * 0.5), c + CGPoint(-s * 0.45 + len * 0.9, -s * 0.5), s * 0.1, .round)
        }
    }
}

/** A bubble of toothpaste foam; a pale rim so it shows on the white teeth. */
private func drawFoam(_ d: DrawScope, _ at: CGPoint, _ i: Int, _ left: CGFloat) {
    let r = d.size.width * (0.016 + CGFloat(i % 3) * 0.007)
    let c = CGPoint(at.x * d.size.width, at.y * d.size.width)
    d.drawCircle(Color.white.copy(alpha: Double(0.95 * left)), r, c)
    d.drawCircle(Color(argb: 0xFF80DEEA).copy(alpha: Double(0.9 * left)), r, c, style: Stroke(width: r * 0.22))
    d.drawCircle(Color(argb: 0xFFB2EBF2).copy(alpha: Double(left)), r * 0.28, c + CGPoint(-r * 0.35, -r * 0.35))
}

/** The toothpaste tube, flattening as it's squeezed, its nozzle towards the brush. */
private func drawTube(_ d: DrawScope, squeeze: CGFloat) {
    let w = d.size.width, h = d.size.height
    let fat = h * 0.11 * (1 - 0.35 * squeeze)
    let y = h * 0.72
    let tube = Path { p in
        p.moveTo(w * 0.06, y - h * 0.1); p.lineTo(w * 0.14, y - h * 0.1); p.lineTo(w * 0.56, y - fat)
        p.quadraticBezierTo(w * 0.64, y, w * 0.56, y + fat); p.lineTo(w * 0.14, y + h * 0.1); p.lineTo(w * 0.06, y + h * 0.1); p.close()
    }
    d.drawPath(tube, .white)
    d.drawPath(tube, Color(argb: 0xFF90A4AE), style: Stroke(width: w * 0.012))
    for k in 0...2 { d.drawLine(Color(argb: 0xFFB0BEC5), CGPoint(w * (0.075 + CGFloat(k) * 0.025), y - h * 0.09), CGPoint(w * (0.075 + CGFloat(k) * 0.025), y + h * 0.09), w * 0.008) }
    d.drawRoundRect(Color(argb: 0xFFEF5350), CGPoint(w * 0.24, y - fat * 0.55), CGSize(w * 0.22, fat * 1.1), fat * 0.3)
    d.drawRoundRect(Color(argb: 0xFF26A69A), CGPoint(w * 0.6, y - h * 0.045), CGSize(w * 0.09, h * 0.09), h * 0.02)
}

/** A cup of water, for the rinse. */
private func drawCup(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawPath(Path { p in p.moveTo(w * 0.15, h * 0.15); p.lineTo(w * 0.85, h * 0.15); p.lineTo(w * 0.72, h * 0.92); p.lineTo(w * 0.28, h * 0.92); p.close() }, Color(argb: 0xFFFFB74D))
    d.drawPath(Path { p in p.moveTo(w * 0.2, h * 0.3); p.lineTo(w * 0.8, h * 0.3); p.lineTo(w * 0.7, h * 0.85); p.lineTo(w * 0.3, h * 0.85); p.close() }, Color(argb: 0xFF4FC3F7))
    d.drawLine(Color.white.copy(alpha: 0.7), CGPoint(w * 0.3, h * 0.4), CGPoint(w * 0.36, h * 0.75), w * 0.06, .round)
}

/** Sparkles around a clean grin. */
private func drawSparkles(_ d: DrawScope, _ phase: CGFloat) {
    for (i, s0) in ([(0.27, 0.58), (0.73, 0.58), (0.5, 0.84), (0.12, 0.4), (0.88, 0.42)] as [(CGFloat, CGFloat)]).enumerated() {
        let t = (phase + CGFloat(i) * 0.2).truncatingRemainder(dividingBy: 1)
        let s = d.size.width * 0.035 * sin(t * .pi)
        let c = CGPoint(d.size.width * s0.0, d.size.width * s0.1)
        d.drawLine(.white, c - CGPoint(s, 0), c + CGPoint(s, 0), d.size.width * 0.009, .round)
        d.drawLine(.white, c - CGPoint(0, s), c + CGPoint(0, s), d.size.width * 0.009, .round)
        d.drawLine(Color(argb: 0xFFFFF59D), c - CGPoint(s * 0.5, s * 0.5), c + CGPoint(s * 0.5, s * 0.5), d.size.width * 0.006, .round)
    }
}
