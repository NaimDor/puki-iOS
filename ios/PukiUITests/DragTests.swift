import XCTest

/**
 * A real drag, finger down on a piece, across the screen and onto its place: the piece must follow the finger
 * and land. (In Hebrew it once went the other way: SwiftUI mirrors offsets in a right-to-left layout.)
 */
final class DragTests: PukiUITestCase {
    func testDragFollowsTheFinger_Hebrew() { dragEachPieceHome("he") }
    func testDragFollowsTheFinger_English() { dragEachPieceHome("en") }

    /** Table time: each piece has a place with its own name. Drag them all there, one by one. */
    private func dragEachPieceHome(_ lang: String) {
        g.launch(route: "myday/table", lang: lang)
        g.pause(2)
        var placed = 0
        for _ in 0..<12 {
            let all = g.nodes()
            if g.visible(all).contains(where: { $0.id == "continue" }) { break }
            let pieces = g.nodes(prefix: "piece.", in: all)
            guard let piece = pieces.first(where: { p in g.node("zone.\(p.id.dropFirst(6))", in: all) != nil }),
                  let zone = g.node("zone.\(piece.id.dropFirst(6))", in: all) else { break }
            let before = g.rounds?.done ?? 0
            g.drag(piece, to: zone)
            let landed = g.waitFor(6) { (self.g.rounds?.done ?? 0) > before || self.g.node("continue") != nil }
            XCTAssertTrue(landed, "dragging \(piece.id) onto its place didn't put it there (\(lang))")
            if !landed { g.shot("\(lang) missed \(piece.id)"); g.describe("\(piece.id) -> \(zone.id) [\(Int(zone.frame.midX)),\(Int(zone.frame.midY))]"); return }
            placed += 1
            g.pause(1.2)
        }
        XCTAssertGreaterThanOrEqual(placed, 3, "too few pieces to drag")
        XCTAssertNotNil(g.waitNode("continue", 8), "the table was set but the end card didn't come")
    }

    /** A piece let go on the wrong place goes back home, gently, and can be dragged again. */
    func testWrongDropGoesBackHome() {
        g.launch(route: "myday/table", lang: "he")
        g.pause(2)
        let all = g.nodes()
        let pieces = g.nodes(prefix: "piece.", in: all)
        guard let piece = pieces.first, let wrong = g.nodes(prefix: "zone.", in: all).first(where: { $0.id != "zone.\(piece.id.dropFirst(6))" }) else {
            return XCTFail("no piece and other place to try")
        }
        let before = g.rounds?.done ?? 0
        g.drag(piece, to: wrong)
        g.pause(1.5)
        XCTAssertEqual(g.rounds?.done ?? 0, before, "a wrong drop counted")
        let back = g.node(piece.id)
        XCTAssertNotNil(back, "the piece is gone after a wrong drop")
        if let back { XCTAssertEqual(back.frame.midX, piece.frame.midX, accuracy: 30, "the piece didn't go back home") }
    }
}
