import SwiftUI

@MainActor private var greetedThisLaunch = false

/** How long the hub waits before Puki calls out an island. */
private let idleCallMs = 30_000

struct HubScreen: View {
    let onIslandClick: (Route) -> Void
    let onSettingsClick: () -> Void
    let onStickersClick: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.appSettings) private var settings
    @Environment(\.gamePaused) private var paused
    @State private var called: String?

    var body: some View {
        let islands = { () -> [IslandDef] in
            let shown = allIslands.filter { !settings.hiddenIslands.contains($0.id) }
            return shown.isEmpty ? allIslands : shown
        }()
        ZStack {
            HubBackground()
            GeometryReader { g in
                let width = g.size.width, height = g.size.height
                // On a phone's short screen the islands would pile up: smaller there
                let screenScale = (height / 560).coerceIn(0.6, 1)
                ZStack(alignment: .topLeading) {
                    // Dotted sea route linking the islands in order, like a treasure map
                    DrawCanvas { d in
                        let pts = islands.map { CGPoint(d.size.width * $0.fractionalX, d.size.height * $0.fractionalY + 40) }
                        guard pts.count >= 2 else { return }
                        var route = Path()
                        route.move(to: pts[0])
                        for i in 1..<pts.count {
                            let a = pts[i - 1], b = pts[i]
                            let bend: CGFloat = i % 2 == 0 ? 1 : -1
                            let mid = CGPoint((a.x + b.x) / 2, (a.y + b.y) / 2)
                            let ctrl = mid + CGPoint(-(b.y - a.y), b.x - a.x) * (0.18 * bend)
                            route.addQuadCurve(to: b, control: ctrl)
                        }
                        d.ctx.stroke(route, with: .color(Color.white.opacity(0.75)),
                                     style: StrokeStyle(lineWidth: 7, lineCap: .round, dash: [1, 22]))
                    }
                    // The map is the same in both languages: absolute positions. Nearer the horizon, smaller
                    ForEach(islands, id: \.id) { island in
                        let index = allIslands.firstIndex(of: island) ?? 0
                        let depth = (0.82 + 0.35 * ((island.fractionalY - 0.42) / 0.38).coerceIn(0, 1)) * screenScale
                        IslandButton(
                            island: island,
                            onTap: {
                                app.sfx.play(.pop)
                                app.voice.stop()  // (her tap comes before Puki's hello)
                                app.voice.play("voice_island_\(island.id)", language)
                            },
                            onClick: { onIslandClick(island.route) },
                            palmOnLeft: index % 2 == 0,
                            called: called == island.id
                        )
                        .scaleEffect(depth)
                        .position(x: width * island.fractionalX, y: height * island.fractionalY)
                    }
                    // Puki on the far hill: he says hello with her, and a tap makes him talk
                    let pukiSize = 110 * screenScale
                    OfflineParrot(size: pukiSize)
                        .position(x: width * 0.095 + pukiSize / 2, y: height * 0.305 - pukiSize / 2)
                }
                .frame(width: width, height: height)
                .environment(\.layoutDirection, .leftToRight)
            }
            VStack {
                LiveStarCounter(progress: app.progressRepository, onClick: onStickersClick).testID("starCounter").padding(.top, 16)
                Spacer()
            }
            // Settings, top end: a 2-second hold so the child doesn't open it by accident
            VStack {
                HStack { Spacer(); CornerButton(icon: "ic_ui_settings", onHold: onSettingsClick) }
                Spacer()
                // Free canvas, bottom end: a big bright button, this one's for her
                HStack { Spacer(); CornerButton(icon: "ic_ui_canvas", onClick: { onIslandClick(.canvas) }) }
            }
            SparkleLayer()
        }
        .task {
            // Say hello (by name, if the personal lines exist) the first time the hub opens
            if !greetedThisLaunch {
                greetedThisLaunch = true
                await delay(600)
                app.voice.play(app.voice.personalKey("voice_hello", language), language)
            }
        }
        .task(id: islands.map(\.id)) {
            // Left a while with nothing touched, Puki calls out an island, which hops (a few times, no more)
            for _ in 0..<3 {
                guard await delay(idleCallMs) else { return }
                await awaitUnpaused { paused }
                if !app.voice.isPlaying(), let island = islands.randomElement() {
                    called = island.id
                    app.voice.play("voice_island_\(island.id)", language)
                    await delay(1500)
                    called = nil
                }
            }
        }
    }
}

/**
 * A round corner button: big and bright for her. With `onHold` it's for grown-ups, small and faded: it needs a
 * 2-second hold (a ring fills up around it), so a toddler's taps do nothing.
 */
private struct CornerButton: View {
    let icon: String
    var onClick: () -> Void = {}
    var onHold: (() -> Void)? = nil
    var body: some View {
        let grown = onHold != nil
        let face = Pic(icon)
            .frame(width: grown ? 30 : 72, height: grown ? 30 : 72)
            .frame(width: grown ? 52 : 100, height: grown ? 52 : 100)
            .background(Circle().fill(grown ? Color(argb: 0xFFCFD8DC) : Color(argb: 0xFFFFE082)).shadow(color: .black.opacity(0.25), radius: grown ? 2 : 8, y: 3))
        if let onHold {
            // The gap between the gate and the button leaves room for the ring to show
            LongPressGate(onUnlocked: onHold) { face.opacity(0.6).padding(6) }.testID(icon).padding(10)
        } else {
            face.clickable(action: onClick).testID(icon).idleBob().padding(12)
        }
    }
}
