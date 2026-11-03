import SwiftUI

/** Where an animal can hide on the farm. */
enum HidingSpot: CaseIterable { case barn, bush, hay, stump }

let peekabooRounds = 8

struct PeekabooState {
    var animal: AnimalDef
    var spot: HidingSpot
    var found = false  // the animal is out
    var roundIndex = 0
    var totalRounds = peekabooRounds
    var stars = 0
    var sessionComplete = false
}

/**
 * Peekaboo barn: each round an animal hides behind one of the spots, and the child finds it. Nothing to get
 * wrong: a tap on another spot only sends out a butterfly and a clue.
 */
@MainActor
final class PeekabooViewModel: ObservableObject {
    private(set) var session: GameSession!
    private let animals = Array(allAnimals.shuffled().prefix(peekabooRounds))
    private var lastSpot: HidingSpot?
    @Published private(set) var state: PeekabooState
    @Published private(set) var level = 0

    init() {
        state = PeekabooState(animal: level1Animals[0], spot: .barn)
        state = round(0, stars: 0)
    }

    func start(_ app: AppModel) {
        guard session == nil else { return }
        session = GameSession("peekaboo", app.progressRepository)
        level = session.level()
    }

    private func round(_ index: Int, stars: Int) -> PeekabooState {
        // Never the same spot twice in a row, so it's a real search
        let spot = HidingSpot.allCases.filter { $0 != lastSpot }.randomElement()!
        lastSpot = spot
        return PeekabooState(animal: animals[index], spot: spot, roundIndex: index, stars: stars)
    }

    /** True if the animal was there, false if not, nil once it's already out (a second tap). */
    func onSpotTapped(_ spot: HidingSpot) -> Bool? {
        if state.found || state.sessionComplete { return nil }
        if spot != state.spot { return false }
        state.found = true
        state.stars += 1
        session.star()
        DayLog.record(RecapContent.found(state.animal))
        return true
    }

    func nextRound() {
        let next = state.roundIndex + 1
        if next >= peekabooRounds {
            state.sessionComplete = true
            session.finish(rounds: peekabooRounds)
        } else {
            state = round(next, stars: state.stars)
        }
    }
}

/**
 * Peekaboo barn: an animal hides on the farm, Puki asks who's hiding and the animal's sound gives it away. A tap
 * on the right spot brings it out; any other spot sends out a butterfly and the animal peeks and calls again.
 */
struct PeekabooScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var vm = PeekabooViewModel()
    @StateObject private var help = Help()
    @StateObject private var foundReaction = ReactionState()
    @State private var peeking = false
    @State private var butterflyFrom: HidingSpot?
    @State private var butterflyKey = 0
    @State private var names: [AnimalDef] = []

    var body: some View {
        let state = vm.state
        let animal = state.animal
        let she = feminineAnimals.contains(animal.id)
        // Puki flies in the sky here: in his usual corner he'd stand in front of the barn
        GameBackground(withCompanion: false, scene: .farm) {
            if vm.level > 0 {
                ZStack(alignment: .topLeading) {
                    VStack(spacing: 0) {
                        GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.totalRounds, starsEarned: state.stars)
                        HStack(alignment: .bottom, spacing: 0) {
                            ForEach(HidingSpot.allCases, id: \.self) { spot in
                                let here = spot == state.spot
                                SpotView(spot: spot, animal: here ? animal : nil,
                                         show: here && state.found ? 1 : here && peeking ? 0.35 : 0,
                                         hint: here && help.showHint && !state.found, hiding: here && !state.found,
                                         reaction: foundReaction, butterflyKey: butterflyFrom == spot ? butterflyKey : 0) { tap(spot) }
                                    // The barn is the big one
                                    .frame(maxWidth: .infinity)
                                    .layoutPriority(spot == .barn ? 1.35 : 1)
                            }
                        }
                        .frame(maxHeight: .infinity)
                        .padding(.horizontal, 8)
                    }
                    .padding(16)
                    // After the farm: the barn doesn't cover him, and a tap on him isn't a tap on the barn
                    CompanionParrot().padding(.top, 96).padding(.leading, 12)
                    ConfettiOverlay(show: state.found)
                    if state.sessionComplete { RoundCompleteSheet(starsEarned: state.stars, onContinue: onPlayAgain, onHome: onBack) }
                }
            }
        }
        .task { vm.start(app) }
        .modifier(PeekPrompt(state: state, level: vm.level))
        .helpLoop(help, key: state.roundIndex, active: !state.found && vm.level > 0, missesCounted: false, onAssisted: { vm.session?.slip() }) { peek() }
        .task(id: "\(state.roundIndex)-\(vm.level)") {
            peeking = false
            names = [animal] + allAnimals.filter { $0.id != animal.id }.shuffled().prefix(7)
            // At the first level the animal gives itself away without being asked
            guard vm.level == 1, await delay(3500) else { return }
            if !vm.state.found { peek() }
        }
        .onChange(of: state.found) { _, f in if f { Task { await foundReaction.celebrate() } } }
        // "Who's hiding?" "פרה!": naming the animal finds it; another animal's name gets a peek
        .answerOutLoud(state.roundIndex, options: names.map { [$0.name(language)] }, active: !state.found && vm.level > 0, askedAfterMs: 1200) { i in
            if i == 0 { tap(vm.state.spot) } else { help.missed(); peek() }
        }
        .onAppear { _ = she }
    }

    /** The animal peeks out a little (with its sound): a clue. */
    private func peek() {
        let id = vm.state.animal.id
        Task {
            peeking = true
            app.voice.play("voice_sound_\(id)", language)
            await delay(1500)
            peeking = false
        }
    }

    /** A hiding spot tapped: the animal is found, or a butterfly flies out and the animal peeks. */
    private func tap(_ spot: HidingSpot) {
        let animal = vm.state.animal
        let she = feminineAnimals.contains(animal.id)
        switch vm.onSpotTapped(spot) {
        case true?:
            app.sfx.play(.success)
            let whereHe: String, whereEn: String
            switch spot {
            case .barn: whereHe = "ברפת"; whereEn = "in the barn"
            case .bush: whereHe = "מאחורי השיח"; whereEn = "behind the bush"
            case .hay: whereHe = "מאחורי ערמת החציר"; whereEn = "behind the haystack"
            case .stump: whereHe = "בתוך הגזע"; whereEn = "in the tree stump"
            }
            app.companion.react("מצאו את ה\(animal.name.he)! \(she ? "היא התחבאה" : "הוא התחבא") \(whereHe).",
                                "The child found the \(animal.name.en.lowercased())! It was hiding \(whereEn).") {
                app.voice.playSequence(["voice_peekaboo", "voice_sound_\(animal.id)", "voice_animal_\(animal.id)"], language)
            }
            Task {
                await delay(app.companion.isReady ? 4000 : 3200)
                vm.nextRound()
            }
        case false?:
            app.sfx.play(.pop)
            butterflyFrom = spot
            butterflyKey += 1
            help.missed()
            peek()
        case nil: break
        }
    }
}

private struct PeekPrompt: ViewModifier {
    let state: PeekabooState
    let level: Int
    func body(content: Content) -> some View {
        let a = state.animal
        let she = feminineAnimals.contains(a.id)
        // At the top level the animal doesn't call out first: she has to look
        content.roundPrompt(level == 0 ? -1 : state.roundIndex,
                            "במשחק המחבואים: \(a.name.he) \(she ? "מתחבאת" : "מתחבא") ומחפשים \(she ? "אותה" : "אותו").",
                            "In the peekaboo game: the \(a.name.en.lowercased()) is hiding, and we're looking for it.",
                            keys: level == 0 ? [] : level >= 3 ? ["voice_prompt_whos_hiding"] : ["voice_prompt_whos_hiding", "voice_sound_\(a.id)"])
    }
}

/** One hiding spot, with the animal (if it's here) `show`ing: 0 hidden, about 0.35 peeking, 1 out. */
private struct SpotView: View {
    let spot: HidingSpot
    let animal: AnimalDef?
    let show: CGFloat
    let hint: Bool
    let hiding: Bool
    @ObservedObject var reaction: ReactionState
    let butterflyKey: Int
    let onTap: () -> Void
    @State private var shown: AnimalDef?
    @State private var wobble = 0.0

    var body: some View {
        GeometryReader { g in
            // A square-ish spot, as big as fits
            let w = min(g.size.width, g.size.height * 0.9 / 1.1)
            ZStack(alignment: .bottom) {
                Tweened(show) { rise in
                    ZStack(alignment: .bottom) {
                        if spot == .barn { Barn(w: w, animal: shown ?? animal, open: rise, out: show >= 1, reaction: reaction) }
                        else { Cover(spot: spot, w: w, animal: shown ?? animal, rise: rise, out: show >= 1, reaction: reaction) }
                    }
                }
                .animation(.spring(response: 0.42, dampingFraction: 0.45), value: show)
                if butterflyKey != 0 { Butterfly(key: butterflyKey) }
            }
            .frame(width: w, height: w * 1.1)
            .hintGlow(hint)
            .rotationEffect(.degrees(wobble), anchor: .bottom)
            // Only the spot itself: the air above it isn't a wrong guess
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .frame(width: g.size.width, height: g.size.height, alignment: .bottom)
        }
        .onAppear { shown = animal }
        .onChange(of: animal) { _, a in
            // The animal that was here stays until it has ducked back in, instead of vanishing
            if a != nil || show < 0.03 { shown = a } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { shown = animal }
            }
        }
        .task(id: hiding) {
            // Something's in there: now and then the spot rustles
            while hiding && !Task.isCancelled {
                guard await delay(Int.random(in: 4000..<6000)) else { return }
                for a in [2.0, -2, 1.5, -1, 0] { withAnimation(.easeInOut(duration: 0.09)) { wobble = a }; await delay(90) }
            }
        }
        .task(id: butterflyKey) {
            guard butterflyKey != 0 else { return }
            for a in [6.0, -5, 4, -2, 0] { withAnimation(.easeInOut(duration: 0.07)) { wobble = a }; await delay(70) }
        }
    }
}

private struct Alive: ViewModifier {
    let out: Bool
    @ObservedObject var reaction: ReactionState
    func body(content: Content) -> some View {
        // Out and found: it jumps, and keeps breathing there
        Group { if out { content.idleBob() } else { content } }.reaction(reaction, jumpHeight: 16)
    }
}

/** The barn: the animal stands in the doorway, behind two doors that swing open. */
private struct Barn: View {
    let w: CGFloat
    let animal: AnimalDef?
    let open: CGFloat
    let out: Bool
    let reaction: ReactionState
    var body: some View {
        ZStack(alignment: .bottom) {
            DrawCanvas { d in drawBarn(d) }
            ZStack(alignment: .bottom) {
                if let animal {
                    Pic(animal.drawable)
                        .frame(width: w * 0.42, height: w * 0.42)
                        .scaleEffect(0.75 + 0.35 * open, anchor: .bottom)
                        .offset(y: -w * 0.42 * 0.08 * open)
                        .modifier(Alive(out: out, reaction: reaction))
                }
                // Left to right in both languages: mirrored, the doors would hinge in the middle
                HStack(spacing: 0) {
                    ForEach([-1.0, 1.0], id: \.self) { side in
                        // Each door turns on its outer hinge, like a real barn door
                        DrawCanvas { d in drawDoor(d) }
                            .rotation3DEffect(.degrees(side * -100 * Double(open)), axis: (x: 0, y: 1, z: 0),
                                              anchor: side < 0 ? .leading : .trailing, perspective: 0.5)
                    }
                }
                .environment(\.layoutDirection, .leftToRight)
            }
            .frame(width: w * 0.44, height: w * 0.48)
        }
    }
}

/** A bush, the haystack or a tree stump; the animal rises from behind it. */
private struct Cover: View {
    let spot: HidingSpot
    let w: CGFloat
    let animal: AnimalDef?
    let rise: CGFloat
    let out: Bool
    let reaction: ReactionState
    var body: some View {
        ZStack(alignment: .bottom) {
            if let animal {
                Pic(animal.drawable)
                    .frame(width: w * 0.6, height: w * 0.6)
                    .offset(y: (1 - rise) * w * 0.6 * 0.95)
                    .opacity(rise < 0.03 ? 0 : 1)  // fully hidden: not even a foot under the hay
                    .modifier(Alive(out: out, reaction: reaction))
                    .padding(.bottom, w * 0.4)
            }
            DrawCanvas { d in
                switch spot {
                case .hay: drawHaystack(d)
                case .bush: drawBush(d)
                default: drawStump(d)
                }
            }
            .frame(width: w, height: w * 0.56)
        }
        .frame(width: w, height: w * 1.1, alignment: .bottom)
        .clipped()
    }
}

/** A butterfly flutters up out of a spot where no one was hiding. */
private struct Butterfly: View {
    let key: Int
    @State private var t: CGFloat = 0
    var body: some View {
        Tweened(t) { p in
            DrawCanvas { d in
                guard p < 1 else { return }
                let x = d.size.width * (0.5 + 0.25 * sin(p * 3 * .pi))
                let y = d.size.height * (0.55 - 0.65 * p)
                let flap = 0.4 + 0.6 * abs(sin(p * 18 * .pi))
                let r = d.size.width * 0.13
                let fade = Double(1 - p * p)
                for side in [-1.0, 1.0] as [CGFloat] {
                    let ww = r * flap
                    let left = side < 0 ? x - ww : x
                    d.drawOval(Color(argb: 0xFFFF7043).copy(alpha: fade), CGPoint(left, y - r), CGSize(ww, r))
                    d.drawOval(Color(argb: 0xFFFFCA28).copy(alpha: fade), CGPoint(left + (side < 0 ? ww * 0.25 : 0), y - r * 0.1), CGSize(ww * 0.75, r * 0.75))
                }
                d.drawLine(Color(argb: 0xFF4E342E).copy(alpha: fade), CGPoint(x, y - r * 0.9), CGPoint(x, y + r * 0.6), r * 0.16)
            }
        }
        .allowsHitTesting(false)
        .onAppear { start() }
        .onChange(of: key) { _, _ in start() }
    }
    private func start() {
        var tr = Transaction(animation: nil); tr.disablesAnimations = true
        withTransaction(tr) { t = 0 }
        DispatchQueue.main.async { withAnimation(.easeInOut(duration: 1.6)) { t = 1 } }
    }
}

// ---------- Drawing the farm's hiding spots ----------

private let barnRed = Color(argb: 0xFFD84343)
private let barnDark = Color(argb: 0xFFA62F2F)
private let trim = Color(argb: 0xFFFFF8E1)

private func drawBarn(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let wallTop = h * 0.42
    let roof = Path { p in p.moveTo(w * 0.02, wallTop + h * 0.02); p.lineTo(w * 0.5, h * 0.06); p.lineTo(w * 0.98, wallTop + h * 0.02); p.close() }
    d.drawPath(roof, barnDark)
    d.drawRoundRect(barnRed, CGPoint(w * 0.08, wallTop), CGSize(w * 0.84, h - wallTop), w * 0.02)
    d.drawPath(roof, trim, style: Stroke(width: w * 0.025))
    d.drawRoundRect(trim, CGPoint(w * 0.42, h * 0.22), CGSize(w * 0.16, h * 0.13), w * 0.02)
    d.drawRoundRect(Color(argb: 0xFF5D4037), CGPoint(w * 0.44, h * 0.24), CGSize(w * 0.12, h * 0.09), w * 0.015)
    d.drawRect(Color(argb: 0xFF3E2723), CGPoint(w * 0.28, h - w * 0.48), CGSize(w * 0.44, w * 0.48))
}

private func drawDoor(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRect(barnRed, .zero, d.size)
    let t = w * 0.09
    d.drawRect(trim, .zero, d.size, style: Stroke(width: t))
    d.drawLine(trim, .zero, CGPoint(w, h), t)
    d.drawLine(trim, CGPoint(w, 0), CGPoint(0, h), t)
}

private func drawBush(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let dark = Color(argb: 0xFF388E3C), light = Color(argb: 0xFF66BB6A)
    for (fx, fy) in [(0.22, 0.62), (0.5, 0.45), (0.78, 0.62)] as [(CGFloat, CGFloat)] { d.drawCircle(dark, h * 0.42, CGPoint(w * fx, h * fy)) }
    for (fx, fy) in [(0.3, 0.66), (0.55, 0.5), (0.74, 0.7)] as [(CGFloat, CGFloat)] { d.drawCircle(light, h * 0.26, CGPoint(w * fx, h * fy)) }
    d.drawRect(dark, CGPoint(w * 0.08, h * 0.7), CGSize(w * 0.84, h * 0.3))
    for (fx, fy) in [(0.32, 0.4), (0.62, 0.32), (0.7, 0.6)] as [(CGFloat, CGFloat)] { d.drawCircle(Color(argb: 0xFFE53935), h * 0.045, CGPoint(w * fx, h * fy)) }
}

private func drawHaystack(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let dome = Path { p in
        p.moveTo(w * 0.05, h)
        p.cubicTo(w * 0.08, h * 0.4, w * 0.3, h * 0.04, w * 0.5, h * 0.04)
        p.cubicTo(w * 0.7, h * 0.04, w * 0.92, h * 0.4, w * 0.95, h)
        p.close()
    }
    d.drawPath(dome, Color(argb: 0xFFDDAA3E))
    d.drawOval(Color(argb: 0xFFF2CC62), CGPoint(w * 0.24, h * 0.12), CGSize(w * 0.5, h * 0.42))
    // Loose straws, the same ones every time
    var rnd = SeededRandom(5)
    for i in 0..<26 {
        let x = w * (0.14 + rnd.nextFloat() * 0.72)
        let y = h * (0.26 + rnd.nextFloat() * 0.68)
        let lean = (rnd.nextFloat() - 0.5) * w * 0.09
        d.drawLine(i % 3 == 0 ? Color(argb: 0xFFB9862B) : Color(argb: 0xFFFFE08A), CGPoint(x, y), CGPoint(x + lean, y + h * 0.13), w * 0.012, .round)
    }
    d.drawLine(Color(argb: 0xFFB9862B), CGPoint(w * 0.05, h * 0.985), CGPoint(w * 0.95, h * 0.985), h * 0.03, .round)
}

private func drawStump(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    let bark = Color(argb: 0xFF8D6E63), barkDark = Color(argb: 0xFF6D4C41)
    d.drawRect(bark, CGPoint(w * 0.14, h * 0.22), CGSize(w * 0.72, h * 0.78))
    d.drawOval(barkDark, CGPoint(w * 0.14, h * 0.08), CGSize(w * 0.72, h * 0.28))
    d.drawOval(Color(argb: 0xFFD7A86E), CGPoint(w * 0.19, h * 0.11), CGSize(w * 0.62, h * 0.22))
    d.drawOval(Color(argb: 0xFF4E342E), CGPoint(w * 0.3, h * 0.15), CGSize(w * 0.4, h * 0.14))
    for i in 0..<4 {
        let x = w * (0.24 + CGFloat(i) * 0.17)
        d.drawLine(barkDark, CGPoint(x, h * 0.36), CGPoint(x + w * 0.02, h * 0.95), w * 0.02)
    }
    d.drawOval(bark, CGPoint(w * 0.02, h * 0.84), CGSize(w * 0.24, h * 0.16))
    d.drawOval(bark, CGPoint(w * 0.74, h * 0.84), CGSize(w * 0.24, h * 0.16))
}
