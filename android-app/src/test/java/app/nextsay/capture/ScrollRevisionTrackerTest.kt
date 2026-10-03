package app.nextsay.capture

import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollRevisionTrackerTest {
    private fun lines(vararg texts: String) = texts.map { ChatMessage(MessageRole.OTHER, it, 1f) }
    private fun baseline(tracker: ScrollRevisionTracker) {
        tracker.onConversation("com.tencent.mm", "测试")
        tracker.onFrame(lines("当前消息"))
    }
    @Test fun firstKeyboardLayoutScrollAfterBaselineDoesNotDiscardPendingIncoming() {
        val tracker = ScrollRevisionTracker()
        tracker.onConversation("com.tencent.mm", "测试")
        tracker.onFrame(lines("当前消息"))
        tracker.onScroll(10, 1500)
        tracker.onFrame(lines("当前消息"))
        assertEquals(0L, tracker.revision)
        tracker.onScroll(10, 1500)
        tracker.onFrame(lines("较早历史"))
        assertEquals(1L, tracker.revision)
    }
    @Test fun keyboardResizeIsNotOldHistoryButSubsequentSameLayoutScrollStillIs() {
        val tracker = ScrollRevisionTracker()
        baseline(tracker)
        tracker.onScroll(10, null)
        tracker.onFrame(lines("较早历史"))
        tracker.onScroll(10, 1500)
        tracker.onFrame(lines("较早历史"))
        assertEquals(1L, tracker.revision)
        tracker.onScroll(10, 1500)
        tracker.onFrame(lines("更早历史"))
        assertEquals(2L, tracker.revision)
        tracker.onScroll(10, null)
        tracker.onFrame(lines("更早历史"))
        assertEquals(2L, tracker.revision)
    }
    @Test
    fun `first title capture retains current package list count for the next incoming auto scroll`() {
        val tracker = ScrollRevisionTracker()
        tracker.onScroll(5)
        tracker.onConversation("com.tencent.mm", "测试甲")
        tracker.onFrame(lines("第一条"))
        tracker.onScroll(6)
        tracker.onFrame(lines("第一条", "新的一条"))
        assertEquals(0L, tracker.revision)
    }

    @Test
    fun `different chat resets list count but repeated title capture does not`() {
        val tracker = ScrollRevisionTracker()
        tracker.onConversation("com.tencent.mm", "测试甲")
        tracker.onFrame(lines("第一条"))
        tracker.onScroll(5)
        tracker.onFrame(lines("第一条", "第二条"))
        tracker.onConversation("com.tencent.mm", "测试甲")
        tracker.onScroll(6)
        tracker.onFrame(lines("第一条", "第二条", "第三条"))
        assertEquals(0L, tracker.revision)
        tracker.onConversation("com.tencent.mm", "测试乙")
        tracker.onScroll(20)
        tracker.onFrame(lines("另一人首屏"))
        assertEquals(0L, tracker.revision)
    }

    @Test
    fun `same item count needs history evidence while append does not change revision`() {
        val tracker = ScrollRevisionTracker()
        baseline(tracker)
        tracker.onScroll(10)
        tracker.onFrame(lines("当前消息", "新增消息"))
        assertEquals(0L, tracker.revision)
        tracker.onScroll(10)
        tracker.onFrame(lines("旧历史"))
        assertEquals(1L, tracker.revision)
        tracker.onScroll(11)
        tracker.onFrame(lines("旧历史", "新增历史末尾"))
        assertEquals(1L, tracker.revision)
        tracker.onScroll(11)
        tracker.onFrame(lines("更旧历史"))
        assertEquals(2L, tracker.revision)
    }

    @Test
    fun `missing or decreasing item count scrolls are conservative viewport changes`() {
        val tracker = ScrollRevisionTracker()
        baseline(tracker)
        tracker.onScroll(0)
        tracker.onFrame(lines("当前消息", "新增消息"))
        assertEquals(0L, tracker.revision)
        tracker.onScroll(-1)
        tracker.onFrame(lines("旧历史"))
        tracker.onScroll(5)
        tracker.onFrame(lines("旧历史"))
        tracker.onScroll(4)
        tracker.onFrame(lines("更旧历史"))
        assertEquals(2L, tracker.revision)
    }

    @Test
    fun `package change forgets old list count without making false append evidence`() {
        val tracker = ScrollRevisionTracker()
        tracker.onScroll(5)
        tracker.resetList()
        tracker.onScroll(20)
        tracker.onFrame(lines("新聊天"))
        assertEquals(0L, tracker.revision)
    }
}
