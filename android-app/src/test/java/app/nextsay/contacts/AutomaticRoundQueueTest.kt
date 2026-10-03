package app.nextsay.contacts

import app.nextsay.context.*
import org.junit.Assert.*
import org.junit.Test

class AutomaticRoundQueueTest {
    @Test fun correctionOfEarlierUncertainTextDoesNotStrandAPendingReliableRound() {
        val queue = AutomaticRoundQueue()
        val old = listOf(ChatMessage(MessageRole.ME, "开始", 1f), ChatMessage(MessageRole.UNKNOWN, "模糊", .5f), ChatMessage(MessageRole.OTHER, "新消息", 1f))
        val offered = frame("新消息").copy(messages = old, replyRound = ReplyRoundSnapshot("测试", old))
        queue.offer(offered)
        val corrected = old.toMutableList().also { it[1] = ChatMessage(MessageRole.OTHER, "纠正后的文字", 1f) }
        val fresh = offered.copy(messages = corrected, replyRound = ReplyRoundSnapshot("测试", corrected))
        val ticket = queue.begin(fresh)
        assertNotNull("Only an older uncertain entry changed; the reliable new tail is still current", ticket)
        assertTrue(queue.matches(ticket!!, fresh))
        assertNull(queue.begin(fresh))
        queue.finish(ticket)
        assertFalse(queue.hasPending)
    }
    @Test fun sentSelfMessageCanStartSupplementButCannotMatchAnIncomingReplyWithSameWords() {
        val queue = AutomaticRoundQueue()
        val messages = listOf(ChatMessage(MessageRole.OTHER, "今晚有空吗", 1f), ChatMessage(MessageRole.ME, "有空", 1f))
        val sent = frame("有空").copy(messages = messages, replyRound = ReplyRoundSnapshot("测试", messages))
        queue.offer(sent)
        val ticket = queue.begin(sent)
        assertNotNull(ticket)
        assertTrue(queue.matches(ticket!!, sent))
        assertFalse(queue.matches(ticket, frame("有空")))
        queue.finish(ticket)
        assertFalse(queue.hasPending)
    }
    @Test fun oldHistoryWithTheSameTailCannotStartOrApprovePendingWork() {
        val queue = AutomaticRoundQueue()
        val current = frame("[图片]")
        queue.offer(current)
        val oldHistory = current.copy(replyRound = current.replyRound!!.copy(viewportRevision = 1))
        assertNull(queue.begin(oldHistory))
        val ticket = queue.begin(current)!!
        assertFalse(queue.matches(ticket, oldHistory))
    }
    @Test fun sameLastWordWithDifferentPrecedingEvidenceCannotApproveAResponse() {
        val queue = AutomaticRoundQueue()
        val current = frame("好的")
        queue.offer(current)
        val ticket = queue.begin(current)!!
        val old = current.copy(replyRound = current.replyRound!!.copy(visibleMessages = listOf(
            ChatMessage(MessageRole.ME, "旧的别的话题", 1f), ChatMessage(MessageRole.OTHER, "好的", 1f))))
        assertFalse(queue.matches(ticket, old))
    }
    @Test fun freshKeyboardSubsetStillMatchesButCoveredTailCannotApproveAResponse() {
        val queue = AutomaticRoundQueue()
        val c = frame("新消息")
        queue.offer(c)
        val ticket = queue.begin(c)!!
        assertTrue(queue.matches(ticket, c.copy(replyRound = c.replyRound!!.copy(visibleMessages = c.replyRound!!.visibleMessages.takeLast(1)))))
        assertFalse(queue.matches(ticket, c.copy(replyRound = c.replyRound!!.copy(tailObscured = true))))
    }
    @Test fun detectorAndQueueKeepAnIncomingRoundWhileAnEditorIsBusy() {
        val detector = IncomingRoundDetector()
        val queue = AutomaticRoundQueue()
        val base = frame("之前消息")
        detector.observe("a", base)
        val newMessages = base.messages + ChatMessage(MessageRole.OTHER, "之后消息", 1f)
        val updated = base.copy(messages = newMessages, replyRound = base.replyRound!!.copy(visibleMessages = newMessages))
        if (detector.observe("a", updated)) queue.offer(updated)
        // A second read is not a new event. Pending work must nevertheless survive.
        assertFalse(detector.observe("a", updated))
        assertNotNull(queue.begin(updated))
    }

    @Test fun openCandidateWindowDoesNotPreventAnIncomingRoundFromBeingQueued() {
        val detector = IncomingRoundDetector()
        val queue = AutomaticRoundQueue()
        val base = frame("上一条")
        detector.observe("a", base)
        val incoming = base.copy(messages = base.messages + ChatMessage(MessageRole.OTHER, "对方新回复", 1f),
            replyRound = base.replyRound!!.copy(visibleMessages = base.messages + ChatMessage(MessageRole.OTHER, "对方新回复", 1f)))
        assertTrue(detector.observe("a", incoming))
        queue.offer(incoming)
        assertNotNull(queue.begin(incoming))
    }
    private fun frame(text: String) = ChatContext("wechat", "com.tencent.mm",
        listOf(ChatMessage(MessageRole.ME, "开始", 1f), ChatMessage(MessageRole.OTHER, text, 1f)), "", 1f,
        ReplyRoundSnapshot("测试", listOf(ChatMessage(MessageRole.ME, "开始", 1f), ChatMessage(MessageRole.OTHER, text, 1f))))

    @Test fun busyObservationIsPendingUntilItCanActuallyStart() {
        val queue = AutomaticRoundQueue()
        queue.offer(frame("新消息"))
        assertTrue(queue.hasPending)
        val ticket = queue.begin(frame("新消息"))!!
        assertTrue(queue.isCurrent(ticket))
        assertNull(queue.begin(frame("新消息")))
        queue.finish(ticket)
        assertFalse(queue.hasPending)
    }
    @Test fun cancelledWorkCanResumeWithoutANewAccessibilityEvent() {
        val queue = AutomaticRoundQueue()
        queue.offer(frame("新消息"))
        val ticket = queue.begin(frame("新消息"))!!
        queue.defer(ticket)
        assertNotNull(queue.begin(frame("新消息")))
    }
    @Test fun newerRoundInvalidatesAnOldHttpResponseWithoutLosingTheNewestRound() {
        val queue = AutomaticRoundQueue()
        queue.offer(frame("第一条"))
        val old = queue.begin(frame("第一条"))!!
        queue.offer(frame("第二条"))
        assertFalse(queue.isCurrent(old))
        queue.finish(old)
        assertTrue(queue.hasPending)
        queue.defer(old)
        assertNotNull(queue.begin(frame("第二条")))
    }
    @Test fun noGenerationFromUncertainOrDifferentVisibleTail() {
        val queue = AutomaticRoundQueue()
        queue.offer(frame("新消息"))
        assertNull(queue.begin(frame("其他消息")))
        val unknown = frame("新消息").copy(messages = listOf(ChatMessage(MessageRole.UNKNOWN, "新消息", .4f)),
            replyRound = null)
        assertNull(queue.begin(unknown))
        assertTrue(queue.hasPending)
    }
    @Test fun explicitRoleWithOcrConfidenceAboveFloorCanStartAutomaticWork() {
        val queue = AutomaticRoundQueue()
        val original = frame("新消息")
        val visible = original.replyRound!!.visibleMessages.map { it.copy(confidence = .65f) }
        val current = original.copy(
            messages = visible,
            confidence = .65f,
            replyRound = original.replyRound.copy(visibleMessages = visible),
        )
        queue.offer(current)
        assertNotNull(queue.begin(current))
    }
    @Test fun leavingChatClearsPendingAndInvalidatesRunningResponse() {
        val queue = AutomaticRoundQueue()
        queue.offer(frame("新消息"))
        val ticket = queue.begin(frame("新消息"))!!
        queue.clear()
        assertFalse(queue.isCurrent(ticket))
        assertFalse(queue.hasPending)
    }
}
