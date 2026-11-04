import SwiftUI

/** Counting each thing, then (from level 2) answering "how many?", then the round is done. */
enum CountingPhase { case count, howMany, done }

struct CountingState {
    var currentRound = level1CountingRounds[0]
    /** For each thing in the garden, the number it got when tapped (0: not counted yet). */
    var numbers: [Int] = [0]
    var phase = CountingPhase.count
    /** The numbers to choose from for "how many?", smallest first. */
    var choices: [Int] = []
    /** A round of giving: the rabbit wants this many, and a couple more lie around than it wants. */
    var give = false
    var roundIndex = 0
    var totalRounds = 4
    var sessionComplete = false
    var totalStarsEarned = 0
    var isLoading = true
    var currentCount: Int { numbers.filter { $0 > 0 }.count }
    var roundComplete: Bool { phase == .done }
}

private let giveSpare = 2

@MainActor
final class CountingGardenViewModel: ObservableObject {
    @Published private(set) var state = CountingState()
    private(set) var session: GameSession!
    private var rounds: [CountingRound] = []
    private var level = 1

    func start(_ app: AppModel) {
        guard session == nil else { return }
        session = GameSession("counting", app.progressRepository)
        level = session.level()
        // Bigger numbers at higher levels, with one easier round from the level below
        let counting: [CountingRound]
        if level == 1 {
            counting = Dictionary(grouping: level1CountingRounds, by: \.targetNumber).values.map { $0.randomElement()! }.shuffled()
        } else {
            counting = (Array(countingRoundsForLevel(level).shuffled().prefix(3)) + Array(countingRoundsForLevel(level - 1).shuffled().prefix(1))).shuffled()
        }
        // Every other round gives instead of counts
        rounds = counting.enumerated().map { i, r in i % 2 == 1 ? giveRound(r.targetNumber) : r }
        startRound(0)
    }

    private func startRound(_ index: Int) {
        if index >= rounds.count {
            state.sessionComplete = true
            session.finish(rounds: rounds.count)
            return
        }
        let round = rounds[index]
        let give = index % 2 == 1
        state = CountingState(currentRound: round, numbers: Array(repeating: 0, count: round.targetNumber + (give ? giveSpare : 0)),
                              give: give, roundIndex: index, totalRounds: rounds.count, totalStarsEarned: state.totalStarsEarned, isLoading: false)
    }

    /** The child touched thing `index`: its number (1-based), or 0 if it was already counted or counting is over. */
    func onItemTapped(_ index: Int) -> Int {
        guard state.phase == .count, state.numbers.getOrNull(index) == 0 else { return 0 }
        let number = state.currentCount + 1
        var s = state
        s.numbers[index] = number
        let target = s.currentRound.targetNumber
        if number < target {
        } else if level >= 2 && !s.give {
            s.phase = .howMany
            s.choices = choicesFor(target)
        } else {
            s = complete(s)
        }
        state = s
        return number
    }

    /** "How many?" answered with `number`: true if right (the round is done). */
    func onAnswer(_ number: Int) -> Bool {
        guard state.phase == .howMany else { return false }
        if number != state.currentRound.targetNumber {
            session.slip()
            return false
        }
        state = complete(state)
        return true
    }

    private func complete(_ s: CountingState) -> CountingState {
        session.star()
        if s.currentRound.targetNumber > 1 { DayLog.record(RecapContent.counted(s.currentRound)) }
        var out = s
        out.phase = .done
        out.totalStarsEarned += 1
        return out
    }

    /** The answer and two other numbers close to it, smallest first. */
    private func choicesFor(_ target: Int) -> [Int] {
        let near = ((target - 2)...(target + 2)).filter { (1...10).contains($0) && $0 != target }.shuffled().prefix(2)
        return (Array(near) + [target]).sorted()
    }

    func nextRound() { startRound(state.roundIndex + 1) }
}

/**
 * Counting: one touch, one number. Each thing counts once, hops and keeps its number. At the end the last number
 * is said again, and from level 2 the child answers "how many?" herself, from three numbers.
 */
struct CountingGardenScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.layoutDirection) private var dir
    @StateObject private var vm = CountingGardenViewModel()
    @StateObject private var flow = RoundFlow()
    @StateObject private var help = Help()
    @StateObject private var items = ReactionBank()
    @StateObject private var cards = ReactionBank()
    @StateObject private var rabbit = ReactionState()
    @State private var sessionSeed = newSessionSeed()

    struct PhaseKey: Hashable { let round: Int; let phase: String }

    var body: some View {
        let state = vm.state
        let round = state.currentRound
        let target = round.targetNumber
        let phaseKey = PhaseKey(round: state.roundIndex, phase: "\(state.phase)")
        GameBackground(scene: .garden) {
            if !state.isLoading {
                VStack(spacing: 0) {
                    GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.totalRounds, starsEarned: state.totalStarsEarned)
                    // The things in their place, and beside them the count or "how many?"
                    GeometryReader { g in
                        let few = state.numbers.count <= 5
                        let itemSize = min(g.size.height * (few ? 0.44 : 0.34), few ? 120 : 88)
                        let sunSize = min(g.size.height * 0.34, 96)
                        let cardSize = min((g.size.height - 24) / 3, 104)
                        let nextToCount = state.numbers.firstIndex(of: 0) ?? -1
                        HStack(spacing: 16) {
                            ZStack {
                                DrawCanvas { d in drawHabitat(d, round.habitat) }
                                Scatter(seed: sessionSeed + state.roundIndex, rtl: dir == .rightToLeft) {
                                    ForEach(0..<state.numbers.count, id: \.self) { i in
                                        // (a carrot she's given goes off to the rabbit)
                                        CountedThing(icon: round.itemIcon, number: state.numbers[i], size: itemSize, index: i, roundIndex: state.roundIndex,
                                                     reaction: items(String(i)), hint: help.showHint && state.phase == .count && i == nextToCount) { tapped(i) }
                                            .popAway(state.give && state.numbers[i] > 0, index: i)
                                    }
                                }
                                .padding(14)
                                .id(state.roundIndex)
                            }
                            .clipShape(RoundedRectangle(cornerRadius: 36))
                            .overlay(RoundedRectangle(cornerRadius: 36).stroke(Color.white, lineWidth: 6))
                            .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            VStack(spacing: 8) {
                                if state.phase == .howMany {
                                    ForEach(Array(state.choices.enumerated()), id: \.element) { i, n in
                                        NumberCard(number: n, size: cardSize, index: i, reaction: cards(String(n)), hint: help.showHint && n == target) { answer(n) }
                                    }
                                } else {
                                    if state.give {
                                        Pic("ic_animal_rabbit").frame(width: sunSize * 1.3, height: sunSize * 1.3)
                                            .reaction(rabbit, jumpHeight: 18).popIn(key: state.roundIndex)
                                    }
                                    // The count so far, in a sun
                                    ZStack {
                                        DrawCanvas { d in drawSunRays(d, d.size.width / 2 / 1.5) }.frame(width: sunSize * 1.5, height: sunSize * 1.5)
                                        Circle().fill(RadialGradient(colors: [Color(argb: 0xFFFFF59D), Color(argb: 0xFFFFCA28)], center: .center, startRadius: 0, endRadius: sunSize / 2))
                                            .frame(width: sunSize, height: sunSize)
                                            .shadow(color: .black.opacity(0.2), radius: 8, y: 3)
                                        Text("\(state.currentCount)")
                                            .font(PukiFont.rounded(sunSize * 0.58, .bold))
                                            .foregroundColor(Color(argb: 0xFF5D4037))
                                    }
                                    .popIn(key: state.currentCount)
                                }
                            }
                            .frame(width: max(cardSize, sunSize * 1.5))
                            .frame(maxHeight: .infinity)
                        }
                        .padding(.leading, pukiCorner)
                        .frame(width: g.size.width, height: g.size.height)
                    }
                }
                .padding(16)
                ConfettiOverlay(show: flow.won)
                if state.sessionComplete { RoundCompleteSheet(starsEarned: state.totalStarsEarned, onContinue: onPlayAgain, onHome: onBack) }
            }
        }
        .task { flow.companion = app.companion; vm.start(app) }
        .onChange(of: state.roundIndex) { _, r in items.reset(for: r); cards.reset(for: r) }
        .modifier(CountingPrompt(state: state))
        .helpLoop(help, key: phaseKey, active: !flow.won && !state.roundComplete && !state.isLoading, onAssisted: { vm.session?.slip() }) {
            let s = vm.state
            app.voice.play(s.phase == .howMany ? "voice_prompt_how_many" : s.give ? "voice_give_\(s.currentRound.targetNumber)" : "voice_prompt_count_along", language)
        }
        // Counting done, from level 2: "how many?" once the last number has been said
        .task(id: phaseKey) {
            guard state.phase == .howMany else { return }
            guard await delay(1100) else { return }
            app.voice.play("voice_prompt_how_many", language)
        }
        .answerOutLoud(phaseKey, options: state.choices.map { numberWords($0, english: language == .english) },
                       active: state.phase == .howMany && !flow.won && !state.roundComplete, askedAfterMs: 1900) { i in
            if let n = vm.state.choices.getOrNull(i) { answer(n) }
        }
    }

    private func tapped(_ i: Int) {
        let r = items(String(i))
        let number = vm.onItemTapped(i)
        if number == 0 {
            // Already counted: a little wiggle, no new number
            if vm.state.phase == .count { Task { await r.shake() } }
            return
        }
        help.touched()
        app.sfx.play(.pop)
        Task { await r.celebrate() }
        if vm.state.give { Task { await rabbit.munch() } }
        app.voice.play("voice_number_\(number)", language)
        if vm.state.roundComplete {
            // Level 1: the last one counted is the answer (on a timer: a held Puki stops the line)
            Task { await delay(900); celebrate() }
        }
    }

    /** "How many?": a number card picked, by a tap or by saying the number. */
    private func answer(_ n: Int) {
        let card = cards(String(n))
        if vm.onAnswer(n) {
            Task { await card.celebrate() }
            celebrate()
        } else {
            app.sfx.play(.wrongGentle)
            help.missed()
            Task { await card.shake() }
            app.voice.playSequence(["voice_number_\(n)", "voice_prompt_try_again"], language)
        }
    }

    /** All the things hop one after another, the number is said once more, and on to the next round. */
    private func celebrate() {
        let s = vm.state
        let target = s.currentRound.targetNumber
        app.sfx.play(.success)
        for i in 0..<s.numbers.count {
            let r = items(String(i))
            Task { await delay(i * 90); await r.celebrate() }
        }
        if s.give { Task { await rabbit.celebrate() } }
        app.voice.play("voice_number_\(target)", language) {
            if s.give {
                app.companion.react("נתנו לארנב \(target) גזרים והוא אכל הכל!", "The child gave the rabbit \(target) carrots, and it ate them all!") { app.voice.playPraise(language) }
            } else {
                app.companion.react("ספרו \(target) \(s.currentRound.itemName.he)!", "The child counted \(target) \(s.currentRound.itemName.en.lowercased())!") { app.voice.playPraise(language) }
            }
        }
        flow.win(quietMs: 3000, talkingMs: 4200) { vm.nextRound() }
    }
}

private struct CountingPrompt: ViewModifier {
    let state: CountingState
    func body(content: Content) -> some View {
        let t = state.currentRound.targetNumber
        if state.isLoading {
            content
        } else if state.give {
            content.roundPrompt(state.roundIndex, "במשחק הספירה: הארנב רעב ורוצה \(t) גזרים, ונותנים לו אחד אחד.",
                                "In the counting game: the hungry rabbit wants \(t) carrots, and we give them one at a time.", "voice_give_\(t)")
        } else {
            content.roundPrompt(state.roundIndex, "במשחק הספירה: סופרים \(state.currentRound.itemName.he), נוגעים בכל אחד.",
                                "In the counting game: we're counting \(state.currentRound.itemName.en.lowercased()), touching each one.", "voice_prompt_count_along")
        }
    }
}

/** One thing in the garden: smaller and still until counted, then full size with its number. */
private struct CountedThing: View {
    let icon: String
    let number: Int
    let size: CGFloat
    let index: Int
    let roundIndex: Int
    @ObservedObject var reaction: ReactionState
    let hint: Bool
    let onTap: () -> Void
    var body: some View {
        let counted = number > 0
        ZStack(alignment: .topTrailing) {
            Group {
                if counted { Pic(icon).idleBob(index, amount: 0.6) } else { Pic(icon) }
            }
            .scaleEffect(counted ? 1 : 0.82)
            .animation(.spring(response: 0.36, dampingFraction: 0.35), value: counted)
            .frame(width: size, height: size)
            if counted {
                // Its number, in a little white badge at the top corner
                Text("\(number)")
                    .font(PukiFont.rounded(24, .semibold))
                    .foregroundColor(Color(argb: 0xFF5D4037))
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(Color.white).shadow(color: .black.opacity(0.2), radius: 4, y: 2))
                    .overlay(Circle().stroke(Color(argb: 0xFFFFCA28), lineWidth: 3))
                    .popIn(key: number)
                    .offset(x: 6, y: -6)
            }
        }
        .frame(width: size, height: size)
        .hintGlow(hint)
        .reaction(reaction)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .testID("choice")
        .popIn(index, key: roundIndex)
    }
}

/** A big number to answer "how many?" with. */
private struct NumberCard: View {
    let number: Int
    let size: CGFloat
    let index: Int
    @ObservedObject var reaction: ReactionState
    let hint: Bool
    let onTap: () -> Void
    var body: some View {
        Text("\(number)")
            .font(PukiFont.rounded(size * 0.58, .bold))
            .foregroundColor(Color(argb: 0xFF5D4037))
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: 28).fill(Color.white).shadow(color: .black.opacity(0.2), radius: 8, y: 3))
            .overlay(RoundedRectangle(cornerRadius: 28).stroke(Color(argb: 0xFF8BC34A), lineWidth: 5))
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .testID("choice")
            .hintGlow(hint)
            .reaction(reaction, jumpHeight: 24)
            .popIn(index)
    }
}

// ---------- Where the round's things live ----------

func drawHabitat(_ d: DrawScope, _ habitat: Habitat) {
    switch habitat {
    case .bed: drawFlowerBed(d)
    case .tree: drawTree(d)
    case .pond: drawPond(d)
    case .night: drawNight(d)
    case .sky: drawOpenSky(d)
    }
}

/** The count's sun: rays around a disc of `radius`. */
func drawSunRays(_ d: DrawScope, _ radius: CGFloat) {
    let c = d.center
    for i in 0..<12 {
        d.rotate(Double(i) * 30, pivot: c) { $0.drawLine(Color(argb: 0xFFFFCA28), c - CGPoint(0, radius * 1.1), c - CGPoint(0, radius * 1.32), radius * 0.12, .round) }
    }
}

private func sky(_ d: DrawScope, _ top: Color, _ bottom: Color) { d.drawRect(Brush.verticalGradient([top, bottom])) }
private func hill(_ d: DrawScope, _ color: Color, _ topFraction: CGFloat) {
    d.drawOval(color, CGPoint(-d.size.width * 0.2, d.size.height * topFraction), CGSize(d.size.width * 1.4, d.size.height * 1.2))
}

private func drawFlowerBed(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    sky(d, Color(argb: 0xFFBBDEFB), Color(argb: 0xFFE3F2FD))
    hill(d, Color(argb: 0xFFA5D6A7), 0.26)
    hill(d, Color(argb: 0xFF81C784), 0.46)
    for k in 0..<3 { d.drawOval(Color(argb: 0x338D6E63), CGPoint(w * 0.06, h * (0.62 + CGFloat(k) * 0.13)), CGSize(w * 0.88, h * 0.075)) }
}

private func drawTree(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    sky(d, Color(argb: 0xFFB3E5FC), Color(argb: 0xFFE1F5FE))
    hill(d, Color(argb: 0xFF81C784), 0.84)
    d.drawRect(Color(argb: 0xFF8D6E63), CGPoint(w * 0.46, h * 0.6), CGSize(w * 0.08, h * 0.34))
    let r = h * 0.3
    for (fx, fy) in [(0.2, 0.42), (0.36, 0.27), (0.5, 0.42), (0.64, 0.27), (0.8, 0.42), (0.36, 0.58), (0.64, 0.58)] as [(CGFloat, CGFloat)] {
        d.drawCircle(Color(argb: 0xFF66BB6A), r, CGPoint(w * fx, h * fy))
    }
    for (fx, fy) in [(0.3, 0.26), (0.58, 0.2), (0.76, 0.34)] as [(CGFloat, CGFloat)] { d.drawCircle(Color(argb: 0xFF81C784), r * 0.42, CGPoint(w * fx, h * fy)) }
}

private func drawPond(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRect(Color(argb: 0xFF81C784))
    d.drawOval(Color(argb: 0xFF8D6E63).copy(alpha: 0.35), CGPoint(w * 0.02, h * 0.05), CGSize(w * 0.96, h * 0.92))
    d.drawOval(Brush.verticalGradient([Color(argb: 0xFF4FC3F7), Color(argb: 0xFF0288D1)]), CGPoint(w * 0.04, h * 0.08), CGSize(w * 0.92, h * 0.86))
    for (fx, fy) in [(0.26, 0.3), (0.66, 0.24), (0.46, 0.66), (0.78, 0.62), (0.18, 0.62)] as [(CGFloat, CGFloat)] {
        d.drawArc(Color.white.copy(alpha: 0.4), startAngle: 200, sweepAngle: 140, useCenter: false, CGPoint(w * fx, h * fy), CGSize(w * 0.09, h * 0.05),
                  style: Stroke(width: h * 0.008, cap: .round))
    }
    for k in 0..<3 {
        let x = w * (0.03 + CGFloat(k) * 0.03)
        d.drawLine(Color(argb: 0xFF558B2F), CGPoint(x, h), CGPoint(x + w * 0.01, h * (0.62 + CGFloat(k) * 0.07)), w * 0.006, .round)
        d.drawOval(Color(argb: 0xFF6D4C41), CGPoint(x, h * (0.62 + CGFloat(k) * 0.07)), CGSize(w * 0.015, h * 0.08))
    }
}

private func drawNight(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    sky(d, Color(argb: 0xFF1A237E), Color(argb: 0xFF3F51B5))
    for (fx, fy) in [(0.08, 0.15), (0.3, 0.08), (0.55, 0.2), (0.86, 0.12), (0.7, 0.5), (0.16, 0.55), (0.45, 0.6), (0.92, 0.6)] as [(CGFloat, CGFloat)] {
        d.drawCircle(Color.white.copy(alpha: 0.7), h * 0.008, CGPoint(w * fx, h * fy))
    }
    hill(d, Color(argb: 0xFF283593), 0.82)
}

private func drawOpenSky(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    sky(d, Color(argb: 0xFF81D4FA), Color(argb: 0xFFE1F5FE))
    for (fx, fy, s) in [(0.2, 0.2, 1.0), (0.76, 0.34, 1.3), (0.44, 0.7, 0.8)] as [(CGFloat, CGFloat, CGFloat)] {
        let r = h * 0.07 * s
        let c = CGPoint(w * fx, h * fy)
        let cloud = Color.white.copy(alpha: 0.8)
        d.drawCircle(cloud, r, c)
        d.drawCircle(cloud, r * 0.8, c + CGPoint(-r, r * 0.3))
        d.drawCircle(cloud, r * 0.8, c + CGPoint(r, r * 0.3))
    }
    hill(d, Color(argb: 0xFF81C784), 0.88)
}
