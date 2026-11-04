import SwiftUI

private enum EchoMode { case idle, listening, echoing }

/** A different parrot each time: squeaky, deep, squeakier. */
private let echoPitches: [Float] = [1.45, 0.8, 1.8]
private let echoesForAStar = 3

/**
 * Echo parrot: she says anything and Puki says it back in a silly parrot voice. Holding Puki records until she
 * lets go; a tap records until she stops talking. Works with no brain at all (the recording only goes to the
 * speaker, a moment later, and is never kept).
 */
struct EchoScreen: View {
    let onBack: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gamePaused) private var paused
    @StateObject private var puki = ReactionState()
    @State private var recorder = MicRecorder()
    @State private var session: GameSession?
    @State private var mode = EchoMode.idle
    @State private var lastActive = uptimeMs()
    @State private var echoes = 0
    @State private var stars = 0
    @State private var party = false
    @State private var loudness: CGFloat = 0
    @State private var pressStart: Int64 = 0
    @State private var pressed = false
    @State private var holdTask: Task<Bool, Never>?

    var body: some View {
        GameBackground(withCompanion: false, scene: .treehouse) {
            GeometryReader { g in
                // Sized from the screen's height, so the microphone stays at his beak
                let stage = min(g.size.height * 0.92, 560)
                let micHeight = g.size.height * 0.62
                ZStack {
                    VStack {
                        HStack(alignment: .top) {
                            BackButton(onBack: onBack).padding(16)
                            Spacer()
                            StarCounter(stars: stars).padding(16)
                        }
                        Spacer()
                    }
                    // A standing microphone for her, in front of his beak, leaning in as she gets louder
                    DrawCanvas { d in drawStandingMic(d) }
                        .frame(width: micHeight * 0.55, height: micHeight)
                        .scaleEffect(1 + 0.06 * loudness, anchor: .bottom)
                        .rotationEffect(.degrees(Double(-6 * loudness)), anchor: .bottom)
                        .frame(width: g.size.width, height: g.size.height, alignment: .bottom)
                        .offset(x: -stage / 2 - micHeight * 0.275, y: -12)
                        .environment(\.layoutDirection, .leftToRight)
                        .allowsHitTesting(false)
                    TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
                        let t = tl.date.timeIntervalSinceReferenceDate
                        let period = mode == .echoing ? 0.22 : 1.4
                        let raw = (t / period).truncatingRemainder(dividingBy: 2)
                        let bob = CGFloat(raw < 1 ? raw : 2 - raw)
                        let lean: CGFloat = mode == .listening ? 1 : 0
                        ZStack {
                            // Rings while Puki listens
                            if mode == .listening {
                                DrawCanvas { d in
                                    for k in 0...1 {
                                        let tt = (CGFloat((t / 0.9).truncatingRemainder(dividingBy: 1)) + CGFloat(k) * 0.5).truncatingRemainder(dividingBy: 1)
                                        d.drawCircle(Color(argb: 0xFFFFEB3B).copy(alpha: Double(0.8 * (1 - tt))), d.size.minDimension * (0.32 + (0.14 + 0.14 * loudness) * tt),
                                                     d.center, style: Stroke(width: 6 + 8 * loudness))
                                    }
                                }
                            }
                            TalkingParrotImage(talking: mode == .echoing, flapMs: 110)
                                .frame(width: stage * 0.76, height: stage * 0.76)
                                .scaleEffect(1 + 0.08 * lean, anchor: .bottom)
                                .rotationEffect(.degrees(Double(-8 * lean + (mode == .echoing ? (bob - 0.5) * 10 : 0))), anchor: .bottom)
                                .offset(y: -bob * (mode == .echoing ? 14 : 5))
                                .animation(.spring(response: 0.4, dampingFraction: 0.5), value: lean)
                                .reaction(puki)
                        }
                        .frame(width: stage, height: stage)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 0).onChanged { _ in press() }.onEnded { _ in release() })
                        .testID("hold")
                    }
                }
                .frame(width: g.size.width, height: g.size.height)
            }
            ConfettiOverlay(show: party)
        }
        .onDisappear { recorder.stopInBackground() }
        .task { session = GameSession("echo", app.progressRepository) }
        .task(id: mode == .listening) {
            // How loud she is, smoothed, for the rings and the microphone
            guard mode == .listening else { loudness = 0; return }
            while !Task.isCancelled {
                loudness += ((CGFloat(recorder.level) * 6).coerceIn(0, 1) - loudness) * 0.35
                guard await delay(50) else { return }
            }
        }
        .task {
            // "Say something to Puki!", and again (twice at most) if she goes quiet for a while
            guard await delay(600) else { return }
            app.voice.play("voice_prompt_echo", language)
            for _ in 0..<2 {
                while uptimeMs() - lastActive < 15_000 || mode != .idle { guard await delay(1000) else { return } }
                await awaitUnpaused { paused }
                app.voice.play("voice_prompt_echo", language)
                lastActive = uptimeMs()
            }
        }
    }

    private func press() {
        if pressed { return }
        pressed = true
        pressStart = uptimeMs()
        if mode != .idle { holdTask = nil; return }
        // Held for a moment: listen until she lets go; a quick tap: until she's quiet
        holdTask = Task { @MainActor in
            guard await delay(300) else { return false }
            return await listen(untilQuiet: false)
        }
    }

    private func release() {
        pressed = false
        if mode == .echoing { return }
        if mode == .listening && holdTask == nil {  // a tap while listening: done talking
            echo()
            return
        }
        let task = holdTask
        holdTask = nil
        task?.cancel()
        Task { @MainActor in
            let holding = await task?.value ?? false
            if holding && mode == .listening {
                echo()
            } else if !holding {
                _ = await listen(untilQuiet: true)
            }
        }
    }

    private func echo() {
        mode = .echoing
        Task { @MainActor in
            let wav = await recorder.stopOffMain()
            await EchoVoice.play(wav, volume: app.voice.level, pitch: echoPitches[echoes % echoPitches.count])
            mode = .idle
            lastActive = uptimeMs()
            echoes += 1
            if echoes % echoesForAStar == 0 {
                // Every few echoes: a star, and Puki dances
                stars += 1
                session?.star()
                app.sfx.play(.success)
                party = true
                await puki.celebrate()
                await delay(1800)
                party = false
            }
        }
    }

    /** Starts listening; with `untilQuiet` it stops by itself once she pauses (a tap), else on release. */
    private func listen(untilQuiet: Bool) async -> Bool {
        if !hasMicPermission() {
            // Refused already: he tilts his head rather than a dialog that won't come
            if micRefused { Task { await puki.oops() } } else { requestMicPermission { _ in } }
            return false
        }
        app.voice.stop()  // so the microphone doesn't hear the app
        guard await recorder.startOffMain() else { return false }
        mode = .listening
        lastActive = uptimeMs()
        if untilQuiet {
            Task { @MainActor in
                let t0 = uptimeMs()
                var heard = false
                var quietMs = 0
                while mode == .listening {
                    await delay(100)
                    if recorder.level > 0.08 { heard = true; quietMs = 0 } else { quietMs += 100 }
                    let elapsed = uptimeMs() - t0
                    if (heard && quietMs >= 900) || elapsed > 7000 || (!heard && elapsed > 4500) {
                        if heard { echo() } else {
                            // Quiet: not a dead end, Puki tilts his head and asks again
                            _ = await recorder.stopOffMain()
                            mode = .idle
                            lastActive = uptimeMs()
                            app.voice.play("voice_say_again", language)
                            Task { await puki.oops() }
                        }
                        break
                    }
                }
            }
        }
        return true
    }
}

/** A round microphone on a stand with a heavy foot. */
private func drawStandingMic(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let steel = Color(argb: 0xFF78909C)
    d.drawLine(steel, CGPoint(w / 2, h * 0.32), CGPoint(w / 2, h * 0.95), w * 0.07, .round)
    d.drawOval(Color(argb: 0xFF546E7A), CGPoint(w * 0.2, h * 0.91), CGSize(w * 0.6, h * 0.08))
    d.drawRoundRect(Color(argb: 0xFF455A64), CGPoint(w * 0.38, h * 0.24), CGSize(w * 0.24, h * 0.12), w * 0.05)
    let c = CGPoint(w / 2, h * 0.15)
    let r = w * 0.27
    d.drawCircle(Color(argb: 0xFFB0BEC5), r, c)
    for k in -2...2 {
        let fk = CGFloat(k)
        d.drawLine(steel, c + CGPoint(-r * 0.86, fk * r * 0.34), c + CGPoint(r * 0.86, fk * r * 0.34), w * 0.012)
        d.drawLine(steel, c + CGPoint(fk * r * 0.34, -r * 0.86), c + CGPoint(fk * r * 0.34, r * 0.86), w * 0.012)
    }
    d.drawCircle(Color.white.copy(alpha: 0.6), r * 0.2, c - CGPoint(r * 0.35, r * 0.35))
}
