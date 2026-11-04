import SwiftUI

/** What Puki calls out when the music stops; the animals do it too, and so does the child. */
private enum Move: CaseIterable {
    case freeze, jump, clap, spin
    var voice: String {
        switch self {
        case .freeze: return "voice_dance_freeze"
        case .jump: return "voice_dance_jump"
        case .clap: return "voice_dance_clap"
        case .spin: return "voice_dance_spin"
        }
    }
}

private let stopsInDance = 5  // music stops in one dance

/**
 * Dance and Freeze: the animals dance while the music plays; when it stops Puki calls out a move (freeze, jump,
 * clap, spin) and everyone does it, away from the screen. Then the dancing goes on.
 */
struct DanceScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gamePaused) private var paused
    @StateObject private var help = Help()
    @StateObject private var reactions = ReactionBank()
    @State private var session: GameSession?
    @State private var started = false
    @State private var dancing = false
    @State private var move: Move?
    @State private var stops = 0
    @State private var celebrate = false
    @State private var sessionComplete = false
    @State private var dance: CGFloat = 0  // eases out when the music stops
    @State private var dancers = Array(level1Animals.shuffled().prefix(3))

    var body: some View {
        GameBackground(scene: .stage) {
            TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
                let t = tl.date.timeIntervalSinceReferenceDate
                let beat = CGFloat((t / 0.52).truncatingRemainder(dividingBy: 1))
                let spin = (t / 1.1).truncatingRemainder(dividingBy: 1) * 360
                let ball = CGFloat((t / 5).truncatingRemainder(dividingBy: 1))
                Tweened(dance) { dance in
                    ZStack {
                        // The mirror ball over the stage; the lights it throws dance with the music
                        DrawCanvas { d in
                            d.drawLine(Color(argb: 0xFFB0BEC5), CGPoint(d.size.width * 0.74, 0), CGPoint(d.size.width * 0.74, d.size.height * 0.1), d.size.height * 0.006)
                            drawDiscoBall(d, CGPoint(d.size.width * 0.74, d.size.height * 0.17), d.size.height * 0.08, ball)
                            if dance > 0 { drawBallLights(d, ball, dance) }
                        }
                        .ignoresSafeArea()
                        VStack(spacing: 0) {
                            GameTopBar(onBack: onBack, roundIndex: min(stops, stopsInDance - 1), totalRounds: stopsInDance, starsEarned: stops)
                            GeometryReader { g in
                                let side = min(g.size.width / 3.6, g.size.height * 0.66)
                                let gap = side * 0.15
                                let slot = min(g.size.height * 0.32, 130)
                                ZStack(alignment: .top) {
                                    // Spots on the floor that flash to the beat, and the dancers' shadows
                                    DrawCanvas { d in drawDanceFloor(d, beat, dance, side, gap) }
                                    VStack {
                                        Spacer()
                                        HStack(alignment: .bottom, spacing: gap) {
                                            ForEach(Array(dancers.enumerated()), id: \.element.id) { i, animal in
                                                dancer(animal, i, side: side, beat: beat, spin: spin, dance: dance)
                                            }
                                        }
                                        .padding(.bottom, g.size.height * 0.05)
                                    }
                                    // Up above the dancers: the start, and then the move being called, big and simple
                                    ZStack {
                                        if let m = move {
                                            DrawCanvas { d in drawMoveSign(d, m) }
                                                .frame(width: slot, height: slot)
                                                .background(Circle().fill(Color.white).shadow(color: .black.opacity(0.25), radius: 8, y: 3))
                                                .idleBob(amount: 0.8)
                                                .popIn(key: "\(m)")
                                        }
                                        if !started {
                                            DrawCanvas { d in
                                                let w = d.size.width, h = d.size.height
                                                d.drawPath(Path { p in p.moveTo(w * 0.2, h * 0.08); p.lineTo(w, h * 0.5); p.lineTo(w * 0.2, h * 0.92); p.close() }, .white)
                                            }
                                            .frame(width: slot * 0.5, height: slot * 0.5)
                                            .frame(width: slot, height: slot)
                                            .background(Circle().fill(Color(argb: 0xFFFFCA28)).shadow(color: .black.opacity(0.3), radius: 10, y: 4))
                                            .overlay(Circle().stroke(Color.white, lineWidth: 6))
                                            .clickable { started = true; app.sfx.play(.pop) }
                                            .testID("play")
                                            .hintGlow(help.showHint)
                                            .idleBob(0, amount: 1.4)
                                            .popIn()
                                        }
                                    }
                                    .frame(width: slot, height: slot)
                                }
                                .frame(width: g.size.width, height: g.size.height)
                            }
                        }
                        .padding(16)
                    }
                }
            }
            ConfettiOverlay(show: celebrate)
            if sessionComplete { RoundCompleteSheet(starsEarned: stops, onContinue: onPlayAgain, onHome: onBack) }
        }
        .helpLoop(help, key: started, active: !started) { app.voice.play("voice_dance_intro", language) }
        .task {
            session = GameSession("dance", app.progressRepository)
            app.companion.setScene("ריקוד ועצירה: רוקדים עם החיות, וכשהמוזיקה נעצרת עושים מה שפוקי אומר.", "Dance and freeze: dancing with the animals, and when the music stops doing what Puki says.")
            // The music is the game: the lively track
            app.music.playTrack("music_dance")
            await delay(500)
            app.voice.play("voice_dance_intro", language)
        }
        .onDisappear {
            app.music.resume()
            app.music.playTrack("music_hub")
        }
        .task(id: started) { await run() }
    }

    private func dancer(_ animal: AnimalDef, _ i: Int, side: CGFloat, beat: CGFloat, spin: Double, dance: CGFloat) -> some View {
        let wave = sin(((beat + CGFloat(i) * 0.33).truncatingRemainder(dividingBy: 1)) * 2 * .pi)
        var rot = 0.0, dy: CGFloat = 0, sx: CGFloat = 1, sy: CGFloat = 1, roty = 0.0
        if dance > 0 {  // sway and bounce to the beat
            rot = Double(wave * 12 * dance)
            dy = -abs(wave) * side * 0.12 * dance
        } else if move == .jump {
            dy = -abs(wave) * side * 0.3
        } else if move == .spin {
            roty = spin
        } else if move == .clap {
            sx = 1 + 0.08 * wave; sy = 1 - 0.06 * wave
        } else if move == .freeze {
            rot = i % 2 == 0 ? -10 : 10  // a funny frozen pose
        }
        let r = reactions(animal.id)
        return Pic(animal.drawable)
            .frame(width: side, height: side)
            .scaleEffect(x: sx, y: sy, anchor: .bottom)
            .rotationEffect(.degrees(rot), anchor: .bottom)
            .rotation3DEffect(.degrees(roty), axis: (x: 0, y: 1, z: 0))
            .offset(y: dy)
            .reaction(r)
            .modifier(BobUnless(started: started, index: i))
            .contentShape(Rectangle())
            .testID("tap")
            .onTapGesture {
                Task { await r.celebrate() }
                app.voice.play("voice_sound_\(animal.id)", language)
            }
    }

    private func setDancing(_ on: Bool) {
        dancing = on
        withAnimation(.easeInOut(duration: 0.35)) { dance = on ? 1 : 0 }
    }

    /** One dance: play for a while, stop, call a move, hold it, and again. */
    private func run() async {
        guard started else { return }
        var last: Move?
        for i in 0..<stopsInDance {
            await awaitUnpaused { paused }  // (nothing goes on under the time limit's goodbye)
            setDancing(true)
            move = nil
            app.music.resume()
            guard await delay(Int.random(in: 5000..<9000)) else { return }
            await awaitUnpaused { paused }
            setDancing(false)
            app.music.pause()
            let next = Move.allCases.filter { $0 != last }.randomElement()!
            last = next
            move = next
            app.voice.play(next.voice, language)
            guard await delay(3800) else { return }
            await awaitUnpaused { paused }
            stops = i + 1
            session?.star()
            app.sfx.play(.starCollect, volume: 0.7)
            if i < stopsInDance - 1 {
                app.voice.play("voice_dance_again", language)
                guard await delay(900) else { return }
            }
        }
        await awaitUnpaused { paused }
        // The end: everyone takes a bow
        move = nil
        app.music.resume()
        celebrate = true
        app.sfx.play(.success)
        for (i, a) in dancers.enumerated() {
            let r = reactions(a.id)
            Task { await delay(i * 120); await r.celebrate() }
        }
        app.companion.react("ריקוד ועצירה: רקדו עד הסוף ועצרו בכל פעם!", "Dance and Freeze: the child danced to the end and stopped every time!") {
            app.voice.playPraise(language)
        }
        guard await delay(2600) else { return }
        celebrate = false
        session?.finish(rounds: stopsInDance)
        sessionComplete = true
    }
}

private struct BobUnless: ViewModifier {
    let started: Bool
    let index: Int
    func body(content: Content) -> some View {
        if started { content } else { content.idleBob(index, amount: 0.8) }
    }
}

/** A picture for each move, so even a child who doesn't follow the words sees what to do. */
private func drawMoveSign(_ d: DrawScope, _ m: Move) {
    let c = d.center
    let r = d.size.minDimension * 0.34
    switch m {
    case .freeze:  // a snowflake
        let ice = Color(argb: 0xFF4FC3F7)
        for i in 0..<6 {
            d.rotate(Double(i) * 60, pivot: c) { s in
                s.drawLine(ice, c, c - CGPoint(0, r), r * 0.14, .round)
                for k in [-1.0, 1.0] as [CGFloat] { s.drawLine(ice, c - CGPoint(0, r * 0.62), c - CGPoint(k * r * 0.26, r * 0.9), r * 0.1, .round) }
            }
        }
    case .jump:  // up, up
        for k in 0...1 {
            let y = c.y + r * (0.5 - CGFloat(k) * 0.75)
            let tip = Path { p in p.moveTo(c.x - r * 0.75, y + r * 0.4); p.lineTo(c.x, y - r * 0.35); p.lineTo(c.x + r * 0.75, y + r * 0.4) }
            d.drawPath(tip, Color(argb: 0xFF66BB6A), style: Stroke(width: r * 0.28, cap: .round, join: .round))
        }
    case .clap:  // two hands, with little lines of sound
        for s in [-1.0, 1.0] as [CGFloat] {
            d.rotate(Double(s * 16), pivot: c + CGPoint(0, r)) {
                $0.drawRoundRect(Color(argb: 0xFFFFCC80), c + CGPoint(s * r * 0.34 - r * 0.3, -r * 0.5), CGSize(r * 0.6, r * 1.3), r * 0.28)
            }
            d.drawLine(Color(argb: 0xFFFFB300), c + CGPoint(s * r * 0.9, -r * 0.65), c + CGPoint(s * r * 1.15, -r * 0.95), r * 0.13, .round)
        }
        d.drawLine(Color(argb: 0xFFFFB300), c - CGPoint(0, r * 0.8), c - CGPoint(0, r * 1.15), r * 0.13, .round)
    case .spin:  // a circle with an arrowhead
        let pink = Color(argb: 0xFFEC407A)
        d.drawArc(pink, startAngle: -60, sweepAngle: 300, useCenter: false, c - CGPoint(r * 0.85, r * 0.85), CGSize(r * 1.7, r * 1.7), style: Stroke(width: r * 0.26, cap: .round))
        let tip = c + CGPoint(r * 0.42, -r * 0.74)
        d.drawPath(Path { p in p.moveTo(tip.x + r * 0.5, tip.y - r * 0.05); p.lineTo(tip.x - r * 0.1, tip.y - r * 0.42); p.lineTo(tip.x - r * 0.05, tip.y + r * 0.34); p.close() }, pink)
    }
}

/** A mirror ball at `c`, its facets sliding round as `turn` goes 0 → 1. */
func drawDiscoBall(_ d: DrawScope, _ c: CGPoint, _ r: CGFloat, _ turn: CGFloat) {
    d.drawCircle(Brush.radialGradient([Color(argb: 0xFFECEFF1), Color(argb: 0xFF78909C)], center: c - CGPoint(r * 0.3, r * 0.3), radius: r * 1.5), r, c)
    let line = Color.white.copy(alpha: 0.55)
    for k in -2...2 {
        let y = c.y + CGFloat(k) * r * 0.36
        let half = max(0, r * r - (y - c.y) * (y - c.y)).squareRoot()
        d.drawLine(line, CGPoint(c.x - half, y), CGPoint(c.x + half, y), r * 0.04)
    }
    for k in 0..<6 {
        let x = c.x + r * cos((CGFloat(k) / 6 + turn) * .pi)
        let half = max(0, r * r - (x - c.x) * (x - c.x)).squareRoot()
        d.drawLine(line, CGPoint(x, c.y - half), CGPoint(x, c.y + half), r * 0.04)
    }
    d.drawCircle(Color.white.copy(alpha: 0.8), r * 0.12, c - CGPoint(r * 0.36, r * 0.4))
}

private let lights = [Color(argb: 0xFFFFEE58), Color(argb: 0xFFF48FB1), Color(argb: 0xFF81D4FA), Color(argb: 0xFFA5D6A7)]

/** Flecks of light thrown round the stage by the ball. */
private func drawBallLights(_ d: DrawScope, _ turn: CGFloat, _ amount: CGFloat) {
    for i in 0..<12 {
        let t = (turn + CGFloat(i) / 12).truncatingRemainder(dividingBy: 1)
        let x = d.size.width * (0.14 + 0.72 * t)
        let y = d.size.height * (0.3 + 0.42 * CGFloat((i * 37) % 10) / 10)
        d.drawCircle(lights[i % lights.count].copy(alpha: Double(0.5 * amount)), d.size.height * 0.012, CGPoint(x, y))
    }
}

/** The dancers' shadows, and colored spots on the floor flashing to `beat` while the music plays. */
private func drawDanceFloor(_ d: DrawScope, _ beat: CGFloat, _ dance: CGFloat, _ side: CGFloat, _ gap: CGFloat) {
    let y = d.size.height * 0.9
    let total = 3 * side + 2 * gap
    for k in 0..<7 {
        let x = d.size.width * (0.14 + CGFloat(k) * 0.12)
        let flash: CGFloat = (beat * 2 + CGFloat(k) * 0.5).truncatingRemainder(dividingBy: 1) < 0.5 ? 0.55 : 0.2
        let alpha = 0.16 + (flash - 0.16) * dance
        let r = side * 0.2
        d.drawOval(lights[k % lights.count].copy(alpha: Double(alpha)), CGPoint(x - r, y - r * 0.28), CGSize(r * 2, r * 0.56))
    }
    for i in 0...2 {
        let x = d.size.width / 2 - total / 2 + CGFloat(i) * (side + gap)
        d.drawOval(Color.black.copy(alpha: 0.18), CGPoint(x + side * 0.14, y - side * 0.045), CGSize(side * 0.72, side * 0.1))
    }
}

/** The disco ball, turning slowly: the music island's door to the dance. */
struct SpinningDiscoBall: View {
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
            let turn = CGFloat((tl.date.timeIntervalSinceReferenceDate / 5).truncatingRemainder(dividingBy: 1))
            DrawCanvas { d in drawDiscoBall(d, d.center, d.size.width * 0.46, turn) }
        }
    }
}
