package app.nextsay.api

data class MessageDto(
    val role: String,
    val text: String,
    val confidence: Float,
)

data class ReplyRequestDto(
    val messages: List<MessageDto>,
    val draft: String,
    val instruction: String,
    val relationship: String,
    val locale: String = "zh-CN",
)

data class ReplyCandidateDto(
    val style: String,
    val text: String,
)

data class ReplyResponseDto(
    val candidates: List<ReplyCandidateDto>,
    val requestId: String,
)
