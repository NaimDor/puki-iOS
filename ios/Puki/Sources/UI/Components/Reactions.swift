import SwiftUI

/**
 * Lets a game make a character or item react: a happy jump for a right answer, munching when an animal eats,
 * and a gentle side-to-side "nope" for a wrong one. Attach with `.reaction(state)` and trigger from a Task.
 */
@MainActor
final class ReactionState: ObservableObject {
    @Published var lift: CGFloat = 0     // 0 = resting, 1 = top of the jump
    @Published var squash: CGFloat = 1   // vertical scale, width compensates
    @Published var sway: CGFloat = 0     // -1..1 sideways shake
    @Published var tilt: Double = 0      // degrees
    @Published var burst: CGFloat = 1    // 0..1 through a burst of stars; 1 = nothing showing
    var burstSeed = 0

    private func anim(_ a: Animation, _ change: () -> Void) { withAnimation(a, change) }

    func celebrate() async {
        burstSeed += 1
        var t = Transaction(animation: nil)
        t.disablesAnimations = true
        withTransaction(t) { burst = 0 }
        Task { @MainActor in
            await delay(16)  // (a frame at 0 first, or SwiftUI sees no change to animate)
            self.anim(.linear(duration: 1)) { self.burst = 1 }
        }
        for i in 0..<2 {
            anim(.easeInOut(duration: 0.09)) { squash = 0.82 }
            await delay(90)
            anim(.easeInOut(duration: 0.16)) { squash = 1.12 }
            anim(.easeInOut(duration: 0.2)) { tilt = i == 0 ? -8 : 8 }
            anim(.easeInOut(duration: 0.2)) { lift = 1 }
            await delay(200)
            anim(.spring(response: 0.28, dampingFraction: 0.35)) { squash = 1 }
            anim(.easeInOut(duration: 0.22)) { lift = 0 }
            await delay(220)
        }
        anim(.easeInOut(duration: 0.07)) { squash = 0.9 }
        await delay(70)
        anim(.spring(response: 0.5, dampingFraction: 0.4)) { tilt = 0 }
        anim(.spring(response: 0.31, dampingFraction: 0.3)) { squash = 1 }
        await delay(300)
    }

    /** One small jump: he noticed. */
    func hop() async {
        anim(.easeOut(duration: 0.13)) { lift = 1 }
        await delay(130)
        anim(.spring(response: 0.31, dampingFraction: 0.4)) { lift = 0 }
        await delay(250)
    }

    /** A puzzled head tilt. */
    func oops() async {
        anim(.easeInOut(duration: 0.18)) { tilt = -14 }
        await delay(180)
        anim(.spring(response: 0.44, dampingFraction: 0.35)) { tilt = 0 }
        await delay(300)
    }

    func munch() async {
        for _ in 0..<3 {
            anim(.easeInOut(duration: 0.11)) { squash = 0.9 }
            await delay(110)
            anim(.easeInOut(duration: 0.11)) { squash = 1.05 }
            await delay(110)
        }
        anim(.spring(response: 0.4, dampingFraction: 0.4)) { squash = 1 }
        await delay(200)
    }

    func shake() async {
        for (v, ms) in [(-1.0, 60), (1.0, 100), (-0.8, 100), (0.6, 90), (-0.3, 70), (0.0, 60)] {
            anim(.easeInOut(duration: Double(ms) / 1000)) { sway = CGFloat(v) }
            await delay(ms)
        }
    }
}

/** The star burst, drawn from its progress (animated by SwiftUI frame by frame). */
private struct StarBurst: ViewModifier, Animatable {
    var p: CGFloat
    let seed: Int
    var animatableData: CGFloat { get { p } set { p = newValue } }
    func body(content: Content) -> some View {
        content.overlay {
            if p < 1 {
                GeometryReader { g in
                    let pad: CGFloat = 90
                    DrawCanvas { d in drawStarBurst(d, p, seed, g.size, pad) }
                        .frame(width: g.size.width + pad * 2, height: g.size.height + pad * 2)
                        .offset(x: -pad, y: -pad)
                }
                .allowsHitTesting(false)
            }
        }
    }
}

private struct ReactionModifier: ViewModifier {
    @ObservedObject var state: ReactionState
    let jumpHeight: CGFloat
    let shakeDistance: CGFloat
    func body(content: Content) -> some View {
        content
            .scaleEffect(x: 2 - state.squash, y: state.squash, anchor: .bottom)
            .rotationEffect(.degrees(state.tilt + Double(state.sway) * 4), anchor: .bottom)
            .offset(x: state.sway * shakeDistance, y: -state.lift * jumpHeight)
            .modifier(StarBurst(p: state.burst, seed: state.burstSeed))
    }
}

extension View {
    /** Applies `state`'s jumps, squashes and shakes (pivoting on the bottom edge), and its burst of stars. */
    func reaction(_ state: ReactionState, jumpHeight: CGFloat = 48, shakeDistance: CGFloat = 16) -> some View {
        modifier(ReactionModifier(state: state, jumpHeight: jumpHeight, shakeDistance: shakeDistance))
    }
    @ViewBuilder func reaction(_ state: ReactionState?, jumpHeight: CGFloat = 48) -> some View {
        if let state { reaction(state, jumpHeight: jumpHeight) } else { self }
    }
}

private let burstColors = [Color(argb: 0xFFFFD54F), Color(argb: 0xFFFF80AB), Color(argb: 0xFF80DEEA), Color(argb: 0xFFFFFFFF), Color(argb: 0xFFC5E1A5), Color(argb: 0xFFFFAB40)]

/** A seeded random source (Kotlin's Random(seed)), so a burst looks the same every frame. */
struct SeededRandom: RandomNumberGenerator {
    private var s: UInt64
    init(_ seed: Int) { s = UInt64(bitPattern: Int64(seed)) &* 0x9E3779B97F4A7C15 &+ 0x2545F4914F6CDD1D }
    mutating func next() -> UInt64 {
        s &+= 0x9E3779B97F4A7C15
        var z = s
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
    mutating func nextFloat() -> CGFloat { CGFloat(Double(next() >> 11) / Double(1 << 53)) }
    mutating func nextBool() -> Bool { next() & 1 == 1 }
    mutating func nextInt(_ n: Int) -> Int { n <= 0 ? 0 : Int(next() % UInt64(n)) }
}

/** Stars and twinkles flying out from the centre, with a soft ring, at `p` (0..1) of the way through. */
private func drawStarBurst(_ d: DrawScope, _ p: CGFloat, _ seed: Int, _ itemSize: CGSize, _ pad: CGFloat) {
    let c = CGPoint(pad + itemSize.width / 2, pad + itemSize.height / 2)
    let minDim = itemSize.minDimension
    let reach = minDim * 0.55 + 40
    let out = 1 - (1 - p) * (1 - p) * (1 - p)
    let fade = p < 0.6 ? 1 : 1 - (p - 0.6) / 0.4
    if p < 0.5 {
        let k = p / 0.5
        d.drawCircle(Color(argb: 0xFFFFF59D).copy(alpha: Double(0.7 * (1 - k))), minDim * 0.35 + reach * 0.6 * k, c, style: Stroke(width: 10 * (1 - k) + 1))
    }
    let count = 14
    let base = (minDim * 0.11).coerceIn(10, 26)
    for i in 0..<count {
        var r = SeededRandom(seed * 31 + i)
        let angle = (CGFloat(i) + r.nextFloat() * 0.6) / CGFloat(count) * 2 * .pi
        let dist = reach * (0.55 + r.nextFloat() * 0.55) * out
        let drop = p * p * reach * 0.25
        let pos = CGPoint(c.x + cos(angle) * dist, c.y + sin(angle) * dist + drop)
        let grow = p < 0.12 ? p / 0.12 : 1
        let s = base * (0.7 + r.nextFloat() * 0.6) * grow * (1 - p * 0.45)
        let color = burstColors[(i + seed) % burstColors.count].copy(alpha: Double(fade))
        if i % 3 == 2 {
            drawTwinkle(d, pos, s * 0.9, color, Double(p * 360))
        } else {
            let rot = Double(p * 220 * (i % 2 == 0 ? 1 : -1) + r.nextFloat() * 72)
            d.rotate(rot, pivot: pos) { $0.drawPath(starPath(pos, s), color) }
        }
    }
}

func starPath(_ c: CGPoint, _ r: CGFloat, inner: CGFloat = 0.45) -> Path {
    Path { p in
        for k in 0..<10 {
            let rad = k % 2 == 0 ? r : r * inner
            let a = (CGFloat(k) * 36 - 90) * .pi / 180
            let pt = CGPoint(c.x + cos(a) * rad, c.y + sin(a) * rad)
            if k == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
    }
}

/** A four-pointed sparkle. */
func drawTwinkle(_ d: DrawScope, _ c: CGPoint, _ r: CGFloat, _ color: Color, _ spin: Double) {
    d.rotate(spin * 0.3, pivot: c) { s in
        let path = Path { p in
            p.moveTo(c.x, c.y - r)
            p.quadraticBezierTo(c.x, c.y, c.x + r, c.y)
            p.quadraticBezierTo(c.x, c.y, c.x, c.y + r)
            p.quadraticBezierTo(c.x, c.y, c.x - r, c.y)
            p.quadraticBezierTo(c.x, c.y, c.x, c.y - r)
            p.closeSubpath()
        }
        s.drawPath(path, color)
    }
}

/** A ReactionState for each thing on screen this round (Compose's remember(round) { associate { ReactionState() } }). */
@MainActor
final class ReactionBank: ObservableObject {
    private var states: [String: ReactionState] = [:]
    private var round: AnyHashable?
    func callAsFunction(_ id: String) -> ReactionState {
        if let s = states[id] { return s }
        let s = ReactionState()
        states[id] = s
        return s
    }
    /** A new round: fresh reactions. */
    func reset(for key: AnyHashable) {
        if round == key { return }
        round = key
        states = [:]
        objectWillChange.send()
    }
}

/** A value SwiftUI animates frame by frame, handed to `content` (Compose's animateFloatAsState read in a draw). */
struct Tweened<Content: View>: View, Animatable {
    var t: CGFloat
    let content: (CGFloat) -> Content
    init(_ t: CGFloat, @ViewBuilder _ content: @escaping (CGFloat) -> Content) { self.t = t; self.content = content }
    var animatableData: CGFloat { get { t } set { t = newValue } }
    var body: some View { content(t) }
}
