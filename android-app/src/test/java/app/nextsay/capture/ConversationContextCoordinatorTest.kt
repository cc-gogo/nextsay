package app.nextsay.capture

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.history.HistoryLoadResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import com.google.gson.Gson
import org.junit.Test

class ConversationContextCoordinatorTest {
    @Test fun timingCallbackFailureDoesNotBreakCaptureOrLeaveItBusy() = runTest {
        val coordinator = ConversationContextCoordinator(
            accessibilityCapture = { capture(it, "测试") },
            ocrCapture = { capture(it, "测试") },
            mergeHistory = { HistoryLoadResult(it.context.messages, false) },
            isPackageActive = { true },
            onDuration = { _, _ -> error("synthetic diagnostic failure") },
        )
        repeat(2) { assertTrue(coordinator.capture("com.tencent.mm") is ContextCaptureResult.Success) }
    }
    @Test fun cancellationStillPropagatesAndReleasesBusyWithFailingTimingCallback() = runTest {
        var cancel = true
        val coordinator = ConversationContextCoordinator(
            accessibilityCapture = { capture(it, "测试") },
            ocrCapture = { if (cancel) throw kotlinx.coroutines.CancellationException(); capture(it, "测试") },
            mergeHistory = { HistoryLoadResult(it.context.messages, false) },
            isPackageActive = { true },
            onDuration = { _, _ -> error("synthetic diagnostic failure") },
        )
        try { coordinator.capture("com.tencent.mm"); org.junit.Assert.fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        cancel = false
        assertTrue(coordinator.capture("com.tencent.mm") is ContextCaptureResult.Success)
    }
    @Test fun captureTimingSeparatesReadAndHistoryWithoutRecordingText() = runTest {
        var clock = 100L
        val timings = mutableListOf<Pair<String, Long>>()
        val coordinator = ConversationContextCoordinator(
            accessibilityCapture = { capture(it, "不应出现在计时中的标题") },
            ocrCapture = { clock += 230; capture(it, "不应出现在计时中的标题") },
            mergeHistory = { clock += 70; HistoryLoadResult(it.context.messages, false) },
            isPackageActive = { true }, elapsedMillis = { clock },
            onDuration = { stage, duration -> timings += stage to duration },
        )
        assertTrue(coordinator.capture("com.tencent.mm") is ContextCaptureResult.Success)
        assertEquals(listOf("ocr_start" to 230L, "history_merge" to 70L, "capture_start" to 300L), timings)
    }
    @Test
    fun `qq accessibility exception still falls back to OCR`() = runTest {
        val coordinator = coordinator(
            accessibilityCapture = { throw IllegalStateException("synthetic node unavailable") },
            ocrCapture = { capture(it, "QQ测试") },
        )
        val result = coordinator.capture("com.tencent.mobileqq")
        assertTrue("Node failure must not skip OCR fallback", result is ContextCaptureResult.Success)
        assertEquals("qq", (result as ContextCaptureResult.Success).context.sourceApp)
    }

    @Test
    fun `accessibility cancellation must not read a screenshot`() = runTest {
        var screenshotRead = false
        val coordinator = coordinator(
            accessibilityCapture = { throw kotlinx.coroutines.CancellationException() },
            ocrCapture = { screenshotRead = true; capture(it, "QQ测试") },
        )
        try { coordinator.capture("com.tencent.mobileqq"); org.junit.Assert.fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(false, screenshotRead)
    }
    @Test
    fun `local snapshot uses visible frame and pre-merge viewport not bounded history`() = runTest {
        var revision = 1L
        val coordinator = ConversationContextCoordinator(
            accessibilityCapture = { capture(it, "测试") },
            ocrCapture = { capture(it, "测试") },
            mergeHistory = {
                revision = 2L
                HistoryLoadResult(List(25) { ChatMessage(MessageRole.ME, "历史", 1f) } + it.context.messages, persisted = true)
            },
            isPackageActive = { true },
            viewportRevision = { revision },
        )
        val result = (coordinator.capture("com.tencent.mm") as ContextCaptureResult.Success).context
        assertEquals(20, result.messages.size)
        assertEquals(1L, result.replyRound!!.viewportRevision)
        assertEquals(listOf("你好"), result.replyRound.visibleMessages.map { it.text })
    }

    @Test
    fun `identical messages in different chats retain distinct local identity without uploading titles`() = runTest {
        var title = "本地测试甲"
        val coordinator = coordinator(
            accessibilityCapture = { capture(it, title) },
            ocrCapture = { capture(it, title) },
        )
        val first = (coordinator.capture("com.tencent.mm") as ContextCaptureResult.Success).context
        title = "本地测试乙"
        val second = (coordinator.capture("com.tencent.mm") as ContextCaptureResult.Success).context

        assertNotEquals("Different chat titles must not share a reply round", first, second)
        assertTrue("Local chat identity must never enter serialized context", !Gson().toJson(first).contains("本地测试甲"))
    }

    @Test
    fun `capture returns context without a generation callback`() = runTest {
        var ocrCalls = 0
        val coordinator = coordinator(
            accessibilityCapture = { error("WeChat must not read accessibility nodes") },
            ocrCapture = { packageName ->
                ocrCalls += 1
                capture(packageName, "微信好友")
            },
        )

        val result = coordinator.capture("com.tencent.mm")

        assertTrue(result is ContextCaptureResult.Success)
        assertEquals(1, ocrCalls)
    }

    @Test
    fun `qq falls back to OCR only when accessibility capture is empty`() = runTest {
        var ocrCalls = 0
        val coordinator = coordinator(
            accessibilityCapture = { null },
            ocrCapture = {
                ocrCalls += 1
                capture(it, "QQ好友")
            },
        )

        val result = coordinator.capture("com.tencent.mobileqq")

        assertTrue(result is ContextCaptureResult.Success)
        assertEquals(1, ocrCalls)
    }

    @Test
    fun `foreground change cancels captured context`() = runTest {
        var active = true
        val coordinator = coordinator(
            accessibilityCapture = { capture(it, "QQ好友").also { active = false } },
            ocrCapture = { capture(it, "QQ好友") },
            isPackageActive = { active },
        )

        assertEquals(ContextCaptureResult.Cancelled, coordinator.capture("com.tencent.mobileqq"))
    }

    @Test
    fun `second capture is busy while first capture is running`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            accessibilityCapture = {
                started.complete(Unit)
                release.await()
                capture(it, "QQ好友")
            },
            ocrCapture = { capture(it, "QQ好友") },
        )

        val first = async { coordinator.capture("com.tencent.mobileqq") }
        started.await()
        val second = coordinator.capture("com.tencent.mobileqq")
        release.complete(Unit)

        assertEquals(ContextCaptureResult.Busy, second)
        assertTrue(first.await() is ContextCaptureResult.Success)
    }

    private fun coordinator(
        accessibilityCapture: suspend (String) -> CapturedConversation?,
        ocrCapture: suspend (String) -> CapturedConversation?,
        isPackageActive: (String) -> Boolean = { true },
    ) = ConversationContextCoordinator(
        accessibilityCapture = accessibilityCapture,
        ocrCapture = ocrCapture,
        mergeHistory = { HistoryLoadResult(it.context.messages, persisted = it.persistable) },
        isPackageActive = isPackageActive,
    )

    private fun capture(packageName: String, title: String) = CapturedConversation(
        title = title,
        context = ChatContext(
            sourceApp = if (packageName == "com.tencent.mm") "wechat" else "qq",
            sourcePackage = packageName,
            messages = listOf(ChatMessage(MessageRole.OTHER, "你好", 0.9f)),
            draft = "",
            confidence = 0.9f,
        ),
        persistable = true,
    )
}
