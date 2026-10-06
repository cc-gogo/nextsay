package app.nextsay.accessibility

data class WindowPackageSnapshot(
    val isApplication: Boolean,
    val layer: Int,
    val packageName: String?,
)

class ForegroundWindowResolver {
    fun resolve(windows: List<WindowPackageSnapshot>): String? = windows
        .asSequence()
        .filter { it.isApplication }
        .maxByOrNull { it.layer }
        ?.packageName

    fun resolveEventPackage(
        resolvedPackage: String?,
        eventPackage: String?,
        supportedPackages: Set<String>,
    ): String? = resolvedPackage ?: eventPackage?.takeIf { it in supportedPackages }

    fun resolveCurrentUserPackage(
        windows: List<WindowPackageSnapshot>,
        supportedPackages: Set<String>,
    ): String? = windows.asSequence()
        .filter { it.isApplication && it.packageName in supportedPackages }
        .maxByOrNull { it.layer }
        ?.packageName
}
