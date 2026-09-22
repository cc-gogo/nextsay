package app.nextsay.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

data class ImeKeyboardCallbacks(
    val onGenerate: () -> Unit,
    val onCandidate: (Int) -> Unit,
    val onSwitchInputMethod: () -> Unit,
    val onCopyDiagnostics: (String) -> Unit,
)

class ImeKeyboardViewFactory(private val context: Context) {
    fun create(callbacks: ImeKeyboardCallbacks): ImeKeyboardViews {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 12.dp, 16.dp, 14.dp)
            setBackgroundColor(Color.rgb(244, 247, 246))
        }
        val status = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.rgb(55, 67, 62))
            setPadding(0, 0, 0, 8.dp)
        }
        val switchInputMethod = Button(context).apply {
            text = "切换输入法"
            setOnClickListener { callbacks.onSwitchInputMethod() }
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "NextSay"
                textSize = 20f
                setTextColor(Color.rgb(23, 107, 77))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(switchInputMethod)
        }
        val generate = Button(context).apply {
            text = "读取上下文并生成"
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.rgb(23, 107, 77))
            setOnClickListener { callbacks.onGenerate() }
        }
        val candidates = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val copyDiagnostics = Button(context).apply {
            text = "复制诊断信息"
            visibility = View.GONE
        }
        val candidateScroll = ScrollView(context).apply {
            addView(candidates)
        }

        root.addView(header, matchWrap())
        root.addView(status, matchWrap())
        root.addView(generate, matchWrap())
        root.addView(copyDiagnostics, matchWrap())
        root.addView(candidateScroll, matchWrap())
        return ImeKeyboardViews(root, status, generate, copyDiagnostics, candidates, callbacks)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
}

class ImeKeyboardViews(
    val root: View,
    private val status: TextView,
    private val generate: Button,
    private val copyDiagnostics: Button,
    private val candidates: LinearLayout,
    private val callbacks: ImeKeyboardCallbacks,
) {
    fun render(state: ImeReplyState) {
        candidates.removeAllViews()
        copyDiagnostics.visibility = View.GONE
        when (state) {
            is ImeReplyState.Unavailable -> renderUnavailable(state.reason)
            is ImeReplyState.Ready -> {
                status.text = "准备好后读取当前可见对话"
                configureGenerate("读取上下文并生成", enabled = true)
            }
            is ImeReplyState.Loading -> {
                status.text = "正在识别对话并生成回复"
                configureGenerate("正在生成…", enabled = false)
            }
            is ImeReplyState.Results -> {
                status.text = state.message ?: "选择一条写入当前光标位置"
                configureGenerate("重新生成", enabled = true)
                state.candidates.forEachIndexed(::addCandidate)
            }
            is ImeReplyState.Error -> {
                status.text = state.message
                configureGenerate("重新生成", enabled = true)
                state.diagnosticId?.let { diagnosticId ->
                    copyDiagnostics.visibility = View.VISIBLE
                    copyDiagnostics.setOnClickListener {
                        callbacks.onCopyDiagnostics(diagnosticId)
                    }
                }
            }
            is ImeReplyState.Inserted -> {
                status.text = "已写入，请检查后手动发送"
                configureGenerate("再次生成", enabled = true)
            }
        }
    }

    private fun renderUnavailable(reason: ImeUnavailableReason) {
        status.text = when (reason) {
            ImeUnavailableReason.UNSUPPORTED_APP -> "目前仅支持微信和 QQ/TIM"
            ImeUnavailableReason.SENSITIVE_FIELD -> "敏感输入框中已停用 NextSay"
            ImeUnavailableReason.SERVICE_DISCONNECTED -> "请先开启 NextSay 无障碍服务"
        }
        configureGenerate("读取上下文并生成", enabled = false)
    }

    private fun configureGenerate(label: String, enabled: Boolean) {
        generate.text = label
        generate.isEnabled = enabled
        generate.alpha = if (enabled) 1f else 0.6f
    }

    private fun addCandidate(index: Int, candidate: app.nextsay.overlay.ReplyCandidate) {
        val surface = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(1.dp, Color.rgb(210, 220, 216))
            cornerRadius = 10.dp.toFloat()
        }
        candidates.addView(TextView(candidates.context).apply {
            text = candidate.text
            textSize = 15f
            setTextColor(Color.rgb(26, 31, 29))
            setPadding(12.dp, 11.dp, 12.dp, 11.dp)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(Color.rgb(187, 218, 205)),
                surface,
                surface,
            )
            setOnClickListener {
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                callbacks.onCandidate(index)
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = 7.dp
        })
    }

    private val Int.dp: Int get() = (this * root.resources.displayMetrics.density).toInt()
}
