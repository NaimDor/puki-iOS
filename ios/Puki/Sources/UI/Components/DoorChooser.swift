import SwiftUI

/** One way into an island: a big round door with a picture, and its name under it for the grown-up. */
struct Door: Identifiable {
    let id = UUID()
    let label: BilingualText
    let color: Color
    let onTap: () -> Void
    let picture: AnyView
}

/** A door whose picture is a drawable, with an optional `badge` in its corner ("?!"). */
func imageDoor(_ label: BilingualText, _ color: Color, _ drawable: String, badge: String? = nil, onTap: @escaping () -> Void) -> Door {
    Door(label: label, color: color, onTap: onTap, picture: AnyView(
        GeometryReader { g in
            ZStack(alignment: .topTrailing) {
                Pic(drawable).frame(width: g.size.width * 0.7, height: g.size.height * 0.7).frame(maxWidth: .infinity, maxHeight: .infinity)
                if let badge {
                    Text(badge).font(.system(size: 56, weight: .bold, design: .rounded)).foregroundColor(Color(argb: 0xFFE65100))
                        .padding(.top, 40).padding(.trailing, 56)
                }
            }
            .environment(\.layoutDirection, .leftToRight)  // (behind his head, whichever way the page reads)
        }
    ))
}

/**
 * An island with more than one game in it opens here: a door for each, so every game is a door away and none
 * hides inside another.
 */
struct DoorChooserScreen: View {
    let onBack: () -> Void
    let scene: Scene
    let doors: [Door]
    @EnvironmentObject private var app: AppModel
    @Environment(\.language) private var language

    var body: some View {
        GameBackground(withCompanion: false, scene: scene) {  // (the doors show Puki already)
            VStack {
                GameTopBar(onBack: onBack, roundIndex: 0, totalRounds: 0, starsEarned: nil)
                GeometryReader { g in
                    let size = min(240, g.size.width / CGFloat(max(1, doors.count)) - 28, g.size.height - 80)
                    HStack {
                        ForEach(doors) { door in
                            Spacer(minLength: 0)
                            VStack(spacing: 14) {
                                door.picture
                                    .frame(width: size, height: size)
                                    .background(Circle().fill(door.color).shadow(color: .black.opacity(0.25), radius: 10, y: 4))
                                    .clipShape(Circle())
                                    .idleBob(amount: 0.6)
                                // On a tag, like the islands' names, so it reads on any scene
                                Text(door.label(language))
                                    .font(PukiFont.titleLarge)
                                    .foregroundColor(PukiColors.earthBrown)
                                    .padding(.horizontal, 18).padding(.vertical, 6)
                                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.white.opacity(0.92)))
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                app.sfx.play(.pop)
                                door.onTap()
                            }
                            Spacer(minLength: 0)
                        }
                    }
                    .frame(width: g.size.width, height: g.size.height)
                }
            }
            .padding(16)
        }
    }
}
