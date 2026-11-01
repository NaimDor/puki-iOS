package io.github.tomsadeh.puki.data.content

import androidx.compose.ui.graphics.Color
import io.github.tomsadeh.puki.data.models.BilingualText

data class ShapeDef(
    val id: String,
    val name: BilingualText,
    val color: Color,
    val sides: Int
)

val level1Shapes = listOf(
    ShapeDef("circle", BilingualText("עיגול", "Circle"), Color(0xFFEF5350), 0),
    ShapeDef("square", BilingualText("ריבוע", "Square"), Color(0xFF42A5F5), 4),
    ShapeDef("triangle", BilingualText("משולש", "Triangle"), Color(0xFFFFEE58), 3),
    ShapeDef("star", BilingualText("כוכב", "Star"), Color(0xFFAB47BC), 5)
)

val level2Shapes = level1Shapes + listOf(
    ShapeDef("heart", BilingualText("לב", "Heart"), Color(0xFFE91E63), 0),
    ShapeDef("diamond", BilingualText("מעוין", "Diamond"), Color(0xFF00BCD4), 4),
    ShapeDef("hexagon", BilingualText("משושה", "Hexagon"), Color(0xFFFF9800), 6)
)

val level3Shapes = level2Shapes + listOf(
    ShapeDef("pentagon", BilingualText("מחומש", "Pentagon"), Color(0xFF4CAF50), 5),
    ShapeDef("octagon", BilingualText("מתומן", "Octagon"), Color(0xFF607D8B), 8)
)

/** A place for a shape in a picture: its center and size, as fractions of the picture. */
data class ShapeSlot(val shapeId: String, val x: Float, val y: Float, val size: Float)

/** A picture built from shapes, one a round. */
enum class ShapePicture(val slots: List<ShapeSlot>) {
    HOUSE(listOf(ShapeSlot("square", 0.46f, 0.66f, 0.52f), ShapeSlot("triangle", 0.46f, 0.24f, 0.62f), ShapeSlot("circle", 0.46f, 0.64f, 0.2f), ShapeSlot("star", 0.88f, 0.16f, 0.22f))),
    CAR(listOf(ShapeSlot("square", 0.5f, 0.34f, 0.26f), ShapeSlot("circle", 0.27f, 0.76f, 0.26f), ShapeSlot("star", 0.5f, 0.58f, 0.2f), ShapeSlot("circle", 0.73f, 0.76f, 0.26f))),
    ROCKET(listOf(ShapeSlot("triangle", 0.5f, 0.14f, 0.38f), ShapeSlot("circle", 0.5f, 0.4f, 0.22f), ShapeSlot("star", 0.5f, 0.62f, 0.2f), ShapeSlot("square", 0.5f, 0.85f, 0.2f)))
}

fun shapesForLevel(level: Int): List<ShapeDef> = when {
    level >= 3 -> level3Shapes
    level >= 2 -> level2Shapes
    else -> level1Shapes
}
