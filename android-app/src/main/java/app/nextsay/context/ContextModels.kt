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
    // Local capture metadata only: never serialize titles or the unbounded visible frame.
    @Transient val replyRound: ReplyRoundSnapshot? = null,
    @Transient val contactId: String? = null,
    @Transient val generationExtras: app.nextsay.contacts.GenerationExtras? = null,
)

data class ReplyRoundSnapshot(
    val title: String,
    val visibleMessages: List<ChatMessage>,
    val viewportRevision: Long = 0L,
    val tailObscured: Boolean = false,
)
