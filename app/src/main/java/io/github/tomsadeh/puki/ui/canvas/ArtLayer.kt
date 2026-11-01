package io.github.tomsadeh.puki.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.io.ByteArrayOutputStream

/** A stroke of paint, or of the eraser ([erase]), which rubs out what's under it. */
data class DrawPath(val path: List<Offset>, val color: Color, val strokeWidth: Float, val erase: Boolean = false) {
    /** The smoothed outline, built once (finished strokes never change), not on every frame. */
    val smooth: Path by lazy {
        // Through the midpoints, so lines look painted, not jagged
        Path().apply {
            moveTo(path[0].x, path[0].y)
            for (i in 1 until path.size - 1) {
                val mid = (path[i] + path[i + 1]) / 2f
                quadraticBezierTo(path[i].x, path[i].y, mid.x, mid.y)
            }
            lineTo(path.last().x, path.last().y)
        }
    }
}

/** A sticker stamped on the paper. */
data class Stamp(val icon: Int, val center: Offset, val size: Float, val rotation: Float)

/** How many points the stroke being drawn holds before it goes into the paint (see [ArtLayer]). */
internal const val LIVE_POINTS = 64

/** Past this many, the oldest stamps are stuck down into the paint, so they stop costing a redraw. */
internal const val LOOSE_STAMPS = 40

/**
 * The drawing's paint as two bitmaps the size of the paper. Fills are redrawn whole from the
 * page's shapes when one changes; strokes are only ever added to, once each, so a long drawing
 * costs no more than a short one. The eraser clears both. [version] changes with every change,
 * to redraw what shows them.
 */
class ArtLayer {
    var fills: ImageBitmap? = null
        private set
    var strokes: ImageBitmap? = null
        private set
    var version by mutableIntStateOf(0)
        private set

    /** New, empty bitmaps if the paper isn't [size]; otherwise what's there stays. */
    fun ensure(size: IntSize) {
        if (size.width <= 0 || size.height <= 0 || (strokes?.width == size.width && strokes?.height == size.height)) return
        fills = ImageBitmap(size.width, size.height)
        strokes = ImageBitmap(size.width, size.height)
        version++
    }

    /** Replaces the fills with what [paint] draws. */
    fun paintFills(density: Density, paint: DrawScope.() -> Unit) {
        val b = fills ?: return
        b.asAndroidBitmap().eraseColor(0)
        draw(b, density, paint)
        version++
    }

    /** Draws [paint] onto the strokes (and, for the eraser, onto the fills as well). */
    fun paint(density: Density, erase: Boolean = false, paint: DrawScope.() -> Unit) {
        strokes?.let { draw(it, density, paint) }
        if (erase) fills?.let { draw(it, density, paint) }
        version++
    }

    fun clearStrokes() {
        strokes?.asAndroidBitmap()?.eraseColor(0)
        version++
    }

    private fun draw(b: ImageBitmap, density: Density, paint: DrawScope.() -> Unit) {
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(b),
            Size(b.width.toFloat(), b.height.toFloat())) { paint() }
    }
}

internal fun DrawScope.drawStroke(stroke: DrawPath) {
    val pts = stroke.path
    if (pts.isEmpty()) return
    // The eraser clears its own layer (the artwork's), so the paper underneath shows again
    val blend = if (stroke.erase) BlendMode.Clear else DrawScope.DefaultBlendMode
    if (pts.size == 1) {
        drawCircle(stroke.color, stroke.strokeWidth / 2, pts[0], blendMode = blend)
        return
    }
    drawPath(stroke.smooth, stroke.color, style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}

internal fun DrawScope.drawStamp(stamp: Stamp, painter: Painter, grow: Float = 1f) {
    translate(stamp.center.x - stamp.size / 2, stamp.center.y - stamp.size / 2) {
        val pivot = Offset(stamp.size / 2, stamp.size / 2)
        rotate(stamp.rotation, pivot = pivot) {
            scale(grow, grow, pivot = pivot) { with(painter) { draw(Size(stamp.size, stamp.size)) } }
        }
    }
}

/** The paint (from [art]), the page's lines over it, and the stickers on top; [pop] is the newest one's size. */
internal fun DrawScope.drawArtwork(
    art: ArtLayer,
    pageShapes: List<Path>,
    stamps: List<Stamp>,
    painters: Map<Int, Painter>,
    pop: Float = 1f
) {
    art.version  // read, so this redraws when the paint changes
    art.fills?.let { drawImage(it) }
    art.strokes?.let { drawImage(it) }
    drawPageLines(pageShapes)
    stamps.forEachIndexed { i, stamp ->
        val painter = painters[stamp.icon] ?: return@forEachIndexed
        drawStamp(stamp, painter, if (i == stamps.lastIndex) pop else 1f)
    }
}

/**
 * The drawing on white paper, scaled to at most [maxSide] px (640 is plenty for Gemma to see it):
 * [paper] under the artwork, which the eraser doesn't clear, and [over] on top of it.
 */
internal fun snapshot(
    size: IntSize,
    density: Density,
    maxSide: Int = 640,
    paper: DrawScope.() -> Unit = {},
    over: DrawScope.() -> Unit = {},
    artwork: DrawScope.() -> Unit
): ImageBitmap {
    val factor = minOf(1f, maxSide.toFloat() / maxOf(size.width, size.height))
    val w = (size.width * factor).toInt()
    val h = (size.height * factor).toInt()
    // The artwork on its own clear layer first (the eraser clears it), then onto the paper
    val art = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(art), Size(w.toFloat(), h.toFloat())) {
        scale(factor, factor, pivot = Offset.Zero) { artwork() }
    }
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
        drawRect(Color.White)
        scale(factor, factor, pivot = Offset.Zero) { paper() }
        drawImage(art)
        scale(factor, factor, pivot = Offset.Zero) { over() }
    }
    return bitmap
}

internal fun ImageBitmap.toPng(): ByteArray = ByteArrayOutputStream().also {
    asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
}.toByteArray()
