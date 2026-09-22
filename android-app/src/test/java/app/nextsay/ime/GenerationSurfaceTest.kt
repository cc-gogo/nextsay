package app.nextsay.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationSurfaceTest {
    private val policy = GenerationPresentationPolicy()

    @Test
    fun `quick requests render only the quick window`() {
        assertEquals(GenerationDestination.QUICK_WINDOW, policy.destination(GenerationSurface.QUICK))
    }

    @Test
    fun `advanced requests render only the white panel`() {
        assertEquals(GenerationDestination.ADVANCED_PANEL, policy.destination(GenerationSurface.ADVANCED))
    }

    @Test
    fun `IME requests render no overlay`() {
        assertEquals(GenerationDestination.IME_ONLY, policy.destination(GenerationSurface.IME))
    }
}
