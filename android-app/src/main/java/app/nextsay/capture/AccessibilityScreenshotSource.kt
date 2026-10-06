package app.nextsay.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
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

    /**
     * Android 14 can capture only the target app window. This excludes the IME
     * and our own accessibility overlay from the source image. Older releases
     * keep using the full-display accessibility screenshot.
     */
    suspend fun captureWindow(windowId: Int): Result<Bitmap>? =
        gate.run {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@run captureOnce()
            captureWindowOnce(windowId)
        }

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

    private suspend fun captureWindowOnce(windowId: Int): Bitmap =
        suspendCancellableCoroutine { continuation ->
            service.takeScreenshotOfWindow(
                windowId,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = screenshot.hardwareBuffer.use { buffer ->
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        }
                        val output = bitmap
                        if (output == null) {
                            bitmap?.recycle()
                            if (continuation.isActive) continuation.resumeWithException(
                                IllegalStateException("Window screenshot bitmap unavailable"),
                            )
                        } else if (continuation.isActive) continuation.resume(output)
                        else output.recycle()
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resumeWithException(ScreenshotFailureException(errorCode))
                    }
                },
            )
        }

}
