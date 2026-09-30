package app.nextsay.capture

import java.util.concurrent.atomic.AtomicBoolean

class ScreenshotCaptureGate {
    private val running = AtomicBoolean(false)

    suspend fun <T> run(block: suspend () -> T): Result<T>? {
        if (!running.compareAndSet(false, true)) return null
        return try {
            try {
                Result.success(block())
            } catch (error: Throwable) {
                Result.failure(error)
            }
        } finally {
            running.set(false)
        }
    }
}
