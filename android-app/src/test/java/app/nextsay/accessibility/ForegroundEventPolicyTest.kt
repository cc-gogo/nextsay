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

    @Test
    fun `keeps the active chat when the input method temporarily hides the application window`() {
        assertTrue(
            policy.shouldKeepActiveDuringTransientWindow(
                eventPackage = "com.miui.inputmethod",
                resolvedForeground = null,
                activePackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.miui.inputmethod",
                supportedPackages = setOf("com.tencent.mm"),
            ),
        )
    }

    @Test
    fun `keeps the active chat for a Xiaomi keyboard event even if its package is not the saved default`() {
        assertTrue(
            policy.shouldKeepActiveDuringTransientWindow(
                eventPackage = "com.baidu.input_mi",
                resolvedForeground = null,
                activePackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.iflytek.inputmethod.miui",
                supportedPackages = setOf("com.tencent.mm"),
            ),
        )
    }

    @Test
    fun `keeps the active chat when Xiaomi keyboard leaves WeChat as resolved foreground`() {
        assertTrue(
            policy.shouldKeepActiveDuringTransientWindow(
                eventPackage = "com.miui.inputmethod",
                resolvedForeground = "com.tencent.mm",
                activePackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.iflytek.inputmethod.miui",
                supportedPackages = setOf("com.tencent.mm"),
            ),
        )
    }

    @Test
    fun `keeps the active chat for an unknown transition package when chat remains foreground`() {
        assertTrue(
            policy.shouldKeepActiveDuringTransientWindow(
                eventPackage = "com.miui.some_transient_window",
                resolvedForeground = "com.tencent.mm",
                activePackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.baidu.input_mi",
                supportedPackages = setOf("com.tencent.mm"),
            ),
        )
    }

    @Test
    fun `does not keep the chat when the user really leaves to the launcher`() {
        assertFalse(
            policy.shouldKeepActiveDuringTransientWindow(
                eventPackage = "com.miui.home",
                resolvedForeground = "com.miui.home",
                activePackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.miui.inputmethod",
                supportedPackages = setOf("com.tencent.mm"),
            ),
        )
    }

    @Test
    fun `does not treat a switch from WeChat to QQ as an IME transition`() {
        assertFalse(
            policy.shouldKeepActiveDuringTransientWindow(
                eventPackage = "com.tencent.mobileqq",
                resolvedForeground = "com.tencent.mobileqq",
                activePackage = "com.tencent.mm",
                ownPackage = "app.nextsay",
                defaultImePackage = "com.baidu.input_mi",
                supportedPackages = setOf("com.tencent.mm", "com.tencent.mobileqq"),
            ),
        )
    }
}
