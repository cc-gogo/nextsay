package app.nextsay.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
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
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import app.nextsay.ui.NextSayUi
import app.nextsay.ui.RelationshipChoices
import android.widget.TextView
import app.nextsay.context.ChatContext
import android.os.SystemClock
import android.os.Build
import android.provider.Settings
import app.nextsay.R

class OverlayViewFactory(private val context: Context) {
    fun trigger(): ImageButton = ImageButton(context).apply {
        val ui = NextSayUi(context)
        contentDescription = "运行 NextSay"
        setImageResource(R.drawable.nextsay_trigger)
        setColorFilter(ui.onAccent)
        setPadding(12.dp, 12.dp, 12.dp, 12.dp)
        val surface = rounded(ui.triggerFill, 26f).apply { setStroke(1.dp, Color.rgb(177, 210, 191)) }
        background = RippleDrawable(
            ColorStateList.valueOf(ui.line),
            surface,
            rounded(Color.WHITE, 32f),
        )
        elevation = 4.dp.toFloat()
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
        val ui = NextSayUi(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 14.dp, 20.dp, 18.dp)
            background = ui.shape(ui.overlaySurface, stroke = true)
        }
        val title = TextView(context).apply {
            text = "下一句"
            textSize = 19f
            setTextColor(ui.ink)
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
            setTextColor(ui.muted)
            setPadding(0, 6.dp, 0, 8.dp)
        }
        val preview = TextView(context).apply {
            textSize = 14f
            setTextColor(ui.ink)
            setTextIsSelectable(true)
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
            background = ui.shape(ui.inset, 12)
        }
        val instruction = ui.field("", "例如：委婉说明会晚一天", 2)
        val relationship = RelationshipChoices(context)
        val association = ui.text("关系和补充资料来自对象管理", 12f, tint = ui.muted)
        val manageContact = ui.button("选择聊天对象") {}
        val primaryActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val dismiss = ui.button("关闭") { callbacks.onDismiss() }
        val generate = ui.button("生成回复", primary = true) {}
        primaryActions.addView(dismiss)
        primaryActions.addView(generate)

        val candidates = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val retry = ui.button("重试") { generate.performClick() }.apply {
            visibility = View.GONE
        }
        val copy = ui.button("复制候选") {}.apply {
            visibility = View.GONE
        }
        val copyDiagnostics = ui.button("复制诊断信息") {}.apply {
            visibility = View.GONE
        }

        content.addView(titleRow, matchWrap())
        content.addView(status)
        content.addView(preview, matchWrap())
        content.addView(ui.text("本轮补充要求", 14f, true))
        content.addView(instruction, matchWrap())
        content.addView(ui.text("对象关系", 14f, true))
        content.addView(association)
        content.addView(relationship, matchWrap())
        content.addView(manageContact, matchWrap())
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
            association,
            generate,
            candidates,
            retry,
            copy,
            copyDiagnostics,
            manageContact,
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
        val RELATIONSHIP_LABELS = listOf("未指定关系", "领导", "老师", "客户", "同事", "朋友", "家人", "恋人")
        val RELATIONSHIP_VALUES = listOf("unspecified", "manager", "teacher", "customer", "colleague", "friend", "family", "lover")
    }
}

data class PanelCallbacks(
    val onRefresh: () -> Unit,
    val onGenerate: (String, String) -> Unit,
    val onRetry: () -> Unit,
    val onCandidate: (ReplyCandidate) -> Unit,
    val onDismiss: () -> Unit,
    val onCopyDiagnostics: (String) -> Unit,
    val onManageContact: (String?) -> Unit = {},
)

class PanelViews(
    val root: View,
    private val status: TextView,
    private val preview: TextView,
    private val instruction: EditText,
    private val relationship: RelationshipChoices,
    private val association: TextView,
    private val generate: Button,
    private val candidates: LinearLayout,
    private val retry: Button,
    private val copy: Button,
    private val copyDiagnostics: Button,
    private val manageContact: Button,
    private val callbacks: PanelCallbacks,
) {
    private val ui = NextSayUi(root.context)
    private var relationshipContextKey: Pair<String, String>? = null
    private var savedRelationship: String? = null
    private var currentState: OverlayState = OverlayState.Idle
    private var captureBusy = false
    init {
        relationship.visibility = View.GONE
        relationship.isEnabled = false
        generate.setOnClickListener { callbacks.onGenerate(instruction.text.toString(), savedRelationship ?: "unspecified") }
    }
    fun setBusy(busy: Boolean) {
        captureBusy = busy
        updateControls()
    }
    private fun updateControls() {
        val enabled = !captureBusy && currentState !is OverlayState.Loading
        generate.isEnabled = enabled
        instruction.isEnabled = enabled
        relationship.isEnabled = false
        manageContact.isEnabled = enabled
        retry.isEnabled = enabled
        copy.isEnabled = enabled
        for (i in 0 until candidates.childCount) candidates.getChildAt(i).apply {
            isEnabled = enabled && currentState is OverlayState.Results
            alpha = if (isEnabled) 1f else .5f
        }
    }
    fun render(state: OverlayState) {
        currentState = state
        if (state == OverlayState.Idle) {
            relationshipContextKey = null
            savedRelationship = null
            return
        }
        val context = when (state) {
            is OverlayState.Preview -> state.context
            is OverlayState.Loading -> state.context
            is OverlayState.Results -> state.context
            is OverlayState.Error -> state.context
            OverlayState.Idle -> return
        }
        val key = context.sourcePackage to (context.contactId ?: context.replyRound?.title.orEmpty())
        val saved = context.generationExtras?.relationship ?: "unspecified"
        val managed = context.contactId != null && context.generationExtras != null
        association.text = if (managed)
            "已带入对象资料 · ${RelationshipChoices.label(saved)}" + if (saved == "lover")
                " · 恋人${app.nextsay.provider.LoverReplyModes.label(context.generationExtras.replyMode)}风格" else ""
        else "未找到当前对象的资料；可选择已有对象，或仅参考当前画面生成"
        manageContact.text = if (managed) "编辑对象资料" else "选择聊天对象"
        manageContact.setOnClickListener { callbacks.onManageContact(context.contactId) }
        if (key != relationshipContextKey || saved != savedRelationship) {
            relationship.select(saved)
            relationshipContextKey = key
            savedRelationship = saved
        }
        preview.text = context.asPreview()
        val retainOldCandidates = (state is OverlayState.Preview || state is OverlayState.Loading) && candidates.childCount > 0
        if (retainOldCandidates) {
            for (i in 0 until candidates.childCount) candidates.getChildAt(i).apply { isEnabled = false; alpha = .5f }
        } else candidates.removeAllViews()
        copy.visibility = View.GONE
        copyDiagnostics.visibility = View.GONE
        retry.visibility = if (state is OverlayState.Error) View.VISIBLE else View.GONE
        status.text = OverlayStatusPresenter().render(state)
        if (state is OverlayState.Results) {
            state.candidates.forEach { candidate -> addCandidate(candidate) }
        }
        if (state is OverlayState.Error && state.diagnosticId != null) {
            copyDiagnostics.visibility = View.VISIBLE
            copyDiagnostics.setOnClickListener {
                callbacks.onCopyDiagnostics(state.diagnosticId)
            }
        }
        updateControls()
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
            setTextColor(ui.ink)
            isClickable = true
            isFocusable = true
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
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
    private val onQuickDismiss: () -> Unit,
    private val onAdvancedTrigger: () -> Unit,
    private val onQuickGenerate: (String) -> Unit,
    private val onQuickInsert: (ReplyCandidate) -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    // MIUI treats accessibility overlays specially while an IME is opening.
    // When the user grants the ordinary overlay permission, use that layer so
    // the trigger is independent from the accessibility/IME transition.
    private var overlayWindowType = preferredWindowType()
    init {
        Log.i("NextSayOverlay", "window type=$overlayWindowType appOverlay=${overlayWindowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY}")
    }

    private fun preferredWindowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        Settings.canDrawOverlays(service)) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    }
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
            onEditInstruction = ::openInstructionEditor,
        ),
    )
    private val instructionEditor = QuickInstructionEditor(
        service,
        onComplete = { text ->
            if (instructionEditorAttached) quick.setInstruction(text)
            closeInstructionEditor()
        },
        onCancel = ::closeInstructionEditor,
    )
    private val quickCandidateHandler = QuickCandidateHandler(
        copy = clipboardCopier::copy,
        insert = onQuickInsert,
        onCopied = {
            showCopiedFeedback()
        },
        onCopyFailed = quick::showCopyFailure,
    )
    private val positionStore = OverlayPositionStore(service)
    private val gestureController = FloatingTriggerGestureController(
        touchSlop = ViewConfiguration.get(service).scaledTouchSlop.toFloat(),
        longPressMillis = ViewConfiguration.getLongPressTimeout().toLong(),
    )
    private var triggerLayoutParams = triggerParams()
    private var triggerAttached = false
    private var panelAttached = false
    private var quickAttached = false
    private var instructionEditorAttached = false
    private var copyFeedbackAttached = false
    private var hiddenForCapture = false
    private var quickSourcePackage: String? = null
    private var quickSuppressed = false
    private var cachedQuick: QuickReplyModel = QuickReplyModel.Waiting("等待新消息；可点生成新回复")
    private var menu: View? = null
    var onEditorVisibilityChanged: (Boolean) -> Unit = {}
    val isEditing: Boolean get() = instructionEditorAttached || menu != null || panelAttached
    private var draggedSinceDown = false
    private val floatingTop: Int get() = (screenHeight * .09f).toInt() + 12.dp
    // Reserve the bottom input/keyboard entry area, including window/status-bar offsets.
    private val floatingBottom: Int get() = (screenHeight * .84f).toInt()
    private val longPressRunnable = Runnable {
        handleGesture(gestureController.onLongPress(SystemClock.uptimeMillis()))
    }

    init {
        trigger.setOnTouchListener { _, event -> handleTriggerTouch(event) }
        quick.root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) constrainQuickPosition()
        }
    }

    val isPanelOpen: Boolean
        get() = panelAttached

    val isAnyContentOpen: Boolean
        get() = panelAttached || quickWindowAttached() || instructionEditorAttached || menu != null

    /**
     * WindowManager can remove an accessibility overlay without sending a
     * callback (notably during MIUI IME transitions). The view attachment is
     * the source of truth; the flag is only an optimization for old ROMs.
     */
    val isQuickOpen: Boolean get() = quickWindowAttached()

    private fun quickWindowAttached(): Boolean = quickAttached &&
        (quick.root.isAttachedToWindow || quick.root.parent != null)

    val quickInstructionText: String get() = quick.instructionText

    fun setQuickInstruction(text: String) {
        quick.setInstruction(text)
    }

    fun setSupportedAppActive(active: Boolean) {
        Log.d("NextSayOverlay", "setSupportedAppActive active=$active attached=$triggerAttached viewAttached=${trigger.isAttachedToWindow}")
        if (active) {
            val preferredType = preferredWindowType()
            if (preferredType != overlayWindowType) {
                Log.i("NextSayOverlay", "switching window type $overlayWindowType -> $preferredType")
                hideAllContent()
                if (triggerAttached) windowManager.removeViewImmediate(trigger)
                triggerAttached = false
                overlayWindowType = preferredType
                triggerLayoutParams = triggerParams()
            }
            // Some ROMs can remove an accessibility overlay without notifying
            // the service. The boolean alone then becomes stale and prevents
            // the trigger from ever being added again.
            if (!trigger.isAttachedToWindow) {
                // WindowManager may remove a view without notifying us. The
                // actual attachment is authoritative; never add an attached
                // view a second time, even if the boolean is stale.
                triggerAttached = false
                restoreTriggerPosition()
                try {
                    windowManager.addView(trigger, triggerLayoutParams)
                    triggerAttached = true
                    Log.d("NextSayOverlay", "trigger attached type=$overlayWindowType x=${triggerLayoutParams.x} y=${triggerLayoutParams.y}")
                } catch (error: RuntimeException) {
                    Log.e("NextSayOverlay", "add trigger failed", error)
                }
            } else triggerAttached = true
        } else if (!active) {
            hideAllContent()
            if (triggerAttached || trigger.isAttachedToWindow) {
                Log.d("NextSayOverlay", "removing trigger because supported app became inactive")
                windowManager.removeViewImmediate(trigger)
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
        panel.root.visibility = if (hiddenForCapture) View.INVISIBLE else View.VISIBLE
    }

    fun renderQuick(state: OverlayState) {
        // Preview is an internal context transition, not a visibility instruction.
        if (state is OverlayState.Preview) return
        val rendered = quickPresenter.render(state)
        val model = if (state is OverlayState.Results && rendered is QuickReplyModel.Candidates) rendered.copy(
            notice = listOfNotNull(state.context.replyRound?.title?.let { "$it · 当前候选${if (state.context.contactId == null) "（仅当前画面，未关联记忆）" else ""}" }, rendered.notice).joinToString("\n").ifBlank { null },
        ) else rendered
        if (model == QuickReplyModel.Hidden) {
            closeQuick()
            return
        }
        cachedQuick = model
        if (state is OverlayState.Results) quickSourcePackage = state.context.sourcePackage
        if (quickSuppressed) {
            if (state is OverlayState.Results) trigger.setImageResource(R.drawable.nextsay_trigger_ready)
            return
        }
        showQuickModel(model)
    }

    fun showQuickReading() {
        quickSuppressed = false
        showQuickModel(QuickReplyModel.Loading("正在读取最新对话…（原候选为上轮）"))
    }
    fun showCachedQuick() {
        quickSuppressed = false
        trigger.setImageResource(R.drawable.nextsay_trigger)
        showQuickModel(cachedQuick)
    }
    fun suppressQuick() { quickSuppressed = true; closeQuick() }
    fun clearCachedQuick() {
        cachedQuick = QuickReplyModel.Waiting("等待新消息；可点生成新回复")
        quickSourcePackage = null
        trigger.setImageResource(R.drawable.nextsay_trigger)
        if (quickWindowAttached()) quick.render(cachedQuick)
    }
    fun showGenerationCancelled() {
        cachedQuick = QuickReplyModel.Waiting("已暂停生成；可以手动生成新回复")
        quickSourcePackage = null
        if (quickWindowAttached()) quick.render(cachedQuick)
    }
    fun resumeQuickPresentation() { if (quickWindowAttached() && !isEditing) quick.render(cachedQuick) }

    fun showAutomaticStatus(message: String) {
        // Background observation is not a generation result. Never destroy a
        // candidate, in-flight request or retryable error to show a polling status.
        if (cachedQuick !is QuickReplyModel.Waiting && cachedQuick != QuickReplyModel.Hidden) return
        if (cachedQuick == QuickReplyModel.Waiting(message)) return
        // Keep background confirmation separate from the interactive entry model.
        // Replacing Loading here disabled the manual generate button.
        cachedQuick = QuickReplyModel.Waiting(message)
        if (quickWindowAttached() && !isEditing) quick.render(cachedQuick)
    }

    fun showQuickError(message: String, diagnosticId: String? = null) {
        cachedQuick = QuickReplyModel.Error(message, diagnosticId)
        showQuickModel(cachedQuick)
    }

    private fun showQuickModel(model: QuickReplyModel) {
        if (isEditing) return
        closePanel()
        quick.render(model)
        quick.root.visibility = if (hiddenForCapture) View.INVISIBLE else View.VISIBLE
        val actuallyAttached = quick.root.isAttachedToWindow || quick.root.parent != null
        if (!actuallyAttached) {
            // A stale flag must never prevent re-attaching a window removed by
            // the system. Reset it before the add so all dependent state sees
            // the same truth even when WindowManager rejects the operation.
            quickAttached = false
            try {
                windowManager.addView(quick.root, quickParams())
                quickAttached = true
            } catch (error: RuntimeException) {
                Log.e("NextSayOverlay", "add quick window failed", error)
                quickAttached = quick.root.isAttachedToWindow || quick.root.parent != null
                return
            }
            anchorQuickToTrigger()
        } else {
            quickAttached = true
            constrainQuickPosition()
        }
    }

    fun render(state: OverlayState) = renderAdvanced(state)

    fun showCopyFallback(candidate: ReplyCandidate) = panel.showCopyFallback(candidate)

    fun hidePanel() = closePanel()

    fun hideQuick() = closeQuick()

    fun hideAllContent() {
        closeMenu()
        closeQuick()
        closePanel()
        closeCopyFeedback()
    }

    fun hidePanelForInsertion() = closePanel()

    fun hideForCapture() {
        closeInstructionEditor()
        hiddenForCapture = true
        closeCopyFeedback()
        if (quickWindowAttached()) quick.root.visibility = View.INVISIBLE
        if (panelAttached) panel.root.visibility = View.INVISIBLE
        trigger.visibility = View.INVISIBLE
    }

    fun restoreAfterCapture() {
        hiddenForCapture = false
        if (triggerAttached) trigger.visibility = View.VISIBLE
        if (quickWindowAttached()) quick.root.visibility = View.VISIBLE
        if (panelAttached) panel.root.visibility = View.VISIBLE
    }

    fun setBusy(busy: Boolean) {
        quick.setBusy(busy)
        panel.setBusy(busy)
        // Keep the trigger usable so the user can collapse a running request.
        trigger.alpha = if (busy) 0.65f else 1f
    }

    fun dispose() {
        trigger.removeCallbacks(longPressRunnable)
        copyFeedback.removeCallbacks(hideCopyFeedbackRunnable)
        hideAllContent()
        if (triggerAttached) windowManager.removeView(trigger)
        triggerAttached = false
    }

    fun showMenu(title: String, actions: List<Pair<String, () -> Unit>>) {
        closeMenu()
        closeInstructionEditor()
        val ui = NextSayUi(service)
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            background = ui.shape(ui.overlaySurface, stroke = true)
            addView(ui.text(title, 18f, true))
            actions.forEach { (label, action) ->
                ui.add(this, ui.button(label) { closeMenu(); action() }, 10)
            }
            ui.add(this, ui.button("返回聊天") { closeMenu() }, 10)
        }
        val view = ScrollView(service).apply { addView(content) }
        windowManager.addView(view, instructionEditorParams())
        menu = view
        onEditorVisibilityChanged(true)
    }
    private fun closeMenu() {
        val current = menu ?: return
        windowManager.removeView(current)
        menu = null
        onEditorVisibilityChanged(false)
    }
    /** Screen-space exclusion, without changing any window visibility/focus. */
    fun captureExclusions(): List<Rect> = listOfNotNull(
        trigger.takeIf { triggerAttached && trigger.isAttachedToWindow }, quick.root.takeIf { quickWindowAttached() },
        panel.root.takeIf { panelAttached }, instructionEditor.root.takeIf { instructionEditorAttached },
        copyFeedback.takeIf { copyFeedbackAttached }, menu,
    ).mapNotNull { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (view.width <= 0 || view.height <= 0) null else Rect(location[0] - 4.dp, location[1] - 4.dp, location[0] + view.width + 4.dp, location[1] + view.height + 4.dp)
    }

    private fun closePanel() {
        if (panelAttached) {
            windowManager.removeView(panel.root)
        }
        panelAttached = false
        panel.render(OverlayState.Idle)
    }

    private fun closeQuick() {
        closeInstructionEditor()
        val attached = quick.root.isAttachedToWindow || quick.root.parent != null
        if (quickAttached || attached) {
            try {
                // Immediate removal avoids a stale touchable window surviving
                // the next click while MIUI is animating the IME.
                windowManager.removeViewImmediate(quick.root)
            } catch (error: RuntimeException) {
                Log.w("NextSayOverlay", "remove quick window failed", error)
            }
        }
        quickAttached = false
    }

    private fun generateQuickReply(text: String) {
        closeInstructionEditor()
        val instruction = quickInteraction.submit(text)
        onQuickGenerate(instruction)
    }

    private fun openInstructionEditor() {
        if (!quickWindowAttached() || hiddenForCapture || instructionEditorAttached) return
        windowManager.addView(instructionEditor.root, instructionEditorParams())
        instructionEditorAttached = true
        instructionEditor.begin(quick.instructionText)
        onEditorVisibilityChanged(true)
    }

    private fun closeInstructionEditor() {
        val wasAttached = instructionEditorAttached
        instructionEditor.release()
        if (instructionEditorAttached) windowManager.removeView(instructionEditor.root)
        instructionEditorAttached = false
        if (wasAttached) onEditorVisibilityChanged(false)
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
                if (panelAttached || instructionEditorAttached || menu != null) {
                    hideAllContent()
                    onQuickDismiss()
                } else {
                    onQuickTrigger()
                }
            }
            GestureAction.LongPress -> {
                trigger.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onAdvancedTrigger()
            }
            is GestureAction.DragBy -> {
                draggedSinceDown = true
                moveTrigger(action.deltaX, action.deltaY)
            }
        }
    }

    private fun moveTrigger(deltaX: Float, deltaY: Float) {
        val oldX = triggerLayoutParams.x
        val oldY = triggerLayoutParams.y
        val availableWidth = (screenWidth - TRIGGER_SIZE_DP.dp).coerceAtLeast(0)
        val availableHeight = (screenHeight - TRIGGER_SIZE_DP.dp).coerceAtLeast(0)
        triggerLayoutParams.x = (triggerLayoutParams.x + deltaX.toInt()).coerceIn(0, availableWidth)
        triggerLayoutParams.y = (triggerLayoutParams.y + deltaY.toInt()).coerceIn(minOf(floatingTop, availableHeight), availableHeight)
        if (triggerAttached) windowManager.updateViewLayout(trigger, triggerLayoutParams)
        if (quickWindowAttached()) {
            val params = quick.root.layoutParams as WindowManager.LayoutParams
            params.x += triggerLayoutParams.x - oldX
            params.y += triggerLayoutParams.y - oldY
            constrainQuickPosition(updateEvenIfUnchanged = true)
        }
    }

    private fun constrainQuickPosition(updateEvenIfUnchanged: Boolean = false) {
        if (!quickWindowAttached()) return
        val params = quick.root.layoutParams as WindowManager.LayoutParams
        val oldX = params.x
        val oldY = params.y
        params.x = params.x.coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
        val naturalLimit = (screenHeight * .70f).toInt().coerceAtLeast(1)
        val height = quick.root.height.takeIf { it > 0 } ?: QUICK_MIN_VISIBLE_HEIGHT_DP.dp
        val horizontalOverlap = params.x < triggerLayoutParams.x + TRIGGER_SIZE_DP.dp &&
            params.x + params.width > triggerLayoutParams.x
        if (horizontalOverlap) {
            val belowTop = maxOf(floatingTop, triggerLayoutParams.y + TRIGGER_SIZE_DP.dp + QUICK_GAP_DP.dp)
            val aboveBottom = minOf(floatingBottom, triggerLayoutParams.y - QUICK_GAP_DP.dp)
            val belowSpace = (floatingBottom - belowTop).coerceAtLeast(0)
            val aboveSpace = (aboveBottom - floatingTop).coerceAtLeast(0)
            val limit = minOf(naturalLimit, maxOf(aboveSpace, belowSpace)).coerceAtLeast(1)
            quick.setMaximumHeight(limit)
            params.y = if (belowSpace >= aboveSpace) belowTop else aboveBottom - minOf(height, limit)
        } else {
            quick.setMaximumHeight(naturalLimit)
            params.y = params.y.coerceIn(floatingTop, maxOf(floatingTop, floatingBottom - minOf(height, naturalLimit)))
        }
        if (updateEvenIfUnchanged || oldX != params.x || oldY != params.y) windowManager.updateViewLayout(quick.root, params)
    }

    private fun anchorQuickToTrigger() {
        if (!quickWindowAttached()) return
        val params = quick.root.layoutParams as WindowManager.LayoutParams
        val width = params.width.takeIf { it > 0 } ?: minOf(QUICK_WIDTH_DP.dp, (screenWidth - 24.dp).coerceAtLeast(1))
        val height = quick.root.height.takeIf { it > 0 } ?: QUICK_MIN_VISIBLE_HEIGHT_DP.dp
        params.x = if (triggerLayoutParams.x > screenWidth / 2) {
            triggerLayoutParams.x - width - QUICK_GAP_DP.dp
        } else {
            triggerLayoutParams.x + TRIGGER_SIZE_DP.dp + QUICK_GAP_DP.dp
        }.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val limit = (screenHeight * .70f).toInt().coerceAtLeast(1)
        quick.setMaximumHeight(limit)
        params.y = (triggerLayoutParams.y - QUICK_VERTICAL_OFFSET_DP.dp).coerceIn(
            floatingTop,
            maxOf(floatingTop, floatingBottom - minOf(height, limit)),
        )
        windowManager.updateViewLayout(quick.root, params)
        constrainQuickPosition(updateEvenIfUnchanged = true)
    }

    private fun restoreTriggerPosition() {
        val position = OverlayPosition.fromFractions(
            positionStore.load(),
            (screenWidth - TRIGGER_SIZE_DP.dp).coerceAtLeast(0),
            (screenHeight - TRIGGER_SIZE_DP.dp).coerceAtLeast(0),
        )
        triggerLayoutParams.x = position.x
        triggerLayoutParams.y = position.y.coerceAtLeast(floatingTop)
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
        overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
        // Xiaomi may attach accessibility overlays to the IME during its
        // resize animation unless this is explicit. Keep the trigger fixed.
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    }

    private fun quickParams() = WindowManager.LayoutParams(
        minOf(QUICK_WIDTH_DP.dp, (screenWidth - 24.dp).coerceAtLeast(1)),
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.TOP
        // Candidate content must stay in its own screen position while the
        // chat input method opens underneath it.
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        val width = minOf(QUICK_WIDTH_DP.dp, (screenWidth - 24.dp).coerceAtLeast(1))
        x = if (triggerLayoutParams.x > screenWidth / 2) {
            triggerLayoutParams.x - width - QUICK_GAP_DP.dp
        } else {
            triggerLayoutParams.x + TRIGGER_SIZE_DP.dp + QUICK_GAP_DP.dp
        }.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        y = (triggerLayoutParams.y - QUICK_VERTICAL_OFFSET_DP.dp).coerceIn(
            floatingTop,
            maxOf(floatingTop, floatingBottom - QUICK_MIN_VISIBLE_HEIGHT_DP.dp),
        )
    }

    @Suppress("DEPRECATION")
    private fun instructionEditorParams() = WindowManager.LayoutParams(
        minOf(320.dp, (screenWidth - 32.dp).coerceAtLeast(1)),
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType,
        // Modal: outside touches cannot race a focus handoff into the chat.
        WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = (screenHeight * 0.12f).toInt()
        dimAmount = 0.25f
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    private fun copyFeedbackParams(width: Int, height: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType,
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
        overlayWindowType,
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
        const val QUICK_GAP_DP = 4
        const val QUICK_VERTICAL_OFFSET_DP = 12
        const val QUICK_MIN_VISIBLE_HEIGHT_DP = 180
        const val COPY_FEEDBACK_GAP_DP = 8
        const val COPY_FEEDBACK_DURATION_MS = 800L
    }
}
