package app.nextsay.overlay

import kotlin.math.roundToInt

data class PositionFractions(val x: Float, val y: Float)
data class PixelPosition(val x: Int, val y: Int)

object OverlayPosition {
    fun toFractions(
        x: Int,
        y: Int,
        availableWidth: Int,
        availableHeight: Int,
    ): PositionFractions = PositionFractions(
        x = fraction(x, availableWidth),
        y = fraction(y, availableHeight),
    )

    fun fromFractions(
        fractions: PositionFractions,
        availableWidth: Int,
        availableHeight: Int,
    ): PixelPosition = PixelPosition(
        x = pixel(fractions.x, availableWidth),
        y = pixel(fractions.y, availableHeight),
    )

    private fun fraction(value: Int, available: Int): Float =
        if (available <= 0) 0f else (value.toFloat() / available).coerceIn(0f, 1f)

    private fun pixel(fraction: Float, available: Int): Int =
        if (available <= 0) 0 else (fraction.coerceIn(0f, 1f) * available).roundToInt()
}
