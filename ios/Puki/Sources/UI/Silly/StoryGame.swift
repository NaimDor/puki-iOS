import SwiftUI

/** The places and things the app has pictures for. */
private let placeArt = ["farm": "ic_island_animals", "hill": "ic_word_mountain", "garden": "ic_island_counting"]
private let thingArt = ["banana": "ic_food_banana", "apple": "ic_count_apple", "ball": "ic_tidy_ball", "flower": "ic_sticker_flower",
                        "star": "ic_sticker_star", "rainbow": "ic_sticker_rainbow", "cake": "ic_myday_cake"]

/** A pick the child can make: its id, and its picture. */
private struct Pick: Hashable { let id: String; let art: String }

/**
 * The story with choices: Puki tells a little animal's story, a sentence at a time, written by his brain; the
 * child picks where it goes, who it meets and what it finds, and the next sentence goes there. Five steps, then
 * the hero goes home to sleep. Only with the brain: a door in Puki's corner.
 */
struct StoryGameScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.gamePaused) private var paused
    private static let animals = Dictionary(allAnimals.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
    @State private var hero = StoryGameScreen.animals[StoryContent.heroes.filter { StoryGameScreen.animals[$0] != nil }.randomElement()!]!
    @State private var step = 0
    @State private var sentence = ""
    @State private var picture: String?
    @State private var choices: [Pick] = []
    @State private var tap: Deferred<String>?
    @State private var done = false

    var body: some View {
        GameBackground(scene: .treehouse) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: step, totalRounds: StoryContent.steps, starsEarned: nil)
                GeometryReader { g in
                    let pictureSize = min(220, g.size.height * 0.38)
                    let bubbleSize = min(140, g.size.height * 0.26)
                    VStack(spacing: 0) {
                        let art = picture ?? hero.drawable
                        Pic(art).frame(width: pictureSize, height: pictureSize).idleBob(amount: 0.5).popIn(key: art).id(art)
                        Spacer().frame(height: 12)
                        // What Puki said, for the grown-up reading along
                        if !sentence.isEmpty {
                            Text(sentence).font(PukiFont.titleLarge).foregroundColor(PukiColors.earthBrown).multilineTextAlignment(.center)
                                .padding(.horizontal, 20).padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.85)))
                        }
                        Spacer().frame(height: 16)
                        HStack(spacing: 48) {
                            ForEach(choices, id: \.self) { c in
                                Pic(c.art).frame(width: bubbleSize * 0.7, height: bubbleSize * 0.7)
                                    .frame(width: bubbleSize, height: bubbleSize)
                                    .background(Circle().fill(Color.white).shadow(color: .black.opacity(0.25), radius: 8, y: 3))
                                    .contentShape(Circle())
                                    .onTapGesture { tap?.complete(c.id) }
                            }
                        }
                        .frame(height: bubbleSize)
                    }
                    .frame(width: g.size.width, height: g.size.height)
                }
            }
            .padding(16)
            if done { RoundCompleteSheet(starsEarned: 1, onContinue: onPlayAgain, onHome: onBack, calm: true) }
        }
        .task { await run() }
    }

    private func run() async {
        let companion = app.companion
        let english = language == .english
        let animals = Self.animals
        let session = GameSession("story", app.progressRepository)
        guard await companion.awaitReady() else { onBack(); return }
        companion.setScene("בסיפור עם פוקי: סיפור על \(hero.name.he).", "In the story with Puki: a little \(hero.name.en.lowercased()) goes for a walk.")
        var soFar: [String] = []
        var offered: [Int: [String]] = [:]  // (what she didn't pick mustn't turn up in the story)
        var place = placeArt.keys.randomElement()!
        var friend = animals[StoryContent.friends.filter { $0 != hero.id && animals[$0] != nil }.randomElement()!]!
        var thing = thingArt.keys.randomElement()!
        for s in 0..<StoryContent.steps {
            if Task.isCancelled { return }
            step = s
            await awaitUnpaused { paused }
            // The child picks where it goes, who it meets, what it finds
            let options: [Pick]
            switch s {
            case 1: options = placeArt.keys.shuffled().prefix(2).map { Pick(id: $0, art: placeArt[$0]!) }
            case 2: options = StoryContent.friends.filter { $0 != hero.id && animals[$0] != nil }.shuffled().prefix(2).map { Pick(id: $0, art: animals[$0]!.drawable) }
            case 3: options = thingArt.keys.shuffled().prefix(2).map { Pick(id: $0, art: thingArt[$0]!) }
            default: options = []
            }
            if !options.isEmpty {
                offered[s] = options.map(\.id)
                let pick = Deferred<String>()
                tap = pick
                choices = options
                // Puki asks, and asks again when she's quiet; only after a long while (not counting a pause) does he pick
                let ask = s == 1 ? (english ? "Where shall we go?" : "לאן נלך?") : s == 2 ? (english ? "Who shall we meet?" : "את מי נפגוש?") : (english ? "What shall we find?" : "מה נמצא?")
                _ = await companion.speakAndWait(ask)
                var waited = 0
                while !pick.isCompleted && waited < 40_000 {
                    guard await delay(250) else { return }
                    if !paused { waited += 250 }
                    if waited > 0 && waited % 12_000 == 0 { _ = await companion.speakAndWait(ask) }
                }
                let id = pick.isCompleted ? (await pick.wait(timeoutMs: 1) ?? options.randomElement()!.id) : options.randomElement()!.id
                tap = nil
                choices = []
                app.sfx.play(.pop)
                switch s {
                case 1: place = id; picture = placeArt[id]
                case 2: friend = animals[id]!; picture = friend.drawable
                default: thing = id; picture = thingArt[id]
                }
            }
            if s == StoryContent.steps - 1 { picture = hero.drawable }
            let plan = StoryContent.Plan(hero: hero, place: place, friend: friend, thing: thing)
            let said = await companion.line("story_next", input: StoryContent.input(plan, english: english, soFar: soFar, step: s),
                                            absent: StoryContent.notPicked(plan, english: english, step: s, offered: offered),
                                            pick: StoryContent.pickWord(plan, english: english, step: s) ?? "")
            guard let said else {
                if s == 0 { onBack(); return }  // (no story at all: nothing to finish)
                break  // (no sentence that names her pick: the story ends here, gently)
            }
            soFar.append(said)
            sentence = said
            guard await delay(700) else { return }
        }
        guard await delay(1200) else { return }
        session.star()  // (a story told to the end: its star, counted like any game's)
        session.finish(rounds: StoryContent.steps)
        done = true
    }
}
