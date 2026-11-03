import SwiftUI

struct ShapeDef: Hashable {
    let id: String
    let name: BilingualText
    let color: Color
    let sides: Int
    init(_ id: String, _ name: BilingualText, _ color: Color, _ sides: Int) {
        self.id = id; self.name = name; self.color = color; self.sides = sides
    }
}

let level1Shapes = [
    ShapeDef("circle", BilingualText("עיגול", "Circle"), Color(argb: 0xFFEF5350), 0),
    ShapeDef("square", BilingualText("ריבוע", "Square"), Color(argb: 0xFF42A5F5), 4),
    ShapeDef("triangle", BilingualText("משולש", "Triangle"), Color(argb: 0xFFFFEE58), 3),
    ShapeDef("star", BilingualText("כוכב", "Star"), Color(argb: 0xFFAB47BC), 5),
]

let level2Shapes = level1Shapes + [
    ShapeDef("heart", BilingualText("לב", "Heart"), Color(argb: 0xFFE91E63), 0),
    ShapeDef("diamond", BilingualText("מעוין", "Diamond"), Color(argb: 0xFF00BCD4), 4),
    ShapeDef("hexagon", BilingualText("משושה", "Hexagon"), Color(argb: 0xFFFF9800), 6),
]

let level3Shapes = level2Shapes + [
    ShapeDef("pentagon", BilingualText("מחומש", "Pentagon"), Color(argb: 0xFF4CAF50), 5),
    ShapeDef("octagon", BilingualText("מתומן", "Octagon"), Color(argb: 0xFF607D8B), 8),
]

/** A place for a shape in a picture: its center and size, as fractions of the picture. */
struct ShapeSlot: Hashable {
    let shapeId: String
    let x: CGFloat, y: CGFloat, size: CGFloat
    init(_ shapeId: String, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat) { self.shapeId = shapeId; self.x = x; self.y = y; self.size = size }
}

/** A picture built from shapes, one a round. */
enum ShapePicture: CaseIterable {
    case house, car, rocket
    var slots: [ShapeSlot] {
        switch self {
        case .house: return [ShapeSlot("square", 0.46, 0.66, 0.52), ShapeSlot("triangle", 0.46, 0.24, 0.62), ShapeSlot("circle", 0.46, 0.64, 0.2), ShapeSlot("star", 0.88, 0.16, 0.22)]
        case .car: return [ShapeSlot("square", 0.5, 0.34, 0.26), ShapeSlot("circle", 0.27, 0.76, 0.26), ShapeSlot("star", 0.5, 0.58, 0.2), ShapeSlot("circle", 0.73, 0.76, 0.26)]
        case .rocket: return [ShapeSlot("triangle", 0.5, 0.14, 0.38), ShapeSlot("circle", 0.5, 0.4, 0.22), ShapeSlot("star", 0.5, 0.62, 0.2), ShapeSlot("square", 0.5, 0.85, 0.2)]
        }
    }
}

func shapesForLevel(_ level: Int) -> [ShapeDef] {
    level >= 3 ? level3Shapes : level >= 2 ? level2Shapes : level1Shapes
}
