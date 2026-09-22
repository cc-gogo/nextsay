package app.nextsay.overlay

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickReplyPresenterTest {
    private val presenter = QuickReplyPresenter()
    private val context = ChatContext(
        sourceApp = "wechat",
        sourcePackage = "com.tencent.mm",
        messages = listOf(ChatMessage(MessageRole.OTHER, "今晚有空吗", 0.9f)),
        draft = "",
        confidence = 0.9f,
    )

    @Test
    fun `loading state becomes compact loading card`() {
        assertEquals(QuickReplyModel.Loading("正在生成回复…"), presenter.render(OverlayState.Loading(context)))
    }

    @Test
    fun `three results become candidate cards`() {
        val candidates = listOf(
            ReplyCandidate("brief", "可以"),
            ReplyCandidate("warm", "可以呀"),
            ReplyCandidate("formal", "可以，我会准时到"),
        )

        assertEquals(QuickReplyModel.Candidates(candidates), presenter.render(OverlayState.Results(context, candidates)))
    }

    @Test
    fun `error becomes retryable error card`() {
        assertEquals(
            QuickReplyModel.Error("网络不可用"),
            presenter.render(OverlayState.Error(context, "网络不可用")),
        )
    }

    @Test
    fun `quick error offers diagnostic action only when ID exists`() {
        val model = presenter.render(OverlayState.Error(context, "失败", "evt-1"))

        assertEquals("evt-1", (model as QuickReplyModel.Error).diagnosticId)
    }

    @Test
    fun `idle and preview stay hidden in quick surface`() {
        assertEquals(QuickReplyModel.Hidden, presenter.render(OverlayState.Idle))
        assertEquals(QuickReplyModel.Hidden, presenter.render(OverlayState.Preview(context)))
    }

    @Test
    fun `wechat candidate copies without input insertion`() {
        val copied = mutableListOf<ReplyCandidate>()
        var insertions = 0
        var closed = 0
        var failed = 0
        val handler = QuickCandidateHandler(
            copy = { copied += it; true },
            insert = { insertions += 1 },
            onCopied = { closed += 1 },
            onCopyFailed = { failed += 1 },
        )
        val candidate = ReplyCandidate("brief", "收到")

        handler.select(candidate, "com.tencent.mm")

        assertEquals(listOf(candidate), copied)
        assertEquals(0, insertions)
        assertEquals(1, closed)
        assertEquals(0, failed)
    }

    @Test
    fun `qq candidate copies before input insertion`() {
        val actions = mutableListOf<String>()
        val handler = QuickCandidateHandler(
            copy = { actions += "copy"; true },
            insert = { actions += "insert" },
            onCopied = { actions += "close" },
            onCopyFailed = { actions += "failed" },
        )

        handler.select(ReplyCandidate("brief", "收到"), "com.tencent.mobileqq")

        assertEquals(listOf("copy", "close", "insert"), actions)
    }

    @Test
    fun `copy failure keeps cards open and prevents insertion`() {
        var closed = 0
        var failed = 0
        var insertions = 0
        val handler = QuickCandidateHandler(
            copy = { false },
            insert = { insertions += 1 },
            onCopied = { closed += 1 },
            onCopyFailed = { failed += 1 },
        )

        handler.select(ReplyCandidate("brief", "收到"), "com.tencent.mobileqq")

        assertEquals(0, closed)
        assertEquals(1, failed)
        assertEquals(0, insertions)
    }
}
