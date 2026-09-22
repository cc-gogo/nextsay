package app.nextsay.provider

import app.nextsay.api.ReplyRequestDto
import com.google.gson.Gson

class ReplyPromptBuilder(
    private val gson: Gson,
) {
    fun build(request: ReplyRequestDto): List<OpenAiMessageDto> {
        val payload = linkedMapOf<String, Any>(
            "relationship" to request.relationship,
            "locale" to request.locale,
            "messages" to request.messages,
            "draft" to request.draft,
            "instruction" to request.instruction,
        )
        return listOf(
            OpenAiMessageDto("system", SYSTEM_PROMPT),
            OpenAiMessageDto("user", gson.toJson(payload)),
        )
    }

    private companion object {
        val SYSTEM_PROMPT = """
            你是 NextSay 的回复生成器。请站在用户立场，根据给定对话生成下一句可直接发送的回复。
            规则：
            1. 只使用提供的上下文，不得虚构日期、承诺、事实、身份或关系。
            2. 不要解释如何回复，不要输出思考过程。
            3. 不得自动发送；你的职责仅是生成候选文本供用户确认。
            4. 除非用户明确要求长文，否则每条回复保持简短。
            5. 使用请求中的语言，默认简体中文。
            6. 三条回复含义和措辞必须明显不同。
            7. 只返回 JSON，不要使用 Markdown。
            返回格式：
            {"candidates":[{"style":"concise","text":"..."},{"style":"tactful","text":"..."},{"style":"natural","text":"..."}]}
        """.trimIndent()
    }
}
