import SwiftUI

struct RoomDef { let route: Route; let name: BilingualText; let icon: String; let color: Color }

private let rooms = [
    RoomDef(route: .myDayBath, name: BilingualText("אמבטיה", "Bath Time"), icon: "ic_myday_bathtub", color: Color(argb: 0xFF81D4FA)),
    RoomDef(route: .myDayBrush, name: BilingualText("צחצוח שיניים", "Brushy Brushy"), icon: "ic_myday_toothbrush", color: Color(argb: 0xFF80CBC4)),
    RoomDef(route: .myDayTidy, name: BilingualText("סידור חדר", "Tidy Up"), icon: "ic_tidy_teddy", color: Color(argb: 0xFFFFCC80)),
    RoomDef(route: .myDayDress, name: BilingualText("להתלבש", "Getting Dressed"), icon: "ic_clothing_shirt", color: Color(argb: 0xFFEF9A9A)),
    RoomDef(route: .myDayTable, name: BilingualText("ארוחה", "Table Time"), icon: "ic_table_plate", color: Color(argb: 0xFFA5D6A7)),
    RoomDef(route: .myDayBedtime, name: BilingualText("שינה", "Bedtime"), icon: "ic_bedtime_moon", color: Color(argb: 0xFF7E57C2)),
    RoomDef(route: .myDayKitchen, name: BilingualText("מטבח", "Kitchen"), icon: "ic_myday_cake", color: Color(argb: 0xFFFFAB91)),
    RoomDef(route: .myDayGarage, name: BilingualText("שטיפת מכונית", "Car Wash"), icon: "ic_myday_car", color: Color(argb: 0xFF90A4AE)),
]

// Upstairs: toys, dressing, teeth and bed; downstairs: the bath, the table, the kitchen and the garage
private let upstairs = [rooms[2], rooms[3], rooms[1], rooms[5]]
private let downstairs = [rooms[0], rooms[4], rooms[6], rooms[7]]

// The house occupies this part of the screen (fractions of width / height)
private let houseLeft: CGFloat = 0.12
private let houseRight: CGFloat = 0.88
private let wallBottom: CGFloat = 0.94

/** The house's top half: on a phone the roof flattens, to give the rooms the height. */
private struct HouseTop { let wall, ridge, atticY, atticR: CGFloat }
private let tallHouse = HouseTop(wall: 0.30, ridge: 0.04, atticY: 0.18, atticR: 0.06)
private let flatHouse = HouseTop(wall: 0.14, ridge: 0.01, atticY: 0.085, atticR: 0.05)

struct MyDayScreen: View {
    let onRoomClick: (Route) -> Void
    let onBack: () -> Void

    var body: some View {
        let compact = isCompact()
        let house = compact ? flatHouse : tallHouse
        ZStack(alignment: .topLeading) {
            SceneBackdrop(scene: .garden)
            AmbientTimeline { t in DrawCanvas { d in drawSmoke(d, Ambient.loop(t, 4)) } }.ignoresSafeArea()
            DrawCanvas { d in drawHouse(d, house) }.ignoresSafeArea()
            GeometryReader { g in
                let w = g.size.width, h = g.size.height
                // Puki peeks out of the round attic window
                let atticSize = h * house.atticR * 2
                OfflineParrot(size: atticSize * 0.8)
                    .frame(width: atticSize, height: atticSize)
                    .position(x: w / 2, y: h * house.atticY)
                // Rooms, laid out inside the house walls as a cutaway, two floors
                VStack(spacing: compact ? 12 : 24) {
                    ForEach(0..<2, id: \.self) { f in
                        HStack(spacing: 20) {
                            ForEach(Array((f == 0 ? upstairs : downstairs).enumerated()), id: \.offset) { i, room in
                                RoomButton(room: room, index: f * 4 + i, compact: compact) { onRoomClick(room.route) }
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, compact ? 6 : 16)
                .frame(width: w * (houseRight - houseLeft), height: h * (wallBottom - house.wall))
                .position(x: w / 2, y: h * house.wall + h * (wallBottom - house.wall) / 2)
            }
            BackButton(onBack: onBack).padding(16)
            SparkleLayer()
        }
    }
}

/** Little clouds puffing out of the chimney, behind the house. */
private func drawSmoke(_ d: DrawScope, _ smoke: CGFloat) {
    let w = d.size.width, h = d.size.height
    let chimneyX = w * houseRight - w * 0.14
    for k in 0..<3 {
        let t = (smoke + CGFloat(k) / 3).truncatingRemainder(dividingBy: 1)
        let puff = CGPoint(chimneyX + w * 0.025 + sin(t * 6) * w * 0.012 + t * w * 0.03, h * (0.05 - t * 0.1))
        d.drawCircle(Color.white.copy(alpha: Double(0.8 * (1 - t))), h * (0.018 + t * 0.03), puff)
    }
}

/** Bushes, a tiled roof with its chimney, and the walls the rooms sit in. */
private func drawHouse(_ d: DrawScope, _ house: HouseTop) {
    let w = d.size.width, h = d.size.height
    let l = w * houseLeft, r = w * houseRight
    let top = h * house.wall, bottom = h * wallBottom
    let chimneyX = r - w * 0.14
    let chimneyTop = h * (house.ridge + 0.02)
    d.drawRect(Color(argb: 0xFF8D6E63), CGPoint(chimneyX, chimneyTop), CGSize(w * 0.05, top - chimneyTop))
    d.drawRect(Color(argb: 0xFF6D4C41), CGPoint(chimneyX - w * 0.005, chimneyTop - h * 0.01), CGSize(w * 0.06, h * 0.025))
    let roofL = l - w * 0.04, roofR = r + w * 0.04, roofBase = top + h * 0.01
    let roof = Path { p in p.moveTo(roofL, roofBase); p.lineTo(w / 2, h * house.ridge); p.lineTo(roofR, roofBase); p.close() }
    d.drawPath(roof, Color(argb: 0xFFE53935))
    d.clip(roof) { s in
        let tile = h * 0.045
        var y = h * house.ridge + tile
        var row = 0
        while y < roofBase + tile {
            var x: CGFloat = row % 2 == 0 ? 0 : tile
            while x < w {
                s.drawArc(Color(argb: 0xFFC62828), startAngle: 0, sweepAngle: 180, useCenter: false, CGPoint(x - tile, y - tile), CGSize(tile * 2, tile * 2), style: Stroke(width: 3))
                x += tile * 2
            }
            y += tile
            row += 1
        }
    }
    let eave = Path { p in
        p.moveTo(roofL, roofBase); p.lineTo(roofR, roofBase); p.lineTo(r + w * 0.02, top - h * 0.015); p.lineTo(l - w * 0.02, top - h * 0.015); p.close()
    }
    d.drawPath(eave, Color(argb: 0xFFB71C1C))
    let attic = CGPoint(w / 2, h * house.atticY)
    d.drawCircle(Color(argb: 0xFFFFF8E1), h * (house.atticR + 0.012), attic)
    d.drawCircle(Color(argb: 0xFFB3E5FC), h * house.atticR, attic)
    d.drawRoundRect(Color(argb: 0x33000000), CGPoint(l + 8, top + 10), CGSize(r - l, bottom - top), 16)
    d.drawRoundRect(Color(argb: 0xFFFFE0B2), CGPoint(l, top), CGSize(r - l, bottom - top), 16)
    d.drawRect(Color(argb: 0xFFBC8F5F), CGPoint(l, (top + bottom) / 2 - h * 0.01), CGSize(r - l, h * 0.02))
    for (x, s) in [(l - w * 0.02, 1.0), (l + w * 0.04, 0.75), (r + w * 0.02, 1.0), (r - w * 0.04, 0.75)] as [(CGFloat, CGFloat)] {
        let c = CGPoint(x, bottom - h * 0.01)
        let rad = h * 0.06 * s
        d.drawCircle(Color(argb: 0xFF43A047), rad, c)
        d.drawCircle(Color(argb: 0xFF66BB6A), rad * 0.8, c + CGPoint(-rad * 0.6, -rad * 0.3))
        d.drawCircle(Color(argb: 0xFF66BB6A), rad * 0.7, c + CGPoint(rad * 0.6, -rad * 0.25))
        d.drawCircle(Color(argb: 0xFFF48FB1), rad * 0.12, c + CGPoint(-rad * 0.3, -rad * 0.55))
        d.drawCircle(Color(argb: 0xFFFFF59D), rad * 0.12, c + CGPoint(rad * 0.4, -rad * 0.1))
    }
}

/** The inside of a room: patterned wallpaper, a wooden floor and a skirting board. */
private func drawRoomInterior(_ d: DrawScope, _ color: Color, _ pattern: Int) {
    let w = d.size.width, h = d.size.height
    let floorTop = h * 0.78
    d.drawRect(.white)
    d.drawRect(Brush.verticalGradient([color.copy(alpha: 0.6), color.copy(alpha: 0.9)], startY: 0, endY: floorTop))
    let ink = Color.white.copy(alpha: 0.35)
    d.clip(Path(CGRect(x: 0, y: 0, width: w, height: floorTop))) { s in
        switch pattern % 3 {
        case 0:  // polka dots
            let step: CGFloat = 36
            var y = step / 2, row = 0
            while y < floorTop {
                var x = row % 2 == 0 ? step / 2 : step
                while x < w { s.drawCircle(ink, 5, CGPoint(x, y)); x += step }
                y += step * 0.8; row += 1
            }
        case 1:  // stripes
            var x: CGFloat = 0
            while x < w { s.drawRect(ink, CGPoint(x, 0), CGSize(34 * 0.4, floorTop)); x += 34 }
        default:  // little stars
            let step: CGFloat = 44
            var y = step / 2, row = 0
            while y < floorTop {
                var x = row % 2 == 0 ? step / 2 : step
                while x < w {
                    s.drawLine(ink, CGPoint(x - 6, y), CGPoint(x + 6, y), 3)
                    s.drawLine(ink, CGPoint(x, y - 6), CGPoint(x, y + 6), 3)
                    x += step
                }
                y += step * 0.8; row += 1
            }
        }
    }
    d.drawRect(Color(argb: 0xFFD7A86E), CGPoint(0, floorTop), CGSize(w, h - floorTop))
    var px = w * 0.17
    while px < w { d.drawLine(Color(argb: 0xFFBF8A55), CGPoint(px, floorTop), CGPoint(px, h), 2); px += w * 0.25 }
    d.drawRect(.white, CGPoint(0, floorTop - 4), CGSize(w, 5))
}

private struct RoomButton: View {
    let room: RoomDef
    let index: Int
    let compact: Bool
    let onClick: () -> Void
    @Environment(\.language) private var language

    var body: some View {
        GeometryReader { g in
            // On a phone on its side the picture has the room to itself; the label is for reading along
            let iconSize = max(40, min(124, g.size.height - (compact ? 16 : 72)))
            ZStack(alignment: .bottom) {
                DrawCanvas { d in drawRoomInterior(d, room.color, index) }
                Pic(room.icon)
                    .frame(width: iconSize, height: iconSize)
                    .idleBob(index, amount: 1.3)
                    .padding(.bottom, compact ? 0 : 36)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                if !compact {
                    Text(room.name(language))
                        .font(PukiFont.rounded(18, .bold))
                        .foregroundColor(Color(argb: 0xFF5D4037))
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                        .padding(.horizontal, 14).padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(argb: 0xFFFFF8E1)))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(argb: 0xFFBC8F5F), lineWidth: 2))
                        .padding(.bottom, 10)
                        .padding(.horizontal, 8)
                }
            }
            .frame(width: g.size.width, height: g.size.height)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white, lineWidth: 6))
        .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
        .clickable(action: onClick)
        .popIn(index)
    }
}
