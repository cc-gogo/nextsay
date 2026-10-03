package app.nextsay.accessibility

import android.view.accessibility.AccessibilityEvent

class ForegroundEventPolicy {
    fun shouldInvalidateContext(eventType: Int, supportedPackageChanged: Boolean): Boolean =
        supportedPackageChanged || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
    fun shouldIgnore(
        eventPackage: String?,
        defaultImePackage: String?,
        panelOpen: Boolean,
    ): Boolean = panelOpen && defaultImePackage != null && eventPackage == defaultImePackage

    /**
     * Some Android skins briefly report no application window while showing
     * the IME. Do not interpret that transient gap as leaving the chat.
     */
    fun shouldKeepActiveDuringTransientWindow(
        eventPackage: String?,
        resolvedForeground: String?,
        activePackage: String?,
        ownPackage: String,
        defaultImePackage: String?,
        supportedPackages: Set<String>,
    ): Boolean = activePackage != null &&
        activePackage in supportedPackages &&
        resolvedForeground == null &&
        (eventPackage == ownPackage || eventPackage == defaultImePackage ||
            eventPackage == "com.android.systemui" || eventPackage?.endsWith(".systemui") == true ||
            eventPackage?.contains("inputmethod", ignoreCase = true) == true ||
            eventPackage?.contains("input_", ignoreCase = true) == true ||
            eventPackage?.contains("keyboard", ignoreCase = true) == true)

    fun shouldScheduleRefresh(
        eventPackage: String?,
        foregroundPackage: String? = eventPackage,
        ownPackage: String,
        defaultImePackage: String? = null,
        supportedPackages: Set<String>,
        interactive: Boolean,
        eventType: Int = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        contentOpen: Boolean = false,
        supportedPackageChanged: Boolean = false,
    ): Boolean = interactive && !contentOpen &&
        (supportedPackageChanged || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
        eventPackage != ownPackage &&
        eventPackage != defaultImePackage &&
        eventPackage == foregroundPackage &&
        eventPackage in supportedPackages
}
