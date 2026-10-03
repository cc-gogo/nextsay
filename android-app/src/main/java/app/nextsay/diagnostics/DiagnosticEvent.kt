package app.nextsay.diagnostics

enum class DiagnosticEventType {
    CONNECTION_TEST_STARTED,
    CONNECTION_TEST_SUCCEEDED,
    CONNECTION_TEST_FAILED,
    GENERATION_STARTED,
    GENERATION_SUCCEEDED,
    GENERATION_FAILED,
    CANDIDATE_REQUESTED,
    AUTOMATIC_STATE,
    CAPTURE_TIMING,
    HISTORY_SAVE_FAILED,
    CAPTURE_FAILED,
    INSERTION_FAILED,
    APP_CRASHED,
}

enum class DiagnosticSurface { MAIN, SETTINGS, OVERLAY, IME }

data class DiagnosticEvent(
    val id: String,
    val timestampMillis: Long,
    val type: DiagnosticEventType,
    val surface: DiagnosticSurface,
    val appVersion: String,
    val buildType: String,
    val androidVersion: String,
    val device: String,
    val providerScheme: String? = null,
    val providerHost: String? = null,
    val model: String? = null,
    val httpStatus: Int? = null,
    val durationMillis: Long? = null,
    val errorCode: String? = null,
    val accessibilityEnabled: Boolean? = null,
    val imeEnabled: Boolean? = null,
    val exceptionClass: String? = null,
    val stackFrames: List<String> = emptyList(),
    val finishReason: String? = null,
    val contentState: String? = null,
    val reasoningPresent: Boolean? = null,
    val triggerReason: String? = null,
    val memoryIncluded: Boolean? = null,
    val captureStage: String? = null,
    val captureApp: String? = null,
    val captureNodeCount: Int? = null,
    val captureTextCount: Int? = null,
    val automaticState: String? = null,
    val keyboardVisible: Boolean? = null,
    val captureBottom: Int? = null,
    val pendingIncoming: Boolean? = null,
    val visibleMessageCount: Int? = null,
    val latestRole: String? = null,
    val latestConfidence: Float? = null,
    val bubbleCount: Int? = null,
)

data class DiagnosticMetadata(
    val appVersion: String,
    val buildType: String,
    val androidVersion: String,
    val device: String,
)

fun interface DiagnosticMetadataProvider {
    fun current(): DiagnosticMetadata
}
