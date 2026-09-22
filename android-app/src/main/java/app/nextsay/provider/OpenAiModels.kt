package app.nextsay.provider

import app.nextsay.api.ReplyCandidateDto

data class OpenAiMessageDto(
    val role: String,
    val content: String,
)

data class ResponseFormatDto(
    val type: String = "json_object",
)

data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    val temperature: Double = 0.7,
    val response_format: ResponseFormatDto? = ResponseFormatDto(),
    val max_tokens: Int? = null,
)

data class ChatCompletionResponseDto(
    val choices: List<ChatChoiceDto> = emptyList(),
)

data class ChatChoiceDto(
    val message: ChatChoiceMessageDto,
)

data class ChatChoiceMessageDto(
    val content: String?,
)

data class CandidateEnvelopeDto(
    val candidates: List<ReplyCandidateDto> = emptyList(),
)
