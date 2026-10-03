package app.nextsay.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.widget.LinearLayout
import app.nextsay.provider.LoverReplyModes

/** Inline native buttons; no popup or additional overlay window. */
class LoverReplyModeChoices(context: Context, initial: String = LoverReplyModes.NATURAL) : LinearLayout(context) {
    private val ui = NextSayUi(context)
    var value: String = LoverReplyModes.normalize("lover", initial)
        private set
    private val modes = listOf(LoverReplyModes.NATURAL, LoverReplyModes.HUANGMAO)
    private val buttons = mutableListOf<Button>()
    init {
        modes.forEach { mode ->
            val button = ui.button(LoverReplyModes.label(mode)) { value = mode; refresh() }
            buttons += button
            addView(button, LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3)) })
        }
        refresh()
    }
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        buttons.forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else .5f }
    }
    private fun refresh() {
        buttons.forEachIndexed { index, button ->
            val selected = modes[index] == value
            button.isSelected = selected
            button.setTextColor(if (selected) ui.onAccent else ui.accent)
            button.background = RippleDrawable(ColorStateList.valueOf(ui.line), ui.shape(if (selected) ui.primaryFill else ui.inset, 12, true).apply {
                if (selected) setStroke(ui.dp(1), ui.onAccent)
            }, null)
            button.contentDescription = LoverReplyModes.label(modes[index]) + "回复风格，" + if (selected) "已选择" else "未选择"
        }
    }
}
