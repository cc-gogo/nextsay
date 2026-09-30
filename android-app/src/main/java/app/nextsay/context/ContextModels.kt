package app.nextsay.context

enum class MessageRole(val wireValue: String) {
    ME("me"),
    OTHER("other"),
    UNKNOWN("unknown"),
}

data class ChatMessage(
    val role: MessageRole,
    val text: String,
    val confidence: Float,
)

data class ChatContext(
    val sourceApp: String,
    val sourcePackage: String,
    val messages: List<ChatMessage>,
    val draft: String,
    val confidence: Float,
)
