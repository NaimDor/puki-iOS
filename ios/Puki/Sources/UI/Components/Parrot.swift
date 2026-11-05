import SwiftUI

private let lid = Color(argb: 0xFF4CAF50)
private let lidLine = Color(argb: 0xFF2E7D32)
private let fullParrot: CGFloat = 140
private let compactParrot: CGFloat = 96

/** Puki's picture, flapping his beak while `talking`, and blinking now and then. */
struct TalkingParrotImage: View {
    let talking: Bool
    var flapMs = 140
    @State private var beakOpen = false
    @State private var blink = false

    var body: some View {
        ZStack {
            Pic("ic_companion_parrot").opacity(beakOpen ? 0 : 1)
            Pic("ic_companion_parrot_talk").opacity(beakOpen ? 1 : 0)
            if blink {
                DrawCanvas { d in
                    let u = d.size.width / 100  // (the pictures are drawn on a 100 x 100 viewport)
                    d.drawCircle(lid, 8.8 * u, CGPoint(40 * u, 28 * u))
                    d.drawLine(lidLine, CGPoint(32 * u, 29 * u), CGPoint(48 * u, 29 * u), 1.6 * u)
                }
            }
        }
        .task(id: talking) {
            beakOpen = false
            while talking {
                beakOpen.toggle()
                guard await delay(flapMs) else { break }
            }
            beakOpen = false
        }
        .task {
            while !Task.isCancelled {
                guard await delay(Int.random(in: 2500..<6000)) else { return }
                blink = true
                await delay(130)
                blink = false
            }
        }
    }
}

/**
 * פוקי sitting in the corner of a game. With his brain ready, he flaps his beak while he talks: a tap makes him
 * say something, and holding him records the child talking and he answers when let go. Without a brain he's
 * still there, with his recorded lines.
 */
struct CompanionParrot: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.pukiMood) private var mood
    var body: some View {
        Inner(companion: app.companion, voice: app.voice, mood: mood)
            .reaction(mood, jumpHeight: 24)
            .task { app.companion.wake() }
    }

    private struct Inner: View {
        @ObservedObject var companion: Companion
        @ObservedObject var voice: VoicePlayer
        let mood: ReactionState?
        var body: some View {
            // Until his brain is ready (or if it never is), the recorded Puki keeps the corner warm
            if companion.status != .ready {
                OfflineParrot()
            } else {
                ReadyParrot(companion: companion, voice: voice, mood: mood)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }
}

private struct ReadyParrot: View {
    @ObservedObject var companion: Companion
    @ObservedObject var voice: VoicePlayer
    let mood: ReactionState?
    @State private var listening = false
    @State private var holdTask: Task<Void, Never>?
    @State private var pressed = false
    @State private var pressStart: Int64 = 0
    @State private var recorder = MicRecorder()

    var body: some View {
        let speaking = companion.speaking || voice.speaking
        let size = isCompact() ? compactParrot : fullParrot
        let anyListening = listening || companion.listening
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let period = speaking ? 0.26 : 1.4
            let raw = (t / period).truncatingRemainder(dividingBy: 2)
            let bob = CGFloat(raw < 1 ? raw : 2 - raw)  // (back and forth)
            ZStack {
                if anyListening {
                    DrawCanvas { d in
                        let pulse = CGFloat((t / 0.9).truncatingRemainder(dividingBy: 1))
                        for k in 0...1 {
                            let tt = (pulse + CGFloat(k) * 0.5).truncatingRemainder(dividingBy: 1)
                            d.drawCircle(Color(argb: 0xFFFFEB3B).copy(alpha: Double(0.8 * (1 - tt))), d.size.minDimension * (0.35 + 0.25 * tt), d.center, style: Stroke(width: 6))
                        }
                    }
                }
                if companion.thinking && !speaking {
                    DrawCanvas { d in
                        let phase = (t / 0.9).truncatingRemainder(dividingBy: 1)
                        let r = d.size.width * 0.035
                        for k in 0...2 {
                            let bounce = CGFloat(abs(sin((phase + Double(k) / 3) * .pi)))
                            d.drawCircle(.white, r, CGPoint(d.size.width / 2 + CGFloat(k - 1) * r * 3, r * 2 - bounce * r * 1.5))
                        }
                    }
                }
                TalkingParrotImage(talking: speaking)
                    .frame(width: size, height: size)
                    .scaleEffect(anyListening ? 1.15 : 1, anchor: .bottom)
                    .animation(.spring(response: 0.4, dampingFraction: 0.4), value: anyListening)
                    .rotationEffect(.degrees(speaking ? Double((bob - 0.5) * 8) : 0), anchor: .bottom)
                    .offset(y: -bob * (speaking ? 10 : 4))
                    .gesture(DragGesture(minimumDistance: 0).onChanged { _ in press() }.onEnded { _ in release(true) })
            }
            .frame(width: size, height: size)
        }
        .onDisappear { recorder.stopInBackground() }
    }

    private func press() {
        #if DEBUG
        if !pressed { TouchHub.shared.caught = "parrot (ready) took a touch" }
        #endif
        if pressed { return }
        pressed = true
        pressStart = uptimeMs()
        // Holding for a moment starts listening; a quick tap just chats
        holdTask = Task { @MainActor in
            guard await delay(300) else { return }
            if !hasMicPermission() {
                // Refused already: he tilts his head rather than a dialog that won't come
                if micRefused { await mood?.oops() } else { requestMicPermission { _ in } }
            } else {
                // So the mic doesn't hear the app: silence now, and the game's next lines wait until she's done
                voice.stop()
                voice.hold()
                listening = await recorder.startOffMain()
            }
        }
    }

    private func release(_ released: Bool) {
        pressed = false
        let quick = uptimeMs() - pressStart < 300
        holdTask?.cancel()
        Task { @MainActor in
            await holdTask?.value  // (the microphone may have been opening)
            if listening {
                listening = false
                let wav = await recorder.stopOffMain()
                voice.unhold()
                if released { companion.listen(wav) }
            } else {
                voice.unhold()  // (never listened after all)
                if released && quick {
                    Task { await mood?.hop() }  // he noticed, right away
                    companion.chat()
                }
            }
        }
    }
}

/** What Puki says when tapped without his brain: a few recorded lines, never the same twice in a row. */
private let offlineLines = ["voice_puki_hi", "voice_puki_tickle", "voice_puki_fun", "voice_puki_here", "voice_puki_dance"]

/** Puki with recorded lines only: he bobs in the corner, and a tap makes him talk and dance. */
struct OfflineParrot: View {
    var size: CGFloat? = nil
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @State private var tapped = false
    @State private var last = -1

    var body: some View {
        Inner(voice: app.voice, tapped: $tapped, size: size ?? (isCompact() ? compactParrot : fullParrot)) {
            if tapped { return }
            let next = offlineLines.indices.filter { $0 != last }.randomElement() ?? 0
            last = next
            tapped = true
            Task { @MainActor in await delay(4000); tapped = false }  // in case the line never ends
            app.voice.play(offlineLines[next], language) { tapped = false }
        }
    }

    private struct Inner: View {
        @ObservedObject var voice: VoicePlayer
        @Binding var tapped: Bool
        let size: CGFloat
        let onTap: () -> Void
        var body: some View {
            let talking = tapped || voice.speaking
            TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
                let t = tl.date.timeIntervalSinceReferenceDate
                let period = talking ? 0.26 : 1.4
                let raw = (t / period).truncatingRemainder(dividingBy: 2)
                let bob = CGFloat(raw < 1 ? raw : 2 - raw)
                TalkingParrotImage(talking: talking)
                    .frame(width: size, height: size)
                    .rotationEffect(.degrees(talking ? Double((bob - 0.5) * 14) : 0), anchor: .bottom)  // a little dance while he talks
                    .offset(y: -bob * (talking ? 14 : 4))
                    .contentShape(Rectangle())
                    .onTapGesture {
                        #if DEBUG
                        TouchHub.shared.caught = "parrot (offline) took a touch"
                        #endif
                        onTap()
                    }
            }
        }
    }
}
