import SwiftUI

struct IslandDef: Hashable {
    let id: String
    let name: BilingualText
    let route: Route
    let color: Color
    let drawable: String
    let fractionalX: CGFloat
    let fractionalY: CGFloat
    /** The icon in English, where it differs (the Words book shows A and B, not א and ב). */
    var drawableEn: String? = nil
}

let allIslands = [
    IslandDef(id: "animals", name: BilingualText("גן החיות", "Animal Garden"), route: .animals, color: PukiColors.grassGreen, drawable: "ic_island_animals", fractionalX: 0.14, fractionalY: 0.50),
    IslandDef(id: "colors", name: BilingualText("צבעים", "Color Splash"), route: .colors, color: PukiColors.coralPink, drawable: "ic_island_colors", fractionalX: 0.33, fractionalY: 0.42),
    IslandDef(id: "shapes", name: BilingualText("צורות", "Shapes"), route: .shapes, color: PukiColors.softPurple, drawable: "ic_island_shapes", fractionalX: 0.52, fractionalY: 0.53),
    IslandDef(id: "counting", name: BilingualText("ספירה", "Counting"), route: .counting, color: PukiColors.sunYellow, drawable: "ic_island_counting", fractionalX: 0.71, fractionalY: 0.43),
    IslandDef(id: "peekaboo", name: BilingualText("מחבואים", "Peekaboo"), route: .peekaboo, color: Color(argb: 0xFFFFCC80), drawable: "ic_island_peekaboo", fractionalX: 0.88, fractionalY: 0.37),
    IslandDef(id: "music", name: BilingualText("מוזיקה", "Music"), route: .musicDoors, color: PukiColors.warmOrange, drawable: "ic_island_music", fractionalX: 0.91, fractionalY: 0.635),
    IslandDef(id: "words", name: BilingualText("מילים", "Words"), route: .words, color: Color(argb: 0xFF9FA8DA), drawable: "ic_island_words", fractionalX: 0.77, fractionalY: 0.81, drawableEn: "ic_island_words_en"),
    // The bottom row, right to left in the order the sea route visits it
    IslandDef(id: "echo", name: BilingualText("תוכי מחקה", "Echo Puki"), route: .echo, color: Color(argb: 0xFFCE93D8), drawable: "ic_island_echo", fractionalX: 0.58, fractionalY: 0.80),
    IslandDef(id: "myday", name: BilingualText("היום שלי", "My Day"), route: .myDay, color: PukiColors.earthBrown, drawable: "ic_island_myday", fractionalX: 0.385, fractionalY: 0.80),
    // (his brain games; without the brain, Say it with Puki)
    IslandDef(id: "say", name: BilingualText("הפינה של פוקי", "Puki's corner"), route: .corner, color: Color(argb: 0xFF81D4FA), drawable: "ic_companion_parrot_talk", fractionalX: 0.205, fractionalY: 0.80),
]
