package app.nextsay.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.nextsay.ui.NextSayUi

/** A separate, modal input surface. The persistent candidate window never owns the IME. */
class QuickInstructionEditor(
    context: Context,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val ui = NextSayUi(context)
    val root = object : LinearLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onCancel()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp, 16.dp, 16.dp, 12.dp)
        background = ui.shape(ui.overlaySurface, stroke = true)
        elevation = 12.dp.toFloat()
    }
    private val input = ui.field("", "告诉 AI 你想怎么回…", 2).apply {
        setSingleLine(false)
        minLines = 2
        maxLines = 4
    }
    private var focusPending = false
    private val focusRunnable = Runnable {
        if (focusPending && root.isAttachedToWindow && root.hasWindowFocus()) {
            focusPending = false
            removeFocusListener()
            input.requestFocus()
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus && focusPending) input.post(focusRunnable)
    }

    init {
        root.addView(TextView(context).apply {
            text = "补充要求"
            textSize = 18f
            setTextColor(ui.ink)
        })
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.END
            addView(ui.button("取消") { onCancel() })
            addView(ui.button("完成", primary = true) { onComplete(input.text.toString()) })
        })
    }

    fun begin(text: String) {
        cancelPendingFocus()
        input.setText(text)
        input.setSelection(input.text.length)
        focusPending = true
        root.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        input.post(focusRunnable)
    }

    fun release() {
        cancelPendingFocus()
        // Only this modal editor's keyboard is dismissed; candidate copy never calls this.
        if (root.hasWindowFocus()) {
            root.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(input.windowToken, 0)
        }
        input.clearFocus()
    }

    private fun cancelPendingFocus() {
        focusPending = false
        input.removeCallbacks(focusRunnable)
        removeFocusListener()
    }

    private fun removeFocusListener() {
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
    }

    private val Int.dp: Int get() = (this * density).toInt()
}
