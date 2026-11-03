import SwiftUI

// ---------- Touch feedback ----------

/** Pressed things squish a little and spring back (the app-wide SquishIndication on Android). */
struct SquishButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.88 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.35), value: configuration.isPressed)
    }
}

/** No visual press at all (Compose's clickable with indication = null). */
struct PlainTapStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.contentShape(Rectangle()) }
}

extension View {
    /** Compose's clickable: a tap with the squish. */
    func clickable(_ enabled: Bool = true, squish: Bool = true, action: @escaping () -> Void) -> some View {
        Group {
            if squish {
                Button(action: action) { self }.buttonStyle(SquishButtonStyle()).disabled(!enabled)
            } else {
                Button(action: action) { self }.buttonStyle(PlainTapStyle()).disabled(!enabled)
            }
        }
    }
}

// ---------- Life ----------

/** Pops in with a bounce when it first appears; `index` staggers a row of items. */
struct PopIn<K: Equatable>: ViewModifier {
    var index = 0
    let key: K
    @State private var s: CGFloat = 0
    func body(content: Content) -> some View {
        content
            .scaleEffect(s)
            .opacity(Double(s.coerceIn(0, 1)))
            .onAppear { start() }
            .onChange(of: key) { _, _ in s = 0; start() }
    }
    private func start() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.08 * Double(index) + 0.016) {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.45)) { s = 1 }
        }
    }
}

extension View {
    func popIn(_ index: Int = 0) -> some View { modifier(PopIn(index: index, key: 0)) }
    func popIn<K: Equatable>(_ index: Int = 0, key: K) -> some View { modifier(PopIn(index: index, key: key)) }

    /** Pops away and fades when `gone`: for what's no longer needed, like the wrong answers after a win. */
    func popAway(_ gone: Bool, index: Int = 0) -> some View {
        scaleEffect(gone ? 0.001 : 1)
            .opacity(gone ? 0 : 1)
            .animation(.easeInOut(duration: 0.26).delay(gone ? 0.06 * Double(index) : 0), value: gone)
    }

    /** Compose's graphicsLayer alpha, for readability. */
    func alpha(_ a: Double) -> some View { opacity(a) }
}

/** What to do, in a white speech pill (readable on any scene; mostly for the grown-up). */
struct PromptBubble: View {
    let text: String
    var body: some View {
        if !text.trimmed().isEmpty && !isCompact() {
            Text(text)
                .font(PukiFont.headlineSmall)
                .foregroundColor(Color(argb: 0xFF5D4037))
                .padding(.horizontal, 28)
                .padding(.vertical, 10)
                .background(Capsule().fill(Color.white.opacity(0.93)).shadow(color: .black.opacity(0.2), radius: 6, y: 3))
                .popIn(key: text)
        }
    }
}

/** A picture from the asset catalog, sized to fit (Compose's Image(painterResource)). */
struct Pic: View {
    let name: String
    var body: some View { Image(name).resizable().scaledToFit() }
    init(_ name: String) { self.name = name }
}
