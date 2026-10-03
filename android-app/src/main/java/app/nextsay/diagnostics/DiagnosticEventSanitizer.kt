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
        triggerReason = event.triggerReason?.takeIf { it in setOf("manual_refresh", "auto_incoming", "auto_self", "ime", "advanced") },
        captureStage = event.captureStage?.takeIf { it in CAPTURE_STAGES },
        captureApp = event.captureApp?.takeIf { it in setOf("wechat", "qq") },
        captureNodeCount = event.captureNodeCount?.takeIf { it in 0..2000 },
        captureTextCount = event.captureTextCount?.takeIf { it in 0..10000 },
        automaticState = event.automaticState?.takeIf { it in setOf("tail_obscured", "waiting_other", "uncertain_tail",
            "baseline_or_unchanged", "pending_editor", "pending_busy", "generating", "generated", "generation_failed", "unverified_frame") },
        captureBottom = event.captureBottom?.takeIf { it in 1..20000 },
        visibleMessageCount = event.visibleMessageCount?.takeIf { it in 0..500 },
        latestRole = event.latestRole?.takeIf { it in setOf("ME", "OTHER", "UNKNOWN") },
        latestConfidence = event.latestConfidence?.takeIf { it in 0f..1f },
        bubbleCount = event.bubbleCount?.takeIf { it in 0..500 },
    )

    private val FINISH_REASONS = setOf(
        "stop", "length", "tool_calls", "function_call", "content_filter", "insufficient_system_resource",
    )
    private val CONTENT_STATES = setOf("missing", "non_string", "blank", "present")
    private val CAPTURE_STAGES = setOf("capture_start", "accessibility_start", "accessibility_root", "accessibility_nodes",
        "accessibility_parse", "password_blocked", "ocr_start", "screenshot", "screenshot_busy", "overlay_moved",
        "ocr_recognize", "ocr_parse", "header_obscured", "history_merge", "history_save")

    private fun safe(value: String, allowed: Regex): String =
        if (allowed.matches(value) && !credentialMarker.containsMatchIn(value)) value else "[redacted]"
}
