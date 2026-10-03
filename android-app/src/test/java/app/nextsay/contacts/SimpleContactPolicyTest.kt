package app.nextsay.contacts

import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import org.junit.Assert.*
import org.junit.Test

class SimpleContactPolicyTest {
    private val conversation = listOf(
        ChatMessage(MessageRole.OTHER, "周六一起去看那场电影好吗", .95f),
        ChatMessage(MessageRole.ME, "好呀我来订下午三点的票", .95f),
        ChatMessage(MessageRole.OTHER, "看完我们再去附近吃个晚饭", .95f),
    )

    @Test fun newProfileIncludesLongTermMemoryByDefault() {
        val profile = ContactEntity("id", "com.tencent.mm", "default", "encrypted")
        assertTrue(profile.saveHistory)
        assertTrue(profile.useMemory)
        assertFalse(profile.autoEnabled)
    }

    @Test fun reliableConversationCanMatchAnOlderViewportInsideSavedHistory() {
        val saved = listOf(ChatMessage(MessageRole.ME, "更早的内容", .9f)) + conversation +
            ChatMessage(MessageRole.ME, "好的到时见", .9f)
        assertTrue(ContactEvidence.distinctiveOverlap(saved, conversation))
    }

    @Test fun similarWordsWithDifferentSpeakersDoNotIdentifyAPerson() {
        assertFalse(ContactEvidence.distinctiveOverlap(conversation, conversation.map { it.copy(role = MessageRole.ME) }))
    }

    @Test fun greetingsAreInsufficientIdentityEvidence() {
        val greetings = listOf("你好", "好的", "嗯嗯").map { ChatMessage(MessageRole.OTHER, it, .95f) }
        assertFalse(ContactEvidence.distinctiveOverlap(greetings, greetings))
    }

    @Test fun uncertainOcrDoesNotIdentifyAPerson() {
        assertFalse(ContactEvidence.distinctiveOverlap(conversation, conversation.map { it.copy(confidence = .4f) }))
    }
}
