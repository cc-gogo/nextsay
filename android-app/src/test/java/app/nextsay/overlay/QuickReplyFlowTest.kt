package app.nextsay.overlay

import app.nextsay.capture.ContextCaptureResult
import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.context.ReplyRoundSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class QuickReplyFlowTest {
    @Test fun `background new round does not erase requirements written afterwards for the same round`() = runTest {
        val fixture = Fixture(context("原消息"))
        fixture.request()
        fixture.current = context("原消息", "新消息")
        assertTrue(fixture.flow.observe(fixture.current))
        fixture.instruction = "针对新消息至少50字"
        fixture.request()
        assertEquals("针对新消息至少50字", fixture.requests.last().second)
    }
    @Test
    fun `no overlap after scroll retains original anchor for next genuine incoming`() = runTest {
        val fixture = Fixture(context("当前末尾"))
        fixture.request()
        fixture.instruction = "保留"
        fixture.current = context("完全不同的旧记录").let {
            it.copy(replyRound = it.replyRound!!.copy(viewportRevision = 1L))
        }
        fixture.request(toggle = true)
        assertEquals(1, fixture.requests.size)
        assertEquals("保留", fixture.instruction)
        fixture.current = context("当前末尾", "新增回复").let {
            it.copy(replyRound = it.replyRound!!.copy(viewportRevision = 1L))
        }
        fixture.request()
        assertEquals("", fixture.instruction)
        assertEquals("新增回复", fixture.requests.last().first.messages.last().text)
    }

    @Test
    fun `overlapping downward scroll retains requirement then actual incoming starts clean round`() = runTest {
        val fixture = Fixture(context("旧消息甲", "旧消息乙", "旧消息丙"))
        fixture.request()
        fixture.instruction = "不能因为滚动丢失"
        fixture.current = context("旧消息丙", "早已存在丁", "早已存在戊").let {
            it.copy(replyRound = it.replyRound!!.copy(viewportRevision = 1L))
        }
        fixture.request(toggle = true)
        assertEquals("Scrolling must not automatically generate a new round", 1, fixture.requests.size)
        assertEquals("不能因为滚动丢失", fixture.instruction)
        fixture.current = context("早已存在丁", "早已存在戊", "真正新增己").let {
            it.copy(replyRound = it.replyRound!!.copy(viewportRevision = 1L))
        }
        fixture.request()
        assertEquals("真正新增己", fixture.requests.last().first.messages.last().text)
        assertEquals("", fixture.instruction)
    }

    @Test
    fun `explicit generate after scroll reads current viewport without discarding requirement`() = runTest {
        val fixture = Fixture(context("旧消息甲", "旧消息乙"))
        fixture.request()
        fixture.instruction = "保留"
        fixture.current = context("旧消息乙", "旧消息丙").let {
            it.copy(replyRound = it.replyRound!!.copy(viewportRevision = 1L))
        }
        fixture.request()
        assertEquals("旧消息丙", fixture.requests.last().first.messages.last().text)
        assertEquals("保留", fixture.requests.last().second)
        assertEquals("保留", fixture.instruction)
    }

    @Test
    fun `one trigger click with open candidates generates latest incoming and clears both instructions`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "至少50字"
        fixture.request(toggle = false)
        fixture.current = context("你好", "我已回复", "对方又回复了")
        fixture.request(toggle = true)

        assertEquals(3, fixture.requests.size)
        assertEquals("对方又回复了", fixture.requests.last().first.messages.last().text)
        assertEquals("", fixture.requests.last().second)
        assertEquals("", fixture.instruction)
        assertTrue(fixture.controller.state.value is OverlayState.Results)
        assertEquals(0, fixture.dismissals)
    }

    @Test
    fun `unchanged trigger collapses and reopened same round retains saved instructions`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "简短一点"
        fixture.request(toggle = true)
        assertEquals(OverlayState.Idle, fixture.controller.state.value)
        assertEquals(1, fixture.requests.size)
        assertEquals(1, fixture.dismissals)
        assertEquals("简短一点", fixture.instruction)
        fixture.request()
        assertEquals("简短一点", fixture.requests.last().second)
    }

    @Test
    fun `generate button recaptures and detects new round instead of rewriting stale context`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "至少50字"
        fixture.current = context("你好", "最新问题")
        fixture.request(toggle = false)
        assertEquals(2, fixture.captures)
        assertEquals("最新问题", fixture.requests.last().first.messages.last().text)
        assertEquals("", fixture.requests.last().second)
        assertEquals("", fixture.instruction)
    }

    @Test
    fun `same round failed generation retry retains instruction`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "至少50字"
        fixture.failGeneration = true
        fixture.request()
        assertTrue(fixture.controller.state.value is OverlayState.Error)
        fixture.failGeneration = false
        fixture.request()
        assertEquals(listOf("", "至少50字", "至少50字"), fixture.requests.map { it.second })
        assertEquals("至少50字", fixture.instruction)
    }

    @Test
    fun `different chat with identical messages starts clean round`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "旧聊天要求"
        fixture.current = context("你好", title = "另一个测试聊天")
        fixture.request(toggle = true)
        assertEquals(2, fixture.requests.size)
        assertEquals("", fixture.instruction)
        assertEquals("", fixture.requests.last().second)
    }

    @Test
    fun `draft confidence and history bound changes do not reset instruction`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "保留要求"
        fixture.current = fixture.current.copy(
            draft = "输入中", confidence = 0.5f,
            messages = listOf(ChatMessage(MessageRole.ME, "历史变化", 0.5f)) + fixture.current.messages,
            replyRound = ReplyRoundSnapshot("测试聊天", listOf(ChatMessage(MessageRole.OTHER, " 你好\n ", 0.5f))),
        )
        fixture.request(toggle = true)
        assertEquals(1, fixture.requests.size)
        assertEquals("保留要求", fixture.instruction)
        assertEquals(OverlayState.Idle, fixture.controller.state.value)
    }

    @Test
    fun `older prefix and keyboard viewport subsets are not new incoming rounds`() = runTest {
        val fixture = Fixture(context("消息甲", "消息乙", "消息丙"))
        fixture.request()
        fixture.instruction = "保留要求"
        fixture.current = context("更早消息", "消息甲", "消息乙", "消息丙")
        fixture.request()
        fixture.current = context("消息乙", "消息丙")
        fixture.request()
        fixture.current = context("消息甲", "消息乙")
        fixture.request()
        assertEquals(listOf("", "保留要求", "保留要求", "保留要求"), fixture.requests.map { it.second })
        fixture.current = context("消息丙", "真正的新消息")
        fixture.request(toggle = true)
        assertEquals("", fixture.instruction)
        assertEquals("真正的新消息", fixture.requests.last().first.messages.last().text)
    }

    @Test
    fun `own sent message alone does not consume requirement before next incoming`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "本轮要求"
        val own = ChatMessage(MessageRole.ME, "我回复了", 1f)
        fixture.current = fixture.current.copy(messages = fixture.current.messages + own,
            replyRound = ReplyRoundSnapshot("测试聊天", fixture.current.messages + own))
        fixture.request(toggle = true)
        assertEquals(1, fixture.requests.size)
        assertEquals("本轮要求", fixture.instruction)
        fixture.current = context("你好").let { it.copy(
            messages = it.messages + own + ChatMessage(MessageRole.OTHER, "新的回答", 1f),
            replyRound = ReplyRoundSnapshot("测试聊天", it.messages + own + ChatMessage(MessageRole.OTHER, "新的回答", 1f))) }
        fixture.request()
        assertEquals("", fixture.instruction)
    }

    @Test
    fun `capture failures and cancellation preserve requirements and do not generate`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "不可丢失"
        fixture.captureResult = ContextCaptureResult.CaptureError("读取失败")
        fixture.request()
        assertEquals(listOf("读取失败"), fixture.errors)
        fixture.captureResult = ContextCaptureResult.Cancelled
        fixture.request()
        assertEquals(1, fixture.requests.size)
        assertEquals("不可丢失", fixture.instruction)
        fixture.captureResult = null
        fixture.request()
        assertEquals("不可丢失", fixture.requests.last().second)
    }


    @Test
    fun `dismissed capture cannot clear instructions or reopen candidates`() = runTest {
        val fixture = Fixture()
        fixture.request()
        fixture.instruction = "保留"
        fixture.current = context("你好", "新消息")
        fixture.captureGate = CompletableDeferred()
        val request = async { fixture.request() }
        fixture.captureStarted.await()
        fixture.controller.dismiss()
        fixture.captureGate!!.complete(Unit)
        request.await()
        assertEquals("保留", fixture.instruction)
        assertEquals(1, fixture.requests.size)
        assertEquals(OverlayState.Idle, fixture.controller.state.value)
        fixture.captureGate = null
        fixture.request()
        assertEquals("", fixture.instruction)
    }

    @Test
    fun `unrelated viewport does not silently erase requirement and uses fresh context on explicit generate`() = runTest {
        val fixture = Fixture(context("当前末尾"))
        fixture.request()
        fixture.instruction = "保留"
        fixture.current = context("不重叠的旧消息")
        fixture.request(toggle = true)
        assertEquals(1, fixture.requests.size)
        assertEquals("保留", fixture.instruction)
        fixture.request()
        assertEquals("不重叠的旧消息", fixture.requests.last().first.messages.last().text)
        assertEquals("保留", fixture.requests.last().second)
        fixture.current = context("当前末尾", "新增回复")
        fixture.request()
        assertEquals("", fixture.instruction)
    }

    private class Fixture(var current: ChatContext = context("你好")) {
        var instruction = ""
        var captures = 0
        var dismissals = 0
        var failGeneration = false
        var captureResult: ContextCaptureResult? = null
        var captureGate: CompletableDeferred<Unit>? = null
        val captureStarted = CompletableDeferred<Unit>()
        val requests = mutableListOf<Pair<ChatContext, String>>()
        val errors = mutableListOf<String>()
        val controller = OverlayController { context, text, _, _ ->
            requests += context to text
            if (failGeneration) Result.failure(IllegalStateException("本地失败")) else Result.success(replies())
        }
        val flow = QuickReplyFlow(controller,
            capture = {
                captures++
                captureGate?.let { captureStarted.complete(Unit); it.await() }
                captureResult ?: ContextCaptureResult.Success(current)
            },
            setInstruction = { instruction = it },
            dismiss = { dismissals++ },
            captureError = { errors += it },
        )
        suspend fun request(toggle: Boolean = false) = flow.request("com.tencent.mm", instruction, toggle)
    }

    companion object {
        private fun context(vararg text: String, title: String = "测试聊天"): ChatContext {
            val messages = text.map { ChatMessage(MessageRole.OTHER, it, 1f) }
            return ChatContext("wechat", "com.tencent.mm", messages, "", 1f, ReplyRoundSnapshot(title, messages))
        }
        private fun replies() = listOf(ReplyCandidate("brief", "本地一"), ReplyCandidate("polite", "本地二"), ReplyCandidate("natural", "本地三"))
    }
}
