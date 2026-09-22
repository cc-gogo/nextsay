package app.nextsay.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class CopyFeedbackPositionTest {
    @Test
    fun `feedback appears left of a trigger on the right half`() {
        val position = CopyFeedbackPosition.calculate(
            screenWidth = 1080,
            screenHeight = 2400,
            triggerX = 960,
            triggerY = 900,
            triggerSize = 52,
            feedbackWidth = 140,
            feedbackHeight = 60,
            gap = 8,
        )

        assertEquals(812, position.x)
        assertEquals(896, position.y)
    }

    @Test
    fun `feedback appears right of a trigger on the left half`() {
        val position = CopyFeedbackPosition.calculate(
            screenWidth = 1080,
            screenHeight = 2400,
            triggerX = 20,
            triggerY = 300,
            triggerSize = 52,
            feedbackWidth = 140,
            feedbackHeight = 60,
            gap = 8,
        )

        assertEquals(80, position.x)
        assertEquals(296, position.y)
    }

    @Test
    fun `feedback is clamped inside the screen`() {
        val position = CopyFeedbackPosition.calculate(
            screenWidth = 300,
            screenHeight = 500,
            triggerX = 0,
            triggerY = 490,
            triggerSize = 52,
            feedbackWidth = 260,
            feedbackHeight = 80,
            gap = 8,
        )

        assertEquals(40, position.x)
        assertEquals(420, position.y)
    }
}
