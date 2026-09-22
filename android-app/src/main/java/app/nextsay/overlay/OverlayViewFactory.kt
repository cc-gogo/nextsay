package app.nextsay.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import app.nextsay.context.ChatContext
import android.os.SystemClock

class OverlayViewFactory(private val context: Context) {
    fun trigger(): ImageButton = ImageButton(context).apply {
        contentDescription = "运行 NextSay"
        setImageResource(android.R.drawable.ic_menu_edit)
        setColorFilter(Color.WHITE)
        val surface = rounded(Color.rgb(23, 107, 77), 32f)
        background = RippleDrawable(
            ColorStateList.valueOf(Color.rgb(146, 211, 183)),
            surface,
            rounded(Color.WHITE, 32f),
        )
        elevation = 8.dp.toFloat()
    }

    fun copyFeedback(): TextView = TextView(context).apply {
        text = "已复制"
        textSize = 12f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(10.dp, 5.dp, 10.dp, 5.dp)
        background = rounded(Color.argb(220, 32, 36, 34), 12f)
        elevation = 6.dp.toFloat()
    }

    fun panel(callbacks: PanelCallbacks): PanelViews {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 14.dp, 20.dp, 18.dp)
            background = rounded(Color.WHITE, 8f)
        }
        val title = TextView(context).apply {
            text = "下一句"
            textSize = 19f
            setTextColor(Color.rgb(26, 31, 29))
        }
        val titleRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageButton(context).apply {
                contentDescription = "刷新上下文"
                setImageResource(android.R.drawable.ic_popup_sync)
                background = selectableBackground()
                setOnClickListener { callbacks.onRefresh() }
            }, LinearLayout.LayoutParams(48.dp, 48.dp))
        }
        val status = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.rgb(83, 94, 89))
            setPadding(0, 6.dp, 0, 8.dp)
        }
        val preview = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.rgb(35, 43, 40))
            setTextIsSelectable(true)
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
            background = rounded(Color.rgb(240, 244, 242), 8f)
        }
        val instruction = EditText(context).apply {
            hint = "补充要求，例如：委婉说明会晚一天"
            textSize = 14f
            maxLines = 3
        }
        val relationship = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                RELATIONSHIP_LABELS,
            )
        }
        val primaryActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val dismiss = Button(context).apply {
            text = "关闭"
            setOnClickListener { callbacks.onDismiss() }
        }
        val generate = Button(context).apply {
            text = "生成回复"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(23, 107, 77))
            setOnClickListener {
                callbacks.onGenerate(
                    instruction.text.toString(),
                    RELATIONSHIP_VALUES[relationship.selectedItemPosition],
                )
            }
        }
        primaryActions.addView(dismiss)
        primaryActions.addView(generate)

        val candidates = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val retry = Button(context).apply {
            text = "重试"
            visibility = View.GONE
            setOnClickListener { callbacks.onRetry() }
        }
        val copy = Button(context).apply {
            text = "复制候选"
            visibility = View.GONE
        }
        val copyDiagnostics = Button(context).apply {
            text = "复制诊断信息"
            visibility = View.GONE
        }

        content.addView(titleRow, matchWrap())
        content.addView(status)
        content.addView(preview, matchWrap())
        content.addView(instruction, matchWrap())
        content.addView(relationship, matchWrap())
        content.addView(primaryActions, matchWrap())
        content.addView(candidates, matchWrap())
        content.addView(retry, matchWrap())
        content.addView(copy, matchWrap())
        content.addView(copyDiagnostics, matchWrap())

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }
        return PanelViews(
            scroll,
            status,
            preview,
            instruction,
            relationship,
            generate,
            candidates,
            retry,
            copy,
            copyDiagnostics,
            callbacks,
        )
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
    }

    private fun selectableBackground() = RippleDrawable(
        ColorStateList.valueOf(Color.rgb(187, 218, 205)),
        null,
        rounded(Color.WHITE, 24f),
    )

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        val RELATIONSHIP_LABELS = listOf("未指定关系", "领导", "老师", "客户", "同事", "朋友", "家人")
        val RELATIONSHIP_VALUES = listOf("unspecified", "manager", "teacher", "customer", "colleague", "friend", "family")
    }
}

data class PanelCallbacks(
    val onRefresh: () -> Unit,
    val onGenerate: (String, String) -> Unit,
    val onRetry: () -> Unit,
    val onCandidate: (ReplyCandidate) -> Unit,
    val onDismiss: () -> Unit,
    val onCopyDiagnostics: (String) -> Unit,
)

class PanelViews(
    val root: View,
    private val status: TextView,
    private val preview: TextView,
    private val instruction: EditText,
    private val relationship: Spinner,
    private val generate: Button,
    private val candidates: LinearLayout,
    private val retry: Button,
    private val copy: Button,
    private val copyDiagnostics: Button,
    private val callbacks: PanelCallbacks,
) {
    fun render(state: OverlayState) {
        if (state == OverlayState.Idle) return
        val context = when (state) {
            is OverlayState.Preview -> state.context
            is OverlayState.Loading -> state.context
            is OverlayState.Results -> state.context
            is OverlayState.Error -> state.context
            OverlayState.Idle -> return
        }
        preview.text = context.asPreview()
        candidates.removeAllViews()
        copy.visibility = View.GONE
        copyDiagnostics.visibility = View.GONE
        retry.visibility = if (state is OverlayState.Error) View.VISIBLE else View.GONE
        generate.isEnabled = state !is OverlayState.Loading
        instruction.isEnabled = state !is OverlayState.Loading
        relationship.isEnabled = state !is OverlayState.Loading
        status.text = when (state) {
            is OverlayState.Preview -> "请检查下面将要上传的可见上下文"
            is OverlayState.Loading -> "正在生成，请稍候"
            is OverlayState.Results -> "选择一条写入当前输入框"
            is OverlayState.Error -> state.message
            OverlayState.Idle -> ""
        }
        if (state is OverlayState.Results) {
            state.candidates.forEach { candidate -> addCandidate(candidate) }
        }
        if (state is OverlayState.Error && state.diagnosticId != null) {
            copyDiagnostics.visibility = View.VISIBLE
            copyDiagnostics.setOnClickListener {
                callbacks.onCopyDiagnostics(state.diagnosticId)
            }
        }
    }

    fun showCopyFallback(candidate: ReplyCandidate) {
        status.text = "没有找到可写入的输入框，可以手动复制"
        copy.visibility = View.VISIBLE
        copy.setOnClickListener {
            val clipboard = copy.context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("NextSay 回复", candidate.text))
            status.text = "已复制，请返回输入框粘贴"
        }
    }

    private fun addCandidate(candidate: ReplyCandidate) {
        val row = TextView(candidates.context).apply {
            text = candidate.text
            textSize = 15f
            setTextColor(Color.rgb(26, 31, 29))
            isClickable = true
            isFocusable = true
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
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
                if (!isEnabled) return@setOnClickListener
                isEnabled = false
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                callbacks.onCandidate(candidate)
            }
        }
        candidates.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 8.dp
        })
    }

    private fun ChatContext.asPreview(): String = messages.joinToString("\n") {
        val speaker = when (it.role) {
            app.nextsay.context.MessageRole.ME -> "我"
            app.nextsay.context.MessageRole.OTHER -> "对方"
            app.nextsay.context.MessageRole.UNKNOWN -> "未知"
        }
        "$speaker：${it.text}"
    }

    private val Int.dp: Int get() = (this * root.resources.displayMetrics.density).toInt()
}

class OverlayWindow(
    private val service: AccessibilityService,
    callbacks: PanelCallbacks,
    private val onQuickTrigger: () -> Unit,
    private val onAdvancedTrigger: () -> Unit,
    private val onQuickGenerate: (String) -> Unit,
    private val onQuickInsert: (ReplyCandidate) -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val factory = OverlayViewFactory(service)
    private val trigger = factory.trigger()
    private val copyFeedback = factory.copyFeedback()
    private val panel = factory.panel(callbacks)
    private val quickPresenter = QuickReplyPresenter()
    private val quickInteraction = QuickReplyInteraction()
    private val clipboardCopier = ClipboardReplyCopier(service)
    private val quick = QuickReplyViewFactory(service).create(
        QuickReplyCallbacks(
            onCandidate = ::copyQuickCandidate,
            onGenerate = ::generateQuickReply,
            onRetry = ::generateQuickReply,
            onCopyDiagnostics = callbacks.onCopyDiagnostics,
        ),
    )
    private val quickCandidateHandler = QuickCandidateHandler(
        copy = clipboardCopier::copy,
        insert = onQuickInsert,
        onCopied = {
            closeQuick()
            showCopiedFeedback()
        },
        onCopyFailed = quick::showCopyFailure,
    )
    private val positionStore = OverlayPositionStore(service)
    private val gestureController = FloatingTriggerGestureController(
        touchSlop = ViewConfiguration.get(service).scaledTouchSlop.toFloat(),
        longPressMillis = ViewConfiguration.getLongPressTimeout().toLong(),
    )
    private val triggerLayoutParams = triggerParams()
    private var triggerAttached = false
    private var panelAttached = false
    private var quickAttached = false
    private var copyFeedbackAttached = false
    private var quickSourcePackage: String? = null
    private var draggedSinceDown = false
    private val longPressRunnable = Runnable {
        handleGesture(gestureController.onLongPress(SystemClock.uptimeMillis()))
    }

    init {
        trigger.setOnTouchListener { _, event -> handleTriggerTouch(event) }
        quick.root.setOnTouchListener { _, event ->
            if (quickInteraction.shouldDismiss(event.actionMasked)) {
                closeQuick()
                true
            } else {
                false
            }
        }
    }

    val isPanelOpen: Boolean
        get() = panelAttached

    val isAnyContentOpen: Boolean
        get() = panelAttached || quickAttached

    fun setSupportedAppActive(active: Boolean) {
        if (active && !triggerAttached) {
            restoreTriggerPosition()
            windowManager.addView(trigger, triggerLayoutParams)
            triggerAttached = true
        } else if (!active) {
            hideAllContent()
            if (triggerAttached) {
                windowManager.removeView(trigger)
                triggerAttached = false
            }
        }
    }

    fun renderAdvanced(state: OverlayState) {
        if (state == OverlayState.Idle) {
            closePanel()
            return
        }
        closeQuick()
        if (!panelAttached) {
            windowManager.addView(panel.root, panelParams())
            panelAttached = true
        }
        panel.render(state)
    }

    fun renderQuick(state: OverlayState) {
        val model = quickPresenter.render(state)
        if (model == QuickReplyModel.Hidden) {
            closeQuick()
            return
        }
        if (state is OverlayState.Results) quickSourcePackage = state.context.sourcePackage
        showQuickModel(model)
    }

    fun showQuickReading() = showQuickModel(QuickReplyModel.Loading("正在读取最新对话…"))

    fun showQuickError(message: String, diagnosticId: String? = null) =
        showQuickModel(QuickReplyModel.Error(message, diagnosticId))

    private fun showQuickModel(model: QuickReplyModel) {
        closePanel()
        quick.render(model)
        if (!quickAttached) {
            windowManager.addView(quick.root, quickParams())
            quickAttached = true
        } else {
            windowManager.updateViewLayout(quick.root, quickParams())
        }
    }

    fun render(state: OverlayState) = renderAdvanced(state)

    fun showCopyFallback(candidate: ReplyCandidate) = panel.showCopyFallback(candidate)

    fun hidePanel() = closePanel()

    fun hideQuick() = closeQuick()

    fun hideAllContent() {
        closeQuick()
        closePanel()
        closeCopyFeedback()
    }

    fun hidePanelForInsertion() = closePanel()

    fun hideForCapture() {
        hideAllContent()
        trigger.visibility = View.INVISIBLE
    }

    fun restoreAfterCapture() {
        if (triggerAttached) trigger.visibility = View.VISIBLE
    }

    fun setBusy(busy: Boolean) {
        trigger.isEnabled = !busy
        trigger.alpha = if (busy) 0.65f else 1f
    }

    fun dispose() {
        trigger.removeCallbacks(longPressRunnable)
        copyFeedback.removeCallbacks(hideCopyFeedbackRunnable)
        hideAllContent()
        if (triggerAttached) windowManager.removeView(trigger)
        triggerAttached = false
    }

    private fun closePanel() {
        if (panelAttached) {
            windowManager.removeView(panel.root)
        }
        panelAttached = false
    }

    private fun closeQuick() {
        quick.clearFocus()
        service.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(quick.root.windowToken, 0)
        if (quickAttached) windowManager.removeView(quick.root)
        quickAttached = false
    }

    private fun generateQuickReply(text: String) {
        val instruction = quickInteraction.submit(text)
        quick.clearFocus()
        service.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(quick.root.windowToken, 0)
        onQuickGenerate(instruction)
    }

    private val hideCopyFeedbackRunnable = Runnable { closeCopyFeedback() }

    private fun showCopiedFeedback() {
        closeCopyFeedback()
        copyFeedback.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST),
        )
        windowManager.addView(
            copyFeedback,
            copyFeedbackParams(copyFeedback.measuredWidth, copyFeedback.measuredHeight),
        )
        copyFeedbackAttached = true
        copyFeedback.postDelayed(hideCopyFeedbackRunnable, COPY_FEEDBACK_DURATION_MS)
    }

    private fun closeCopyFeedback() {
        copyFeedback.removeCallbacks(hideCopyFeedbackRunnable)
        if (copyFeedbackAttached) windowManager.removeView(copyFeedback)
        copyFeedbackAttached = false
    }

    private fun copyQuickCandidate(candidate: ReplyCandidate) {
        val sourcePackage = quickSourcePackage ?: return
        quickCandidateHandler.select(candidate, sourcePackage)
    }

    private fun handleTriggerTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggedSinceDown = false
                gestureController.onDown(event.rawX, event.rawY, event.eventTime)
                trigger.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> handleGesture(
                gestureController.onMove(event.rawX, event.rawY, event.eventTime),
            )
            MotionEvent.ACTION_UP -> {
                trigger.removeCallbacks(longPressRunnable)
                handleGesture(gestureController.onUp(event.rawX, event.rawY, event.eventTime))
                if (draggedSinceDown) saveTriggerPosition()
            }
            MotionEvent.ACTION_CANCEL -> {
                trigger.removeCallbacks(longPressRunnable)
                gestureController.cancel()
            }
        }
        return true
    }

    private fun handleGesture(action: GestureAction) {
        when (action) {
            GestureAction.None -> Unit
            GestureAction.Click -> {
                trigger.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onQuickTrigger()
            }
            GestureAction.LongPress -> {
                trigger.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onAdvancedTrigger()
            }
            is GestureAction.DragBy -> {
                if (!draggedSinceDown) hideAllContent()
                draggedSinceDown = true
                moveTrigger(action.deltaX, action.deltaY)
            }
        }
    }

    private fun moveTrigger(deltaX: Float, deltaY: Float) {
        val availableWidth = (screenWidth - TRIGGER_SIZE_DP.dp).coerceAtLeast(0)
        val availableHeight = (screenHeight - TRIGGER_SIZE_DP.dp).coerceAtLeast(0)
        triggerLayoutParams.x = (triggerLayoutParams.x + deltaX.toInt()).coerceIn(0, availableWidth)
        triggerLayoutParams.y = (triggerLayoutParams.y + deltaY.toInt()).coerceIn(0, availableHeight)
        if (triggerAttached) windowManager.updateViewLayout(trigger, triggerLayoutParams)
    }

    private fun restoreTriggerPosition() {
        val position = OverlayPosition.fromFractions(
            positionStore.load(),
            (screenWidth - TRIGGER_SIZE_DP.dp).coerceAtLeast(0),
            (screenHeight - TRIGGER_SIZE_DP.dp).coerceAtLeast(0),
        )
        triggerLayoutParams.x = position.x
        triggerLayoutParams.y = position.y
    }

    private fun saveTriggerPosition() {
        positionStore.save(
            OverlayPosition.toFractions(
                triggerLayoutParams.x,
                triggerLayoutParams.y,
                (screenWidth - TRIGGER_SIZE_DP.dp).coerceAtLeast(0),
                (screenHeight - TRIGGER_SIZE_DP.dp).coerceAtLeast(0),
            ),
        )
    }

    private fun triggerParams() = WindowManager.LayoutParams(
        TRIGGER_SIZE_DP.dp,
        TRIGGER_SIZE_DP.dp,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
    }

    private fun quickParams() = WindowManager.LayoutParams(
        minOf(QUICK_WIDTH_DP.dp, (screenWidth - 24.dp).coerceAtLeast(1)),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        val width = minOf(QUICK_WIDTH_DP.dp, (screenWidth - 24.dp).coerceAtLeast(1))
        x = if (triggerLayoutParams.x > screenWidth / 2) {
            triggerLayoutParams.x - width - QUICK_GAP_DP.dp
        } else {
            triggerLayoutParams.x + TRIGGER_SIZE_DP.dp + QUICK_GAP_DP.dp
        }.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        y = triggerLayoutParams.y.coerceIn(0, (screenHeight - QUICK_MIN_VISIBLE_HEIGHT_DP.dp).coerceAtLeast(0))
    }

    private fun copyFeedbackParams(width: Int, height: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
        val position = CopyFeedbackPosition.calculate(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            triggerX = triggerLayoutParams.x,
            triggerY = triggerLayoutParams.y,
            triggerSize = TRIGGER_SIZE_DP.dp,
            feedbackWidth = width,
            feedbackHeight = height,
            gap = COPY_FEEDBACK_GAP_DP.dp,
        )
        x = position.x
        y = position.y
    }

    @Suppress("DEPRECATION")
    private fun panelParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    private val screenWidth: Int get() = service.resources.displayMetrics.widthPixels
    private val screenHeight: Int get() = service.resources.displayMetrics.heightPixels
    private val Int.dp: Int get() = (this * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val TRIGGER_SIZE_DP = 52
        const val QUICK_WIDTH_DP = 280
        const val QUICK_GAP_DP = 8
        const val QUICK_MIN_VISIBLE_HEIGHT_DP = 180
        const val COPY_FEEDBACK_GAP_DP = 8
        const val COPY_FEEDBACK_DURATION_MS = 800L
    }
}
