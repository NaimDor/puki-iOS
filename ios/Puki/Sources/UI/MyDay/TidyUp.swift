import SwiftUI

struct TidyItem: Hashable { let id: String; let name: BilingualText; let icon: String; let homeId: String; var tidied = false }
struct TidyHome: Hashable { let id: String; let name: BilingualText; let icon: String }

/** Everything that can end up on the floor, each with its home in the room. */
private let allToys = [
    TidyItem(id: "teddy", name: BilingualText("דובי", "Teddy"), icon: "ic_tidy_teddy", homeId: "shelf"),
    TidyItem(id: "bunny", name: BilingualText("ארנב", "Bunny"), icon: "ic_tidy_bunny", homeId: "shelf"),
    TidyItem(id: "book", name: BilingualText("ספר", "Book"), icon: "ic_tidy_book", homeId: "bookcase"),
    TidyItem(id: "storybook", name: BilingualText("ספר סיפורים", "Story book"), icon: "ic_tidy_storybook", homeId: "bookcase"),
    TidyItem(id: "shirt", name: BilingualText("חולצה", "Shirt"), icon: "ic_clothing_shirt", homeId: "hamper"),
    TidyItem(id: "socks", name: BilingualText("גרביים", "Socks"), icon: "ic_clothing_socks", homeId: "hamper"),
    TidyItem(id: "pants", name: BilingualText("מכנסיים", "Pants"), icon: "ic_clothing_pants", homeId: "hamper"),
    TidyItem(id: "ball", name: BilingualText("כדור", "Ball"), icon: "ic_tidy_ball", homeId: "toybox"),
    TidyItem(id: "car", name: BilingualText("מכונית", "Car"), icon: "ic_tidy_car", homeId: "toybox"),
    TidyItem(id: "blocks", name: BilingualText("קוביות", "Blocks"), icon: "ic_tidy_blocks", homeId: "toybox"),
]

/** Today's mess: 3 or 4 toys, each with a home of its own. */
private func todaysToys() -> [TidyItem] {
    Array(Dictionary(grouping: allToys, by: \.homeId).values.shuffled().prefix(Int.random(in: 3..<5))).map { $0.randomElement()! }
}

private let homes = [
    TidyHome(id: "shelf", name: BilingualText("מדף", "Shelf"), icon: "ic_tidy_shelf"),
    TidyHome(id: "bookcase", name: BilingualText("ארון ספרים", "Bookcase"), icon: "ic_tidy_bookcase"),
    TidyHome(id: "hamper", name: BilingualText("סל כביסה", "Hamper"), icon: "ic_tidy_hamper"),
    TidyHome(id: "toybox", name: BilingualText("ארגז צעצועים", "Toy Box"), icon: "ic_tidy_toybox"),
]

/** Where a home stands in the room (fractions of the play area), its size, and where a put-away item shows inside it. */
private struct HomeSpot { let x, y, w, h, inX, inY, inSize: CGFloat }
private let homeSpots: [String: HomeSpot] = [
    "bookcase": HomeSpot(x: 0.03, y: 0.06, w: 0.20, h: 0.68, inX: 0.30, inY: 0.34, inSize: 0.40),  // standing on the floor
    "shelf": HomeSpot(x: 0.33, y: 0.02, w: 0.26, h: 0.30, inX: 0.32, inY: -0.30, inSize: 0.36),
    "toybox": HomeSpot(x: 0.56, y: 0.42, w: 0.18, h: 0.36, inX: 0.25, inY: 0.20, inSize: 0.50),
    "hamper": HomeSpot(x: 0.79, y: 0.36, w: 0.17, h: 0.42, inX: 0.22, inY: 0.30, inSize: 0.55),
]

/** Where a toy can lie on the floor (fractions of the play area) and how it's tilted. */
private let floorSpots: [(CGFloat, CGFloat, Double)] = [(0.29, 0.66, -14), (0.66, 0.74, 22), (0.46, 0.72, 9), (0.16, 0.72, 0)]

struct TidyUpScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var dd = DragDropState()
    @StateObject private var help = Help()
    @StateObject private var homeReactions = ReactionBank()
    @StateObject private var itemReactions = ReactionBank()
    @State private var session: GameSession?
    @State private var items = todaysToys()
    @State private var spots = floorSpots.shuffled()  // the toys lie somewhere else every time
    @State private var showCard = false

    private var isComplete: Bool { items.allSatisfy(\.tidied) }

    var body: some View {
        let done = items.filter(\.tidied).count
        let next = items.first { !$0.tidied }
        GameBackground(scene: .home) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: done, totalRounds: items.count, starsEarned: done)
                // The room: homes where they belong, toys scattered on the floor
                GeometryReader { g in
                    let w = g.size.width, h = g.size.height
                    let toySize = min(130, h * 0.27)
                    ZStack(alignment: .topLeading) {
                        ForEach(homes, id: \.id) { home in
                            let spot = homeSpots[home.id]!
                            let inside = items.first { $0.homeId == home.id && $0.tidied }
                            DropZone(zoneId: home.id) {
                                ZStack(alignment: .topLeading) {
                                    Image(home.icon).resizable().scaledToFit()
                                        .frame(width: w * spot.w, height: h * spot.h, alignment: .bottom)  // furniture stands on the floor
                                    if let inside {
                                        // Put away: it shows in its home
                                        Pic(inside.icon)
                                            .frame(width: w * spot.w * spot.inSize, height: w * spot.w * spot.inSize)
                                            .popIn(key: inside.id)
                                            .offset(x: w * spot.w * spot.inX, y: h * spot.h * spot.inY)
                                    }
                                }
                                .frame(width: w * spot.w, height: h * spot.h, alignment: .topLeading)
                                .reaction(homeReactions(home.id), jumpHeight: 14)
                            }
                            .hintGlow(help.showHint && next?.homeId == home.id)
                            .offset(x: w * spot.x, y: h * spot.y)
                        }
                        ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                            if !item.tidied {
                                let spot = spots[i]
                                DragTarget(itemId: item.id, onDragEnd: { dropped(item, $0) }, tapToZone: item.homeId) {
                                    Pic(item.icon)
                                        .frame(width: toySize, height: toySize)
                                        .rotationEffect(.degrees(spot.2))
                                        .idleBob(i, amount: 0.5)
                                        .hintGlow(help.showHint && item.id == next?.id)
                                        .reaction(itemReactions(item.id))
                                }
                                .offset(x: w * spot.0, y: h * spot.1)
                            }
                        }
                    }
                    .frame(width: w, height: h, alignment: .topLeading)
                    .environment(\.layoutDirection, .leftToRight)
                }
            }
            .padding(16)
            ConfettiOverlay(show: isComplete)
            if showCard { RoundCompleteSheet(starsEarned: done, onContinue: onPlayAgain, onHome: onBack) }
        }
        .environmentObject(dd)
        .task { session = GameSession("tidy", app.progressRepository) }
        .roundPrompt(0, "בסידור החדר: מחזירים כל צעצוע למקום שלו.", "Tidying up: putting every toy back where it belongs.", "voice_prompt_tidy_up")
        .helpLoop(help, key: done, active: !isComplete) { app.voice.play("voice_prompt_tidy_up", language) }
        .task(id: isComplete) {
            // All put away: every home hops, and then the end card
            guard isComplete else { return }
            app.voice.play("voice_tidy_done", language) {
                app.companion.react("סידרו את כל החדר, כל צעצוע במקום!", "The child tidied the whole room, every toy in its place!")
            }
            for (i, home) in homes.enumerated() {
                let r = homeReactions(home.id)
                Task { await delay(i * 120); await r.celebrate() }
            }
            guard await delay(3200) else { return }
            showCard = true
        }
    }

    private func dropped(_ item: TidyItem, _ dropId: String?) {
        guard let dropId, let idx = items.firstIndex(where: { $0.id == item.id }), !items[idx].tidied else { return }
        if item.homeId != dropId {
            session?.slip()
            app.sfx.play(.wrongGentle)
            app.voice.playSequence(["voice_tidy_\(item.id)", "voice_prompt_try_again"], language)
            help.missed()
            let r = itemReactions(item.id)
            Task { await r.shake() }
            return
        }
        let doneBefore = items.filter(\.tidied).count
        items[idx].tidied = true
        session?.star()
        if items.allSatisfy(\.tidied) { session?.finish(rounds: items.count) }
        app.sfx.play(.drop)
        app.voice.play("voice_tidy_\(item.id)", language)
        let r = homeReactions(item.homeId)
        Task { await r.celebrate() }
        if doneBefore + 1 == 2 && items.count == 4 {
            app.companion.react("בסידור החדר: חצי מהצעצועים כבר במקום.", "Tidying up: half the toys are back in their places.")
        }
    }
}
