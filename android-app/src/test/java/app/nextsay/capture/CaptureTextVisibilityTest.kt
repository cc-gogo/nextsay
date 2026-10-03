package app.nextsay.capture

import app.nextsay.context.ScreenRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTextVisibilityTest {
    private val panel = ScreenRect(50, 300, 900, 900)

    @Test
    fun `keeps chat text when only its lower edge touches overlay`() {
        assertTrue(CaptureTextVisibility.keep(ScreenRect(160, 260, 520, 320), listOf(panel)))
    }

    @Test
    fun `drops overlay text fully inside overlay`() {
        assertFalse(CaptureTextVisibility.keep(ScreenRect(100, 420, 700, 470), listOf(panel)))
    }

    @Test
    fun `drops text mostly hidden by overlay`() {
        assertFalse(CaptureTextVisibility.keep(ScreenRect(160, 280, 520, 380), listOf(panel)))
    }
}
