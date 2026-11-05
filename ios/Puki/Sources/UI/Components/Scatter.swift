import SwiftUI

/** How much of the start of a row Puki's corner takes (him and his margins, on a tablet). */
private let pukiCorner: CGFloat = 150

/**
 * Lays its children out loosely instead of in a row: high and low in turn, nudged a little each round (`seed`)
 * so no two rounds look the same. Order is kept (first child first, in reading direction). With `aroundCenter`
 * the middle is left free (for a target), and the children stand half on one side of it and half on the other.
 */
struct Scatter: Layout {
    let seed: Int
    var aroundCenter = false
    var rtl = false

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let tallest = subviews.map { $0.sizeThatFits(.unspecified).height }.max() ?? 0
        return CGSize(width: proposal.width ?? 600, height: proposal.height ?? tallest * 1.7)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let tallest = sizes.map(\.height).max() ?? 0
        let w = bounds.width, h = bounds.height
        let flat = h < tallest * 1.5
        let centers = scatterCenters(subviews.count, seed, aroundCenter, flat: flat)
        // In one row (a phone on its side) the things stand as low as Puki, so his corner at the start of the row is
        // left free when there's room: he's drawn on top, and a touch on a balloon behind him made him talk instead
        let room = w - sizes.map(\.width).reduce(0, +) * 1.1
        let clear = flat && !aroundCenter ? min(pukiCorner, max(0, room)) : 0  // (around a centre, the middle stays put)
        for (i, sv) in subviews.enumerated() {
            let (fx0, fy) = centers[i]
            let lead = clear + (w - clear) * fx0  // from where reading starts
            let p = sizes[i]
            let cx = rtl ? w - lead : lead  // mirrors in Hebrew, so the first one is where reading starts
            let x = (cx - p.width / 2).coerceIn(0, max(0, w - p.width))
            let y = (h * fy - p.height / 2).coerceIn(0, max(0, h - p.height))
            sv.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY + y), anchor: .topLeading, proposal: .init(p))
        }
    }
}

/** Where `n` scattered things stand, as fractions of the area. `flat`: too short for high and low. */
func scatterCenters(_ n: Int, _ seed: Int, _ aroundCenter: Bool, flat: Bool) -> [(CGFloat, CGFloat)] {
    var rnd = SeededRandom(seed * 7919 + n)
    let flip = rnd.nextBool()  // whether the first one starts high or low
    if flat && aroundCenter {
        let left = (n + 1) / 2
        return (0..<n).map { i in
            let onLeft = i < left
            let k = CGFloat(onLeft ? i : i - left)
            return (onLeft ? 0.08 + k * 0.14 : 0.92 - k * 0.14, 0.5)
        }
    } else if !aroundCenter {
        return (0..<n).map { i in
            // (the first one never starts low: that's Puki's corner)
            let high = i == 0 || ((i % 2 == 0) != flip)
            let x = (CGFloat(i) + 0.5) / CGFloat(n) + (rnd.nextFloat() - 0.5) * 0.25 / CGFloat(n)
            let y: CGFloat = flat ? 0.5 : (high ? 0.3 : 0.7) + (rnd.nextFloat() - 0.5) * 0.12
            return (x, y)
        }
    }
    let left = (n + 1) / 2
    return (0..<n).map { i in
        let onLeft = i < left
        let k = onLeft ? i : i - left
        let m = onLeft ? left : n - left
        let x: CGFloat = (onLeft ? 0.15 : 0.85) + (k % 2 == 0 ? -0.04 : 0.04) + (rnd.nextFloat() - 0.5) * 0.04
        let y = (CGFloat(k) + 0.5) / CGFloat(m) + (rnd.nextFloat() - 0.5) * 0.1
        return (x, y)
    }
}

/** A small tilt for the `index`th scattered thing, different each round. */
func scatterTilt(_ seed: Int, _ index: Int) -> Double {
    var r = SeededRandom(seed * 131 + index)
    return Double(r.nextFloat() * 16 - 8)
}

/** A random number picked once per visit to a screen, so round 1 doesn't look the same every time. */
func newSessionSeed() -> Int { Int.random(in: 0..<100_000) }

/** A round of a pick-one game: `won` while the win is being celebrated (the confetti goes by it). */
@MainActor
final class RoundFlow: ObservableObject {
    @Published private(set) var won = false
    weak var companion: Companion?

    /** The round is won: a party of `quietMs`, or `talkingMs` if Puki will have something to say, then `next`. */
    func win(quietMs: Int, talkingMs: Int, next: @escaping () async -> Void) {
        won = true
        let ms = companion?.isReady == true ? talkingMs : quietMs
        Task { @MainActor in
            await delay(ms)
            won = false
            await next()
        }
    }
}
