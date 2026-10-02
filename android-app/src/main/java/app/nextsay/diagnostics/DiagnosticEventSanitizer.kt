package app.nextsay.diagnostics

/** Enforces the diagnostic schema at both persistence and presentation boundaries. */
object DiagnosticEventSanitizer {
    private val identifier = Regex("[A-Za-z0-9._:/-]{1,128}")
    private val shortIdentifier = Regex("[A-Za-z0-9._-]{1,64}")
    private val host = Regex("[A-Za-z0-9.-]{1,253}")
    private val metadata = Regex("[\\p{L}\\p{N} ._()/+-]{1,80}")
    private val frame = Regex("app\\.nextsay\\.[A-Za-z0-9_.$]+\\.[A-Za-z0-9_$<>]+\\([A-Za-z0-9_.-]+:[0-9]+\\)")
    private val credentialMarker = Regex("(?i)(authorization|bearer|api.?key|secret|token|sk-[A-Za-z0-9_-]{8,})")

    fun sanitize(event: DiagnosticEvent): DiagnosticEvent = event.copy(
        id = safe(event.id, shortIdentifier),
        appVersion = safe(event.appVersion, metadata),
        buildType = safe(event.buildType, shortIdentifier),
        androidVersion = safe(event.androidVersion, metadata),
        device = safe(event.device, metadata),
        providerScheme = event.providerScheme?.let { safe(it, shortIdentifier) },
        providerHost = event.providerHost?.let { safe(it, host) },
        model = event.model?.let { safe(it, identifier) },
        errorCode = event.errorCode?.let { safe(it, shortIdentifier) },
        exceptionClass = event.exceptionClass?.let { safe(it, identifier) },
        stackFrames = event.stackFrames.take(40).map { safe(it, frame) },
        finishReason = event.finishReason?.takeIf { it in FINISH_REASONS },
        contentState = event.contentState?.takeIf { it in CONTENT_STATES },
    )

    private val FINISH_REASONS = setOf(
        "stop", "length", "tool_calls", "function_call", "content_filter", "insufficient_system_resource",
    )
    private val CONTENT_STATES = setOf("missing", "non_string", "blank", "present")

    private fun safe(value: String, allowed: Regex): String =
        if (allowed.matches(value) && !credentialMarker.containsMatchIn(value)) value else "[redacted]"
}
