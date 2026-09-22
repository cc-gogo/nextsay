package app.nextsay.overlay

import kotlin.math.hypot

sealed interface GestureAction {
    data object None : GestureAction
    data object Click : GestureAction
    data object LongPress : GestureAction
    data class DragBy(val deltaX: Float, val deltaY: Float) : GestureAction
}

class FloatingTriggerGestureController(
    private val touchSlop: Float,
    private val longPressMillis: Long,
) {
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var active = false
    private var dragging = false
    private var longPressDelivered = false

    fun onDown(x: Float, y: Float, atMillis: Long): GestureAction {
        downX = x
        downY = y
        lastX = x
        lastY = y
        downAt = atMillis
        active = true
        dragging = false
        longPressDelivered = false
        return GestureAction.None
    }

    fun onMove(x: Float, y: Float, atMillis: Long): GestureAction {
        if (!active || longPressDelivered) return GestureAction.None
        if (!dragging && hypot(x - downX, y - downY) >= touchSlop) dragging = true
        if (!dragging) return GestureAction.None
        val action = GestureAction.DragBy(x - lastX, y - lastY)
        lastX = x
        lastY = y
        return action
    }

    fun onLongPress(atMillis: Long): GestureAction {
        if (!active || dragging || longPressDelivered || atMillis - downAt < longPressMillis) {
            return GestureAction.None
        }
        longPressDelivered = true
        return GestureAction.LongPress
    }

    fun onUp(x: Float, y: Float, atMillis: Long): GestureAction {
        if (!active) return GestureAction.None
        val wasDragging = dragging || hypot(x - downX, y - downY) >= touchSlop
        val action = when {
            wasDragging || longPressDelivered -> GestureAction.None
            atMillis - downAt >= longPressMillis -> GestureAction.LongPress
            else -> GestureAction.Click
        }
        active = false
        dragging = false
        longPressDelivered = action == GestureAction.LongPress
        return action
    }

    fun cancel() {
        active = false
        dragging = false
        longPressDelivered = false
    }
}
