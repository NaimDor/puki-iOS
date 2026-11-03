import SwiftUI

struct SillyState {
    var items: [SillyItem] = []
    var roundIndex = 0
    var starsEarned = 0
    var sessionComplete = false
    var item: SillyItem? { items.getOrNull(roundIndex) }
}

@MainActor
final class SillyPukiViewModel: ObservableObject {
    static let rounds = 5
    private var session: GameSession!
    @Published private(set) var state: SillyState

    init() {
        // Mixed kinds: a sound, then a color, then a count…, never the same kind twice in a row
        var byKind = Dictionary(grouping: SillyContent.playable, by: \.kind).mapValues { $0.shuffled() }
        let kinds = [SillyKind.sound, .food, .color, .shape, .count].shuffled()
        var picked: [SillyItem] = []
        for i in 0..<SillyPukiViewModel.rounds {
            let k = kinds[i % kinds.count]
            if var list = byKind[k], !list.isEmpty { picked.append(list.removeFirst()); byKind[k] = list }
        }
        state = SillyState(items: picked)
    }

    func start(_ app: AppModel) { if session == nil { session = GameSession("silly", app.progressRepository) } }

    func award() { state.starsEarned += 1; session.star() }

    func next() {
        if state.roundIndex + 1 >= state.items.count {
            session.finish(rounds: state.items.count)
            state.sessionComplete = true
        } else {
            state.roundIndex += 1
        }
    }
}

/** A choice to tap: the real answer, or the wrong one Puki said. */
private struct Choice: Hashable { let id: String; let right: Bool }

/** What Puki got wrong, found in his `line`, or another at random. */
private func wrongChoice(_ item: SillyItem, _ line: String, english: Bool) -> String {
    let others: [(String, (String, String))]
    switch item.kind {
    case .food: others = level3Foods.filter { $0.id != item.answerId }.map { ($0.id, ($0.name.he, $0.name.en.lowercased())) }
    case .color: others = level3Colors.filter { $0.id != item.answerId }.map { ($0.id, ($0.name.he, $0.name.en.lowercased())) }
    case .shape: others = level3Shapes.filter { $0.id != item.answerId }.map { ($0.id, ($0.name.he, $0.name.en.lowercased())) }
    case .count: others = (1...10).filter { "\($0)" != item.answerId }.map { ("\($0)", ("\($0)", "\($0)")) }
    case .sound: others = []
    }
    return others.first { SillyContent.saysTruth(line, english ? $0.1.1 : $0.1.0) }?.0 ?? others.randomElement()!.0
}

/** A choice's name in the game's language (what a tap on it "says"). */
private func choiceName(_ kind: SillyKind, _ id: String, english: Bool) -> String {
    func n(_ b: BilingualText?) -> String { b.map { english ? $0.en.lowercased() : $0.he } ?? "" }
    switch kind {
    case .food: return n(level3Foods.first { $0.id == id }?.name)
    case .color: return n(level3Colors.first { $0.id == id }?.name)
    case .shape: return n(level3Shapes.first { $0.id == id }?.name)
    case .count: return id
    case .sound: return ""
    }
}

private enum Tap { case right, wrong, nothing }

/**
 * Silly Puki: a picture, and Puki says something wrong about it on purpose ("the cow says meow!"), written by his
 * brain. The child puts him right, out loud or by tapping the real answer, and he laughs at himself and says it.
 * Only with the brain: the island hides the game without it.
 */
struct SillyPukiScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gamePaused) private var paused
    @StateObject private var vm = SillyPukiViewModel()
    @StateObject private var pictureReaction = ReactionState()
    @State private var talking = false
    @State private var confetti = false
    @State private var choices: [Choice] = []
    @State private var tapped: Deferred<Tap>?
    @State private var sillyLine = ""
    @State private var micAsked = false
    @State private var ready: Bool?

    struct RunKey: Hashable { let round: Int; let asked: Bool; let ready: Bool? }

    var body: some View {
        let state = vm.state
        GameBackground(withCompanion: false, scene: .playground) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.items.count, starsEarned: state.starsEarned)
                GeometryReader { g in
                    let pictureSize = min(300, g.size.height - 110)
                    let hostSize = min(240, g.size.height)
                    CompanionState(companion: app.companion) { c in
                        HStack(spacing: 40) {
                            Host(talking: talking || c.speaking, listening: c.listening, size: hostSize)
                            if let item = state.item {
                                VStack(spacing: 14) {
                                    SillyPictureView(picture: item.picture, size: pictureSize * 0.74)
                                        .frame(width: pictureSize, height: pictureSize)
                                        .background(RoundedRectangle(cornerRadius: 40).fill(Color.white).shadow(color: .black.opacity(0.25), radius: 10, y: 4))
                                        .overlay(RoundedRectangle(cornerRadius: 40).stroke(Color(argb: 0xFFFFEB3B), lineWidth: c.listening ? 8 : 0))
                                        .contentShape(Rectangle())
                                        .onTapGesture {
                                            if !sillyLine.isEmpty && !c.speaking && !c.listening { Task { _ = await app.companion.speakAndWait(sillyLine) } }
                                        }
                                        .reaction(pictureReaction)
                                        .idleBob(amount: 0.5)
                                        .popIn(key: state.roundIndex)
                                    HStack(spacing: 28) {
                                        ForEach(choices, id: \.self) { ch in
                                            ChoiceBubble(kind: item.kind, id: ch.id) {
                                                app.sfx.play(.pop)
                                                tapped?.complete(ch.right ? .right : .wrong)
                                            }
                                        }
                                    }
                                    .frame(height: 96)
                                }
                            }
                        }
                        .frame(width: g.size.width, height: g.size.height)
                    }
                }
            }
            .padding(16)
            ConfettiOverlay(show: confetti)
            if state.sessionComplete { RoundCompleteSheet(starsEarned: state.starsEarned, onContinue: onPlayAgain, onHome: onBack) }
        }
        .task {
            vm.start(app)
            if !hasMicPermission() { requestMicPermission { _ in micAsked = true } } else { micAsked = true }
            // His brain, once, before the first round (it may still be waking)
            ready = await app.companion.awaitReady()
            if ready != true { onBack() }
        }
        .task(id: RunKey(round: state.roundIndex, asked: micAsked, ready: ready)) { await runRound() }
        .onChange(of: state.sessionComplete) { _, done in
            if done { app.companion.react("שיחקו בפוקי המבולבל ותיקנו אותו!", "The child played Silly Puki and put him right!") { app.voice.playPraise(language) } }
        }
    }

    private func say(_ key: String) async {
        await awaitUnpaused { paused }
        talking = true
        await app.voice.say(key, language)
        talking = false
    }

    private func runRound() async {
        guard let item = vm.state.item, micAsked, ready == true else { return }
        let english = language == .english
        let companion = app.companion
        let truth = english ? item.truthEn : item.truthHe
        guard await delay(600) else { return }
        if vm.state.roundIndex == 0 {
            _ = await companion.speakAndWait(english ? "Let's play! I'll say something silly, and you fix me!" : "בואו נשחק! אני אגיד משהו מבולבל, ואתם תתקנו אותי!")
        }
        sillyLine = ""
        if let k = item.nameKey { await say(k) }
        await awaitUnpaused { paused }
        guard let silly = await companion.line("silly", input: english ? item.en : item.he, truth: truth, mustSay: false) else {
            // No silly line this time: just the real thing, and on
            Task { await pictureReaction.hop() }
            await say(item.truthKey)
            guard await delay(700) else { return }
            vm.next()
            return
        }
        if Task.isCancelled { return }
        Task { await pictureReaction.hop() }
        sillyLine = silly

        // She puts him right: out loud, or a tap on the real answer (or on his mistake)
        let wrong = item.kind == .sound ? nil : wrongChoice(item, silly, english: english)
        choices = wrong.map { [Choice(id: item.answerId, right: true), Choice(id: $0, right: false)].shuffled() } ?? []
        let tap = Deferred<Tap>()
        tapped = tap
        let forms = item.kind == .count ? [truth] + numberWords(Int(item.truthEn) ?? 1, english: english) : [truth]
        var heard = ""
        let hasChoices = !choices.isEmpty
        let listen = Task { @MainActor in
            let h = await companion.listenForAnswer(forms)
            heard = h ?? ""
            if let h, !h.trimmed().isEmpty {
                tap.complete(SpokenAnswer.pick(h, [forms]) == 0 ? .right : .wrong)
            } else if !hasChoices {
                if h == nil { await delay(3000) }
                tap.complete(.nothing)  // (a sound to say, no bubbles: done listening)
            }
        }
        let result = await tap.wait(timeoutMs: 12_000)  // (toddlers take their time)
        listen.cancel()
        tapped = nil
        choices = []
        if Task.isCancelled { return }
        let answer: SillyContent.Answer = result == .right ? .right : (result == .wrong || !heard.trimmed().isEmpty) ? .other : .nothing
        // A tap on his mistake says what she picked, as if she'd said it
        if result == .wrong && heard.trimmed().isEmpty, let wrong { heard = choiceName(item.kind, wrong, english: english) }
        if answer == .right {
            app.sfx.play(.success)
            confetti = true
            vm.award()
            Task { await pictureReaction.celebrate() }
        }
        await awaitUnpaused { paused }
        let fixed = await companion.line("silly_fix", input: SillyContent.fixInput(english: english, line: silly, truth: truth, answer: answer, heard: heard),
                                         truth: truth, mustSay: true)
        // The real thing: the cow's own moo, the color's name, the number
        if fixed == nil || item.kind == .sound || item.kind == .count {
            Task { await pictureReaction.hop() }
            await say(item.truthKey)
        }
        guard await delay(900) else { return }
        confetti = false
        vm.next()
    }
}

/** Follows the companion's state (speaking, listening) for the views that show it. */
struct CompanionState<Content: View>: View {
    @ObservedObject var companion: Companion
    @ViewBuilder let content: (Companion) -> Content
    var body: some View { content(companion) }
}

/** The round's picture: the animal, the thing, the shape, or the things to count. */
struct SillyPictureView: View {
    let picture: SillyPicture?
    let size: CGFloat
    var body: some View {
        switch picture {
        case .animal(let a)?: Pic(a.drawable).frame(width: size, height: size)
        case .thing(let res)?: Pic(res).frame(width: size, height: size)
        case .shape(let s)?: DrawCanvas { d in drawShapeFilled(d, s, d.center, d.size.minDimension * 0.42) }.frame(width: size, height: size)
        case .count(let r)?:
            let n = r.targetNumber
            let perRow = n <= 4 ? n : (n + 1) / 2
            VStack(spacing: 8) {
                ForEach(Array(stride(from: 0, to: n, by: perRow)), id: \.self) { start in
                    HStack(spacing: 8) {
                        ForEach(start..<min(start + perRow, n), id: \.self) { _ in
                            Pic(r.itemIcon).frame(width: size / CGFloat(perRow + 1), height: size / CGFloat(perRow + 1))
                        }
                    }
                }
            }
        case nil: EmptyView()
        }
    }
}

/** A choice to tap: a food, a color, a shape, or a number. */
private struct ChoiceBubble: View {
    let kind: SillyKind
    let id: String
    let onTap: () -> Void
    var body: some View {
        ZStack {
            switch kind {
            case .food: if let f = level3Foods.first(where: { $0.id == id }) { Pic(f.drawable).frame(width: 70, height: 70) }
            case .color: if let c = level3Colors.first(where: { $0.id == id }) {
                Circle().fill(c.color).frame(width: 70, height: 70).overlay(Circle().stroke(Color.black.opacity(0.12), lineWidth: 2))
            }
            case .shape: if let s = level3Shapes.first(where: { $0.id == id }) {
                DrawCanvas { d in drawShapeFilled(d, s, d.center, d.size.minDimension * 0.42) }.frame(width: 70, height: 70)
            }
            case .count: Fingers(n: Int(id) ?? 1).frame(width: 80, height: 80)
            case .sound: EmptyView()
            }
        }
        .frame(width: 96, height: 96)
        .background(Circle().fill(Color.white).shadow(color: .black.opacity(0.2), radius: 6, y: 3))
        .contentShape(Circle())
        .onTapGesture(perform: onTap)
    }
}

/** Puki hosting: bobs, talks, and glows while he listens. */
private struct Host: View {
    let talking: Bool
    let listening: Bool
    let size: CGFloat
    var body: some View {
        ZStack {
            if listening { Circle().fill(Color(argb: 0x55FFEB3B)).frame(width: size * 0.9, height: size * 0.9) }
            TalkingParrotImage(talking: talking).frame(width: size * 0.77, height: size * 0.77).idleBob(amount: talking ? 1 : 0.5)
        }
        .frame(width: size, height: size)
    }
}

/** `n` fingers held up (one hand, or two for more than five): how many, for a child who can't read numbers yet. */
struct Fingers: View {
    let n: Int
    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            ForEach(Array((n <= 5 ? [n] : [5, n - 5]).enumerated()), id: \.offset) { _, up in
                DrawCanvas { d in drawHand(d, up) }.frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

private let skin = Color(argb: 0xFFFFCC80)
private let skinLine = Color(argb: 0xFFE0A050)

/** A hand with `up` fingers raised (the thumb last), the rest folded down. */
private func drawHand(_ d: DrawScope, _ up: Int) {
    let w = d.size.width, h = d.size.height
    let palmTop = h * 0.5
    let palm = CGRect(x: w * 0.2, y: palmTop, width: w * 0.6, height: h * 0.45)
    let fingerW = palm.width / 4.4
    for i in 0..<4 {
        let raised = i < min(up, 4)
        let left = palm.minX + CGFloat(i) * palm.width / 4 + (palm.width / 4 - fingerW) / 2
        let top = raised ? h * (0.08 + (i == 0 || i == 3 ? 0.06 : 0)) : palmTop - fingerW * 0.4
        let sz = CGSize(fingerW, palmTop - top + fingerW)
        d.drawRoundRect(skin, CGPoint(left, top), sz, fingerW / 2)
        d.drawRoundRect(skinLine, CGPoint(left, top), sz, fingerW / 2, style: Stroke(width: w * 0.025))
    }
    d.drawRoundRect(skin, palm.origin, palm.size, w * 0.12)
    d.drawRoundRect(skinLine, palm.origin, palm.size, w * 0.12, style: Stroke(width: w * 0.025))
    // The thumb: out to the side when it's up (the fifth), tucked across the palm when not
    let thumbUp = up >= 5
    d.rotate(thumbUp ? -40 : 60, pivot: CGPoint(palm.maxX - fingerW * 0.3, palm.minY + palm.height * 0.55)) { r in
        let len = thumbUp ? palm.height * 0.85 : palm.height * 0.5
        let tl = CGPoint(palm.maxX - fingerW * 0.8, palm.minY + palm.height * 0.55 - len)
        r.drawRoundRect(skin, tl, CGSize(fingerW * 1.05, len), fingerW / 2)
        r.drawRoundRect(skinLine, tl, CGSize(fingerW * 1.05, len), fingerW / 2, style: Stroke(width: w * 0.025))
    }
}
