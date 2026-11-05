import SwiftUI

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
        for (sv, f) in zip(subviews, frames(in: bounds.size, sizes)) {
            sv.place(at: CGPoint(x: bounds.minX + f.minX, y: bounds.minY + f.minY), anchor: .topLeading, proposal: .init(f.size))
        }
    }

    /** Where children of these sizes stand in an area of `size` (from its top left corner). */
    func frames(in size: CGSize, _ sizes: [CGSize]) -> [CGRect] {
        let tallest = sizes.map(\.height).max() ?? 0
        let w = size.width, h = size.height
        let centers = scatterCenters(sizes.count, seed, aroundCenter, flat: h < tallest * 1.5)
        return sizes.enumerated().map { i, p in
            let (fx0, fy) = centers[i]
            let fx = rtl ? 1 - fx0 : fx0  // mirrors in Hebrew, so the first one is where reading starts
            let x = (w * fx - p.width / 2).coerceIn(0, max(0, w - p.width))
            let y = (h * fy - p.height / 2).coerceIn(0, max(0, h - p.height))
            return CGRect(x: x, y: y, width: p.width, height: p.height)
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

/**
 * Where tappable things are on the screen, for one touch catcher over them all: kept in a box, not in view state,
 * so a bobbing balloon doesn't redraw the screen.
 */
final class TouchTargets {
    var frames: [String: CGRect] = [:]

    /** The one a touch at `p` (global) is on, or nearest to just outside one. */
    func under(_ p: CGPoint, among ids: Set<String>, slack: CGFloat = 24) -> String? {
        frames.filter { ids.contains($0.key) && $0.value.insetBy(dx: -slack, dy: -slack).contains(p) }
            .min { hypot($0.value.midX - p.x, $0.value.midY - p.y) < hypot($1.value.midX - p.x, $1.value.midY - p.y) }?.key
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
