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
import org.junit.Test

class ConversationContextCoordinatorTest {
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
