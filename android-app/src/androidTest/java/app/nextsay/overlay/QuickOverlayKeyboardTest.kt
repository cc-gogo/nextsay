package app.nextsay.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nextsay.MainActivity
import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.context.ReplyRoundSnapshot
import app.nextsay.capture.ContextCaptureResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Real overlay code and Android IME on a local fixture page. No API, chat reads, typing or sends. */
@RunWith(AndroidJUnit4::class)
class QuickOverlayKeyboardTest {
    @Test fun horizontalDragNeverCoversBallAndCanContinueDragging() = withFixture { fixture ->
        fixture.showCandidates()
        val trigger = main { field(fixture.overlay, "trigger") as View }
        main {
            val params = trigger.layoutParams as WindowManager.LayoutParams
            params.y = 420
            (field(fixture.overlay, "windowManager") as WindowManager).updateViewLayout(trigger, params)
        }
        fun move(dx: Float, dy: Float) {
            val box = main { bounds(trigger) }
            val start = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                val event = MotionEvent.obtain(start, start + index * 100L, action,
                    box.centerX() + if (index == 0) 0f else dx, box.centerY() + if (index == 0) 0f else dy, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            instrumentation.waitForIdleSync()
        }
        move(-360f, 0f)
        main {
            assertTrue(fixture.overlay.isQuickOpen && fixture.quick.root.isShown)
            assertFalse("Window must not cover the ball", Rect.intersects(bounds(trigger), bounds(fixture.quick.root)))
        }
        val oldY = main { bounds(trigger).top }
        move(0f, 120f)
        main {
            assertTrue("Ball must still accept a second drag", bounds(trigger).top > oldY)
            assertTrue(fixture.overlay.isQuickOpen && fixture.quick.root.isShown)
            assertFalse(Rect.intersects(bounds(trigger), bounds(fixture.quick.root)))
            assertTrue(fixture.generated.isEmpty() && fixture.triggerClicks.isEmpty())
        }
    }
    @Test
    fun dragKeepsCandidateRootAndRequirementAndRefreshDoesNotReplaceWindow() = withFixture { fixture ->
        fixture.showCandidates()
        val root = fixture.quick.root
        main {
            fixture.overlay.setQuickInstruction("本地要求至少50字")
            val trigger = field(fixture.overlay, "trigger") as View
            val params = trigger.layoutParams as WindowManager.LayoutParams
            params.y = 420
            (field(fixture.overlay, "windowManager") as WindowManager).updateViewLayout(trigger, params)
        }
        val before = main { bounds(root) }
        val triggerBox = main { bounds(field(fixture.overlay, "trigger") as View) }
        val start = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val y = triggerBox.centerY().toFloat() + if (index == 0) 0f else 120f
            val event = MotionEvent.obtain(start, start + index * 100L, action, triggerBox.centerX().toFloat(), y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
        main {
            assertTrue(root.isAttachedToWindow && root.isShown && fixture.overlay.isQuickOpen)
            assertTrue("Candidate root must follow the ball vertically", bounds(root).top > before.top)
            assertEquals("本地要求至少50字", fixture.overlay.quickInstructionText)
            assertTrue("Drag must not request generation", fixture.generated.isEmpty())
            assertTrue("Drag release must not act like a click", fixture.triggerClicks.isEmpty())
            fixture.overlay.renderQuick(OverlayState.Loading(roundContext("本地刷新")))
            assertTrue(root.isAttachedToWindow && root.isShown)
            fixture.overlay.renderQuick(OverlayState.Results(roundContext("本地刷新"), listOf(ReplyCandidate("a", "新候选"))))
            assertTrue(root === fixture.quick.root && root.isAttachedToWindow && root.isShown)
            assertTrue(bounds(root).top >= (root.resources.displayMetrics.heightPixels * .09f).toInt())
        }
    }
    @Test
    fun savedRelationshipLoadsOnOpenAndTemporaryChoiceDoesNotLeak() = withFixture { fixture ->
        main {
            val lover = roundContext("本地对象甲").copy(contactId = "local-a",
                generationExtras = app.nextsay.contacts.GenerationExtras(relationship = "lover"))
            fixture.overlay.renderAdvanced(OverlayState.Preview(lover))
            val panel = field(fixture.overlay, "panel") as PanelViews
            val picker = field(panel, "relationship") as app.nextsay.ui.RelationshipChoices
            assertEquals("lover", picker.value)
            assertTrue(textView(picker, "恋人").isSelected)
            textView(picker, "朋友").performClick()
            fixture.overlay.renderAdvanced(OverlayState.Loading(lover))
            fixture.overlay.renderAdvanced(OverlayState.Error(lover, "本地模拟失败"))
            assertEquals("friend", picker.value)
            fixture.overlay.renderAdvanced(OverlayState.Preview(lover))
            assertEquals("friend", picker.value) // Same attached round refresh.
            fixture.overlay.hidePanel()
            fixture.overlay.renderAdvanced(OverlayState.Preview(lover))
            assertEquals("lover", picker.value)
            val colleague = lover.copy(contactId = "local-b",
                generationExtras = app.nextsay.contacts.GenerationExtras(relationship = "colleague"))
            fixture.overlay.renderAdvanced(OverlayState.Preview(colleague))
            assertEquals("colleague", picker.value)
            fixture.overlay.renderAdvanced(OverlayState.Preview(roundContext("未关联本地对象")))
            assertEquals("unspecified", picker.value)
        }
    }

    @Test
    fun relationshipSelectionStaysInsideAttachedOverlay() = withFixture { fixture ->
        main {
            fixture.overlay.renderAdvanced(OverlayState.Preview(roundContext("本地关系选择测试")))
            val panel = field(fixture.overlay, "panel") as PanelViews
            val picker = field(panel, "relationship") as app.nextsay.ui.RelationshipChoices
            val root = panel.root
            textView(picker, "恋人").performClick()
            assertEquals("lover", picker.value)
            assertTrue("Selection must not close or replace the overlay", root.isAttachedToWindow && root.isShown)
            fixture.overlay.renderAdvanced(OverlayState.Loading(roundContext("本地关系选择测试")))
            assertFalse("Relationship chips must be disabled during generation", textView(picker, "朋友").isEnabled)
            assertTrue(root.isAttachedToWindow && root.isShown)
        }
    }

    @Test
    fun advancedRefreshRetainsVisibleOldRowsUntilResults() = withFixture { fixture ->
        main {
            val context = roundContext("本地高级面板测试")
            fixture.overlay.renderAdvanced(OverlayState.Results(context, listOf(ReplyCandidate("a", "本地旧候选"))))
            val panel = field(fixture.overlay, "panel") as PanelViews
            val rows = field(panel, "candidates") as LinearLayout
            val oldRow = rows.getChildAt(0)
            fixture.overlay.renderAdvanced(OverlayState.Loading(context))
            assertTrue(panel.root.isAttachedToWindow && oldRow.isAttachedToWindow)
            assertFalse(oldRow.isEnabled)
            fixture.overlay.renderAdvanced(OverlayState.Results(context, listOf(ReplyCandidate("a", "本地新候选"))))
            assertEquals("本地新候选", (rows.getChildAt(0) as TextView).text.toString())
        }
    }
    @Test
    fun refreshKeepsSameRootAttachedAndDoesNotReopenSuppressedWindow() = withFixture { fixture ->
        fixture.showCandidates()
        main {
            val root = fixture.quick.root
            fixture.overlay.renderQuick(OverlayState.Preview(roundContext("新的测试消息")))
            assertTrue(root.isAttachedToWindow && root.isShown)
            fixture.overlay.renderQuick(OverlayState.Loading(roundContext("新的测试消息")))
            assertTrue(root.isAttachedToWindow && root.isShown)
            fixture.overlay.suppressQuick()
            fixture.overlay.renderQuick(OverlayState.Results(roundContext("新的测试消息"), listOf(ReplyCandidate("a", "一"), ReplyCandidate("b", "二"), ReplyCandidate("c", "三"))))
            assertFalse(fixture.overlay.isQuickOpen)
        }
        // WindowManager.removeView detaches on the next traversal, not inside this main task.
        await("Suppressed candidates must detach and stay closed") { main { !fixture.quick.root.isAttachedToWindow } }
        main {
            fixture.overlay.showCachedQuick()
        }
        await("Explicit reopening must show the original root") { main { fixture.quick.root.isAttachedToWindow && fixture.quick.root.isShown } }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation: UiAutomation get() = instrumentation.uiAutomation

    @Test
    fun busyCaptureLocksInstructionAndGenerateButNotTrigger() = withFixture { fixture ->
        fixture.showCandidates()
        main { fixture.overlay.setBusy(true) }
        tap(main { bounds(descendants(fixture.quick.root).first { it.contentDescription == "编辑补充要求" }) })
        assertFalse("Capture must prevent a late editor from overwriting the submitted instruction",
            main { fixture.editorRoot().isAttachedToWindow })
        tap(main { bounds(textView(fixture.quick.root, "生成新回复")) })
        assertTrue("Capture must reject a second generation", main { fixture.generated.isEmpty() })
        fixture.tapTrigger()
        assertEquals("Busy trigger must still reach cancellation handler", 1, main { fixture.triggerClicks.size })
        main { fixture.overlay.setBusy(false) }
        fixture.openEditor()
        tap(main { bounds(textView(fixture.editorRoot(), "取消")) })
        fixture.awaitEditorClosed()
        main {
            fixture.overlay.renderQuick(OverlayState.Loading(roundContext("本地测试")))
            fixture.overlay.setBusy(false)
        }
        assertFalse("Ending capture must not reenable Generate while model is loading",
            main { textView(fixture.quick.root, "生成新回复").isEnabled })
    }

    @Test
    fun openCandidateTriggerDelegatesBeforeCollapsing() = withFixture { fixture ->
        fixture.showCandidates()
        fixture.tapTrigger()
        assertEquals("Trigger must let service check current messages", 1, main { fixture.triggerClicks.size })
        assertTrue("Candidate window must not be discarded before context check", main { fixture.quick.root.isShown })
    }

    @Test
    fun singleTriggerWithNewIncomingClearsRequirementAndRefreshesCandidates() = withFixture(roundFlow = true) { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        main { fixture.editorInput().setText("至少50字") }
        fixture.completeEditor()
        main {
            fixture.roundHarness!!.current = roundContext("本地测试", "对方新的回复")
        }
        fixture.tapTrigger()
        await("One tap did not regenerate for latest incoming") { main { fixture.generated.size == 2 } }
        assertEquals(listOf("", ""), main { fixture.generated.toList() })
        assertEquals("对方新的回复", main { fixture.roundHarness!!.received.last().messages.last().text })
        assertEquals("", main { fixture.overlay.quickInstructionText })
        assertTrue("New candidates must remain open", main { fixture.quick.root.isShown })
        fixture.openEditor()
        assertEquals("", main { fixture.editorInput().text.toString() })
        tap(main { bounds(textView(fixture.editorRoot(), "取消")) })
        fixture.awaitEditorClosed()
    }

    @Test
    fun unchangedCollapseAndReopenRetainsCompletedRequirement() = withFixture(roundFlow = true) { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        main { fixture.editorInput().setText("本轮简短") }
        fixture.completeEditor()
        fixture.tapTrigger()
        assertFalse("Unchanged trigger must collapse", main { fixture.quick.root.isShown })
        assertEquals(1, main { fixture.generated.size })
        assertEquals("本轮简短", main { fixture.overlay.quickInstructionText })
        fixture.tapTrigger()
        await("Same-round reopen failed") { main { fixture.quick.root.isShown } }
        assertEquals(listOf("", "本轮简短"), main { fixture.generated.toList() })
        assertEquals("本轮简短", main { fixture.overlay.quickInstructionText })
    }

    @Test
    fun generateOnNewIncomingClearsPreviousRoundRequirement() = withFixture(roundFlow = true) { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        main { fixture.editorInput().setText("至少50字") }
        fixture.completeEditor()
        main { fixture.roundHarness!!.current = roundContext("本地测试", "新的问题") }
        tap(main { bounds(textView(fixture.quick.root, "生成新回复")) })
        await("Generate failed to recapture latest round") { main { fixture.generated.size == 2 } }
        assertEquals("新的问题", main { fixture.roundHarness!!.received.last().messages.last().text })
        assertEquals(listOf("", ""), main { fixture.generated.toList() })
        assertEquals("", main { fixture.overlay.quickInstructionText })
    }

    @Test
    fun candidatesNeverOwnInputFocusAndCopyKeepsKeyboard() = withFixture { fixture ->
        fixture.showCandidates()
        assertTrue("Candidate window must contain no editable input",
            main { descendants(fixture.quick.root).none { it is EditText } })
        assertTrue("Candidate window must always be non-focusable", main {
            val params = fixture.quick.root.layoutParams as WindowManager.LayoutParams
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
        })
        tap(main { bounds(fixture.chatInput) })
        await("Candidate window stole chat keyboard") { keyboardVisible() }
        tap(main { bounds(textView(fixture.quick.root, "本地候选一")) })
        SystemClock.sleep(600)
        assertTrue("Copy must keep candidates visible", main { fixture.quick.root.isShown })
        assertTrue("Copy must not hide chat keyboard", keyboardVisible())
        assertFalse("Candidate window must not acquire focus", main { fixture.quick.root.hasWindowFocus() })
    }

    @Test
    fun completeInstructionThenSingleChatTapShowsKeyboard() = withFixture { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        main { fixture.editorInput().setText("简短一点") }
        fixture.completeEditor()
        assertTrue("Completing must keep candidates", main { fixture.quick.root.isShown })
        assertTrue("Completing must not generate", main { fixture.generated.isEmpty() })
        tap(main { bounds(fixture.chatInput) })
        await("Single chat tap after completing must show keyboard") {
            keyboardVisible() && main { fixture.chatInput.hasFocus() && fixture.chatInput.hasWindowFocus() }
        }
        SystemClock.sleep(600)
        assertTrue("Chat keyboard must remain visible", keyboardVisible())
        tap(main { bounds(textView(fixture.quick.root, "生成新回复")) })
        await("Saved requirement did not reach generate") { main { fixture.generated.isNotEmpty() } }
        assertEquals(listOf("简短一点"), main { fixture.generated.toList() })
    }

    @Test
    fun cancelInstructionPreservesSavedRequirement() = withFixture { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        main { fixture.editorInput().setText("保留这条要求") }
        fixture.completeEditor()
        fixture.openEditor()
        assertEquals("保留这条要求", main { fixture.editorInput().text.toString() })
        main { fixture.editorInput().setText("这条不要保存") }
        tap(main { bounds(textView(fixture.editorRoot(), "取消")) })
        fixture.awaitEditorClosed()
        tap(main { bounds(textView(fixture.quick.root, "生成新回复")) })
        await("Saved requirement did not reach generate") { main { fixture.generated.isNotEmpty() } }
        assertEquals(listOf("保留这条要求"), main { fixture.generated.toList() })
        assertTrue("Cancel must keep candidates", main { fixture.quick.root.isShown })
    }

    @Test
    fun longRequirementKeepsCompletionReachableAboveKeyboard() = withFixture { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        val longRequirement = (1..80).joinToString("\n") { "本地测试要求第 $it 行，允许滚动编辑" }
        main { fixture.editorInput().setText(longRequirement) }
        instrumentation.waitForIdleSync()
        fixture.completeEditor() // Tap helper rejects controls hidden behind the IME.
        fixture.openEditor()
        assertEquals(longRequirement, main { fixture.editorInput().text.toString() })
        tap(main { bounds(textView(fixture.editorRoot(), "取消")) })
        fixture.awaitEditorClosed()
    }

    @Test
    fun collapseDuringEditingRemovesEditorAndKeepsSavedRequirement() = withFixture { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        main {
            fixture.editorInput().setText("未完成的草稿")
            fixture.overlay.hideQuick()
        }
        fixture.awaitEditorClosed()
        assertFalse("Collapse must remove candidates", main { fixture.quick.root.isShown })
        fixture.showCandidates()
        fixture.openEditor()
        assertEquals("", main { fixture.editorInput().text.toString() })
        tap(main { bounds(textView(fixture.editorRoot(), "取消")) })
        fixture.awaitEditorClosed()
    }

    @Test
    fun outsideTapCannotRaceEditorFocusHandoff() = withFixture { fixture ->
        fixture.showCandidates()
        fixture.openEditor()
        assertFalse("Outside test target must not overlap the modal",
            main { Rect.intersects(bounds(fixture.chatInput), bounds(fixture.editorRoot())) })
        tap(main { bounds(fixture.chatInput) })
        SystemClock.sleep(600)
        assertTrue("Outside tap must keep modal editor active", main { fixture.editorRoot().hasWindowFocus() })
        assertFalse("Outside tap must not focus the chat window", main { fixture.chatInput.hasWindowFocus() })
        assertTrue("Outside tap must keep editor keyboard", keyboardVisible())
        tap(main { bounds(textView(fixture.editorRoot(), "取消")) })
        fixture.awaitEditorClosed()
    }

    @Test
    fun captureCloseCancelsPendingEditorKeyboardRequest() = withFixture { fixture ->
        fixture.showCandidates()
        main {
            descendants(fixture.quick.root).first { it.contentDescription == "编辑补充要求" }.performClick()
            fixture.overlay.hideForCapture()
            fixture.overlay.restoreAfterCapture()
        }
        fixture.awaitEditorClosed()
        SystemClock.sleep(600)
        assertFalse("A closed editor must not show a late keyboard", keyboardVisible())
        assertTrue("Capture restoration must keep candidates", main { fixture.quick.root.isShown })
    }

    private fun withFixture(roundFlow: Boolean = false, test: (Fixture) -> Unit) {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var chatInput: EditText
        lateinit var overlay: OverlayWindow
        lateinit var quick: QuickReplyViews
        lateinit var savedPosition: PositionFractions
        val generated = mutableListOf<String>()
        val triggerClicks = mutableListOf<Unit>()
        val roundHarness = if (roundFlow) LocalRoundHarness(generated) else null
        scenario.onActivity { activity ->
            // Matches the STATE_UNSPECIFIED / ADJUST_NOTHING behavior seen on WeChat.
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            chatInput = EditText(activity).apply { hint = "本地键盘测试，不发送" }
            activity.setContentView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                // ADJUST_NOTHING does not move the view above the IME; keep fixture input
                // in the visible middle of the screen so taps cannot hit keyboard keys.
                setPadding(30, (activity.resources.displayMetrics.heightPixels * 0.45f).toInt(), 30, 0)
                addView(chatInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120))
            })
            val failIfGenerated = { error("This test must never generate or insert") }
            overlay = OverlayWindow(
                HostedService(activity),
                PanelCallbacks({}, { _, _ -> failIfGenerated() }, failIfGenerated,
                    { failIfGenerated() }, {}, {}),
                onQuickTrigger = {
                    triggerClicks += Unit
                    roundHarness?.request(toggle = overlay.isQuickOpen)
                },
                onQuickDismiss = {},
                onAdvancedTrigger = {},
                onQuickGenerate = {
                    if (roundHarness != null) roundHarness.request(toggle = false)
                    else generated += it
                },
                onQuickInsert = { failIfGenerated() },
            )
            savedPosition = (field(overlay, "positionStore") as OverlayPositionStore).load()
            overlay.setSupportedAppActive(true)
            // Use deterministic in-memory placement without overwriting the user's saved position.
            val trigger = field(overlay, "trigger") as View
            val triggerParams = trigger.layoutParams as WindowManager.LayoutParams
            triggerParams.x = activity.resources.displayMetrics.widthPixels - triggerParams.width
            triggerParams.y = 64
            (field(overlay, "windowManager") as WindowManager).updateViewLayout(trigger, triggerParams)
            quick = field(overlay, "quick") as QuickReplyViews
            roundHarness?.overlay = overlay
        }
        try {
            await("Local input page not laid out") { main { chatInput.width > 0 && chatInput.hasWindowFocus() } }
            test(Fixture(chatInput, overlay, quick, generated, triggerClicks, roundHarness))
        } finally {
            main {
                overlay.dispose()
                val store = field(overlay, "positionStore") as OverlayPositionStore
                if (store.load() != savedPosition) store.save(savedPosition)
            }
            scenario.close()
        }
    }

    private inner class Fixture(
        val chatInput: EditText,
        val overlay: OverlayWindow,
        val quick: QuickReplyViews,
        val generated: List<String>,
        val triggerClicks: List<Unit>,
        val roundHarness: LocalRoundHarness?,
    ) {
        fun showCandidates() {
            main {
                if (roundHarness != null) roundHarness.request(toggle = false)
                else overlay.renderQuick(OverlayState.Results(
                    ChatContext("wechat", "com.tencent.mm", listOf(ChatMessage(MessageRole.OTHER, "本地测试", 1f)), "", 1f),
                    listOf(ReplyCandidate("brief", "本地候选一"), ReplyCandidate("polite", "本地候选二"),
                        ReplyCandidate("natural", "本地候选三")),
                ))
            }
            await("Local candidates not shown") { main { quick.root.isShown && quick.root.width > 0 } }
            assertFalse("Fixture chat input must not overlap candidates",
                main { Rect.intersects(bounds(chatInput), bounds(quick.root)) })
        }

        fun tapTrigger() = tap(main { bounds(field(overlay, "trigger") as View) })

        fun editorRoot(): View = field(field(overlay, "instructionEditor")!!, "root") as View
        fun editorInput(): EditText = descendants(editorRoot()).filterIsInstance<EditText>().single()

        fun openEditor() {
            tap(main { bounds(descendants(quick.root).first { it.contentDescription == "编辑补充要求" }) })
            await("Independent editor did not acquire keyboard") {
                main { editorRoot().isShown && editorRoot().hasWindowFocus() && editorInput().hasFocus() } && keyboardVisible()
            }
            assertFalse("Candidate window must never own editor focus", main { quick.root.hasWindowFocus() })
        }

        fun completeEditor() {
            tap(main { bounds(textView(editorRoot(), "完成")) })
            awaitEditorClosed()
        }

        fun awaitEditorClosed() {
            await("Independent editor not removed") { main { !editorRoot().isAttachedToWindow && chatInput.hasWindowFocus() } }
            await("Editor keyboard not dismissed") { !keyboardVisible() }
        }
    }

    /** Capture/API boundaries are synthetic; the round policy, controller and UI are real. */
    private class LocalRoundHarness(private val generated: MutableList<String>) {
        lateinit var overlay: OverlayWindow
        var current = roundContext("本地测试")
        val received = mutableListOf<ChatContext>()
        private val controller = OverlayController { context, instruction, _, _ ->
            received += context
            generated += instruction
            Result.success(listOf(ReplyCandidate("brief", "本地候选一"), ReplyCandidate("polite", "本地候选二"),
                ReplyCandidate("natural", "本地候选三")))
        }
        private val flow = QuickReplyFlow(controller,
            capture = { ContextCaptureResult.Success(current) },
            setInstruction = { overlay.setQuickInstruction(it) },
            dismiss = { overlay.hideQuick() },
            captureError = { error(it) },
        )
        fun request(toggle: Boolean) {
            runBlocking { flow.request("com.tencent.mm", overlay.quickInstructionText, toggle) }
            overlay.renderQuick(controller.state.value)
        }
    }

    private companion object {
        fun roundContext(vararg text: String): ChatContext {
            val messages = text.map { ChatMessage(MessageRole.OTHER, it, 1f) }
            return ChatContext("wechat", "com.tencent.mm", messages, "", 1f, ReplyRoundSnapshot("本地测试聊天", messages))
        }
    }

    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()

    private fun textView(root: View, text: String): TextView =
        descendants(root).filterIsInstance<TextView>().first { it.text.toString() == text }

    // Only the Android window token/type is adapted for an Activity-hosted test. All overlay
    // flags, focus callbacks, touches and IME operations come from the production OverlayWindow.
    private class HostedService(activity: Activity) : AccessibilityService() {
        private val hostedWindows = object : WindowManager by activity.getSystemService(WindowManager::class.java) {
            private val delegate = activity.getSystemService(WindowManager::class.java)
            private fun adapt(params: ViewGroup.LayoutParams) {
                (params as WindowManager.LayoutParams).apply {
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                    token = activity.window.decorView.windowToken
                }
            }
            override fun addView(view: View, params: ViewGroup.LayoutParams) {
                adapt(params)
                delegate.addView(view, params)
            }
            override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
                adapt(params)
                delegate.updateViewLayout(view, params)
            }
        }
        init { attachBaseContext(activity) }
        override fun getSystemService(name: String): Any? =
            if (name == WINDOW_SERVICE) hostedWindows else super.getSystemService(name)
        override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
        override fun onInterrupt() = Unit
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).run {
        isAccessible = true
        get(owner)
    }
    private fun keyboardVisible() = automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    private fun bounds(view: View): Rect {
        val position = IntArray(2)
        view.getLocationOnScreen(position)
        return Rect(position[0], position[1], position[0] + view.width, position[1] + view.height)
    }
    private fun tap(rect: Rect) {
        assertTrue("Tap target must have bounds", rect.width() > 0 && rect.height() > 0)
        val imeBounds = Rect().also { bounds ->
            automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.getBoundsInScreen(bounds)
        }
        assertTrue("Tap target must not overlap the IME", imeBounds.isEmpty || rect.bottom <= imeBounds.top)
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                rect.centerX().toFloat(), rect.centerY().toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue("Tap injection failed", automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error(message)
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> main(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        return result as T
    }
}
