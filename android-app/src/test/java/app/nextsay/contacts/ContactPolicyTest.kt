package app.nextsay.contacts

import app.nextsay.context.*
import org.junit.Assert.*
import org.junit.Test

class ContactPolicyTest {
    @Test fun correctingConsumedUncertainTextAloneDoesNotTriggerButNextMessageDoes() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "开始"))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.UNKNOWN to "模糊", MessageRole.OTHER to "可靠末尾")))
        val corrected = frame(MessageRole.ME to "开始", MessageRole.OTHER to "纠正后的文字", MessageRole.OTHER to "可靠末尾")
        assertFalse(detector.observe("a", corrected))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.OTHER to "纠正后的文字", MessageRole.OTHER to "可靠末尾", MessageRole.ME to "我补充一句")))
        assertTrue(detector.advancedToSelf)
    }
    @Test fun correctingUncertainAnchorNeverMakesAnUnrelatedReliableTailMatch() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "开始"))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.UNKNOWN to "模糊", MessageRole.OTHER to "可靠末尾")))
        assertFalse(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.OTHER to "纠正后的文字", MessageRole.OTHER to "无关的旧消息", MessageRole.ME to "另一条旧消息")))
    }
    @Test fun uncertainBaselineTailCannotWildcardMatchArbitraryHistory() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.UNKNOWN to "未确认"))
        assertFalse(detector.observe("a", frame(MessageRole.OTHER to "完全不同的历史", MessageRole.ME to "历史回复")))
    }
    @Test fun correctingEarlierUncertainOcrMustNotFreezeLaterIncomingRounds() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "开始"))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.UNKNOWN to "模糊", MessageRole.OTHER to "新消息一")))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.OTHER to "识别纠正了", MessageRole.OTHER to "新消息一", MessageRole.OTHER to "新消息二")))
        assertFalse(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.OTHER to "识别纠正了", MessageRole.OTHER to "新消息一", MessageRole.OTHER to "新消息二")))
    }
    @Test fun newlySentSelfMessageTriggersSupplementOnceButKeyboardSubsetsDoNot() {
        val detector = IncomingRoundDetector()
        val base = frame(MessageRole.OTHER to "今晚有空吗")
        assertFalse(detector.observe("a", base))
        val sent = frame(MessageRole.OTHER to "今晚有空吗", MessageRole.ME to "有空，八点之后")
        assertTrue(detector.observe("a", sent))
        assertTrue(detector.advancedToSelf)
        assertFalse(detector.observe("a", frame(MessageRole.ME to "有空，八点之后")))
        assertFalse(detector.observe("a", sent))
    }
    @Test fun uncertainOlderAppendDoesNotBlockReliableLatestMessage() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "开始"))
        val next = frame(MessageRole.ME to "开始", MessageRole.UNKNOWN to "较早的模糊文字", MessageRole.OTHER to "最新明确消息")
        assertTrue(detector.observe("a", next))
        assertFalse(detector.observe("a", next))
    }
    @Test fun obscuredFrameDoesNotConsumeAnIncomingMessageOrClearReliableAnchors() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "开始"))
        val updated = frame(MessageRole.ME to "开始", MessageRole.OTHER to "新消息")
        assertFalse(detector.observe("a", updated.copy(replyRound = updated.replyRound!!.copy(tailObscured = true))))
        assertTrue(detector.observe("a", updated))
    }
    @Test fun repeatedSelfFrameDoesNotInvalidateManualCandidatesAgain() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.OTHER to "你好"))
        detector.observe("a", frame(MessageRole.OTHER to "你好", MessageRole.ME to "你好呀"))
        assertTrue(detector.advancedToSelf)
        detector.observe("a", frame(MessageRole.OTHER to "你好", MessageRole.ME to "你好呀"))
        assertFalse(detector.advancedToSelf)
    }
    @Test fun keyboardExpansionDoesNotDestroyTheLatestMessageAnchor() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "我查一下资料", MessageRole.OTHER to "那我等你消息"))
        assertFalse(detector.observe("a", frame(MessageRole.OTHER to "那我等你消息")))
        assertTrue(detector.observe("a", frame(MessageRole.OTHER to "更早的消息", MessageRole.ME to "我查一下资料",
            MessageRole.OTHER to "那我等你消息", MessageRole.OTHER to "找到了吗")))
    }

    @Test fun reliableIncomingTailAfterOurCandidateRoundTriggersAgain() {
        val detector = IncomingRoundDetector()
        val first = frame(MessageRole.ME to "准备这么利索，那我可要出发了")
        assertFalse(detector.observe("a", first))
        val reply = frame(MessageRole.ME to "准备这么利索，那我可要出发了", MessageRole.OTHER to "好的，那你来接我吧")
        assertTrue("A new incoming tail after an existing candidate round must advance", detector.observe("a", reply))
        assertFalse(detector.observe("a", reply))
    }
    @Test fun ocrChangeInConsumedLineDoesNotHideReliableIncomingTail() {
        val detector = IncomingRoundDetector()
        val base = frame(MessageRole.ME to "准备这么利索，那我可要出发了", MessageRole.OTHER to "好呀")
        detector.observe("a", base)
        val changed = frame(
            MessageRole.ME to "准备这么利索，那我可要出发了。",
            MessageRole.OTHER to "好呀",
            MessageRole.OTHER to "那你什么时候来接我？",
        )
        assertTrue(detector.observe("a", changed))
        assertFalse(detector.observe("a", changed))
    }
    @Test fun repeatedManualRefreshFrameDoesNotCreateAnotherIncomingRound() {
        val detector = IncomingRoundDetector()
        val first = frame(MessageRole.ME to "准备出发")
        detector.observe("a", first)
        val incoming = frame(MessageRole.ME to "准备出发", MessageRole.OTHER to "好的，我来接你")
        assertTrue(detector.observe("a", incoming))
        assertFalse(detector.observe("a", incoming))
    }
    @Test fun uncertainNewTailCanBeRecognizedOnTheNextReliableCapture() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "开始"))
        assertFalse(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.UNKNOWN to "找到了吗")))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.OTHER to "找到了吗")))
    }
    @Test fun lowConfidenceNewTailIsNotConsumedBeforeAStableRead() {
        val detector = IncomingRoundDetector()
        val base = frame(MessageRole.ME to "开始")
        detector.observe("a", base)
        val low = frame(MessageRole.ME to "开始", MessageRole.OTHER to "找到了吗")
        assertFalse(detector.observe("a", low.copy(replyRound = low.replyRound!!.copy(
            visibleMessages = low.replyRound!!.visibleMessages.map { it.copy(confidence = .5f) }))))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "开始", MessageRole.OTHER to "找到了吗")))
    }
    @Test fun explicitBubbleSideWithOcrConfidenceAboveFloorTriggersAutomatically() {
        val detector = IncomingRoundDetector()
        val base = frame(MessageRole.ME to "开始")
        detector.observe("a", base)
        val incoming = frame(MessageRole.ME to "开始", MessageRole.OTHER to "找到了吗")
        val visible = incoming.replyRound!!.visibleMessages.map { it.copy(confidence = .65f) }
        assertTrue(detector.observe("a", incoming.copy(
            messages = visible,
            confidence = .65f,
            replyRound = incoming.replyRound.copy(visibleMessages = visible),
        )))
    }
    @Test fun litLockscreenAndScreenOffAreNeverEligible() {
        assertFalse(GenerationEligibility.unlocked(true, true, false))
        assertFalse(GenerationEligibility.unlocked(true, false, true))
        assertFalse(GenerationEligibility.unlocked(false, false, false))
        assertTrue(GenerationEligibility.unlocked(true, false, false))
    }
    @Test fun unlockReestablishesBaselineRatherThanReplayingMissedMessages() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "稍后聊"))
        detector.reset()
        assertFalse(detector.observe("a", frame(MessageRole.ME to "稍后聊", MessageRole.OTHER to "锁屏期间的消息")))
    }
    private fun frame(vararg messages: Pair<MessageRole, String>, revision: Long = 0) = ChatContext(
        "wechat", "com.tencent.mm", messages.map { ChatMessage(it.first, it.second, 1f) }, "", 1f,
        ReplyRoundSnapshot("测试", messages.map { ChatMessage(it.first, it.second, 1f) }, revision),
    )
    @Test fun baselineSelfScrollAndRepeatedFramesNeverTrigger() {
        val detector = IncomingRoundDetector()
        val first = frame(MessageRole.OTHER to "准备考试的资料找到了吗")
        assertFalse(detector.observe("a", first))
        assertFalse(detector.observe("a", first))
        assertTrue("A newly sent self message now requests a supplement", detector.observe("a", frame(MessageRole.OTHER to "准备考试的资料找到了吗", MessageRole.ME to "找到了")))
        assertFalse(detector.observe("a", frame(MessageRole.ME to "找到了", MessageRole.OTHER to "旧消息", revision = 1)))
    }
    @Test fun onlyForwardOverlapIncomingAdvancesAndNewContactIsBaseline() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "我查一下资料"))
        assertTrue(detector.observe("a", frame(MessageRole.ME to "我查一下资料", MessageRole.OTHER to "那我等你消息")))
        assertFalse(detector.observe("a", frame(MessageRole.ME to "我查一下资料", MessageRole.OTHER to "那我等你消息")))
        assertFalse(detector.observe("b", frame(MessageRole.OTHER to "那我等你消息")))
    }
    @Test fun noOverlapAndUnknownOrLowConfidenceCannotTrigger() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.ME to "开始"))
        assertFalse(detector.observe("a", frame(MessageRole.OTHER to "完全不重叠")))
        assertFalse(detector.observe("a", frame(MessageRole.OTHER to "完全不重叠", MessageRole.UNKNOWN to "归属未知")))
    }
    @Test fun increasedVisibleMessageCountWithExplicitTailTriggersDespiteOcrAnchorRewrite() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(MessageRole.OTHER to "更早一条", MessageRole.ME to "上一条已发送"))
        val updated = frame(
            MessageRole.OTHER to "更早一条",
            MessageRole.ME to "上一条已发送",
            MessageRole.OTHER to "对方新消息",
        ).copy(messages = listOf(
            ChatMessage(MessageRole.OTHER, "更早一条被重新识别", .62f),
            ChatMessage(MessageRole.ME, "上一条被重新识别", .62f),
            ChatMessage(MessageRole.OTHER, "对方新消息", .62f),
        ))
        assertTrue(detector.observe("a", updated.copy(
            replyRound = updated.replyRound!!.copy(visibleMessages = updated.messages),
        )))
    }
    @Test fun reducedVisibleMessageCountNeverUsesNewMessageFallback() {
        val detector = IncomingRoundDetector()
        detector.observe("a", frame(
            MessageRole.OTHER to "更早一条",
            MessageRole.ME to "上一条已发送",
        ))
        val keyboardSubset = frame(MessageRole.ME to "上一条已发送")
        assertFalse(detector.observe("a", keyboardSubset))
    }
    @Test fun renameEvidenceRejectsGenericAndRequiresOrderedDistinctiveMessages() {
        val generic = listOf(ChatMessage(MessageRole.OTHER, "你好", 1f), ChatMessage(MessageRole.ME, "好的", 1f))
        assertFalse(ContactEvidence.distinctiveOverlap(generic, generic))
        val distinctive = listOf(ChatMessage(MessageRole.OTHER, "明天准备一起去图书馆复习", 1f), ChatMessage(MessageRole.ME, "我会带上上星期借来的教材", 1f), ChatMessage(MessageRole.OTHER, "顺便看一下那份考试大纲", 1f))
        assertTrue(ContactEvidence.distinctiveOverlap(distinctive, distinctive))
        assertFalse(ContactEvidence.distinctiveOverlap(distinctive, distinctive.reversed()))
    }
    @Test fun memoryRetrievalExcludesUnrelatedAndRecentDuplicatesAndIsBounded() {
        val recent = listOf(ChatMessage(MessageRole.OTHER, "考试复习压力很大", 1f))
        val old = listOf("上个月讨论过考试复习计划", "昨天晚饭吃面条", "考试复习压力很大")
        val result = LocalMemorySelector.select(recent, old, 100)
        assertTrue(result.contains("上个月"))
        assertFalse(result.contains("晚饭"))
        assertEquals(1, result.lines().size)
        assertTrue(LocalMemorySelector.select(recent, old, 5).length <= 5)
    }
}
