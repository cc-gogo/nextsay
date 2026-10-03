package app.nextsay.ocr

import app.nextsay.context.MessageRole
import app.nextsay.context.NodeSnapshot
import app.nextsay.context.ScreenRect

/** Only visible attachment controls, never image contents or avatar identity. */
class WechatMediaDetector {
    fun detect(nodes: List<NodeSnapshot>, width: Int, contentTop: Int, contentBottom: Int): List<OcrBubble> {
        val candidates = nodes.filter { node ->
            val r = node.bounds
            val label = node.contentDescription?.trim().orEmpty()
            val media = label.matches(Regex("^(?:\\[图片\\]|图片(?:消息)?|照片)(?:[，,：:、\\s].*)?$")) ||
                node.className?.endsWith("ImageView") == true && node.text.isNullOrBlank() && !label.contains("头像")
            media && !node.editable && !node.password &&
                r.bottom > contentTop && r.top < contentBottom &&
                r.right - r.left >= width * .075f && r.bottom - r.top >= width * .075f &&
                r.left >= width * .10f && r.right <= width * .92f &&
                (r.left in (width * .12f).toInt()..(width * .22f).toInt() ||
                    r.right in (width * .78f).toInt()..(width * .90f).toInt())
        }.map { node ->
            val r = node.bounds
            val left = r.left in (width * .12f).toInt()..(width * .22f).toInt()
            val right = r.right in (width * .78f).toInt()..(width * .90f).toInt()
            val role = when {
                left && !right -> MessageRole.OTHER
                right && !left -> MessageRole.ME
                else -> {
                    // Only an outer avatar at the attachment's top row is side evidence.
                    val avatars = nodes.filter { avatar ->
                        val a = avatar.bounds
                        (avatar.contentDescription.orEmpty().contains("头像") || avatar.className?.endsWith("ImageView") == true) &&
                            kotlin.math.abs(a.top - r.top) <= width * .025f &&
                            a.bottom - a.top in (width * .05f).toInt()..(width * .15f).toInt() &&
                            (a.right <= width * .14f || a.left >= width * .86f)
                    }
                    val outerLeft = avatars.any { it.bounds.right <= width * .14f }
                    val outerRight = avatars.any { it.bounds.left >= width * .86f }
                    when { outerLeft && !outerRight -> MessageRole.OTHER; outerRight && !outerLeft -> MessageRole.ME; else -> MessageRole.UNKNOWN }
                }
            }
            OcrBubble(ScreenRect(r.left, maxOf(r.top, contentTop), r.right, minOf(r.bottom, contentBottom)), role, isMedia = true)
        }.distinctBy { it.bounds }
        // Nested ImageViews contribute only one outer attachment, not duplicate messages.
        return candidates.filter { item -> candidates.none { other -> other != item && contains(other.bounds, item.bounds) } }
    }

    private fun contains(a: ScreenRect, b: ScreenRect) = a.left <= b.left && a.top <= b.top && a.right >= b.right && a.bottom >= b.bottom
}
