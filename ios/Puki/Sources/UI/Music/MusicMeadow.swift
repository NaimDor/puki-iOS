import SwiftUI

enum MusicPhase { case freePlay, showPattern, playerTurn, success, sessionComplete }

struct MusicState {
    var phase = MusicPhase.freePlay
    var pattern: [Int] = []
    var playerInput: [Int] = []
    var highlightedInstrument = -1
    var roundIndex = 0
    var totalRounds = 4
    var totalStarsEarned = 0
    var patternLength = 1  // a one-note tune first: 1, 1, 2, 2
    var isReplay = false  // the same tune again, after a wrong note
    var tuneMisses = 0
    /** The instrument the tune wants now, or -1 when it isn't her turn. */
    var nextNote: Int { phase == .playerTurn ? (pattern.getOrNull(playerInput.count) ?? -1) : -1 }
}

private let longestTune = 3

@MainActor
final class MusicMeadowViewModel: ObservableObject {
    @Published private(set) var state = MusicState()
    private(set) var session: GameSession!

    func start(_ app: AppModel) {
        guard session == nil else { return }
        session = GameSession("music", app.progressRepository)
        // Tunes start one note longer at each level (tunes she can win at her age)
        state.patternLength = min(session.level(), 2)
    }

    func startSimonSays() {
        let pattern = (0..<state.patternLength).map { _ in Int.random(in: 0..<instruments.count) }
        state.phase = .showPattern; state.pattern = pattern; state.playerInput = []; state.isReplay = false; state.tuneMisses = 0
    }

    func onShowPatternDone() { state.phase = .playerTurn; state.highlightedInstrument = -1 }
    func setHighlight(_ i: Int) { state.highlightedInstrument = i }

    func onInstrumentTapped(_ index: Int) -> Bool? {
        let c = state
        if c.phase == .freePlay { return nil }
        let newInput = c.playerInput + [index]
        if newInput != Array(c.pattern.prefix(newInput.count)) {
            // Wrong: play her the same tune again; the second miss makes it as short as what she got right
            let misses = c.tuneMisses + 1
            let shorter = misses >= 2 ? Array(c.pattern.prefix(max(1, c.playerInput.count))) : c.pattern
            state.pattern = shorter; state.playerInput = []; state.phase = .showPattern; state.isReplay = true; state.tuneMisses = misses
            session.slip()
            return false
        }
        if newInput.count == c.pattern.count {
            let next = c.roundIndex + 1
            state.playerInput = newInput
            state.phase = next >= c.totalRounds ? .sessionComplete : .success
            state.totalStarsEarned += 1
            state.roundIndex = next
            state.patternLength = min(c.patternLength + (next % 2 == 0 ? 1 : 0), longestTune)
            session.star()
            if next >= c.totalRounds { session.finish(rounds: c.totalRounds) }
            return true
        }
        state.playerInput = newInput
        return nil  // still going
    }

    /** Straight on to the next tune. */
    func nextRound() { startSimonSays() }
}

/** The xylophone's notes, as playback rates of its one bar: a major pentatonic, up and back down. */
private let xyloScale: [Float] = [1, 1.122, 1.26, 1.498, 1.682, 2, 1.682, 1.498, 1.26, 1.122]

struct MusicMeadowScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gender) private var gender
    @StateObject private var vm = MusicMeadowViewModel()
    @StateObject private var help = Help()
    @StateObject private var reactions = ReactionBank()
    @State private var plays = [0, 0, 0, 0]  // how many times each has been played (each time, notes float up)
    @State private var showCard = false
    @State private var xyloNote = 0

    struct HelpKey: Hashable { let round: Int; let input: Int }

    var body: some View {
        let state = vm.state
        let compact = isCompact()
        GameBackground(scene: .stage) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: state.roundIndex,
                           totalRounds: state.phase == .freePlay ? 0 : state.totalRounds, starsEarned: state.totalStarsEarned)
                PromptBubble(text: promptText(state.phase))
                // The tune's notes, filling up as she plays them
                if state.phase == .playerTurn {
                    HStack(spacing: 10) {
                        ForEach(0..<state.pattern.count, id: \.self) { i in
                            Circle().fill(i < state.playerInput.count ? Color(argb: 0xFFFFD54F) : Color.white.opacity(0.4)).frame(width: 18, height: 18).popIn(i)
                        }
                    }
                    .padding(.top, 6)
                }
                // The instruments stand on the stage, each in its own soft pool of light
                HStack(spacing: 0) {
                    ForEach(Array(instruments.enumerated()), id: \.element.id) { index, instrument in
                        InstrumentView(instrument: instrument, index: index, highlighted: state.highlightedInstrument == index,
                                       plays: plays[index], reaction: reactions(instrument.id),
                                       hint: (help.showHint || state.tuneMisses > 0) && state.nextNote == index, compact: compact) { tapped(index) }
                            // Not all in one line: the middle two a little higher, clear of the play button
                            .offset(y: index == 0 || index == instruments.count - 1 ? 18 : -18)
                            .frame(maxWidth: .infinity)
                    }
                }
                .frame(maxHeight: .infinity)
                .padding(.horizontal, compact ? 90 : 130)  // inside the curtains
                if state.phase == .freePlay && !compact { PlayButton(side: 130) { vm.startSimonSays() } }
            }
            .padding(16)
            if state.phase == .freePlay && compact {
                VStack { Spacer(); PlayButton(side: 88) { vm.startSimonSays() }.padding(.bottom, 10) }
            }
            ConfettiOverlay(show: state.phase == .success || state.phase == .sessionComplete)
            if showCard { RoundCompleteSheet(starsEarned: state.totalStarsEarned, onContinue: onPlayAgain, onHome: onBack) }
        }
        .task {
            vm.start(app)
            app.companion.setScene("במשחק המוזיקה: יש תוף, קסילופון, תוף מרים ורעשנים.", "In the music game: there's a drum, a xylophone, a tambourine and maracas.")
            await delay(500)
            app.voice.play("voice_music_free", language)
        }
        // Her turn and quiet: "your turn", and the next note lights up
        .helpLoop(help, key: HelpKey(round: state.roundIndex, input: state.playerInput.count), active: state.phase == .playerTurn, onAssisted: { vm.session?.slip() }) {
            app.voice.play("voice_music_your_turn", language)
            let note = vm.state.nextNote
            if note >= 0 {
                Task { vm.setHighlight(note); await delay(700); vm.setHighlight(-1) }
            }
        }
        .task(id: "\(state.phase)") { await phaseChanged(state.phase) }
        .task(id: state.phase == .success ? state.roundIndex : -1) {
            guard state.phase == .success else { return }
            guard await delay(app.companion.isReady ? 3400 : 2400) else { return }
            vm.nextRound()
        }
    }

    private func promptText(_ phase: MusicPhase) -> String {
        switch phase {
        case .freePlay: return BilingualText("תלחץ על הכלים ותנגן!", "Tap the instruments and play!", heGirl: "תלחצי על הכלים ותנגני!")(language, gender)
        case .showPattern: return BilingualText("תקשיב ותזכור...", "Listen and remember...", heGirl: "תקשיבי ותזכרי...")(language, gender)
        case .playerTurn: return BilingualText("עכשיו תורך!", "Your turn!")(language)
        case .success: return BilingualText("מצוין!", "Awesome!")(language)
        case .sessionComplete: return ""
        }
    }

    private func phaseChanged(_ phase: MusicPhase) async {
        if phase == .playerTurn { app.voice.play("voice_music_your_turn", language) }
        if phase == .success || phase == .sessionComplete {
            app.companion.react("ניגנו את המנגינה בדיוק נכון!", "The child played the tune back just right!") { app.voice.playPraise(language) }
            for (i, inst) in instruments.enumerated() {
                let r = reactions(inst.id)
                Task { await delay(i * 80); await r.celebrate() }
            }
        }
        if phase == .sessionComplete {
            guard await delay(2600) else { return }
            showCard = true
        }
        if phase == .showPattern {
            // "Listen and remember!" first, then the pattern (after a wrong note, let "try again" finish)
            if vm.state.isReplay { guard await delay(1600) else { return } }
            app.voice.play("voice_music_listen", language)
            guard await delay(1800) else { return }
            for note in vm.state.pattern {
                vm.setHighlight(note)
                playInstrument(note)
                guard await delay(600) else { return }
                vm.setHighlight(-1)
                guard await delay(200) else { return }
            }
            vm.onShowPatternDone()
        }
    }

    private func playInstrument(_ index: Int) {
        plays[index] += 1
        switch instruments[index].id {
        case "drum": app.sfx.play(.drum)
        case "xylophone":
            // Up and down a happy scale, one bar at a time
            app.sfx.play(.xylophone, rate: xyloScale[xyloNote % xyloScale.count])
            xyloNote += 1
        case "tambourine": app.sfx.play(.tambourine)
        default: app.sfx.play(.maracas)
        }
    }

    private func tapped(_ index: Int) {
        playInstrument(index)
        let phase = vm.state.phase
        if phase == .playerTurn {
            let result = vm.onInstrumentTapped(index)
            help.touched()
            if result == false {
                app.sfx.play(.wrongGentle)
                app.voice.play("voice_prompt_try_again", language)
                help.missed()
                let r = reactions(instruments[index].id)
                Task { await r.shake() }
            } else if result == true {
                app.sfx.play(.success)
            }
        } else if phase == .freePlay {
            app.voice.play("voice_instrument_\(instruments[index].id)", language)
        }
    }
}

private struct InstrumentView: View {
    let instrument: InstrumentDef
    let index: Int
    let highlighted: Bool
    let plays: Int
    @ObservedObject var reaction: ReactionState
    let hint: Bool
    let compact: Bool
    let onTap: () -> Void
    @Environment(\.language) private var language
    @State private var pressed = false

    var body: some View {
        let scale: CGFloat = highlighted ? 1.2 : pressed ? 0.88 : 1
        let glow = highlighted || pressed ? 1.0 : 0.3
        VStack(spacing: 4) {
            ZStack {
                // A round glow that fades out, so there's no edge to see
                Circle().fill(RadialGradient(colors: [instrument.color.opacity(0.8 * glow), .clear], center: .center, startRadius: 0, endRadius: 110))
                    .animation(.easeInOut(duration: 0.16), value: glow)
                GeometryReader { g in
                    Pic(instrument.drawable)
                        .frame(width: g.size.width * 0.8, height: g.size.height * 0.8)
                        .frame(width: g.size.width, height: g.size.height)
                }
                .scaleEffect(scale)
                .animation(.spring(response: 0.36, dampingFraction: 0.4), value: scale)
                NoteBurst(key: plays, color: lerp(instrument.color, .white, 0.35))
            }
            .frame(maxWidth: 220)
            .aspectRatio(1, contentMode: .fit)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { _ in if !pressed { pressed = true } }
                .onEnded { _ in pressed = false; onTap() })
            if !compact {
                Text(instrument.name(language)).font(PukiFont.titleMedium).foregroundColor(PukiColors.cloudWhite).lineLimit(1).fixedSize()
            }
        }
        .hintGlow(hint)
        .reaction(reaction, jumpHeight: 24)
        .modifier(MaybeBob(on: !highlighted, index: index))
        .popIn(index)
    }
}

private struct MaybeBob: ViewModifier {
    let on: Bool
    let index: Int
    func body(content: Content) -> some View {
        if on { content.idleBob(index, amount: 0.7) } else { content }
    }
}

/** A big round play button (no reading needed), gently pulsing. */
struct PlayButton: View {
    let side: CGFloat
    let onClick: () -> Void
    @State private var pulse: CGFloat = 1
    var body: some View {
        DrawCanvas { d in
            // A play triangle, nudged right so it looks centered
            let w = d.size.width, h = d.size.height
            d.drawPath(Path { p in p.moveTo(w * 0.22, h * 0.08); p.lineTo(w * 0.98, h * 0.5); p.lineTo(w * 0.22, h * 0.92); p.close() }, .white)
        }
        .frame(width: side * 0.43, height: side * 0.43)
        .frame(width: side, height: side)
        .background(Circle().fill(RadialGradient(colors: [Color(argb: 0xFF81C784), Color(argb: 0xFF43A047)], center: .center, startRadius: 0, endRadius: side / 2)))
        .overlay(Circle().stroke(Color.white, lineWidth: 6))
        .shadow(color: .black.opacity(0.3), radius: 10, y: 4)
        .clickable(action: onClick)
        .scaleEffect(pulse)
        .onAppear { withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { pulse = 1.08 } }
    }
}

/** A couple of music notes that float up and fade each time `key` changes. */
private struct NoteBurst: View {
    let key: Int
    let color: Color
    @State private var t: CGFloat = 0
    var body: some View {
        Tweened(t) { tt in
            ZStack {
                ForEach(Array([(-0.25, "\u{266A}", 0.0), (0.27, "\u{266B}", 0.15)].enumerated()), id: \.offset) { _, note in
                    let (dx, glyph, lag) = (CGFloat(note.0), note.1, CGFloat(note.2))
                    let p = ((tt - lag) / (1 - lag)).coerceIn(0, 1)
                    Text(glyph)
                        .font(.system(size: 52))
                        .foregroundColor(color)
                        .scaleEffect(0.7 + p * 0.5)
                        .offset(x: dx * 220 + sin(p * 6) * 8, y: -40 - p * 130)
                        .opacity(key == 0 || p <= 0 ? 0 : Double(1 - p))
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: key) { _, _ in
            var tr = Transaction(animation: nil); tr.disablesAnimations = true
            withTransaction(tr) { t = 0 }
            DispatchQueue.main.async { withAnimation(.easeOut(duration: 1)) { t = 1 } }
        }
    }
}
