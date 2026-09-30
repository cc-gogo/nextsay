package app.nextsay.context

data class ScreenRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = left + (right - left) / 2
}

data class NodeSnapshot(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val viewId: String?,
    val bounds: ScreenRect,
    val editable: Boolean,
    val focused: Boolean,
    val password: Boolean,
)
