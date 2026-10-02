package app.nextsay.overlay

import app.nextsay.context.ChatContext
import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.diagnostics.DiagnosticRecorder
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout

class OverlayController(
    // HTTP generation has a 60s deadline; allow a small margin for local work.
    private val timeoutMillis: Long = 65_000,
    private val diagnostics: DiagnosticRecorder? = null,
    private val eventFactory: DiagnosticEventFactory? = null,
    private val generateReplies: suspend (
        ChatContext,
        String,
        String,
        DiagnosticSurface,
    ) -> Result<List<ReplyCandidate>>,
) {
    private val mutableState = MutableStateFlow<OverlayState>(OverlayState.Idle)
    val state: StateFlow<OverlayState> = mutableState
    private var generationEpoch = 0L

    fun showPreview(context: ChatContext) {
        generationEpoch += 1
        mutableState.value = OverlayState.Preview(context)
    }

    fun dismiss() {
        generationEpoch += 1
        mutableState.value = OverlayState.Idle
    }

    fun refresh(context: ChatContext?) {
        if (context != null) {
            showPreview(context)
            return
        }
        val reviewed = reviewedContext() ?: return
        generationEpoch += 1
        mutableState.value = OverlayState.Error(reviewed, "刷新失败，已保留原上下文")
    }

    private var lastInstruction = ""
    private var lastRelationship = "unspecified"
    private var lastSurface = DiagnosticSurface.OVERLAY

    suspend fun generate(
        instruction: String = "",
        relationship: String = "unspecified",
        surface: DiagnosticSurface = DiagnosticSurface.OVERLAY,
    ) {
        val context = reviewedContext() ?: return
        lastInstruction = instruction
        lastRelationship = relationship
        lastSurface = surface
        generationEpoch += 1
        val requestEpoch = generationEpoch
        mutableState.value = OverlayState.Loading(context)
        val result = try {
            withTimeout(timeoutMillis) {
                generateReplies(context, instruction, relationship, surface)
            }
        } catch (_: TimeoutCancellationException) {
            Result.failure(localFailure(ProviderErrorCode.NET_TIMEOUT, surface))
        }
        if (requestEpoch != generationEpoch) return
        mutableState.value = result.fold(
            onSuccess = { candidates ->
                if (candidates.size == 3) OverlayState.Results(context, candidates)
                else errorState(
                    context,
                    localFailure(ProviderErrorCode.APP_INTERNAL, surface),
                    surface,
                )
            },
            onFailure = { errorState(context, it, surface) },
        )
    }

    suspend fun retry() = generate(lastInstruction, lastRelationship, lastSurface)

    fun onActivePackageChanged(packageName: String) {
        val context = reviewedContext() ?: return
        if (context.sourcePackage != packageName) dismiss()
    }

    fun hasActiveResultsFor(packageName: String): Boolean {
        val current = mutableState.value
        return current is OverlayState.Results && current.context.sourcePackage == packageName
    }

    private fun reviewedContext(): ChatContext? = when (val current = mutableState.value) {
        is OverlayState.Preview -> current.context
        is OverlayState.Loading -> current.context
        is OverlayState.Results -> current.context
        is OverlayState.Error -> current.context
        OverlayState.Idle -> null
    }

    private fun errorState(
        context: ChatContext,
        error: Throwable,
        surface: DiagnosticSurface,
    ): OverlayState.Error {
        val failure = error as? ProviderException
            ?: localFailure(ProviderErrorCode.APP_INTERNAL, surface)
        return OverlayState.Error(
            context = context,
            message = failure.message ?: "生成失败，请重试",
            diagnosticId = failure.diagnosticId.ifBlank { null },
        )
    }

    private fun localFailure(
        code: ProviderErrorCode,
        surface: DiagnosticSurface,
    ): ProviderException {
        val event = eventFactory?.create(
            type = DiagnosticEventType.GENERATION_FAILED,
            surface = surface,
            errorCode = code.wireCode,
        )
        if (event != null) diagnostics?.record(event)
        return ProviderException(code, event?.id.orEmpty())
    }
}
