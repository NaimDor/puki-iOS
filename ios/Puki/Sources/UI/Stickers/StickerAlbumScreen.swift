import SwiftUI

private let stickerRims = [Color(argb: 0xFFFFCDD2), Color(argb: 0xFFBBDEFB), Color(argb: 0xFFFFF9C4), Color(argb: 0xFFC8E6C9), Color(argb: 0xFFE1BEE7), Color(argb: 0xFFFFE0B2)]
private let perPage = 9
private let columns = 3

struct StickerAlbumScreen: View {
    let onBack: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @Environment(\.layoutDirection) private var dir
    @State private var opened: GameProgress?
    @State private var spread = 0
    @State private var waiting: [String] = []  // the new stickers still to arrive
    private let spreads: [[StickerDef]] = stride(from: 0, to: allStickers.count, by: perPage * 2).map { Array(allStickers[$0..<min($0 + perPage * 2, allStickers.count)]) }

    var body: some View {
        let compact = isCompact()
        ZStack {
            DrawCanvas { d in drawTable(d) }.ignoresSafeArea()
            // The album lies open on the table: two pages of three by three
            GeometryReader { g in
                let pageInset: CGFloat = compact ? 16 : 40
                let cell = min((g.size.width / 2 - pageInset * 2) / CGFloat(columns), (g.size.height - pageInset * 2) / 3)
                ZStack {
                    DrawCanvas { d in drawAlbum(d) }
                    if let opened {
                        AlbumSpread(stickers: spreads[spread], spreadIndex: spread, cell: cell, pageInset: pageInset,
                                    totalStars: app.progressRepository.progress.totalStars, waiting: $waiting)
                            .id(spread)
                            .transition(.opacity)
                            .onAppear { _ = opened }
                    }
                }
            }
            .padding(.horizontal, compact ? 96 : 40)
            .padding(.top, compact ? 12 : 92)
            .padding(.bottom, compact ? 12 : 20)
            // Turning the pages, on the album's two edges
            HStack {
                if spread > 0 { PageTurn(forward: false) { app.sfx.play(.whoosh, volume: 0.4); withAnimation(.easeInOut(duration: 0.22)) { spread -= 1 } } }
                Spacer()
                if spread < spreads.count - 1 { PageTurn(forward: true) { app.sfx.play(.whoosh, volume: 0.4); withAnimation(.easeInOut(duration: 0.22)) { spread += 1 } } }
            }
            .padding(4)
            VStack {
                HStack(alignment: .top) {
                    BackButton(onBack: onBack).padding(compact ? 8 : 16)
                    Spacer()
                    LiveStarCounter(progress: app.progressRepository)
                        .scaleEffect(compact ? 0.72 : 1, anchor: dir == .rightToLeft ? .topLeading : .topTrailing)
                        .padding(compact ? 6 : 20)
                }
                Spacer()
            }
            SparkleLayer()
        }
        .task {
            app.companion.wake()
            // Where things stood when she opened the album; from here on, what she has counts as seen
            let then = app.progressRepository.progress
            app.progressRepository.markAlbumSeen(then.totalStars)
            let fresh = allStickers.filter { $0.starsRequired > then.albumSeenStars && $0.starsRequired <= then.totalStars }
            waiting = fresh.map(\.id)
            // Open at the page with something new on it
            if let new = fresh.first, let i = spreads.firstIndex(where: { $0.contains(new) }) { spread = i }
            opened = then
            if fresh.isEmpty { app.voice.play("voice_album_hello", language) }
        }
    }
}

private struct AlbumSpread: View {
    let stickers: [StickerDef]
    let spreadIndex: Int
    let cell: CGFloat
    let pageInset: CGFloat
    let totalStars: Int
    @Binding var waiting: [String]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(0..<2, id: \.self) { page in
                let pageStickers = Array(stickers.dropFirst(page * perPage).prefix(perPage))
                VStack(spacing: 0) {
                    ForEach(0..<3, id: \.self) { row in
                        HStack(spacing: 0) {
                            ForEach(0..<columns, id: \.self) { col in
                                let k = row * columns + col
                                if k < pageStickers.count {
                                    let sticker = pageStickers[k]
                                    let index = spreadIndex * perPage * 2 + page * perPage + k
                                    Spacer(minLength: 0)
                                    StickerSlot(sticker: sticker, unlocked: totalStars >= sticker.starsRequired, index: index, size: cell,
                                                arrival: waiting.firstIndex(of: sticker.id), onArrived: { waiting.removeAll { $0 == sticker.id } })
                                    Spacer(minLength: 0)
                                }
                            }
                        }
                        .frame(maxHeight: .infinity)
                    }
                }
                .padding(pageInset)
                .frame(maxWidth: .infinity)
            }
        }
    }
}

/** A big round arrow for turning the album's pages. */
private struct PageTurn: View {
    let forward: Bool
    let onClick: () -> Void
    var body: some View {
        NextArrow()
            .scaleEffect(x: forward ? 1 : -1, y: 1)
            .frame(width: 72, height: 72)
            .background(Circle().fill(Color(argb: 0xFFFFCA28)).shadow(color: .black.opacity(0.25), radius: 8, y: 3))
            .overlay(Circle().stroke(Color.white, lineWidth: 4))
            .clickable(action: onClick)
    }
}

/** One place in the album: a stuck-on sticker that wiggles now and then, or a dashed outline waiting for it. */
private struct StickerSlot: View {
    let sticker: StickerDef
    let unlocked: Bool
    let index: Int
    let size: CGFloat
    let arrival: Int?
    let onArrived: () -> Void
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language
    @StateObject private var reaction = ReactionState()
    @State private var landed: Bool?
    @State private var landing: CGFloat = 1

    var body: some View {
        // Stuck on a little crooked, the way a child sticks them
        let tilt = Double((index * 37) % 13 - 6)
        let isLanded = landed ?? (arrival == nil)
        Group {
            if unlocked && isLanded {
                AmbientTimeline { t in
                    // Every few seconds the sticker gives a quick happy wiggle; neighbours take turns
                    let phase = (Ambient.loop(t, 4.2) + CGFloat(index) * 0.137).truncatingRemainder(dividingBy: 1)
                    let k = phase / 0.16
                    let rot = phase < 0.16 ? tilt + Double(sin(k * 4 * .pi) * 9 * (1 - k)) : tilt
                    let s = phase < 0.16 ? 1 + sin(k * .pi) * 0.08 : 1
                    ZStack {
                        Circle().fill(Color.white)
                        Circle().stroke(stickerRims[index % stickerRims.count], lineWidth: 6)
                        Pic(sticker.icon).frame(width: size * 0.88 * 0.78, height: size * 0.88 * 0.78)
                        // A glossy sheen across the top
                        DrawCanvas { d in
                            d.drawArc(Color.white.copy(alpha: 0.35), startAngle: 200, sweepAngle: 70, useCenter: false,
                                      CGPoint(d.size.width * 0.12, d.size.height * 0.12), CGSize(d.size.width * 0.76, d.size.height * 0.76),
                                      style: Stroke(width: d.size.width * 0.05, cap: .round))
                        }
                    }
                    .shadow(color: .black.opacity(0.25), radius: 8, y: 3)
                    .rotationEffect(.degrees(rot))
                    .scaleEffect(s)
                }
                .reaction(reaction, jumpHeight: 28)
                .scaleEffect(landing)
                .frame(width: size * 0.88, height: size * 0.88)
                .clickable {
                    app.sfx.play(.pop)
                    Task { await reaction.celebrate() }
                    app.companion.react("באלבום המדבקות לחצו על המדבקה של \(sticker.name.he). תגיד עליה משהו קטן ומצחיק.",
                                        "In the sticker album the child tapped the \(sticker.name.en.lowercased()) sticker. Say something small and funny about it.") {
                        app.voice.play("voice_sticker_\(sticker.id)", language)
                    }
                }
            } else {
                ZStack(alignment: .bottom) {
                    Circle().fill(Color(argb: 0x14000000))
                    Circle().stroke(Color(argb: 0x55A1887F), style: StrokeStyle(lineWidth: 4, dash: [14, 10]))
                    // A faint silhouette of what's coming
                    Image(sticker.icon).resizable().renderingMode(.template).scaledToFit()
                        .foregroundColor(Color(argb: 0xFFBCAAA4)).opacity(0.45)
                        .frame(width: size * 0.88 * 0.62, height: size * 0.88 * 0.62)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    HStack(spacing: 2) {
                        Pic("ic_ui_star").frame(width: 20, height: 20)
                        Text("\(sticker.starsRequired)").font(PukiFont.labelLarge).foregroundColor(PukiColors.earthBrown)
                    }
                    .padding(.horizontal, 10).padding(.vertical, 2)
                    .background(Capsule().fill(Color.white.opacity(0.85)))
                }
                .reaction(reaction)
                .frame(width: size * 0.88, height: size * 0.88)
                .contentShape(Circle())
                .onTapGesture {
                    // Not yet: it wiggles, and there's what's needed
                    app.sfx.play(.pop)
                    Task { await reaction.shake() }
                    app.voice.play("voice_more_stars", language)
                }
            }
        }
        .frame(width: size, height: size)
        .task(id: arrival) {
            guard let arrival else { return }
            // A new sticker waits its turn as an outline, then lands with a pop
            landing = 0
            guard await delay(800 + arrival * 1500) else { return }
            landed = true
            app.sfx.play(.starCollect, volume: 0.8)
            app.voice.play("voice_sticker_\(sticker.id)", language)
            withAnimation(.spring(response: 0.6, dampingFraction: 0.4)) { landing = 1 }
            await delay(500)
            await reaction.celebrate()
            onArrived()
        }
    }
}

private func drawTable(_ d: DrawScope) {
    d.drawRect(Brush.verticalGradient([Color(argb: 0xFFD7A86E), Color(argb: 0xFFC08A55)]))
    let grain = Color(argb: 0x14000000)
    var y: CGFloat = 30
    var i = 0
    while y < d.size.height {
        let wave = sin(CGFloat(i) * 1.3) * 6
        d.drawLine(grain, CGPoint(0, y + wave), CGPoint(d.size.width, y - wave), 2)
        y += 46
        i += 1
    }
}

private func drawAlbum(_ d: DrawScope) {
    let w = d.size.width, h = d.size.height
    d.drawRoundRect(Color(argb: 0x33000000), CGPoint(6, 10), d.size, 28)
    d.drawRoundRect(Brush.verticalGradient([Color(argb: 0xFF9C6ADE), Color(argb: 0xFF7E4FC4)]), .zero, d.size, 28)
    let inset: CGFloat = 14
    for k in stride(from: 3, through: 1, by: -1) {
        d.drawRoundRect(Color(argb: 0xFFEDE3D1), CGPoint(inset, inset + CGFloat(k) * 2), CGSize(w - inset * 2, h - inset * 2), 14)
    }
    d.drawRoundRect(Color(argb: 0xFFFFFAF0), CGPoint(inset, inset), CGSize(w - inset * 2, h - inset * 2), 14)
    // Shade into the spine
    let spine = w / 2, shade: CGFloat = 60
    d.drawRect(Brush.horizontalGradient([.clear, Color(argb: 0x22000000), .clear], startX: spine - shade, endX: spine + shade),
               CGPoint(spine - shade, inset), CGSize(shade * 2, h - inset * 2))
    // Little dotted guide lines on each page
    let dot = Color(argb: 0x18A1887F)
    let step: CGFloat = 26
    var y = inset + step
    while y < h - inset {
        var x = inset + step
        while x < w - inset {
            if abs(x - spine) > shade { d.drawCircle(dot, 2, CGPoint(x, y)) }
            x += step
        }
        y += step
    }
    // Spiral rings down the spine
    let ringGap: CGFloat = 44
    var ry = inset + ringGap / 2
    while ry < h - inset {
        d.drawRoundRect(Color(argb: 0xFF9E9E9E), CGPoint(spine - 14, ry - 5), CGSize(28, 10), 5)
        d.drawRoundRect(Color(argb: 0xFFE0E0E0), CGPoint(spine - 14, ry - 5), CGSize(28, 4), 2)
        ry += ringGap
    }
}
