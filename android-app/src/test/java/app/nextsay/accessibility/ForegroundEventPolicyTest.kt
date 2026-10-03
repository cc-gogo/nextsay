package app.nextsay.accessibility

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundEventPolicyTest {
    private val policy = ForegroundEventPolicy()

    @Test
    fun `scroll invalidates in-flight frame without starting background OCR`() {
        assertTrue(policy.shouldInvalidateContext(AccessibilityEvent.TYPE_VIEW_SCROLLED, false))
        assertFalse(refresh(AccessibilityEvent.TYPE_VIEW_SCROLLED, contentOpen = true))
        assertFalse(refresh(AccessibilityEvent.TYPE_VIEW_SCROLLED))
    }

    @Test
    fun `window visibility changes do not start another OCR`() {
        assertFalse(refresh(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
        assertFalse(refresh(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
    }

    @Test
    fun `opening supported app still primes context from a window event`() {
        assertTrue(refresh(AccessibilityEvent.TYPE_WINDOWS_CHANGED, switched = true))
    }

    @Test
    fun `open candidate window pauses automatic OCR even on real content changes`() {
        assertFalse(refresh(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, contentOpen = true))
        assertFalse(refresh(AccessibilityEvent.TYPE_WINDOWS_CHANGED, contentOpen = true, switched = true))
    }

    @Test
    fun `real changes while candidates are open still invalidate cached context`() {
        assertTrue(policy.shouldInvalidateContext(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, false))
        assertTrue(policy.shouldInvalidateContext(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, false))
        assertFalse(policy.shouldInvalidateContext(AccessibilityEvent.TYPE_WINDOWS_CHANGED, false))
        assertTrue(policy.shouldInvalidateContext(AccessibilityEvent.TYPE_WINDOWS_CHANGED, true))
    }

    private fun refresh(eventType: Int, contentOpen: Boolean = false, switched: Boolean = false) =
        policy.shouldScheduleRefresh(
            eventPackage = "com.tencent.mm",
            ownPackage = "app.nextsay",
            supportedPackages = setOf("com.tencent.mm"),
            interactive = true,
            eventType = eventType,
            contentOpen = contentOpen,
            supportedPackageChanged = switched,
        )

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
