import SwiftUI

#if DEBUG
/**
 * Debug builds only (never in the App Store build): what the store pictures and the preview video need.
 *   -PukiScreenshot <route>   opens that screen (Router)
 *   -PukiCaption "<text>"     frames the game under a headline, for the store's screenshots
 *   -PukiTour 1               walks through the islands by itself, for the store's preview video
 */
enum ScreenshotMode {
    static var caption: String? { UserDefaults.standard.string(forKey: "PukiCaption").flatMap { $0.isEmpty ? nil : $0 } }
    static var tour: Bool { UserDefaults.standard.bool(forKey: "PukiTour") }
    /** The islands the preview video visits, and how long it stays on each (seconds). */
    static let tourStops: [(Route, Double)] = [(.hub, 3.5), (.animals, 4), (.colors, 3.5), (.counting, 3.5), (.peekaboo, 3.5),
                                               (.music, 3.5), (.myDayKitchen, 3.5), (.myDayBath, 3.5)]
}

/** The game, a little smaller, under a big headline on a bright background. */
struct ScreenshotFrame: ViewModifier {
    func body(content: Content) -> some View {
        if let caption = ScreenshotMode.caption {
            GeometryReader { g in
                let w = g.size.width, h = g.size.height
                let k: CGFloat = 0.78
                ZStack(alignment: .top) {
                    LinearGradient(colors: [Color(argb: 0xFF29B6F6), Color(argb: 0xFF7E57C2)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    Text(caption)
                        .font(.system(size: h * 0.085, weight: .heavy, design: .rounded))
                        .foregroundColor(.white)
                        .shadow(color: .black.opacity(0.25), radius: 6, y: 3)
                        .lineLimit(1).minimumScaleFactor(0.5)
                        .padding(.horizontal, w * 0.05)
                        .frame(width: w, height: h * (1 - k) - h * 0.02)
                    content
                        .frame(width: w, height: h)
                        .clipShape(RoundedRectangle(cornerRadius: h * 0.06, style: .continuous))
                        .scaleEffect(k, anchor: .bottom)
                        .offset(y: -h * 0.03)
                        .shadow(color: .black.opacity(0.35), radius: 18, y: 8)
                        .allowsHitTesting(false)
                }
                .frame(width: w, height: h)
            }
            .ignoresSafeArea()
        } else {
            content
        }
    }
}
#endif

extension View {
    /** The store's screenshot frame in debug builds when asked for; nothing at all in a release build. */
    @ViewBuilder func storeFrame() -> some View {
        #if DEBUG
        modifier(ScreenshotFrame())
        #else
        self
        #endif
    }
}
