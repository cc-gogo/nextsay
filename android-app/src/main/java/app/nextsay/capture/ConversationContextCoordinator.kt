package app.nextsay.capture

import app.nextsay.context.ChatContext
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
) {
    private val running = AtomicBoolean(false)

    suspend fun capture(packageName: String): ContextCaptureResult {
        if (packageName !in SUPPORTED_PACKAGES) {
            return ContextCaptureResult.CaptureError("当前应用不受支持")
        }
        if (!running.compareAndSet(false, true)) return ContextCaptureResult.Busy
        return try {
            val captured = if (packageName == WECHAT_PACKAGE) {
                ocrCapture(packageName)
            } else {
                accessibilityCapture(packageName) ?: ocrCapture(packageName)
            } ?: return ContextCaptureResult.CaptureError("没有识别到可用的聊天文字")
            if (!isPackageActive(packageName)) return ContextCaptureResult.Cancelled

            val history = mergeHistory(captured)
            val context = contextBuilder.build(captured.context, history.messages)
            if (!isPackageActive(packageName)) return ContextCaptureResult.Cancelled
            ContextCaptureResult.Success(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ContextCaptureResult.CaptureError("识别当前对话失败，请重试")
        } finally {
            running.set(false)
        }
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
