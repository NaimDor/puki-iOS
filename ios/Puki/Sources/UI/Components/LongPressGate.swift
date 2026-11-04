import SwiftUI

/** Until when (uptime) the gate stays shut after a wrong answer: for every gate, whichever screen. */
@MainActor private var lockedUntil: Int64 = 0
private let lockMs: Int64 = 60_000

/**
 * For grown-ups only: after a `holdDurationMs` hold (a ring fills up), a grown-up taps the number written out in
 * words among six, twice in a row (ParentCheck); only then `onUnlocked` runs. A wrong tap shuts every gate for a
 * minute.
 */
struct LongPressGate<Content: View>: View {
    var holdDurationMs = 2000
    let onUnlocked: () -> Void
    @ViewBuilder let content: () -> Content
    @State private var checking = false
    @State private var progress: CGFloat = 0
    @State private var holdTask: Task<Void, Never>?
    @State private var pressed = false

    var body: some View {
        content()
            .background(
                DrawCanvas { d in
                    if progress > 0 {
                        d.drawArc(Color(argb: 0xFF4CAF50), startAngle: -90, sweepAngle: 360 * Double(progress), useCenter: false,
                                  .zero, d.size, style: Stroke(width: 4))
                    }
                }
                .animation(.linear(duration: 0.1), value: progress)
            )
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { _ in press() }
                .onEnded { _ in release() })
            .sheet(isPresented: $checking) {
                ParentCheck { passed in
                    checking = false
                    if passed { onUnlocked() } else { lockedUntil = uptimeMs() + lockMs }
                }
                .presentationDetents([.medium])
            }
    }

    private func press() {
        if pressed { return }
        pressed = true
        if uptimeMs() < lockedUntil { return }  // (shut for now: the ring doesn't fill)
        holdTask = Task { @MainActor in
            let steps = 15
            for i in 1...steps {
                guard await delay(holdDurationMs / steps) else { return }
                progress = CGFloat(i) / CGFloat(steps)
            }
            checking = true
            progress = 0
        }
    }

    private func release() {
        pressed = false
        holdTask?.cancel()
        progress = 0
    }
}

/** A number in words, for ParentCheck: 11 to 19, Hebrew (feminine, as numbers are counted) or English. */
func numberInWords(_ n: Int, _ language: Language) -> String {
    switch language {
    case .hebrew: return ["אחת-עשרה", "שתים-עשרה", "שלוש-עשרה", "ארבע-עשרה", "חמש-עשרה", "שש-עשרה", "שבע-עשרה", "שמונה-עשרה", "תשע-עשרה"][n - 11]
    case .english: return ["eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"][n - 11]
    }
}

/** "For grown-ups: tap seventeen", among six numbers, then a second number (a guess gets through once in 36). */
private struct ParentCheck: View {
    let onDone: (Bool) -> Void
    @Environment(\.language) private var language
    @State private var round = 0
    @State private var target = Int.random(in: 11...19)
    @State private var choices: [Int] = []

    var body: some View {
        VStack(spacing: 16) {
            Text(BilingualText("להורים: לחצו על המספר", "For grown-ups: tap the number")(language))
                .font(PukiFont.titleMedium).foregroundColor(Color(argb: 0xFF5D4037))
            Text(numberInWords(target, language)).font(.system(size: 30, weight: .bold, design: .rounded)).foregroundColor(Color(argb: 0xFF5D4037))
                .testID("parentTarget").accessibilityValue(numberInWords(target, language))
            ForEach(Array(stride(from: 0, to: choices.count, by: 3)), id: \.self) { start in
                HStack(spacing: 12) {
                    ForEach(choices[start..<min(start + 3, choices.count)], id: \.self) { n in
                        Text("\(n)")
                            .font(.system(size: 28, design: .rounded))
                            .foregroundColor(Color(argb: 0xFF5D4037))
                            .frame(width: 72, height: 72)
                            .background(RoundedRectangle(cornerRadius: 16).fill(Color(argb: 0xFFFFF3E0)))
                            .clickable { picked(n) }
                            .testID("parentNumber.\(n)")
                    }
                }
            }
        }
        .padding(24)
        .onAppear { newRound() }
        .interactiveDismissDisabled(false)
    }

    private func newRound() {
        target = Int.random(in: 11...19)
        choices = (Array((11...19).filter { $0 != target }.shuffled().prefix(5)) + [target]).shuffled()
    }

    private func picked(_ n: Int) {
        if n != target { onDone(false) } else if round == 0 { round = 1; newRound() } else { onDone(true) }
    }
}

private let eveningStars: [(CGFloat, CGFloat)] = [(0.08, 0.12), (0.2, 0.28), (0.33, 0.09), (0.47, 0.22), (0.6, 0.07),
                                                  (0.68, 0.32), (0.93, 0.4), (0.12, 0.46), (0.88, 0.08), (0.4, 0.38)]

/**
 * The calm end of playtime, when the parent's time limit is up. No countdown: the evening comes in over the game,
 * Puki yawns and says it's time for a break, waves and says see you soon, and the music fades away. The game
 * can't be played underneath; a parent closes this with a long press.
 */
struct TimeLimitOverlay: View {
    let onDismiss: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @State private var evening: CGFloat = 0
    @State private var yawn: CGFloat = 0
    @State private var wave: CGFloat = 0
    @State private var mouthOpen = false

    var body: some View {
        GeometryReader { g in
            let compact = g.size.height < 480
            let parrot = min(200, g.size.height * 0.4)
            ZStack {
                // The evening sky comes in over the game, with a moon and stars
                TimelineView(.animation(minimumInterval: 1.0 / 20)) { tl in
                    let tw = tl.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 4.8) / 2.4
                    let twinkle = CGFloat(tw < 1 ? tw : 2 - tw)
                    DrawCanvas { d in
                        let e = evening
                        d.drawRect(Color(argb: 0xFF1A237E).copy(alpha: Double(0.82 * e)))
                        let moon = CGPoint(d.size.width * 0.82, d.size.height * 0.2)
                        let r = d.size.height * 0.07
                        var ctx = d.ctx
                        ctx.opacity = Double(e)
                        ctx.drawLayer { l in
                            l.fill(Path(ellipseIn: CGRect(x: moon.x - r, y: moon.y - r, width: 2 * r, height: 2 * r)), with: .color(Color(argb: 0xFFFFF59D)))
                            l.blendMode = .clear
                            let b = moon + CGPoint(r * 0.5, -r * 0.28), br = r * 0.86
                            l.fill(Path(ellipseIn: CGRect(x: b.x - br, y: b.y - br, width: 2 * br, height: 2 * br)), with: .color(.black))
                        }
                        for (i, s) in eveningStars.enumerated() {
                            let glow = 0.5 + 0.5 * sin((twinkle + CGFloat(i) * 0.37) * .pi * 2)
                            d.drawCircle(Color.white.copy(alpha: Double(e * (0.4 + 0.6 * glow))), d.size.height * 0.006, CGPoint(d.size.width * s.0, d.size.height * s.1))
                        }
                    }
                }
                .ignoresSafeArea()
                VStack(spacing: 0) {
                    HStack(spacing: 8) {
                        Pic(mouthOpen ? "ic_companion_parrot_talk" : "ic_companion_parrot")
                            .frame(width: parrot, height: parrot)
                            .scaleEffect(x: 1 - 0.05 * yawn, y: 1 + 0.14 * yawn, anchor: .bottom)
                            .rotationEffect(.degrees(Double(wave * 6)), anchor: .bottom)
                        Pic("ic_ui_wave")
                            .frame(width: parrot * 0.45, height: parrot * 0.45)
                            .rotationEffect(.degrees(Double(wave * 28)), anchor: .bottom)
                            .opacity(Double(evening))
                    }
                    Spacer().frame(height: compact ? 8 : 20)
                    Text(BilingualText("הגיע הזמן להפסקה!", "Time for a break!")(language))
                        .font(PukiFont.headlineLarge).foregroundColor(PukiColors.cloudWhite.opacity(Double(evening))).textHalo()
                    if !compact {
                        Spacer().frame(height: 8)
                        Text(BilingualText("נתראה בקרוב!", "See you soon!")(language))
                            .font(PukiFont.headlineMedium).foregroundColor(PukiColors.cloudWhite.opacity(Double(evening))).textHalo()
                    }
                }
                .padding(.horizontal, 32).padding(.top, 16).padding(.bottom, 72)
                // For the grown-up: a long press closes it (small and out of the way)
                VStack {
                    Spacer()
                    LongPressGate(onUnlocked: onDismiss) {
                        Text(BilingualText("לחיצה ארוכה לסגירה", "Long press to close")(language))
                            .font(PukiFont.bodyMedium)
                            .foregroundColor(PukiColors.cloudWhite.opacity(0.8))
                            .padding(.horizontal, 20).padding(.vertical, 10)
                            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.18)))
                    }
                    .testID("timeLimitClose")
                    .padding(.bottom, 20)
                }
            }
            .frame(width: g.size.width, height: g.size.height)
        }
        .contentShape(Rectangle())
        .onTapGesture {}  // the game underneath can't be played
        .overlay(alignment: .topTrailing) { Color.clear.frame(width: 1, height: 1).testID("timeLimit").allowsHitTesting(false) }
        .environment(\.ambientHeld, true)
        .task { await run() }
        .onDisappear {
            app.music.held = false
            app.music.duck(false)
            app.music.resume()
        }
    }

    private func run() async {
        app.voice.stop()
        app.music.duck(true)
        withAnimation(.easeOut(duration: 5)) { evening = 1 }
        await delay(700)
        // A big, slow yawn and stretch
        mouthOpen = true
        withAnimation(.easeInOut(duration: 1.1)) { yawn = 1 }
        await delay(1500)
        withAnimation(.easeInOut(duration: 0.8)) { yawn = 0 }
        await delay(800)
        mouthOpen = false
        app.voice.play("voice_prompt_time_break", language)
        await delay(2300)
        // Wave goodbye
        for _ in 0..<3 {
            withAnimation(.easeInOut(duration: 0.32)) { wave = 1 }
            await delay(320)
            withAnimation(.easeInOut(duration: 0.32)) { wave = -1 }
            await delay(320)
        }
        withAnimation(.easeInOut(duration: 0.26)) { wave = 0 }
        await delay(260)
        app.voice.play("voice_prompt_see_you_soon", language)
        guard await delay(2500) else { return }
        app.music.pause()
        app.music.held = true  // (from here no game loop or return to the app brings it back)
    }
}
