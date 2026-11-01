package io.github.tomsadeh.puki.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragDropStateTest {
    private fun dragging(at: Offset, size: Size = Size(100f, 100f)) = DragDropState().apply {
        registerDropZone("bowl", Rect(0f, 0f, 200f, 200f))
        registerDropZone("plate", Rect(400f, 0f, 600f, 200f))
        isDragging = true
        dragPosition = at
        draggedSize = size
    }

    @Test
    fun theZoneUnderItsCenterTakesIt() {
        assertEquals("plate", dragging(Offset(500f, 100f)).findDropTarget())
    }

    @Test
    fun smallHandsNeedntLandDeadCenter() {
        assertEquals("bowl", dragging(Offset(205f, 100f)).findDropTarget())  // its center is just outside
    }

    @Test
    fun farFromAnythingItsNowhere() {
        assertNull(dragging(Offset(300f, 500f)).findDropTarget())
    }

    @Test
    fun notDraggingItsNowhere() {
        assertNull(dragging(Offset(100f, 100f)).apply { isDragging = false }.findDropTarget())
    }
}
