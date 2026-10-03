package app.nextsay.ui

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeUiTest {
    @Test fun lightPrimaryUsesPaleFillWithReadableText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val base = instrumentation.targetContext
            val config = Configuration(base.resources.configuration).apply { uiMode = Configuration.UI_MODE_NIGHT_NO }
            val ui = NextSayUi(base.createConfigurationContext(config))
            val button = ui.button("生成", primary = true) {}
            val fill = ((button.background as RippleDrawable).getDrawable(0) as GradientDrawable).color!!.defaultColor
            assertTrue("Primary background should feel pale, not solid dark green", Color.red(fill) >= 190 && Color.green(fill) >= 220 && Color.blue(fill) >= 200)
            assertTrue("Pale button still needs readable text", contrast(fill, button.currentTextColor) >= 4.5)
        }
    }
    @Test fun relationshipSelectionChangesSubmittedValueAndDisabledState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val choices = RelationshipChoices(instrumentation.targetContext, "friend")
            val buttons = (0 until choices.childCount).flatMap { i ->
                val row = choices.getChildAt(i) as ViewGroup
                (0 until row.childCount).map { row.getChildAt(it) as Button }
            }
            buttons.first { it.text == "恋人" }.performClick()
            assertEquals("lover", choices.value)
            assertEquals(1, buttons.count { it.isSelected })
            choices.isEnabled = false
            assertTrue(buttons.none { it.isEnabled })
        }
    }
    @Test fun lightAndDarkPrimaryButtonsRemainReadable() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES).forEach { mode ->
            val configuration = Configuration(base.resources.configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode }
            val ui = NextSayUi(base.createConfigurationContext(configuration))
            assertTrue("Primary text contrast must pass AA", contrast(ui.primaryFill, ui.onAccent) >= 4.5)
            assertTrue("Helper text must pass AA", contrast(ui.muted, ui.surface) >= 4.5)
            assertTrue("Field placeholder must pass AA", contrast(ui.muted, ui.inset) >= 4.5)
        }
    }
    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 255) / 255.0
                return if (value <= .04045) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
            }
            return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
        }
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
