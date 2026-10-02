package app.nextsay.overlay

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayControllerTest {
    private val context = ChatContext(
        sourceApp = "wechat",
        sourcePackage = "com.tencent.mm",
        messages = listOf(ChatMessage(MessageRole.OTHER, "明天能交吗", 0.9f)),
        draft = "",
        confidence = 0.9f,
    )

    @Test
    fun `capture opens preview and cancel returns idle`() {
        val controller = OverlayController { _, _, _, _ -> Result.success(replies()) }

        controller.showPreview(context)
        assertTrue(controller.state.value is OverlayState.Preview)
        controller.dismiss()
        assertEquals(OverlayState.Idle, controller.state.value)
    }

    @Test
    fun `successful generation exposes exactly three candidates`() = runTest {
        var receivedInstruction = ""
        var receivedRelationship = ""
        var receivedSurface: DiagnosticSurface? = null
        val controller = OverlayController { receivedContext, instruction, relationship, surface ->
            assertEquals(context, receivedContext)
            receivedInstruction = instruction
            receivedRelationship = relationship
            receivedSurface = surface
            Result.success(replies())
        }
        controller.showPreview(context)

        controller.generate("委婉一点", "manager")

        val result = controller.state.value as OverlayState.Results
        assertEquals(3, result.candidates.size)
        assertEquals("委婉一点", receivedInstruction)
        assertEquals("manager", receivedRelationship)
        assertEquals(DiagnosticSurface.OVERLAY, receivedSurface)
    }

    @Test
    fun `retry reuses reviewed context without recapture`() = runTest {
        var calls = 0
        val controller = OverlayController { _, _, _, _ ->
            calls += 1
            if (calls == 1) Result.failure(IllegalStateException("offline")) else Result.success(replies())
        }
        controller.showPreview(context)
        controller.generate()
        assertTrue(controller.state.value is OverlayState.Error)

        controller.retry()

        assertTrue(controller.state.value is OverlayState.Results)
        assertEquals(2, calls)
    }

    @Test
    fun `timeout becomes retryable error`() = runTest {
        val controller = OverlayController(timeoutMillis = 10) { _, _, _, _ ->
            delay(100)
            Result.success(replies())
        }
        controller.showPreview(context)

        controller.generate()

        assertTrue(controller.state.value is OverlayState.Error)
    }

    @Test
    fun `default overlay deadline allows a twenty second generation`() = runTest {
        val controller = OverlayController { _, _, _, _ ->
            delay(20_000)
            Result.success(replies())
        }
        controller.showPreview(context)

        controller.generate()

        assertTrue(controller.state.value is OverlayState.Results)
    }

    @Test
    fun `app change clears reviewed context`() {
        val controller = OverlayController { _, _, _, _ -> Result.success(replies()) }
        controller.showPreview(context)

        controller.onActivePackageChanged("com.tencent.mobileqq")

        assertEquals(OverlayState.Idle, controller.state.value)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `late response cannot restore results after app change`() = runTest {
        val controller = OverlayController { _, _, _, _ ->
            delay(100)
            Result.success(replies())
        }
        controller.showPreview(context)
        launch { controller.generate() }
        runCurrent()
        controller.onActivePackageChanged("com.tencent.mobileqq")

        advanceUntilIdle()

        assertEquals(OverlayState.Idle, controller.state.value)
    }

    @Test
    fun `refresh replaces results with a new context preview`() = runTest {
        val controller = OverlayController { _, _, _, _ -> Result.success(replies()) }
        controller.showPreview(context)
        controller.generate()
        val refreshed = context.copy(
            messages = listOf(ChatMessage(MessageRole.OTHER, "这是最新消息", 0.9f)),
        )

        controller.refresh(refreshed)

        assertEquals(OverlayState.Preview(refreshed), controller.state.value)
    }

    @Test
    fun `failed refresh preserves reviewed context in error state`() = runTest {
        val controller = OverlayController { _, _, _, _ -> Result.success(replies()) }
        controller.showPreview(context)
        controller.generate()

        controller.refresh(null)

        val error = controller.state.value as OverlayState.Error
        assertEquals(context, error.context)
    }

    @Test
    fun `insertion target is valid only while matching results remain active`() = runTest {
        val controller = OverlayController { _, _, _, _ -> Result.success(replies()) }
        controller.showPreview(context)
        controller.generate()

        assertTrue(controller.hasActiveResultsFor("com.tencent.mm"))
        controller.onActivePackageChanged("com.tencent.mobileqq")

        assertEquals(false, controller.hasActiveResultsFor("com.tencent.mm"))
    }

    @Test
    fun `provider failure preserves diagnostic ID in overlay state`() = runTest {
        val controller = OverlayController { _, _, _, _ ->
            Result.failure(ProviderException(ProviderErrorCode.API_AUTH, "evt-auth", 401))
        }
        controller.showPreview(context)

        controller.generate()

        val error = controller.state.value as OverlayState.Error
        assertEquals("evt-auth", error.diagnosticId)
        assertTrue(error.message.contains("API-AUTH"))
    }

    private fun replies() = listOf(
        ReplyCandidate("concise", "可以"),
        ReplyCandidate("tactful", "可以，我会尽快完成"),
        ReplyCandidate("natural", "没问题，我弄好后发你"),
    )
}
