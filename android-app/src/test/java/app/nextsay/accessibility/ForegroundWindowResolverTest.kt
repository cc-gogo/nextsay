package app.nextsay.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundWindowResolverTest {
    private val resolver = ForegroundWindowResolver()

    @Test
    fun `ignores input method above the chat application`() {
        val foreground = resolver.resolve(
            listOf(
                WindowPackageSnapshot(isApplication = true, layer = 1, packageName = "com.tencent.mm"),
                WindowPackageSnapshot(isApplication = false, layer = 3, packageName = "com.sohu.inputmethod.sogou"),
            ),
        )

        assertEquals("com.tencent.mm", foreground)
    }

    @Test
    fun `selects a higher application when user leaves chat`() {
        val foreground = resolver.resolve(
            listOf(
                WindowPackageSnapshot(isApplication = true, layer = 1, packageName = "com.tencent.mm"),
                WindowPackageSnapshot(isApplication = true, layer = 4, packageName = "com.honor.browser"),
                WindowPackageSnapshot(isApplication = false, layer = 5, packageName = "com.android.systemui"),
            ),
        )

        assertEquals("com.honor.browser", foreground)
    }

    @Test
    fun `does not reuse a stale chat package when foreground is unknown`() {
        val foreground = resolver.resolveEventPackage(
            resolvedPackage = null,
            eventPackage = "com.android.systemui",
            supportedPackages = setOf("com.tencent.mm", "com.tencent.mobileqq"),
        )

        assertEquals(null, foreground)
    }

    @Test
    fun `uses a supported event package when application windows are unavailable`() {
        val foreground = resolver.resolveEventPackage(
            resolvedPackage = null,
            eventPackage = "com.tencent.mobileqq",
            supportedPackages = setOf("com.tencent.mm", "com.tencent.mobileqq"),
        )

        assertEquals("com.tencent.mobileqq", foreground)
    }

    @Test
    fun `selects only the highest supported application window`() {
        val foreground = resolver.resolveCurrentUserPackage(
            listOf(
                WindowPackageSnapshot(true, 2, "com.tencent.mm"),
                WindowPackageSnapshot(true, 8, "com.android.settings"),
                WindowPackageSnapshot(true, 4, "com.tencent.mobileqq"),
                WindowPackageSnapshot(false, 20, "com.tencent.mm"),
            ),
            setOf("com.tencent.mm", "com.tencent.mobileqq"),
        )

        assertEquals("com.tencent.mobileqq", foreground)
    }
}
