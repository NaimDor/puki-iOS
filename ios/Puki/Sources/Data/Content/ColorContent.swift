import SwiftUI

struct ColorDef: Hashable {
    let id: String
    let name: BilingualText
    let color: Color
    init(_ id: String, _ name: BilingualText, _ color: Color) { self.id = id; self.name = name; self.color = color }
}

let level1Colors = [
    ColorDef("red", BilingualText("אדום", "Red"), Color(argb: 0xFFEF5350)),
    ColorDef("blue", BilingualText("כחול", "Blue"), Color(argb: 0xFF42A5F5)),
    ColorDef("yellow", BilingualText("צהוב", "Yellow"), Color(argb: 0xFFFFEE58)),
    ColorDef("green", BilingualText("ירוק", "Green"), Color(argb: 0xFF66BB6A)),
]

let level2Colors = level1Colors + [
    ColorDef("orange", BilingualText("כתום", "Orange"), Color(argb: 0xFFFF9800)),
    ColorDef("purple", BilingualText("סגול", "Purple"), Color(argb: 0xFF9C27B0)),
]

let level3Colors = level2Colors + [
    ColorDef("pink", BilingualText("ורוד", "Pink"), Color(argb: 0xFFE91E63)),
    ColorDef("brown", BilingualText("חום", "Brown"), Color(argb: 0xFF795548)),
    ColorDef("white", BilingualText("לבן", "White"), Color(argb: 0xFFFAFAFA)),
    ColorDef("black", BilingualText("שחור", "Black"), Color(argb: 0xFF424242)),
]

func colorsForLevel(_ level: Int) -> [ColorDef] {
    level >= 3 ? level3Colors : level >= 2 ? level2Colors : level1Colors
}
