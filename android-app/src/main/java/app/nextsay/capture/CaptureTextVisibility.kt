package app.nextsay.capture

import app.nextsay.context.ScreenRect

/** Keeps OCR blocks that are only touched by an overlay edge. */
object CaptureTextVisibility {
    private const val MAX_COVERED_FRACTION = 0.45f

    fun keep(block: ScreenRect, exclusions: List<ScreenRect>): Boolean {
        val area = block.area().toFloat()
        if (area <= 0f) return false
        val covered = exclusions.maxOfOrNull { overlapArea(block, it) } ?: 0
        return covered / area < MAX_COVERED_FRACTION
    }

    private fun overlapArea(a: ScreenRect, b: ScreenRect): Int {
        val width = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0)
        val height = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0)
        return width * height
    }

    private fun ScreenRect.area(): Int =
        (right - left).coerceAtLeast(0) * (bottom - top).coerceAtLeast(0)
}
