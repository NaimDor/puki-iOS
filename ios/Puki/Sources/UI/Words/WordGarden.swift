import SwiftUI

/** Toy-block colors for the letters. */
private let letterBlocks = [Color(argb: 0xFFEF5350), Color(argb: 0xFF42A5F5), Color(argb: 0xFF66BB6A), Color(argb: 0xFFFFA726), Color(argb: 0xFFAB47BC)]
/** The ink of a letter on a block: dark, so it reads on the pale face. */
let letterInk = Color(argb: 0xFF4E342E)
private let wood = Color(argb: 0xFFC89B6C)
private let woodDark = Color(argb: 0xFF8D6240)
private let paper = Color(argb: 0xFFFFFDF5)

struct WordState {
    var targetLetter = hebrewLetters[0]
    var letterChoices: [LetterDef] = []
    var roundIndex = 0
    var totalRounds = 6
    var showSuccess = false
    var sessionComplete = false
    var totalStarsEarned = 0
}

/** Level 1 stays with the first letters of the alphabet. */
private let familiarLetters = 6

@MainActor
final class WordGardenViewModel: ObservableObject {
    @Published private(set) var state = WordState()
    private(set) var session: GameSession!
    private var letters: [LetterDef] = []
    private var rounds: [LetterDef] = []
    private var choices = 2  // blocks to pick from: 2, then 3, then 4
    private var lookalikes = false  // the top level puts letters that look alike side by side

    func initialize(_ app: AppModel, _ language: Language) {
        if session == nil { session = GameSession("words", app.progressRepository) }
        let alphabet = language == .hebrew ? hebrewLetters : englishLetters
        let level = session.level()
        choices = level + 1
        lookalikes = level >= 3
        letters = level == 1 ? Array(alphabet.prefix(familiarLetters)) : alphabet
        // A short session of letters from anywhere in the alphabet, not always from א
        rounds = Array(letters.shuffled().prefix(4))
        startRound(0)
    }

    private func startRound(_ index: Int) {
        if index >= rounds.count {
            state.sessionComplete = true
            session.finish(rounds: rounds.count)
            return
        }
        let target = rounds[index]
        let twin = lookalikes ? letters.first { $0.id == letterLookalikes[target.id] } : nil
        let choicesNow: [LetterDef]
        if let twin {
            choicesNow = (pickChoices(target, letters.filter { $0 != twin }, choices - 1) { $0.id } + [twin]).shuffled()
        } else {
            choicesNow = pickChoices(target, letters, choices) { $0.id }
        }
        state = WordState(targetLetter: target, letterChoices: choicesNow, roundIndex: index, totalRounds: rounds.count, totalStarsEarned: state.totalStarsEarned)
    }

    /** True if right, false if not, nil while the round is already won (a toddler's second tap). */
    func onLetterTapped(_ id: String) -> Bool? {
        if state.showSuccess || state.sessionComplete { return nil }
        let correct = id == state.targetLetter.id
        if correct {
            state.showSuccess = true
            state.totalStarsEarned += 1
            session.star()
        } else {
            session.slip()
        }
        return correct
    }

    func nextRound() { startRound(state.roundIndex + 1) }
}

struct WordGardenScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gender) private var gender
    @Environment(\.layoutDirection) private var dir
    @StateObject private var vm = WordGardenViewModel()
    @StateObject private var round = RoundFlow()
    @StateObject private var help = Help()
    @StateObject private var reactions = ReactionBank()
    @StateObject private var pictureReaction = ReactionState()
    @State private var sessionSeed = newSessionSeed()

    struct SceneKey: Hashable { let round: Int; let ready: Bool }

    var body: some View {
        let state = vm.state
        let ready = !state.letterChoices.isEmpty
        GameBackground(scene: .garden) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.totalRounds, starsEarned: state.totalStarsEarned)
                // The picture on its sign, and the blocks beside it
                GeometryReader { g in
                    let pictureSize = min(g.size.height * 0.62, 170)
                    let blockSize = min(g.size.height * 0.62, 160)
                    HStack(spacing: 0) {
                        if ready {
                            VStack(spacing: 0) {
                                WordSign(size: pictureSize) {
                                    Pic(state.targetLetter.exampleIcon)
                                        .frame(width: pictureSize * 0.82, height: pictureSize * 0.82)
                                        .clickable { tappedPicture() }
                                        .testID("tap")
                                }
                                .popIn(key: state.roundIndex)
                                Text(state.targetLetter.exampleWord(language)).font(PukiFont.titleLarge).foregroundColor(letterInk)
                                Spacer().frame(height: 8)
                                PromptBubble(text: BilingualText("תמצא את: \(state.targetLetter.hebrewLetter)", "Find: \(state.targetLetter.englishLetter)",
                                                                 heGirl: "תמצאי את: \(state.targetLetter.hebrewLetter)")(language, gender))
                            }
                            .reaction(pictureReaction, jumpHeight: 20)
                        }
                        // Letter choices
                        Scatter(seed: sessionSeed + state.roundIndex, rtl: dir == .rightToLeft) {
                            ForEach(Array(state.letterChoices.enumerated()), id: \.element.id) { index, letter in
                                letterBlock(state, letter, index, blockSize)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .id(state.roundIndex)
                    }
                    .padding(.leading, pukiCorner)
                    .frame(width: g.size.width, height: g.size.height)
                }
            }
            .padding(16)
            ConfettiOverlay(show: round.won)
            if state.sessionComplete { RoundCompleteSheet(starsEarned: state.totalStarsEarned, onContinue: onPlayAgain, onHome: onBack) }
        }
        .task(id: language) { round.companion = app.companion; vm.initialize(app, language) }
        .onChange(of: state.roundIndex) { _, r in reactions.reset(for: r) }
        .helpLoop(help, key: state.roundIndex, active: !round.won && ready, onAssisted: { vm.session?.slip() }) {
            app.voice.play("voice_letter_\(vm.state.targetLetter.id)", language)
        }
        .task(id: SceneKey(round: state.roundIndex, ready: ready)) {
            let l = state.targetLetter
            app.companion.setScene("במשחק האותיות: מחפשים את האות \(l.hebrewLetter), כמו \(l.exampleWord.he).",
                                   "In the letter game: we're looking for the letter \(l.englishLetter), like \(l.exampleWord.en.lowercased()).")
            guard ready, await delay(500) else { return }
            app.voice.play("voice_letter_\(l.id)", language)
        }
    }

    private func tappedPicture() {
        let letter = vm.state.targetLetter
        app.companion.react("במשחק האותיות לחצו על התמונה של \(letter.exampleWord.he), שמתחילה באות \(letter.hebrewLetter).",
                            "In the letter game the child tapped the picture of \(letter.exampleWord.en.lowercased()), which starts with \(letter.englishLetter).") {
            app.voice.play("voice_letter_\(letter.id)", language)
        }
    }

    private func letterBlock(_ state: WordState, _ letter: LetterDef, _ index: Int, _ blockSize: CGFloat) -> some View {
        let block = letterBlocks[index % letterBlocks.count]
        let r = reactions(letter.id)
        return ZStack {
            DrawCanvas { d in drawWoodBlock(d, block) }
            Text(language == .hebrew ? letter.hebrewLetter : letter.englishLetter)
                .font(.system(size: blockSize * 0.52, weight: .bold, design: .rounded))
                .foregroundColor(letterInk)
                .padding(.bottom, blockSize * 0.06)
        }
        .frame(width: blockSize, height: blockSize)
        .rotationEffect(.degrees(scatterTilt(sessionSeed + state.roundIndex, index)))
        .clickable { tapped(letter, r) }
        .testID("choice")
        .hintGlow(help.showHint && letter.id == state.targetLetter.id)
        .reaction(r)
        .idleBob(index)
        .popAway(round.won && letter.id != state.targetLetter.id, index: index)
        .popIn(index, key: state.roundIndex)
    }

    private func tapped(_ letter: LetterDef, _ r: ReactionState) {
        let target = vm.state.targetLetter
        guard let correct = vm.onLetterTapped(letter.id) else { return }
        if correct {
            app.sfx.play(.success)
            app.companion.react("מצאו את האות \(letter.hebrewLetter), כמו \(target.exampleWord.he)!", "The child found the letter \(letter.englishLetter), like \(target.exampleWord.en.lowercased())!") {
                app.voice.playPraise(language)
            }
            Task { await r.celebrate() }
            Task { await pictureReaction.celebrate() }
            round.win(quietMs: 2000, talkingMs: 3400) { vm.nextRound() }
        } else {
            app.sfx.play(.wrongGentle)
            app.companion.react("בחרו את האות \(letter.hebrewLetter), אבל חיפשנו את \(target.hebrewLetter).",
                                "The child picked the letter \(letter.englishLetter), but we were looking for \(target.englishLetter).") {
                app.voice.playSequence(["voice_letter_\(letter.id)", "voice_prompt_try_again"], language)
            }
            help.missed()
            Task { await r.shake() }
        }
    }
}

/** A chunky toy block: colored wood with a pale face for the letter. */
func drawWoodBlock(_ d: DrawScope, _ color: Color) {
    let s = d.size.minDimension
    let r = s * 0.15
    d.drawRoundRect(Color.black.copy(alpha: 0.14), CGPoint(s * 0.02, s * 0.07), CGSize(s, s * 0.97), r)
    d.drawRoundRect(lerp(color, .black, 0.28), .zero, CGSize(s, s), r)
    d.drawRoundRect(color, .zero, CGSize(s, s * 0.93), r)
    d.drawRoundRect(lerp(color, .white, 0.86), CGPoint(s * 0.11, s * 0.09), CGSize(s * 0.78, s * 0.74), s * 0.09)
    d.drawLine(Color.white.copy(alpha: 0.5), CGPoint(s * 0.16, s * 0.045), CGPoint(s * 0.5, s * 0.045), s * 0.03, .round)
}

/** The word's picture on a wooden sign in the garden: `size` is the picture's side. */
struct WordSign<Content: View>: View {
    let size: CGFloat
    @ViewBuilder let content: () -> Content
    var body: some View {
        ZStack(alignment: .top) {
            DrawCanvas { d in
                let w = d.size.width, h = d.size.height
                let board = w
                d.drawRect(woodDark, CGPoint(w * 0.44, board * 0.9), CGSize(w * 0.12, h - board * 0.9))
                d.drawOval(Color.black.copy(alpha: 0.12), CGPoint(w * 0.3, h * 0.95), CGSize(w * 0.4, h * 0.05))
                d.drawRoundRect(woodDark, CGPoint(0, h * 0.012), CGSize(w, board), w * 0.1)
                d.drawRoundRect(wood, .zero, CGSize(w, board * 0.985), w * 0.1)
                d.drawRoundRect(paper, CGPoint(w * 0.065, w * 0.065), CGSize(w * 0.87, w * 0.85), w * 0.07)
            }
            content().frame(width: size, height: size).padding(.top, size * 0.08)
        }
        .frame(width: size * 1.16, height: size * 1.34)
    }
}
