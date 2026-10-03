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
}
