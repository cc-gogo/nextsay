package app.nextsay.history

import app.nextsay.capture.CapturedConversation
import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.history.db.ConversationDao
import app.nextsay.history.db.ConversationEntity
import app.nextsay.history.db.MessageEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryRepositoryTest {
    @Test
    fun `new conversation stores encrypted message text`() = runTest {
        val dao = FakeConversationDao()
        val repository = ConversationHistoryRepository(dao, PrefixCipher())

        val result = repository.mergeAndLoad(capture("小明", "你好"))

        assertTrue(result.persisted)
        assertEquals(listOf("你好"), result.messages.map { it.text })
        assertEquals(listOf("encrypted:你好"), dao.messages.map { it.encryptedText })
    }

    @Test
    fun `different visible titles keep separate histories`() = runTest {
        val dao = FakeConversationDao()
        val repository = ConversationHistoryRepository(dao, PrefixCipher())

        repository.mergeAndLoad(capture("小明", "你好"))
        repository.mergeAndLoad(capture("小红", "在吗"))

        assertEquals(2, dao.conversations.size)
        assertEquals(2, dao.messages.map { it.conversationId }.distinct().size)
    }

    @Test
    fun `unrelated viewport is usable once but does not pollute stored history`() = runTest {
        val dao = FakeConversationDao()
        val repository = ConversationHistoryRepository(dao, PrefixCipher())
        repository.mergeAndLoad(capture("小明", "昨天的消息"))

        val result = repository.mergeAndLoad(capture("小明", "完全不同的消息", role = MessageRole.ME))

        assertFalse(result.persisted)
        assertEquals(1, dao.messages.size)
        assertEquals(listOf("完全不同的消息"), result.messages.map { it.text })
    }

    @Test
    fun `excluded title uses visible context without persistence`() = runTest {
        val dao = FakeConversationDao()
        val repository = ConversationHistoryRepository(dao, PrefixCipher())
        repository.excludeTitle("com.tencent.mm", "小明")

        val result = repository.mergeAndLoad(capture("小明", "你好"))

        assertFalse(result.persisted)
        assertTrue(dao.messages.isEmpty())
        assertEquals(listOf("你好"), result.messages.map { it.text })
    }

    @Test
    fun `clear all removes conversations messages and exclusions`() = runTest {
        val dao = FakeConversationDao()
        val repository = ConversationHistoryRepository(dao, PrefixCipher())
        repository.mergeAndLoad(capture("小明", "你好"))
        repository.excludeTitle("com.tencent.mm", "小红")

        repository.clearAll()

        assertTrue(dao.conversations.isEmpty())
        assertTrue(dao.messages.isEmpty())
        assertFalse(dao.isTitleExcluded("com.tencent.mm", "小红"))
    }

    private fun capture(title: String, text: String, role: MessageRole = MessageRole.OTHER) =
        CapturedConversation(
            title = title,
            context = ChatContext(
                sourceApp = "wechat",
                sourcePackage = "com.tencent.mm",
                messages = listOf(ChatMessage(role, text, 0.9f)),
                draft = "",
                confidence = 0.9f,
            ),
            persistable = true,
        )

    private class PrefixCipher : MessageCipher {
        override fun encrypt(plaintext: String): String = "encrypted:$plaintext"
        override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("encrypted:")
    }

    private class FakeConversationDao : ConversationDao {
        val conversations = mutableListOf<ConversationEntity>()
        val messages = mutableListOf<MessageEntity>()
        private val excluded = mutableSetOf<Pair<String, String>>()

        override suspend fun findConversation(sourcePackage: String, normalizedTitle: String): ConversationEntity? =
            conversations.firstOrNull {
                it.sourcePackage == sourcePackage && it.normalizedTitle == normalizedTitle
            }

        override suspend fun insertConversation(conversation: ConversationEntity): Long {
            val id = (conversations.maxOfOrNull { it.id } ?: 0L) + 1L
            conversations += conversation.copy(id = id)
            return id
        }

        override suspend fun loadMessages(conversationId: Long): List<MessageEntity> =
            messages.filter { it.conversationId == conversationId }.sortedBy { it.id }

        override suspend fun insertMessages(items: List<MessageEntity>) {
            items.forEach { item ->
                val id = (messages.maxOfOrNull { it.id } ?: 0L) + 1L
                messages += item.copy(id = id)
            }
        }

        override suspend fun isTitleExcluded(sourcePackage: String, normalizedTitle: String): Boolean =
            sourcePackage to normalizedTitle in excluded

        override suspend fun excludeTitle(sourcePackage: String, normalizedTitle: String) {
            excluded += sourcePackage to normalizedTitle
        }

        override suspend fun clearMessages() {
            messages.clear()
        }

        override suspend fun clearConversations() {
            conversations.clear()
        }

        override suspend fun clearExcludedTitles() {
            excluded.clear()
        }
    }
}
