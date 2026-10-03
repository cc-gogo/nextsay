package app.nextsay.capture

import app.nextsay.context.ScreenRect

object CaptureVisibility {
    // A covered contour may be truncated or absent. The latest outer avatar starts
    // the final message; include all remaining rows above the input, not just its height.
    fun latestTail(avatar: ScreenRect, width: Int, contentBottom: Int, bubbles: List<ScreenRect> = emptyList()): ScreenRect {
        val outer = bubbles.filter { kotlin.math.abs(it.top-avatar.top) <= width*.04f && it.bottom > avatar.top }
            .maxByOrNull { it.right-it.left }
        if (outer == null) return ScreenRect((width*.10f).toInt(), avatar.top, (width*.92f).toInt(), contentBottom)
        // A contour truncated by the masking boundary ends at the overlay. Keep a
        // margin so this still counts as obscured, but do not block blank space far below.
        val margin = maxOf(4, (width*.02f).toInt())
        return ScreenRect((outer.left-margin).coerceAtLeast(0), minOf(avatar.top,outer.top),
            (outer.right+margin).coerceAtMost(width), (outer.bottom+margin).coerceAtMost(contentBottom))
    }

    fun blocked(tail: ScreenRect, exclusions: List<ScreenRect>, reliableRole: Boolean = false): Boolean {
        if (reliableRole) return false
        return exclusions.any {
            tail.left < it.right && tail.right > it.left && tail.top < it.bottom && tail.bottom > it.top
        }
    }
}
