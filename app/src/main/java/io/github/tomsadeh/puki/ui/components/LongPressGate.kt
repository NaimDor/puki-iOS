package io.github.tomsadeh.puki.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.tomsadeh.puki.data.models.BilingualText
import io.github.tomsadeh.puki.data.models.Language
import io.github.tomsadeh.puki.ui.common.LocalLanguage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.os.SystemClock
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Until when (uptime) the gate stays shut after a wrong answer: for every gate, whichever screen. */
private var lockedUntil = 0L
private const val LOCK_MS = 60_000L

/**
 * For grown-ups only: after a [holdDurationMs] hold (a ring fills up), a grown-up taps the number
 * written out in words among six, twice in a row ([ParentCheck]); only then [onUnlocked] runs. A
 * toddler can hold on for two seconds, but can't read "seventeen", and mashing gets nowhere: a
 * wrong tap shuts every gate for a minute.
 */
@Composable
fun LongPressGate(
    holdDurationMs: Long = 2000L,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var checking by remember { mutableStateOf(false) }
    if (checking) {
        ParentCheck(onDone = { passed ->
            checking = false
            if (passed) onUnlocked() else lockedUntil = SystemClock.uptimeMillis() + LOCK_MS
        })
    }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val unlocked = { checking = true }
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(100),
        label = "hold_progress"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (SystemClock.uptimeMillis() < lockedUntil) return@detectTapGestures  // (shut for now: the ring doesn't fill)
                        holdJob = scope.launch {
                            val steps = 15
                            for (i in 1..steps) {
                                delay(holdDurationMs / steps)
                                holdProgress = i.toFloat() / steps
                            }
                            unlocked()
                            holdProgress = 0f
                        }
                        tryAwaitRelease()
                        holdJob?.cancel()
                        holdProgress = 0f
                    }
                )
            }
            .drawBehind {
                if (animatedProgress > 0f) {
                    drawArc(
                        color = Color(0xFF4CAF50),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = size,
                        style = Stroke(width = 4f)
                    )
                }
            }
    ) {
        content()
    }
}

/** A number in words, for [ParentCheck]: 11 to 19, Hebrew (feminine, as numbers are counted) or English. */
internal fun numberInWords(n: Int, language: Language): String = when (language) {
    Language.HEBREW -> listOf("אחת-עשרה", "שתים-עשרה", "שלוש-עשרה", "ארבע-עשרה", "חמש-עשרה", "שש-עשרה",
        "שבע-עשרה", "שמונה-עשרה", "תשע-עשרה")[n - 11]
    Language.ENGLISH -> listOf("eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen")[n - 11]
}

/**
 * "For grown-ups: tap seventeen", among six numbers, then a second number. [onDone] says whether
 * the right one was tapped both times (a guess gets through once in 36).
 */
@Composable
private fun ParentCheck(onDone: (Boolean) -> Unit) {
    val language = LocalLanguage.current
    var round by remember { mutableIntStateOf(0) }
    val target = remember(round) { (11..19).random() }
    val choices = remember(round) { ((11..19).filter { it != target }.shuffled().take(5) + target).shuffled() }
    fun picked(n: Int) = when {
        n != target -> onDone(false)
        round == 0 -> round = 1
        else -> onDone(true)
    }
    Dialog(onDismissRequest = { onDone(false) }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                BilingualText("להורים: לחצו על המספר", "For grown-ups: tap the number").resolve(language),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF5D4037)
            )
            Text(numberInWords(target, language), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
            choices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { n ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFFF3E0))
                                .clickable { picked(n) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$n", fontSize = 28.sp, color = Color(0xFF5D4037))
                        }
                    }
                }
            }
        }
    }
}
