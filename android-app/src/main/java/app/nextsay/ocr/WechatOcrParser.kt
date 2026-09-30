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
    ): CapturedConversation? {
        require(screenWidth > 0 && screenHeight > 0) { "screen dimensions must be positive" }
        require(contentBottom in 1..screenHeight) { "contentBottom must be on screen" }

        val usable = blocks.mapNotNull { block ->
            val text = normalize(block.text)
            block.copy(text = text).takeIf { text.isNotEmpty() && block.confidence >= MIN_CONFIDENCE }
        }
        val headerBottom = (screenHeight * HEADER_BOTTOM_RATIO).toInt()
        val headerTop = (screenHeight * HEADER_TOP_RATIO).toInt()
        val titleBlock = usable
            .filter { block ->
                block.bounds.top >= headerTop &&
                    block.bounds.bottom <= headerBottom &&
                    block.bounds.centerX in (screenWidth * 0.30f).toInt()..(screenWidth * 0.70f).toInt() &&
                    !isChrome(block.text)
            }
            .maxByOrNull { (it.bounds.right - it.bounds.left) * (it.bounds.bottom - it.bounds.top) }
            ?: return null

        val positioned = usable.asSequence()
            .filter { it !== titleBlock }
            .filter { it.bounds.top >= headerBottom && it.bounds.bottom <= contentBottom }
            .filterNot { isMetadata(it.text) || isChrome(it.text) }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .map { block ->
                PositionedLine(inferRole(block, screenWidth), block.text, block.bounds, block.confidence)
            }
            .toList()
        val messages = mergeAdjacentLines(positioned, screenWidth, screenHeight)
        if (messages.isEmpty()) return null

        return CapturedConversation(
            title = titleBlock.text,
            context = ChatContext(
                sourceApp = sourceApp,
                sourcePackage = sourcePackage,
                messages = messages,
                draft = "",
                confidence = messages.map { it.confidence }.average().toFloat(),
            ),
            persistable = !GROUP_TITLE_PATTERN.matches(titleBlock.text),
        )
    }

    private fun normalize(text: String): String = text.trim().replace(WHITESPACE_PATTERN, " ")

    private fun inferRole(block: OcrTextBlock, screenWidth: Int): MessageRole {
        val left = block.bounds.left.toFloat() / screenWidth
        val right = block.bounds.right.toFloat() / screenWidth
        val center = block.bounds.centerX.toFloat() / screenWidth
        return when {
            left <= OTHER_LEFT_EDGE && right < OTHER_RIGHT_LIMIT -> MessageRole.OTHER
            right >= ME_RIGHT_EDGE && left > ME_LEFT_LIMIT -> MessageRole.ME
            center <= OTHER_MAX_POSITION -> MessageRole.OTHER
            center >= ME_MIN_POSITION -> MessageRole.ME
            else -> MessageRole.UNKNOWN
        }
    }

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
