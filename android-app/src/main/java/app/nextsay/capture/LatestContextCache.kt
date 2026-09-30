package app.nextsay.capture

import app.nextsay.context.ChatContext

data class CaptureTicket(
    val packageName: String,
    val pageEpoch: Long,
)

class LatestContextCache {
    private var activePackage: String? = null
    private var pageEpoch = 0L
    private var capturedEpoch = -1L
    private var context: ChatContext? = null
    private var refreshing = false
    private var activeTicket: CaptureTicket? = null

    @Synchronized
    fun markPageChanged(packageName: String): Long {
        pageEpoch += 1
        if (activePackage != packageName) {
            activePackage = packageName
            capturedEpoch = -1L
            context = null
            refreshing = false
            activeTicket = null
        }
        return pageEpoch
    }

    @Synchronized
    fun beginCapture(packageName: String): CaptureTicket? {
        if (activePackage != packageName || refreshing) return null
        return CaptureTicket(packageName, pageEpoch).also {
            refreshing = true
            activeTicket = it
        }
    }

    @Synchronized
    fun complete(ticket: CaptureTicket, newContext: ChatContext): Boolean {
        if (ticket != activeTicket) return false
        refreshing = false
        activeTicket = null
        if (ticket.packageName != activePackage || ticket.pageEpoch != pageEpoch) return false
        context = newContext
        capturedEpoch = ticket.pageEpoch
        return true
    }

    @Synchronized
    fun fail(ticket: CaptureTicket) {
        if (ticket == activeTicket) {
            refreshing = false
            activeTicket = null
        }
    }

    @Synchronized
    fun fresh(packageName: String): ChatContext? = context?.takeIf {
        activePackage == packageName && capturedEpoch == pageEpoch
    }

    @Synchronized
    fun isDirty(packageName: String): Boolean =
        activePackage == packageName && (context == null || capturedEpoch != pageEpoch)

    @Synchronized
    fun clear() {
        activePackage = null
        capturedEpoch = -1L
        context = null
        refreshing = false
        activeTicket = null
    }
}
