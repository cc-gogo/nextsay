package app.nextsay.capture

import kotlin.math.max

class AutoRefreshScheduler(
    private val debounceMillis: Long = 800L,
    private val wechatCooldownMillis: Long = 2_000L,
) {
    private var pendingPackage: String? = null
    private var pendingAtMillis: Long? = null
    private var captureRunning = false
    private var lastWechatOcrStartedAt: Long? = null

    @Synchronized
    fun onPageChanged(packageName: String, nowMillis: Long): Long {
        pendingPackage = packageName
        pendingAtMillis = earliestTime(packageName, nowMillis)
        return pendingAtMillis!!
    }

    @Synchronized
    fun consumeDue(nowMillis: Long): String? {
        val dueAt = pendingAtMillis ?: return null
        if (captureRunning || nowMillis < dueAt) return null
        return pendingPackage.also {
            pendingPackage = null
            pendingAtMillis = null
        }
    }

    @Synchronized
    fun onCaptureStarted() {
        captureRunning = true
    }

    @Synchronized
    fun onCaptureFinished(nowMillis: Long, packageName: String): Long? {
        captureRunning = false
        val pending = pendingPackage ?: return null
        val existing = pendingAtMillis ?: nowMillis
        pendingAtMillis = if (pending == WECHAT_PACKAGE && pending == packageName) {
            max(existing, lastWechatOcrStartedAt?.plus(wechatCooldownMillis) ?: existing)
        } else {
            existing
        }
        return pendingAtMillis
    }

    @Synchronized
    fun recordWechatOcrStarted(nowMillis: Long) {
        lastWechatOcrStartedAt = nowMillis
    }

    @Synchronized
    fun cancel() {
        pendingPackage = null
        pendingAtMillis = null
        captureRunning = false
    }

    private fun earliestTime(packageName: String, nowMillis: Long): Long {
        val debounceAt = nowMillis + debounceMillis
        if (packageName != WECHAT_PACKAGE) return debounceAt
        val cooldownAt = lastWechatOcrStartedAt?.plus(wechatCooldownMillis) ?: Long.MIN_VALUE
        return max(debounceAt, cooldownAt)
    }

    private companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
    }
}
