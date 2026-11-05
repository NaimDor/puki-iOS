import SwiftUI
import UIKit

/**
 * Every touch on the screen, watched without taking it (a window-wide gesture recognizer that never wins):
 * for the sparkles under her finger, and for Help, which counts any touch as the child being busy.
 */
@MainActor
final class TouchHub: ObservableObject {
    static let shared = TouchHub()
    /** When the screen was last touched (uptime ms). */
    private(set) var lastTouch: Int64 = uptimeMs()
    @Published private(set) var sparks: [Spark] = []
    #if DEBUG
    /** For the UI tests: where the last touch landed and which view UIKit gave it to. */
    @Published var trace = ""
    #endif

    struct Spark: Identifiable { let id = UUID(); let at: CGPoint; let born: Int64; let seed: Int }

    static let lifeMs: Int64 = 500
    static let maxSparks = 6

    func touched(at p: CGPoint) {
        lastTouch = uptimeMs()
        var s = sparks.filter { lastTouch - $0.born < Self.lifeMs }
        if s.count >= Self.maxSparks { s.removeFirst() }
        s.append(Spark(at: p, born: lastTouch, seed: Int(lastTouch % 7)))
        sparks = s
    }

    func prune() {
        let now = uptimeMs()
        if sparks.contains(where: { now - $0.born >= Self.lifeMs }) { sparks.removeAll { now - $0.born >= Self.lifeMs } }
    }
}

/** Watches touches on the whole window and tells the hub; it never takes a touch from anything. */
private final class WatchingRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    override init(target: Any?, action: Selector?) {
        super.init(target: target, action: action)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = self
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        for t in touches {
            let p = t.location(in: view)
            #if DEBUG
            let hit = view?.hitTest(p, with: event)
            let line = "\(Int(p.x)),\(Int(p.y)) \(hit.map { String(describing: type(of: $0)) } ?? "nothing")"
            Task { @MainActor in TouchHub.shared.trace = line }
            #endif
            Task { @MainActor in TouchHub.shared.touched(at: p) }
        }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) { state = .failed }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { state = .failed }
    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
}

/** Installs the watcher on the window once (put it anywhere in the app). */
struct TouchWatcherInstaller: UIViewRepresentable {
    final class V: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard let w = window, !(w.gestureRecognizers ?? []).contains(where: { $0 is WatchingRecognizer }) else { return }
            w.addGestureRecognizer(WatchingRecognizer(target: nil, action: nil))
        }
    }
    func makeUIView(context: Context) -> V { let v = V(); v.isUserInteractionEnabled = false; return v }
    func updateUIView(_ uiView: V, context: Context) {}
}

private let sparkColors = [Color(argb: 0xFFFFEE58), Color.white, Color(argb: 0xFFF8BBD0)]

/** Draws the sparkles (in window coordinates: put it on a full-screen layer). It ticks only while one is alive. */
struct SparkleLayer: View {
    @ObservedObject private var hub = TouchHub.shared
    var body: some View {
        GeometryReader { g in
            let origin = g.frame(in: .global).origin
            if !hub.sparks.isEmpty {
                TimelineView(.animation) { tl in
                    let now = uptimeMs()
                    DrawCanvas { d in
                        for s in hub.sparks {
                            let p = (CGFloat(now - s.born) / CGFloat(TouchHub.lifeMs)).coerceIn(0, 1)
                            for k in 0..<5 {
                                let a = CGFloat(k) * 2 * .pi / 5 + CGFloat(s.seed)
                                let at = s.at - origin + CGPoint(cos(a), sin(a)) * (56 * p)
                                drawTwinkle(d, at, 11 * (1 - p), sparkColors[k % sparkColors.count].copy(alpha: Double(1 - p * p)), Double(p * 360))
                            }
                        }
                    }
                    .onChange(of: tl.date) { _, _ in hub.prune() }
                }
            }
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
    }
}
