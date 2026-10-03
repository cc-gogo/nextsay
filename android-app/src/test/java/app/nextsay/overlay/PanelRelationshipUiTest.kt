package app.nextsay.overlay

import android.content.res.Configuration
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.nextsay.contacts.GenerationExtras
import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.ui.NextSayUi
import app.nextsay.ui.RelationshipChoices
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PanelRelationshipUiTest {
    private val callbacks = PanelCallbacks({}, { _, _ -> }, {}, {}, {}, {})
    private fun context(id: String?, relationship: String = "unspecified") = ChatContext(
        "wechat", "com.tencent.mm", listOf(ChatMessage(MessageRole.OTHER, "本地测试", 1f)), "", 1f,
        contactId = id, generationExtras = id?.let { GenerationExtras(relationship) },
    )
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun picker(panel: PanelViews) = descendants(panel.root).filterIsInstance<RelationshipChoices>().single()

    @Test fun recognizedContactShowsSavedModeAndDoesNotOfferASecondRelationshipSetting() {
        var submitted = ""
        val panel = OverlayViewFactory(RuntimeEnvironment.getApplication()).panel(callbacks.copy(onGenerate = { _, relation -> submitted = relation }))
        panel.render(OverlayState.Preview(context("a", "lover").copy(generationExtras = GenerationExtras("lover", replyMode = "huangmao"))))
        assertEquals(View.GONE, picker(panel).visibility)
        assertFalse(picker(panel).isEnabled)
        assertTrue(descendants(panel.root).filterIsInstance<TextView>().any { it.text.toString().contains("黄毛") })
        descendants(panel.root).filterIsInstance<TextView>().single { it.text == "生成回复" }.performClick()
        assertEquals("lover", submitted)
    }

    @Test fun unmatchedProfileDoesNotShowAnUnrelatedOrdinaryRelationshipSelection() {
        val panel = OverlayViewFactory(RuntimeEnvironment.getApplication()).panel(callbacks)
        panel.render(OverlayState.Preview(context(null)))
        assertEquals(View.GONE, picker(panel).visibility)
        assertTrue(descendants(panel.root).filterIsInstance<TextView>().any { it.text.toString() == "选择聊天对象" && it.visibility == View.VISIBLE })
    }

    @Test fun systemNightModeDoesNotDarkenNextSayPanelsOrFields() {
        val base = RuntimeEnvironment.getApplication()
        val config = Configuration(base.resources.configuration).apply { uiMode = Configuration.UI_MODE_NIGHT_YES }
        val ui = NextSayUi(base.createConfigurationContext(config))
        for (fill in listOf(ui.canvas, ui.surface, ui.inset, ui.overlaySurface)) {
            assertTrue("NextSay backgrounds should stay pale in system night mode", Color.red(fill) >= 230 && Color.green(fill) >= 240 && Color.blue(fill) >= 230)
        }
        assertTrue("Light panels need dark, readable text", Color.red(ui.ink) < 100)
    }
    @Test fun unassociatedContextDoesNotClaimSavedProfileWasApplied() {
        val panel = OverlayViewFactory(RuntimeEnvironment.getApplication()).panel(callbacks)
        panel.render(OverlayState.Preview(context(null)))
        val labels = descendants(panel.root).filterIsInstance<TextView>().map { it.text.toString() }
        assertFalse("No profile was resolved, so the status must not claim one was applied", labels.any { it.contains("已带入对象资料") })
        assertTrue("Explain that no managed profile was found", labels.any { it.contains("未找到当前对象") })
    }

    @Test fun retryUsesManagedRelationshipInsteadOfStaleHiddenSelection() {
        var submitted = ""
        val panel = OverlayViewFactory(RuntimeEnvironment.getApplication()).panel(callbacks.copy(onGenerate = { _, relation -> submitted = relation }))
        val lover = context("a", "lover")
        panel.render(OverlayState.Preview(lover))
        panel.render(OverlayState.Error(lover, "本地模拟错误"))
        descendants(panel.root).filterIsInstance<TextView>().single { it.text == "普通" }.performClick()
        descendants(panel.root).filterIsInstance<TextView>().single { it.text == "重试" }.performClick()
        assertEquals("lover", submitted)
    }

    @Test fun captureBusyDisablesAdvancedInputsWithoutDetachingRoot() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val overlay = OverlayWindow(HostedService(activity.get()), callbacks, {}, {}, {}, {}, {})
        try {
            overlay.renderAdvanced(OverlayState.Preview(context("a", "lover")))
            shadowOf(Looper.getMainLooper()).idle()
            val panelRoot = (overlay.javaClass.getDeclaredField("panel").apply { isAccessible = true }.get(overlay) as PanelViews).root
            assertTrue(panelRoot.isAttachedToWindow)
            overlay.setBusy(true)
            assertFalse(descendants(panelRoot).filterIsInstance<TextView>().single { it.text == "生成回复" }.isEnabled)
            assertFalse(descendants(panelRoot).filterIsInstance<TextView>().single { it.text == "朋友" }.isEnabled)
            assertTrue(panelRoot.isAttachedToWindow)
            overlay.setBusy(false)
            assertTrue(descendants(panelRoot).filterIsInstance<TextView>().single { it.text == "生成回复" }.isEnabled)
            overlay.renderAdvanced(OverlayState.Loading(context("a", "lover")))
            overlay.setBusy(false)
            assertFalse(descendants(panelRoot).filterIsInstance<TextView>().single { it.text == "生成回复" }.isEnabled)
        } finally { overlay.dispose(); activity.pause().stop().destroy() }
    }
    private class HostedService(activity: Activity) : AccessibilityService() {
        private val windows = object : WindowManager by activity.getSystemService(WindowManager::class.java) {
            private val delegate = activity.getSystemService(WindowManager::class.java)
            override fun addView(view: View, params: ViewGroup.LayoutParams) {
                (params as WindowManager.LayoutParams).apply { type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL; token = activity.window.decorView.windowToken }
                delegate.addView(view, params)
            }
        }
        init { attachBaseContext(activity) }
        override fun getSystemService(name: String): Any? = if (name == WINDOW_SERVICE) windows else super.getSystemService(name)
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }

    @Test fun savedLoverIsSelectedAndSubmitted() {
        var submitted = ""
        val panel = OverlayViewFactory(RuntimeEnvironment.getApplication()).panel(callbacks.copy(onGenerate = { _, relation -> submitted = relation }))
        panel.render(OverlayState.Preview(context("a", "lover")))
        assertEquals("lover", picker(panel).value)
        assertTrue(descendants(panel.root).filterIsInstance<TextView>().single { it.text == "恋人" }.isSelected)
        descendants(panel.root).filterIsInstance<TextView>().single { it.text == "生成回复" }.performClick()
        assertEquals("lover", submitted)
    }
    @Test fun managedRelationshipSurvivesRefreshAndChangesWithTheCurrentObject() {
        val panel = OverlayViewFactory(RuntimeEnvironment.getApplication()).panel(callbacks)
        val lover = context("a", "lover")
        panel.render(OverlayState.Preview(lover))
        assertFalse(picker(panel).isEnabled)
        panel.render(OverlayState.Loading(lover))
        panel.render(OverlayState.Error(lover, "本地错误"))
        panel.render(OverlayState.Preview(lover))
        assertEquals("lover", picker(panel).value)
        panel.render(OverlayState.Idle)
        panel.render(OverlayState.Preview(lover))
        assertEquals("lover", picker(panel).value)
        panel.render(OverlayState.Preview(context("b", "colleague")))
        assertEquals("colleague", picker(panel).value)
        panel.render(OverlayState.Preview(context(null)))
        assertEquals("unspecified", picker(panel).value)
    }
    @Test fun editedSavedRelationshipLoadsOnNextOpen() {
        val panel = OverlayViewFactory(RuntimeEnvironment.getApplication()).panel(callbacks)
        panel.render(OverlayState.Preview(context("a", "friend")))
        panel.render(OverlayState.Idle)
        panel.render(OverlayState.Preview(context("a", "lover")))
        assertEquals("lover", picker(panel).value)
    }
    @Test fun lightPrimaryHasPaleFillAndReadableText() {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply { uiMode = Configuration.UI_MODE_NIGHT_NO }
        val ui = NextSayUi(base.createConfigurationContext(configuration))
        val button = ui.button("生成", primary = true) {}
        val fill = ((button.background as RippleDrawable).getDrawable(0) as GradientDrawable).color!!.defaultColor
        assertTrue("Expected pale mint fill", Color.red(fill) >= 190 && Color.green(fill) >= 220 && Color.blue(fill) >= 200)
        assertTrue("Pale button needs dark text", Color.red(button.currentTextColor) < 100)
    }
    @Test fun bothThemesKeepButtonsAndTranslucentOverlayTextReadable() {
        val base = RuntimeEnvironment.getApplication()
        fun contrast(a: Int, b: Int): Double {
            fun luminance(color: Int): Double {
                fun channel(value: Int): Double {
                    val v = value / 255.0
                    return if (v <= .04045) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
                }
                return .2126 * channel(Color.red(color)) + .7152 * channel(Color.green(color)) + .0722 * channel(Color.blue(color))
            }
            val x = luminance(a); val y = luminance(b)
            return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
        }
        for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(base.resources.configuration).apply { uiMode = mode }
            val ui = NextSayUi(base.createConfigurationContext(config))
            for (primary in listOf(false, true)) {
                val button = ui.button("回复", primary) {}
                val fill = ((button.background as RippleDrawable).getDrawable(0) as GradientDrawable).color!!.defaultColor
                assertTrue("Button text must meet AA", contrast(fill, button.currentTextColor) >= 4.5)
            }
            val alpha = Color.alpha(ui.overlaySurface) / 255.0
            for (underlay in listOf(0, 255)) {
                fun composite(c: Int) = (c * alpha + underlay * (1 - alpha)).toInt()
                val fill = Color.rgb(composite(Color.red(ui.overlaySurface)), composite(Color.green(ui.overlaySurface)), composite(Color.blue(ui.overlaySurface)))
                assertTrue("Overlay text must meet AA over light and dark chats", contrast(fill, ui.ink) >= 4.5)
                assertTrue("Overlay helper text must meet AA", contrast(fill, ui.muted) >= 4.5)
            }
        }
    }
}
