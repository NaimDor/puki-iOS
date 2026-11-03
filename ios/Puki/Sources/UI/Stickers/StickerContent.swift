import SwiftUI

struct StickerDef: Hashable {
    let id: String
    let name: BilingualText
    let icon: String
    let starsRequired: Int
    init(_ id: String, _ name: BilingualText, _ icon: String, _ stars: Int) { self.id = id; self.name = name; self.icon = icon; starsRequired = stars }
}

let allStickers = [
    StickerDef("cow", BilingualText("פרה", "Cow"), "ic_animal_cow", 0),
    StickerDef("dog", BilingualText("כלב", "Dog"), "ic_animal_dog", 2),
    StickerDef("cat", BilingualText("חתול", "Cat"), "ic_animal_cat", 4),
    StickerDef("rainbow", BilingualText("קשת", "Rainbow"), "ic_sticker_rainbow", 6),
    StickerDef("star", BilingualText("כוכב", "Star"), "ic_sticker_star", 8),
    StickerDef("flower", BilingualText("פרח", "Flower"), "ic_sticker_flower", 10),
    StickerDef("butterfly", BilingualText("פרפר", "Butterfly"), "ic_sticker_butterfly", 14),
    StickerDef("heart", BilingualText("לב", "Heart"), "ic_sticker_heart", 18),
    StickerDef("music", BilingualText("מוזיקה", "Music"), "ic_sticker_music", 22),
    StickerDef("sun", BilingualText("שמש", "Sun"), "ic_sticker_sun", 26),
    StickerDef("moon", BilingualText("ירח", "Moon"), "ic_sticker_moon", 30),
    StickerDef("rocket", BilingualText("טיל", "Rocket"), "ic_sticker_rocket", 40),
    StickerDef("crown", BilingualText("כתר", "Crown"), "ic_sticker_crown", 50),
    StickerDef("trophy", BilingualText("גביע", "Trophy"), "ic_sticker_trophy", 60),
    StickerDef("diamond", BilingualText("יהלום", "Diamond"), "ic_sticker_diamond", 80),
    StickerDef("unicorn", BilingualText("חד קרן", "Unicorn"), "ic_sticker_unicorn", 100),
    StickerDef("duck", BilingualText("ברווז", "Duck"), "ic_animal_duck", 120),
    StickerDef("horse", BilingualText("סוס", "Horse"), "ic_animal_horse", 140),
    StickerDef("sheep", BilingualText("כבשה", "Sheep"), "ic_animal_sheep", 160),
    StickerDef("pig", BilingualText("חזיר", "Pig"), "ic_animal_pig", 180),
    StickerDef("rabbit", BilingualText("ארנב", "Rabbit"), "ic_animal_rabbit", 200),
    StickerDef("chicken", BilingualText("תרנגולת", "Chicken"), "ic_animal_chicken", 230),
    StickerDef("goat", BilingualText("עז", "Goat"), "ic_animal_goat", 260),
    StickerDef("monkey", BilingualText("קוף", "Monkey"), "ic_animal_monkey", 290),
    StickerDef("lion", BilingualText("אריה", "Lion"), "ic_animal_lion", 320),
    StickerDef("elephant", BilingualText("פיל", "Elephant"), "ic_animal_elephant", 350),
    StickerDef("giraffe", BilingualText("ג׳ירפה", "Giraffe"), "ic_animal_giraffe", 380),
    StickerDef("penguin", BilingualText("פינגווין", "Penguin"), "ic_animal_penguin", 420),
]

/** The next sticker to celebrate: the first line crossed since `rewarded`, if she's reached one. */
func nextSticker(rewarded: Int, stars: Int) -> StickerDef? {
    allStickers.first { $0.starsRequired > rewarded && $0.starsRequired <= stars }
}

/** Her stars; with `onClick`, also the door to the album where they go: one big tap target. */
struct StarCounter: View {
    let stars: Int
    var onClick: (() -> Void)? = nil
    var body: some View {
        let pill = HStack(spacing: 8) {
            if onClick != nil { Pic("ic_ui_stickers").frame(width: 36, height: 36) }
            Pic("ic_ui_star").frame(width: 36, height: 36)
            Text("\(stars)")
                .font(PukiFont.headlineMedium)
                .foregroundColor(Color(argb: 0xFF6D4C41))
                .contentTransition(.numericText())
                .animation(.spring, value: stars)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 24).fill(Color(argb: 0xFFFFF8E1)).shadow(color: .black.opacity(0.2), radius: 6, y: 3))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color(argb: 0xFFFFC107), lineWidth: 3))
        if let onClick {
            pill.frame(minWidth: 160, minHeight: 100, alignment: .top).contentShape(Rectangle()).onTapGesture(perform: onClick)
        } else {
            pill
        }
    }
}

/** The star counter, following her stars as they come. */
struct LiveStarCounter: View {
    @ObservedObject var progress: ProgressRepository
    var onClick: (() -> Void)? = nil
    var body: some View { StarCounter(stars: progress.progress.totalStars, onClick: onClick) }
}
