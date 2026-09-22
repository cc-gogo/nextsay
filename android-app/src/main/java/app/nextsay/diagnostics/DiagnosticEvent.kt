package app.nextsay.diagnostics

enum class DiagnosticEventType {
    CONNECTION_TEST_STARTED,
    CONNECTION_TEST_SUCCEEDED,
    CONNECTION_TEST_FAILED,
    GENERATION_STARTED,
    GENERATION_SUCCEEDED,
    GENERATION_FAILED,
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
