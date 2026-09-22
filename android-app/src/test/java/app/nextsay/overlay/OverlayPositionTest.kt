package app.nextsay.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionTest {
    @Test
    fun `pixel position round trips through fractions`() {
        val fractions = OverlayPosition.toFractions(
            x = 300,
            y = 600,
            availableWidth = 900,
            availableHeight = 1_800,
        )

        assertEquals(PositionFractions(1f / 3f, 1f / 3f), fractions)
        assertEquals(
            PixelPosition(300, 600),
            OverlayPosition.fromFractions(fractions, 900, 1_800),
        )
    }

    @Test
    fun `fractions and restored pixels are clamped to visible range`() {
        assertEquals(
            PositionFractions(1f, 0f),
            OverlayPosition.toFractions(1_500, -20, 900, 1_800),
        )
        assertEquals(
            PixelPosition(900, 0),
            OverlayPosition.fromFractions(PositionFractions(2f, -1f), 900, 1_800),
        )
    }

    @Test
    fun `zero sized available range restores to origin`() {
        val fractions = OverlayPosition.toFractions(20, 30, 0, 0)

        assertEquals(PositionFractions(0f, 0f), fractions)
        assertEquals(PixelPosition(0, 0), OverlayPosition.fromFractions(fractions, 0, 0))
    }
}
