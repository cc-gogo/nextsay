package app.nextsay.context

class UnsupportedChatPackageException(packageName: String) :
    IllegalArgumentException("Unsupported chat package: $packageName")

class ContextNormalizer {
    fun normalize(
        packageName: String,
        nodes: List<NodeSnapshot>,
        screenWidth: Int,
    ): ChatContext {
        require(screenWidth > 0) { "screenWidth must be positive" }
        val sourceApp = SUPPORTED_PACKAGES[packageName]
            ?: throw UnsupportedChatPackageException(packageName)

        val ordered = nodes.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        val draft = ordered.firstOrNull { it.editable && it.focused && !it.password }
            ?.visibleText()
            .orEmpty()

        val messageNodes = if (sourceApp == "qq") {
            ordered.filter { it.viewId?.substringAfter(":id/") in QQ_MESSAGE_VIEW_IDS }
        } else {
            ordered
        }
        val lastTopByText = mutableMapOf<String, Int>()
        val messages = messageNodes.mapNotNull { node ->
            if (node.editable || node.password) return@mapNotNull null
            val text = node.visibleText()
            if (text.isEmpty() || isMetadata(text)) return@mapNotNull null
            val previousTop = lastTopByText[text]
            if (previousTop != null && kotlin.math.abs(node.bounds.top - previousTop) <= DUPLICATE_Y_TOLERANCE) {
                return@mapNotNull null
            }
            lastTopByText[text] = node.bounds.top

            val role = inferRole(sourceApp, node, ordered, screenWidth)
            ChatMessage(role, text, if (role == MessageRole.UNKNOWN) 0.4f else 0.85f)
        }

        val confidence = if (messages.isEmpty()) 0f else messages.map { it.confidence }.average().toFloat()
        return ChatContext(sourceApp, packageName, messages, draft, confidence)
    }

    private fun NodeSnapshot.visibleText(): String =
        (text?.takeIf { it.isNotBlank() } ?: contentDescription.orEmpty()).trim()

    private fun isMetadata(text: String): Boolean =
        TIME_PATTERN.matches(text) || text in CHAT_CHROME

    private fun inferRole(
        sourceApp: String,
        message: NodeSnapshot,
        nodes: List<NodeSnapshot>,
        screenWidth: Int,
    ): MessageRole {
        if (sourceApp == "qq") {
            val profileDescription = nodes.asSequence()
                .mapNotNull { node ->
                    val description = node.contentDescription?.trim()
                    val overlap = verticalOverlap(node.bounds, message.bounds)
                    if (description?.endsWith("资料卡") == true && overlap > 0) description to overlap else null
                }
                .maxByOrNull { it.second }
                ?.first
            if (profileDescription != null) {
                return if (profileDescription == "我的资料卡") MessageRole.ME else MessageRole.OTHER
            }
        }

        val horizontalPosition = message.bounds.centerX.toFloat() / screenWidth
        return when {
            horizontalPosition <= 0.40f -> MessageRole.OTHER
            horizontalPosition >= 0.60f -> MessageRole.ME
            else -> MessageRole.UNKNOWN
        }
    }

    private fun verticalOverlap(first: ScreenRect, second: ScreenRect): Int =
        minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)

    private companion object {
        val SUPPORTED_PACKAGES = mapOf(
            "com.tencent.mm" to "wechat",
            "com.tencent.mobileqq" to "qq",
            "com.tencent.tim" to "qq",
            "com.tencent.qqlite" to "qq",
        )
        val TIME_PATTERN = Regex(
            "^[|｜丨]?\\s*(?:(?:\\d{4}年)?\\d{1,2}月\\d{1,2}日|今天|昨天|前天|星期[一二三四五六日天]|周[一二三四五六日天])?\\s*" +
                "(?:凌晨|早上|上午|中午|下午|傍晚|晚上)?\\s*(?:[01]?\\d|2[0-3])[:：][0-5]\\d$",
        )
        val CHAT_CHROME = setOf("返回", "聊天信息", "更多", "发送", "语音", "表情", "相册", "拍摄")
        val QQ_MESSAGE_VIEW_IDS = setOf("mjh", "mjn", "mjo")
        const val DUPLICATE_Y_TOLERANCE = 24
    }
}
