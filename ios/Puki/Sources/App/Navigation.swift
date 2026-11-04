import SwiftUI

/** All routes (the names Safety's game allowances key on). */
enum Route: String {
    case hub = "hub", animals = "animals", colors = "colors", shapes = "shapes", counting = "counting", music = "music"
    /** The music island: a door to the instruments and one to the dance. */
    case musicDoors = "music/doors"
    case dance = "dance", words = "words", say = "say"
    /** The parrot island: Puki's corner with his brain on, else Say it with Puki directly. */
    case corner = "corner"
    case silly = "silly", care = "care", story = "story", peekaboo = "peekaboo", echo = "echo"
    case myDay = "myday", myDayBath = "myday/bath", myDayBrush = "myday/brush", myDayTidy = "myday/tidy"
    case myDayDress = "myday/dress", myDayTable = "myday/table", myDayBedtime = "myday/bedtime"
    case myDayKitchen = "myday/kitchen", myDayGarage = "myday/garage"
    case canvas = "canvas", stickers = "stickers", settings = "settings"
}

/** The My Day rooms in the order a day goes. */
private let dayOrder: [Route] = [.myDayDress, .myDayBrush, .myDayTable, .myDayTidy, .myDayKitchen, .myDayGarage, .myDayBath, .myDayBedtime]

private let navWindowMs: Int64 = 400

/** The back stack: one live screen at a time, a plain cut between them (as on the tablet). */
@MainActor
final class Router: ObservableObject {
    struct Entry: Identifiable, Equatable { let id = UUID(); let route: Route }
    @Published private(set) var stack: [Entry] = [Entry(route: .hub)]
    private var lastNavAt: Int64 = 0

    init() {
        #if DEBUG
        // Screenshot mode (debug builds only): `-PukiScreenshot animals` opens that screen straight away
        if let r = UserDefaults.standard.string(forKey: "PukiScreenshot").flatMap(Route.init(rawValue:)), r != .hub {
            stack = [Entry(route: .hub), Entry(route: r)]
        }
        #endif
    }
    var current: Route { stack.last!.route }

    /** One navigation at a time: a toddler's double tap inside this window is ignored. */
    private func settled() -> Bool {
        let now = uptimeMs()
        if now - lastNavAt >= navWindowMs { lastNavAt = now; return true }
        return false
    }

    func back(_ sfx: SfxPlayer) {
        if current != .hub && settled() {
            sfx.play(.pop)
            stack.removeLast()
        }
    }

    func navigate(_ route: Route, _ sfx: SfxPlayer) {
        if current != route && settled() {
            sfx.play(.whoosh, volume: 0.4)
            stack.append(Entry(route: route))
        }
    }

    /** "Continue" on a game's end card: the same game again, as a fresh screen (so a fresh game). */
    func playAgain(_ route: Route) {
        if current == route { stack[stack.count - 1] = Entry(route: route) }
    }

    /** "Continue" in a My Day room: on through the day, and after bedtime back to the house. */
    func nextRoom(_ route: Route) {
        guard current == route else { return }
        if let i = dayOrder.firstIndex(of: route), i + 1 < dayOrder.count {
            stack[stack.count - 1] = Entry(route: dayOrder[i + 1])
        } else {
            stack.removeLast()
        }
    }
}

struct NavRoot: View {
    @EnvironmentObject private var app: AppModel
    @StateObject private var router = Router()

    var body: some View {
        let entry = router.stack.last!
        screen(entry.route)
            .id(entry.id)
            .environmentObject(router)
            .environment(\.openAlbum, { router.navigate(.stickers, app.sfx) })
            .onChange(of: entry) { _, e in app.companion.game = e.route.rawValue }  // (safety word lists allow a game its own words)
            .onAppear { app.companion.game = entry.route.rawValue }
    }

    @ViewBuilder private func screen(_ route: Route) -> some View {
        let back = { router.back(app.sfx) }
        let go: (Route) -> Void = { router.navigate($0, app.sfx) }
        let again = { router.playAgain(route) }
        let next = { router.nextRoom(route) }
        switch route {
        case .hub: HubScreen(onIslandClick: go, onSettingsClick: { go(.settings) }, onStickersClick: { go(.stickers) })
        case .animals: AnimalGardenScreen(onBack: back, onPlayAgain: again)
        case .colors: ColorSplashScreen(onBack: back, onPlayAgain: again)
        case .shapes: ShapePlaygroundScreen(onBack: back, onPlayAgain: again)
        case .counting: CountingGardenScreen(onBack: back, onPlayAgain: again)
        case .music: MusicMeadowScreen(onBack: back, onPlayAgain: again)
        case .musicDoors:
            DoorChooserScreen(onBack: back, scene: .stage, doors: [
                imageDoor(BilingualText("כלי נגינה", "Instruments"), Color(argb: 0xFFFFB74D), "ic_island_music") { go(.music) },
                Door(label: BilingualText("ריקוד", "Dance"), color: Color(argb: 0xFF4A148C), onTap: { go(.dance) },
                     picture: AnyView(SpinningDiscoBall().frame(width: 170, height: 170))),
            ])
        case .dance: DanceScreen(onBack: back, onPlayAgain: again)
        case .words: WordGardenScreen(onBack: back, onPlayAgain: again)
        case .say: SayItScreen(onBack: back, onPlayAgain: again)
        case .corner: CornerScreen(onBack: back, go: go, again: { router.playAgain(.corner) })
        case .silly: SillyPukiScreen(onBack: back, onPlayAgain: again)
        case .care: CareGameScreen(onBack: back, onPlayAgain: again)
        case .story: StoryGameScreen(onBack: back, onPlayAgain: again)
        case .peekaboo: PeekabooScreen(onBack: back, onPlayAgain: again)
        case .echo: EchoScreen(onBack: back)
        case .myDay: MyDayScreen(onRoomClick: go, onBack: back)
        case .myDayBath: BathTimeScreen(onBack: back, onPlayAgain: next)
        case .myDayBrush: BrushyBrushyScreen(onBack: back, onPlayAgain: next)
        case .myDayTidy: TidyUpScreen(onBack: back, onPlayAgain: next)
        case .myDayDress: GettingDressedScreen(onBack: back, onPlayAgain: next)
        case .myDayTable: TableTimeScreen(onBack: back, onPlayAgain: next)
        case .myDayKitchen: CakeScreen(onBack: back, onPlayAgain: next)
        case .myDayGarage: CarWashScreen(onBack: back, onPlayAgain: next)
        case .myDayBedtime: BedtimeRoutineScreen(onBack: back, onPlayAgain: next)
        case .canvas: FreeCanvasScreen(onBack: back)
        case .stickers: StickerAlbumScreen(onBack: back)
        case .settings: SettingsScreen(onBack: back)
        }
    }
}

/** Puki's corner: his brain games need his brain and a reader for the game's language, else Say it with Puki. */
private struct CornerScreen: View {
    let onBack: () -> Void
    let go: (Route) -> Void
    let again: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language

    var body: some View {
        let voices = language == .english ? app.deviceModels.hasEnglish() : app.deviceModels.hasHebrew()
        if app.companion.brain == nil || !voices {
            SayItScreen(onBack: onBack, onPlayAgain: again)
        } else {
            DoorChooserScreen(onBack: onBack, scene: .playground, doors: [
                imageDoor(BilingualText("מדברים עם פוקי", "Say it with Puki"), Color(argb: 0xFF81D4FA), "ic_companion_parrot_talk") { go(.say) },
                imageDoor(BilingualText("פוקי המבולבל", "Silly Puki"), Color(argb: 0xFFFFCC80), "ic_companion_parrot", badge: "?!") { go(.silly) },
                imageDoor(BilingualText("עוזרים לחברים", "Helping friends"), Color(argb: 0xFFF8BBD0), "ic_tidy_teddy") { go(.care) },
                imageDoor(BilingualText("סיפור עם פוקי", "A story with Puki"), Color(argb: 0xFFC5E1A5), "ic_tidy_storybook") { go(.story) },
            ])
        }
    }
}
