package app.nextsay.ocr

import app.nextsay.capture.CapturedConversation
import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.context.ScreenRect

class WechatOcrParser {
    fun parse(
        blocks: List<OcrTextBlock>,
        screenWidth: Int,
        screenHeight: Int,
        contentBottom: Int,
        sourcePackage: String = WECHAT_PACKAGE,
        sourceApp: String = "wechat",
        bubbles: List<OcrBubble>? = null,
        fallbackTitle: String? = null,
    ): CapturedConversation? {
        require(screenWidth > 0 && screenHeight > 0) { "screen dimensions must be positive" }
        require(contentBottom in 1..screenHeight) { "contentBottom must be on screen" }

        val usable = blocks.mapNotNull { block ->
            val text = normalize(block.text)
            block.copy(text = text).takeIf { text.isNotEmpty() && block.confidence >= MIN_CONFIDENCE }
        }
        val headerBottom = (screenHeight * HEADER_BOTTOM_RATIO).toInt()
        val headerTop = (screenHeight * HEADER_TOP_RATIO).toInt()
        val isQq = sourceApp == "qq"
        val titleCandidates = usable
            .filter { block ->
                block.bounds.top >= headerTop &&
                    block.bounds.bottom <= headerBottom &&
                    block.bounds.centerX in (screenWidth * if (isQq) 0.12f else 0.30f).toInt()..(screenWidth * 0.70f).toInt() &&
                    !isChrome(block.text) && (!isQq || !QQ_STATUS_PATTERN.matches(block.text))
            }
        // QQ puts the contact name above a left-aligned online-status row.
        val titleBlock = (if (isQq) titleCandidates.minByOrNull { it.bounds.top }
            else titleCandidates.maxByOrNull { (it.bounds.right - it.bounds.left) * (it.bounds.bottom - it.bounds.top) })
        val title = titleBlock?.text ?: fallbackTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val hasConfirmedTitle = titleBlock != null

        val messageBlocks = usable.asSequence()
            .filter { it !== titleBlock }
            .filter { it.bounds.top >= headerBottom && it.bounds.bottom <= contentBottom }
            .filterNot { bubbles == null && (isMetadata(it.text) || isChrome(it.text)) }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .toList()
        val messages = if (bubbles != null) {
            messagesByBubble(messageBlocks, bubbles, screenWidth)
        } else {
            // QQ text bounds alone cannot distinguish wide outgoing bubbles from incoming ones.
            mergeAdjacentLines(messageBlocks.map { block ->
                val role = if (isQq) MessageRole.UNKNOWN else inferRole(block, screenWidth)
                PositionedLine(role, block.text, block.bounds,
                    if (role == MessageRole.UNKNOWN) minOf(block.confidence, 0.4f) else block.confidence)
            }, screenWidth, screenHeight)
        }
        if (messages.isEmpty()) return null

        return CapturedConversation(
            title = title,
            context = ChatContext(
                sourceApp = sourceApp,
                sourcePackage = sourcePackage,
                messages = messages,
                draft = "",
                confidence = messages.map { it.confidence }.average().toFloat(),
            ),
            // A generic fallback title is only for the current frame. Do not
            // write it as a durable contact/history key.
            persistable = hasConfirmedTitle && !GROUP_TITLE_PATTERN.matches(title),
        )
    }

    private fun normalize(text: String): String = text.trim().replace(WHITESPACE_PATTERN, " ")

    private fun messagesByBubble(
        blocks: List<OcrTextBlock>,
        bubbles: List<OcrBubble>,
        screenWidth: Int,
    ): List<ChatMessage> {
        // A large multiline voice transcript can be exposed as a plain
        // rectangular accessibility node and accidentally enter the media
        // fallback. Restore it as a text bubble when several aligned Chinese
        // OCR lines clearly occupy that rectangle.
        val effectiveBubbles = bubbles.map { bubble ->
            if (bubble.isMedia && looksLikeTranscriptBubble(bubble, blocks, screenWidth)) {
                bubble.copy(isMedia = false)
            } else bubble
        }
        val media = effectiveBubbles.filter { it.isMedia }
        val grouped = linkedMapOf<Int, MutableList<OcrTextBlock>>()
        val unmatched = mutableListOf<Pair<Int, ChatMessage>>()
        for (block in blocks) {
            // Image contents are not typed conversation text, including nested UI screenshots.
            if (media.any { intersects(block.bounds, it.bounds) }) continue
            val area = (block.bounds.right - block.bounds.left).coerceAtLeast(1) *
                (block.bounds.bottom - block.bounds.top).coerceAtLeast(1)
            val matches = effectiveBubbles.withIndex().filter { (_, bubble) ->
                if (bubble.isMedia) return@filter false
                bubbleContainsText(block.bounds, bubble.bounds, area)
            }
            if (matches.size == 1) {
                grouped.getOrPut(matches.single().index) { mutableListOf() }.add(block)
            } else if (matches.isNotEmpty() || (!isMetadata(block.text) && !isChrome(block.text))) {
                // A voice message converted to text can expose its glyphs while
                // the colored bubble or tail is partly hidden by the IME/overlay.
                // When no bubble claims the text, use the same left/right rule as
                // ordinary OCR. A long transcript line can also overlap a stale
                // geometry fragment; use its clear side evidence, while keeping
                // short ambiguous controls/timestamps UNKNOWN.
                val fallbackRole = if (matches.isEmpty() ||
                    (matches.size > 1 && isLikelyTranscript(block.text))) inferRole(block, screenWidth)
                else MessageRole.UNKNOWN
                val confidence = if (fallbackRole == MessageRole.UNKNOWN) minOf(block.confidence, 0.4f)
                else minOf(block.confidence, 0.72f)
                unmatched += block.bounds.top to ChatMessage(fallbackRole, block.text, confidence)
            }
        }
        val messages = grouped.map { (index, lines) ->
            val bubble = effectiveBubbles[index]
            val ordered = lines.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            val text = ordered.map { it.text }.reduce { first, second -> first + separator(first, second) + second }
            val confidence = ordered.map { it.confidence }.average().toFloat()
            val role = bubble.role.takeKnownOr { inferRole(bubble.bounds, screenWidth) }
            ordered.first().bounds.top to ChatMessage(role, text,
                if (role == MessageRole.UNKNOWN) minOf(confidence, 0.4f) else confidence)
        }
        val attachments = media.map { it.bounds.top to ChatMessage(it.role, "[图片]", if (it.role == MessageRole.UNKNOWN) .4f else .9f) }
        return (messages + unmatched + attachments).sortedBy { it.first }.map { it.second }
    }

    private fun looksLikeTranscriptBubble(
        bubble: OcrBubble,
        blocks: List<OcrTextBlock>,
        screenWidth: Int,
    ): Boolean {
        val candidates = blocks.filter { block ->
            !isMetadata(block.text) && !isChrome(block.text) &&
                block.text.any { it in '\u4e00'..'\u9fff' } &&
                bubbleContainsText(
                    block.bounds,
                    bubble.bounds,
                    (block.bounds.right - block.bounds.left).coerceAtLeast(1) *
                        (block.bounds.bottom - block.bounds.top).coerceAtLeast(1),
                )
        }
        if (candidates.size < 3) return false
        val anchor = candidates.minBy { it.bounds.top }
        val aligned = candidates.count {
            kotlin.math.abs(it.bounds.left - anchor.bounds.left) <= (screenWidth * .12f).toInt() &&
                it.bounds.right - it.bounds.left >= (screenWidth * .16f).toInt()
        }
        return aligned >= 3
    }

    private fun intersects(a: ScreenRect, b: ScreenRect) = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    /** OCR returns the glyph rectangle, not the complete chat bubble. */
    private fun bubbleContainsText(text: ScreenRect, bubble: ScreenRect, textArea: Int): Boolean {
        val expanded = ScreenRect(bubble.left - 20, bubble.top - 14, bubble.right + 20, bubble.bottom + 14)
        val centerInside = text.centerX in expanded.left..expanded.right &&
            text.centerY() in expanded.top..expanded.bottom
        if (!centerInside) return false
        val overlapWidth = (minOf(text.right, bubble.right) - maxOf(text.left, bubble.left)).coerceAtLeast(0)
        val overlapHeight = (minOf(text.bottom, bubble.bottom) - maxOf(text.top, bubble.top)).coerceAtLeast(0)
        val overlap = overlapWidth.toFloat() * overlapHeight / textArea
        return overlap >= 0.15f
    }

    private fun ScreenRect.centerY(): Int = top + (bottom - top) / 2

    private fun inferRole(block: OcrTextBlock, screenWidth: Int): MessageRole = inferRole(block.bounds, screenWidth)

    private fun inferRole(bounds: ScreenRect, screenWidth: Int): MessageRole {
        val left = bounds.left.toFloat() / screenWidth
        val right = bounds.right.toFloat() / screenWidth
        val center = bounds.centerX.toFloat() / screenWidth
        return when {
            left <= OTHER_LEFT_EDGE && right < OTHER_RIGHT_LIMIT -> MessageRole.OTHER
            right >= ME_RIGHT_EDGE && left > ME_LEFT_LIMIT -> MessageRole.ME
            center <= OTHER_MAX_POSITION -> MessageRole.OTHER
            center >= ME_MIN_POSITION -> MessageRole.ME
            else -> MessageRole.UNKNOWN
        }
    }

    private inline fun MessageRole.takeKnownOr(fallback: () -> MessageRole): MessageRole =
        if (this == MessageRole.UNKNOWN) fallback() else this

    private fun mergeAdjacentLines(
        lines: List<PositionedLine>,
        screenWidth: Int,
        screenHeight: Int,
    ): List<ChatMessage> {
        val merged = mutableListOf<PositionedLine>()
        val maxGap = maxOf(8, (screenHeight * MAX_LINE_GAP_RATIO).toInt())
        val maxLeftShift = (screenWidth * MAX_LINE_LEFT_SHIFT_RATIO).toInt()
        for (line in lines) {
            val previous = merged.lastOrNull()
            val gap = if (previous == null) Int.MAX_VALUE else line.bounds.top - previous.bounds.bottom
            if (
                previous != null &&
                line.role != MessageRole.UNKNOWN &&
                line.role == previous.role &&
                gap in -4..maxGap &&
                kotlin.math.abs(line.bounds.left - previous.bounds.left) <= maxLeftShift
            ) {
                merged[merged.lastIndex] = previous.copy(
                    text = previous.text + separator(previous.text, line.text) + line.text,
                    bounds = ScreenRect(
                        minOf(previous.bounds.left, line.bounds.left),
                        previous.bounds.top,
                        maxOf(previous.bounds.right, line.bounds.right),
                        maxOf(previous.bounds.bottom, line.bounds.bottom),
                    ),
                    confidence = (previous.confidence + line.confidence) / 2f,
                )
            } else {
                merged += line
            }
        }
        return merged.map { ChatMessage(it.role, it.text, it.confidence) }
    }

    private fun separator(first: String, second: String): String =
        if (first.lastOrNull()?.isLetterOrDigit() == true && first.last().code < 128 &&
            second.firstOrNull()?.isLetterOrDigit() == true && second.first().code < 128
        ) " " else ""

    private fun isMetadata(text: String): Boolean =
        TIME_PATTERN.matches(text) || DATE_TIME_PATTERN.matches(text) || text in SYSTEM_METADATA

    private fun isLikelyTranscript(text: String): Boolean =
        text.length >= 8 && text.any { it in '\u4e00'..'\u9fff' }

    private fun isChrome(text: String): Boolean = text in CHAT_CHROME

    private companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        const val MIN_CONFIDENCE = 0.55f
        const val HEADER_TOP_RATIO = 0.025f
        const val HEADER_BOTTOM_RATIO = 0.09f
        const val OTHER_LEFT_EDGE = 0.28f
        const val OTHER_RIGHT_LIMIT = 0.92f
        const val ME_RIGHT_EDGE = 0.72f
        const val ME_LEFT_LIMIT = 0.28f
        const val OTHER_MAX_POSITION = 0.48f
        const val ME_MIN_POSITION = 0.52f
        const val MAX_LINE_GAP_RATIO = 0.008f
        const val MAX_LINE_LEFT_SHIFT_RATIO = 0.12f
        val WHITESPACE_PATTERN = Regex("\\s+")
        val TIME_PATTERN = Regex("^(?:今天|昨天)?\\s*(?:[01]?\\d|2[0-3]):[0-5]\\d$")
        val DATE_TIME_PATTERN = Regex("^\\d{1,2}月\\d{1,2}日\\s*(?:[01]?\\d|2[0-3]):[0-5]\\d$")
        val GROUP_TITLE_PATTERN = Regex("^.+[（(]\\d+[)）]$")
        val QQ_STATUS_PATTERN = Regex("^(?:在线|离线|手机在线|电脑在线|忙碌|离开|隐身|请勿打扰)(?:\\s*[-·]\\s*(?:[245]G|Wi-?Fi|手机在线))?$", RegexOption.IGNORE_CASE)
        val SYSTEM_METADATA = setOf("以上是打招呼的消息")
        val CHAT_CHROME = setOf("返回", "聊天信息", "更多", "发送", "语音", "表情", "相册", "拍摄")
    }

    private data class PositionedLine(
        val role: MessageRole,
        val text: String,
        val bounds: ScreenRect,
        val confidence: Float,
    )
}
