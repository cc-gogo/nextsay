package app.nextsay.provider

import app.nextsay.api.ReplyCandidateDto
import com.google.gson.JsonElement

data class OpenAiMessageDto(
    val role: String,
    val content: String,
)

data class ResponseFormatDto(
    val type: String = "json_object",
)

data class ThinkingModeDto(
    val type: String,
)

data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    val temperature: Double = 0.7,
    val response_format: ResponseFormatDto? = ResponseFormatDto(),
    val max_tokens: Int? = null,
    val thinking: ThinkingModeDto? = null,
)

data class ChatCompletionResponseDto(
    val choices: List<ChatChoiceDto?>? = emptyList(),
)

data class ChatChoiceDto(
    val message: ChatChoiceMessageDto?,
    val finish_reason: JsonElement? = null,
)

data class ChatChoiceMessageDto(
    val content: JsonElement?,
    val reasoning_content: JsonElement? = null,
)

data class CandidateEnvelopeDto(
    val candidates: List<ReplyCandidateDto> = emptyList(),
)
