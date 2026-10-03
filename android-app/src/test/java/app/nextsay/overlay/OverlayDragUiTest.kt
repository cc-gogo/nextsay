package app.nextsay.overlay

import android.accessibilityservice.AccessibilityService
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class OverlayDragUiTest {
    class Host : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }
    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).run {
        isAccessible = true; get(owner)!!
    }
    private fun registered(overlay: OverlayWindow, view: View): Boolean =
        Shadow.extract<ShadowWindowManagerImpl>(field(overlay, "windowManager")).views.contains(view)
    private fun fixture(test: (OverlayWindow, View, QuickReplyViews, () -> Int) -> Unit) {
        val host = Robolectric.buildService(Host::class.java).create()
        var dismissed = 0
        val overlay = OverlayWindow(host.get(), PanelCallbacks({}, { _, _ -> }, {}, {}, {}, {}),
            {}, { dismissed++ }, {}, {}, {})
        try {
            overlay.setSupportedAppActive(true)
            val trigger = field(overlay, "trigger") as View
            val params = trigger.layoutParams as WindowManager.LayoutParams
            params.x = 550; params.y = 400
            (field(overlay, "windowManager") as WindowManager).updateViewLayout(trigger, params)
            overlay.showQuickError("本地错误", "local-id")
            val quick = field(overlay, "quick") as QuickReplyViews
            quick.root.measure(View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
            quick.root.layout(0, 0, 560, 480)
            assertTrue("Fixture must open a real candidate root in the window registry", registered(overlay, quick.root))
            test(overlay, trigger, quick) { dismissed }
        } finally { overlay.dispose(); host.destroy() }
    }
    private fun drag(trigger: View, dx: Float, dy: Float) {
        listOf(Triple(MotionEvent.ACTION_DOWN, 10f, 10f),
            Triple(MotionEvent.ACTION_MOVE, 10f + dx, 10f + dy),
            Triple(MotionEvent.ACTION_UP, 10f + dx, 10f + dy)).forEachIndexed { index, step ->
            val event = MotionEvent.obtain(1000, 1000L + index * 100, step.first, step.second, step.third, 0)
            try { trigger.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }
    @Test fun dragMovesExistingCandidateRootWithoutDismissOrLossOfInstruction() = fixture { overlay, trigger, quick, dismissed ->
        overlay.setQuickInstruction("至少50字")
        val before = (quick.root.layoutParams as WindowManager.LayoutParams).y
        drag(trigger, 0f, 80f)
        assertTrue("Drag must keep the same candidate root registered", overlay.isQuickOpen && registered(overlay, quick.root))
        assertEquals(0, dismissed())
        assertEquals(before + 80, (quick.root.layoutParams as WindowManager.LayoutParams).y)
        assertEquals("至少50字", overlay.quickInstructionText)
    }
    @Test fun draggingToTopKeepsCandidateWindowBelowChatHeader() = fixture { overlay, trigger, quick, _ ->
        drag(trigger, 0f, -800f)
        assertTrue(overlay.isQuickOpen)
        val height = trigger.resources.displayMetrics.heightPixels
        assertTrue((quick.root.layoutParams as WindowManager.LayoutParams).y >= (height * .09f).toInt())
    }
    @Test fun openingWindowAtTopDoesNotCoverHeader() = fixture { overlay, trigger, quick, _ ->
        overlay.hideQuick()
        val params = trigger.layoutParams as WindowManager.LayoutParams
        params.y = 0
        (field(overlay, "windowManager") as WindowManager).updateViewLayout(trigger, params)
        overlay.showQuickError("本地错误")
        assertTrue((quick.root.layoutParams as WindowManager.LayoutParams).y >= (trigger.resources.displayMetrics.heightPixels * .09f).toInt())
    }
    @Test fun draggingToBottomKeepsWholeWindowAboveInputArea() = fixture { overlay, trigger, quick, _ ->
        drag(trigger, 0f, 2000f)
        assertTrue(overlay.isQuickOpen)
        val bottom = (quick.root.layoutParams as WindowManager.LayoutParams).y + quick.root.height
        assertTrue(bottom <= (trigger.resources.displayMetrics.heightPixels * .88f).toInt())
    }
    @Test fun longContentCanScrollWithoutGrowingOverHeaderAndInput() = fixture { overlay, trigger, quick, _ ->
        overlay.showQuickError("本地长提示\n".repeat(100))
        quick.root.measure(View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.AT_MOST))
        assertTrue(quick.root.measuredHeight <= (trigger.resources.displayMetrics.heightPixels * .70f).toInt())
        fun scrolls(view: View): Boolean = view is android.widget.ScrollView ||
            (view is android.view.ViewGroup && (0 until view.childCount).any { scrolls(view.getChildAt(it)) })
        assertTrue("Capped content must remain reachable", scrolls(quick.root))
    }
    @Test fun refreshedTallerContentStaysAboveInputWithoutReplacingRoot() = fixture { overlay, trigger, quick, _ ->
        drag(trigger, 0f, 700f)
        quick.root.layout(0, 0, 560, 900)
        val params = quick.root.layoutParams as WindowManager.LayoutParams
        assertTrue(overlay.isQuickOpen && registered(overlay, quick.root))
        assertTrue(params.y + quick.root.height <= (trigger.resources.displayMetrics.heightPixels * .88f).toInt())
    }
    @Test fun horizontalDragKeepsBallAccessibleWhenWindowReachesScreenEdge() = fixture { overlay, trigger, quick, _ ->
        drag(trigger, -200f, 0f)
        val ball = trigger.layoutParams as WindowManager.LayoutParams
        val window = quick.root.layoutParams as WindowManager.LayoutParams
        val ballBox = android.graphics.Rect(ball.x, ball.y, ball.x + ball.width, ball.y + ball.height)
        val contentBox = android.graphics.Rect(window.x, window.y, window.x + window.width, window.y + quick.root.height)
        assertTrue(overlay.isQuickOpen)
        assertFalse("Candidate must never cover the only expand/collapse control", android.graphics.Rect.intersects(ballBox, contentBox))
    }
}
