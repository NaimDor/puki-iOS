import SwiftUI

/** Feed the animal, or find whose shadow it is (every other round). */
enum AnimalRoundKind { case feed, shadow }

struct AnimalRoundState {
    var currentAnimal = level1Animals[0]
    var kind = AnimalRoundKind.feed
    var foodChoices: [FoodDef] = []
    /** For a shadow round: the animals to choose from, the right one among them. */
    var animalChoices: [AnimalDef] = []
    var roundIndex = 0
    var totalRounds = 4
    var roundComplete = false
    var sessionComplete = false
    var totalStarsEarned = 0
    var showSuccess = false
    var isLoading = true
}

@MainActor
final class AnimalGardenViewModel: ObservableObject {
    @Published private(set) var state = AnimalRoundState()
    private(set) var session: GameSession!
    private var animals: [AnimalDef] = []
    private var pool: [AnimalDef] = []
    private var allFoods = level1Foods
    private var level = 1

    func start(_ app: AppModel) {
        guard session == nil else { return }
        session = GameSession("animals", app.progressRepository)
        level = session.level()
        // New animals join the farm ones, so old friends keep coming back
        pool = level == 1 ? level1Animals : level == 2 ? level1Animals + level2Animals : level1Animals + level2Animals + level3Animals
        animals = Array(pool.shuffled().prefix(4))
        allFoods = foodsForLevel(level)
        startRound(0)
    }

    private func startRound(_ index: Int) {
        if index >= animals.count {
            state.sessionComplete = true
            session.finish(rounds: animals.count)
            return
        }
        let animal = animals[index]
        let correct = allFoods.first { $0.id == animal.foodId }!
        let distractors = allFoods.filter { $0.id != animal.foodId }.shuffled().prefix(level == 1 ? 1 : 2)  // two foods at first
        // The second and fourth rounds: whose shadow is this? (two animals at first, then three)
        let kind: AnimalRoundKind = index % 2 == 1 ? .shadow : .feed
        let animalChoices = kind == .shadow ? ([animal] + pool.filter { $0.id != animal.id }.shuffled().prefix(level == 1 ? 1 : 2)).shuffled() : []
        state = AnimalRoundState(currentAnimal: animal, kind: kind, foodChoices: ([correct] + distractors).shuffled(), animalChoices: animalChoices,
                                 roundIndex: index, totalRounds: animals.count, totalStarsEarned: state.totalStarsEarned, isLoading: false)
    }

    /** True on the right food, false on a wrong one, nil once the round is already won. */
    func onFoodDropped(_ foodId: String) -> Bool? {
        if state.roundComplete { return nil }
        let correct = foodId == state.currentAnimal.foodId
        if correct {
            state.showSuccess = true
            state.roundComplete = true
            state.totalStarsEarned += 1
            session.star()
            DayLog.record(RecapContent.fed(state.currentAnimal))
        } else {
            session.slip()
        }
        return correct
    }

    /** A shadow round: the child put `animalId` on the shadow. True if it's the one. */
    func onAnimalDropped(_ animalId: String) -> Bool? {
        if state.roundComplete { return nil }
        let correct = animalId == state.currentAnimal.id
        if correct {
            state.showSuccess = true
            state.roundComplete = true
            state.totalStarsEarned += 1
            session.star()
        } else {
            session.slip()
        }
        return correct
    }

    func nextRound() { startRound(state.roundIndex + 1) }
}

/** Where an animal lives; the others are at home on the farm. */
private func habitatOf(_ id: String) -> Scene {
    switch id {
    case "lion", "elephant", "giraffe": return .savanna
    case "monkey": return .jungle
    case "penguin": return .ice
    default: return .farm
    }
}

/** Where the animals go in a shadow round. */
private let shadowZone = "shadow"

struct AnimalGardenScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var vm = AnimalGardenViewModel()
    @StateObject private var dd = DragDropState()
    @StateObject private var round = RoundFlow()
    @StateObject private var help = Help()
    @StateObject private var animalReaction = ReactionState()
    @State private var choiceReactions: [String: ReactionState] = [:]

    var body: some View {
        let state = vm.state
        let shadowRound = state.kind == .shadow
        GameBackground(scene: state.isLoading ? .farm : habitatOf(state.currentAnimal.id)) {
            if !state.isLoading {
                VStack(spacing: 0) {
                    GameTopBar(onBack: onBack, roundIndex: state.roundIndex, totalRounds: state.totalRounds, starsEarned: state.totalStarsEarned)
                    Spacer().frame(height: 8)
                    // The animal with its drop zone, and the food tray: under it on a tablet; beside it on a phone
                    GeometryReader { g in
                        let compact = g.size.height < 440
                        let stage = compact ? min(g.size.height - 64, 320) : 320
                        if compact {
                            HStack { animalView(state, compact: compact, stage: stage).frame(maxWidth: .infinity, maxHeight: .infinity); tray(state, compact: compact) }
                        } else {
                            VStack { animalView(state, compact: compact, stage: stage).frame(maxHeight: .infinity); tray(state, compact: compact) }
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(16)
                ConfettiOverlay(show: round.won)
                if state.sessionComplete { RoundCompleteSheet(starsEarned: state.totalStarsEarned, onContinue: onPlayAgain, onHome: onBack) }
            }
        }
        .environmentObject(dd)
        .task {
            round.companion = app.companion
            vm.start(app)
        }
        .onChange(of: state.roundIndex) { _, _ in resetReactions() }
        .onAppear { resetReactions() }
        .helpLoop(help, key: state.roundIndex, active: !state.roundComplete && !state.isLoading, onAssisted: { vm.session?.slip() }) {
            if shadowRound { app.voice.play("voice_prompt_whose_shadow", language) }
            else { app.voice.playSequence(["voice_prompt_whos_hungry", "voice_animal_\(state.currentAnimal.id)"], language) }
        }
        .modifier(AnimalPrompt(state: state))
        // "Whose shadow?" "פרה!": naming an animal puts it on the shadow
        .answerOutLoud(state.roundIndex, options: shadowRound ? state.animalChoices.map { [$0.name(language)] } : [],
                       active: shadowRound && !state.roundComplete && !state.isLoading) { i in
            if let a = state.animalChoices.getOrNull(i) { onAnimalDropped(a, shadowZone) }
        }
    }

    private func resetReactions() {
        choiceReactions = Dictionary(uniqueKeysWithValues: vm.state.animalChoices.map { ($0.id, ReactionState()) })
    }

    @ViewBuilder private func animalView(_ state: AnimalRoundState, compact: Bool, stage: CGFloat) -> some View {
        if state.kind == .shadow {
            DropZone(zoneId: shadowZone, highlightColor: PukiColors.grassGreen) {
                ShadowStage(animal: state.currentAnimal, revealed: state.roundComplete, reaction: animalReaction, size: compact ? 200 : 280)
            }
        } else {
            DropZone(zoneId: state.currentAnimal.id, highlightColor: PukiColors.grassGreen) {
                AnimalDisplay(animal: state.currentAnimal, reaction: animalReaction, stage: stage)
            }
        }
    }

    @ViewBuilder private func tray(_ state: AnimalRoundState, compact: Bool) -> some View {
        if !state.roundComplete {
            Group {
                if state.kind == .shadow {
                    HStack(spacing: 20) {
                        ForEach(Array(state.animalChoices.enumerated()), id: \.element.id) { i, animal in
                            AnimalChoice(animal: animal, onDropped: { onAnimalDropped(animal, $0) }, tapToZone: shadowZone, index: i,
                                         reaction: choiceReactions[animal.id] ?? animalReaction,
                                         hint: help.showHint && animal.id == state.currentAnimal.id, size: compact ? 130 : 165)
                        }
                    }
                } else {
                    HStack(spacing: 20) {
                        ForEach(state.foodChoices, id: \.id) { food in
                            FoodItem(food: food, onDropped: { foodDropped(food, $0) }, hint: help.showHint && food.id == state.currentAnimal.foodId,
                                     tapToZone: state.currentAnimal.id,
                                     landed: { $0 == vm.state.currentAnimal.id && food.id == vm.state.currentAnimal.foodId },
                                     plateSize: compact ? 130 : 165)
                        }
                    }
                }
            }
            .padding(.bottom, 16)
            .id(state.roundIndex)
            .transition(.scale.combined(with: .opacity))
        }
    }

    /** A shadow round: the child put `animal` on the shadow. */
    private func onAnimalDropped(_ animal: AnimalDef, _ dropTargetId: String?) {
        guard dropTargetId == shadowZone else { app.sfx.play(.wrongGentle); return }
        let target = vm.state.currentAnimal
        guard let correct = vm.onAnimalDropped(animal.id) else { return }  // (the round's already won)
        if correct {
            app.sfx.play(.success)
            Task { await animalReaction.celebrate() }
            // The shadow turns into the animal, which says its name and its sound
            app.voice.playSequence(["voice_animal_\(target.id)", "voice_sound_\(target.id)"], language) {
                app.companion.react("במשחק הצללים מצאו של מי הצל: \(target.name.he)!", "In the shadow game the child found whose shadow it was: the \(target.name.en.lowercased())!") {
                    app.voice.playPraise(language)
                }
            }
            round.win(quietMs: 3600, talkingMs: 4600) { vm.nextRound() }
        } else {
            app.sfx.play(.wrongGentle)
            help.missed()
            if let r = choiceReactions[animal.id] { Task { await r.shake() } }
            app.voice.playSequence(["voice_animal_\(animal.id)", "voice_prompt_try_again"], language)
        }
    }

    private func foodDropped(_ food: FoodDef, _ dropTargetId: String?) {
        let animal = vm.state.currentAnimal
        guard dropTargetId == animal.id else { app.sfx.play(.wrongGentle); return }
        let correct = vm.onFoodDropped(food.id)
        if correct == true {
            app.sfx.play(.success)
            app.companion.react("נתנו ל\(animal.name.he) \(food.name.he). זה האוכל הנכון!", "The child fed the \(animal.name.en.lowercased()) \(food.name.en.lowercased()). That's the right food!") {
                app.voice.playPraise(language)
            }
            Task {
                // Swallowed first, then a munch and a happy hop
                await delay(220)
                await animalReaction.munch()
                await animalReaction.celebrate()
            }
            let roundIndex = vm.state.roundIndex
            round.win(quietMs: 2400, talkingMs: 3600) {
                // Every other round, Puki asks what the animal says and listens for an answer
                if app.companion.isReady && roundIndex % 2 == 0 {
                    let says = feminineAnimals.contains(animal.id) ? "אומרת" : "אומר"
                    let question = language == .english ? "What does the \(animal.name.en.lowercased()) say?" : "מה ה\(animal.name.he) \(says)?"
                    let ctx = language == .english ? "You asked what the \(animal.name.en.lowercased()) says, and this is the answer." : "שאלת מה ה\(animal.name.he) \(says), וזו התשובה."
                    _ = await app.companion.askAndListen(question, context: ctx) {
                        app.voice.play("voice_sound_\(animal.id)", language)
                        Task { await animalReaction.celebrate() }
                    }
                }
                vm.nextRound()
            }
        } else if correct == false {
            app.sfx.play(.wrongGentle)
            // The animal shakes its head, makes its sound and says what it eats: a little fact, not a "no"
            app.companion.react("ניסו לתת ל\(animal.name.he) \(food.name.he), אבל זה לא האוכל שלו.", "The child tried to give the \(animal.name.en.lowercased()) \(food.name.en.lowercased()), but that's not its food.") {
                app.voice.playSequence(["voice_sound_\(animal.id)", "voice_eats_\(animal.id)"], language)
            }
            help.missed()
            Task { await animalReaction.shake() }
        }
    }
}

private struct AnimalPrompt: ViewModifier {
    let state: AnimalRoundState
    func body(content: Content) -> some View {
        if state.isLoading {
            content
        } else if state.kind == .shadow {
            content.roundPrompt(state.roundIndex, "במשחק החיות: מנחשים של מי הצל.", "In the animal game: guessing whose shadow this is.", "voice_prompt_whose_shadow")
        } else {
            content.roundPrompt(state.roundIndex, "במשחק החיות: \(state.currentAnimal.name.he) מחכה לאוכל.",
                                "In the animal game: the \(state.currentAnimal.name.en.lowercased()) is waiting for food.",
                                "voice_prompt_whos_hungry", "voice_animal_\(state.currentAnimal.id)")
        }
    }
}

struct AnimalDisplay: View {
    let animal: AnimalDef
    @ObservedObject var reaction: ReactionState
    var stage: CGFloat = 320
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @State private var bouncing = false

    var body: some View {
        VStack(spacing: 12) {
            ZStack {
                // A soft shadow, so it stands on the ground
                DrawCanvas { d in d.drawOval(Color.black.copy(alpha: 0.14), CGPoint(d.size.width * 0.16, d.size.height * 0.88), CGSize(d.size.width * 0.68, d.size.height * 0.08)) }
                Pic(animal.drawable)
                    .frame(width: stage * 0.875, height: stage * 0.875)
                    .idleBreathing()
                    .reaction(reaction)
            }
            .frame(width: stage, height: stage)
            .contentShape(Rectangle())
            .testID("tap")
            .onTapGesture {
                bouncing = true
                app.voice.playSequence(["voice_animal_\(animal.id)", "voice_sound_\(animal.id)"], language) { bouncing = false }
            }
            Text(animal.name(language))
                .font(PukiFont.headlineMedium)
                .foregroundColor(Color(argb: 0xFF5D4037))
                .padding(.horizontal, 24).padding(.vertical, 6)
                .background(Capsule().fill(Color.white.opacity(0.93)))
        }
        .scaleEffect(bouncing ? 1.15 : 1)
        .animation(.spring(response: 0.36, dampingFraction: 0.4), value: bouncing)
        .popIn(key: animal.id)  // each round's animal arrives
        .wiggling(!bouncing)
    }
}

private let bowlWood = Color(argb: 0xFFB98352)
private let bowlInside = Color(argb: 0xFF8D6240)
private let bowlRim = Color(argb: 0xFFD7A86E)

struct FoodItem: View {
    let food: FoodDef
    let onDropped: (String?) -> Void
    var hint = false  // wiggle and glow: this is the one
    var tapToZone: String? = nil  // a tap sends it there (the hungry animal)
    var landed: (String) -> Bool = { _ in false }  // the animal took it: it goes in, not home
    var plateSize: CGFloat = 165
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language

    var body: some View {
        DragTarget(itemId: food.id, onDragEnd: onDropped, onDragStart: { app.voice.play("voice_food_\(food.id)", language) },
                   tapToZone: tapToZone, landed: landed) {
            VStack(spacing: 2) {
                // In a feed bowl, so it reads as something to pick up
                ZStack(alignment: .top) {
                    DrawCanvas { d in
                        let w = d.size.width, h = d.size.height
                        d.drawOval(Color.black.copy(alpha: 0.14), CGPoint(w * 0.08, h * 0.84), CGSize(w * 0.84, h * 0.14))
                        d.drawOval(bowlInside, CGPoint(w * 0.06, h * 0.48), CGSize(w * 0.88, h * 0.26))
                    }
                    Pic(food.drawable).frame(width: plateSize * 0.74, height: plateSize * 0.74)
                    DrawCanvas { d in
                        let w = d.size.width, h = d.size.height
                        let front = Path { p in
                            p.moveTo(w * 0.04, h * 0.6)
                            p.quadraticBezierTo(w * 0.5, h * 0.76, w * 0.96, h * 0.6)
                            p.quadraticBezierTo(w * 0.9, h * 0.92, w * 0.72, h * 0.92)
                            p.lineTo(w * 0.28, h * 0.92)
                            p.quadraticBezierTo(w * 0.1, h * 0.92, w * 0.04, h * 0.6)
                            p.close()
                        }
                        d.drawPath(front, bowlWood)
                        d.drawArc(bowlRim, startAngle: 18, sweepAngle: 144, useCenter: false, CGPoint(w * 0.04, h * 0.46), CGSize(w * 0.92, h * 0.28),
                                  style: Stroke(width: h * 0.03, cap: .round))
                    }
                }
                .frame(width: plateSize, height: plateSize)
                .idleBob(food.id.count)
                .hintGlow(hint)
                Text(food.name(language))
                    .font(PukiFont.rounded(16))
                    .foregroundColor(Color(argb: 0xFF33691E))
                    .padding(.horizontal, 12).padding(.vertical, 2)
                    .background(Capsule().fill(Color.white.opacity(0.9)))
            }
            .padding(8)
        }
    }
}

/** The animal's shadow, and the animal itself once the child found it. */
struct ShadowStage: View {
    let animal: AnimalDef
    let revealed: Bool
    @ObservedObject var reaction: ReactionState
    var size: CGFloat = 280
    var body: some View {
        ZStack {
            Group {
                if revealed {
                    Pic(animal.drawable)
                } else {
                    // Just the outline's shape, in a soft dark grey: a shadow, not a black hole
                    Image(animal.drawable).resizable().renderingMode(.template).scaledToFit().foregroundColor(Color(argb: 0xFF455A64).opacity(0.82))
                }
            }
            .id("\(animal.id)-\(revealed)")
            .transition(.opacity.animation(.easeInOut(duration: 0.5)))
            .frame(width: size, height: size)
            .idleBreathing()
            .reaction(reaction)
        }
        .frame(width: size + 40, height: size + 40)
    }
}

/** An animal to drag onto the shadow (or tap: it flies there by itself). */
struct AnimalChoice: View {
    let animal: AnimalDef
    let onDropped: (String?) -> Void
    let tapToZone: String
    let index: Int
    @ObservedObject var reaction: ReactionState
    var hint = false
    var size: CGFloat = 165
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    var body: some View {
        DragTarget(itemId: animal.id, onDragEnd: onDropped, onDragStart: { app.voice.play("voice_animal_\(animal.id)", language) }, tapToZone: tapToZone) {
            Pic(animal.drawable)
                .frame(width: size * 0.8, height: size * 0.8)
                .frame(width: size, height: size)
                .background(Circle().fill(Color.white.opacity(0.9)).shadow(color: .black.opacity(0.2), radius: 6, y: 3))
                .overlay(Circle().stroke(Color(argb: 0xFFC8E6C9), lineWidth: 6))
                .idleBob(index)
                .hintGlow(hint)
                .reaction(reaction, jumpHeight: 24)
                .padding(8)
        }
    }
}
