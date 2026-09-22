package app.nextsay.overlay

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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

sealed interface QuickReplyModel {
    data object Hidden : QuickReplyModel
    data class Loading(val message: String) : QuickReplyModel
    data class Candidates(val candidates: List<ReplyCandidate>) : QuickReplyModel
    data class Error(val message: String, val diagnosticId: String? = null) : QuickReplyModel
}

class QuickReplyPresenter {
    fun render(state: OverlayState): QuickReplyModel = when (state) {
        is OverlayState.Loading -> QuickReplyModel.Loading("正在生成回复…")
        is OverlayState.Results -> QuickReplyModel.Candidates(state.candidates)
        is OverlayState.Error -> QuickReplyModel.Error(state.message, state.diagnosticId)
        OverlayState.Idle, is OverlayState.Preview -> QuickReplyModel.Hidden
    }
}

class QuickCandidateHandler(
    private val copy: (ReplyCandidate) -> Boolean,
    private val insert: (ReplyCandidate) -> Unit,
    private val onCopied: () -> Unit,
    private val onCopyFailed: () -> Unit,
) {
    fun select(candidate: ReplyCandidate, sourcePackage: String) {
        if (!copy(candidate)) {
            onCopyFailed()
            return
        }
        onCopied()
        if (sourcePackage in INSERTABLE_PACKAGES) insert(candidate)
    }

    private companion object {
        val INSERTABLE_PACKAGES = setOf(
            "com.tencent.mobileqq",
            "com.tencent.tim",
            "com.tencent.qqlite",
        )
    }
}

data class QuickReplyCallbacks(
    val onCandidate: (ReplyCandidate) -> Unit,
    val onGenerate: (String) -> Unit,
    val onRetry: (String) -> Unit,
    val onCopyDiagnostics: (String) -> Unit,
)

class QuickReplyViewFactory(private val context: Context) {
    fun create(callbacks: QuickReplyCallbacks): QuickReplyViews {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
            background = rounded(Color.WHITE, 10f)
            elevation = 10.dp.toFloat()
        }
        val input = EditText(context).apply {
            hint = "告诉 AI 你想怎么回…"
            textSize = 13f
            minLines = 1
            maxLines = 3
            setPadding(10.dp, 6.dp, 10.dp, 6.dp)
        }
        val generate = Button(context).apply {
            text = "生成"
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(10.dp, 0, 10.dp, 0)
            setOnClickListener { callbacks.onGenerate(input.text.toString()) }
        }
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(generate, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = 4.dp })
        }
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(inputRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        return QuickReplyViews(root, input, generate, content, callbacks)
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
}

class QuickReplyViews(
    val root: LinearLayout,
    private val input: EditText,
    private val generate: Button,
    private val content: LinearLayout,
    private val callbacks: QuickReplyCallbacks,
) {
    fun render(model: QuickReplyModel) {
        content.removeAllViews()
        val loading = model is QuickReplyModel.Loading
        generate.isEnabled = !loading
        input.isEnabled = !loading
        when (model) {
            QuickReplyModel.Hidden -> Unit
            is QuickReplyModel.Loading -> content.addView(status(model.message))
            is QuickReplyModel.Error -> {
                content.addView(status(model.message))
                content.addView(Button(root.context).apply {
                    text = "重试"
                    setOnClickListener { callbacks.onRetry(input.text.toString()) }
                })
                model.diagnosticId?.let { diagnosticId ->
                    content.addView(Button(root.context).apply {
                        text = "复制诊断信息"
                        setOnClickListener { callbacks.onCopyDiagnostics(diagnosticId) }
                    })
                }
            }
            is QuickReplyModel.Candidates -> model.candidates.forEach { addCandidate(it) }
        }
    }

    fun showCopyFailure() {
        content.addView(status("复制失败"), 0)
    }

    fun clearFocus() = input.clearFocus()

    private fun status(message: String) = TextView(root.context).apply {
        text = message
        textSize = 12f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(47, 58, 53))
        setPadding(8.dp, 8.dp, 8.dp, 8.dp)
    }

    private fun addCandidate(candidate: ReplyCandidate) {
        content.addView(TextView(root.context).apply {
            text = candidate.text
            textSize = 13f
            maxLines = 4
            setTextColor(Color.rgb(26, 31, 29))
            isClickable = true
            isFocusable = true
            setPadding(10.dp, 8.dp, 10.dp, 8.dp)
            val surface = GradientDrawable().apply {
                setColor(Color.rgb(246, 248, 247))
                setStroke(1.dp, Color.rgb(214, 221, 218))
                cornerRadius = 8.dp.toFloat()
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.rgb(187, 218, 205)),
                surface,
                surface,
            )
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                callbacks.onCandidate(candidate)
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = 4.dp })
    }

    private val Int.dp: Int get() = (this * root.resources.displayMetrics.density).toInt()
}
