package app.nextsay.capture

import app.nextsay.context.ChatContext
import app.nextsay.history.GenerationContextBuilder
import app.nextsay.history.HistoryLoadResult

sealed interface OneTapResult {
    data class Success(val context: ChatContext) : OneTapResult
    data class CaptureError(val message: String) : OneTapResult
    data object Cancelled : OneTapResult
    data object Busy : OneTapResult
}

class OneTapReplyCoordinator(
    private val accessibilityCapture: suspend (String) -> CapturedConversation?,
    private val ocrCapture: suspend (String) -> CapturedConversation?,
    private val mergeHistory: suspend (CapturedConversation) -> HistoryLoadResult,
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(),
    private val isPackageActive: (String) -> Boolean,
    private val onContextReady: suspend (ChatContext) -> Unit,
) {
    private val captureCoordinator = ConversationContextCoordinator(
        accessibilityCapture = accessibilityCapture,
        ocrCapture = ocrCapture,
        mergeHistory = mergeHistory,
        contextBuilder = contextBuilder,
        isPackageActive = isPackageActive,
    )

    suspend fun run(packageName: String): OneTapResult {
        return when (val result = captureCoordinator.capture(packageName)) {
            is ContextCaptureResult.Success -> {
                onContextReady(result.context)
                OneTapResult.Success(result.context)
            }
            is ContextCaptureResult.CaptureError -> OneTapResult.CaptureError(result.message)
            ContextCaptureResult.Cancelled -> OneTapResult.Cancelled
            ContextCaptureResult.Busy -> OneTapResult.Busy
        }
    }
}
