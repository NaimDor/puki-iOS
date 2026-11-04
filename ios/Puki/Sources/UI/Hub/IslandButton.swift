import SwiftUI

/** Size of the whole island view; HubScreen centers it on the island's position. */
let islandWidth: CGFloat = 220
let islandHeight: CGFloat = 230

private let plankColor = Color(argb: 0xFFFFF8E1)
private let plankBorder = Color(argb: 0xFFBC8F5F)
private let plankText = Color(argb: 0xFF5D4037)

/** One turn of the hub's shared clock. */
let hubClockSeconds = 60.0
private let rippleCycles: CGFloat = 20  // a ripple every 3 s
private let swayCycles: CGFloat = 17
private let tapToGoMs = 150

/** What answers a tap: the disc, the sand and the plank. The rest of the box is water. */
private struct IslandHitShape: Shape {
    func path(in r: CGRect) -> Path {
        let w = r.width, h = r.height
        let unit = h / islandHeight
        var p = Path()
        p.addEllipse(in: CGRect(x: w / 2 - 60 * unit, y: 72 * unit - 60 * unit, width: 120 * unit, height: 120 * unit))
        p.addEllipse(in: CGRect(x: w * 0.04, y: h * 0.48, width: w * 0.92, height: h * 0.28))
        p.addRect(CGRect(x: w * 0.08, y: 148 * unit, width: w * 0.84, height: 44 * unit))
        return p
    }
}

/** A stable small hash of a string (Kotlin's String.hashCode), so each island keeps its own pace. */
func ktHash(_ s: String) -> Int32 {
    var h: Int32 = 0
    for u in s.utf16 { h = h &* 31 &+ Int32(u) }
    return h
}

struct IslandButton: View {
    let island: IslandDef
    let onTap: () -> Void
    let onClick: () -> Void
    var palmOnLeft = true
    var called = false  // Puki's calling this island: it hops
    @Environment(\.language) private var language
    @State private var hop: CGFloat = 0
    @State private var pressed = false

    var body: some View {
        let phase = CGFloat(Int(ktHash(island.id)) & 0xFF) / 255
        let bobCycles = CGFloat(12 + (Int(ktHash(island.id)) & 3))
        AmbientTimeline { t in
            let clock = Ambient.loop(t, hubClockSeconds)
            ZStack(alignment: .top) {
                // Water ripples stay put while the island bobs above them
                ForEach(0..<2, id: \.self) { k in
                    let tt = (clock * rippleCycles + CGFloat(k) * 0.5 + phase).truncatingRemainder(dividingBy: 1)
                    DrawCanvas { d in
                        d.drawOval(.white, CGPoint(d.size.width * 0.06, d.size.height * 0.51), CGSize(d.size.width * 0.88, d.size.height * 0.30), style: Stroke(width: 3))
                    }
                    .scaleEffect(x: 1 + 0.18 * tt, y: 1 + 0.2 * tt, anchor: UnitPoint(x: 0.5, y: 0.66))
                    .opacity(Double(0.45 * (1 - tt)))
                }
                ZStack(alignment: .top) {
                    DrawCanvas { d in drawIsland(d, palmOnLeft) }
                    DrawCanvas { d in drawPalmCrown(d, palmOnLeft) }
                        .rotationEffect(.degrees(Double(3 * sin(2 * .pi * (clock * swayCycles + phase)))),
                                        anchor: UnitPoint(x: palmOnLeft ? 0.1 : 0.9, y: 0.22))
                    // The island's sign: its picture on a colored disc
                    Pic((language == .english ? island.drawableEn : nil) ?? island.drawable)
                        .frame(width: 84, height: 84)
                        .frame(width: 108, height: 108)
                        .background(Circle().fill(island.color))
                        .overlay(Circle().stroke(Color.white, lineWidth: 5))
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.3), radius: 10, y: 4)
                        .padding(.top, 18)
                    // Name on a little wooden plank resting on the beach
                    Text(island.name(language))
                        .font(PukiFont.rounded(18, .bold))
                        .foregroundColor(plankText)
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.horizontal, 14).padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 12).fill(plankColor).shadow(color: .black.opacity(0.2), radius: 4, y: 2))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(plankBorder, lineWidth: 2))
                        .padding(.top, 150)
                }
                .offset(y: sin(2 * .pi * (clock * bobCycles + phase)) * 4)
            }
            .frame(width: islandWidth, height: islandHeight)
        }
        .frame(width: islandWidth, height: islandHeight)
        .scaleEffect(pressed ? 0.92 : 1)
        .animation(.spring(response: 0.36, dampingFraction: 0.4), value: pressed)
        .offset(y: -hop * 16)
        .contentShape(IslandHitShape())
        .gesture(DragGesture(minimumDistance: 0)
            .onChanged { _ in pressed = true }
            .onEnded { v in
                pressed = false
                if abs(v.translation.width) < 30 && abs(v.translation.height) < 30 { tapped() }
            })
        .testID("island.\(island.id)")
        .task(id: called) {
            guard called else { return }
            for _ in 0..<3 {
                withAnimation(.easeOut(duration: 0.14)) { hop = 1 }
                await delay(140)
                withAnimation(.spring(response: 0.28, dampingFraction: 0.5)) { hop = 0 }
                await delay(260)
            }
        }
    }

    /** A little hop and the island says its name, then off we go. */
    private func tapped() {
        onTap()
        Task { @MainActor in
            withAnimation(.easeOut(duration: 0.09)) { hop = 1 }
            await delay(90)
            withAnimation(.spring(response: 0.31, dampingFraction: 0.4)) { hop = 0 }
            await delay(tapToGoMs - 90)
            onClick()
        }
    }
}

private struct IslandShapes {
    let w, h, cx, baseY, side: CGFloat
    let trunkBase, top: CGPoint
    init(_ size: CGSize, _ palmOnLeft: Bool) {
        w = size.width; h = size.height; cx = w / 2; baseY = h * 0.64
        side = palmOnLeft ? -1 : 1
        trunkBase = CGPoint(cx + side * w * 0.30, baseY - h * 0.04)
        top = CGPoint(cx + side * w * 0.40, baseY - h * 0.42)
    }
    var sand: Path { Path { p in
        p.moveTo(cx - w * 0.42, baseY)
        p.cubicTo(cx - w * 0.44, baseY - h * 0.13, cx + w * 0.40, baseY - h * 0.15, cx + w * 0.43, baseY - h * 0.01)
        p.cubicTo(cx + w * 0.45, baseY + h * 0.10, cx - w * 0.40, baseY + h * 0.11, cx - w * 0.42, baseY)
        p.close()
    } }
    var sandTop: Path { Path { p in
        p.moveTo(cx - w * 0.40, baseY - h * 0.01)
        p.cubicTo(cx - w * 0.42, baseY - h * 0.13, cx + w * 0.38, baseY - h * 0.15, cx + w * 0.41, baseY - h * 0.02)
        p.cubicTo(cx + w * 0.42, baseY + h * 0.07, cx - w * 0.38, baseY + h * 0.08, cx - w * 0.40, baseY - h * 0.01)
        p.close()
    } }
    var grass: Path { Path { p in
        p.moveTo(cx - w * 0.32, baseY - h * 0.03)
        p.cubicTo(cx - w * 0.30, baseY - h * 0.17, cx + w * 0.28, baseY - h * 0.19, cx + w * 0.31, baseY - h * 0.04)
        p.cubicTo(cx + w * 0.30, baseY + h * 0.03, cx - w * 0.30, baseY + h * 0.04, cx - w * 0.32, baseY - h * 0.03)
        p.close()
    } }
    var trunk: Path { Path { p in
        p.moveTo(trunkBase.x, trunkBase.y)
        p.quadraticBezierTo(trunkBase.x + side * w * 0.01, baseY - h * 0.25, top.x, top.y)
    } }
    var leaf: Path { Path { p in
        p.moveTo(top.x, top.y)
        p.quadraticBezierTo(top.x + w * 0.08, top.y - h * 0.05, top.x + w * 0.16, top.y + h * 0.02)
        p.quadraticBezierTo(top.x + w * 0.08, top.y - h * 0.005, top.x, top.y)
        p.close()
    } }
}

/** Sand, grass and a palm tree, drawn in the lower part of the island box. */
private func drawIsland(_ d: DrawScope, _ palmOnLeft: Bool) {
    let s = IslandShapes(d.size, palmOnLeft)
    let w = s.w, h = s.h, cx = s.cx, baseY = s.baseY
    d.drawOval(Color(argb: 0x5580DEEA), CGPoint(cx - w * 0.47, baseY - h * 0.12), CGSize(w * 0.94, h * 0.27))
    d.drawPath(s.sand, Color(argb: 0xFFE0B972))
    d.drawPath(s.sandTop, Color(argb: 0xFFFFE0A3))
    d.drawPath(s.grass, Color(argb: 0xFF66BB6A))
    d.drawOval(Color(argb: 0xFF81C784), CGPoint(cx - w * 0.22, baseY - h * 0.15), CGSize(w * 0.30, h * 0.06))
    d.drawCircle(Color(argb: 0xFF43A047), w * 0.045, CGPoint(cx + (palmOnLeft ? 0.24 : -0.24) * w, baseY - h * 0.05))
    d.drawCircle(Color(argb: 0xFF4CAF50), w * 0.035, CGPoint(cx + (palmOnLeft ? 0.29 : -0.29) * w, baseY - h * 0.03))
    d.drawPath(s.trunk, Color(argb: 0xFF8D6E63), style: Stroke(width: w * 0.035, cap: .round))
}

/** The palm's crown. */
private func drawPalmCrown(_ d: DrawScope, _ palmOnLeft: Bool) {
    let s = IslandShapes(d.size, palmOnLeft)
    for angle in [-160.0, -120, -60, -20, 20] { d.rotate(angle, pivot: s.top) { $0.drawPath(s.leaf, Color(argb: 0xFF2E7D32)) } }
    d.drawCircle(Color(argb: 0xFF6D4C41), s.w * 0.018, CGPoint(s.top.x - s.w * 0.012, s.top.y + s.h * 0.012))
    d.drawCircle(Color(argb: 0xFF6D4C41), s.w * 0.018, CGPoint(s.top.x + s.w * 0.014, s.top.y + s.h * 0.014))
}
