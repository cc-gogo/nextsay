package app.nextsay.accessibility

class ForegroundEventPolicy {
    fun shouldIgnore(
        eventPackage: String?,
        defaultImePackage: String?,
        panelOpen: Boolean,
    ): Boolean = panelOpen && defaultImePackage != null && eventPackage == defaultImePackage

    fun shouldScheduleRefresh(
        eventPackage: String?,
        foregroundPackage: String? = eventPackage,
        ownPackage: String,
        defaultImePackage: String? = null,
        supportedPackages: Set<String>,
        interactive: Boolean,
    ): Boolean = interactive &&
        eventPackage != ownPackage &&
        eventPackage != defaultImePackage &&
        eventPackage == foregroundPackage &&
        eventPackage in supportedPackages
}
