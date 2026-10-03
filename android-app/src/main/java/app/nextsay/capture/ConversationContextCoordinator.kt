package app.nextsay.capture

import app.nextsay.context.ChatContext
import app.nextsay.context.ReplyRoundSnapshot
import app.nextsay.history.GenerationContextBuilder
import app.nextsay.history.HistoryLoadResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

sealed interface ContextCaptureResult {
    data class Success(val context: ChatContext) : ContextCaptureResult
    data class CaptureError(val message: String) : ContextCaptureResult
    data object Cancelled : ContextCaptureResult
    data object Busy : ContextCaptureResult
}

class ConversationContextCoordinator(
    private val accessibilityCapture: suspend (String) -> CapturedConversation?,
    private val ocrCapture: suspend (String) -> CapturedConversation?,
    private val mergeHistory: suspend (CapturedConversation) -> HistoryLoadResult,
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(),
    private val isPackageActive: (String) -> Boolean,
    private val viewportRevision: () -> Long = { 0L },
    private val onStage: (String) -> Unit = {},
    private val elapsedMillis: () -> Long = { System.nanoTime()/1_000_000 },
    private val onDuration: (String, Long) -> Unit = { _, _ -> },
) {
    private val running = AtomicBoolean(false)

    suspend fun capture(packageName: String): ContextCaptureResult {
        if (packageName !in SUPPORTED_PACKAGES) {
            return ContextCaptureResult.CaptureError("当前应用不受支持")
        }
        if (!running.compareAndSet(false, true)) return ContextCaptureResult.Busy
        val startedAt = elapsedMillis()
        return try {
            onStage("capture_start")
            val captured = if (packageName == WECHAT_PACKAGE) {
                onStage("ocr_start")
                measured("ocr_start") { ocrCapture(packageName) }
            } else {
                onStage("accessibility_start")
                val accessible = try { measured("accessibility_start") { accessibilityCapture(packageName) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                accessible ?: run {
                    if (!isPackageActive(packageName)) return ContextCaptureResult.Cancelled
                    onStage("ocr_start")
                    measured("ocr_start") { ocrCapture(packageName) }
                }
            } ?: return ContextCaptureResult.CaptureError("没有识别到可用的聊天文字")
            if (!isPackageActive(packageName)) return ContextCaptureResult.Cancelled
            val frameViewportRevision = viewportRevision()

            onStage("history_merge")
            val history = measured("history_merge") { mergeHistory(captured) }
            val context = contextBuilder.build(captured.context, history.messages).copy(
                replyRound = ReplyRoundSnapshot(captured.title, captured.context.messages, frameViewportRevision, captured.tailObscured),
            )
            if (!isPackageActive(packageName)) return ContextCaptureResult.Cancelled
            ContextCaptureResult.Success(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ContextCaptureResult.CaptureError("识别当前对话失败，请重试")
        } finally {
            running.set(false)
            reportDuration("capture_start", startedAt)
        }
    }

    private suspend fun <T> measured(stage: String, operation: suspend () -> T): T {
        val startedAt = elapsedMillis()
        return try { operation() } finally { reportDuration(stage, startedAt) }
    }

    private fun reportDuration(stage: String, startedAt: Long) {
        // Instrumentation failure must not change capture/cancellation behavior.
        runCatching { onDuration(stage, (elapsedMillis()-startedAt).coerceIn(0L,120_000L)) }
    }

    private companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        val SUPPORTED_PACKAGES = setOf(
            WECHAT_PACKAGE,
            "com.tencent.mobileqq",
            "com.tencent.tim",
            "com.tencent.qqlite",
        )
    }
}
