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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.nextsay.ui.NextSayUi

sealed interface QuickReplyModel {
    data object Hidden : QuickReplyModel
    data class Loading(val message: String) : QuickReplyModel
    data class Waiting(val message: String) : QuickReplyModel
    data class Candidates(val candidates: List<ReplyCandidate>, val notice: String? = null) : QuickReplyModel
    data class Error(val message: String, val diagnosticId: String? = null) : QuickReplyModel
}

object QuickEntryPolicy {
    fun enabled(model: QuickReplyModel, busy: Boolean) = !busy
}

class QuickReplyPresenter {
    fun render(state: OverlayState): QuickReplyModel = when (state) {
        is OverlayState.Loading -> QuickReplyModel.Loading("正在生成回复…")
        is OverlayState.Results -> QuickReplyModel.Candidates(state.candidates, continuationNotice(state.context))
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
    val onEditInstruction: () -> Unit = {},
)

class QuickReplyViewFactory(private val context: Context) {
    fun create(callbacks: QuickReplyCallbacks): QuickReplyViews {
        val ui = NextSayUi(context)
        val root = QuickReplyLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
            background = ui.shape(ui.overlaySurface, stroke = true)
            elevation = 5.dp.toFloat()
        }
        val input = TextView(context).apply {
            text = "补充要求…"
            contentDescription = "编辑补充要求"
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ui.muted)
            setPadding(10.dp, 6.dp, 10.dp, 6.dp)
            minimumHeight = 48.dp
            gravity = Gravity.CENTER_VERTICAL
            background = ui.shape(ui.inset, 12)
            isFocusable = true
            setOnClickListener { callbacks.onEditInstruction() }
        }
        val generate = ui.button("生成新回复", primary = true) {}.apply {
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(10.dp, 0, 10.dp, 0)
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
        root.addView(ScrollView(context).apply { addView(content) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        return QuickReplyViews(root, input, generate, content, callbacks).also { views ->
            generate.setOnClickListener { callbacks.onGenerate(views.instructionText) }
        }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
}

class QuickReplyViews(
    val root: LinearLayout,
    private val input: TextView,
    private val generate: Button,
    private val content: LinearLayout,
    private val callbacks: QuickReplyCallbacks,
) {
    private val ui = NextSayUi(root.context)
    var instructionText: String = ""
        private set
    private var busy = false
    private var loading = false
    private var currentModel: QuickReplyModel = QuickReplyModel.Hidden
    private var loadingStatus: TextView? = null

    fun setMaximumHeight(height: Int) { (root as? QuickReplyLayout)?.setHeightLimit(height) }

    fun setBusy(value: Boolean) {
        busy = value
        updateEntryControls()
    }

    private fun updateEntryControls() {
        generate.isEnabled = QuickEntryPolicy.enabled(currentModel, busy)
        input.isEnabled = QuickEntryPolicy.enabled(currentModel, busy)
        generate.alpha = if (generate.isEnabled) 1f else .5f
        input.alpha = if (input.isEnabled) 1f else .5f
    }

    fun setInstruction(text: String) {
        instructionText = text
        input.text = text.ifEmpty { "补充要求…" }
    }
    fun render(model: QuickReplyModel) {
        currentModel = model
        if (model is QuickReplyModel.Loading && content.childCount > 0) {
            loading = true
            updateEntryControls()
            for (index in 0 until content.childCount) content.getChildAt(index).isEnabled = false
            val label = loadingStatus ?: status(model.message).also { loadingStatus = it; content.addView(it, 0) }
            label.text = model.message + "（旧候选暂不可复制）"
            return
        }
        // One attached root, updated in one UI traversal; no WindowManager mutation.
        val oldHeight = content.height
        if (oldHeight > 0) content.minimumHeight = oldHeight
        loadingStatus = null
        content.removeAllViews()
        loading = model is QuickReplyModel.Loading
        updateEntryControls()
        when (model) {
            QuickReplyModel.Hidden -> Unit
            is QuickReplyModel.Loading -> content.addView(status(model.message))
            is QuickReplyModel.Waiting -> content.addView(status(model.message))
            is QuickReplyModel.Error -> {
                content.addView(status(model.message, ui.danger))
                content.addView(ui.button("重试") { callbacks.onRetry(instructionText) })
                model.diagnosticId?.let { diagnosticId ->
                    content.addView(ui.button("复制诊断信息") { callbacks.onCopyDiagnostics(diagnosticId) })
                }
            }
            is QuickReplyModel.Candidates -> {
                model.notice?.let { content.addView(status(it)) }
                model.candidates.forEach { addCandidate(it) }
            }
        }
    }

    fun showCopyFailure() {
        content.addView(status("复制失败"), 0)
    }

    private fun status(message: String, tint: Int = ui.muted) = TextView(root.context).apply {
        text = message
        textSize = 12f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(tint)
        setPadding(8.dp, 8.dp, 8.dp, 8.dp)
    }

    private fun addCandidate(candidate: ReplyCandidate) {
        content.addView(TextView(root.context).apply {
            text = candidate.text
            textSize = 13f
            maxLines = 4
            setTextColor(ui.ink)
            isClickable = true
            isFocusable = true
            setPadding(10.dp, 8.dp, 10.dp, 8.dp)
            val surface = GradientDrawable().apply {
                setColor(ui.inset)
                setStroke(1.dp, ui.line)
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

internal class QuickReplyLayout(context: Context) : LinearLayout(context) {
    private var heightLimit = Int.MAX_VALUE
    fun setHeightLimit(height: Int) {
        val bounded = height.coerceAtLeast(1)
        if (bounded != heightLimit) { heightLimit = bounded; requestLayout() }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limit = minOf(heightLimit, (resources.displayMetrics.heightPixels * .70f).toInt().coerceAtLeast(1))
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val size = View.MeasureSpec.getSize(heightMeasureSpec)
        val bounded = if (mode == View.MeasureSpec.UNSPECIFIED) limit else minOf(size, limit)
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(bounded,
            if (mode == View.MeasureSpec.EXACTLY) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST))
    }
}
