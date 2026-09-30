package app.nextsay.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundEventPolicyTest {
    private val policy = ForegroundEventPolicy()

    @Test
    fun `ignores the current input method only while panel is open`() {
        assertTrue(
            policy.shouldIgnore(
                eventPackage = "com.sohu.inputmethod.sogou",
                defaultImePackage = "com.sohu.inputmethod.sogou",
                panelOpen = true,
            ),
        )
    }

    @Test
    fun `does not ignore input method while panel is closed`() {
        assertFalse(
            policy.shouldIgnore(
                eventPackage = "com.sohu.inputmethod.sogou",
                defaultImePackage = "com.sohu.inputmethod.sogou",
                panelOpen = false,
            ),
        )
    }

    @Test
    fun `does not ignore a real switch from WeChat to QQ`() {
        assertFalse(
            policy.shouldIgnore(
                eventPackage = "com.tencent.mobileqq",
                defaultImePackage = "com.sohu.inputmethod.sogou",
                panelOpen = true,
            ),
        )
    }

    @Test
    fun `does not ignore leaving supported applications`() {
        assertFalse(
            policy.shouldIgnore(
                eventPackage = "com.honor.launcher",
                defaultImePackage = "com.sohu.inputmethod.sogou",
                panelOpen = true,
            ),
        )
    }

    @Test
    fun `does not ignore any package when default input method is unknown`() {
        assertFalse(
            policy.shouldIgnore(
                eventPackage = "com.sohu.inputmethod.sogou",
                defaultImePackage = null,
                panelOpen = true,
            ),
        )
    }

    @Test
    fun `supported app content changes refresh only while screen is interactive`() {
        assertTrue(
            policy.shouldScheduleRefresh(
                eventPackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                supportedPackages = setOf("com.tencent.mm"),
                interactive = true,
            ),
        )
        assertFalse(
            policy.shouldScheduleRefresh(
                eventPackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                supportedPackages = setOf("com.tencent.mm"),
                interactive = false,
            ),
        )
    }

    @Test
    fun `own overlay events never schedule refresh`() {
        assertFalse(
            policy.shouldScheduleRefresh(
                eventPackage = "app.nextsay",
                ownPackage = "app.nextsay",
                supportedPackages = setOf("com.tencent.mm"),
                interactive = true,
            ),
        )
    }

    @Test
    fun `input method and system UI events do not refresh the foreground chat`() {
        assertFalse(
            policy.shouldScheduleRefresh(
                eventPackage = "com.sohu.inputmethod.sogou",
                foregroundPackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.sohu.inputmethod.sogou",
                supportedPackages = setOf("com.tencent.mm"),
                interactive = true,
            ),
        )
        assertFalse(
            policy.shouldScheduleRefresh(
                eventPackage = "com.android.systemui",
                foregroundPackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.sohu.inputmethod.sogou",
                supportedPackages = setOf("com.tencent.mm"),
                interactive = true,
            ),
        )
    }
}
