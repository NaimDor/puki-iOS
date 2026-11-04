import SwiftUI

private let cardHeight: CGFloat = 440

/** End-of-session card: stars landing, sun rays, confetti, continue/home (and the album, when a sticker came). */
struct RoundCompleteSheet: View {
    let starsEarned: Int
    let onContinue: () -> Void
    let onHome: () -> Void
    var calm = false  // bedtime: no party, a darker room

    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.openAlbum) private var openAlbum
    @State private var showContent = false
    @State private var showConfetti = false
    @State private var earned: StickerDef?
    @State private var stickerUp = false
    @State private var buttonsLive = false
    @State private var flight: CGFloat = 0
    @StateObject private var door = ReactionState()
    @State private var doorCentre: CGPoint = .zero
    @State private var screenCentre: CGPoint = .zero

    var body: some View {
        GeometryReader { g in
            let fit = min(g.size.height * 0.96 / cardHeight, 1)
            ZStack {
                Color.black.opacity(calm ? 0.6 : 0.45).ignoresSafeArea()
                if showContent && !calm { SunRays(side: max(g.size.width, g.size.height) * 1.1).popIn() }
                if showContent { card.scaleEffect(fit).popIn() }
                if stickerUp, let sticker = earned {
                    let big = min(g.size.height * 0.62, 320)
                    BigSticker(sticker: sticker)
                        .frame(width: big, height: big)
                        .scaleEffect(1 - flight * 0.82)
                        .offset(x: (doorCentre.x - screenCentre.x) * flight, y: (doorCentre.y - screenCentre.y) * flight)
                }
                ConfettiOverlay(show: showConfetti) { showConfetti = false }
            }
            .frame(width: g.size.width, height: g.size.height)
            .onAppear { let f = g.frame(in: .global); screenCentre = CGPoint(f.midX, f.midY) }
        }
        .environment(\.ambientHeld, true)  // the scene waits under the card
        .contentShape(Rectangle())
        .task { await run() }
    }

    private var card: some View {
        VStack(spacing: 0) {
            Text(BilingualText("כל הכבוד!", "Great job!")(language))
                .font(PukiFont.headlineLarge)
                .foregroundColor(Color(argb: 0xFF5D4037))
                .textHalo()
            Spacer().frame(height: 20)
            // The stars land one by one, each with a twinkle
            HStack(spacing: 12) {
                ForEach(0..<max(1, starsEarned), id: \.self) { i in LandingStar(index: i, size: starsEarned > 3 ? 72 : 104) }
            }
            Spacer().frame(height: 32)
            HStack(spacing: 40) {
                RoundButton(color: Color(argb: 0xFF66BB6A), label: BilingualText("המשך", "Continue"), index: 0, pulse: buttonsLive,
                            onClick: { if buttonsLive { onContinue() } }) { NextArrow() }
                    .testID("continue")
                RoundButton(color: Color(argb: 0xFFFFCA28), label: BilingualText("בית", "Home"), index: 1, pulse: false,
                            onClick: { if buttonsLive { onHome() } }) { Pic("ic_word_house").frame(width: 72, height: 72) }
                    .testID("home")
                // Where the new sticker goes, and the way to go and see it
                if earned != nil, let openAlbum {
                    RoundButton(color: Color(argb: 0xFFB39DDB), label: BilingualText("אלבום", "Album"), index: 2, pulse: false,
                                onClick: { if buttonsLive { openAlbum() } }) { Pic("ic_ui_stickers").frame(width: 76, height: 76) }
                        .reaction(door, jumpHeight: 18)
                        .background(GeometryReader { gg in
                            Color.clear.onAppear { let f = gg.frame(in: .global); doorCentre = CGPoint(f.midX, f.midY) }
                        })
                }
            }
        }
        .padding(.horizontal, 56)
        .padding(.vertical, 36)
        .background(RoundedRectangle(cornerRadius: 40).fill(LinearGradient(colors: [Color(argb: 0xFFFFFDF5), PukiColors.cloudWhite], startPoint: .top, endPoint: .bottom)))
        .overlay(RoundedRectangle(cornerRadius: 40).stroke(Color(argb: 0xFFFFE082), lineWidth: 8))
        .clipShape(RoundedRectangle(cornerRadius: 40))
        .shadow(color: .black.opacity(0.3), radius: 16, y: 8)
        .fixedSize()
    }

    private func run() async {
        await delay(200)
        let repository = app.progressRepository
        let progress = repository.progress
        // One sticker to a card: if she's crossed two lines, the next one waits for the next card
        let sticker = nextSticker(rewarded: progress.rewardedStars, stars: progress.totalStars)
        repository.markRewarded(sticker?.starsRequired ?? progress.totalStars)
        earned = sticker
        if let sticker { DayLog.record(RecapContent.sticker(sticker.name)) }
        showContent = true
        showConfetti = !calm
        app.voice.play(app.voice.personalKey("voice_round_done", language), language)
        guard let sticker else {
            await delay(1500)
            buttonsLive = true
            return
        }
        // Once her stars have landed: the sticker, big, then away into the album
        await delay(1300 + max(1, starsEarned) * 380)
        await waitQuiet(3000)
        stickerUp = true
        if calm {
            app.voice.play("voice_sticker_\(sticker.id)", language)
        } else {
            app.sfx.play(.success)
            showConfetti = true
            app.companion.react("קיבלו עכשיו מדבקה חדשה של \(sticker.name.he)!", "The child just got a new \(sticker.name.en.lowercased()) sticker!") {
                app.voice.playSequence(["voice_sticker_new", "voice_sticker_\(sticker.id)"], language)
            }
        }
        await delay(1400)
        await waitQuiet(4000)
        await delay(calm ? 900 : 300)
        withAnimation(.easeInOut(duration: calm ? 1.1 : 0.7)) { flight = 1 }
        await delay(calm ? 1100 : 700)
        stickerUp = false
        if !calm { app.sfx.play(.starCollect, volume: 0.8) }
        buttonsLive = true
        await door.celebrate()
    }

    private func waitQuiet(_ ms: Int) async {
        let end = uptimeMs() + Int64(ms)
        while app.voice.speaking && uptimeMs() < end { await delay(100) }
    }
}

/** A new sticker, shown big: it pops up over the card with a little jump. */
private struct BigSticker: View {
    let sticker: StickerDef
    @StateObject private var jump = ReactionState()
    var body: some View {
        GeometryReader { g in
            ZStack {
                Circle().fill(Color.white).shadow(color: .black.opacity(0.3), radius: 16, y: 6)
                Circle().stroke(Color(argb: 0xFFFFE082), lineWidth: 10)
                Pic(sticker.icon).frame(width: g.size.width * 0.76, height: g.size.height * 0.76)
            }
        }
        .rotationEffect(.degrees(-5))
        .reaction(jump, jumpHeight: 24)
        .popIn()
        .task { await jump.celebrate() }
    }
}

/** Soft rays turning slowly behind the card. */
private struct SunRays: View {
    let side: CGFloat
    @State private var turn = 0.0
    var body: some View {
        DrawCanvas { d in
            let c = d.center
            let r = d.size.maxDimension
            d.drawCircle(Brush.radialGradient([Color(argb: 0x66FFF59D), .clear], center: c, radius: d.size.minDimension * 0.6), d.size.minDimension * 0.6, c)
            let rays = Path { p in
                let n = 16
                for i in stride(from: 0, to: n, by: 2) {
                    let a0 = CGFloat(i) * 2 * .pi / CGFloat(n), a1 = CGFloat(i + 1) * 2 * .pi / CGFloat(n)
                    p.move(to: c)
                    p.addLine(to: CGPoint(c.x + cos(a0) * r, c.y + sin(a0) * r))
                    p.addLine(to: CGPoint(c.x + cos(a1) * r, c.y + sin(a1) * r))
                    p.closeSubpath()
                }
            }
            d.drawPath(rays, Brush.radialGradient([Color(argb: 0x40FFFFFF), .clear], center: c, radius: r * 0.55))
        }
        .frame(width: side, height: side)
        .rotationEffect(.degrees(turn))
        .onAppear { withAnimation(.linear(duration: 30).repeatForever(autoreverses: false)) { turn = 360 } }
        .allowsHitTesting(false)
    }
}

/** A star that drops in spinning, squashes as it lands, and plays its chime. */
private struct LandingStar: View {
    let index: Int
    let size: CGFloat
    @EnvironmentObject private var app: AppModel
    @State private var scale: CGFloat = 0
    @State private var spin = -200.0
    @StateObject private var reaction = ReactionState()
    var body: some View {
        Pic("ic_ui_star")
            .frame(width: size, height: size)
            .scaleEffect(scale)
            .rotationEffect(.degrees(spin))
            .opacity(Double(scale.coerceIn(0, 1)))
            .reaction(reaction, jumpHeight: 14)
            .frame(width: size, height: size)
            .task {
                await delay(450 + index * 380)
                app.sfx.play(.starCollect, volume: 0.8)
                withAnimation(.spring(response: 0.6, dampingFraction: 0.5)) { spin = 0 }
                withAnimation(.spring(response: 0.45, dampingFraction: 0.4)) { scale = 1 }
                await delay(450)
                await reaction.celebrate()
            }
    }
}

/** A big round button with a picture, so it can be used without reading; the label is for grown-ups. */
struct RoundButton<Icon: View>: View {
    let color: Color
    let label: BilingualText
    let index: Int
    let pulse: Bool
    let onClick: () -> Void
    @ViewBuilder let icon: () -> Icon
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @State private var beat: CGFloat = 1

    var body: some View {
        VStack(spacing: 6) {
            ZStack { icon() }
                .frame(width: 124, height: 124)
                .background(Circle().fill(LinearGradient(colors: [lerp(color, .white, 0.2), color], startPoint: .top, endPoint: .bottom)))
                .overlay(Circle().stroke(Color.white, lineWidth: 6))
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.25), radius: 10, y: 4)
                .scaleEffect(beat)
                .clickable { app.sfx.play(.pop); onClick() }
                .popIn(index + 2)
            Text(label(language))
                .font(PukiFont.titleMedium)
                .foregroundColor(Color(argb: 0xFF8D6E63))
        }
        .onChange(of: pulse) { _, on in
            if on { withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { beat = 1.08 } } else { beat = 1 }
        }
    }
}

/** A chunky white arrow pointing onwards (it flips for right-to-left). */
struct NextArrow: View {
    @Environment(\.layoutDirection) private var dir
    var body: some View {
        DrawCanvas { d in
            let w = d.size.width, h = d.size.height
            d.drawLine(.white, CGPoint(w * 0.18, h * 0.5), CGPoint(w * 0.78, h * 0.5), w * 0.17, .round)
            let head = Path { p in p.moveTo(w * 0.5, h * 0.2); p.lineTo(w * 0.8, h * 0.5); p.lineTo(w * 0.5, h * 0.8) }
            d.drawPath(head, .white, style: Stroke(width: w * 0.17, cap: .round, join: .round))
        }
        .frame(width: 64, height: 64)
        .scaleEffect(x: dir == .rightToLeft ? -1 : 1, y: 1)
    }
}
