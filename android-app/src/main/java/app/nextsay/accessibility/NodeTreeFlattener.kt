package app.nextsay.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.nextsay.context.NodeSnapshot
import app.nextsay.context.ScreenRect

class NodeTreeFlattener(private val maxNodes: Int = 2_000) {
    @Suppress("DEPRECATION")
    fun flatten(root: AccessibilityNodeInfo): List<NodeSnapshot> {
        val result = ArrayList<NodeSnapshot>(minOf(maxNodes, 256))

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (result.size >= maxNodes || depth > MAX_DEPTH) return
            val rect = Rect()
            node.getBoundsInScreen(rect)
            result += NodeSnapshot(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString(),
                viewId = node.viewIdResourceName,
                bounds = ScreenRect(rect.left, rect.top, rect.right, rect.bottom),
                editable = node.isEditable,
                focused = node.isFocused,
                password = node.isPassword,
            )
            for (index in 0 until node.childCount) {
                if (result.size >= maxNodes) break
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }

        visit(root, 0)
        return result
    }

    private companion object {
        const val MAX_DEPTH = 80
    }
}
