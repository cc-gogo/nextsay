package app.nextsay.capture

import app.nextsay.contacts.IncomingRoundDetector
import app.nextsay.context.*
import org.junit.Assert.*
import org.junit.Test

class ConfirmedScrollRevisionTest {
    @Test fun twoKeyboardScrollEventsBeforeOneFrameDoNotDiscardTheSharedTail() {
        val tracker = ScrollRevisionTracker()
        tracker.onConversation("com.tencent.mm", "测试")
        tracker.onFrame(lines("前一句", "最新一句"))
        tracker.onScroll(10, 1500)
        tracker.onScroll(10, 1500)
        tracker.onFrame(lines("最新一句"))
        assertEquals(0L, tracker.revision)
    }
    @Test fun correctingEarlierUnknownTextWithASharedTailIsNotHistoryMovement() {
        val tracker = ScrollRevisionTracker()
        tracker.onConversation("com.tencent.mm", "测试")
        tracker.onFrame(listOf(ChatMessage(MessageRole.UNKNOWN, "模糊", .4f)) + lines("最新一句"))
        tracker.onScroll(0)
        tracker.onFrame(lines("纠正后的文字", "最新一句"))
        assertEquals(0L, tracker.revision)
    }
    private fun lines(vararg texts: String) = texts.map { ChatMessage(MessageRole.OTHER, it, 1f) }
    private fun context(messages: List<ChatMessage>, revision: Long) = ChatContext("wechat", "com.tencent.mm", messages, "", 1f, ReplyRoundSnapshot("测试", messages, revision))
    @Test fun unknownCountAutoScrollDoesNotTurnANewBubbleIntoABaseline() {
        val tracker = ScrollRevisionTracker()
        val detector = IncomingRoundDetector()
        tracker.onConversation("com.tencent.mm", "测试")
        tracker.onFrame(lines("之前消息"))
        detector.observe("a", context(lines("之前消息"), tracker.revision))
        tracker.onScroll(0)
        assertEquals(0L, tracker.revision)
        tracker.onFrame(lines("之前消息", "对方新消息"))
        assertEquals(0L, tracker.revision)
        assertTrue(detector.observe("a", context(lines("之前消息", "对方新消息"), tracker.revision)))
    }
    @Test fun confirmedOlderHistoryChangesRevisionButRepeatedLayoutDoesNot() {
        val tracker = ScrollRevisionTracker()
        tracker.onConversation("com.tencent.mm", "测试")
        tracker.onFrame(lines("当前最后一句"))
        tracker.onScroll(0)
        tracker.onFrame(lines("当前最后一句"))
        assertEquals(0L, tracker.revision)
        tracker.onScroll(0)
        tracker.onFrame(lines("更早的历史"))
        assertEquals(1L, tracker.revision)
    }
    @Test fun uncertainFrameDoesNotDiscardPendingAppendBeforeItsReliableRead() {
        val tracker = ScrollRevisionTracker()
        tracker.onConversation("com.tencent.mm", "测试")
        tracker.onFrame(lines("当前最后一句"))
        tracker.onScroll(0)
        tracker.onFrame(lines("当前最后一句") + ChatMessage(MessageRole.UNKNOWN, "新消息", .4f))
        assertEquals(0L, tracker.revision)
        tracker.onFrame(lines("当前最后一句", "新消息"))
        assertEquals(0L, tracker.revision)
    }
}
