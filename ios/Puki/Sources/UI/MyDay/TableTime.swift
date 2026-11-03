import SwiftUI

struct PlaceSetting: Hashable { let id: String; let name: BilingualText; let icon: String; var placed = false }

/** Who's coming to eat today. */
enum Guest: CaseIterable {
    case teddy, puki, cat, rabbit
    var icon: String { self == .teddy ? "ic_tidy_teddy" : self == .puki ? "ic_companion_parrot" : self == .cat ? "ic_animal_cat" : "ic_animal_rabbit" }
}

/** What to lay: sometimes a bowl instead of the plate. */
private func todaysSetting() -> [PlaceSetting] {
    [Int.random(in: 0..<3) == 0 ? PlaceSetting(id: "bowl", name: BilingualText("קערה", "Bowl"), icon: "ic_table_bowl")
                                 : PlaceSetting(id: "plate", name: BilingualText("צלחת", "Plate"), icon: "ic_table_plate"),
     PlaceSetting(id: "cup", name: BilingualText("כוס", "Cup"), icon: "ic_table_cup"),
     PlaceSetting(id: "fork", name: BilingualText("מזלג", "Fork"), icon: "ic_table_fork"),
     PlaceSetting(id: "spoon", name: BilingualText("כפית", "Spoon"), icon: "ic_table_spoon")]
}

/** Where each thing goes on the table (fractions of the table), and how big its place is. */
private struct Place { let id: String; let x, y, w, h: CGFloat }
private let places = [
    Place(id: "plate", x: 0.50, y: 0.56, w: 0.34, h: 0.34), Place(id: "bowl", x: 0.50, y: 0.56, w: 0.34, h: 0.34),
    Place(id: "fork", x: 0.24, y: 0.56, w: 0.13, h: 0.34), Place(id: "spoon", x: 0.76, y: 0.56, w: 0.13, h: 0.34),
    Place(id: "cup", x: 0.74, y: 0.20, w: 0.16, h: 0.16),
]

/** The things in the basket: offset in points and a little tilt, so they look tumbled in. */
private let basketSpots: [(CGFloat, CGFloat, Double)] = [(40, 70, -12), (150, 40, 10), (30, 200, 18), (150, 190, -8)]

struct TableTimeScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var dd = DragDropState()
    @StateObject private var help = Help()
    @StateObject private var itemReactions = ReactionBank()
    @StateObject private var guestReaction = ReactionState()
    @State private var session: GameSession?
    @State private var items = todaysSetting()
    @State private var guest = Guest.allCases.randomElement()!
    @State private var showCard = false

    private var isComplete: Bool { items.allSatisfy(\.placed) }

    var body: some View {
        let done = items.filter(\.placed).count
        let next = items.first { !$0.placed }
        GameBackground(scene: .home) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: done, totalRounds: items.count, starsEarned: done)
                // The table seen from above, with the basket of things to set beside it
                GeometryReader { g in
                    let fit = min(1, g.size.height / 360)  // the basket is drawn for this much height
                    HStack(spacing: 0) {
                        TableScene(items: items, guest: guest, guestReaction: guestReaction, served: isComplete, hintId: help.showHint ? next?.id : nil)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        Basket(fit: fit) {
                            ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                                if !item.placed {
                                    let (dx, dy, tilt) = basketSpots[i]
                                    DragTarget(itemId: item.id, onDragEnd: { placed(item, $0) }, tapToZone: item.id) {
                                        Pic(item.icon)
                                            .frame(width: 118 * fit, height: 118 * fit)
                                            .rotationEffect(.degrees(tilt))
                                            .idleBob(i, amount: 0.5)
                                            .hintGlow(help.showHint && item.id == next?.id)
                                            .reaction(itemReactions(item.id))
                                    }
                                    .offset(x: dx * fit, y: dy * fit)
                                }
                            }
                        }
                        .frame(width: 300 * fit)
                        .frame(maxHeight: .infinity)
                    }
                    .padding(.leading, pukiCorner)
                    .frame(width: g.size.width, height: g.size.height)
                }
            }
            .padding(16)
            ConfettiOverlay(show: isComplete)
            if showCard { RoundCompleteSheet(starsEarned: done, onContinue: onPlayAgain, onHome: onBack) }
        }
        .environmentObject(dd)
        .task { session = GameSession("table", app.progressRepository) }
        .modifier(TablePrompt(guest: guest))
        .helpLoop(help, key: done, active: !isComplete) { app.voice.play("voice_prompt_set_table", language) }
        .task(id: isComplete) {
            // All set: dinner's served, the guest hops for joy, and then the end card
            guard isComplete else { return }
            let bowl = items.contains { $0.id == "bowl" }
            app.voice.play("voice_table_done", language) {
                if bowl { app.companion.react("ערכו את כל השולחן: קערה, כוס, מזלג וכף!", "The child set the whole table: bowl, cup, fork and spoon!") }
                else { app.companion.react("ערכו את כל השולחן: צלחת, כוס, מזלג וכף!", "The child set the whole table: plate, cup, fork and spoon!") }
            }
            Task { await guestReaction.celebrate() }
            guard await delay(3400) else { return }
            showCard = true
        }
    }

    private func placed(_ item: PlaceSetting, _ dropId: String?) {
        guard let dropId else { return }
        if item.id != dropId {
            session?.slip()
            app.sfx.play(.wrongGentle)
            app.voice.playSequence(["voice_table_\(item.id)", "voice_prompt_try_again"], language)
            help.missed()
            let r = itemReactions(item.id)
            Task { await r.shake() }
            return
        }
        guard let idx = items.firstIndex(where: { $0.id == item.id }) else { return }
        let doneBefore = items.filter(\.placed).count
        items[idx].placed = true
        session?.star()
        if items.allSatisfy(\.placed) { session?.finish(rounds: items.count) }
        app.sfx.play(.drop)
        app.voice.play("voice_table_\(item.id)", language)
        if doneBefore + 1 == 2 { app.companion.react("בעריכת השולחן: כבר חצי מהשולחן ערוך.", "Setting the table: half of it is set already.") }
    }
}

private struct TablePrompt: ViewModifier {
    let guest: Guest
    func body(content: Content) -> some View {
        switch guest {
        case .teddy: content.roundPrompt(0, "בעריכת השולחן: עורכים את השולחן לדובי, הוא כבר יושב ומחכה.", "Setting the table for Teddy, who's sitting there waiting.", "voice_prompt_table_teddy")
        case .puki: content.roundPrompt(0, "בעריכת השולחן: עורכים את השולחן בשבילך, אתה כבר יושב ומחכה.", "Setting the table for you: you're sitting there waiting.", "voice_prompt_table_puki")
        case .cat: content.roundPrompt(0, "בעריכת השולחן: עורכים את השולחן לחתול, הוא כבר יושב ומחכה.", "Setting the table for the cat, who's sitting there waiting.", "voice_prompt_table_cat")
        case .rabbit: content.roundPrompt(0, "בעריכת השולחן: עורכים את השולחן לארנב, הוא כבר יושב ומחכה.", "Setting the table for the rabbit, who's sitting there waiting.", "voice_prompt_table_rabbit")
        }
    }
}

/** A wooden table seen from above, with a placemat and a faint outline where each thing goes. */
private struct TableScene: View {
    let items: [PlaceSetting]
    let guest: Guest
    @ObservedObject var guestReaction: ReactionState
    let served: Bool
    let hintId: String?

    var body: some View {
        GeometryReader { g in
            // Room above the table for the guest's head; on a short screen the table gives up less
            let above = g.size.height * (isCompact() ? 0.17 : 0.2)
            let w = min(g.size.width, (g.size.height - above) * 1.35)
            let h = w / 1.35
            let guestSize = above / 0.6  // the head is the top part of the picture
            ZStack {
                // The guest sits on the far side, so the table hides the rest of them
                Pic(guest.icon).frame(width: guestSize, height: guestSize)
                    .idleBob(5, amount: 0.5)
                    .reaction(guestReaction, jumpHeight: 14)
                    .offset(y: (h + above) / -2 + guestSize / 2)
                ZStack(alignment: .topLeading) {
                    DrawCanvas { d in
                        let sw = d.size.width, sh = d.size.height
                        d.drawRoundRect(Color(argb: 0xFFB07A45), .zero, d.size, sh * 0.08)
                        d.drawRoundRect(Color(argb: 0xFFD4A26A), CGPoint(sw * 0.015, sh * 0.02), CGSize(sw * 0.97, sh * 0.96), sh * 0.07)
                        for k in 1...7 {
                            let y = sh * CGFloat(k) / 8
                            d.drawLine(Color(argb: 0xFFB07A45).copy(alpha: 0.45), CGPoint(sw * 0.03, y), CGPoint(sw * 0.97, y + sh * 0.01 * CGFloat(k % 3 - 1)), 3)
                        }
                        d.drawOval(Color(argb: 0xFFFFF3E0), CGPoint(sw * 0.12, sh * 0.22), CGSize(sw * 0.76, sh * 0.68))
                        d.drawOval(Color(argb: 0xFFFFCC80), CGPoint(sw * 0.12, sh * 0.22), CGSize(sw * 0.76, sh * 0.68), style: Stroke(width: sh * 0.012))
                    }
                    ForEach(places, id: \.id) { place in
                        if let item = items.first(where: { $0.id == place.id }) {
                            DropZone(zoneId: place.id) {
                                ZStack {
                                    if item.placed {
                                        GeometryReader { pg in Pic(item.icon).frame(width: pg.size.width * 0.95, height: pg.size.height * 0.95).frame(width: pg.size.width, height: pg.size.height) }.popIn()
                                        if served && (place.id == "plate" || place.id == "bowl") { Dinner(inBowl: place.id == "bowl").popIn() }
                                    } else {
                                        // A faint outline of what goes here
                                        GeometryReader { pg in
                                            Image(item.icon).resizable().renderingMode(.template).scaledToFit().foregroundColor(Color(argb: 0xFF5D4037)).opacity(0.16)
                                                .frame(width: pg.size.width * 0.95, height: pg.size.height * 0.95).frame(width: pg.size.width, height: pg.size.height)
                                        }
                                    }
                                }
                                .frame(width: w * place.w, height: h * place.h * 1.35)
                            }
                            .hintGlow(hintId == place.id)
                            .offset(x: w * (place.x - place.w / 2), y: h * (place.y - place.h / 2 * 1.35))
                        }
                    }
                }
                .frame(width: w, height: h, alignment: .topLeading)
                .offset(y: above / 2)
            }
            .frame(width: g.size.width, height: g.size.height)
            .environment(\.layoutDirection, .leftToRight)  // the table doesn't mirror
        }
    }
}

/** Dinner on the plate, or lying flat inside the bowl, with steam going up. */
private struct Dinner: View {
    let inBowl: Bool
    var body: some View {
        AmbientTimeline { tm in
            let rise = Ambient.loop(tm, 1.8)
            DrawCanvas { d in
                let c = CGPoint(d.size.width / 2, d.size.height * (inBowl ? 0.44 : 0.5))
                let r = d.size.minDimension * (inBowl ? 0.32 : 0.27)
                d.scale(1, inBowl ? 0.36 : 1, pivot: c) { s in
                    s.drawCircle(Color(argb: 0xFFFFD54F), r, c)
                    for k in 0..<5 {
                        let fk = CGFloat(k)
                        s.drawArc(Color(argb: 0xFFFFB300), startAngle: 20 + Double(k) * 70, sweepAngle: 170, useCenter: false, c - CGPoint(r * (0.8 - fk * 0.1), r * (0.8 - fk * 0.1)),
                                  CGSize(r * (1.6 - fk * 0.2), r * (1.6 - fk * 0.2)), style: Stroke(width: r * 0.08, cap: .round))
                    }
                    for (dx, dy) in [(-0.4, -0.2), (0.3, -0.45), (0.45, 0.25), (-0.15, 0.5)] as [(CGFloat, CGFloat)] { s.drawCircle(Color(argb: 0xFF66BB6A), r * 0.13, c + CGPoint(r * dx, r * dy)) }
                    s.drawCircle(Color(argb: 0xFFFF7043), r * 0.2, c + CGPoint(r * 0.05, r * 0.02))
                }
                for k in 0...2 {
                    let t = (rise + CGFloat(k) / 3).truncatingRemainder(dividingBy: 1)
                    let x = c.x + CGFloat(k - 1) * r * 0.7
                    let y = c.y - r * (1 + 1.3 * t)
                    let path = Path { p in p.moveTo(x, y + r * 0.45); p.cubicTo(x - r * 0.2, y + r * 0.3, x + r * 0.2, y + r * 0.15, x, y) }
                    d.drawPath(path, Color(argb: 0xFF90A4AE).copy(alpha: Double(0.8 * (1 - t))), style: Stroke(width: r * 0.09, cap: .round))
                }
            }
        }
    }
}

/** A woven basket the things are tumbled into, `fit` times its full size. */
private struct Basket<Content: View>: View {
    let fit: CGFloat
    @ViewBuilder let content: () -> Content
    var body: some View {
        ZStack(alignment: .topLeading) {
            DrawCanvas { d in
                let sw = d.size.width, sh = d.size.height
                let top = sh * 0.18
                d.drawArc(Color(argb: 0xFFA1887F), startAngle: 180, sweepAngle: 180, useCenter: false, CGPoint(sw * 0.18, top - sh * 0.14), CGSize(sw * 0.64, sh * 0.34), style: Stroke(width: sw * 0.04))
                let body = Path { p in
                    p.moveTo(sw * 0.04, top + sh * 0.04); p.lineTo(sw * 0.96, top + sh * 0.04); p.lineTo(sw * 0.86, sh * 0.98); p.lineTo(sw * 0.14, sh * 0.98); p.close()
                }
                d.drawPath(body, Color(argb: 0xFFD7A86E))
                for k in 1...6 {
                    let y = top + sh * 0.04 + (sh * 0.94 - top) * CGFloat(k) / 7
                    d.drawLine(Color(argb: 0xFFB0834F), CGPoint(sw * 0.06, y), CGPoint(sw * 0.94, y), 4)
                }
                for k in 1...7 {
                    let x = sw * CGFloat(k) / 8
                    d.drawLine(Color(argb: 0xFFB0834F).copy(alpha: 0.6), CGPoint(x, top + sh * 0.05), CGPoint(sw * 0.5 + (x - sw * 0.5) * 0.8, sh * 0.97), 3)
                }
                d.drawRoundRect(Color(argb: 0xFFA1887F), CGPoint(0, top), CGSize(sw, sh * 0.06), sh * 0.03)
            }
            content()
        }
        .frame(width: 300 * fit, height: 360 * fit)
        .environment(\.layoutDirection, .leftToRight)
    }
}
