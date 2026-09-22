package app.nextsay.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTriggerGestureControllerTest {
    private val gestures = FloatingTriggerGestureController(
        touchSlop = 10f,
        longPressMillis = 500L,
    )

    @Test
    fun `short stationary press is a click`() {
        gestures.onDown(20f, 30f, 1_000L)

        assertEquals(GestureAction.Click, gestures.onUp(24f, 33f, 1_300L))
    }

    @Test
    fun `stationary hold is a long press exactly once`() {
        gestures.onDown(20f, 30f, 1_000L)

        assertEquals(GestureAction.LongPress, gestures.onLongPress(1_500L))
        assertEquals(GestureAction.None, gestures.onLongPress(1_600L))
        assertEquals(GestureAction.None, gestures.onUp(20f, 30f, 1_700L))
    }

    @Test
    fun `movement at touch slop starts drag and suppresses click`() {
        gestures.onDown(20f, 30f, 1_000L)

        assertEquals(GestureAction.DragBy(10f, 0f), gestures.onMove(30f, 30f, 1_100L))
        assertEquals(GestureAction.DragBy(4f, 5f), gestures.onMove(34f, 35f, 1_200L))
        assertEquals(GestureAction.None, gestures.onUp(34f, 35f, 1_300L))
    }

    @Test
    fun `drag suppresses a pending long press`() {
        gestures.onDown(20f, 30f, 1_000L)
        gestures.onMove(31f, 30f, 1_100L)

        assertEquals(GestureAction.None, gestures.onLongPress(1_500L))
    }
}
