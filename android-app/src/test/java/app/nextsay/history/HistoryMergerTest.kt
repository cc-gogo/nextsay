package app.nextsay.history

import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryMergerTest {
    private val merger = HistoryMerger()

    @Test
    fun `bootstraps empty history from visible messages`() {
        val visible = listOf(message(MessageRole.OTHER, "你好"), message(MessageRole.ME, "你好呀"))

        val result = merger.merge(emptyList(), visible)

        assertEquals(visible, result.combined)
        assertEquals(visible, result.appended)
        assertTrue(result.reliableOverlap)
    }

    @Test
    fun `appends only messages after longest suffix prefix overlap`() {
        val existing = listOf(
            message(MessageRole.OTHER, "明天有空吗"),
            message(MessageRole.ME, "下午可以"),
            message(MessageRole.OTHER, "那三点见"),
        )
        val visible = listOf(
            message(MessageRole.ME, "下午可以"),
            message(MessageRole.OTHER, "那三点见"),
            message(MessageRole.ME, "好的"),
        )

        val result = merger.merge(existing, visible)

        assertEquals(listOf("明天有空吗", "下午可以", "那三点见", "好的"), result.combined.map { it.text })
        assertEquals(listOf("好的"), result.appended.map { it.text })
        assertTrue(result.reliableOverlap)
    }

    @Test
    fun `keeps repeated short messages when their sequence positions differ`() {
        val existing = listOf(
            message(MessageRole.OTHER, "好的"),
            message(MessageRole.ME, "好的"),
        )
        val visible = listOf(
            message(MessageRole.ME, "好的"),
            message(MessageRole.OTHER, "好的"),
        )

        val result = merger.merge(existing, visible)

        assertEquals(3, result.combined.size)
        assertEquals(MessageRole.OTHER, result.combined.last().role)
    }

    @Test
    fun `does not persist unrelated viewport into existing history`() {
        val existing = listOf(message(MessageRole.OTHER, "昨天的消息"))
        val visible = listOf(message(MessageRole.ME, "完全不同的内容"))

        val result = merger.merge(existing, visible)

        assertEquals(existing, result.combined)
        assertTrue(result.appended.isEmpty())
        assertFalse(result.reliableOverlap)
    }

    private fun message(role: MessageRole, text: String) = ChatMessage(role, text, 0.9f)
}
