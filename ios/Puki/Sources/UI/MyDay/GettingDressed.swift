import SwiftUI

struct ClothingItem: Hashable { let id: String; let name: BilingualText; let icon: String; let order: Int }

/** What today looks like out of the window. */
enum Weather: CaseIterable { case sun, rain, pajama }

/** What goes on the feet after the socks. */
private let feet: Set<String> = ["shoes", "boots", "slippers"]

/** Today's outfit, by what's out of the window. */
private func outfitFor(_ weather: Weather) -> [ClothingItem] {
    switch weather {
    case .sun: return [
        ClothingItem(id: "shirt", name: BilingualText("חולצה", "Shirt"), icon: "ic_clothing_shirt", order: 0),
        ClothingItem(id: "shorts", name: BilingualText("מכנסיים קצרים", "Shorts"), icon: "ic_clothing_shorts", order: 1),
        ClothingItem(id: "socks", name: BilingualText("גרביים", "Socks"), icon: "ic_clothing_socks", order: 2),
        ClothingItem(id: "shoes", name: BilingualText("נעליים", "Shoes"), icon: "ic_clothing_shoes", order: 3),
        ClothingItem(id: "hat", name: BilingualText("כובע", "Hat"), icon: "ic_clothing_hat", order: 4)]
    case .rain: return [
        ClothingItem(id: "pants", name: BilingualText("מכנסיים", "Pants"), icon: "ic_clothing_pants", order: 0),
        ClothingItem(id: "raincoat", name: BilingualText("מעיל גשם", "Raincoat"), icon: "ic_clothing_raincoat", order: 1),
        ClothingItem(id: "socks", name: BilingualText("גרביים", "Socks"), icon: "ic_clothing_socks", order: 2),
        ClothingItem(id: "boots", name: BilingualText("מגפיים", "Boots"), icon: "ic_clothing_boots", order: 3)]
    case .pajama: return [
        ClothingItem(id: "pajama_top", name: BilingualText("חולצת פיג'מה", "Pajama top"), icon: "ic_clothing_pajamas", order: 0),
        ClothingItem(id: "pajama_pants", name: BilingualText("מכנסי פיג'מה", "Pajama pants"), icon: "ic_clothing_pajama_pants", order: 1),
        ClothingItem(id: "socks", name: BilingualText("גרביים", "Socks"), icon: "ic_clothing_socks", order: 2),
        ClothingItem(id: "slippers", name: BilingualText("נעלי בית", "Slippers"), icon: "ic_clothing_slippers", order: 3)]
    }
}

/** Where each piece waits in the wardrobe: offset and size in points, and a little tilt. */
private struct WardrobeSpot { let x, y, size: CGFloat; let tilt: Double }
private let wardrobeSpots: [String: WardrobeSpot] = [
    "shirt": WardrobeSpot(x: 55, y: 70, size: 140, tilt: -4), "raincoat": WardrobeSpot(x: 55, y: 70, size: 140, tilt: -4),
    "pajama_top": WardrobeSpot(x: 55, y: 70, size: 140, tilt: -4), "pants": WardrobeSpot(x: 225, y: 70, size: 140, tilt: 5),
    "shorts": WardrobeSpot(x: 225, y: 70, size: 140, tilt: 5), "pajama_pants": WardrobeSpot(x: 225, y: 70, size: 140, tilt: 5),
    "socks": WardrobeSpot(x: 150, y: 262, size: 120, tilt: 8), "hat": WardrobeSpot(x: 280, y: 245, size: 120, tilt: -6),
    "shoes": WardrobeSpot(x: 140, y: 390, size: 130, tilt: 0), "boots": WardrobeSpot(x: 140, y: 390, size: 130, tilt: 0),
    "slippers": WardrobeSpot(x: 140, y: 390, size: 130, tilt: 0),
]

struct GettingDressedScreen: View {
    let onBack: () -> Void
    let onPlayAgain: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var help = Help()
    @StateObject private var childReaction = ReactionState()
    @StateObject private var reactions = ReactionBank()
    @State private var session: GameSession?
    @State private var weather = Weather.allCases.randomElement()!
    @State private var worn: [String: Date] = [:]  // what's on, and when it went on (it grows into place)
    @State private var showCard = false

    private var clothes: [ClothingItem] { outfitFor(weather) }
    private var isComplete: Bool { worn.count == clothes.count }
    /** What to suggest next; she can pick anything. */
    private var next: ClothingItem? { clothes.first { worn[$0.id] == nil } }

    var body: some View {
        GameBackground(scene: .home) {
            VStack(spacing: 0) {
                GameTopBar(onBack: onBack, roundIndex: worn.count, totalRounds: clothes.count, starsEarned: worn.count)
                // The child by the window, dressed as far as we've got, and the open wardrobe (clear of Puki's corner)
                GeometryReader { g in
                    let wardrobeW = 430 * min(1, g.size.height / 540)
                    HStack(spacing: 0) {
                        DressedChild(clothes: clothes, worn: worn, weather: weather, reaction: childReaction)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        Wardrobe { s in
                            ForEach(clothes, id: \.id) { item in
                                if worn[item.id] == nil {
                                    let spot = wardrobeSpots[item.id]!
                                    let isNext = item.id == next?.id
                                    Pic(item.icon)
                                        .frame(width: spot.size * s, height: spot.size * s)
                                        .rotationEffect(.degrees(spot.tilt))
                                        .clickable { tapped(item) }
                                        .hintGlow(help.showHint && isNext)
                                        .reaction(reactions(item.id))
                                        .idleBob(item.order, amount: isNext ? 1.4 : 0.4)  // what comes next moves a little more
                                        .offset(x: spot.x * s, y: spot.y * s)
                                }
                            }
                        }
                        .frame(width: wardrobeW)
                        .frame(maxHeight: .infinity)
                    }
                    .frame(width: g.size.width, height: g.size.height)
                }
                .padding(.leading, pukiCorner)
            }
            .padding(16)
            ConfettiOverlay(show: isComplete)
            if showCard { RoundCompleteSheet(starsEarned: worn.count, onContinue: onPlayAgain, onHome: onBack) }
        }
        .task { session = GameSession("dress", app.progressRepository) }
        .modifier(DressPrompt(weather: weather))
        // Stuck? Suggest a piece after a quiet moment; after a while, it wiggles
        .helpLoop(help, key: worn.count, active: !isComplete) {
            if let n = next { app.voice.play("voice_clothing_\(n.id)", language) }
        }
        .task(id: isComplete) {
            // All dressed: she hops, and then the end card
            guard isComplete else { return }
            switch weather {
            case .sun: app.voice.play("voice_dress_done", language) { app.companion.react("התלבשו מכף רגל ועד ראש, עד הנעליים!", "The child got dressed from head to toe, shoes and all!") }
            case .rain: app.voice.play("voice_dress_done", language) { app.companion.react("התלבשו לגשם, עם מעיל ומגפיים!", "The child got dressed for the rain, raincoat, boots and all!") }
            case .pajama: app.voice.play("voice_dress_done_pajama", language) { app.companion.react("לבשו פיג'מה ונעלי בית, איזה יום נעים!", "The child put on pajamas and slippers, what a cozy day!") }
            }
            Task { await childReaction.celebrate() }
            guard await delay(3000) else { return }
            showCard = true
        }
    }

    /** She can dress in any order; only what goes on the feet waits for socks. */
    private func tapped(_ item: ClothingItem) {
        if feet.contains(item.id) && worn["socks"] == nil {
            session?.slip()
            // Feet wait for socks: a puzzled look, and the socks
            app.sfx.play(.wrongGentle)
            app.voice.play("voice_clothing_socks", language)
            help.missed()
            Task { await childReaction.oops() }
            let r = reactions(item.id)
            Task { await r.shake() }
            return
        }
        worn[item.id] = Date()
        session?.star()
        if worn.count == clothes.count { session?.finish(rounds: clothes.count) }
        app.sfx.play(.success, volume: 0.6)
        app.voice.play("voice_clothing_\(item.id)", language)
        Task { await childReaction.hop() }
    }
}

private struct DressPrompt: ViewModifier {
    let weather: Weather
    func body(content: Content) -> some View {
        switch weather {
        case .sun: content.roundPrompt(0, "בהתלבשות: בחלון יש שמש, בוחרים מהארון מכנסיים קצרים וכובע.", "Getting dressed: it's sunny in the window, picking shorts and a hat from the wardrobe.", "voice_dress_sunny")
        case .rain: content.roundPrompt(0, "בהתלבשות: בחלון יורד גשם, בוחרים מהארון מעיל גשם ומגפיים.", "Getting dressed: it's raining in the window, picking a raincoat and boots from the wardrobe.", "voice_dress_rainy")
        case .pajama: content.roundPrompt(0, "בהתלבשות: היום יום פיג'מה, בוחרים מהארון פיג'מה ונעלי בית.", "Getting dressed: today is a pajama day, picking pajamas and slippers from the wardrobe.", "voice_dress_pajama")
        }
    }
}

/** The big child by the window, drawn with whatever's been put on so far (each piece grows into place). */
private struct DressedChild: View {
    let clothes: [ClothingItem]
    let worn: [String: Date]
    let weather: Weather
    @ObservedObject var reaction: ReactionState
    @Environment(\.gender) private var gender
    var body: some View {
        GeometryReader { g in
            let side = min(g.size.width, g.size.height)
            // The window hangs on the wall beside her: what's left of the width, and up to her arm
            let windowW = min(side * 0.42, (g.size.width - side) / 2 + side * 0.27)
            ZStack(alignment: .topLeading) {
                DrawCanvas { d in drawWindow(d, weather) }.frame(width: windowW, height: windowW * 1.25)
                TimelineView(.animation(paused: !worn.values.contains { Date().timeIntervalSince($0) < 1.5 })) { tl in
                    DrawCanvas { d in
                        var shown: [String: CGFloat] = ["underwear": 1]
                        for (id, at) in worn { shown[id] = springValue(tl.date.timeIntervalSince(at)) }
                        drawToddler(d, girl: gender == .girl, worn: shown)
                    }
                }
                .frame(width: side, height: side)
                .reaction(reaction, jumpHeight: 24)
                .frame(width: g.size.width, height: g.size.height)
            }
        }
    }
}

/** An open wardrobe: a rail with hangers, a shelf, and the floor for shoes. Drawn 430 × 540 points, smaller where that doesn't fit. */
private struct Wardrobe<Content: View>: View {
    @ViewBuilder let content: (CGFloat) -> Content
    var body: some View {
        GeometryReader { g in
            let s = min(1, g.size.width / 430, g.size.height / 540)
            ZStack(alignment: .topLeading) {
                DrawCanvas { d in
                    let w = d.size.width, h = d.size.height
                    let wood = Color(argb: 0xFFB07A45)
                    d.drawRoundRect(wood, .zero, d.size, w * 0.04)
                    d.drawRoundRect(Color(argb: 0xFFFFF1DC), CGPoint(w * 0.05, h * 0.05), CGSize(w * 0.9, h * 0.9), w * 0.02)
                    d.drawLine(Color(argb: 0xFF9E9E9E), CGPoint(w * 0.07, h * 0.12), CGPoint(w * 0.93, h * 0.12), w * 0.015)
                    for cx in [w * 0.29, w * 0.69] {
                        d.drawArc(Color(argb: 0xFF757575), startAngle: 200, sweepAngle: 140, useCenter: false, CGPoint(cx - w * 0.03, h * 0.085), CGSize(w * 0.06, h * 0.05), style: Stroke(width: w * 0.01))
                        d.drawPath(Path { p in p.moveTo(cx, h * 0.13); p.lineTo(cx - w * 0.13, h * 0.18); p.lineTo(cx + w * 0.13, h * 0.18); p.close() },
                                   Color(argb: 0xFF8D6E63), style: Stroke(width: w * 0.012))
                    }
                    d.drawRect(wood, CGPoint(w * 0.05, h * 0.68), CGSize(w * 0.9, h * 0.025))
                    d.drawRect(Color.black.copy(alpha: 0.08), CGPoint(w * 0.05, h * 0.705), CGSize(w * 0.9, h * 0.02))
                    d.drawRect(wood.copy(alpha: 0.9), CGPoint(0, h * 0.02), CGSize(w * 0.03, h * 0.96))
                    d.drawRect(wood.copy(alpha: 0.9), CGPoint(w * 0.97, h * 0.02), CGSize(w * 0.03, h * 0.96))
                }
                content(s)
            }
            .frame(width: 430 * s, height: 540 * s, alignment: .topLeading)
            .environment(\.layoutDirection, .leftToRight)
            .frame(width: g.size.width, height: g.size.height)
        }
    }
}

/** The room's window, filling its box: today's sky, a wooden frame, curtains and a sill. */
func drawWindow(_ d: DrawScope, _ weather: Weather) {
    let w = d.size.width, h = d.size.height
    let wood = Color(argb: 0xFFB07A45)
    let inX = w * 0.1, inY = h * 0.05, inW = w * 0.8, inH = h * 0.82
    d.drawRoundRect(Color.black.copy(alpha: 0.08), CGPoint(w * 0.06, h * 0.02), CGSize(w * 0.92, h * 0.92), w * 0.05)
    d.drawRoundRect(wood, CGPoint(w * 0.05, 0), CGSize(w * 0.9, h * 0.92), w * 0.05)
    d.clip(Path(CGRect(x: inX, y: inY, width: inW, height: inH))) { s in
        switch weather {
        case .sun: drawSunnySky(s, inX, inY, inW, inH)
        case .rain: drawRainySky(s, inX, inY, inW, inH)
        case .pajama: drawNightSky(s, inX, inY, inW, inH)
        }
    }
    d.drawLine(wood, CGPoint(inX + inW / 2, inY), CGPoint(inX + inW / 2, inY + inH), w * 0.035)
    d.drawLine(wood, CGPoint(inX, inY + inH * 0.48), CGPoint(inX + inW, inY + inH * 0.48), w * 0.035)
    for right in [false, true] {
        let edge = right ? inX + inW : inX
        let dir: CGFloat = right ? -1 : 1
        let curtain = Path { p in
            p.moveTo(edge - dir * w * 0.05, inY - h * 0.02)
            p.lineTo(edge + dir * inW * 0.2, inY - h * 0.02)
            p.quadraticBezierTo(edge + dir * inW * 0.12, inY + inH * 0.42, edge + dir * inW * 0.02, inY + inH * 0.56)
            p.quadraticBezierTo(edge + dir * inW * 0.08, inY + inH * 0.8, edge + dir * inW * 0.07, inY + inH)
            p.lineTo(edge - dir * w * 0.05, inY + inH)
            p.close()
        }
        d.drawPath(curtain, Color(argb: 0xFFF8BBD0))
        d.drawLine(Color(argb: 0xFFEC407A), CGPoint(edge - dir * w * 0.05, inY + inH * 0.54), CGPoint(edge + dir * inW * 0.05, inY + inH * 0.56), w * 0.03, .round)
    }
    d.drawRoundRect(Color(argb: 0xFFD4A26A), CGPoint(0, h * 0.875), CGSize(w, h * 0.09), h * 0.03)
    d.drawRoundRect(wood, CGPoint(w * 0.02, h * 0.945), CGSize(w * 0.96, h * 0.03), h * 0.015)
}

private func drawSunnySky(_ d: DrawScope, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) {
    d.drawRect(Color(argb: 0xFF81D4FA), CGPoint(x, y), CGSize(w, h))
    let sun = CGPoint(x + w * 0.56, y + h * 0.27)
    let r = w * 0.15
    for k in 0..<8 {
        let a = CGFloat(k) * .pi / 4
        let dd = CGPoint(cos(a), sin(a))
        d.drawLine(Color(argb: 0xFFFFCA28), sun + dd * (r * 1.3), sun + dd * (r * 1.75), r * 0.26, .round)
    }
    d.drawCircle(Color(argb: 0xFFFFEE58), r, sun)
    d.drawCircle(Color.white.copy(alpha: 0.45), r * 0.28, sun - CGPoint(r * 0.3, r * 0.3))
    d.drawOval(Color(argb: 0xFF9CCC65), CGPoint(x - w * 0.2, y + h * 0.7), CGSize(w * 1.4, h * 0.7))
    d.drawOval(Color(argb: 0xFF7CB342), CGPoint(x + w * 0.3, y + h * 0.8), CGSize(w * 1.1, h * 0.6))
}

private func drawRainySky(_ d: DrawScope, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) {
    d.drawRect(Color(argb: 0xFFB0BEC5), CGPoint(x, y), CGSize(w, h))
    for k in 0..<16 {
        let fk = CGFloat(k)
        let fx = x + w * (fk * 0.37).truncatingRemainder(dividingBy: 1)
        let fy = y + h * (0.3 + (fk * 0.53).truncatingRemainder(dividingBy: 1) * 0.58)
        d.drawLine(Color(argb: 0xFF42A5F5), CGPoint(fx, fy), CGPoint(fx - w * 0.025, fy + h * 0.075), w * 0.022, .round)
    }
    let cloud = Color(argb: 0xFF78909C)
    d.drawCircle(cloud, w * 0.17, CGPoint(x + w * 0.35, y + h * 0.2))
    d.drawCircle(cloud, w * 0.21, CGPoint(x + w * 0.54, y + h * 0.17))
    d.drawCircle(cloud, w * 0.14, CGPoint(x + w * 0.72, y + h * 0.24))
    d.drawRoundRect(cloud, CGPoint(x + w * 0.2, y + h * 0.2), CGSize(w * 0.65, h * 0.15), h * 0.07)
    d.drawOval(Color(argb: 0xFF90A4AE), CGPoint(x - w * 0.1, y + h * 0.88), CGSize(w * 1.2, h * 0.24))
    d.drawOval(Color(argb: 0xFF81D4FA), CGPoint(x + w * 0.24, y + h * 0.91), CGSize(w * 0.52, h * 0.07))
}

private func drawNightSky(_ d: DrawScope, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) {
    d.drawRect(Color(argb: 0xFF3949AB), CGPoint(x, y), CGSize(w, h))
    let moon = CGPoint(x + w * 0.31, y + h * 0.25)
    let r = w * 0.17
    d.drawCircle(Color(argb: 0xFFFFF59D), r, moon)
    d.drawCircle(Color(argb: 0xFF3949AB), r * 0.84, moon + CGPoint(r * 0.46, -r * 0.16))
    for (fx, fy) in [(0.66, 0.14), (0.82, 0.3), (0.6, 0.36), (0.3, 0.62), (0.72, 0.62), (0.42, 0.84)] as [(CGFloat, CGFloat)] {
        let c = CGPoint(x + w * fx, y + h * fy)
        let s = w * 0.03
        d.drawLine(.white, c - CGPoint(s, 0), c + CGPoint(s, 0), w * 0.016, .round)
        d.drawLine(.white, c - CGPoint(0, s), c + CGPoint(0, s), w * 0.016, .round)
    }
}
