import SwiftUI

private enum WashStep: Int, CaseIterable { case scrub, rinse, dry }

/** Which of a vehicle's six spots are muddy at each level: the rest starts out clean. */
private func muddySpots(_ level: Int) -> Set<Int> {
    switch level {
    case 1: return [0, 2, 4]
    case 2: return [0, 1, 3, 4]
    default: return Set(0...5)
    }
}
private let shineSeconds = 1.4
private let spotR: CGFloat = 0.11  // as a fraction of the vehicle's width
private let carColors = [Color(argb: 0xFFE53935), Color(argb: 0xFF1E88E5), Color(argb: 0xFFFDD835), Color(argb: 0xFF43A047), Color(argb: 0xFF8E24AA)]

/**
 * What comes in for a wash. Each fills a box twice as wide as tall and looks left, the way it drives off. `spots` are
 * where its mud goes, as fractions of that box, so they land on it.
 */
enum Vehicle: CaseIterable {
    case car, truck, bike
    var spots: [CGPoint] {
        switch self {
        case .car: return [CGPoint(0.2, 0.55), CGPoint(0.36, 0.38), CGPoint(0.52, 0.6), CGPoint(0.66, 0.42), CGPoint(0.82, 0.58), CGPoint(0.42, 0.7)]
        case .truck: return [CGPoint(0.2, 0.56), CGPoint(0.46, 0.3), CGPoint(0.52, 0.58), CGPoint(0.7, 0.34), CGPoint(0.84, 0.58), CGPoint(0.28, 0.64)]
        case .bike: return [CGPoint(0.125, 0.62), CGPoint(0.42, 0.33), CGPoint(0.52, 0.73), CGPoint(0.59, 0.42), CGPoint(0.875, 0.62), CGPoint(0.2, 0.17)]
        }
    }
}

/** Washing a muddy car, truck or bike: scrub with the sponge, rinse with the hose, dry with the towel. */
struct CarWashScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var dd = DragDropState()
    @StateObject private var game = StepGame(first: WashStep.scrub, steps: WashStep.allCases.count)
    @StateObject private var help = Help()
    @State private var level = 0
    @State private var carColor = carColors.randomElement()!
    @State private var vehicle = Vehicle.allCases.randomElement()!
    @State private var mud: [CGFloat] = []    // 1: muddy, 0: clean
    @State private var drops: [CGFloat] = []  // water left after the rinse
    @State private var bubbles: [CGPoint] = []
    @State private var tool: CGPoint?  // the sponge or towel, under the finger
    @State private var lastDrag: CGPoint?
    @State private var spraying = false
    @State private var spray = Clip(2.2)
    @State private var driveOff: CGFloat = 0

    var body: some View {
        let step = game.step
        GameBackground {
            DrawCanvas { d in drawGarage(d) }.ignoresSafeArea()
            if level > 0 {
                VStack(spacing: 0) {
                    GameTopBar(onBack: onBack, roundIndex: step.rawValue, totalRounds: WashStep.allCases.count, starsEarned: game.stars)
                    GeometryReader { g in
                        let carW = min(g.size.width * 0.72, g.size.height * 1.5)
                        VStack {
                            Spacer()
                            carView(carW, step)
                            Spacer()
                            // The hose, for the rinse
                            ZStack {
                                if step == .rinse && !spraying {
                                    ToolButton(size: carW * 0.18, hint: help.showHint, color: Color(argb: 0xFF29B6F6), onClick: rinse) {
                                        DrawCanvas { d in drawHose(d) }.frame(width: carW * 0.12, height: carW * 0.12)
                                    }
                                }
                            }
                            .frame(width: carW * 0.2, height: carW * 0.2)
                            Spacer()
                        }
                        .frame(width: g.size.width, height: g.size.height)
                    }
                }
                .padding(16)
            }
            ConfettiOverlay(show: game.celebrate)
            if game.sessionComplete { RoundCompleteSheet(starsEarned: game.stars, onContinue: onPlayAgain, onHome: onBack) }
        }
        .environmentObject(dd)
        .task {
            game.attach(app, island: "garage")
            let lv = game.session?.level() ?? 1
            let muddy = muddySpots(lv)
            mud = (0..<vehicle.spots.count).map { muddy.contains($0) ? 1 : 0 }
            drops = mud
            level = lv
        }
        .onChange(of: step.rawValue) { _, _ in tool = nil; lastDrag = nil }
        .helpLoop(help, key: step.rawValue, active: !game.celebrate && !spraying && !game.sessionComplete && level > 0,
                  onAssisted: { game.session?.slip() }) {
            app.voice.play(step == .scrub ? "voice_car_scrub" : step == .rinse ? "voice_car_rinse" : "voice_car_dry", language)
        }
        .modifier(WashPrompt(step: step, vehicle: vehicle))
    }

    private func carView(_ carW: CGFloat, _ step: WashStep) -> some View {
        let w = carW, h = carW * 0.5
        let spots = vehicle.spots
        let clean = game.sessionComplete || driveOff > 0 || (step == .dry && !drops.isEmpty && drops.allSatisfy { $0 <= 0 })
        return AmbientTimeline { t in
            let now = Date()
            let sv = spray.value(now)
            let bubblesLeft = spraying ? 1 - easeInOut(sv) : 1
            DrawCanvas { d in
                switch vehicle {
                case .car: drawCar(d, carColor)
                case .truck: drawTruck(d, carColor)
                case .bike: drawBike(d, carColor)
                }
                for (i, s) in spots.enumerated() {
                    if i < mud.count && mud[i] > 0 { drawCarMud(d, s, mud[i], i) }
                    if step == .dry && i < drops.count && drops[i] > 0 { drawWashDrop(d, s, drops[i]) }
                }
                for (i, b) in bubbles.enumerated() {
                    d.drawCircle(Color.white.copy(alpha: Double(0.85 * bubblesLeft)), d.size.width * (0.018 + CGFloat(i % 3) * 0.008), CGPoint(b.x * d.size.width, b.y * d.size.height))
                }
                if spraying && sv > 0.01 && sv < 0.99 { drawSpray(d, sv) }
                if clean { drawShine(d, Ambient.loop(t, shineSeconds)) }
                if let p = tool { drawWashTool(d, p, sponge: step == .scrub) }
            }
        }
        .frame(width: w, height: h)
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard step != .rinse else { return }
                let p = CGPoint(v.location.x / w, v.location.y / h)
                if let last = lastDrag {
                    let dist = hypot(v.location.x - last.x, v.location.y - last.y)
                    if dist > 0 {
                        tool = p
                        rub(p, dist / w * 2.2)
                    }
                }
                lastDrag = v.location
            }
            .onEnded { v in
                let moved = hypot(v.translation.width, v.translation.height)
                if moved < 10 && step != .rinse { rub(CGPoint(v.location.x / w, v.location.y / h), 0.34) }  // a tap
                tool = nil
                lastDrag = nil
            })
        .testID("scrub")
        .offset(x: -driveOff * w * 1.6)  // forward, the way its face looks
        .environment(\.layoutDirection, .leftToRight)
    }

    /** Rubbing at `p` (fractions of the car): wipes the mud or the drops near it by `amount`. */
    private func rub(_ p: CGPoint, _ amount: CGFloat) {
        let step = game.step
        var list = step == .scrub ? mud : drops
        if list.allSatisfy({ $0 <= 0 }) || game.betweenSteps { return }
        var touched = false
        for (i, s) in vehicle.spots.enumerated() where i < list.count {
            let dx = p.x - s.x
            let dy = (p.y - s.y) * 0.5  // the box is twice as wide as tall
            if list[i] > 0 && dx * dx + dy * dy < spotR * spotR * 2.2 {
                list[i] = max(0, list[i] - amount)
                touched = true
                if list[i] == 0 { app.sfx.play(.pop, volume: 0.7) }
            }
        }
        if step == .scrub { mud = list } else { drops = list }
        if touched { help.touched() }
        if touched && step == .scrub && bubbles.count < 60 && Bool.random() {
            bubbles.append(p + CGPoint(CGFloat.random(in: 0..<1) * 0.06 - 0.03, CGFloat.random(in: 0..<1) * 0.1 - 0.05))
        }
        guard list.allSatisfy({ $0 <= 0 }) else { return }
        if step == .scrub { game.done(.rinse); return }
        // All dry: it shines, beeps and drives off
        switch vehicle {
        case .car:
            app.voice.play("voice_car_clean", language) {
                app.companion.react("בשטיפת המכונית: סיבנו, שטפו וניגבו, והמכונית נקייה!", "At the car wash the child scrubbed, rinsed and dried the car, and it's clean!") {}
            }
        case .truck:
            app.voice.play("voice_truck_clean", language) {
                app.companion.react("בשטיפת המשאית: סיבנו, שטפו וניגבו, והמשאית נקייה!", "At the car wash the child scrubbed, rinsed and dried the truck, and it's clean!") {}
            }
        case .bike:
            app.voice.play("voice_bike_clean", language) {
                app.companion.react("בשטיפת האופניים: סיבנו, שטפו וניגבו, והאופניים נקיים!", "At the car wash the child scrubbed, rinsed and dried the bike, and it's clean!") {}
            }
        }
        game.done(nil)
        Task {
            await delay(1200)
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 1.6)) { driveOff = 1 }
            await delay(1600)
            game.finish()
        }
    }

    private func rinse() {
        spraying = true
        app.sfx.play(.whoosh)
        spray.run()
        Task {
            await delay(2200)
            bubbles = []
            spraying = false
            game.done(.dry)
        }
    }
}

private func easeInOut(_ x: CGFloat) -> CGFloat { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }

private struct WashPrompt: ViewModifier {
    let step: WashStep
    let vehicle: Vehicle
    func body(content: Content) -> some View {
        switch (vehicle, step) {
        case (.car, .scrub): content.roundPrompt(0, "בשטיפת המכונית: המכונית מלאה בוץ, משפשפים עם ספוג.", "At the car wash: the car is all muddy, scrubbing it with a sponge.", "voice_car_intro")
        case (.car, .rinse): content.roundPrompt(1, "בשטיפת המכונית: שוטפים את הקצף עם הצינור.", "At the car wash: rinsing the bubbles off with the hose.", "voice_car_rinse")
        case (.car, .dry): content.roundPrompt(2, "בשטיפת המכונית: מנגבים את המכונית עם מגבת.", "At the car wash: drying the car with a towel.", "voice_car_dry")
        case (.truck, .scrub): content.roundPrompt(0, "בשטיפת המשאית: המשאית מלאה בוץ, משפשפים עם ספוג.", "At the car wash: the truck is all muddy, scrubbing it with a sponge.", "voice_truck_intro")
        case (.truck, .rinse): content.roundPrompt(1, "בשטיפת המשאית: שוטפים את הקצף עם הצינור.", "At the car wash: rinsing the bubbles off the truck with the hose.", "voice_car_rinse")
        case (.truck, .dry): content.roundPrompt(2, "בשטיפת המשאית: מנגבים את המשאית עם מגבת.", "At the car wash: drying the truck with a towel.", "voice_car_dry")
        case (.bike, .scrub): content.roundPrompt(0, "בשטיפת האופניים: האופניים מלאים בוץ, משפשפים עם ספוג.", "At the car wash: the bike is all muddy, scrubbing it with a sponge.", "voice_bike_intro")
        case (.bike, .rinse): content.roundPrompt(1, "בשטיפת האופניים: שוטפים את הקצף עם הצינור.", "At the car wash: rinsing the bubbles off the bike with the hose.", "voice_car_rinse")
        case (.bike, .dry): content.roundPrompt(2, "בשטיפת האופניים: מנגבים את האופניים עם מגבת.", "At the car wash: drying the bike with a towel.", "voice_car_dry")
        }
    }
}

// ---------- Drawing ----------

private let black = Color(argb: 0xFF000000)

/** A garage wall and a wet, tiled floor. */
private func drawGarage(_ d: DrawScope) {
    d.drawRect(Brush.verticalGradient([Color(argb: 0xFFCFD8DC), Color(argb: 0xFFECEFF1)]))
    let floorTop = d.size.height * 0.74
    d.drawRect(Color(argb: 0xFF90A4AE), CGPoint(0, floorTop), CGSize(d.size.width, d.size.height - floorTop))
    var x: CGFloat = 0
    while x < d.size.width {
        d.drawLine(Color(argb: 0x22FFFFFF), CGPoint(x, floorTop), CGPoint(x - d.size.width * 0.1, d.size.height), 3)
        x += 90
    }
    // Puddles
    d.drawOval(Color(argb: 0x5581D4FA), CGPoint(d.size.width * 0.15, d.size.height * 0.84), CGSize(d.size.width * 0.22, d.size.height * 0.05))
    d.drawOval(Color(argb: 0x5581D4FA), CGPoint(d.size.width * 0.62, d.size.height * 0.88), CGSize(d.size.width * 0.18, d.size.height * 0.04))
}

/** A friendly little car, side on, filling its box (twice as wide as tall). */
func drawCar(_ d: DrawScope, _ color: Color) {
    let w = d.size.width, h = d.size.height
    let dark = lerp(color, black, 0.25)
    // Body and cabin
    let body = Path { p in
        p.moveTo(w * 0.06, h * 0.78); p.lineTo(w * 0.06, h * 0.55); p.quadraticBezierTo(w * 0.07, h * 0.44, w * 0.18, h * 0.42)
        p.lineTo(w * 0.3, h * 0.4); p.lineTo(w * 0.4, h * 0.18); p.quadraticBezierTo(w * 0.43, h * 0.12, w * 0.5, h * 0.12)
        p.lineTo(w * 0.68, h * 0.12); p.quadraticBezierTo(w * 0.74, h * 0.12, w * 0.77, h * 0.2); p.lineTo(w * 0.84, h * 0.4)
        p.quadraticBezierTo(w * 0.95, h * 0.43, w * 0.95, h * 0.56); p.lineTo(w * 0.95, h * 0.78); p.close()
    }
    d.drawPath(body, color)
    d.drawRoundRect(dark, CGPoint(w * 0.06, h * 0.7), CGSize(w * 0.89, h * 0.08), h * 0.04)
    // Windows
    d.drawPath(Path { p in p.moveTo(w * 0.37, h * 0.4); p.lineTo(w * 0.44, h * 0.2); p.lineTo(w * 0.57, h * 0.2); p.lineTo(w * 0.57, h * 0.4); p.close() }, Color(argb: 0xFFB3E5FC))
    d.drawPath(Path { p in p.moveTo(w * 0.61, h * 0.2); p.lineTo(w * 0.72, h * 0.2); p.lineTo(w * 0.78, h * 0.4); p.lineTo(w * 0.61, h * 0.4); p.close() }, Color(argb: 0xFFB3E5FC))
    d.drawLine(Color.white.copy(alpha: 0.6), CGPoint(w * 0.46, h * 0.24), CGPoint(w * 0.42, h * 0.34), w * 0.01, .round)
    // A smiling face at the front: headlight eye and a grille smile
    d.drawCircle(Color(argb: 0xFFFFF59D), h * 0.06, CGPoint(w * 0.12, h * 0.52))
    d.drawCircle(Color(argb: 0xFF3E2723), h * 0.025, CGPoint(w * 0.115, h * 0.52))
    d.drawArc(Color(argb: 0xFF3E2723), startAngle: 20, sweepAngle: 140, useCenter: false, CGPoint(w * 0.06, h * 0.55), CGSize(w * 0.1, h * 0.1), style: Stroke(width: h * 0.02, cap: .round))
    d.drawCircle(Color(argb: 0xFFFF8A65), h * 0.03, CGPoint(w * 0.9, h * 0.52))  // tail light
    d.drawLine(dark, CGPoint(w * 0.6, h * 0.42), CGPoint(w * 0.6, h * 0.68), w * 0.006)  // door
    d.drawLine(dark, CGPoint(w * 0.63, h * 0.48), CGPoint(w * 0.67, h * 0.48), w * 0.01, .round)  // handle
    // Wheels
    for cx in [0.26, 0.76] as [CGFloat] {
        d.drawCircle(Color(argb: 0xFF37474F), h * 0.16, CGPoint(w * cx, h * 0.8))
        d.drawCircle(Color(argb: 0xFFB0BEC5), h * 0.07, CGPoint(w * cx, h * 0.8))
    }
}

/** A little truck: the cab with its smile at the front, a big box behind. */
func drawTruck(_ d: DrawScope, _ color: Color) {
    let w = d.size.width, h = d.size.height
    let dark = lerp(color, black, 0.25)
    let back = lerp(color, .white, 0.55)
    // The box, with planks
    d.drawRoundRect(back, CGPoint(w * 0.36, h * 0.08), CGSize(w * 0.59, h * 0.64), h * 0.05)
    for k in 1...4 {
        let x = w * (0.36 + CGFloat(k) * 0.118)
        d.drawLine(lerp(back, black, 0.12), CGPoint(x, h * 0.1), CGPoint(x, h * 0.68), w * 0.005)
    }
    d.drawRoundRect(lerp(back, black, 0.12), CGPoint(w * 0.36, h * 0.08), CGSize(w * 0.59, h * 0.05), h * 0.025)
    // The cab
    let cab = Path { p in
        p.moveTo(w * 0.05, h * 0.78); p.lineTo(w * 0.05, h * 0.55); p.quadraticBezierTo(w * 0.055, h * 0.45, w * 0.13, h * 0.43)
        p.lineTo(w * 0.17, h * 0.2); p.quadraticBezierTo(w * 0.19, h * 0.14, w * 0.24, h * 0.14); p.lineTo(w * 0.34, h * 0.14)
        p.quadraticBezierTo(w * 0.36, h * 0.14, w * 0.36, h * 0.2); p.lineTo(w * 0.36, h * 0.78); p.close()
    }
    d.drawPath(cab, color)
    d.drawRoundRect(dark, CGPoint(w * 0.05, h * 0.7), CGSize(w * 0.9, h * 0.08), h * 0.04)
    // The window
    d.drawPath(Path { p in p.moveTo(w * 0.17, h * 0.43); p.lineTo(w * 0.205, h * 0.22); p.lineTo(w * 0.32, h * 0.22); p.lineTo(w * 0.32, h * 0.43); p.close() }, Color(argb: 0xFFB3E5FC))
    d.drawLine(Color.white.copy(alpha: 0.6), CGPoint(w * 0.23, h * 0.26), CGPoint(w * 0.21, h * 0.36), w * 0.01, .round)
    // A smiling face at the front: headlight eye and a grille smile
    d.drawCircle(Color(argb: 0xFFFFF59D), h * 0.06, CGPoint(w * 0.11, h * 0.52))
    d.drawCircle(Color(argb: 0xFF3E2723), h * 0.025, CGPoint(w * 0.105, h * 0.52))
    d.drawArc(Color(argb: 0xFF3E2723), startAngle: 20, sweepAngle: 140, useCenter: false, CGPoint(w * 0.05, h * 0.55), CGSize(w * 0.1, h * 0.1), style: Stroke(width: h * 0.02, cap: .round))
    d.drawCircle(Color(argb: 0xFFFF8A65), h * 0.03, CGPoint(w * 0.93, h * 0.62))  // tail light
    d.drawLine(dark, CGPoint(w * 0.285, h * 0.48), CGPoint(w * 0.325, h * 0.48), w * 0.01, .round)  // handle
    // Wheels
    for cx in [0.22, 0.76] as [CGFloat] {
        d.drawCircle(Color(argb: 0xFF37474F), h * 0.16, CGPoint(w * cx, h * 0.8))
        d.drawCircle(Color(argb: 0xFFB0BEC5), h * 0.07, CGPoint(w * cx, h * 0.8))
    }
}

/** A chunky little bike: a lamp with a face at the front, a basket on the handlebars. */
func drawBike(_ d: DrawScope, _ color: Color) {
    let w = d.size.width, h = d.size.height
    let dark = lerp(color, black, 0.25)
    let hub = h * 0.68
    let r = h * 0.3
    let front = CGPoint(w * 0.24, hub)
    let rear = CGPoint(w * 0.76, hub)
    let crank = CGPoint(w * 0.52, h * 0.72)
    let neck = CGPoint(w * 0.31, h * 0.34)
    let seat = CGPoint(w * 0.6, h * 0.28)
    let tube = w * 0.03
    // Wheels: fat tyres, spokes and a hub
    for c in [front, rear] {
        d.drawCircle(Color(argb: 0xFFECEFF1), r, c)
        for k in 0..<4 {
            let a = CGFloat(k) * .pi / 4
            let o = CGPoint(cos(a), sin(a)) * r
            d.drawLine(Color(argb: 0xFFB0BEC5), c - o, c + o, w * 0.004)
        }
        d.drawCircle(Color(argb: 0xFF37474F), r, c, style: Stroke(width: h * 0.065))
        d.drawCircle(Color(argb: 0xFFB0BEC5), h * 0.05, c)
    }
    // Mudguards over the wheels
    for c in [front, rear] {
        d.drawArc(color, startAngle: 200, sweepAngle: 140, useCenter: false, c - CGPoint(r * 1.18, r * 1.18), CGSize(r * 2.36, r * 2.36), style: Stroke(width: h * 0.04, cap: .round))
    }
    // The frame
    d.drawLine(color, rear, crank, tube, .round)
    d.drawLine(color, rear, seat, tube, .round)
    d.drawLine(color, seat, crank, tube, .round)
    d.drawLine(color, seat + CGPoint(0, h * 0.06), neck, tube, .round)
    d.drawLine(color, neck + CGPoint(0, h * 0.05), crank, tube, .round)
    d.drawLine(dark, front, neck - CGPoint(0, h * 0.1), tube, .round)
    // The chain guard and a pedal
    d.drawRoundRect(dark, CGPoint(crank.x - w * 0.05, crank.y - h * 0.09), CGSize(w * 0.2, h * 0.17), h * 0.085)
    d.drawCircle(lerp(color, .white, 0.3), h * 0.075, crank)
    d.drawLine(Color(argb: 0xFF78909C), crank, crank + CGPoint(0, h * 0.13), w * 0.01, .round)
    d.drawRoundRect(Color(argb: 0xFF37474F), CGPoint(crank.x - w * 0.045, crank.y + h * 0.12), CGSize(w * 0.09, h * 0.035), h * 0.017)
    // The saddle
    d.drawRoundRect(Color(argb: 0xFF5D4037), CGPoint(seat.x - w * 0.075, seat.y - h * 0.05), CGSize(w * 0.15, h * 0.075), h * 0.04)
    // The handlebar, with a soft grip
    let bar = neck - CGPoint(0, h * 0.1)
    d.drawLine(Color(argb: 0xFF90A4AE), bar, bar + CGPoint(w * 0.09, -h * 0.07), w * 0.016, .round)
    d.drawLine(Color(argb: 0xFF37474F), bar + CGPoint(w * 0.06, -h * 0.047), bar + CGPoint(w * 0.1, -h * 0.078), w * 0.024, .round)
    // A woven basket
    d.drawRoundRect(Color(argb: 0xFFD7A86E), CGPoint(w * 0.1, h * 0.05), CGSize(w * 0.2, h * 0.24), h * 0.04)
    for k in 1...3 {
        d.drawLine(Color(argb: 0xFFB0834F), CGPoint(w * (0.1 + CGFloat(k) * 0.05), h * 0.07), CGPoint(w * (0.1 + CGFloat(k) * 0.05), h * 0.27), w * 0.004)
    }
    d.drawRoundRect(Color(argb: 0xFFA1887F), CGPoint(w * 0.09, h * 0.04), CGSize(w * 0.22, h * 0.04), h * 0.02)
    // A round lamp with a smiling face
    let lamp = CGPoint(w * 0.25, h * 0.38)
    let lr = h * 0.095
    d.drawCircle(Color(argb: 0xFF90A4AE), lr * 1.15, lamp)
    d.drawCircle(Color(argb: 0xFFFFF59D), lr, lamp)
    for dx in [-0.36, 0.36] as [CGFloat] { d.drawCircle(Color(argb: 0xFF3E2723), lr * 0.14, lamp + CGPoint(dx * lr, -lr * 0.15)) }
    d.drawArc(Color(argb: 0xFF3E2723), startAngle: 20, sweepAngle: 140, useCenter: false, lamp - CGPoint(lr * 0.45, lr * 0.4), CGSize(lr * 0.9, lr * 0.9), style: Stroke(width: h * 0.016, cap: .round))
}

/** A splotch of mud, `amount` (0..1) of it left. */
private func drawCarMud(_ d: DrawScope, _ at: CGPoint, _ amount: CGFloat, _ seed: Int) {
    let c = CGPoint(at.x * d.size.width, at.y * d.size.height)
    let r = d.size.width * spotR * 0.48
    let mudColor = Color(argb: 0xFF6D4C41).copy(alpha: Double(0.95 * amount))
    d.drawCircle(mudColor, r, c)
    var rnd = SeededRandom(seed)
    for _ in 0..<5 {
        let o = CGPoint((rnd.nextFloat() - 0.5) * r * 1.6, (rnd.nextFloat() - 0.5) * r * 1.1)
        d.drawCircle(mudColor, r * (0.3 + rnd.nextFloat() * 0.25), c + o)
    }
    d.drawCircle(Color(argb: 0xFF8D6E63).copy(alpha: Double(0.7 * amount)), r * 0.3, c + CGPoint(-r * 0.3, -r * 0.25))
}

/** A drop of water left after the rinse. */
private func drawWashDrop(_ d: DrawScope, _ at: CGPoint, _ amount: CGFloat) {
    let c = CGPoint(at.x * d.size.width, at.y * d.size.height)
    let r = d.size.width * 0.03
    let drop = Path { p in
        p.moveTo(c.x, c.y - r * 1.8)
        p.cubicTo(c.x + r * 1.2, c.y - r * 0.2, c.x + r, c.y + r, c.x, c.y + r)
        p.cubicTo(c.x - r, c.y + r, c.x - r * 1.2, c.y - r * 0.2, c.x, c.y - r * 1.8)
        p.close()
    }
    d.drawPath(drop, Color(argb: 0xFF4FC3F7).copy(alpha: Double(amount)))
    d.drawCircle(Color.white.copy(alpha: Double(0.7 * amount)), r * 0.3, c + CGPoint(-r * 0.3, -r * 0.1))
}

/** Water from the hose sweeping across the car, `p` (0..1) of the way. */
private func drawSpray(_ d: DrawScope, _ p: CGFloat) {
    let x = d.size.width * (0.05 + p * 0.9)
    for k in 0..<14 {
        let y = d.size.height * (0.05 + CGFloat(k) * 0.065)
        let wob = sin(p * 30 + CGFloat(k)) * d.size.width * 0.01
        d.drawLine(Color(argb: 0xAA4FC3F7), CGPoint(x - d.size.width * 0.06 + wob, y), CGPoint(x + wob, y + d.size.height * 0.04), d.size.width * 0.012, .round)
    }
}

/** Sparkles all over a clean car. */
private func drawShine(_ d: DrawScope, _ phase: CGFloat) {
    for (i, f) in ([(0.3, 0.3), (0.55, 0.15), (0.75, 0.35), (0.2, 0.6), (0.85, 0.62)] as [(CGFloat, CGFloat)]).enumerated() {
        let t = (phase + CGFloat(i) * 0.2).truncatingRemainder(dividingBy: 1)
        let s = d.size.width * 0.03 * sin(t * .pi)
        let c = CGPoint(d.size.width * f.0, d.size.height * f.1)
        d.drawLine(.white, c - CGPoint(s, 0), c + CGPoint(s, 0), d.size.width * 0.008, .round)
        d.drawLine(.white, c - CGPoint(0, s), c + CGPoint(0, s), d.size.width * 0.008, .round)
    }
}

/** The sponge (scrubbing) or towel (drying) under the finger. */
private func drawWashTool(_ d: DrawScope, _ at: CGPoint, sponge: Bool) {
    let c = CGPoint(at.x * d.size.width, at.y * d.size.height)
    let w = d.size.width * 0.12
    let h = d.size.width * 0.08
    if sponge {
        d.drawRoundRect(Color(argb: 0xFFFFEE58), c - CGPoint(w / 2, h / 2), CGSize(w, h), h * 0.3)
        d.drawRoundRect(Color(argb: 0xFF66BB6A), c - CGPoint(w / 2, h / 2), CGSize(w, h * 0.3), h * 0.2)
        for k in 0..<4 { d.drawCircle(Color(argb: 0xFFFBC02D), h * 0.08, c + CGPoint(w * (-0.3 + CGFloat(k) * 0.2), h * 0.15)) }
    } else {
        d.drawRoundRect(Color(argb: 0xFFF48FB1), c - CGPoint(w / 2, h / 2), CGSize(w, h * 1.2), h * 0.2)
        for k in 0..<3 { d.drawLine(Color.white.copy(alpha: 0.7), c + CGPoint(-w / 2, -h * 0.2 + CGFloat(k) * h * 0.3), c + CGPoint(w / 2, -h * 0.2 + CGFloat(k) * h * 0.3), h * 0.08) }
    }
}

/** A hose nozzle with a spurt of water, on the rinse button. */
private func drawHose(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0xFF43A047), CGPoint(w * 0.1, h * 0.45), CGSize(w * 0.45, h * 0.22), h * 0.1)
    d.drawRoundRect(Color(argb: 0xFFFFCA28), CGPoint(w * 0.5, h * 0.4), CGSize(w * 0.18, h * 0.32), h * 0.06)
    for k in 0...2 { d.drawLine(.white, CGPoint(w * 0.72, h * 0.56), CGPoint(w * 0.95, h * (0.35 + CGFloat(k) * 0.21)), w * 0.05, .round) }
}
