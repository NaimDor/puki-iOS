package io.github.tomsadeh.puki.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TICK_NANOS = 33_000_000L  // 30 times a second is plenty for slow idle motion

/**
 * One clock for all the idle motion on screen, so waiting things don't each run an animation of
 * their own. Read it only where it's drawn (a graphicsLayer block, a draw lambda): nothing else
 * notices it tick. It stands still while [hold] is on, under an end card say.
 */
class AmbientClock {
    var seconds by mutableDoubleStateOf(0.0)
        private set
    private var holds = 0

    fun hold() {
        holds++
    }

    fun release() {
        holds--
    }

    /** How far through a loop of [period] seconds, 0..1. */
    fun loop(period: Float): Float = ((seconds / period) % 1.0).toFloat()

    /** A smooth -1..1 wave of [period] seconds. */
    fun wave(period: Float, phase: Float = 0f): Float = sin(2 * PI.toFloat() * (loop(period) + phase))

    /** Ticks for as long as it's called. */
    suspend fun run() {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                if (holds > 0) last = now
                else if (now - last >= TICK_NANOS) {
                    seconds += (now - last) / 1e9
                    last = now
                }
            }
        }
    }
}

val LocalAmbientClock = compositionLocalOf { AmbientClock() }

/**
 * Gentle idle motion (a slow bob and breath), so a waiting screen feels alive. [index]
 * staggers neighbours so they don't move in lockstep.
 */
fun Modifier.idleBob(index: Int = 0, amount: Float = 1f): Modifier = this then IdleBobElement(index, amount)

private data class IdleBobElement(val index: Int, val amount: Float) : ModifierNodeElement<IdleBobNode>() {
    override fun create() = IdleBobNode(index, amount)
    override fun update(node: IdleBobNode) {
        node.index = index
        node.amount = amount
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "idleBob"
    }
}

private class IdleBobNode(var index: Int, var amount: Float) : IdleNode() {
    override fun GraphicsLayerScope.pose(clock: AmbientClock) {
        val phase = 2 * PI.toFloat() * (clock.loop((2200 + index * 170) / 1000f) + index * 0.23f)
        translationY = sin(phase) * 6f * density * amount
        val breath = 1f + sin(phase * 2) * 0.025f * amount
        scaleX = breath
        scaleY = 2f - breath
        rotationZ = sin(phase) * 1.5f * amount
    }
}

/** A slow, gentle breathing loop so characters look alive while waiting. */
fun Modifier.idleBreathing(periodMillis: Int = 2400): Modifier = this then IdleBreathElement(periodMillis)

private data class IdleBreathElement(val periodMillis: Int) : ModifierNodeElement<IdleBreathNode>() {
    override fun create() = IdleBreathNode(periodMillis)
    override fun update(node: IdleBreathNode) {
        node.periodMillis = periodMillis
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "idleBreathing"
    }
}

private class IdleBreathNode(var periodMillis: Int) : IdleNode() {
    override fun GraphicsLayerScope.pose(clock: AmbientClock) {
        val breath = (1f - cos(2 * PI.toFloat() * clock.loop(periodMillis / 1000f))) / 2f
        transformOrigin = TransformOrigin(0.5f, 1f)
        scaleY = 1f + 0.035f * breath
        scaleX = 1f - 0.015f * breath
    }
}

/** A layer posed from the clock, with no composition or animation of its own. */
private abstract class IdleNode : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    abstract fun GraphicsLayerScope.pose(clock: AmbientClock)

    private val layer: GraphicsLayerScope.() -> Unit = { pose(currentValueOf(LocalAmbientClock)) }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layer) }
    }
}
