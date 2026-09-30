package app.nextsay.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ScreenshotFailureException(val errorCode: Int) :
    IllegalStateException("Screenshot failed with code $errorCode")

class AccessibilityScreenshotSource(
    private val service: AccessibilityService,
    private val gate: ScreenshotCaptureGate = ScreenshotCaptureGate(),
) {
    suspend fun capture(): Result<Bitmap>? = gate.run { captureOnce() }

    private suspend fun captureOnce(): Bitmap = suspendCancellableCoroutine { continuation ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val bitmap = screenshot.hardwareBuffer.use { buffer ->
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    }
                    if (bitmap == null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Screenshot bitmap unavailable"))
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(bitmap)
                    } else {
                        bitmap.recycle()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(ScreenshotFailureException(errorCode))
                    }
                }
            },
        )
    }
}
