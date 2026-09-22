package app.nextsay.diagnostics

data class SanitizedCrash(
    val exceptionClass: String,
    val stackFrames: List<String>,
)

class CrashSanitizer {
    fun sanitize(error: Throwable): SanitizedCrash = SanitizedCrash(
        exceptionClass = error.javaClass.name,
        stackFrames = error.stackTrace
            .asSequence()
            .filter { it.className.startsWith(APP_CLASS_PREFIX) }
            .take(MAX_STACK_FRAMES)
            .map(StackTraceElement::toString)
            .toList(),
    )

    private companion object {
        const val APP_CLASS_PREFIX = "app.nextsay."
        const val MAX_STACK_FRAMES = 40
    }
}
