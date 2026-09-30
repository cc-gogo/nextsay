package app.nextsay.capture

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.history.HistoryLoadResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneTapReplyCoordinatorTest {
    @Test
    fun `wechat uses OCR and submits exactly once`() = runTest {
        var accessibilityCalls = 0
        var ocrCalls = 0
        val submitted = mutableListOf<ChatContext>()
        val coordinator = coordinator(
            accessibilityCapture = { accessibilityCalls += 1; null },
            ocrCapture = { packageName -> ocrCalls += 1; capture(packageName, "微信好友") },
            onContextReady = { submitted += it },
        )

        val result = coordinator.run("com.tencent.mm")

        assertTrue(result is OneTapResult.Success)
        assertEquals(0, accessibilityCalls)
        assertEquals(1, ocrCalls)
        assertEquals(1, submitted.size)
    }

    @Test
    fun `qq prefers accessibility capture without OCR`() = runTest {
        var ocrCalls = 0
        val coordinator = coordinator(
            accessibilityCapture = { capture(it, "QQ好友") },
            ocrCapture = { ocrCalls += 1; capture(it, "QQ好友") },
        )

        coordinator.run("com.tencent.mobileqq")

        assertEquals(0, ocrCalls)
    }

    @Test
    fun `qq falls back to OCR when accessibility has no messages`() = runTest {
        var ocrCalls = 0
        val coordinator = coordinator(
            accessibilityCapture = { null },
            ocrCapture = { ocrCalls += 1; capture(it, "QQ好友") },
        )

        val result = coordinator.run("com.tencent.mobileqq")

        assertTrue(result is OneTapResult.Success)
        assertEquals(1, ocrCalls)
    }

    @Test
    fun `package change after capture cancels before submission`() = runTest {
        var active = true
        var submissions = 0
        val coordinator = coordinator(
            accessibilityCapture = { capture(it, "QQ好友").also { active = false } },
            ocrCapture = { capture(it, "QQ好友") },
            isPackageActive = { active },
            onContextReady = { submissions += 1 },
        )

        val result = coordinator.run("com.tencent.mobileqq")

        assertEquals(OneTapResult.Cancelled, result)
        assertEquals(0, submissions)
    }

    @Test
    fun `capture failure never submits context`() = runTest {
        var submissions = 0
        val coordinator = coordinator(
            accessibilityCapture = { error("boom") },
            ocrCapture = { error("boom") },
            onContextReady = { submissions += 1 },
        )

        val result = coordinator.run("com.tencent.mobileqq")

        assertTrue(result is OneTapResult.CaptureError)
        assertEquals(0, submissions)
    }

    private fun coordinator(
        accessibilityCapture: suspend (String) -> CapturedConversation?,
        ocrCapture: suspend (String) -> CapturedConversation?,
        isPackageActive: (String) -> Boolean = { true },
        onContextReady: suspend (ChatContext) -> Unit = {},
    ) = OneTapReplyCoordinator(
        accessibilityCapture = accessibilityCapture,
        ocrCapture = ocrCapture,
        mergeHistory = { HistoryLoadResult(it.context.messages, persisted = it.persistable) },
        isPackageActive = isPackageActive,
        onContextReady = onContextReady,
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
