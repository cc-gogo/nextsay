package app.nextsay.ocr

import app.nextsay.context.MessageRole
import app.nextsay.context.ScreenRect

/** [color] is the detector material: 1 green (own), 2 white, 3 dark mode; 0 for non-pixel sources. */
/** [transcript] marks a voice-to-text box whose side was taken from the voice bubble above it. */
data class OcrBubble(val bounds: ScreenRect, val role: MessageRole, val isMedia: Boolean = false, val color: Int = 0,
    val transcript: Boolean = false)

class WechatBubbleDetector {
    /** Local geometry only. No avatar identity recognition and no screenshot upload. */
    fun detect(pixels: IntArray, width: Int, height: Int, contentTop: Int, contentBottom: Int): List<OcrBubble> {
        require(width > 0 && height > 0 && pixels.size == width * height)
        require(contentTop in 0 until contentBottom && contentBottom <= height)
        val step = maxOf(2, width / 360)
        val columns = (width + step - 1) / step
        val rows = (contentBottom - contentTop + step - 1) / step
        val mask = ByteArray(columns * rows)
        for (row in 0 until rows) for (column in 0 until columns) {
            mask[row * columns + column] = material(pixels[(contentTop + row * step) * width + column * step])
        }
        val queue = IntArray(mask.size)
        val bubbles = mutableListOf<OcrBubble>()
        for (seed in mask.indices) {
            val color = mask[seed]
            if (color.toInt() == 0) continue
            var count = 1
            var head = 0
            queue[0] = seed
            mask[seed] = 0
            var left = columns
            var right = 0
            var top = rows
            var bottom = 0
            val rowLeft = IntArray(rows) { columns }
            val rowRight = IntArray(rows) { -1 }
            fun add(index: Int) {
                if (mask[index] == color) {
                    mask[index] = 0
                    queue[count++] = index
                }
            }
            while (head < count) {
                val index = queue[head++]
                val x = index % columns
                val y = index / columns
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
                rowLeft[y] = minOf(rowLeft[y], x)
                rowRight[y] = maxOf(rowRight[y], x)
                if (x > 0) add(index - 1)
                if (x + 1 < columns) add(index + 1)
                if (y > 0) add(index - columns)
                if (y + 1 < rows) add(index + columns)
            }
            val bounds = ScreenRect(left * step, contentTop + top * step,
                minOf(width, (right + 1) * step), minOf(contentBottom, contentTop + (bottom + 1) * step))
            // The outer avatar columns, page background, text glyphs and central controls are not bubbles.
            val anchoredLeft = bounds.left in (width * 0.12).toInt()..(width * 0.22).toInt()
            val anchoredRight = bounds.right in (width * 0.78).toInt()..(width * 0.90).toInt()
            val compact = bounds.right - bounds.left <= width * 0.58
            val leftPlacement = bounds.left <= width * 0.24 &&
                bounds.right <= width * 0.66 && bounds.centerX <= width * 0.44
            val rightPlacement = bounds.right >= width * 0.76 &&
                bounds.left >= width * 0.34 && bounds.centerX >= width * 0.56
            val fallbackPlacement = compact &&
                ((color.toInt() == 2 && leftPlacement) || (color.toInt() == 1 && rightPlacement))
            if ((!anchoredLeft && !anchoredRight && !fallbackPlacement) || bounds.left < width * 0.10 || bounds.right > width * 0.92 ||
                bounds.right - bounds.left < width * 0.075 || bounds.bottom - bounds.top < height * 0.012 ||
                count.toFloat() / ((right - left + 1) * (bottom - top + 1)) < 0.35f
            ) continue

            fun mode(values: IntArray): Int = values.slice(top..bottom)
                .filter { it in left..right }.groupingBy { it }.eachCount().maxBy { it.value }.key
            val leftTail = (mode(rowLeft) - left) * step
            val rightTail = (right - mode(rowRight)) * step
            val minTail = maxOf(step * 2, (width * 0.008).toInt())
            // Tail pixels are often hidden by the overlay or missed by coarse sampling.
            // A compact, clearly edge-aligned colored block is still reliable enough to
            // identify the side; centered or very wide blocks intentionally remain unknown.
            val role = when {
                leftTail >= minTail && leftTail >= rightTail + minTail -> MessageRole.OTHER
                rightTail >= minTail && rightTail >= leftTail + minTail -> MessageRole.ME
                anchoredLeft && !anchoredRight -> MessageRole.OTHER
                anchoredRight && !anchoredLeft -> MessageRole.ME
                color.toInt() == 2 && compact && leftPlacement -> MessageRole.OTHER
                color.toInt() == 1 && compact && rightPlacement -> MessageRole.ME
                else -> MessageRole.UNKNOWN
            }
            bubbles += OcrBubble(bounds, role, color = color.toInt())
        }
        return inheritTranscriptRoles(bubbles.sortedBy { it.bounds.top }, width, height)
    }

    /**
     * A voice-to-text transcript is a tail-less white box drawn directly below
     * its voice bubble, aligned to the same side. It is often wide enough to
     * cross the screen center, so its own geometry cannot tell the sender.
     */
    private fun inheritTranscriptRoles(bubbles: List<OcrBubble>, width: Int, height: Int): List<OcrBubble> {
        val maxGap = (height * 0.012f).toInt()
        val edgeTolerance = (width * 0.03f).toInt()
        val result = bubbles.toMutableList()
        for (index in 1 until result.size) {
            val transcript = result[index]
            if (transcript.role != MessageRole.UNKNOWN || (transcript.color != 2 && transcript.color != 3)) continue
            val voice = result.subList(0, index).lastOrNull { it.bounds.bottom <= transcript.bounds.top + edgeTolerance } ?: continue
            if (transcript.bounds.top - voice.bounds.bottom !in -edgeTolerance..maxGap) continue
            val aligned = when (voice.role) {
                MessageRole.ME -> transcript.bounds.right in voice.bounds.right - edgeTolerance..voice.bounds.right + edgeTolerance
                MessageRole.OTHER -> transcript.bounds.left in voice.bounds.left - edgeTolerance..voice.bounds.left + edgeTolerance
                else -> false
            }
            if (aligned) result[index] = transcript.copy(role = voice.role, transcript = true)
        }
        return result
    }

    private fun material(pixel: Int): Byte {
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        val spread = maxOf(r, g, b) - minOf(r, g, b)
        return when {
            g >= 90 && g - r >= 25 && g - b >= 25 -> 1
            minOf(r, g, b) >= 248 && spread <= 7 -> 2
            r in 30..70 && spread <= 8 -> 3
            else -> 0
        }
    }
}
