package app.nextsay.history

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationContextBuilderTest {
    private val builder = GenerationContextBuilder()
    private val template = ChatContext("wechat", "com.tencent.mm", emptyList(), "", 0.9f)

    @Test
    fun `keeps only the most recent message limit`() {
        val messages = (0 until 25).map { ChatMessage(MessageRole.OTHER, "m$it", 0.9f) }

        val result = builder.build(template, messages, maxMessages = 20, maxCharacters = 3000)

        assertEquals(20, result.messages.size)
        assertEquals("m5", result.messages.first().text)
        assertEquals("m24", result.messages.last().text)
    }

    @Test
    fun `drops oldest messages until total text fits character limit`() {
        val messages = listOf(
            ChatMessage(MessageRole.OTHER, "1111", 0.9f),
            ChatMessage(MessageRole.ME, "2222", 0.9f),
            ChatMessage(MessageRole.OTHER, "3333", 0.9f),
        )

        val result = builder.build(template, messages, maxMessages = 20, maxCharacters = 8)

        assertEquals(listOf("2222", "3333"), result.messages.map { it.text })
        assertTrue(result.messages.sumOf { it.text.length } <= 8)
    }
}
