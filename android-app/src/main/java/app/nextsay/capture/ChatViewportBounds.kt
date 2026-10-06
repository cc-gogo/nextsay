package app.nextsay.capture

import app.nextsay.context.NodeSnapshot

object ChatViewportBounds {
    fun bottom(height: Int, imeTop: Int?, nodes: List<NodeSnapshot>, fallbackGuard: Int): Int {
        val top = (height * .09f).toInt()
        val limit = (imeTop ?: height).coerceIn(top + 1, height)
        val inputTop = nodes.filter { it.editable && !it.password && it.bounds.top > top &&
            it.bounds.bottom <= limit && it.bounds.right > it.bounds.left && it.bounds.bottom > it.bounds.top }
            .minOfOrNull { it.bounds.top }
        // When the IME exposes its top edge but not the app's input node, the
        // IME boundary is already the chat viewport boundary. Subtracting the
        // fallback guard here would hide the newest bubble above the keyboard.
        val validInput = inputTop?.takeIf { it > top && it < limit }
        return (validInput ?: if (imeTop != null && nodes.isNotEmpty() && nodes.any { it.editable && it.bounds.top in (top + 1) until limit }) limit else limit - fallbackGuard)
            .coerceIn(top + 1, height)
    }

    /**
     * The chat input bar is a flat strip whose color differs from the chat
     * background. Walking up the left margin from [bottom] while the color
     * stays the same finds its top edge, independent of the IME state.
     * Returns null when the strip height is implausible.
     */
    fun inputBarTop(height: Int, bottom: Int, pixelAt: (Int) -> Int): Int? {
        if (bottom <= 1) return null
        val base = pixelAt(bottom - 1)
        var y = bottom - 1
        while (y > 0 && sameColor(pixelAt(y - 1), base)) y--
        return y.takeIf { bottom - it in (height * .04f).toInt()..(height * .12f).toInt() }
    }

    private fun sameColor(a: Int, b: Int): Boolean =
        kotlin.math.abs((a shr 16 and 255) - (b shr 16 and 255)) <= 3 &&
            kotlin.math.abs((a shr 8 and 255) - (b shr 8 and 255)) <= 3 &&
            kotlin.math.abs((a and 255) - (b and 255)) <= 3
}
