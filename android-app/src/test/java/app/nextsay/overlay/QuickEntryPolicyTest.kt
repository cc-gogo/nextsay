package app.nextsay.overlay

import org.junit.Assert.*
import org.junit.Test

class QuickEntryPolicyTest {
    @Test fun waitingAndCancelledStatesAllowManualGeneration() {
        assertTrue(QuickEntryPolicy.enabled(QuickReplyModel.Waiting("等待新消息"), false))
        assertFalse(QuickEntryPolicy.enabled(QuickReplyModel.Waiting("等待新消息"), true))
        assertTrue("Background polling must not lock the manual entry", QuickEntryPolicy.enabled(QuickReplyModel.Loading("确认消息归属"), false))
        assertFalse("A user request already in progress must prevent duplicate requests", QuickEntryPolicy.enabled(QuickReplyModel.Loading("正在生成"), true))
    }
}
