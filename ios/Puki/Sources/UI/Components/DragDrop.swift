import SwiftUI

/** A piece counts as dropped on a zone it covers at least this much of (as a share of the piece). */
private let minOverlap: CGFloat = 0.3

/** Drag and drop for a game: the piece being dragged and the zones it may land on (in window coordinates). */
@MainActor
final class DragDropState: ObservableObject {
    @Published var draggedItemId: String?
    @Published var dragPosition: CGPoint = .zero  // the dragged piece's center
    var draggedSize: CGSize = .zero
    @Published var isDragging = false

    private var dropZones: [String: CGRect] = [:]

    func registerDropZone(_ id: String, _ bounds: CGRect) { dropZones[id] = bounds }
    func unregisterDropZone(_ id: String) { dropZones.removeValue(forKey: id) }

    /** Where to fly a tapped piece to. */
    func zoneCenter(_ id: String) -> CGPoint? { dropZones[id]?.center }

    func findDropTarget() -> String? { isDragging ? zoneUnderPiece() : nil }
    func hoveredDropZone() -> String? { zoneUnderPiece() }

    /** The zone under the piece's center; failing that, the zone it covers most, if it covers enough of it. */
    private func zoneUnderPiece() -> String? {
        if let hit = dropZones.first(where: { $0.value.contains(dragPosition) }) { return hit.key }
        if draggedSize == .zero { return nil }
        let piece = CGRect(x: dragPosition.x - draggedSize.width / 2, y: dragPosition.y - draggedSize.height / 2,
                           width: draggedSize.width, height: draggedSize.height)
        let area = piece.width * piece.height
        return dropZones.map { (id, b) -> (String, CGFloat) in
            let o = piece.intersection(b)
            return (id, o.isNull || o.width <= 0 || o.height <= 0 ? 0 : o.width * o.height / area)
        }.filter { $0.1 >= minOverlap }.max { $0.1 < $1.1 }?.0
    }

    func reset() {
        draggedItemId = nil
        dragPosition = .zero
        draggedSize = .zero
        isDragging = false
    }
}

/** A place a piece can be dropped; glows softly while a piece hovers over it. */
struct DropZone<Content: View>: View {
    let zoneId: String
    var highlightColor = Color(argb: 0xFF4CAF50)
    @ViewBuilder let content: () -> Content
    @EnvironmentObject private var dd: DragDropState

    var body: some View {
        let hovered = dd.isDragging && dd.hoveredDropZone() == zoneId
        content()
            .background(
                GeometryReader { g in
                    let f = g.frame(in: .global)
                    let r = g.size.maxDimension * 0.7
                    Circle()
                        .fill(RadialGradient(colors: [highlightColor.opacity(0.5), .clear], center: .center, startRadius: 0, endRadius: r))
                        .frame(width: r * 2, height: r * 2)
                        .position(x: g.size.width / 2, y: g.size.height / 2)
                        .opacity(hovered ? 1 : 0)
                        .animation(.easeInOut(duration: 0.2), value: hovered)
                        .onAppear { dd.registerDropZone(zoneId, f) }
                        .onChange(of: f) { _, nf in dd.registerDropZone(zoneId, nf) }
                        // The same place, a new round with a new name (the animal waiting for food): under its new name
                        .onChange(of: zoneId) { old, new in dd.unregisterDropZone(old); dd.registerDropZone(new, f) }
                }
                .allowsHitTesting(false)
            )
            .onDisappear { dd.unregisterDropZone(zoneId) }
            .testID("zone.\(zoneId)")
    }
}

private let lifted: CGFloat = 1.12
private let landMs = 220

/**
 * Something the child can drag onto a DropZone. With `tapToZone`, a plain tap works too: the piece flies to
 * that zone by itself, as if dragged there; without it, a tap gets a little shake and `onTap`. Right or wrong is
 * the game's call, through `onDragEnd`. It lifts the moment it's touched. When `landed` says the zone took it,
 * it shrinks away where it was dropped instead of flying back.
 */
struct DragTarget<Content: View>: View {
    let itemId: String
    var onDragEnd: (String?) -> Void = { _ in }
    var onDragStart: () -> Void = {}
    var tapToZone: String? = nil
    var onTap: () -> Void = {}
    var landed: (String) -> Bool = { _ in false }
    @ViewBuilder let content: () -> Content

    @EnvironmentObject private var dd: DragDropState
    @Environment(\.layoutDirection) private var dir
    @State private var offset: CGSize = .zero
    @State private var dragDelta: CGSize = .zero
    @State private var lift: CGFloat = 1
    @State private var shake: CGFloat = 0
    @State private var land: CGFloat = 1  // 1: here; 0: swallowed by the zone
    @State private var isDragging = false
    @State private var mine = false
    @State private var moved = false
    @State private var frame: CGRect = .zero
    /** Down while a finger is on the piece; iOS puts it back up even when it cancels the touch (no onEnded then). */
    @GestureState private var touching = false

    var body: some View {
        // The container stays home (it's what's measured); the piece inside moves
        ZStack {
            content()
                .scaleEffect(lift * (0.35 + 0.65 * land))
                .opacity(Double(land))
                .offset(x: shake)
                // The offsets are the finger's, on the screen (like Android's absoluteOffset), but SwiftUI mirrors an
                // offset in a right-to-left layout: in Hebrew the piece went the other way from the finger
                .offset(x: (dir == .rightToLeft ? -1 : 1) * (offset.width + dragDelta.width), y: offset.height + dragDelta.height)
                .gesture(
                    DragGesture(minimumDistance: 0, coordinateSpace: .global)
                        .updating($touching) { _, t, _ in t = true }
                        .onChanged { v in changed(v) }
                        .onEnded { v in ended(v) }
                )
        }
        // A touch cut short (the system took it, or a second hand): let go of it as if dropped nowhere, or the game
        // would think a piece is still held and ignore every touch after
        // (a turn later: a normal drop's onEnded comes first and clears `moved`)
        .onChange(of: touching) { _, t in if !t { DispatchQueue.main.async { if !touching { cancelled() } } } }
        // Gone mid-drag (the step moved on under the finger): the same
        .onDisappear { cancelled() }
        .background(GeometryReader { g in
            let f = g.frame(in: .global)
            Color.clear
                .onAppear { frame = f }
                .onChange(of: f) { _, nf in frame = nf }
        })
        .testID("piece.\(itemId)")
    }

    /** Where it stands now (its home, plus where it's been moved), as a center in window coordinates. */
    private var homeCenter: CGPoint { frame.center }

    private func changed(_ v: DragGesture.Value) {
        if !moved && !mine {
            // The touch itself: it lifts
            if lift == 1 { withAnimation(.spring(response: 0.2, dampingFraction: 0.5)) { lift = lifted } }
        }
        let far = abs(v.translation.width) > 8 || abs(v.translation.height) > 8
        if !moved && far {
            moved = true
            // One piece at a time: a second finger on another piece while one is being dragged does nothing
            mine = !(dd.isDragging && dd.draggedItemId != itemId)
            if !mine { return }
            onDragStart()
            isDragging = true
            dd.draggedItemId = itemId
            dd.draggedSize = frame.size
            dd.isDragging = true
        }
        if moved && mine {
            var t = Transaction(animation: nil); t.disablesAnimations = true
            withTransaction(t) { dragDelta = v.translation }
            dd.dragPosition = CGPoint(homeCenter.x + offset.width + v.translation.width, homeCenter.y + offset.height + v.translation.height)
        }
    }

    private func ended(_ v: DragGesture.Value) {
        defer { moved = false; mine = false }
        if !moved {
            tap()
            return
        }
        guard mine else { return }
        isDragging = false
        let dropTarget = dd.findDropTarget()
        dd.reset()
        // The finger's movement becomes the piece's offset, then it goes where it goes
        var t = Transaction(animation: nil); t.disablesAnimations = true
        withTransaction(t) {
            offset = CGSize(width: offset.width + dragDelta.width, height: offset.height + dragDelta.height)
            dragDelta = .zero
        }
        onDragEnd(dropTarget)
        finish(dropTarget)
    }

    private func cancelled() {
        // After a normal end ended() has cleared `moved`, and a tapped piece's flight finishes by itself
        guard moved else { if !isDragging && lift != 1 { settle() }; return }
        let wasMine = mine
        moved = false
        mine = false
        guard wasMine else { return }
        isDragging = false
        if dd.draggedItemId == itemId { dd.reset() }
        var t = Transaction(animation: nil); t.disablesAnimations = true
        withTransaction(t) {
            offset = CGSize(width: offset.width + dragDelta.width, height: offset.height + dragDelta.height)
            dragDelta = .zero
        }
        settle()
        goHome()
    }

    private func settle() { withAnimation(.spring(response: 0.25, dampingFraction: 0.5)) { lift = 1 } }

    private func goHome() {
        withAnimation(.spring(response: 0.36, dampingFraction: 0.4)) { offset = .zero }
    }

    /** After a drop: into the zone, or back home. */
    private func finish(_ zone: String?) {
        settle()
        if let zone, landed(zone) {
            withAnimation(.easeInOut(duration: Double(landMs) / 1000)) { land = 0 }
        } else {
            goHome()
        }
    }

    private func tap() {
        guard let zone = tapToZone, let target = dd.zoneCenter(zone) else {
            settle()
            // Nowhere to fly: it still answers
            onTap()
            Task { @MainActor in
                for (v, ms) in [(-10.0, 60), (10.0, 90), (-6.0, 80), (0.0, 70)] {
                    withAnimation(.easeInOut(duration: Double(ms) / 1000)) { shake = CGFloat(v) }
                    await delay(ms)
                }
            }
            return
        }
        if isDragging || dd.isDragging { settle(); return }
        let start = homeCenter
        onDragStart()
        isDragging = true
        dd.draggedItemId = itemId
        dd.draggedSize = frame.size
        dd.isDragging = true
        let to = CGSize(width: target.x - start.x, height: target.y - start.y)
        withAnimation(.easeInOut(duration: 0.45)) { offset = to }
        Task { @MainActor in
            await delay(450)
            dd.dragPosition = target
            let dropTarget = dd.findDropTarget()
            isDragging = false
            dd.reset()
            onDragEnd(dropTarget)
            finish(dropTarget)  // a wrong piece bounces back; a right one usually disappears first
        }
    }
}
