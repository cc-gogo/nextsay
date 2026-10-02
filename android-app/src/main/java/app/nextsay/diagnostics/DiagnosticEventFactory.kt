package app.nextsay.diagnostics

import java.util.UUID

class DiagnosticEventFactory(
    private val metadataProvider: DiagnosticMetadataProvider,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun create(
        type: DiagnosticEventType,
        surface: DiagnosticSurface,
        providerScheme: String? = null,
        providerHost: String? = null,
        model: String? = null,
        httpStatus: Int? = null,
        durationMillis: Long? = null,
        errorCode: String? = null,
        accessibilityEnabled: Boolean? = null,
        imeEnabled: Boolean? = null,
        exceptionClass: String? = null,
        stackFrames: List<String> = emptyList(),
        finishReason: String? = null,
        contentState: String? = null,
        reasoningPresent: Boolean? = null,
    ): DiagnosticEvent {
        val metadata = metadataProvider.current()
        return DiagnosticEventSanitizer.sanitize(DiagnosticEvent(
            id = newId(),
            timestampMillis = nowMillis(),
            type = type,
            surface = surface,
            appVersion = metadata.appVersion,
            buildType = metadata.buildType,
            androidVersion = metadata.androidVersion,
            device = metadata.device,
            providerScheme = providerScheme,
            providerHost = providerHost,
            model = model,
            httpStatus = httpStatus,
            durationMillis = durationMillis,
            errorCode = errorCode,
            accessibilityEnabled = accessibilityEnabled,
            imeEnabled = imeEnabled,
            exceptionClass = exceptionClass,
            stackFrames = stackFrames,
            finishReason = finishReason,
            contentState = contentState,
            reasoningPresent = reasoningPresent,
        ))
    }
}
