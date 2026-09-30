package app.nextsay.history

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage

class GenerationContextBuilder {
    fun build(
        template: ChatContext,
        messages: List<ChatMessage>,
        maxMessages: Int = 20,
        maxCharacters: Int = 3_000,
    ): ChatContext {
        require(maxMessages > 0) { "maxMessages must be positive" }
        require(maxCharacters > 0) { "maxCharacters must be positive" }

        val bounded = messages.takeLast(maxMessages).toMutableList()
        while (bounded.size > 1 && bounded.sumOf { it.text.length } > maxCharacters) {
            bounded.removeAt(0)
        }
        if (bounded.size == 1 && bounded[0].text.length > maxCharacters) {
            bounded[0] = bounded[0].copy(text = bounded[0].text.takeLast(maxCharacters))
        }
        val confidence = bounded.takeIf { it.isNotEmpty() }
            ?.map { it.confidence }
            ?.average()
            ?.toFloat()
            ?: 0f
        return template.copy(messages = bounded, confidence = confidence)
    }
}
