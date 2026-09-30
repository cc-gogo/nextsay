package app.nextsay.history

import app.nextsay.capture.CapturedConversation
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.history.db.ConversationDao
import app.nextsay.history.db.ConversationEntity
import app.nextsay.history.db.MessageEntity

data class HistoryLoadResult(
    val messages: List<ChatMessage>,
    val persisted: Boolean,
)

class ConversationHistoryRepository(
    private val dao: ConversationDao,
    private val cipher: MessageCipher,
    private val merger: HistoryMerger = HistoryMerger(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun mergeAndLoad(capture: CapturedConversation): HistoryLoadResult {
        if (!capture.persistable) return HistoryLoadResult(capture.context.messages, persisted = false)
        val normalizedTitle = normalizeTitle(capture.title)
        if (dao.isTitleExcluded(capture.context.sourcePackage, normalizedTitle)) {
            return HistoryLoadResult(capture.context.messages, persisted = false)
        }

        val conversationId = getOrCreateConversation(capture, normalizedTitle)
        val existing = dao.loadMessages(conversationId).map { entity ->
            ChatMessage(
                role = MessageRole.entries.firstOrNull { it.wireValue == entity.role } ?: MessageRole.UNKNOWN,
                text = cipher.decrypt(entity.encryptedText),
                confidence = entity.confidence,
            )
        }
        val merge = merger.merge(existing, capture.context.messages)
        if (merge.reliableOverlap && merge.appended.isNotEmpty()) {
            val observedAt = now()
            dao.insertMessages(merge.appended.map { message ->
                MessageEntity(
                    conversationId = conversationId,
                    role = message.role.wireValue,
                    encryptedText = cipher.encrypt(message.text),
                    confidence = message.confidence,
                    observedAt = observedAt,
                )
            })
        }
        val generationMessages = if (merge.reliableOverlap) merge.combined else capture.context.messages
        return HistoryLoadResult(generationMessages, persisted = merge.reliableOverlap)
    }

    suspend fun excludeTitle(sourcePackage: String, title: String) {
        dao.excludeTitle(sourcePackage, normalizeTitle(title))
    }

    suspend fun clearAll() {
        dao.clearMessages()
        dao.clearConversations()
        dao.clearExcludedTitles()
    }

    private suspend fun getOrCreateConversation(
        capture: CapturedConversation,
        normalizedTitle: String,
    ): Long {
        dao.findConversation(capture.context.sourcePackage, normalizedTitle)?.let { return it.id }
        val inserted = dao.insertConversation(
            ConversationEntity(
                sourcePackage = capture.context.sourcePackage,
                normalizedTitle = normalizedTitle,
                displayTitle = capture.title,
                updatedAt = now(),
            ),
        )
        if (inserted > 0) return inserted
        return requireNotNull(dao.findConversation(capture.context.sourcePackage, normalizedTitle)).id
    }

    private fun normalizeTitle(title: String): String =
        title.trim().replace(WHITESPACE_PATTERN, " ").lowercase()

    private companion object {
        val WHITESPACE_PATTERN = Regex("\\s+")
    }
}
