package app.nextsay.provider

import app.nextsay.api.MessageDto
import app.nextsay.api.ReplyRequestDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyPromptBuilderTest {
    @Test
    fun `builds system and JSON user messages without changing reviewed context`() {
        val request = ReplyRequestDto(
            messages = listOf(MessageDto("other", "明天能交吗", 0.9f)),
            draft = "可以",
            instruction = "委婉一点",
            relationship = "manager",
        )

        val messages = ReplyPromptBuilder(Gson()).build(request)

        assertEquals(listOf("system", "user"), messages.map { it.role })
        assertTrue(messages[0].content.contains("不得虚构"))
        assertTrue(messages[0].content.contains("只返回 JSON"))
        assertTrue(messages[1].content.contains("明天能交吗"))
        assertTrue(messages[1].content.contains("manager"))
        assertEquals("明天能交吗", request.messages.single().text)
    }
}
