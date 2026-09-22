package app.nextsay.diagnostics

import app.nextsay.provider.ProviderErrorCode

class LocalCrashHandler(
    private val recorder: DiagnosticRecorder,
    private val eventFactory: DiagnosticEventFactory,
    private val sanitizer: CrashSanitizer = CrashSanitizer(),
    private val delegate: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            val safe = sanitizer.sanitize(error)
            recorder.record(
                eventFactory.create(
                    type = DiagnosticEventType.APP_CRASHED,
                    surface = DiagnosticSurface.MAIN,
                    errorCode = ProviderErrorCode.APP_INTERNAL.wireCode,
                    exceptionClass = safe.exceptionClass,
                    stackFrames = safe.stackFrames,
                ),
            )
        } catch (_: Throwable) {
            // Crash recording must never prevent Android's existing crash handler from running.
        } finally {
            delegate?.uncaughtException(thread, error)
        }
    }

    companion object {
        fun install(
            recorder: DiagnosticRecorder,
            eventFactory: DiagnosticEventFactory,
        ) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is LocalCrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(
                LocalCrashHandler(recorder, eventFactory, delegate = current),
            )
        }
    }
}
