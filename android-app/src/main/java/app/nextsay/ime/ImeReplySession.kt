package app.nextsay.ime

import app.nextsay.overlay.ReplyCandidate
import app.nextsay.provider.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun interface ImeGenerationHandler {
    suspend fun generate(targetPackage: String): Result<List<ReplyCandidate>>
}

class ImeReplySession {
    private val mutableState = MutableStateFlow<ImeReplyState>(
        ImeReplyState.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP),
    )
    val state: StateFlow<ImeReplyState> = mutableState

    private var editorEligibility: ImeEditorEligibility =
        ImeEditorEligibility.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP)
    private var generationHandler: ImeGenerationHandler? = null
    private var editorEpoch = 0L
    private var requestRunning = false

    fun attachEditor(eligibility: ImeEditorEligibility) {
        editorEpoch += 1
        editorEligibility = eligibility
        publishAvailability()
    }

    fun detachEditor() {
        attachEditor(ImeEditorEligibility.Unavailable(ImeUnavailableReason.UNSUPPORTED_APP))
    }

    fun registerHandler(handler: ImeGenerationHandler) {
        generationHandler = handler
        if (mutableState.value is ImeReplyState.Unavailable) publishAvailability()
    }

    fun unregisterHandler(handler: ImeGenerationHandler) {
        if (generationHandler !== handler) return
        generationHandler = null
        editorEpoch += 1
        publishAvailability()
    }

    suspend fun requestGeneration() {
        if (requestRunning) return
        val targetPackage = (editorEligibility as? ImeEditorEligibility.Available)?.targetPackage
            ?: return
        val handler = generationHandler
        if (handler == null) {
            mutableState.value = ImeReplyState.Unavailable(ImeUnavailableReason.SERVICE_DISCONNECTED)
            return
        }
        val requestEpoch = editorEpoch
        requestRunning = true
        mutableState.value = ImeReplyState.Loading(targetPackage)
        try {
            val result = handler.generate(targetPackage)
            if (!matchesCurrentEditor(targetPackage, requestEpoch)) return
            mutableState.value = result.fold(
                onSuccess = { candidates ->
                    if (candidates.size == REQUIRED_CANDIDATE_COUNT) {
                        ImeReplyState.Results(targetPackage, candidates)
                    } else {
                        ImeReplyState.Error(targetPackage, "服务返回的候选数量不正确")
                    }
                },
                onFailure = { error ->
                    val providerError = error as? ProviderException
                    ImeReplyState.Error(
                        targetPackage = targetPackage,
                        message = error.message ?: "生成失败，请重试",
                        diagnosticId = providerError?.diagnosticId,
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            requestRunning = false
        }
    }

    fun candidateForCommit(index: Int, targetPackage: String?): ReplyCandidate? {
        val current = mutableState.value as? ImeReplyState.Results
        val editorPackage = (editorEligibility as? ImeEditorEligibility.Available)?.targetPackage
        if (current == null || targetPackage == null || current.targetPackage != targetPackage || editorPackage != targetPackage) {
            publishAvailability()
            return null
        }
        return current.candidates.getOrNull(index).also { candidate ->
            if (candidate == null) publishAvailability()
        }
    }

    fun completeCommit(success: Boolean) {
        val current = mutableState.value as? ImeReplyState.Results ?: return
        mutableState.value = if (success) {
            ImeReplyState.Inserted(current.targetPackage)
        } else {
            current.copy(message = "输入框连接已失效，请重新点击聊天输入框")
        }
    }

    private fun matchesCurrentEditor(targetPackage: String, requestEpoch: Long): Boolean =
        editorEpoch == requestEpoch &&
            (editorEligibility as? ImeEditorEligibility.Available)?.targetPackage == targetPackage

    private fun publishAvailability() {
        mutableState.value = when (val eligibility = editorEligibility) {
            is ImeEditorEligibility.Unavailable -> ImeReplyState.Unavailable(eligibility.reason)
            is ImeEditorEligibility.Available -> if (generationHandler == null) {
                ImeReplyState.Unavailable(ImeUnavailableReason.SERVICE_DISCONNECTED)
            } else {
                ImeReplyState.Ready(eligibility.targetPackage)
            }
        }
    }

    private companion object {
        const val REQUIRED_CANDIDATE_COUNT = 3
    }
}
