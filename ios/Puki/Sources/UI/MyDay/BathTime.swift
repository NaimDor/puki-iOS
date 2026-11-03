import SwiftUI

struct BodyPart: Hashable { let id: String; let name: BilingualText; var cleaned = false }

private enum BathStep: Int, CaseIterable { case soap, rinse, dry }

/** Where each muddy spot sits on the child (fractions of the picture), and its radius. */
private struct Spot { let zoneId: String; let partId: String; let x, y, r: CGFloat }

private let spots = [
    Spot(zoneId: "face", partId: "face", x: 0.565, y: 0.295, r: 0.045),  // a cheek, clear of the eyes
    Spot(zoneId: "arms", partId: "arms", x: 0.31, y: 0.52, r: 0.05),
    Spot(zoneId: "arms2", partId: "arms", x: 0.69, y: 0.52, r: 0.05),
    Spot(zoneId: "tummy", partId: "tummy", x: 0.5, y: 0.5, r: 0.06),
    Spot(zoneId: "legs", partId: "legs", x: 0.435, y: 0.74, r: 0.045),
    Spot(zoneId: "legs2", partId: "legs", x: 0.565, y: 0.74, r: 0.045),
]

/** Bath time: soap off the mud, rinse under the shower, dry with the towel, and all clean. */
struct BathTimeScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var dd = DragDropState()
    @StateObject private var game = StepGame(first: BathStep.soap, steps: BathStep.allCases.count)
    @StateObject private var help = Help()
    @State private var parts = [BodyPart(id: "arms", name: BilingualText("ידיים", "Arms")), BodyPart(id: "legs", name: BilingualText("רגליים", "Legs")),
                                BodyPart(id: "tummy", name: BilingualText("בטן", "Tummy")), BodyPart(id: "face", name: BilingualText("פנים", "Face"))]
    @State private var rinsing = false
    @State private var drying = false
    @State private var dried = false

    private var allClean: Bool { parts.allSatisfy(\.cleaned) }

    var body: some View {
        let step = game.step
        let busy = game.betweenSteps || rinsing || drying || dried
        let nextSpot = spots.first { s in !parts.contains { $0.id == s.partId && $0.cleaned } }
        GameBackground(scene: .bath) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: step.rawValue, totalRounds: BathStep.allCases.count, starsEarned: game.stars)
                // The child in the tub, with the soap, the shower or the towel beside it
                GeometryReader { g in
                    let tool = max(100, min(g.size.height * 0.42, 150))
                    HStack(spacing: 0) {
                        BathScene(parts: parts, rinsing: rinsing, washed: rinsing || step == .dry, wet: step == .dry && !dried,
                                  hintZone: step == .soap && help.showHint ? nextSpot?.zoneId : nil)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        ZStack {
                            if step == .soap && !allClean {
                                // A tap soaps the next muddy spot
                                DragTarget(itemId: "soap", onDragEnd: { soapDropped($0) }, tapToZone: nextSpot?.zoneId) {
                                    Pic("ic_myday_soap").frame(width: tool * 0.68, height: tool * 0.68)
                                        .frame(width: tool, height: tool)
                                        .background(Circle().fill(Color(argb: 0xFFFFF9C4)).shadow(color: .black.opacity(0.25), radius: 8, y: 3))
                                        .idleBob(0)
                                        .hintGlow(help.showHint)
                                }
                            }
                            if step == .rinse && !rinsing && !game.betweenSteps {
                                ToolButton(size: tool, hint: help.showHint, color: Color(argb: 0xFF29B6F6), onClick: rinse) {
                                    Pic("ic_myday_shower").frame(width: tool * 0.64, height: tool * 0.64)
                                }
                            }
                            if step == .dry && !busy {
                                ToolButton(size: tool, hint: help.showHint, color: Color(argb: 0xFFFCE4EC), onClick: dry) {
                                    DrawCanvas { d in drawTowel(d) }.frame(width: tool * 0.66, height: tool * 0.66)
                                }
                            }
                        }
                        .frame(width: tool * 1.4)
                    }
                    .padding(.leading, pukiCorner)  // clear of Puki
                    .frame(width: g.size.width, height: g.size.height)
                }
            }
            .padding(16)
            ConfettiOverlay(show: game.celebrate)
            if game.sessionComplete { RoundCompleteSheet(starsEarned: game.stars, onContinue: onPlayAgain, onHome: onBack) }
        }
        .environmentObject(dd)
        .task { game.attach(app, island: "bath") }
        .helpLoop(help, key: step.rawValue, active: !busy) {
            app.voice.play(step == .soap ? "voice_prompt_bath" : step == .rinse ? "voice_bath_rinse" : "voice_bath_dry", language)
        }
        .modifier(BathPrompt(step: step))
        .onChange(of: allClean) { _, clean in if clean { game.done(.rinse) } }
    }

    private func soapDropped(_ dropId: String?) {
        guard let partId = dropId?.removeSuffix("2"), parts.contains(where: { $0.id == partId }) else { return }  // each arm and leg is its own spot
        parts = parts.map { $0.id == partId ? BodyPart(id: $0.id, name: $0.name, cleaned: true) : $0 }
        app.sfx.play(.pop)
        app.voice.play("voice_body_\(partId)", language)
    }

    private func rinse() {
        app.sfx.play(.whoosh)
        rinsing = true
        Task {
            await delay(2200)  // let the water pour first
            rinsing = false
            game.done(.dry)
        }
    }

    private func dry() {
        app.sfx.play(.whoosh, volume: 0.6)
        drying = true
        dried = true
        Task {
            await delay(900)
            app.voice.play("voice_bath_clean", language) {
                app.companion.react("באמבטיה סיבנו את כל הבוץ, שטפו במקלחת והתנגבו. עכשיו הכול נקי!", "At bath time the child soaped off all the mud, rinsed in the shower and dried off. All clean!") {}
            }
            game.done(nil)
            await delay(2800)
            game.finish()
        }
    }
}

private struct BathPrompt: ViewModifier {
    let step: BathStep
    func body(content: Content) -> some View {
        switch step {
        case .soap: content.roundPrompt(0, "באמבטיה: מסבנים את הבוץ מהפנים, מהידיים, מהבטן ומהרגליים, ואז שוטפים במקלחת.", "At bath time: soaping the mud off the face, arms, tummy and legs, then a rinse in the shower.", "voice_prompt_bath")
        case .rinse: content.roundPrompt(1, "באמבטיה: שוטפים את הסבון במקלחת.", "At bath time: rinsing the soap off in the shower.", "voice_bath_rinse")
        case .dry: content.roundPrompt(2, "באמבטיה: מתנגבים במגבת.", "At bath time: drying off with the towel.", "voice_bath_dry")
        }
    }
}

/** The child standing in the tub: mud on each spot until it's soaped, then bubbles. */
private struct BathScene: View {
    let parts: [BodyPart]
    let rinsing: Bool
    let washed: Bool
    let wet: Bool
    let hintZone: String?
    @Environment(\.gender) private var gender

    var body: some View {
        GeometryReader { g in
            let side = min(g.size.width, g.size.height)
            ZStack(alignment: .topLeading) {
                // Still: the child
                DrawCanvas { d in aboveWater(d) { drawToddler($0, girl: gender == .girl, worn: ["underwear": 1]) } }
                // The mud left on her: each fades as it's soaped off
                ForEach(Array(spots.enumerated()), id: \.offset) { i, spot in
                    let clean = parts.contains { $0.id == spot.partId && $0.cleaned }
                    Tweened(clean ? 0 : 1) { amount in DrawCanvas { d in drawMudSplat(d, CGPoint(spot.x, spot.y), spot.r, i * 7 + 3, amount: amount) } }
                        .animation(.easeInOut(duration: 0.5), value: clean)
                }
                // Moving: the bubbles, the water and the shower
                Tweened(washed ? 0 : 1) { bubblesLeft in
                    Tweened(wet ? 1 : 0) { dropsLeft in
                        AmbientTimeline { t in
                            let wave = Ambient.loop(t, 2.2)
                            let fall = rinsing ? Ambient.loop(t, 0.7) : 0
                            DrawCanvas { d in
                                for spot in spots where parts.contains(where: { $0.id == spot.partId && $0.cleaned }) {
                                    drawSoapBubbles(d, CGPoint(spot.x, spot.y), spot.r, wave, left: bubblesLeft)
                                }
                                // (not on her face: there, a drop reads as a tear)
                                if dropsLeft > 0 { for spot in spots where spot.partId != "face" { drawWaterDrop(d, CGPoint(spot.x, spot.y), dropsLeft) } }
                                drawTub(d, wave: wave, fall: fall, rinsing: rinsing)
                            }
                        }
                    }
                    .animation(.easeInOut(duration: 0.8), value: wet)
                }
                .animation(.easeInOut(duration: 1.4).delay(0.3), value: washed)
                // Where the soap goes: a little bigger than the mud, so it's easy to hit
                ForEach(spots, id: \.zoneId) { spot in
                    let r = spot.r * 1.5
                    DropZone(zoneId: spot.zoneId) { Color.clear.frame(width: side * r * 2, height: side * r * 2) }
                        .hintGlow(hintZone == spot.zoneId)
                        .offset(x: side * (spot.x - r), y: side * (spot.y - r))
                }
            }
            .frame(width: side, height: side)
            .environment(\.layoutDirection, .leftToRight)  // (the picture doesn't mirror)
            .frame(width: g.size.width, height: g.size.height)
        }
    }
}

/** A folded towel, for the last step. */
private func drawTowel(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0xFFF48FB1), CGPoint(0, h * 0.2), CGSize(w, h * 0.62), h * 0.08)
    d.drawRoundRect(Color(argb: 0xFFEC407A), CGPoint(0, h * 0.7), CGSize(w, h * 0.12), h * 0.05)
    for k in 0...2 { d.drawLine(Color.white.copy(alpha: 0.7), CGPoint(w * 0.06, h * (0.32 + CGFloat(k) * 0.13)), CGPoint(w * 0.94, h * (0.32 + CGFloat(k) * 0.13)), h * 0.035) }
}
