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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp

import androidx.compose.runtime.mutableStateMapOf

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
import kotlin.math.sin
import kotlin.math.ceil
import io.github.tomsadeh.puki.ui.components.rememberCompact
import io.github.tomsadeh.puki.ui.components.popIn
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.activity.ComponentActivity
import android.content.ContextWrapper
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
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

// Six pots on one side, three more under the brushes on the other
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

private val PaperColor = Color(0xFFFFFDF6)
private val Highlight = Color(0xFFFFD54F)

/** What the voice calls a page on the shelf. */
private fun pageVoice(page: CanvasPage?) = when (page) {
    null -> "voice_page_blank"
    is ColoringPage -> "voice_page_${page.id}"
    is TracePage -> if (page.numbers) "voice_page_numbers" else "voice_page_letters"
}

/** The drawing on the easel: it belongs to the app's visit, so leaving and coming back finds it still there. */
class DrawingViewModel : ViewModel() {
    var selectedColor by mutableStateOf(leftColors[0])
    var brushDp by mutableStateOf(brushWidths[1])
    var stamping by mutableStateOf(false)  // the stamp tool instead of a brush
    var erasing by mutableStateOf(false)  // the eraser instead of a brush
    val stamps = mutableStateListOf<Stamp>()
    var nextStamp by mutableIntStateOf(0)
    val stampOrder = stampIcons.shuffled()
    var pageIndex by mutableIntStateOf(0)  // 0: blank paper
    val fills = mutableStateMapOf<Int, Color>()
    var letterTurn by mutableIntStateOf(-1)
    var numberTurn by mutableIntStateOf(-1)
    var strokeCount by mutableIntStateOf(0)
    var lastStroke: DrawPath? = null
    var marks by mutableIntStateOf(0)  // strokes, stamps and fills so far: what Puki looks out for
    var lastLooked by mutableIntStateOf(0)
    val art = ArtLayer()

    fun noted(stroke: DrawPath) {
        lastStroke = stroke
        strokeCount++
        marks++
    }

    fun clearStrokes() {
        art.clearStrokes()
        lastStroke = null
        strokeCount = 0
    }
}

/** The activity under [context], so the drawing outlives the screen. */
private fun activityOf(context: android.content.Context): ComponentActivity =
    generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<ComponentActivity>().first()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreeCanvasScreen(onBack: () -> Unit, drawing: DrawingViewModel = viewModel(activityOf(LocalContext.current))) {
    val art = drawing.art
    val stamps = drawing.stamps
    val fills = drawing.fills
    val stampPainters = stampIcons.associateWith { painterResource(it) }
    val currentPath = remember { mutableStateListOf<Offset>() }  // grows in place as the finger moves
    val companion = LocalCompanion.current
    val sfxPlayer = LocalSfxPlayer.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val brushSize = with(density) { (if (drawing.erasing) EraserDp else drawing.brushDp).dp.toPx() }
    val stampSize = with(density) { 96.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val paperSize = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat())
    val pageShapes = remember(drawing.pageIndex, canvasSize) {
        (coloringPages[drawing.pageIndex] as? ColoringPage)?.placed(paperSize) ?: emptyList()
    }
    fun repaintFills() {
        art.paintFills(density) { drawPageFills(pageShapes, fills, PaperColor) }
    }
    LaunchedEffect(canvasSize, pageShapes) {
        if (canvasSize.width > 0) {
            art.ensure(canvasSize)
            repaintFills()
        }
    }
    fun addStroke(stroke: DrawPath) {
        art.paint(density, erase = stroke.erase) { drawStroke(stroke) }
        drawing.noted(stroke)
    }

    // Tracing: the next letter (or number) each time its page comes round
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    val tracePage = coloringPages[drawing.pageIndex] as? TracePage
    val letters = if (language == Language.ENGLISH) englishLetters else hebrewLetters
    val traceText = when {
        tracePage == null -> null
        tracePage.numbers -> "${drawing.numberTurn.coerceAtLeast(0) % 9 + 1}"
        else -> letters[drawing.letterTurn.coerceAtLeast(0) % letters.size].let { if (language == Language.ENGLISH) it.englishLetter else it.hebrewLetter }
    }
    val traceGlyph = remember(traceText, canvasSize) { if (traceText == null || canvasSize.width == 0) null else glyphPath(traceText, paperSize) }
    val traceStep = with(density) { 14.dp.toPx() }
    val traceSamples = remember(traceGlyph) { traceGlyph?.let { samplesInside(it, traceStep) } ?: emptyList() }
    val covered = remember(traceGlyph) { BooleanArray(traceSamples.size) }
    var traced by remember(traceGlyph) { mutableStateOf(false) }
    var tracedParty by remember { mutableStateOf(false) }
    // After each stroke: which bits of the letter it painted, and the party once most of it is
    LaunchedEffect(drawing.strokeCount, traceGlyph) {
        if (traceGlyph == null || traced) return@LaunchedEffect
        if (drawing.strokeCount == 0) { covered.fill(false); return@LaunchedEffect }
        val stroke = drawing.lastStroke ?: return@LaunchedEffect
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
                if (page?.numbers == true) "voice_number_${drawing.numberTurn.coerceAtLeast(0) % 9 + 1}"
                else "voice_letter_${letters[drawing.letterTurn.coerceAtLeast(0) % letters.size].id}",
                language
            )
            delay(2200)
            tracedParty = false
        }
    }

    val stampPop = remember { Animatable(1f) }
    fun stampAt(offset: Offset) {
        val icon = drawing.stampOrder[drawing.nextStamp % drawing.stampOrder.size]
        drawing.nextStamp++
        stamps.add(Stamp(icon, offset, stampSize, (offset.x * 7 + offset.y * 13) % 30f - 15f))
        drawing.marks++
        if (stamps.size > LOOSE_STAMPS) {
            val oldest = stamps.removeAt(0)
            stampPainters[oldest.icon]?.let { painter -> art.paint(density) { drawStamp(oldest, painter) } }
        }
        sfxPlayer.play(Sfx.POP, 0.7f)
        scope.launch {
            stampPop.snapTo(0.5f)
            stampPop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
        }
    }

    /** The eraser at [at] lifts off any sticker it touches (they sit on top of the paint). */
    fun eraseStampsAt(at: Offset) {
        stamps.removeAll { (it.center - at).getDistance() < it.size * 0.45f + brushSize / 2 }
    }

    // A white sweep across the paper, and the page is clean behind it
    val sweep = remember { Animatable(0f) }
    fun wipe() {
        if (sweep.isRunning) return
        sfxPlayer.play(Sfx.WHOOSH)
        scope.launch {
            sweep.animateTo(1f, tween(350))
            drawing.clearStrokes()
            drawing.marks = 0
            stamps.clear()
            fills.clear()
            currentPath.clear()
            repaintFills()
            sweep.snapTo(0f)
        }
    }

    var flash by remember { mutableStateOf(false) }  // a camera flash over the paper when it's saved
    var looking by remember { mutableStateOf(false) }
    var shelfOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        companion?.wake()
        voicePlayer.play("voice_canvas_hello", language)
    }

    fun pickColor(color: Color) {
        drawing.selectedColor = color
        drawing.erasing = false  // a pot of paint means painting again
        sfxPlayer.play(Sfx.POP, 0.6f)
    }

    /** Takes page [index] off the shelf (its paint goes with it; the strokes stay, except on a page to trace). */
    fun choosePage(index: Int) {
        shelfOpen = false
        val page = coloringPages[index]
        if (index == drawing.pageIndex && page !is TracePage) return
        drawing.pageIndex = index
        (page as? TracePage)?.let {
            if (it.numbers) drawing.numberTurn++ else drawing.letterTurn++
            drawing.clearStrokes()  // a clean sheet to trace on
        }
        fills.clear()
        repaintFills()
        sfxPlayer.play(Sfx.WHOOSH, 0.6f)
        voicePlayer.play(pageVoice(page), language)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // On a phone the pots and tools stand two by two, with the buttons side by side above them;
        // sized from the rows that's left them
        val compact = rememberCompact()
        val across = if (compact) 2 else 1
        val rows = if (compact) 4 else 7
        val buttons = if (compact) 64.dp else 128.dp
        val unit = ((maxHeight - 32.dp - buttons) / (rows * 1.06f)).coerceAtMost(92.dp)
        val sideWidth = if (compact) unit * 2.3f + 12.dp else 136.dp
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawStudioWall() }

        val saveButton: @Composable () -> Unit = {
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
            ) { Canvas(modifier = Modifier.size(46.dp)) { drawCamera() } }
        }
        val pots: List<@Composable () -> Unit> = leftColors.mapIndexed { i, color ->
            { PaintPot(color = color, selected = color == drawing.selectedColor, index = i, height = unit, onClick = { pickColor(color) }) }
        }
        val tools: List<@Composable () -> Unit> = brushWidths.mapIndexed<Float, @Composable () -> Unit> { i, width ->
            {
                ToolPlate(selected = !drawing.stamping && !drawing.erasing && drawing.brushDp == width, index = i, size = unit, onClick = {
                    drawing.brushDp = width
                    drawing.stamping = false
                    drawing.erasing = false
                    sfxPlayer.play(Sfx.POP, 0.6f)
                }) {
                    Canvas(modifier = Modifier.size(unit * 0.84f)) { drawBrush(drawing.selectedColor, fat = i == 1) }
                }
            }
        } + listOf<@Composable () -> Unit>(
            {
                ToolPlate(selected = drawing.stamping, index = 2, size = unit, onClick = {
                    drawing.stamping = true
                    drawing.erasing = false
                    sfxPlayer.play(Sfx.POP, 0.6f)
                }) {
                    Image(painter = painterResource(drawing.stampOrder[drawing.nextStamp % drawing.stampOrder.size]), contentDescription = null, modifier = Modifier.size(unit * 0.64f))
                }
            },
            {
                ToolPlate(selected = drawing.erasing, index = 3, size = unit, onClick = {
                    drawing.erasing = true
                    drawing.stamping = false
                    sfxPlayer.play(Sfx.POP, 0.6f)
                }) {
                    Canvas(modifier = Modifier.size(unit * 0.7f)) { drawEraser() }
                }
            }
        ) + rightColors.mapIndexed<Color, @Composable () -> Unit> { i, color ->
            { PaintPot(color = color, selected = color == drawing.selectedColor, index = i + 7, height = unit, onClick = { pickColor(color) }) }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // One side: back, the paint pots, and the camera that saves the drawing
            Column(
                modifier = Modifier.width(sideWidth).fillMaxHeight().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                if (compact) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BackButton(onBack)
                        saveButton()
                    }
                } else BackButton(onBack)
                ToolRows(pots, across)
                if (!compact) saveButton()
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
                            .pointerInput(drawing.selectedColor, brushSize, drawing.stamping, drawing.erasing, pageShapes) {
                                // A tap stamps a sticker, rubs out a spot, fills the shape it lands in, or leaves a dot of paint
                                detectTapGestures { offset ->
                                    val shape = shapeAt(pageShapes, offset)
                                    when {
                                        drawing.stamping -> stampAt(offset)
                                        drawing.erasing -> {
                                            eraseStampsAt(offset)
                                            addStroke(DrawPath(listOf(offset), PaperColor, brushSize, erase = true))
                                        }
                                        shape >= 0 -> {
                                            fills[shape] = drawing.selectedColor
                                            drawing.marks++
                                            repaintFills()  // the strokes already there stay on top
                                            sfxPlayer.play(Sfx.POP, 0.8f)
                                        }
                                        else -> addStroke(DrawPath(listOf(offset), drawing.selectedColor, brushSize))
                                    }
                                }
                            }
                            .pointerInput(drawing.selectedColor, brushSize, drawing.stamping, drawing.erasing) {
                                var lastStamp = Offset.Zero
                                var rubbedTo = 0  // how much of the eraser's path has gone into the paint
                                fun rub() {
                                    if (currentPath.size < 2 || rubbedTo >= currentPath.size) return
                                    val piece = currentPath.subList((rubbedTo - 1).coerceAtLeast(0), currentPath.size).toList()
                                    rubbedTo = currentPath.size
                                    art.paint(density, erase = true) { drawStroke(DrawPath(piece, PaperColor, brushSize, erase = true)) }
                                }
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (drawing.stamping) {
                                            stampAt(offset)
                                            lastStamp = offset
                                        } else {
                                            currentPath.clear(); currentPath.add(offset)
                                            rubbedTo = 0
                                            if (drawing.erasing) {
                                                eraseStampsAt(offset)
                                                art.paint(density, erase = true) { drawStroke(DrawPath(listOf(offset), PaperColor, brushSize, erase = true)) }
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (drawing.stamping) {
                                            // A trail of stickers, a little apart
                                            if ((change.position - lastStamp).getDistance() > stampSize * 0.8f) {
                                                stampAt(change.position)
                                                lastStamp = change.position
                                            }
                                        } else if (drawing.erasing) {
                                            // Rubbed out of the paint as the finger moves, a few points at a time
                                            eraseStampsAt(change.position)
                                            currentPath.add(change.position)
                                            if (currentPath.size - rubbedTo >= 3) rub()
                                        } else {
                                            currentPath.add(change.position)
                                            // A long scribble goes into the paint in pieces, so the live stroke stays short
                                            if (currentPath.size >= LIVE_POINTS) {
                                                val last = currentPath.last()
                                                addStroke(DrawPath(currentPath.toList(), drawing.selectedColor, brushSize))
                                                currentPath.clear(); currentPath.add(last)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (drawing.erasing) {
                                            rub()
                                            if (currentPath.isNotEmpty()) drawing.noted(DrawPath(currentPath.toList(), PaperColor, brushSize, erase = true))
                                        } else if (currentPath.size > 1) {
                                            addStroke(DrawPath(currentPath.toList(), drawing.selectedColor, brushSize))
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
                            drawArtwork(art, pageShapes, stamps, stampPainters, stampPop.value)
                        }
                        // The stroke being drawn right now (the only thing redrawn as the finger moves)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (!drawing.erasing) drawStroke(DrawPath(currentPath, drawing.selectedColor, brushSize))
                        }
                        if (traceGlyph != null) Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) { drawTraceEdge(traceGlyph) }
                        // The white sweep that wipes the page
                        if (sweep.value > 0f) Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(PaperColor, size = Size(size.width * sweep.value, size.height))
                        }
                        // The camera flash when the drawing is saved
                        val flashAlpha by animateFloatAsState(if (flash) 0.85f else 0f, tween(if (flash) 80 else 500), label = "flash")
                        if (flashAlpha > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))
                    }
                }
                // Strips of tape holding the paper's corners
                Canvas(modifier = Modifier.fillMaxSize()) { drawTape() }

                // פוקי looks at the drawing: on tap, or by itself after a pause in drawing
                val drawn = drawing.marks
                val companionStatus = companion?.status?.collectAsStateWithLifecycle()?.value
                fun lookNow() {
                    if (companion == null || looking || canvasSize.width == 0) return
                    looking = true
                    drawing.lastLooked = drawn
                    scope.launch {
                        // Drawn here (the stickers' painters belong to this thread), compressed in the background
                        val bitmap = snapshot(canvasSize, density) { drawArtwork(art, pageShapes, stamps, stampPainters) }
                        val png = withContext(Dispatchers.Default) { bitmap.toPng() }
                        companion.look(png)
                        looking = false
                    }
                }
                LaunchedEffect(drawn, companionStatus) {
                    if (drawn > 0 && drawn != drawing.lastLooked && companionStatus == CompanionStatus.READY) {
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

            // The other side: the shelf of pages, brushes, stamps, the eraser, three more pots, and the wipe at the far end
            Column(
                modifier = Modifier.width(sideWidth).fillMaxHeight().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaperColor)
                        .border(3.dp, Highlight, RoundedCornerShape(12.dp))
                        .clickable {
                            shelfOpen = true
                            sfxPlayer.play(Sfx.WHOOSH, 0.4f)
                        },
                    contentAlignment = Alignment.Center
                ) { PagePicture(coloringPages[drawing.pageIndex], language, 50.dp) }
                val wipeButton: @Composable () -> Unit = { HoldToWipe(if (compact) unit else 60.dp, onWipe = { wipe() }) }
                ToolRows(if (compact) tools + wipeButton else tools, across)
                if (!compact) wipeButton()
            }
        }

        if (shelfOpen) PageShelf(
            current = drawing.pageIndex,
            language = language,
            width = maxWidth,
            onPick = { choosePage(it) },
            onClose = { shelfOpen = false }
        )
        ConfettiOverlay(show = tracedParty)
    }
}

/** [items] in rows of [across], evenly down what's left of a side column. */
@Composable
private fun ToolRows(items: List<@Composable () -> Unit>, across: Int) {
    items.chunked(across).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            row.forEach { it() }
        }
    }
}

/** A page's own little picture: its lines, its first letter or number, or plain paper. */
@Composable
private fun PagePicture(page: CanvasPage?, language: Language, size: Dp) {
    when (page) {
        is ColoringPage -> Canvas(modifier = Modifier.size(size)) { drawPageLines(page.placed(this.size)) }
        is TracePage -> Text(
            if (page.numbers) "1" else if (language == Language.ENGLISH) "A" else "א",
            fontSize = (size.value * 0.72f).sp, fontWeight = FontWeight.Bold, color = Color(0xFF9575CD)
        )
        null -> Spacer(modifier = Modifier.size(size))
    }
}

/** The pages laid out to choose from; a tap outside puts the shelf away. */
@Composable
private fun PageShelf(current: Int, language: Language, width: Dp, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val thumb = ((width - 80.dp) / coloringPages.size - 10.dp).coerceIn(96.dp, 136.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFC8935B), Color(0xFFAE7642))))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            coloringPages.forEachIndexed { i, page ->
                Box(
                    modifier = Modifier
                        .popIn(i)
                        .size(thumb)
                        .shadow(4.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(PaperColor)
                        .border(if (i == current) 5.dp else 0.dp, Highlight, RoundedCornerShape(10.dp))
                        .clickable { onPick(i) },
                    contentAlignment = Alignment.Center
                ) { PagePicture(page, language, thumb * 0.82f) }
            }
        }
    }
}

/** The bin: held down for a second it wobbles harder and harder and then wipes; a tap only shakes it. */
@Composable
private fun HoldToWipe(size: Dp, onWipe: () -> Unit) {
    val scope = rememberCoroutineScope()
    val held = remember { Animatable(0f) }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val k = held.value
                rotationZ = sin(k * 34f) * 16f * k
                val g = 1f + k * 0.22f
                scaleX = g
                scaleY = g
            }
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFFFF8A80))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val hold = scope.launch {
                        held.animateTo(1f, tween(1000, easing = LinearEasing))
                        onWipe()
                    }
                    tryAwaitRelease()
                    hold.cancel()
                    scope.launch { held.animateTo(0f, tween(180)) }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(R.drawable.ic_stamp_trash), contentDescription = null, modifier = Modifier.size(size * 0.6f))
        val k = held.value
        if (k > 0f) Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(Color.White, -90f, 360f * k, false, style = Stroke(5.dp.toPx()))
        }
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
            .then(if (selected) Modifier.idleBob(index, amount = 1.4f) else Modifier)
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
            .then(if (selected) Modifier.idleBob(index + 9, amount = 1.2f) else Modifier)
            .graphicsLayer {
                scaleX = 1f + lift * 0.12f
                scaleY = 1f + lift * 0.12f
            }
            .size(size)
            .shadow(if (selected) 10.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .border(6.dp, Highlight.copy(alpha = shown), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
