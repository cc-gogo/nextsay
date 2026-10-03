package app.nextsay.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Native Android views, one palette/radius/spacing scale shared by all app screens.
 * Material-inspired hierarchy; not a reimplementation of the Material 3 library. */
class NextSayUi(val context: Context) {
    // Fixed light palette, including overlays hosted by a service in system night mode.
    val canvas = color("#F5FAF7")
    val surface = color("#FCFFFD")
    val inset = color("#F1F9F4")
    val ink = color("#263E31")
    val muted = color("#506657")
    val accent = color("#38634F")
    val primaryFill = color("#E6F5EC")
    val onAccent = color("#244B3B")
    val line = color("#DCEBE2")
    // Only the background is translucent; never fade the text or whole window.
    val overlaySurface = Color.argb(245, Color.red(surface), Color.green(surface), Color.blue(surface))
    val triggerFill = color("#F2E6F5EC")
    val danger = color("#A7352B")
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun shape(fill: Int, radius: Int = 16, stroke: Boolean = false) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (stroke) setStroke(dp(1), line)
    }
    fun text(value: String, size: Float = 15f, strong: Boolean = false, tint: Int = ink) = TextView(context).apply {
        text = value; textSize = size; setTextColor(tint)
        if (strong) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    fun column(padding: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }
    fun add(parent: LinearLayout, view: View, top: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    fun card() = column(18).apply { background = shape(surface); elevation = dp(1).toFloat() }
    fun button(label: String, primary: Boolean = false, destructive: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = label; textSize = 15f; isAllCaps = false
        minimumHeight = dp(48); minHeight = dp(48); minWidth = 0; minimumWidth = 0
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setTextColor(if (primary) onAccent else if (destructive) danger else accent)
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(line), shape(if (primary) primaryFill else inset, 12, true), null)
        setOnClickListener { action() }
    }
    fun field(value: String, hintText: String, lines: Int = 1) = EditText(context).apply {
        setText(value); hint = hintText; textSize = 15f; setTextColor(ink); setHintTextColor(muted)
        minLines = lines; maxLines = maxOf(lines, 8); minimumHeight = dp(52)
        gravity = Gravity.TOP or Gravity.START
        setPadding(dp(14), dp(13), dp(14), dp(13)); backgroundTintList = null
        background = shape(inset, 12, true)
        if (lines == 1) setSingleLine(true)
    }
    fun section(parent: LinearLayout, title: String, subtitle: String? = null): LinearLayout {
        val card = card(); add(card, text(title, 18f, true))
        if (subtitle != null) add(card, text(subtitle, 13f, tint = muted), 6)
        add(parent, card, 16); return card
    }
    @Suppress("DEPRECATION")
    fun screen(activity: Activity, title: String, subtitle: String, back: (() -> Unit)? = null): LinearLayout {
        activity.window.statusBarColor = canvas; activity.window.navigationBarColor = canvas
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val content = column(20)
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        if (back != null) heading.addView(button("‹", action = back).apply {
            contentDescription = "返回"; textSize = 28f; setPadding(0, 0, 0, 0)
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
        heading.addView(text(title, 28f, true), LinearLayout.LayoutParams(0, -2, 1f))
        add(content, heading, 8)
        add(content, text(subtitle, 14f, tint = muted), 6)
        activity.setContentView(ScrollView(context).apply { setBackgroundColor(canvas); isFillViewport = true; addView(content) })
        return content
    }
    fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit = {}) = Switch(context).apply {
        text = label; textSize = 15f; setTextColor(ink); minimumHeight = dp(52)
        setPadding(0, dp(8), 0, dp(8)); isChecked = initial
        thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, muted))
        setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }
    private fun color(hex: String) = Color.parseColor(hex)
}

/** Inline chips avoid Spinner popup windows: safe in both Activities and service overlays. */
class RelationshipChoices(context: Context, initial: String = "unspecified", private val onChange: (String) -> Unit = {}) : LinearLayout(context) {
    private val ui = NextSayUi(context)
    var value = initial.takeIf { it in VALUES } ?: "unspecified"
        private set
    private val buttons = mutableListOf<Button>()
    init {
        orientation = VERTICAL
        LABELS.chunked(4).forEachIndexed { rowIndex, labels ->
            val row = LinearLayout(context)
            labels.forEachIndexed { column, label ->
                val index = rowIndex * 4 + column
                val button = ui.button(label) { value = VALUES[index]; refresh(); onChange(value) }.apply {
                    textSize = 13f; setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6))
                }
                buttons += button
                row.addView(button, LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(3)) })
            }
            ui.add(this, row)
        }
        refresh()
    }
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); buttons.forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else .5f } }
    fun select(selectedValue: String) {
        value = selectedValue.takeIf { it in VALUES } ?: "unspecified"
        refresh()
        onChange(value)
    }
    private fun refresh() { buttons.forEachIndexed { i, b ->
        val selected = VALUES[i] == value
        b.isSelected = selected; b.setTextColor(if (selected) ui.onAccent else ui.accent)
        b.background = RippleDrawable(ColorStateList.valueOf(ui.line), ui.shape(if (selected) ui.primaryFill else ui.inset, 12, selected).apply {
            if (selected) setStroke(ui.dp(1), ui.onAccent)
        }, null)
        b.typeface = Typeface.create(if (selected) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        b.contentDescription = LABELS[i] + if (selected) "，已选择" else "，未选择"
    } }
    companion object {
        val LABELS = listOf("普通", "领导", "老师", "客户", "同事", "朋友", "家人", "恋人")
        val VALUES = listOf("unspecified", "manager", "teacher", "customer", "colleague", "friend", "family", "lover")
        fun label(value: String) = LABELS[VALUES.indexOf(value).coerceAtLeast(0)]
    }
}
