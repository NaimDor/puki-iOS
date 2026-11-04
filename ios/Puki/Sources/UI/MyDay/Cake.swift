import SwiftUI

/** The cake's steps, one star each. */
private enum CakeStep: Int, CaseIterable { case add, stir, bake, decorate }

private let ingredients = ["flour", "sugar", "eggs", "milk"]
/** What each ingredient is called out loud. */
private func ingredientVoice(_ id: String) -> String {
    switch id { case "flour": return "voice_cook_flour"; case "sugar": return "voice_cook_sugar"; case "eggs": return "voice_cook_eggs"; default: return "voice_cook_milk" }
}
private let stirTurns: CGFloat = 3  // about three times round the bowl
private func toppingsToFinish(_ level: Int) -> Int { level == 1 ? 3 : level == 2 ? 4 : 6 }

/**
 * Baking a cake: put the ingredients in the bowl, stir round and round, into the oven, and decorate it. Nothing can
 * go wrong; every step ends with a star.
 */
struct CakeScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.layoutDirection) private var dir
    @StateObject private var dd = DragDropState()
    @StateObject private var game = StepGame(first: CakeStep.add, steps: CakeStep.allCases.count)
    @StateObject private var help = Help()
    @State private var level = 0
    @State private var finished = false
    @State private var added: [String] = []
    @State private var stirred: CGFloat = 0  // radians stirred so far
    @State private var spoonAngle: CGFloat?
    @State private var lastAngle: CGFloat?
    @State private var baking = false
    @State private var bake = Clip(3.2)
    @State private var toppings: [Topping] = []
    @State private var pukiShown = false

    var body: some View {
        let step = game.step
        // At the end Puki leaves his corner and comes to taste the cake
        GameBackground(withCompanion: !finished) {
            DrawCanvas { d in drawKitchen(d) }.ignoresSafeArea()
            if level > 0 {
                VStack(spacing: 0) {
                    GameTopBar(onBack: onBack, roundIndex: step.rawValue, totalRounds: CakeStep.allCases.count, starsEarned: game.stars)
                    GeometryReader { g in
                        let unit = min(g.size.width, g.size.height * 1.4)  // things scale with the room there is
                        Group {
                            switch step {
                            case .add: addStep(unit)
                            case .stir: stirStep(unit)
                            case .bake: bakeStep(unit)
                            case .decorate: decorateStep(unit)
                            }
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
            game.attach(app, island: "kitchen")
            level = game.session?.level() ?? 1
        }
        .helpLoop(help, key: step.rawValue, active: !game.celebrate && !baking && !finished && level > 0, onAssisted: { game.session?.slip() }) {
            app.voice.play(step == .add ? "voice_cook_add" : step == .stir ? "voice_cook_stir" : step == .bake ? "voice_cook_oven" : "voice_cook_decorate", language)
        }
        .modifier(CakePrompt(step: step))
    }

    private func addStep(_ unit: CGFloat) -> some View {
        VStack {
            Spacer()
            DropZone(zoneId: "bowl", highlightColor: Color(argb: 0xFFFFCA28)) {
                DrawCanvas { d in drawBowl(d, fill: CGFloat(added.count) / 4, smooth: 0, swirl: 0, spoonAngle: nil) }
                    .frame(width: unit * 0.42, height: unit * 0.33)
            }
            Spacer()
            HStack(spacing: 28) {
                ForEach(Array(ingredients.enumerated()), id: \.offset) { i, id in
                    ZStack {
                        if !added.contains(id) {
                            DragTarget(itemId: id, onDragEnd: { zone in
                                if zone == "bowl" && !added.contains(id) {
                                    added.append(id)
                                    app.sfx.play(.drop)
                                    help.touched()
                                    if added.count == ingredients.count { game.done(.stir) }
                                }
                            }, onDragStart: { app.voice.play(ingredientVoice(id), language) }, tapToZone: "bowl") {
                                DrawCanvas { d in
                                    switch id { case "flour": drawFlour(d); case "sugar": drawSugar(d); case "eggs": drawEggs(d); default: drawMilk(d) }
                                }
                                .frame(width: unit * 0.18, height: unit * 0.18)
                                .idleBob(i)
                                .hintGlow(help.showHint && id == ingredients.first { !added.contains($0) })
                            }
                        }
                    }
                    .frame(width: unit * 0.18, height: unit * 0.18)
                }
            }
            Spacer()
        }
    }

    private func stirStep(_ unit: CGFloat) -> some View {
        let smooth = (stirred / (stirTurns * 2 * .pi)).coerceIn(0, 1)
        let w = unit * 0.5, h = unit * 0.4
        return ZStack {
            if smooth < 1 {
                AmbientTimeline { t in DrawCanvas { d in drawStirGuide(d, Ambient.loop(t, 2.2)) } }
                    .frame(width: unit * 0.66, height: unit * 0.48)
                    .allowsHitTesting(false)
            }
            DrawCanvas { d in drawBowl(d, fill: 1, smooth: smooth, swirl: stirred, spoonAngle: spoonAngle ?? 0.6) }
                .frame(width: w, height: h)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 1)
                    .onChanged { v in
                        let c = CGPoint(w / 2, h * kitchenBowlRim)
                        let p = v.location - c
                        let a = atan2(p.y / 0.5, p.x)  // the bowl is seen from above, squashed
                        if let prev = lastAngle {
                            var d = a - prev
                            if d > .pi { d -= 2 * .pi }
                            if d < -.pi { d += 2 * .pi }
                            let before = stirred
                            stirred += abs(d)
                            let full = stirTurns * 2 * .pi
                            // A soft sound each half turn, and done after three turns
                            if Int(stirred / .pi) > Int(before / .pi) && stirred < full { app.sfx.play(.whoosh, volume: 0.35) }
                            if before < full && stirred >= full { game.done(.bake) }
                        }
                        lastAngle = a
                        spoonAngle = a
                    }
                    .onEnded { _ in spoonAngle = nil; lastAngle = nil })
                .testID("scrub")
        }
    }

    private func bakeStep(_ unit: CGFloat) -> some View {
        HStack {
            Spacer()
            ZStack {
                if !baking {
                    DragTarget(itemId: "pan", onDragEnd: { zone in
                        if zone == "oven" && !baking {
                            baking = true
                            app.sfx.play(.whoosh)
                            bake.run()
                            Task {
                                await delay(3200)
                                app.sfx.play(.starCollect)
                                app.voice.play("voice_cook_ready", language)
                                await delay(600)
                                game.done(.decorate)
                            }
                        }
                    }, tapToZone: "oven") {
                        DrawCanvas { d in drawPan(d) }.frame(width: unit * 0.28, height: unit * 0.28).idleBob(1).hintGlow(help.showHint)
                    }
                }
            }
            .frame(width: unit * 0.28, height: unit * 0.28)
            Spacer()
            DropZone(zoneId: "oven", highlightColor: Color(argb: 0xFFFF9800)) {
                TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !baking)) { tl in
                    let b = bake.value(tl.date)
                    DrawCanvas { d in drawOven(d, baking: b, open: !baking) }
                        .rotationEffect(.degrees(baking && b < 1 ? Double(Ambient.wave(tl.date.timeIntervalSinceReferenceDate, 0.24) * 0.8) : 0))
                }
                .frame(width: unit * 0.32, height: unit * 0.34)
            }
            Spacer()
        }
    }

    private func decorateStep(_ unit: CGFloat) -> some View {
        VStack {
            Spacer()
            ZStack(alignment: .trailing) {
                DropZone(zoneId: "cake", highlightColor: Color(argb: 0xFFF48FB1)) {
                    AmbientTimeline { t in
                        let flame = Ambient.loop(t, 0.7)
                        ZStack(alignment: .topLeading) {
                            DrawCanvas { d in drawCake(d) }
                            ForEach(Array(toppings.enumerated()), id: \.offset) { i, tp in
                                let spot = toppingSpot(i)
                                let cw = unit * 0.46, ch = unit * 0.34
                                let s = cw * 0.16
                                DrawCanvas { d in drawTopping(d, tp, flame: flame) }
                                    .frame(width: s, height: s)
                                    .popIn(key: i)
                                    .offset(x: cw * spot.x - s / 2, y: ch * spot.y - s * 0.85)
                            }
                        }
                        .environment(\.layoutDirection, .leftToRight)
                    }
                    .frame(width: unit * 0.46, height: unit * 0.34)
                    .popIn()
                }
                // Puki comes to taste it
                if finished {
                    Pic("ic_companion_parrot")
                        .frame(width: unit * 0.18, height: unit * 0.18)
                        .idleBob(3, amount: 2)
                        .scaleEffect(pukiShown ? 1 : 0.01)
                        .offset(x: (dir == .rightToLeft ? -1 : 1) * unit * 0.16)
                        .onAppear { withAnimation(.easeInOut(duration: 0.5)) { pukiShown = true } }
                }
            }
            Spacer()
            if !finished {
                HStack(spacing: 28) {
                    ForEach(Array(Topping.allCases.enumerated()), id: \.offset) { i, t in
                        DragTarget(itemId: "\(t)", onDragEnd: { zone in decorated(t, zone) }, tapToZone: "cake") {
                            AmbientTimeline { tm in DrawCanvas { d in drawTopping(d, t, flame: Ambient.loop(tm, 0.7)) } }
                                .frame(width: unit * 0.13, height: unit * 0.13)
                                .idleBob(i)
                        }
                    }
                }
            }
            Spacer()
        }
    }

    private func decorated(_ t: Topping, _ zone: String?) {
        guard zone == "cake", !finished else { return }
        toppings.append(t)
        app.sfx.play(.pop)
        help.touched()
        if toppings.count == toppingsToFinish(level) {
            finished = true
            app.voice.play("voice_cook_yum", language) {
                app.companion.react("במטבח: אפו עוגה וקישטו אותה, ופוקי טועם!", "In the kitchen: the child baked a cake and decorated it, and Puki tastes it!") {}
            }
            game.done(nil)
            Task {
                await delay(app.companion.isReady ? 7000 : 5500)  // time to admire it
                game.finish()
            }
        }
    }
}

private struct CakePrompt: ViewModifier {
    let step: CakeStep
    func body(content: Content) -> some View {
        switch step {
        case .add: content.roundPrompt(0, "במטבח: אופים עוגה, שמים בקערה קמח, סוכר, ביצים וחלב.", "In the kitchen: baking a cake, putting flour, sugar, eggs and milk in the bowl.", "voice_cook_lets_bake")
        case .stir: content.roundPrompt(1, "במטבח: מערבבים את הבצק לעוגה.", "In the kitchen: stirring the cake batter.", "voice_cook_stir")
        case .bake: content.roundPrompt(2, "במטבח: מכניסים את העוגה לתנור.", "In the kitchen: putting the cake in the oven.", "voice_cook_oven")
        case .decorate: content.roundPrompt(3, "במטבח: מקשטים את העוגה בתותים ובנרות.", "In the kitchen: decorating the cake with strawberries and candles.", "voice_cook_decorate")
        }
    }
}

// ---------- The kitchen's art ----------

private let batterColor = Color(argb: 0xFFFFE082)
private let lumps = Color(argb: 0xFFFFFDE7)
/** Where the bowl's rim sits, as a fraction of its box's height (the stirring circles round it). */
let kitchenBowlRim: CGFloat = 0.32

/** A tiled kitchen wall with a wooden counter along the bottom. */
func drawKitchen(_ d: DrawScope) {
    d.drawRect(Brush.verticalGradient([Color(argb: 0xFFFFF8E1), Color(argb: 0xFFFFECB3)]))
    let tile: CGFloat = 64
    var y: CGFloat = 0, row = 0
    while y < d.size.height * 0.78 {
        var x: CGFloat = row % 2 == 0 ? 0 : -tile / 2
        while x < d.size.width {
            d.drawRoundRect(Color(argb: 0x14FF8F00), CGPoint(x + 3, y + 3), CGSize(tile - 6, tile - 6), 8)
            x += tile
        }
        y += tile; row += 1
    }
    let counterTop = d.size.height * 0.78
    d.drawRect(Color(argb: 0xFFBCAAA4), CGPoint(0, counterTop), CGSize(d.size.width, d.size.height - counterTop))
    d.drawRect(Color(argb: 0xFFD7CCC8), CGPoint(0, counterTop), CGSize(d.size.width, 10))
}

/** The mixing bowl, seen a little from above. */
func drawBowl(_ d: DrawScope, fill: CGFloat, smooth: CGFloat, swirl: CGFloat, spoonAngle: CGFloat?) {
    let w = d.size.width, h = d.size.height
    let rimY = h * kitchenBowlRim
    let oh = h * 0.24
    let c = CGPoint(w / 2, rimY)
    d.drawOval(Color(argb: 0xFF1976D2), CGPoint(0, rimY - oh / 2), CGSize(w, oh))
    if fill > 0 {
        let bw = w * (0.7 + 0.22 * fill), bh = oh * (0.62 + 0.3 * fill)
        d.drawOval(lerp(Color(argb: 0xFFFFF3E0), batterColor, Double(smooth)), CGPoint(c.x - bw / 2, c.y - bh / 2 + oh * 0.06), CGSize(bw, bh))
        let bc = CGPoint(c.x, c.y + oh * 0.06)
        for i in 0..<9 {
            let a = swirl + CGFloat(i) * 0.7
            let r = 0.12 + CGFloat(i % 3) * 0.11
            let p = CGPoint(bc.x + cos(a) * bw * r, bc.y + sin(a) * bh * r)
            d.drawCircle(lumps.copy(alpha: Double((1 - smooth) * 0.95)), w * 0.028, p)
            if i % 3 == 0 { d.drawCircle(Color(argb: 0xFFFFB300).copy(alpha: Double((1 - smooth) * 0.9)), w * 0.022, p + CGPoint(w * 0.05, 0)) }
        }
        if smooth > 0.3 {
            let swirlPath = Path { path in
                for k in 0...40 {
                    let t = CGFloat(k) / 40
                    let a = swirl + t * 7
                    let p = CGPoint(bc.x + cos(a) * bw * t * 0.42, bc.y + sin(a) * bh * t * 0.42)
                    if k == 0 { path.move(to: p) } else { path.addLine(to: p) }
                }
            }
            d.drawPath(swirlPath, Color(argb: 0xFFFFA000).copy(alpha: Double(smooth * 0.8)), style: Stroke(width: w * 0.014, cap: .round))
        }
    }
    if let a = spoonAngle {
        let tip = CGPoint(c.x + cos(a) * w * 0.28, c.y + sin(a) * oh * 0.3)
        let handle = CGPoint(tip.x + w * 0.12, tip.y - h * 0.5)
        d.drawLine(Color(argb: 0xFFA1683A), tip, handle, w * 0.035, .round)
        d.drawOval(Color(argb: 0xFFBCAAA4), tip - CGPoint(w * 0.055, oh * 0.14), CGSize(w * 0.11, oh * 0.28))
    }
    let k = oh / 2 * 1.33
    let body = Path { p in
        p.moveTo(0, rimY); p.cubicTo(0, h * 1.02, w, h * 1.02, w, rimY); p.cubicTo(w, rimY + k, 0, rimY + k, 0, rimY); p.close()
    }
    d.drawPath(body, Brush.verticalGradient([Color(argb: 0xFF64B5F6), Color(argb: 0xFF1E88E5)], startY: rimY, endY: h))
    for (fx, fy) in [(0.25, 0.72), (0.5, 0.8), (0.75, 0.72)] as [(CGFloat, CGFloat)] { d.drawCircle(Color.white.copy(alpha: 0.8), w * 0.03, CGPoint(w * fx, h * fy)) }
    d.drawOval(Color(argb: 0xFF90CAF9), CGPoint(0, rimY - oh / 2), CGSize(w, oh), style: Stroke(width: w * 0.022))
}

func drawFlour(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0xFFF5F5F5), CGPoint(w * 0.2, h * 0.2), CGSize(w * 0.6, h * 0.72), w * 0.06)
    d.drawPath(Path { p in p.moveTo(w * 0.2, h * 0.22); p.lineTo(w * 0.3, h * 0.08); p.lineTo(w * 0.7, h * 0.08); p.lineTo(w * 0.8, h * 0.22); p.close() }, Color(argb: 0xFFE0E0E0))
    d.drawCircle(Color(argb: 0xFFFFCC80), w * 0.14, CGPoint(w * 0.5, h * 0.55))
    d.drawLine(Color(argb: 0xFFF9A825), CGPoint(w * 0.5, h * 0.66), CGPoint(w * 0.5, h * 0.45), w * 0.02, .round)
    for k in 0...2 {
        let y = h * (0.47 + CGFloat(k) * 0.05)
        d.drawOval(Color(argb: 0xFFF9A825), CGPoint(w * 0.42, y), CGSize(w * 0.07, h * 0.035))
        d.drawOval(Color(argb: 0xFFF9A825), CGPoint(w * 0.51, y), CGSize(w * 0.07, h * 0.035))
    }
}

func drawSugar(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0xFFE1F5FE).copy(alpha: 0.9), CGPoint(w * 0.2, h * 0.3), CGSize(w * 0.6, h * 0.62), w * 0.1)
    d.drawRoundRect(Color(argb: 0xFFF48FB1), CGPoint(w * 0.24, h * 0.18), CGSize(w * 0.52, h * 0.14), w * 0.05)
    for (fx, fy) in [(0.32, 0.6), (0.5, 0.66), (0.42, 0.48), (0.58, 0.5), (0.36, 0.76), (0.56, 0.78)] as [(CGFloat, CGFloat)] {
        d.drawRoundRect(.white, CGPoint(w * fx, h * fy), CGSize(w * 0.12, w * 0.12), w * 0.02)
    }
}

func drawEggs(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawOval(Color(argb: 0xFFFFF8E1), CGPoint(w * 0.14, h * 0.3), CGSize(w * 0.38, h * 0.5))
    d.drawOval(Color(argb: 0xFFFFE0B2), CGPoint(w * 0.46, h * 0.36), CGSize(w * 0.38, h * 0.5))
    d.drawOval(Color.white.copy(alpha: 0.7), CGPoint(w * 0.22, h * 0.38), CGSize(w * 0.08, h * 0.12))
    d.drawOval(Color.white.copy(alpha: 0.6), CGPoint(w * 0.54, h * 0.44), CGSize(w * 0.08, h * 0.12))
}

func drawMilk(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRect(.white, CGPoint(w * 0.25, h * 0.3), CGSize(w * 0.5, h * 0.62))
    d.drawPath(Path { p in p.moveTo(w * 0.25, h * 0.3); p.lineTo(w * 0.5, h * 0.12); p.lineTo(w * 0.75, h * 0.3); p.close() }, Color(argb: 0xFF42A5F5))
    d.drawRect(Color(argb: 0xFF42A5F5), CGPoint(w * 0.25, h * 0.62), CGSize(w * 0.5, h * 0.12))
    d.drawCircle(Color(argb: 0xFF90CAF9), w * 0.08, CGPoint(w * 0.5, h * 0.47))
    d.drawRect(Color(argb: 0xFF1E88E5), CGPoint(w * 0.45, h * 0.08), CGSize(w * 0.1, h * 0.06))
}

/** The oven: `baking` (0..1) glows warm through the window while the cake bakes. */
func drawOven(_ d: DrawScope, baking: CGFloat, open: Bool) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0xFFECEFF1), .zero, d.size, w * 0.06)
    for k in 0...2 { d.drawCircle(Color(argb: 0xFF90A4AE), w * 0.05, CGPoint(w * (0.3 + CGFloat(k) * 0.2), h * 0.1)) }
    d.drawRect(Color(argb: 0xFFCFD8DC), CGPoint(0, h * 0.18), CGSize(w, h * 0.02))
    if open {
        d.drawRoundRect(Color(argb: 0xFF37474F), CGPoint(w * 0.1, h * 0.26), CGSize(w * 0.8, h * 0.58), w * 0.04)
        d.drawRoundRect(Color(argb: 0xFFB0BEC5), CGPoint(w * 0.06, h * 0.84), CGSize(w * 0.88, h * 0.12), w * 0.03)
    } else {
        d.drawRoundRect(Color(argb: 0xFFB0BEC5), CGPoint(w * 0.08, h * 0.24), CGSize(w * 0.84, h * 0.68), w * 0.05)
        d.drawRoundRect(lerp(Color(argb: 0xFF455A64), Color(argb: 0xFFFF9800), Double(baking)), CGPoint(w * 0.18, h * 0.36), CGSize(w * 0.64, h * 0.4), w * 0.04)
        d.drawRoundRect(Color.white.copy(alpha: 0.25), CGPoint(w * 0.22, h * 0.39), CGSize(w * 0.14, h * 0.06), w * 0.02)
        d.drawLine(Color(argb: 0xFF78909C), CGPoint(w * 0.25, h * 0.29), CGPoint(w * 0.75, h * 0.29), w * 0.04, .round)
    }
}

/** A cake tin full of batter (before baking). */
func drawPan(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0xFF9E9E9E), CGPoint(w * 0.08, h * 0.4), CGSize(w * 0.84, h * 0.4), w * 0.05)
    d.drawOval(Color(argb: 0xFFBDBDBD), CGPoint(w * 0.08, h * 0.3), CGSize(w * 0.84, h * 0.22))
    d.drawOval(batterColor, CGPoint(w * 0.12, h * 0.33), CGSize(w * 0.76, h * 0.16))
}

/** The baked cake: sponge, pink icing and drips. Toppings are drawn on it separately. */
func drawCake(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawOval(Color(argb: 0xFFE0E0E0), CGPoint(w * 0.02, h * 0.84), CGSize(w * 0.96, h * 0.14))
    d.drawRoundRect(Color(argb: 0xFFD7A15E), CGPoint(w * 0.1, h * 0.42), CGSize(w * 0.8, h * 0.46), w * 0.04)
    d.drawRect(Color(argb: 0xFFFFF59D), CGPoint(w * 0.1, h * 0.62), CGSize(w * 0.8, h * 0.05))
    d.drawOval(Color(argb: 0xFFF48FB1), CGPoint(w * 0.08, h * 0.32), CGSize(w * 0.84, h * 0.22))
    for (fx, len) in [(0.16, 0.12), (0.3, 0.07), (0.46, 0.14), (0.62, 0.08), (0.78, 0.12)] as [(CGFloat, CGFloat)] {
        d.drawLine(Color(argb: 0xFFF48FB1), CGPoint(w * fx, h * 0.46), CGPoint(w * fx, h * (0.46 + len)), w * 0.05, .round)
    }
    d.drawOval(Color.white.copy(alpha: 0.35), CGPoint(w * 0.2, h * 0.35), CGSize(w * 0.2, h * 0.05))
}

/** Toppings, each drawn in a box of its own. */
enum Topping: CaseIterable { case strawberry, candle, cherry, heart }

func drawTopping(_ d: DrawScope, _ topping: Topping, flame: CGFloat = 0) {
    let w = d.size.width, h = d.size.height
    switch topping {
    case .strawberry:
        d.drawPath(Path { p in
            p.moveTo(w * 0.5, h * 0.92); p.cubicTo(w * 0.1, h * 0.6, w * 0.12, h * 0.25, w * 0.5, h * 0.28)
            p.cubicTo(w * 0.88, h * 0.25, w * 0.9, h * 0.6, w * 0.5, h * 0.92); p.close()
        }, Color(argb: 0xFFE53935))
        for k in 0..<6 { d.drawCircle(Color(argb: 0xFFFFEB3B), w * 0.025, CGPoint(w * (0.35 + CGFloat(k % 3) * 0.15), h * (0.45 + CGFloat(k / 3) * 0.18))) }
        d.drawOval(Color(argb: 0xFF43A047), CGPoint(w * 0.3, h * 0.18), CGSize(w * 0.4, h * 0.16))
    case .candle:
        d.drawRoundRect(Color(argb: 0xFF64B5F6), CGPoint(w * 0.4, h * 0.35), CGSize(w * 0.2, h * 0.6), w * 0.04)
        for k in 0...2 { d.drawLine(.white, CGPoint(w * 0.4, h * (0.45 + CGFloat(k) * 0.17)), CGPoint(w * 0.6, h * (0.4 + CGFloat(k) * 0.17)), w * 0.04) }
        let f = 1 + 0.12 * sin(flame * 6.28)
        d.drawOval(Color(argb: 0xFFFFB300), CGPoint(w * (0.5 - 0.09 * f), h * 0.06), CGSize(w * 0.18 * f, h * 0.26))
        d.drawOval(Color(argb: 0xFFFFF59D), CGPoint(w * 0.46, h * 0.15), CGSize(w * 0.08, h * 0.13))
    case .cherry:
        d.drawLine(Color(argb: 0xFF6D4C41), CGPoint(w * 0.5, h * 0.45), CGPoint(w * 0.62, h * 0.1), w * 0.04, .round)
        d.drawCircle(Color(argb: 0xFFC62828), w * 0.3, CGPoint(w * 0.5, h * 0.62))
        d.drawCircle(Color.white.copy(alpha: 0.5), w * 0.07, CGPoint(w * 0.4, h * 0.52))
    case .heart:
        d.drawPath(Path { p in
            p.moveTo(w * 0.5, h * 0.88); p.cubicTo(w * 0.05, h * 0.55, w * 0.15, h * 0.12, w * 0.5, h * 0.32)
            p.cubicTo(w * 0.85, h * 0.12, w * 0.95, h * 0.55, w * 0.5, h * 0.88); p.close()
        }, Color(argb: 0xFFAB47BC))
        d.drawCircle(Color.white.copy(alpha: 0.45), w * 0.06, CGPoint(w * 0.34, h * 0.38))
    }
}

/** A circling arrow around the bowl, showing which way to stir. */
func drawStirGuide(_ d: DrawScope, _ phase: CGFloat) {
    let c = d.center
    let rx = d.size.width * 0.5, ry = d.size.height * 0.5
    for k in 0..<10 {
        let a = phase * 6.28 + CGFloat(k) * 0.45
        d.drawCircle(Color(argb: 0xFFFF8F00).copy(alpha: Double(0.15 + CGFloat(k) * 0.07)), d.size.minDimension * (0.012 + CGFloat(k) * 0.0015), CGPoint(c.x + cos(a) * rx, c.y + sin(a) * ry))
    }
    let a = phase * 6.28 + 9 * 0.45
    let p = CGPoint(c.x + cos(a) * rx, c.y + sin(a) * ry)
    d.rotate(Double(a) * 180 / .pi + 90, pivot: p) { r in
        let s = d.size.minDimension * 0.05
        r.drawPath(Path { q in q.moveTo(p.x + s, p.y); q.lineTo(p.x - s * 0.6, p.y - s * 0.7); q.lineTo(p.x - s * 0.6, p.y + s * 0.7); q.close() }, Color(argb: 0xFFFF8F00))
    }
}

/** Where the `index`th topping sits on top of the cake (fractions of the cake's box). */
func toppingSpot(_ index: Int) -> CGPoint {
    [CGPoint(0.5, 0.37), CGPoint(0.26, 0.42), CGPoint(0.74, 0.42), CGPoint(0.38, 0.38), CGPoint(0.62, 0.38), CGPoint(0.36, 0.48), CGPoint(0.64, 0.48), CGPoint(0.5, 0.47)][index % 8]
}
