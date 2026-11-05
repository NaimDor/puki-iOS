import SwiftUI

struct ColorRoundState {
    var targetColor = level1Colors[0]
    var colorChoices = level1Colors
    var roundIndex = 0
    var totalRounds = 4
    var showSuccess = false
    var sessionComplete = false
    var totalStarsEarned = 0
    var isLoading = true
}

@MainActor
final class ColorSplashViewModel: ObservableObject {
    @Published private(set) var state = ColorRoundState()
    private(set) var session: GameSession!
    private var allColors = level1Colors
    private var rounds: [ColorDef] = []
    private var level = 1

    func start(_ app: AppModel) {
        guard session == nil else { return }
        session = GameSession("colors", app.progressRepository)
        level = session.level()
        allColors = colorsForLevel(level)  // later levels add colors to the first four
        rounds = Array(allColors.shuffled().prefix(4))
        startRound(0)
    }

    private func startRound(_ index: Int) {
        if index >= rounds.count {
            state.sessionComplete = true
            session.finish(rounds: rounds.count)
            return
        }
        let target = rounds[index]
        state = ColorRoundState(targetColor: target, colorChoices: pickChoices(target, allColors, level == 1 ? 3 : 4) { $0.id },
                                roundIndex: index, totalRounds: rounds.count, totalStarsEarned: state.totalStarsEarned, isLoading: false)
    }

    /** True if right, false if not, nil while the round is already won (a toddler's second tap). */
    func onColorTapped(_ id: String) -> Bool? {
        if state.showSuccess || state.sessionComplete { return nil }
        let correct = id == state.targetColor.id
        if correct {
            state.showSuccess = true
            state.totalStarsEarned += 1
            session.star()
            DayLog.record(RecapContent.color(state.targetColor))
        } else {
            session.slip()
        }
        return correct
    }

    func nextRound() { startRound(state.roundIndex + 1) }
}

struct ColorSplashScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gender) private var gender
    @Environment(\.layoutDirection) private var dir
    @StateObject private var vm = ColorSplashViewModel()
    @StateObject private var round = RoundFlow()
    @StateObject private var help = Help()
    @StateObject private var reactions = ReactionBank()
    @State private var sessionSeed = newSessionSeed()
    @State private var found: [Color] = []  // the session's colors, as splats on the grass
    @State private var rainbow: CGFloat = 0
    @State private var showCard = false
    @State private var picks: [String: Int] = [:]  // (for the UI tests: how many touches each balloon took)

    var body: some View {
        let state = vm.state
        let roundOver = round.won || state.sessionComplete
        GameBackground(scene: .playground) {
            if !state.isLoading {
                VStack(spacing: 0) {
                    GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.totalRounds, starsEarned: state.totalStarsEarned)
                    PromptBubble(text: BilingualText("תלחץ על \(state.targetColor.name.he)!", "Tap the \(state.targetColor.name.en) one!",
                                                     heGirl: "תלחצי על \(state.targetColor.name.he)!")(language, gender))
                    Spacer().frame(height: 8)
                    // Balloons floating about, high and low
                    GeometryReader { g in
                        let balloonHeight = min(250, g.size.height)
                        Scatter(seed: sessionSeed + state.roundIndex, rtl: dir == .rightToLeft) {
                            ForEach(Array(state.colorChoices.enumerated()), id: \.element.id) { i, color in
                                let right = color.id == state.targetColor.id
                                ColorBlob(color: color.color, height: balloonHeight, popped: roundOver && right)
                                    .rotationEffect(.degrees(scatterTilt(sessionSeed + state.roundIndex, i)))
                                    .hintGlow(help.showHint && right)
                                    // (the touch outside the glow's animation, like every answer that glows)
                                    .contentShape(Rectangle())
                                    .onTapGesture { pick(color) }
                                    .testID("choice")
                                    .testValue("\(right ? "right" : "")|\(color.id) picks:\(picks[color.id] ?? 0)\(right && help.showHint ? " hint" : "")")
                                    .reaction(reactions(color.id))
                                    .idleBob(i)
                                    .popAway(roundOver && !right, index: i)
                                    .popIn(i, key: state.roundIndex)
                            }
                        }
                        .frame(width: g.size.width, height: g.size.height)
                    }
                    // Clear of Puki, like every game: on a phone on its side the balloons stand as low as he does,
                    // and the first one, behind him, gave him its touches
                    .padding(.leading, pukiCorner)
                }
                .padding(16)
                // The colors she's found, splashed on the grass
                VStack {
                    Spacer()
                    HStack(spacing: 14) {
                        ForEach(Array(found.enumerated()), id: \.offset) { i, c in
                            DrawCanvas { d in drawSplat(d, c, i) }.frame(width: 52, height: 52).popIn(key: i)
                        }
                    }
                    .padding(.bottom, 10)
                }
                Tweened(rainbow) { t in DrawCanvas { d in if t > 0 { drawColorBow(d, found, t) } } }
                    .allowsHitTesting(false)
                ConfettiOverlay(show: roundOver)
                if showCard { RoundCompleteSheet(starsEarned: state.totalStarsEarned, onContinue: onPlayAgain, onHome: onBack) }
            }
        }
        .task { round.companion = app.companion; vm.start(app) }
        .onChange(of: state.roundIndex) { _, r in reactions.reset(for: r) }
        .helpLoop(help, key: state.roundIndex, active: !round.won && !state.sessionComplete && !state.isLoading, onAssisted: { vm.session?.slip() }) {
            app.voice.playSequence(["voice_prompt_tap_the", "voice_color_\(vm.state.targetColor.id)"], language)
        }
        .roundPrompt(state.isLoading ? -1 : state.roundIndex, "במשחק הצבעים: מחפשים את הצבע \(state.targetColor.name.he).",
                     "In the color game: we're looking for \(state.targetColor.name.en.lowercased()).",
                     keys: state.isLoading ? [] : ["voice_prompt_tap_the", "voice_color_\(state.targetColor.id)"])
        // "אדום!" picks the red balloon
        .answerOutLoud(state.roundIndex, options: state.colorChoices.map { [$0.name(language)] }, active: !roundOver && !state.isLoading) { i in
            if let c = vm.state.colorChoices.getOrNull(i) { pick(c) }
        }
        .task(id: state.sessionComplete) {
            // The last balloon is popped: her colors make a rainbow, and then the end card
            guard state.sessionComplete else { return }
            withAnimation(.easeInOut(duration: 1.5)) { rainbow = 1 }
            await delay(2800)
            showCard = true
        }
    }

    /** A balloon picked, by a tap or by saying its color. */
    private func pick(_ color: ColorDef) {
        let target = vm.state.targetColor
        let blob = reactions(color.id)
        picks[color.id, default: 0] += 1
        guard let correct = vm.onColorTapped(color.id) else { return }
        if correct {
            app.sfx.play(.pop)
            app.sfx.play(.success, volume: 0.7)
            found.append(color.color)
            app.companion.react("במשחק הצבעים בחרו \(color.name.he). זה נכון!", "In the color game the child picked \(color.name.en.lowercased()). That's right!") {
                app.voice.playPraise(language)
            }
            Task { await blob.celebrate() }
            round.win(quietMs: 2000, talkingMs: 3400) { vm.nextRound() }
        } else {
            app.sfx.play(.wrongGentle)
            app.companion.react("במשחק הצבעים בחרו \(color.name.he), אבל חיפשנו \(target.name.he).",
                                "In the color game the child picked \(color.name.en.lowercased()), but we were looking for \(target.name.en.lowercased()).") {
                app.voice.playSequence(["voice_color_\(color.id)", "voice_prompt_try_again"], language)
            }
            help.missed()
            Task { await blob.shake() }
        }
    }
}

/** A splash of paint in `color`, lumpy in its own way for each `seed`. */
private func drawSplat(_ d: DrawScope, _ color: Color, _ seed: Int) {
    let c = d.center
    let r = d.size.minDimension * 0.3
    var rnd = SeededRandom(seed * 31 + 7)
    d.drawCircle(lerp(color, .black, 0.2), r * 1.06, c)
    d.drawCircle(color, r, c)
    for k in 0..<7 {
        let a = CGFloat(k) / 7 * 2 * .pi + rnd.nextFloat()
        let dd = r * (0.95 + rnd.nextFloat() * 0.5)
        d.drawCircle(color, r * (0.18 + rnd.nextFloat() * 0.2), c + CGPoint(cos(a) * dd, sin(a) * dd))
    }
    d.drawCircle(Color.white.copy(alpha: 0.5), r * 0.2, c - CGPoint(r * 0.35, r * 0.35))
}

/** A rainbow of the session's `colors`, rising across the sky as `t` goes 0 → 1. */
private func drawColorBow(_ d: DrawScope, _ colors: [Color], _ t: CGFloat) {
    let c = CGPoint(d.size.width / 2, d.size.height * 0.92)
    let band = d.size.height * 0.075
    for (i, color) in colors.enumerated() {
        let r = d.size.height * 0.78 - CGFloat(i) * band
        d.drawArc(color, startAngle: 180, sweepAngle: Double(180 * t), useCenter: false, c - CGPoint(r, r), CGSize(r * 2, r * 2), style: Stroke(width: band * 0.96))
    }
}

/** A shiny balloon on a string, in `color`, `height` tall; when `popped` it bursts into drops. */
private struct ColorBlob: View {
    let color: Color
    let height: CGFloat
    let popped: Bool
    var body: some View {
        let light = lerp(color, .white, 0.4), dark = lerp(color, .black, 0.25), rim = lerp(color, .black, 0.35)
        Tweened(popped ? 1 : 0) { burst in
            DrawCanvas { d in
                let w = d.size.width, h = d.size.height
                let bodyW = w * 0.88, bodyH = h * 0.7
                let left = (w - bodyW) / 2
                if burst > 0 {
                    // Drops of paint flying out from where the balloon was
                    let c = CGPoint(w / 2, bodyH / 2)
                    for k in 0..<14 {
                        let a = CGFloat(k) / 14 * 2 * .pi
                        let reach = bodyW * (0.45 + CGFloat(k % 3) * 0.14) * burst
                        d.drawCircle(color.copy(alpha: Double(1 - burst * burst)), w * (0.05 + CGFloat(k % 2) * 0.03),
                                     c + CGPoint(cos(a) * reach, sin(a) * reach + burst * burst * h * 0.12))
                    }
                    if burst >= 1 { return }
                }
                let fade = Double(1 - burst)
                d.scale(1 + burst * 0.3, 1 + burst * 0.3, pivot: CGPoint(w / 2, bodyH / 2)) { s in
                    let string = Path { p in
                        p.moveTo(w / 2, bodyH + h * 0.04)
                        p.cubicTo(w * 0.36, bodyH + h * 0.11, w * 0.64, bodyH + h * 0.2, w * 0.47, h)
                    }
                    s.drawPath(string, Color(argb: 0xFF8D6E63).copy(alpha: fade), style: Stroke(width: 3))
                    let knot = Path { p in
                        p.moveTo(w / 2, bodyH - 4); p.lineTo(w / 2 - w * 0.065, bodyH + h * 0.045); p.lineTo(w / 2 + w * 0.065, bodyH + h * 0.045); p.close()
                    }
                    s.drawPath(knot, dark.copy(alpha: fade))
                    s.drawOval(Color.black.copy(alpha: 0.12 * fade), CGPoint(left + 7, 9), CGSize(bodyW, bodyH))
                    // A white halo and a darker rim, so a white or yellow balloon still stands out
                    s.drawOval(Color.white.copy(alpha: 0.6 * fade), CGPoint(left - 3, -3), CGSize(bodyW + 6, bodyH + 6))
                    s.drawOval(Brush.radialGradient([light, color, dark], center: CGPoint(w * 0.38, bodyH * 0.32), radius: bodyW * 0.95),
                               CGPoint(left, 0), CGSize(bodyW, bodyH), alpha: fade)
                    s.drawOval(rim.copy(alpha: fade), CGPoint(left, 0), CGSize(bodyW, bodyH), style: Stroke(width: 2.5))
                    s.drawOval(Color.white.copy(alpha: 0.6 * fade), CGPoint(left + bodyW * 0.17, bodyH * 0.12), CGSize(bodyW * 0.2, bodyH * 0.27))
                }
            }
        }
        .animation(popped ? .easeOut(duration: 0.42) : nil, value: popped)
        .frame(width: height * 0.68, height: height)
    }
}
