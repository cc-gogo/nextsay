package app.nextsay.ocr

import app.nextsay.context.MessageRole
import app.nextsay.context.ScreenRect
import kotlin.math.abs

/** Conservative local fallback for unlabelled rectangular photos/screenshots, not image understanding. */
class WechatMediaGeometry {
    fun detect(pixels: IntArray, width: Int, height: Int, contentTop: Int, contentBottom: Int,
        exclusions: List<ScreenRect> = emptyList()): List<OcrBubble> {
        require(pixels.size == width * height && contentTop in 0 until contentBottom && contentBottom <= height)
        val step = maxOf(2, width / 360)
        val cols = (width + step - 1) / step
        val rows = (contentBottom - contentTop + step - 1) / step
        // Avatar rows affect only a minority of outer-margin samples.
        val samples = (contentTop until contentBottom step step).map { pixels[it * width + (width * .04f).toInt()] }
        fun channel(shift: Int) = samples.map { it shr shift and 255 }.sorted()[samples.size / 2]
        val bgR = channel(16); val bgG = channel(8); val bgB = channel(0)
        val mask = BooleanArray(cols * rows) { index ->
            val p = pixels[(contentTop + index / cols * step) * width + minOf(width-1, index % cols * step)]
            maxOf(abs((p shr 16 and 255) - bgR), abs((p shr 8 and 255) - bgG), abs((p and 255) - bgB)) > 9
        }
        val queue = IntArray(mask.size)
        val result = mutableListOf<OcrBubble>()
        for (seed in mask.indices) {
            if (!mask[seed]) continue
            var size = 1; var head = 0
            queue[0] = seed; mask[seed] = false
            var left = cols; var right = 0; var top = rows; var bottom = 0
            val rowLeft = IntArray(rows) { cols }; val rowRight = IntArray(rows) { -1 }
            var green = 0; var dark = 0
            fun add(i: Int) { if (mask[i]) { mask[i] = false; queue[size++] = i } }
            while (head < size) {
                val i = queue[head++]; val x = i % cols; val y = i / cols
                left = minOf(left,x); right = maxOf(right,x); top = minOf(top,y); bottom = maxOf(bottom,y)
                rowLeft[y] = minOf(rowLeft[y],x); rowRight[y] = maxOf(rowRight[y],x)
                val p = pixels[(contentTop+y*step)*width+minOf(width-1,x*step)]
                val r = p shr 16 and 255; val g = p shr 8 and 255; val b = p and 255
                if (g-r >= 25 && g-b >= 25) green++
                if (r in 30..70 && maxOf(r,g,b)-minOf(r,g,b) <= 8) dark++
                if (x>0) add(i-1); if (x+1<cols) add(i+1)
                if (y>0) add(i-cols); if (y+1<rows) add(i+cols)
            }
            val bounds = ScreenRect(left*step,contentTop+top*step,minOf(width,(right+1)*step),minOf(contentBottom,contentTop+(bottom+1)*step))
            // An erased tail adjacent to a rectangle is not evidence of an image.
            val margin = maxOf(step * 2, (width * .02f).toInt())
            if (app.nextsay.capture.CaptureVisibility.blocked(
                    ScreenRect(bounds.left-margin, bounds.top-margin, bounds.right+margin, bounds.bottom+margin), exclusions)) continue
            val anchoredLeft = bounds.left in (width*.12f).toInt()..(width*.22f).toInt()
            val anchoredRight = bounds.right in (width*.78f).toInt()..(width*.90f).toInt()
            if ((!anchoredLeft && !anchoredRight) || bounds.left < width*.10f || bounds.right > width*.92f ||
                bounds.right-bounds.left < width*.075f || bounds.bottom-bounds.top < width*.12f ||
                size.toFloat()/((right-left+1)*(bottom-top+1)) < .70f || green.toFloat()/size > .5f || dark.toFloat()/size > .65f) continue
            fun mode(values: IntArray) = values.slice(top..bottom).filter { it in left..right }
                .groupingBy { it }.eachCount().maxBy { it.value }.key
            // Text bubbles have a small tail; a screenshot is an outer rectangle even if it contains bubbles.
            if ((mode(rowLeft)-left)*step >= width*.008f || (right-mode(rowRight))*step >= width*.008f) continue
            val role = when { anchoredLeft && !anchoredRight -> MessageRole.OTHER; anchoredRight && !anchoredLeft -> MessageRole.ME; else -> MessageRole.UNKNOWN }
            result += OcrBubble(bounds, role, isMedia = true)
        }
        return result
    }
}
