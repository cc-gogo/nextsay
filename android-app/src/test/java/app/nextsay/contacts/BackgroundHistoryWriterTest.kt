package app.nextsay.contacts

import app.nextsay.capture.CapturedConversation
import app.nextsay.context.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackgroundHistoryWriterTest {
    private fun frame(text: String) = CapturedConversation("本地模拟", ChatContext("wechat", "com.tencent.mm", listOf(ChatMessage(MessageRole.OTHER, text, 1f)), "", 1f), true)
    @Test fun clearingHistoryDropsQueuedFramesButKeepsNewEpochFrames() = runTest {
        var epoch = 0L
        val saved = mutableListOf<String>()
        val writer = BackgroundHistoryWriter(backgroundScope, { _, capture -> saved += capture.context.messages.single().text }, currentEpoch = { epoch })
        writer.submit("a", frame("旧帧"))
        epoch++
        writer.submit("a", frame("新帧"))
        runCurrent()
        assertEquals(listOf("新帧"), saved)
    }
    @Test fun slowHistorySaveDoesNotBlockNewFrameAndKeepsOriginalOrderAndOwner() = runTest {
        val release = CompletableDeferred<Unit>()
        val saved = mutableListOf<String>()
        val writer = BackgroundHistoryWriter(backgroundScope, { id, capture -> release.await(); saved += "$id:${capture.context.messages.single().text}" })
        assertTrue(writer.submit("a", frame("第一条")))
        runCurrent()
        assertTrue(writer.submit("b", frame("第二条")))
        assertTrue(saved.isEmpty())
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("a:第一条", "b:第二条"), saved)
    }
    @Test fun failedLocalSaveDoesNotKillTheWorkerOrGenerateAReply() = runTest {
        val saved = mutableListOf<String>()
        var failures = 0
        val writer = BackgroundHistoryWriter(backgroundScope, { _, capture ->
            val text = capture.context.messages.single().text
            if (text == "失败") error("synthetic persistence failure")
            saved += text
        }, { failures++ })
        writer.submit("a", frame("失败")); writer.submit("a", frame("成功"))
        runCurrent()
        assertEquals(listOf("成功"), saved)
        assertEquals(1, failures)
    }
}
