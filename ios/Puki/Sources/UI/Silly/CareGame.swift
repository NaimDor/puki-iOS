import SwiftUI

/** The patients and gifts the app has pictures for. */
private let patientArt = ["puki": "ic_companion_parrot", "teddy": "ic_tidy_teddy", "bunny": "ic_animal_rabbit"]
private let giftArt = ["hat": "ic_clothing_hat", "teddy": "ic_tidy_teddy", "water": "ic_table_cup", "apple": "ic_count_apple", "ball": "ic_tidy_ball",
                       "book": "ic_tidy_book", "hug": "ic_sticker_heart", "song": "ic_ui_music_note"]

/** One round: who needs help, with what, and three gifts to choose from (at least one helps). */
struct CareRound { let patient: CareContent.Patient; let problem: CareContent.Problem; let gifts: [String] }

struct CareState {
    var rounds: [CareRound] = []
    var roundIndex = 0
    var starsEarned = 0
    var sessionComplete = false
    var round: CareRound? { rounds.getOrNull(roundIndex) }
}

@MainActor
final class CareGameViewModel: ObservableObject {
    static let rounds = 4
    private var session: GameSession!
    @Published private(set) var state = CareState()

    init() {
        let patients = CareContent.patients.filter { patientArt[$0.id] != nil }
        var rounds: [CareRound] = []
        for problem in CareContent.problems.shuffled() {
            if rounds.count >= Self.rounds { break }
            let patient = patients.filter { $0 != rounds.last?.patient }.randomElement()!  // (a different friend each round)
            let gifts = Array(giftArt.keys).sorted()
            let helpful = gifts.filter { CareContent.helps(patient, problem, $0) }
            let other = gifts.filter { !CareContent.helps(patient, problem, $0) && !(patient.id == "teddy" && $0 == "teddy") }
            if helpful.isEmpty || other.count < 2 { continue }
            rounds.append(CareRound(patient: patient, problem: problem, gifts: ([helpful.randomElement()!] + other.shuffled().prefix(2)).shuffled()))
        }
        state = CareState(rounds: rounds)
    }

    func start(_ app: AppModel) { if session == nil { session = GameSession("care", app.progressRepository) } }
    func award() { state.starsEarned += 1; session.star() }
    func next() {
        if state.roundIndex + 1 >= state.rounds.count {
            session.finish(rounds: state.rounds.count)
            state.sessionComplete = true
        } else {
            state.roundIndex += 1
        }
    }
}

/**
 * The caring game: Puki, the teddy or the bunny needs a little help, Puki's brain says what's wrong, and the child
 * gives them something. It helps, and they feel better; or it's nice but doesn't help yet, and she tries another.
 * Only with the brain: it's a door in Puki's corner.
 */
struct CareGameScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gamePaused) private var paused
    @StateObject private var vm = CareGameViewModel()
    @StateObject private var help = Help()
    @StateObject private var patientReaction = ReactionState()
    @State private var given = Set<String>()
    @State private var choosing = false
    @State private var tap: Deferred<String>?
    @State private var confetti = false
    @State private var problemLine = ""  // (what's wrong, said again when she's quiet)
    @State private var ready: Bool?

    struct RunKey: Hashable { let round: Int; let ready: Bool? }

    var body: some View {
        let state = vm.state
        let round = state.round
        GameBackground(withCompanion: round?.patient.id != "puki", scene: .home) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.rounds.count, starsEarned: state.starsEarned)
                if let round {
                    GeometryReader { g in
                        let big = min(300, g.size.height - 150)
                        VStack(spacing: 16) {
                            CompanionState(companion: app.companion) { c in
                                Group {
                                    if round.patient.id == "puki" { TalkingParrotImage(talking: c.speaking) }
                                    else { Pic(patientArt[round.patient.id]!) }
                                }
                                .frame(width: big, height: big)
                                .reaction(patientReaction)
                                .idleBob(amount: 0.5)
                                .popIn(key: state.roundIndex)
                            }
                            HStack(spacing: 28) {
                                ForEach(round.gifts, id: \.self) { gift in
                                    let gone = given.contains(gift)
                                    let helps = CareContent.helps(round.patient, round.problem, gift)
                                    Pic(giftArt[gift]!)
                                        .frame(width: 78, height: 78)
                                        .frame(width: 110, height: 110)
                                        .background(Circle().fill(Color.white).shadow(color: .black.opacity(0.2), radius: 6, y: 3))
                                        .opacity(gone ? 0.25 : 1)
                                        .hintGlow(help.showHint && helps && !gone)
                                        // (the touch outside the glow: inside its redraw a tap gesture stops taking touches)
                                        .contentShape(Circle())
                                        .onTapGesture { if choosing && !gone { tap?.complete(gift) } }
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
            ready = await app.companion.awaitReady()
            if ready != true { onBack() }
        }
        // Quiet for a while: Puki says what's wrong again; two gifts that don't help, and one that does glows
        .helpLoop(help, key: state.roundIndex, active: choosing, missesCounted: false) {
            if !problemLine.isEmpty { Task { _ = await app.companion.speakAndWait(problemLine) } }
        }
        .task(id: RunKey(round: state.roundIndex, ready: ready)) { await run() }
        .onChange(of: state.sessionComplete) { _, done in
            if done { app.companion.react("עזרו לכולם במשחק הטיפול!", "The child helped everyone in the caring game!") { app.voice.playPraise(language) } }
        }
    }

    private func run() async {
        guard let round = vm.state.round, ready == true else { return }
        let english = language == .english
        let companion = app.companion
        given = []
        problemLine = ""
        let who = round.patient.he ?? "פוקי"
        companion.setScene("במשחק הטיפול: \(CareContent.le(who)) צריך עזרה.", "In the caring game: \(round.patient.en ?? "Puki") needs help.")
        guard await delay(700) else { return }
        await awaitUnpaused { paused }
        problemLine = await companion.line("care", input: CareContent.input(english: english, patient: round.patient, problem: round.problem, item: nil)) ?? ""
        while !Task.isCancelled {
            await awaitUnpaused { paused }
            let pick = Deferred<String>()
            tap = pick
            choosing = true
            guard let gift = await pick.wait(timeoutMs: 86_400_000) else { return }
            choosing = false
            tap = nil
            given.insert(gift)
            app.sfx.play(.pop)
            Task { await patientReaction.hop() }
            let helped = CareContent.helps(round.patient, round.problem, gift)
            if !helped { help.missed() }
            if helped {
                app.sfx.play(.success)
                confetti = true
                vm.award()
                Task { await patientReaction.celebrate() }
            }
            _ = await companion.line("care", input: CareContent.input(english: english, patient: round.patient, problem: round.problem, item: gift))
            if helped || given.count >= round.gifts.count { break }
        }
        guard await delay(900) else { return }
        confetti = false
        vm.next()
    }
}
