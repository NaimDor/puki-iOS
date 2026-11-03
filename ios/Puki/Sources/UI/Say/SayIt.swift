import SwiftUI

struct SayState {
    var words: [SayWord] = []
    var roundIndex = 0
    var starsEarned = 0
    var sessionComplete = false
    var word: SayWord? { words.getOrNull(roundIndex) }
}

/** The words with a sound of their own (voice_sound_*). */
private let speakingAnimals: Set<String> = ["dog", "cat", "cow", "duck", "horse", "sheep", "elephant", "monkey", "lion"]

@MainActor
final class SayItViewModel: ObservableObject {
    static let rounds = 5  // short: three tries a word adds up fast at two
    static let tries = 3
    private var session: GameSession!
    @Published private(set) var state = SayState(words: Array(sayWords.shuffled().prefix(SayItViewModel.rounds)))

    func start(_ app: AppModel) { if session == nil { session = GameSession("say", app.progressRepository) } }

    func award() {
        state.starsEarned += 1
        session.star()
    }

    func next() {
        if state.roundIndex + 1 >= state.words.count {
            session.finish(rounds: state.words.count)
            state.sessionComplete = true
        } else {
            state.roundIndex += 1
        }
    }
}

/**
 * Say it with Puki: a picture appears, Puki says the word, and the child says it back. With the brain listening,
 * the word itself moves the game on: another word or a near miss gets the word again and another go, up to three
 * tries. Puki always closes by saying the word the right way. Without it, any try counts.
 */
struct SayItScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gamePaused) private var paused
    @StateObject private var vm = SayItViewModel()
    @StateObject private var pictureReaction = ReactionState()
    @State private var talking = false
    @State private var confetti = false
    @State private var micAsked = false

    struct RunKey: Hashable { let round: Int; let asked: Bool }

    var body: some View {
        let state = vm.state
        GameBackground(withCompanion: false, scene: .sunset) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.words.count, starsEarned: state.starsEarned)
                GeometryReader { g in
                    // Sized from the room there is, so the word still fits on a phone
                    let pictureSize = min(300, g.size.height - 72)
                    let hostSize = min(260, g.size.height)
                    let compact = g.size.height < 400
                    ListeningState(companion: app.companion) { listening in
                        HStack(spacing: 48) {
                            HostParrot(talking: talking, listening: listening, hostSize: hostSize)
                            if let word = state.word {
                                // The picture; tapping it says the word again
                                VStack(spacing: 8) {
                                    Pic(word.drawable)
                                        .frame(width: pictureSize * 0.74, height: pictureSize * 0.74)
                                        .frame(width: pictureSize, height: pictureSize)
                                        .background(RoundedRectangle(cornerRadius: 40).fill(Color.white).shadow(color: .black.opacity(0.25), radius: 10, y: 4))
                                        .overlay(RoundedRectangle(cornerRadius: 40).stroke(Color(argb: 0xFFFFEB3B), lineWidth: listening ? 8 : 0))
                                        .scaleEffect(listening ? 1.05 : 1)
                                        .animation(.easeInOut(duration: 0.25), value: listening)
                                        .contentShape(Rectangle())
                                        .onTapGesture { if !listening && !talking { Task { await say("voice_say_\(word.id)") } } }
                                        .reaction(pictureReaction)
                                        .idleBob(amount: 0.5)
                                        .popIn(key: state.roundIndex)
                                    // For the grown-up reading along
                                    Text(word.word(language))
                                        .font(compact ? PukiFont.titleLarge : PukiFont.rounded(36))
                                        .foregroundColor(PukiColors.earthBrown)
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
            app.companion.wake()
            // The microphone; a parent approves it once
            if !hasMicPermission() { requestMicPermission { _ in micAsked = true } } else { micAsked = true }
        }
        .task(id: RunKey(round: state.roundIndex, asked: micAsked)) { await runRound() }
        .onChange(of: state.sessionComplete) { _, done in
            // The last word's said: Puki closes the game
            if done { app.companion.react("במשחק המילים אמרו מילים עם פוקי!", "The child said words along with Puki!") { app.voice.playPraise(language) } }
        }
    }

    private func say(_ key: String) async {
        await awaitUnpaused { paused }  // (every step of a round goes through here: nothing goes on under the goodbye)
        talking = true
        await app.voice.say(key, language)
        talking = false
    }

    private func runRound() async {
        guard let word = vm.state.word, micAsked else { return }
        let roundIndex = vm.state.roundIndex
        let text = word.word(language)
        let alsoCounts = language == .english ? word.alsoCountsEn : word.alsoCountsHe
        guard await delay(500) else { return }
        if roundIndex == 0 { await say("voice_say_intro") }
        let wordKey = "voice_say_\(word.id)"
        await say(wordKey)
        var last: WordTry?
        var nearMiss = false
        var silences = 0
        for _ in 0..<SayItViewModel.tries {
            if Task.isCancelled { return }
            switch last {
            case nil: await say("voice_say_your_turn")
            case .silent?: await say("voice_say_again"); await say(wordKey)
            case .close?: await say("voice_say_almost"); await say(wordKey); await say("voice_say_your_turn")
            default: await say("voice_say_listen"); await say(wordKey); await say("voice_say_your_turn")
            }
            await awaitUnpaused { paused }
            if let heard = await app.companion.listenForWord(text, alsoCounts: alsoCounts) {
                last = heard
            } else {
                guard await delay(3500) else { return }  // no microphone: just leave time to say it
                last = .spoke
            }
            if Task.isCancelled { return }
            if last == .close { nearMiss = true }
            if last == .word || last == .spoke { break }
            if last == .silent { silences += 1; if silences >= 2 { break } }  // she's not in the mood: on to another picture
        }
        if last == .word { DayLog.record(RecapContent.said(word.word)) }  // (heard, not just a sound with no ears)
        if last == .word {
            app.sfx.play(.success)
            confetti = true
            vm.award()
            Task { await pictureReaction.celebrate() }
            talking = true
            await app.voice.praise(language)
            talking = false
        } else if last == .spoke || nearMiss {
            app.sfx.play(.pop)
            vm.award()
            Task { await pictureReaction.celebrate() }
            await say("voice_say_nice")
        }  // else no star this time, and no "no" either: Puki just says the word once more
        await say("voice_say_\(word.id)")
        // An animal answers for itself
        if speakingAnimals.contains(word.id) {
            Task { await pictureReaction.hop() }
            app.voice.play("voice_sound_\(word.id)", language)
        }
        guard await delay(900) else { return }
        confetti = false
        vm.next()
    }
}

/** Follows the companion's listening, for views that glow while Puki listens. */
struct ListeningState<Content: View>: View {
    @ObservedObject var companion: Companion
    @ViewBuilder let content: (Bool) -> Content
    var body: some View { content(companion.listening) }
}

/** Puki as the game's host: bobs, moves its beak while talking, and glows while listening. */
struct HostParrot: View {
    let talking: Bool
    let listening: Bool
    let hostSize: CGFloat
    @StateObject private var hop = ReactionState()
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30)) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let raw = (t / (talking ? 0.26 : 1.4)).truncatingRemainder(dividingBy: 2)
            let bob = CGFloat(raw < 1 ? raw : 2 - raw)
            ZStack {
                if listening {
                    // Rings, like sound coming in: "I'm listening"
                    DrawCanvas { d in
                        for k in 0...1 {
                            let tt = (CGFloat((t / 0.9).truncatingRemainder(dividingBy: 1)) + CGFloat(k) * 0.5).truncatingRemainder(dividingBy: 1)
                            d.drawCircle(Color(argb: 0xFFFFEB3B).copy(alpha: Double(0.9 * (1 - tt))), d.size.minDimension * (0.32 + 0.18 * tt), d.center, style: Stroke(width: 8))
                        }
                    }
                }
                TalkingParrotImage(talking: talking)
                    .frame(width: hostSize * 0.77, height: hostSize * 0.77)
                    .scaleEffect(listening ? 1.1 : 1)
                    .rotationEffect(.degrees(talking ? Double((bob - 0.5) * 8) : 0))
                    .offset(y: -bob * (talking ? 12 : 6))
                    .reaction(hop)
            }
        }
        .frame(width: hostSize, height: hostSize)
        .contentShape(Rectangle())
        // (a tap gets a silent hop: he's the one who's doing the talking here)
        .onTapGesture { Task { await hop.hop() } }
    }
}
