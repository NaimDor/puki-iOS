import SwiftUI

private enum BedStep: Int, CaseIterable { case pajamas, teeth, teddy, light, stars }

private let bedZone = "bed"
private let night = Color(argb: 0xFF0B1240)

/** The stars on the ceiling: fractions of the screen. */
private let ceilingStars: [(CGFloat, CGFloat)] = [(0.36, 0.1), (0.5, 0.17), (0.62, 0.09), (0.76, 0.16), (0.88, 0.1)]

/**
 * Getting ready for bed in her room: pajamas on, teeth brushed, teddy in bed, the lamp off, and stars on the ceiling.
 * The room gets slowly darker, she's tucked in, and with the brain on, Puki tells a story. No confetti here: it's
 * bedtime.
 */
struct BedtimeRoutineScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gender) private var gender
    @StateObject private var dd = DragDropState()
    @StateObject private var game = StepGame(first: BedStep.pajamas, steps: BedStep.allCases.count)
    @StateObject private var help = Help()
    @State private var pajamasOn = false
    @State private var brushing = false
    @State private var teddyInBed = false
    @State private var lampOn = true
    @State private var starsOut = 0
    @State private var starring = false
    @State private var tucked = false

    var body: some View {
        let step = game.step
        let busy = game.betweenSteps || brushing || starring || tucked
        let girl = gender == .girl
        // The room gets dark slowly, step by step, and properly once the lamp is off
        let dark: Double = lampOn ? Double(step.rawValue) * 0.05 : tucked ? 0.72 : 0.62
        GameBackground {
            GeometryReader { g in
                let w = g.size.width, h = g.size.height
                let childSide = h * 0.62
                let childX = w * 0.31 - childSide / 2
                let childY = h * 0.9 - childSide * 0.93
                let tool = max(100, min(h * 0.24, 150))
                let headSide = min(h * 0.44, w * 0.26)
                ZStack(alignment: .topLeading) {
                    DrawCanvas { d in drawBedroom(d) }

                    // The child, in the day's clothes until the pajamas go on
                    Tweened(pajamasOn ? 1 : 0) { pajamas in
                        DrawCanvas { d in
                            drawToddler(d, girl: girl, worn: pajamas > 0.05 ? ["underwear": 1, "pajamas": min(1, pajamas)]
                                                                           : ["underwear": 1, "shirt": 1, "pants": 1, "socks": 1])
                        }
                    }
                    .animation(.spring(response: 0.36, dampingFraction: 0.5), value: pajamasOn)
                    .frame(width: childSide, height: childSide)
                    .opacity(tucked ? 0 : 1)
                    .animation(.easeInOut(duration: 0.9), value: tucked)
                    .offset(x: childX, y: childY)
                    if brushing {
                        BrushAtMouth().frame(width: childSide * 0.26, height: childSide * 0.26)
                            .offset(x: childX + childSide * 0.56, y: childY + childSide * 0.2)
                    }

                    // Asleep on the pillow, with the blanket pulled up
                    Group {
                        DrawCanvas { d in drawSleepingHead(d, girl: girl) }
                            .frame(width: headSide, height: headSide)
                            .offset(x: w * pillowCenter.x - headSide / 2, y: h * pillowCenter.y - headSide * 0.25)
                        DrawCanvas { d in drawTuckedBlanket(d) }
                    }
                    .opacity(tucked ? 1 : 0)
                    .animation(.easeInOut(duration: 0.9), value: tucked)
                    .allowsHitTesting(false)

                    // The bed takes teddy
                    DropZone(zoneId: bedZone) { Color.clear.frame(width: w * bedArea.width, height: h * bedArea.height) }
                        .offset(x: w * bedArea.minX, y: h * bedArea.minY)
                    if teddyInBed {
                        Pic("ic_tidy_teddy").frame(width: h * 0.22, height: h * 0.22).popIn()
                            .offset(x: w * 0.55, y: h * 0.42)
                    } else {
                        DragTarget(itemId: "teddy", onDragEnd: { zone in
                            if game.step == .teddy && zone == bedZone {
                                teddyInBed = true
                                game.done(.light, party: false)
                            }
                        }, tapToZone: step == .teddy ? bedZone : nil) {
                            Pic("ic_tidy_teddy").frame(width: h * 0.2, height: h * 0.2).hintGlow(step == .teddy && help.showHint).idleBob(amount: 0.4)
                        }
                        .offset(x: w * 0.5, y: h * 0.76)
                    }

                    // Everything dims together, except the window, the lamp and the stars
                    night.opacity(dark).animation(.easeInOut(duration: 1.4), value: dark).allowsHitTesting(false)
                    Tweened(lampOn ? 1 : 0) { v in DrawCanvas { d in drawLamp(d, v) } }
                        .animation(.easeInOut(duration: 0.7), value: lampOn).allowsHitTesting(false)
                    Tweened(CGFloat(step.rawValue) / 4) { v in DrawCanvas { d in drawNightWindow(d, v) } }
                        .animation(.easeInOut(duration: 1.4), value: step.rawValue).allowsHitTesting(false)
                    AmbientTimeline { t in
                        let twinkle = Ambient.loop(t, 5)
                        ZStack(alignment: .topLeading) {
                            ForEach(0..<starsOut, id: \.self) { i in
                                let s = min(h * 0.1, 56)
                                Pic("ic_ui_star").frame(width: s, height: s).popIn()
                                    .opacity(Double(0.75 + 0.25 * sin((twinkle + CGFloat(i) * 0.2) * 2 * .pi)))
                                    .offset(x: w * ceilingStars[i].0, y: h * ceilingStars[i].1)
                            }
                        }
                        .frame(width: w, height: h, alignment: .topLeading)
                    }
                    .allowsHitTesting(false)

                    // What each step answers to
                    if step == .pajamas && !busy {
                        ToolButton(size: tool, hint: help.showHint, color: Color(argb: 0xFFF3E5F5), onClick: {
                            app.sfx.play(.whoosh, volume: 0.5)
                            pajamasOn = true
                            game.done(.teeth, party: false)
                        }) { Pic("ic_clothing_pajamas").frame(width: tool * 0.8, height: tool * 0.8) }
                        .offset(x: w * 0.5, y: h * 0.34)
                    }
                    if step == .teeth && !busy {
                        ToolButton(size: tool, hint: help.showHint, onClick: {
                            brushing = true
                            Task {
                                for _ in 0..<3 {
                                    app.sfx.play(.maracas, volume: 0.35)
                                    await delay(450)
                                }
                                brushing = false
                                game.done(.teddy, party: false)
                            }
                        }) { Pic("ic_myday_toothbrush").frame(width: tool * 0.8, height: tool * 0.8) }
                        .offset(x: w * 0.5, y: h * 0.34)
                    }
                    if step == .light && !busy {
                        TapArea(hint: help.showHint) {
                            app.sfx.play(.pop)
                            lampOn = false
                            game.done(.stars, party: false)
                        }
                        .frame(width: w * lampArea.width, height: h * lampArea.height)
                        .offset(x: w * lampArea.minX, y: h * lampArea.minY)
                    }
                    if step == .stars && !busy {
                        TapArea(hint: help.showHint) {
                            starring = true
                            Task {
                                for i in 0..<ceilingStars.count {
                                    app.sfx.play(.starCollect, volume: 0.5)
                                    starsOut = i + 1
                                    await delay(420)
                                }
                                game.done(nil, party: false)
                                await delay(600)
                                tucked = true
                            }
                        }
                        .frame(width: w * windowArea.width, height: h * windowArea.height)
                        .offset(x: w * windowArea.minX, y: h * windowArea.minY)
                    }

                    VStack {
                        GameTopBar(onBack: onBack, roundIndex: step.rawValue, totalRounds: BedStep.allCases.count, starsEarned: game.stars)
                        Spacer()
                    }
                    .padding(16)
                    .frame(width: w, height: h)
                }
                .frame(width: w, height: h, alignment: .topLeading)
                .environment(\.layoutDirection, .leftToRight)  // (the room doesn't mirror)
            }
            .ignoresSafeArea()

            // Puki's bedtime story, when the brain is reachable; then the quiet end card
            if tucked { BedtimeStory { game.finish() } }
            if game.sessionComplete { RoundCompleteSheet(starsEarned: game.stars, onContinue: onPlayAgain, onHome: onBack, calm: true) }
        }
        .environmentObject(dd)
        .task { game.attach(app, island: "bedtime") }
        .onAppear {
            app.companion.wake()  // now, so the story is ready by the end
            app.music.playTrack("music_night")  // the calm night track while getting ready for bed
        }
        .onDisappear {
            app.music.duck(false)
            app.music.playTrack("music_hub")
        }
        .helpLoop(help, key: step.rawValue, active: !busy) {
            app.voice.play(["voice_bedtime_pajamas", "voice_bedtime_brush", "voice_bedtime_teddy", "voice_bedtime_light", "voice_bedtime_stars"][step.rawValue], language)
        }
        .modifier(BedPrompt(step: step))
    }
}

private struct BedPrompt: ViewModifier {
    let step: BedStep
    func body(content: Content) -> some View {
        switch step {
        case .pajamas: content.roundPrompt(0, "בשגרת השינה: לובשים פיג'מה.", "Bedtime: putting on pajamas.", "voice_bedtime_pajamas")
        case .teeth: content.roundPrompt(1, "בשגרת השינה: מצחצחים שיניים.", "Bedtime: brushing teeth.", "voice_bedtime_brush")
        case .teddy: content.roundPrompt(2, "בשגרת השינה: שמים את הדובי במיטה.", "Bedtime: putting teddy in bed.", "voice_bedtime_teddy")
        case .light: content.roundPrompt(3, "בשגרת השינה: מכבים את האור.", "Bedtime: turning off the light.", "voice_bedtime_light")
        case .stars: content.roundPrompt(4, "בשגרת השינה: כוכבים בשמיים, לילה טוב.", "Bedtime: stars in the sky, good night.", "voice_bedtime_stars")
        }
    }
}

/** A part of the room that answers a tap, glowing as a hint. */
private struct TapArea: View {
    let hint: Bool
    let onTap: () -> Void
    var body: some View {
        Color.clear.contentShape(Rectangle()).hintGlow(hint).onTapGesture(perform: onTap)
    }
}

/** The toothbrush going back and forth at her mouth. */
private struct BrushAtMouth: View {
    var body: some View {
        TimelineView(.animation) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            Pic("ic_myday_toothbrush").offset(x: CGFloat(sin(t * 2 * .pi / 0.64)) * 8)
        }
    }
}

/**
 * After the bedtime routine, Puki tells the child about her day: his brain retells two or three of the things she did
 * today (DayLog), calm and in the past tense, under the moon. A line that names fewer than two of them doesn't pass
 * (RecapContent.eventsNamed); with no brain, or fewer than two things today, a quiet moment instead.
 */
struct BedtimeStory: View {
    let onFinished: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @State private var showing = false
    @State private var said = ""

    var body: some View {
        ZStack {
            if showing {
                ZStack {
                    Color(argb: 0xF51A237E).ignoresSafeArea()
                    VStack {
                        Pic("ic_bedtime_moon").frame(width: 220, height: 220)
                        // What he said, for the grown-up reading along
                        Text(said).font(PukiFont.titleLarge).foregroundColor(.white.opacity(0.85)).multilineTextAlignment(.center)
                            .padding(.top, 24).padding(.horizontal, 48)
                    }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.9), value: showing)
        .task {
            guard await delay(2500) else { return }  // let "good night" land first
            let events = Array(DayLog.today().suffix(5))  // (the brain learned days of two to five things)
            if !app.companion.isReady || events.count < 2 {
                guard await delay(3500) else { return }  // no recap tonight: a quiet moment with her tucked in
                onFinished()
                return
            }
            app.music.duck(true)  // quieter music under the recap
            showing = true
            said = await app.companion.line("recap", input: RecapContent.input(english: language == .english, events: events)) ?? ""
            await delay(said.isEmpty ? 1500 : 3000)
            app.music.duck(false)
            showing = false
            await delay(900)  // (the fade out)
            onFinished()
        }
    }
}

// ---------- The bedroom ----------

/*
 * The bedroom, in fractions of the screen so it fits a tablet and a phone on its side: the window on the left, the
 * bed in the middle with its pillow on the right, and the lamp beside the pillow.
 */
private let windowArea = CGRect(x: 0.04, y: 0.2, width: 0.175, height: 0.3)
private let bedArea = CGRect(x: 0.4, y: 0.5, width: 0.42, height: 0.38)
private let lampArea = CGRect(x: 0.83, y: 0.3, width: 0.14, height: 0.46)
private let pillowCenter = CGPoint(0.74, 0.56)
private let floorY: CGFloat = 0.76
private let blanket = Color(argb: 0xFF7E57C2)
private let blanketLight = Color(argb: 0xFF9575CD)
private let wood = Color(argb: 0xFFB98352)
private let woodDark = Color(argb: 0xFF8D6240)

/** The room, drawn once: wall, floor, rug, bed, shelf and the lamp's table. */
private func drawBedroom(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let floor = h * floorY
    // Wall with soft stripes, then the floorboards
    d.drawRect(Color(argb: 0xFFD9D2F0), .zero, CGSize(w, floor))
    var x: CGFloat = 0
    while x < w {
        d.drawRect(Color(argb: 0xFFE4DEF6), CGPoint(x, 0), CGSize(w * 0.04, floor))
        x += w * 0.08
    }
    d.drawRect(Color(argb: 0xFFC89B6C), CGPoint(0, floor), CGSize(w, h - floor))
    for k in 1...3 { d.drawLine(Color(argb: 0xFFB98352), CGPoint(0, floor + (h - floor) * CGFloat(k) / 4), CGPoint(w, floor + (h - floor) * CGFloat(k) / 4), h * 0.004) }
    d.drawRect(.white, CGPoint(0, floor - h * 0.012), CGSize(w, h * 0.012))
    // A round rug
    d.drawOval(Color(argb: 0xFFF48FB1), CGPoint(w * 0.1, h * 0.84), CGSize(w * 0.36, h * 0.12))
    d.drawOval(Color(argb: 0xFFF8BBD0), CGPoint(w * 0.13, h * 0.855), CGSize(w * 0.3, h * 0.09))
    drawShelf(d, w, h)
    drawBed(d, w, h)
    // The lamp's little table
    d.drawRoundRect(wood, CGPoint(w * 0.845, h * 0.56), CGSize(w * 0.11, h * 0.025), h * 0.01)
    d.drawRect(woodDark, CGPoint(w * 0.86, h * 0.585), CGSize(w * 0.012, h * 0.17))
    d.drawRect(woodDark, CGPoint(w * 0.928, h * 0.585), CGSize(w * 0.012, h * 0.17))
}

private func drawShelf(_ d: DrawScope, _ w: CGFloat, _ h: CGFloat) {
    let y = h * 0.3
    d.drawRoundRect(wood, CGPoint(w * 0.46, y), CGSize(w * 0.3, h * 0.018), h * 0.008)
    // Books and two blocks on it
    let books = [Color(argb: 0xFFEF5350), Color(argb: 0xFF42A5F5), Color(argb: 0xFFFFCA28), Color(argb: 0xFF66BB6A)]
    for (i, c) in books.enumerated() {
        let bookH = h * (0.09 + CGFloat(i % 2) * 0.02)
        d.drawRoundRect(c, CGPoint(w * (0.48 + CGFloat(i) * 0.022), y - bookH), CGSize(w * 0.018, bookH), w * 0.003)
    }
    d.drawRoundRect(Color(argb: 0xFFAB47BC), CGPoint(w * 0.64, y - h * 0.06), CGSize(h * 0.06, h * 0.06), h * 0.008)
    d.drawRoundRect(Color(argb: 0xFF26A69A), CGPoint(w * 0.685, y - h * 0.045), CGSize(h * 0.045, h * 0.045), h * 0.008)
}

private func drawBed(_ d: DrawScope, _ w: CGFloat, _ h: CGFloat) {
    // Legs, frame, and the headboard behind the pillow
    for fx in [0.42, 0.79] as [CGFloat] { d.drawRect(woodDark, CGPoint(w * fx, h * 0.8), CGSize(w * 0.016, h * 0.075)) }
    d.drawRoundRect(wood, CGPoint(w * 0.8, h * 0.42), CGSize(w * 0.024, h * 0.45), w * 0.012)
    d.drawRoundRect(wood, CGPoint(w * 0.405, h * 0.56), CGSize(w * 0.02, h * 0.31), w * 0.01)
    d.drawRoundRect(wood, CGPoint(w * 0.41, h * 0.72), CGSize(w * 0.4, h * 0.09), h * 0.012)
    // The mattress, the pillow and the blanket
    d.drawRoundRect(Color(argb: 0xFFFFF8E1), CGPoint(w * 0.42, h * 0.61), CGSize(w * 0.38, h * 0.12), h * 0.03)
    d.drawRoundRect(.white, CGPoint(w * 0.68, h * 0.5), CGSize(w * 0.115, h * 0.13), h * 0.05)
    d.drawArc(Color(argb: 0xFFE0E0E0), startAngle: 20, sweepAngle: 140, useCenter: false, CGPoint(w * 0.69, h * 0.53), CGSize(w * 0.095, h * 0.08), style: Stroke(width: h * 0.005))
    drawBlanket(d, w, h, 0.585)
}

/** The blanket, its top at `top` (fraction of the height): raised over a child in bed. */
private func drawBlanket(_ d: DrawScope, _ w: CGFloat, _ h: CGFloat, _ top: CGFloat) {
    d.drawRoundRect(blanket, CGPoint(w * 0.42, h * top), CGSize(w * 0.265, h * (0.76 - top)), h * 0.035)
    d.drawRoundRect(blanketLight, CGPoint(w * 0.655, h * top), CGSize(w * 0.03, h * (0.76 - top)), h * 0.012)
    for k in 0..<4 {
        d.drawCircle(Color(argb: 0xFFFFEE58), h * 0.012, CGPoint(w * (0.46 + CGFloat(k) * 0.055), h * (top + 0.05 + CGFloat(k % 2) * 0.05)))
    }
}

/** The blanket pulled up over her, drawn over the child's head on the pillow. */
private func drawTuckedBlanket(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    drawBlanket(d, w, h, 0.555)
    d.drawOval(blanketLight.copy(alpha: 0.5), CGPoint(w * 0.47, h * 0.555), CGSize(w * 0.2, h * 0.05))
}

/** The lamp on its table, lit by `on` (0..1). */
private func drawLamp(_ d: DrawScope, _ on: CGFloat) {
    let w = d.size.width, h = d.size.height
    let c = CGPoint(w * 0.9, h * 0.42)
    if on > 0 {
        d.drawCircle(Brush.radialGradient([Color(argb: 0xFFFFE082).copy(alpha: Double(0.67 * on)), .clear], center: c, radius: h * 0.42), h * 0.42, c)
    }
    d.drawRoundRect(woodDark, CGPoint(w * 0.893, h * 0.45), CGSize(w * 0.014, h * 0.11), w * 0.005)
    d.drawOval(wood, CGPoint(w * 0.875, h * 0.545), CGSize(w * 0.05, h * 0.02))
    let shade = Path { p in
        p.moveTo(w * 0.875, h * 0.345); p.lineTo(w * 0.925, h * 0.345); p.lineTo(w * 0.948, h * 0.46); p.lineTo(w * 0.852, h * 0.46); p.close()
    }
    d.drawPath(shade, lerp(Color(argb: 0xFF9E8FB8), Color(argb: 0xFFFFE082), Double(on)))
    d.drawLine(Color(argb: 0xFF6D4C41), CGPoint(w * 0.934, h * 0.46), CGPoint(w * 0.934, h * 0.53), h * 0.005)
    d.drawCircle(Color(argb: 0xFFFFCA28), h * 0.012, CGPoint(w * 0.934, h * 0.535))
}

/** The window at night, with curtains and the moon coming up as `moon` goes 0 → 1. */
private func drawNightWindow(_ d: DrawScope, _ moon: CGFloat) {
    let r = CGRect(x: d.size.width * windowArea.minX, y: d.size.height * windowArea.minY, width: d.size.width * windowArea.width, height: d.size.height * windowArea.height)
    d.drawRoundRect(Color(argb: 0xFFFFF8E1), r.origin - CGPoint(r.width * 0.05, r.width * 0.05), CGSize(r.width * 1.1, r.height + r.width * 0.1), r.width * 0.03)
    d.drawRect(Brush.verticalGradient([Color(argb: 0xFF1A237E), Color(argb: 0xFF3F51B5)], startY: r.minY, endY: r.maxY), r.origin, r.size)
    d.clip(Path(r)) { c2 in
        let c = CGPoint(r.minX + r.width * 0.62, r.maxY - r.height * (0.1 + 0.62 * moon))
        let m = r.width * 0.17
        c2.drawCircle(Color(argb: 0x33FFF59D), m * 1.7, c)
        c2.drawCircle(Color(argb: 0xFFFFF59D), m, c)
        c2.drawCircle(Color(argb: 0xFF1F2A8A), m * 0.85, c + CGPoint(m * 0.5, -m * 0.2))  // a crescent
        for (fx, fy) in [(0.2, 0.25), (0.4, 0.6), (0.15, 0.75), (0.85, 0.2)] as [(CGFloat, CGFloat)] {
            c2.drawCircle(.white, r.width * 0.012, CGPoint(r.minX + r.width * fx, r.minY + r.height * fy))
        }
    }
    // The bars, and curtains tied back at the sides
    d.drawLine(Color(argb: 0xFFFFF8E1), CGPoint(r.midX, r.minY), CGPoint(r.midX, r.maxY), r.width * 0.025)
    d.drawLine(Color(argb: 0xFFFFF8E1), CGPoint(r.minX, r.midY), CGPoint(r.maxX, r.midY), r.width * 0.025)
    let curtain = Color(argb: 0xFFF48FB1)
    for side in [-1, 1] as [CGFloat] {
        let edge = side < 0 ? r.minX : r.maxX
        let p = Path { p in
            p.moveTo(edge - side * r.width * 0.12, r.minY - r.width * 0.05)
            p.lineTo(edge + side * r.width * 0.06, r.minY - r.width * 0.05)
            p.lineTo(edge + side * r.width * 0.06, r.maxY + r.width * 0.05)
            p.quadraticBezierTo(edge - side * r.width * 0.02, r.midY, edge - side * r.width * 0.12, r.minY - r.width * 0.05)
            p.close()
        }
        d.drawPath(p, curtain)
    }
    d.drawLine(woodDark, CGPoint(r.minX - r.width * 0.12, r.minY - r.width * 0.06), CGPoint(r.maxX + r.width * 0.12, r.minY - r.width * 0.06), r.width * 0.022, .round)
}
