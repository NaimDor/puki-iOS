package io.github.tomsadeh.puki.ui.canvas

import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.tomsadeh.puki.data.content.englishLetters
import io.github.tomsadeh.puki.data.content.hebrewLetters
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import io.github.tomsadeh.puki.ui.common.LocalVoicePlayer
import io.github.tomsadeh.puki.ui.components.ConfettiOverlay
import kotlinx.coroutines.delay

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.translate

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.audio.CompanionStatus
import io.github.tomsadeh.puki.audio.Sfx
import io.github.tomsadeh.puki.theme.EarthBrown
import io.github.tomsadeh.puki.ui.common.LocalCompanion
import io.github.tomsadeh.puki.ui.common.LocalSfxPlayer
import io.github.tomsadeh.puki.ui.components.BackButton
import io.github.tomsadeh.puki.ui.components.idleBob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import kotlin.math.sin

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

// Six pots on the left, three more under the brushes on the right
private val leftColors = listOf(
    Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFFFFEE58),
    Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFFFF9800)
)
private val rightColors = listOf(Color(0xFFF48FB1), Color(0xFF8D6E63), Color(0xFF424242))

/** Thin and fat brushes, as stroke widths in dp. */
private val brushWidths = listOf(10f, 28f)

/** The eraser is fatter than the fat brush: rubbing out is for big mistakes. */
private const val EraserDp = 44f

/** The stamp tool's stickers: each tap puts down the next one. */
private val stampIcons = listOf(
    R.drawable.ic_sticker_star, R.drawable.ic_sticker_heart, R.drawable.ic_sticker_flower,
    R.drawable.ic_sticker_butterfly, R.drawable.ic_stamp_ladybug, R.drawable.ic_count_apple,
    R.drawable.ic_sticker_rainbow, R.drawable.ic_sticker_sun, R.drawable.ic_sticker_moon,
    R.drawable.ic_sticker_unicorn, R.drawable.ic_count_bird, R.drawable.ic_sticker_rocket
)

/** A sticker stamped on the paper. */
data class Stamp(val icon: Int, val center: Offset, val size: Float, val rotation: Float)

private val PaperColor = Color(0xFFFFFDF6)
private val Highlight = Color(0xFFFFD54F)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreeCanvasScreen(onBack: () -> Unit) {
    var selectedColor by remember { mutableStateOf(leftColors[0]) }
    var brushDp by remember { mutableStateOf(brushWidths[1]) }
    var stamping by remember { mutableStateOf(false) }  // the stamp tool instead of a brush
    var erasing by remember { mutableStateOf(false) }  // the eraser instead of a brush
    val paths = remember { mutableStateListOf<DrawPath>() }
    val stamps = remember { mutableStateListOf<Stamp>() }
    var nextStamp by remember { mutableIntStateOf(0) }
    val stampOrder = remember { stampIcons.shuffled() }
    val stampPainters = stampIcons.associateWith { painterResource(it) }
    var pageIndex by remember { mutableIntStateOf(0) }  // 0: blank paper
    val fills = remember { mutableStateMapOf<Int, Color>() }
    val currentPath = remember { mutableStateListOf<Offset>() }  // grows in place as the finger moves
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val brushSize = with(density) { (if (erasing) EraserDp else brushDp).dp.toPx() }
    val stampSize = with(density) { 96.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val paperSize = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
    val pageShapes = remember(pageIndex, canvasSize) {
        (coloringPages[pageIndex] as? ColoringPage)?.placed(paperSize) ?: emptyList()
    }
    // The paint (the page's fills and the finished strokes) lives in one bitmap: a new stroke is
    // drawn into it once, the eraser clears it, and the screen just shows it
    val art = remember { ArtLayer() }
    fun repaint() {
        art.reset(canvasSize, density) {
            drawPageFills(pageShapes, fills, PaperColor)
            paths.forEach { drawStroke(it) }
        }
    }
    LaunchedEffect(canvasSize, pageShapes) { if (canvasSize.width > 0) repaint() }
    fun addStroke(stroke: DrawPath) {
        paths.add(stroke)
        art.paint(density) { drawStroke(stroke) }
    }

    // Tracing: the next letter (or number) each time its page comes round
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val tracePage = coloringPages[pageIndex] as? TracePage
    var letterTurn by remember { mutableIntStateOf(-1) }
    var numberTurn by remember { mutableIntStateOf(-1) }
    val letters = if (language == Language.ENGLISH) englishLetters else hebrewLetters
    val traceText = when {
        tracePage == null -> null
        tracePage.numbers -> "${numberTurn.coerceAtLeast(0) % 9 + 1}"
        else -> letters[letterTurn.coerceAtLeast(0) % letters.size].let { if (language == Language.ENGLISH) it.englishLetter else it.hebrewLetter }
    }
    val traceGlyph = remember(traceText, canvasSize) { if (traceText == null || canvasSize.width == 0) null else glyphPath(traceText, paperSize) }
    val traceStep = with(density) { 14.dp.toPx() }
    val traceSamples = remember(traceGlyph) { traceGlyph?.let { samplesInside(it, traceStep) } ?: emptyList() }
    val covered = remember(traceGlyph) { BooleanArray(traceSamples.size) }
    var traced by remember(traceGlyph) { mutableStateOf(false) }
    var tracedParty by remember { mutableStateOf(false) }
    // After each stroke: which bits of the letter it painted, and the party once most of it is
    LaunchedEffect(paths.size, traceGlyph) {
        if (traceGlyph == null || traced) return@LaunchedEffect
        if (paths.isEmpty()) { covered.fill(false); return@LaunchedEffect }
        val stroke = paths.last()
        if (stroke.erase) return@LaunchedEffect
        val reach = stroke.strokeWidth / 2 + traceStep * 0.6f
        traceSamples.forEachIndexed { i, s ->
            if (!covered[i] && stroke.path.any { (it - s).getDistance() < reach }) covered[i] = true
        }
        if (traceSamples.isNotEmpty() && covered.count { it } >= traceSamples.size * 0.6f) {
            traced = true
            sfxPlayer.play(Sfx.SUCCESS)
            tracedParty = true
            val page = tracePage
            voicePlayer.play(
                if (page?.numbers == true) "voice_number_${numberTurn.coerceAtLeast(0) % 9 + 1}"
                else "voice_letter_${letters[letterTurn.coerceAtLeast(0) % letters.size].id}",
                language
            )
            delay(2200)
            tracedParty = false
        }
    }

    fun stampAt(offset: Offset) {
        val icon = stampOrder[nextStamp % stampOrder.size]
        nextStamp++
        stamps.add(Stamp(icon, offset, stampSize, (offset.x * 7 + offset.y * 13) % 30f - 15f))
        sfxPlayer.play(Sfx.POP, 0.7f)
    }

    /** The eraser at [at] lifts off any sticker it touches (they sit on top of the paint). */
    fun eraseStampsAt(at: Offset) {
        stamps.removeAll { (it.center - at).getDistance() < it.size * 0.45f + brushSize / 2 }
    }

    fun wipe() {
        paths.clear()
        stamps.clear()
        fills.clear()
        currentPath.clear()
        repaint()
        sfxPlayer.play(Sfx.WHOOSH)
    }
    var flash by remember { mutableStateOf(false) }  // a camera flash over the paper when it's saved
    var looking by remember { mutableStateOf(false) }
    var lastLooked by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { companion?.wake() }

    fun pickColor(color: Color) {
        selectedColor = color
        erasing = false  // a pot of paint means painting again
        sfxPlayer.play(Sfx.POP, 0.6f)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Pots and tool plates sized so each side column fits: a round button and seven of them
        val unit = ((maxHeight - 32.dp - 64.dp) / 7.6f).coerceIn(48.dp, 92.dp)
        Canvas(modifier = Modifier.fillMaxSize()) { drawStudioWall() }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left: back, the paint pots, and the camera that saves the drawing
            Column(
                modifier = Modifier.width(136.dp).fillMaxHeight().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                BackButton(onBack)
                leftColors.forEachIndexed { i, color ->
                    PaintPot(color = color, selected = color == selectedColor, index = i, height = unit, onClick = { pickColor(color) })
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF81D4FA))
                        .clickable {
                            if (canvasSize.width == 0 || flash) return@clickable
                            flash = true
                            sfxPlayer.play(Sfx.SUCCESS, 0.7f)
                            scope.launch {
                                // As it looks on the easel: the paper and the letter to trace under the artwork
                                val bitmap = snapshot(canvasSize, density, maxSide = 2048, paper = {
                                    traceGlyph?.let { drawTraceGlyph(it) }
                                }, over = { traceGlyph?.let { drawTraceEdge(it) } }) {
                                    drawArtwork(art, pageShapes, stamps, stampPainters)
                                }
                                withContext(Dispatchers.IO) { saveDrawing(context, bitmap.toPng()) }
                                delay(150)
                                flash = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { Text("📸", fontSize = 30.sp) }
            }

            // The easel with a sheet of paper taped to it
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 30.dp, bottom = 22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(10.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFC8935B), Color(0xFFAE7642))))
                        .padding(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(4.dp, RoundedCornerShape(6.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(PaperColor)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(selectedColor, brushSize, stamping, erasing, pageShapes) {
                                // A tap stamps a sticker, rubs out a spot, fills the shape it lands in, or leaves a dot of paint
                                detectTapGestures { offset ->
                                    val shape = shapeAt(pageShapes, offset)
                                    when {
                                        stamping -> stampAt(offset)
                                        erasing -> {
                                            eraseStampsAt(offset)
                                            addStroke(DrawPath(listOf(offset), PaperColor, brushSize, erase = true))
                                        }
                                        shape >= 0 -> {
                                            fills[shape] = selectedColor
                                            repaint()  // the fill goes under the strokes already there
                                            sfxPlayer.play(Sfx.POP, 0.8f)
                                        }
                                        else -> addStroke(DrawPath(listOf(offset), selectedColor, brushSize))
                                    }
                                }
                            }
                            .pointerInput(selectedColor, brushSize, stamping, erasing) {
                                var lastStamp = Offset.Zero
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (stamping) {
                                            stampAt(offset)
                                            lastStamp = offset
                                        } else {
                                            currentPath.clear(); currentPath.add(offset)
                                            if (erasing) {
                                                eraseStampsAt(offset)
                                                art.paint(density) { drawStroke(DrawPath(listOf(offset), PaperColor, brushSize, erase = true)) }
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (stamping) {
                                            // A trail of stickers, a little apart
                                            if ((change.position - lastStamp).getDistance() > stampSize * 0.8f) {
                                                stampAt(change.position)
                                                lastStamp = change.position
                                            }
                                        } else if (erasing) {
                                            // Rubbed straight out of the paint, as the finger moves
                                            eraseStampsAt(change.position)
                                            val from = currentPath.lastOrNull() ?: change.position
                                            currentPath.add(change.position)
                                            art.paint(density) { drawStroke(DrawPath(listOf(from, change.position), PaperColor, brushSize, erase = true)) }
                                        } else {
                                            currentPath.add(change.position)
                                            // A long scribble goes into the paint in pieces, so the live stroke stays short
                                            if (currentPath.size >= LIVE_POINTS) {
                                                val last = currentPath.last()
                                                addStroke(DrawPath(currentPath.toList(), selectedColor, brushSize))
                                                currentPath.clear(); currentPath.add(last)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (erasing) {
                                            if (currentPath.isNotEmpty()) paths.add(DrawPath(currentPath.toList(), PaperColor, brushSize, erase = true))
                                        } else if (currentPath.size > 1) {
                                            addStroke(DrawPath(currentPath.toList(), selectedColor, brushSize))
                                        }
                                        currentPath.clear()
                                    }
                                )
                            }
                    ) {
                        // The paper, with the letter to trace
                        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                            drawPaperGrain()
                            traceGlyph?.let { drawTraceGlyph(it) }
                        }
                        // The paint, the page's lines and the stickers, in a layer of their own
                        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                            drawArtwork(art, pageShapes, stamps, stampPainters)
                        }
                        // The stroke being drawn right now (the only thing redrawn as the finger moves)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (!erasing) drawStroke(DrawPath(currentPath, selectedColor, brushSize))
                            traceGlyph?.let { drawTraceEdge(it) }
                        }
                        // The camera flash when the drawing is saved
                        val flashAlpha by animateFloatAsState(if (flash) 0.85f else 0f, tween(if (flash) 80 else 500), label = "flash")
                        if (flashAlpha > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
                    }
                }
                // Strips of tape holding the paper's corners
                Canvas(modifier = Modifier.fillMaxSize()) { drawTape() }

                // פוקי looks at the drawing: on tap, or by itself after a pause in drawing
                val drawn = paths.size + stamps.size + fills.size
                val companionStatus = companion?.status?.collectAsStateWithLifecycle()?.value
                fun lookNow() {
                    if (companion == null || looking || canvasSize.width == 0) return
                    looking = true
                    lastLooked = drawn
                    scope.launch {
                        // Drawn here (the stickers' painters belong to this thread), compressed in the background
                        val bitmap = snapshot(canvasSize, density) { drawArtwork(art, pageShapes, stamps, stampPainters) }
                        val png = withContext(Dispatchers.Default) { bitmap.toPng() }
                        companion.look(png)
                        looking = false
                    }
                }
                LaunchedEffect(drawn, companionStatus) {
                    if (drawn > 0 && drawn != lastLooked && companionStatus == CompanionStatus.READY) {
                        delay(7000)
                        lookNow()
                    }
                }
                if (companionStatus == CompanionStatus.READY && drawn > 0) {
                    val bob by rememberInfiniteTransition(label = "look_bob").animateFloat(
                        initialValue = 1f, targetValue = if (looking) 1.12f else 1.04f,
                        animationSpec = infiniteRepeatable(tween(if (looking) 300 else 1200), RepeatMode.Reverse), label = "bob"
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-24).dp)
                            .graphicsLayer { scaleX = bob; scaleY = bob }
                            .size(84.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF8E1))
                            .clickable { lookNow() },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painter = painterResource(R.drawable.ic_companion_parrot), contentDescription = null, modifier = Modifier.size(70.dp))
                    }
                }
            }

            // Right: wipe the paper clean, the next page, brushes, stamps, the eraser and three more pots
            Column(
                modifier = Modifier.width(136.dp).fillMaxHeight().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFFFF8A80))
                            .clickable { wipe() },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painter = painterResource(R.drawable.ic_stamp_trash), contentDescription = null, modifier = Modifier.size(36.dp))
                    }
                    // The next page to color in (its paint goes with it; the strokes stay)
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(6.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaperColor)
                            .border(3.dp, Highlight, RoundedCornerShape(12.dp))
                            .clickable {
                                pageIndex = (pageIndex + 1) % coloringPages.size
                                (coloringPages[pageIndex] as? TracePage)?.let { if (it.numbers) numberTurn++ else letterTurn++ }
                                if (coloringPages[pageIndex] is TracePage) paths.clear()  // a clean sheet to trace on
                                fills.clear()
                                repaint()
                                sfxPlayer.play(Sfx.WHOOSH, 0.6f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (val next = coloringPages[(pageIndex + 1) % coloringPages.size]) {
                            is ColoringPage -> Canvas(modifier = Modifier.size(48.dp)) { drawPageLines(next.placed(size)) }
                            is TracePage -> Text(
                                if (next.numbers) "1" else if (language == Language.ENGLISH) "A" else "א",
                                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9575CD)
                            )
                            null -> {}
                        }
                    }
                }
                brushWidths.forEachIndexed { i, width ->
                    ToolPlate(selected = !stamping && !erasing && brushDp == width, index = i, size = unit, onClick = {
                        brushDp = width
                        stamping = false
                        erasing = false
                        sfxPlayer.play(Sfx.POP, 0.6f)
                    }) {
                        Canvas(modifier = Modifier.size(unit * 0.84f)) { drawBrush(selectedColor, fat = i == 1) }
                    }
                }
                ToolPlate(selected = stamping, index = 2, size = unit, onClick = {
                    stamping = true
                    erasing = false
                    sfxPlayer.play(Sfx.POP, 0.6f)
                }) {
                    Image(painter = painterResource(stampOrder[nextStamp % stampOrder.size]), contentDescription = null, modifier = Modifier.size(unit * 0.64f))
                }
                ToolPlate(selected = erasing, index = 3, size = unit, onClick = {
                    erasing = true
                    stamping = false
                    sfxPlayer.play(Sfx.POP, 0.6f)
                }) {
                    Canvas(modifier = Modifier.size(unit * 0.7f)) { drawEraser() }
                }
                rightColors.forEachIndexed { i, color ->
                    PaintPot(color = color, selected = color == selectedColor, index = i + 7, height = unit, onClick = { pickColor(color) })
                }
            }
        }
        ConfettiOverlay(show = tracedParty)
    }
}

/** A pot of paint. The chosen one lifts up, wobbles and has a brush standing in it. */
@Composable
private fun PaintPot(color: Color, selected: Boolean, index: Int, height: Dp, onClick: () -> Unit) {
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "pot_lift"
    )
    Canvas(
        modifier = Modifier
            .idleBob(index, amount = if (selected) 1.4f else 0.4f)
            .graphicsLayer {
                translationY = -lift * 10.dp.toPx()
                scaleX = 1f + lift * 0.14f
                scaleY = 1f + lift * 0.14f
            }
            .size(width = height * (100f / 92f), height = height)
            .clickable(onClick = onClick)
    ) { drawPaintPot(color, lift) }
}

/** A round plate for a brush; the chosen one is ringed in yellow and lifted. */
@Composable
private fun ToolPlate(selected: Boolean, index: Int, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "plate_lift"
    )
    // The spring overshoots; colours must stay within 0..1
    val shown = lift.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .idleBob(index + 9, amount = if (selected) 1.2f else 0.3f)
            .graphicsLayer {
                scaleX = 1f + lift * 0.12f
                scaleY = 1f + lift * 0.12f
            }
            .size(size)
            .shadow(if (selected) 10.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.6f + 0.4f * shown))
            .border(6.dp, Highlight.copy(alpha = shown), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ---------- Drawing the studio ----------

/** A warm wall with soft polka dots behind the easel. */
private fun DrawScope.drawStudioWall() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF1DC), Color(0xFFFFE2BF))))
    val dot = Color(0x22FF8A65)
    val step = 70.dp.toPx()
    var row = 0
    var y = step / 2
    while (y < size.height) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < size.width) {
            drawCircle(dot, 6.dp.toPx(), Offset(x, y))
            x += step
        }
        y += step * 0.75f
        row++
    }
    // Floorboards along the bottom
    val floorTop = size.height * 0.93f
    drawRect(Color(0xFFD7A86E), Offset(0f, floorTop), Size(size.width, size.height - floorTop))
    drawRect(Color(0xFFBF8A55), Offset(0f, floorTop), Size(size.width, 4.dp.toPx()))
}

/** Very faint fibres so the page reads as paper rather than a white screen. */
private fun DrawScope.drawPaperGrain() {
    val fibre = Color(0x068D6E63)
    val step = 22.dp.toPx()
    var y = step
    var i = 0
    while (y < size.height) {
        val wobble = sin(i * 1.7f) * 3.dp.toPx()
        drawLine(fibre, Offset(0f, y + wobble), Offset(size.width, y - wobble), 1.dp.toPx())
        y += step
        i++
    }
}

private fun DrawScope.drawTape() {
    val tape = Color(0xAAFFF59D)
    val w = 110.dp.toPx()
    val h = 34.dp.toPx()
    listOf(
        Triple(Offset(30.dp.toPx(), 26.dp.toPx()), -35f, tape),
        Triple(Offset(size.width - 30.dp.toPx(), 26.dp.toPx()), 35f, Color(0xAAB3E5FC)),
        Triple(Offset(30.dp.toPx(), size.height - 26.dp.toPx()), 35f, Color(0xAAF8BBD0)),
        Triple(Offset(size.width - 30.dp.toPx(), size.height - 26.dp.toPx()), -35f, tape)
    ).forEach { (c, angle, color) ->
        rotate(angle, pivot = c) {
            drawRect(color, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
            // Zig-zag torn ends
            for (end in listOf(-1f, 1f)) {
                val x = c.x + end * w / 2
                var y = c.y - h / 2
                val tooth = h / 5
                while (y < c.y + h / 2 - 1f) {
                    val tri = Path().apply {
                        moveTo(x, y); lineTo(x + end * tooth * 0.6f, y + tooth / 2); lineTo(x, y + tooth); close()
                    }
                    drawPath(tri, color)
                    y += tooth
                }
            }
        }
    }
}

private fun DrawScope.drawPaintPot(color: Color, lift: Float) {
    val w = size.width
    val h = size.height
    val dark = color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f)
    // Shadow on the "table"
    drawOval(Color(0x33000000), Offset(w * 0.2f, h * 0.88f), Size(w * 0.6f, h * 0.1f))

    // A brush standing in the chosen pot
    if (lift > 0.01f) {
        rotate(18f, pivot = Offset(w * 0.5f, h * 0.45f)) {
            val top = h * (0.42f - 0.42f * lift)
            drawLine(Color(0xFFA1683A), Offset(w * 0.5f, top), Offset(w * 0.5f, h * 0.5f), w * 0.07f, StrokeCap.Round)
            drawRoundRect(Color(0xFFB0BEC5), Offset(w * 0.46f, h * 0.38f), Size(w * 0.08f, h * 0.1f), CornerRadius(3f))
        }
    }

    // Pot body: a white ceramic jar with paint dribbling down and a coloured band
    val bodyTop = h * 0.36f
    val bodyLeft = w * 0.18f
    val bodyW = w * 0.64f
    drawRoundRect(Color(0xFFFAFAFA), Offset(bodyLeft, bodyTop), Size(bodyW, h * 0.54f), CornerRadius(w * 0.12f))
    // Paint pooled at the bottom, and drips running down from the rim
    drawRoundRect(color.copy(alpha = 0.35f), Offset(bodyLeft, h * 0.76f), Size(bodyW, h * 0.14f), CornerRadius(w * 0.12f))
    listOf(0.3f to 0.3f, 0.5f to 0.4f, 0.68f to 0.2f).forEach { (fx, len) ->
        val x = w * fx
        drawLine(color, Offset(x, bodyTop), Offset(x, bodyTop + h * len), w * 0.07f, StrokeCap.Round)
        drawCircle(color, w * 0.05f, Offset(x, bodyTop + h * len))
    }
    // Rim and the paint inside
    drawOval(Color(0xFFE0E0E0), Offset(w * 0.14f, h * 0.27f), Size(w * 0.72f, h * 0.18f))
    drawOval(color, Offset(w * 0.19f, h * 0.29f), Size(w * 0.62f, h * 0.13f))
    drawOval(dark.copy(alpha = 0.35f), Offset(w * 0.3f, h * 0.33f), Size(w * 0.4f, h * 0.06f))
    // Shine
    drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(bodyLeft + w * 0.06f, h * 0.48f), Size(w * 0.06f, h * 0.3f), CornerRadius(w * 0.03f))
}

/** A paintbrush lying diagonally, its tip dipped in [color]. */
private fun DrawScope.drawBrush(color: Color, fat: Boolean) {
    val w = size.width
    val h = size.height
    rotate(-45f, pivot = Offset(w / 2, h / 2)) {
        val cx = w / 2
        val handleW = if (fat) w * 0.13f else w * 0.09f
        drawLine(Color(0xFFA1683A), Offset(cx, h * 0.92f), Offset(cx, h * 0.5f), handleW, StrokeCap.Round)
        val ferruleW = if (fat) w * 0.22f else w * 0.13f
        drawRoundRect(Color(0xFFB0BEC5), Offset(cx - ferruleW / 2, h * 0.38f), Size(ferruleW, h * 0.14f), CornerRadius(4f))
        val tipW = if (fat) w * 0.3f else w * 0.15f
        val tip = Path().apply {
            moveTo(cx - tipW / 2, h * 0.39f)
            cubicTo(cx - tipW / 2, h * 0.2f, cx, h * 0.12f, cx, h * 0.04f)
            cubicTo(cx, h * 0.12f, cx + tipW / 2, h * 0.2f, cx + tipW / 2, h * 0.39f)
            close()
        }
        drawPath(tip, color)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - tipW * 0.15f, h * 0.34f), Offset(cx - tipW * 0.05f, h * 0.16f), w * 0.025f, StrokeCap.Round)
    }
}

/** A pink rubber eraser with a blue paper sleeve, lying at a slant. */
private fun DrawScope.drawEraser() {
    val w = size.width
    val h = size.height
    rotate(-30f, pivot = Offset(w / 2, h / 2)) {
        val top = h * 0.3f
        val bodyH = h * 0.4f
        drawRoundRect(Color(0xFFF48FB1), Offset(w * 0.08f, top), Size(w * 0.84f, bodyH), CornerRadius(w * 0.08f))
        drawRect(Color(0xFF64B5F6), Offset(w * 0.5f, top), Size(w * 0.3f, bodyH))
        drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(w * 0.14f, top + bodyH * 0.15f), Size(w * 0.28f, bodyH * 0.18f), CornerRadius(w * 0.04f))
    }
}

// ---------- The artwork ----------

private fun DrawScope.drawStroke(stroke: DrawPath) {
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

/** The paint (fills and strokes, from [art]), the page's lines over it, and the stickers on top. */
private fun DrawScope.drawArtwork(
    art: ArtLayer,
    pageShapes: List<androidx.compose.ui.graphics.Path>,
    stamps: List<Stamp>,
    painters: Map<Int, Painter>
) {
    art.version  // read, so this redraws when the paint changes
    art.bitmap?.let { drawImage(it) }
    drawPageLines(pageShapes)
    stamps.forEach { stamp ->
        val painter = painters[stamp.icon] ?: return@forEach
        translate(stamp.center.x - stamp.size / 2, stamp.center.y - stamp.size / 2) {
            rotate(stamp.rotation, pivot = Offset(stamp.size / 2, stamp.size / 2)) {
                with(painter) { draw(Size(stamp.size, stamp.size)) }
            }
        }
    }
}

/** How many points the stroke being drawn holds before it goes into the paint (see [ArtLayer]). */
private const val LIVE_POINTS = 64

/**
 * The drawing's paint as a bitmap the size of the paper: strokes are drawn into it once (and the
 * eraser clears it with BlendMode.Clear), so the screen shows one image however long the drawing
 * gets. [version] changes with every stroke, to redraw what shows it.
 */
private class ArtLayer {
    var bitmap: ImageBitmap? = null
        private set
    var version by mutableIntStateOf(0)
        private set

    /** A new, empty bitmap of [size], with [paint] drawn on it. */
    fun reset(size: IntSize, density: Density, paint: DrawScope.() -> Unit) {
        if (size.width <= 0 || size.height <= 0) return
        bitmap = ImageBitmap(size.width, size.height)
        paint(density, paint)
    }

    /** Draws [paint] onto the bitmap. */
    fun paint(density: Density, paint: DrawScope.() -> Unit) {
        val b = bitmap ?: return
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(b),
            Size(b.width.toFloat(), b.height.toFloat())) { paint() }
        version++
    }
}

/**
 * The drawing on white paper, scaled to at most [maxSide] px (640 is plenty for Gemma to see it):
 * [paper] under the artwork, which the eraser doesn't clear, and [over] on top of it.
 */
private fun snapshot(
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

private fun ImageBitmap.toPng(): ByteArray = ByteArrayOutputStream().also {
    asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
}.toByteArray()
