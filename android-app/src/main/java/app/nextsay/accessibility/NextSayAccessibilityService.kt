package app.nextsay.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.app.KeyguardManager
import android.os.Build
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Region
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.util.Log
import android.widget.Toast
import app.nextsay.api.NextSayRepository
import app.nextsay.capture.AccessibilityScreenshotSource
import app.nextsay.capture.CapturedConversation
import app.nextsay.capture.CaptureTextVisibility
import app.nextsay.capture.AutoRefreshScheduler
import app.nextsay.capture.ContextCaptureResult
import app.nextsay.capture.ConversationContextCoordinator
import app.nextsay.capture.LatestContextCache
import app.nextsay.capture.ScrollRevisionTracker
import app.nextsay.capture.ChatViewportBounds
import app.nextsay.capture.CaptureVisibility
import app.nextsay.context.ChatContext
import app.nextsay.context.ConversationTitleExtractor
import app.nextsay.context.ContextNormalizer
import app.nextsay.history.AndroidKeystoreMessageCipher
import app.nextsay.history.ConversationHistoryRepository
import app.nextsay.history.db.NextSayDatabase
import app.nextsay.insertion.InsertResult
import app.nextsay.insertion.InsertionGate
import app.nextsay.insertion.ReplyInserter
import app.nextsay.ime.GenerationPresentationPolicy
import app.nextsay.ime.GenerationDestination
import app.nextsay.ime.GenerationSurface
import app.nextsay.ime.ImeGenerationHandler
import app.nextsay.ime.NextSayImeRuntime
import app.nextsay.overlay.OverlayController
import app.nextsay.overlay.QuickReplyFlow
import app.nextsay.overlay.OverlayWindow
import app.nextsay.overlay.PanelCallbacks
import app.nextsay.overlay.ReplyCandidate
import app.nextsay.ocr.MlKitChineseOcrEngine
import app.nextsay.ocr.WechatOcrParser
import app.nextsay.ocr.WechatBubbleDetector
import app.nextsay.ocr.WechatMediaDetector
import app.nextsay.ocr.WechatMediaGeometry
import app.nextsay.privacy.TextRedactor
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import app.nextsay.nextSayDependencies
import app.nextsay.contacts.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class NextSayAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val flattener = NodeTreeFlattener()
    private val normalizer = ContextNormalizer()
    private val titleExtractor = ConversationTitleExtractor()
    private val ocrParser = WechatOcrParser()
    private val inserter = ReplyInserter()
    private val redactor = TextRedactor()
    private val foregroundWindowResolver = ForegroundWindowResolver()
    private val foregroundEventPolicy = ForegroundEventPolicy()
    private val insertionGate = InsertionGate()
    private val generationPresentationPolicy = GenerationPresentationPolicy()
    private val latestContextCache = LatestContextCache()
    private val scrollRevisionTracker = ScrollRevisionTracker()
    private val autoRefreshScheduler = AutoRefreshScheduler()
    private val imeGenerationHandler = ImeGenerationHandler(::generateForIme)
    private var activePackage: String? = null
    private var captureStage: String? = null
    private var captureApp: String? = null
    private var captureNodeCount: Int? = null
    private var captureTextCount: Int? = null
    private var captureBottom: Int? = null
    private var lastAutomaticState: String? = null
    private var generationSurface = GenerationSurface.ADVANCED
    private lateinit var repository: NextSayRepository
    private lateinit var controller: OverlayController
    private lateinit var overlay: OverlayWindow
    private lateinit var ocrEngine: MlKitChineseOcrEngine
    private lateinit var screenshotSource: AccessibilityScreenshotSource
    private lateinit var contextCoordinator: ConversationContextCoordinator
    private lateinit var historyWriter: BackgroundHistoryWriter
    private var autoRefreshJob: Job? = null
    private var autoCaptureRunning = false
    private var quickRequestJob: Job? = null
    // All surfaces share the busy lock; an old surface must not unlock a newer one.
    private var busyEpoch = 0L
    private lateinit var quickReplyFlow: QuickReplyFlow
    private val incomingDetector = IncomingRoundDetector()
    private var currentCapture: CapturedConversation? = null
    private var currentContact: ContactSnapshot? = null
    private var currentResolution: ContactResolution? = null
    private var automaticGenerationJob: Job? = null
    private val automaticRounds = AutomaticRoundQueue()
    private var automaticTicket: AutomaticRoundQueue.Ticket? = null
    private var automaticWatchJob: Job? = null
    private var automaticViewportRevision: Long? = null
    private var editorChanged = false
    private var retainInstruction = false
    private var screenReceiverRegistered = false
    private var imeGenerationJob: Job? = null
    private var advancedGenerationJob: Job? = null
    private var unsupportedPackageJob: Job? = null
    // Cancelling a coroutine that already passed delay() is racy on some ROMs.
    // This token makes an old unsupported-app teardown harmless after a chat
    // window becomes foreground again.
    private var unsupportedTeardownToken = 0L
    private fun cancelUnsupportedTeardown() {
        unsupportedPackageJob?.cancel()
        unsupportedPackageJob = null
        unsupportedTeardownToken++
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::overlay.isInitialized) return
            cancelAutoRefresh()
            cancelAutomaticGeneration()
            cancelImeGeneration()
            cancelAdvancedGeneration()
            cancelQuickRequest()
            controller.beginContextCheck()
            incomingDetector.reset()
            automaticRounds.clear()
            latestContextCache.clear()
            if (intent?.action == Intent.ACTION_USER_PRESENT && isUnlockedInteractive()) scheduleCurrentRefresh()
        }
    }
    private val contacts get() = nextSayDependencies.contactStore
    private fun isUnlockedInteractive(): Boolean {
        val keyguard = getSystemService(KeyguardManager::class.java)
        return GenerationEligibility.unlocked(getSystemService(PowerManager::class.java)?.isInteractive == true,
            keyguard?.isKeyguardLocked != false, keyguard?.isDeviceLocked != false)
    }
    private fun cancelImeGeneration() { imeGenerationJob?.cancel(); imeGenerationJob = null }
    private fun cancelAdvancedGeneration() {
        if (advancedGenerationJob?.isActive == true) { advancedGenerationJob?.cancel(); controller.stopPendingGeneration() }
        advancedGenerationJob = null
    }

    override fun onServiceConnected() {
        val dependencies = nextSayDependencies
        repository = NextSayRepository(
            configStore = dependencies.providerConfigStore,
            configValidator = dependencies.providerConfigValidator,
            client = dependencies.replyProviderClient,
            redactor = redactor,
            diagnostics = dependencies.diagnostics,
            eventFactory = dependencies.diagnosticEventFactory,
        )
        controller = OverlayController(
            diagnostics = dependencies.diagnostics,
            eventFactory = dependencies.diagnosticEventFactory,
        ) { context, instruction, relationship, surface ->
            val dependencies = nextSayDependencies
            val freshContext = prepareGenerationContext(context, includeMemory = true)
            val requestTicket = automaticTicket
            val automatic = requestTicket != null
            dependencies.diagnostics.record(dependencies.diagnosticEventFactory.create(
                DiagnosticEventType.CANDIDATE_REQUESTED, surface,
                triggerReason = if (!automatic) "manual_refresh" else if (
                    (freshContext.replyRound?.visibleMessages ?: freshContext.messages).lastOrNull()?.role == app.nextsay.context.MessageRole.ME
                ) "auto_self" else "auto_incoming",
                memoryIncluded = !freshContext.generationExtras?.memory.isNullOrBlank(),
            ))
            val result = repository.generate(freshContext, instruction, relationship, surface)
            if (automatic) {
                var frameConfirmed = latestContextCache.fresh(context.sourcePackage)?.let { automaticRounds.matches(requestTicket!!, it) } == true
                if (!frameConfirmed) {
                    val checked = obtainFreshContext(context.sourcePackage, forceRefresh = true)
                    if (checked is ContextCaptureResult.Success) {
                        val updated = checked.context
                        if (incomingDetector.observe(detectorKey(updated), updated)) automaticRounds.offer(updated)
                        frameConfirmed = automaticRounds.matches(requestTicket!!, updated)
                    }
                }
                val stillEnabled = withContext(Dispatchers.IO) { context.contactId?.let { contacts.get(it)?.entity?.autoEnabled } == true }
                if (!stillEnabled || contacts.totalPaused || activePackage != context.sourcePackage ||
                    currentContact?.entity?.id != context.contactId || resolveForegroundApplicationPackage() != context.sourcePackage ||
                    !isUnlockedInteractive() || (overlay.isEditing && !overlay.isQuickOpen) ||
                    requestTicket?.let { !automaticRounds.isCurrent(it) } != false || !frameConfirmed) {
                    // A paid response that cannot safely be displayed is not auto-retried.
                    requestTicket?.let(automaticRounds::finish)
                    recordAutomaticState("unverified_frame")
                    controller.beginContextCheck()
                    throw kotlinx.coroutines.CancellationException("Automatic eligibility changed")
                }
            }
            result
        }
        ocrEngine = MlKitChineseOcrEngine().also { it.warmUp() }
        screenshotSource = AccessibilityScreenshotSource(this)
        overlay = OverlayWindow(
            service = this,
            callbacks = PanelCallbacks(
                onRefresh = { openAdvancedPanel(refresh = true) },
                onGenerate = { instruction, relationship ->
                    advancedGenerationJob = scope.launch { controller.generate(instruction, relationship) }
                },
                onRetry = { advancedGenerationJob = scope.launch { controller.retry() } },
                onCandidate = ::insertCandidate,
                onDismiss = { cancelAdvancedGeneration(); controller.dismiss() },
                onCopyDiagnostics = ::copyDiagnostics,
                onManageContact = { id -> openContactSettings(id) },
            ),
            onQuickTrigger = ::runQuickReply,
            onQuickDismiss = ::dismissQuickReply,
            onAdvancedTrigger = ::openContactMenu,
            onQuickGenerate = ::regenerateQuickReply,
            onQuickInsert = ::insertQuickCandidate,
        )
        overlay.onEditorVisibilityChanged = { editing ->
            if (editing) {
                cancelAutoRefresh()
                cancelAutomaticGeneration()
            } else if (editorChanged) {
                editorChanged = false
                if (currentContact?.entity?.autoEnabled == true && !contacts.totalPaused) {
                    overlay.resumeQuickPresentation()
                    scheduleCurrentRefresh() // Keep the old baseline so arrivals during editing remain new.
                } else overlay.showMenu("编辑期间聊天可能已变化，如何处理本轮要求？", listOf(
                    "用于最新消息并生成" to { retainInstruction = true; regenerateQuickReply(overlay.quickInstructionText) },
                    "清空本轮要求，等待新消息" to { overlay.setQuickInstruction(""); incomingDetector.reset(); scheduleCurrentRefresh() },
                ))
            } else {
                overlay.resumeQuickPresentation()
                scheduleCurrentRefresh()
            }
        }
        historyWriter = BackgroundHistoryWriter(CoroutineScope(scope.coroutineContext + Dispatchers.IO),
            persist = { id, capture -> contacts.observe(id, capture) },
            currentEpoch = { contacts.historyEpoch },
            isEpochCurrent = contacts::isHistoryEpochCurrent,
            persistAtEpoch = { id, capture, epoch ->
                contacts.observe(id, capture, expectedEpoch = epoch)
                scope.launch(Dispatchers.IO) {
                    if (!contacts.warmMemory(id, expectedEpoch = contacts.historyEpoch))
                        dependencies.diagnostics.record(dependencies.diagnosticEventFactory.create(
                            DiagnosticEventType.HISTORY_SAVE_FAILED, DiagnosticSurface.OVERLAY, captureStage = "history_save"))
                }
            },
            onFailure = { dependencies.diagnostics.record(dependencies.diagnosticEventFactory.create(
                DiagnosticEventType.HISTORY_SAVE_FAILED, DiagnosticSurface.OVERLAY, captureStage = "history_save")) },
        )
        contextCoordinator = ConversationContextCoordinator(
            accessibilityCapture = ::captureAccessibilityConversation,
            ocrCapture = ::captureWithOcr,
            mergeHistory = { capture ->
                withContext(Dispatchers.IO) {
                    val historyEpoch = contacts.historyEpoch
                    val resolution = contacts.resolve(capture)
                    val oldKey = currentCapture?.let { it.context.sourcePackage to it.title }
                    val chosenId = contacts.consumeSelection(capture.context.sourcePackage, capture.title)
                        ?: currentContact?.entity?.id?.takeIf { oldKey == (capture.context.sourcePackage to capture.title) }
                    val contact = (resolution.matched.firstOrNull { it.entity.id == chosenId }
                        ?: resolution.matched.singleOrNull())?.takeIf { it.entity.confirmed }
                    val sameConfirmedPerson = contact != null && currentContact?.entity?.id == contact.entity.id && oldKey?.first == capture.context.sourcePackage
                    if ((!sameConfirmedPerson && oldKey != (capture.context.sourcePackage to capture.title)) || currentContact?.entity?.id != contact?.entity?.id) {
                        withContext(Dispatchers.Main) {
                            cancelAutomaticGeneration()
                            incomingDetector.reset()
                            automaticRounds.clear()
                            overlay.clearCachedQuick()
                            overlay.setQuickInstruction("")
                        }
                    }
                    currentCapture = capture
                    currentContact = contact
                    currentResolution = resolution
                    if (contact != null) historyWriter.submit(contact.entity.id, capture, historyEpoch)
                    // New-message detection uses the visible frame, not a disk/crypto pass.
                    app.nextsay.history.HistoryLoadResult(capture.context.messages, false)
                }
            },
            isPackageActive = { packageName ->
                activePackage == packageName && resolveForegroundApplicationPackage() == packageName
            },
            viewportRevision = { scrollRevisionTracker.revision },
            onStage = { stage ->
                if (stage == "capture_start") {
                    captureNodeCount = null; captureTextCount = null
                    captureApp = if (activePackage == WECHAT_PACKAGE) "wechat" else "qq"
                }
                captureStage = stage
            },
            onDuration = { stage, duration ->
                val event = dependencies.diagnosticEventFactory.create(
                    DiagnosticEventType.CAPTURE_TIMING, DiagnosticSurface.OVERLAY,
                    durationMillis = duration, captureStage = stage, captureApp = captureApp,
                    keyboardVisible = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD },
                )
                scope.launch(Dispatchers.IO) { dependencies.diagnostics.record(event) }
            },
        )
        quickReplyFlow = QuickReplyFlow(
            controller = controller,
            capture = { obtainFreshContext(it, forceRefresh = true) },
            setInstruction = overlay::setQuickInstruction,
            dismiss = overlay::hideQuick,
            captureError = { message ->
                if (message == "正在读取对话，请稍候") overlay.showQuickError(message)
                else showCaptureFailure(DiagnosticSurface.OVERLAY)
            },
            prepare = ::prepareGenerationContext,
            retainNewRoundInstruction = { retainInstruction.also { retainInstruction = false } },
        )
        NextSayImeRuntime.session.registerHandler(imeGenerationHandler)
        if (!screenReceiverRegistered) {
            val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        }
        scope.launch {
            controller.state.collectLatest { state ->
                when (generationPresentationPolicy.destination(generationSurface)) {
                    GenerationDestination.QUICK_WINDOW -> overlay.renderQuick(state)
                    GenerationDestination.ADVANCED_PANEL -> overlay.renderAdvanced(state)
                    GenerationDestination.IME_ONLY -> overlay.hideAllContent()
                }
            }
        }
        // Low-frequency local safety net for missing/coalesced accessibility events.
        // Never captures other apps, a locked screen, disabled profiles or editor contents.
        automaticWatchJob = scope.launch {
            while (true) {
                delay(4_000L)
                if (!isUnlockedInteractive()) continue
                val resolved = resolveForegroundApplicationPackage()
                val pkg = (activePackage ?: resolved)?.takeIf { it in SUPPORTED_PACKAGES }
                    ?: continue
                val keyboardTransition = resolved == null &&
                    windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                if (resolved != pkg && !keyboardTransition) continue
                if (activePackage == null) activePackage = pkg
                // Also repairs a window removed by a ROM without a matching
                // accessibility event, including the IME transition case.
                overlay.setSupportedAppActive(true)
                if (contacts.totalPaused || (overlay.isEditing && !overlay.isQuickOpen) || quickRequestJob?.isActive == true ||
                    imeGenerationJob?.isActive == true || advancedGenerationJob?.isActive == true || autoCaptureRunning) continue
                val enabled = try { withContext(Dispatchers.IO) { currentContact?.entity?.id?.let { contacts.get(it)?.entity?.autoEnabled } == true } }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                if (enabled) scheduleCurrentRefresh()
            }
        }
        // A service can be rebound while a supported app is already in the
        // foreground. In that case no new window-state event is guaranteed;
        // synthesize one after the accessibility window list settles so the
        // trigger is created immediately.
        scope.launch {
            repeat(12) { attempt ->
                delay(if (attempt == 0) 250L else 500L)
                if (synchronizeCurrentForeground()) return@launch
            }
        }
    }

    private fun synchronizeCurrentForeground(): Boolean {
        if (!::overlay.isInitialized) return false
        val packageName = resolveForegroundApplicationPackage()
            ?.takeIf { it in SUPPORTED_PACKAGES }
            ?: return false
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = packageName
        return try {
            onAccessibilityEvent(event)
            true
        } finally {
            event.recycle()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::overlay.isInitialized) return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName && resolveForegroundApplicationPackage() in SUPPORTED_PACKAGES) return
        val interactive = isUnlockedInteractive()
        if (!interactive) {
            cancelAutoRefresh()
            cancelQuickRequest()
            cancelAutomaticGeneration()
            cancelImeGeneration()
            cancelAdvancedGeneration()
            controller.stopPendingGeneration()
            incomingDetector.reset()
            automaticRounds.clear()
            latestContextCache.clear()
            return
        }
        val defaultImePackage = defaultInputMethodPackage()
        // A supported-app or IME event invalidates any stale delayed teardown,
        // even when the foreground policy ignores the event while a panel is open.
        if (packageName in SUPPORTED_PACKAGES || packageName == defaultImePackage) {
            cancelUnsupportedTeardown()
        }
        if (
            foregroundEventPolicy.shouldIgnore(
                eventPackage = packageName,
                defaultImePackage = defaultImePackage,
                panelOpen = overlay.isAnyContentOpen,
            )
        ) {
            return
        }
        val resolvedForegroundPackage = resolveForegroundApplicationPackage()
        if (foregroundEventPolicy.shouldKeepActiveDuringTransientWindow(
                eventPackage = packageName,
                resolvedForeground = resolvedForegroundPackage,
                activePackage = activePackage,
                ownPackage = applicationContext.packageName,
                defaultImePackage = defaultImePackage,
                supportedPackages = SUPPORTED_PACKAGES,
            )) {
            // Xiaomi and some other ROMs briefly expose only the IME/system
            // window while the chat input is opening. Keep the trigger and
            // current candidates until the real application window returns.
            // The ROM may have removed the accessibility window during the
            // same transition, so reconcile the actual attachment as well.
            cancelUnsupportedTeardown()
            overlay.setSupportedAppActive(true)
            return
        }
        val foregroundPackage = foregroundWindowResolver.resolveEventPackage(
            resolvedPackage = resolvedForegroundPackage,
            eventPackage = packageName,
            supportedPackages = SUPPORTED_PACKAGES,
        )
        val supported = foregroundPackage in SUPPORTED_PACKAGES
        Log.d("NextSayForeground", "event=$packageName type=${event.eventType} resolved=$resolvedForegroundPackage foreground=$foregroundPackage active=$activePackage supported=$supported ime=$defaultImePackage")
        if (supported) {
            cancelUnsupportedTeardown()
            val supportedPackage = foregroundPackage!!
            val supportedPackageChanged = activePackage != supportedPackage
            if (supportedPackageChanged) {
                cancelQuickRequest()
                cancelImeGeneration()
                cancelAdvancedGeneration()
                cancelAutomaticGeneration()
                incomingDetector.reset()
                automaticRounds.clear()
                currentContact = null
                currentCapture = null
                scrollRevisionTracker.resetList()
                controller.onActivePackageChanged(supportedPackage)
                latestContextCache.clear()
            }
            activePackage = supportedPackage
            overlay.setSupportedAppActive(true)
            val isForegroundEvent = packageName == supportedPackage
            if (isForegroundEvent && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !supportedPackageChanged) {
                // A keyboard/layout window event is not proof of changing person.
                // Re-read the chat; confirmed identity changes are handled by mergeHistory.
                cancelQuickRequest()
                cancelImeGeneration()
                cancelAdvancedGeneration()
                // Keep the current automatic HTTP request across keyboard/window layout
                // changes. Its result is checked against a fresh frame before display.
                if (automaticGenerationJob?.isActive != true) controller.beginContextCheck()
            }
            if (isForegroundEvent && event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                scrollRevisionTracker.onScroll(event.itemCount, visibleKeyboardTop())
            }
            if (isForegroundEvent && foregroundEventPolicy.shouldInvalidateContext(event.eventType, supportedPackageChanged)) {
                latestContextCache.markPageChanged(supportedPackage)
                if (overlay.isEditing) editorChanged = true
            }
            // The quick candidate window is presentation only. It must not stop
            // the automatic observer; only the instruction editor/menu/advanced
            // panel are editing surfaces that intentionally defer generation.
            if (overlay.isEditing && !overlay.isQuickOpen && !autoCaptureRunning) {
                cancelAutoRefresh()
            }
            if (
                foregroundEventPolicy.shouldScheduleRefresh(
                    eventPackage = packageName,
                    foregroundPackage = supportedPackage,
                    ownPackage = applicationContext.packageName,
                    defaultImePackage = defaultImePackage,
                    supportedPackages = SUPPORTED_PACKAGES,
                    interactive = interactive,
                    eventType = event.eventType,
                    contentOpen = (overlay.isEditing && !overlay.isQuickOpen) || quickRequestJob?.isActive == true || imeGenerationJob?.isActive == true,
                    supportedPackageChanged = supportedPackageChanged || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                )
            ) {
                scheduleAutoRefresh(
                    autoRefreshScheduler.onPageChanged(supportedPackage, SystemClock.elapsedRealtime()),
                )
            }
        } else {
            // Xiaomi briefly reports the launcher or an unknown window while
            // attaching the IME. Defer teardown until the foreground remains
            // unsupported after the transition settles.
            unsupportedPackageJob?.cancel()
            val teardownToken = ++unsupportedTeardownToken
            unsupportedPackageJob = scope.launch {
                delay(350L)
                if (teardownToken != unsupportedTeardownToken) return@launch
                val settled = resolveForegroundApplicationPackage()
                val keyboardTransition = settled == null &&
                    windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                if (settled in SUPPORTED_PACKAGES || keyboardTransition) return@launch
                if (teardownToken != unsupportedTeardownToken) return@launch
                cancelQuickRequest()
                cancelImeGeneration()
                cancelAdvancedGeneration()
                cancelAutoRefresh()
                cancelAutomaticGeneration()
                incomingDetector.reset()
                automaticRounds.clear()
                currentContact = null
                currentCapture = null
                latestContextCache.clear()
                activePackage = null
                controller.dismiss()
                overlay.setSupportedAppActive(false)
            }
        }
    }

    override fun onInterrupt() = Unit

    fun captureOnUserRequest(): ChatContext? {
        val expectedPackage = activePackage?.takeIf { it in SUPPORTED_PACKAGES }
        if (expectedPackage == null) return null
        return captureAccessibilityConversation(expectedPackage)?.context
    }

    private fun captureAccessibilityConversation(expectedPackage: String): CapturedConversation? {
        captureStage = "accessibility_root"
        val root = findSupportedRoot(expectedPackage)
        if (root == null) return null
        return try {
            captureStage = "accessibility_nodes"
            val nodes = flattener.flatten(root)
            captureNodeCount = nodes.size
            if (nodes.any { it.password }) {
                captureStage = "password_blocked"
                return null
            }
            captureStage = "accessibility_parse"
            val context = normalizer.normalize(expectedPackage, nodes, resources.displayMetrics.widthPixels)
            val title = titleExtractor.extract(expectedPackage, nodes)
            context.takeIf { it.messages.isNotEmpty() }?.let {
                CapturedConversation(
                    title = title ?: "当前会话",
                    context = it,
                    persistable = title != null,
                ).also {
                    scrollRevisionTracker.onConversation(expectedPackage, it.title, visibleKeyboardTop())
                    scrollRevisionTracker.onFrame(it.context.messages)
                }
            }
        } finally {
            recycleNode(root)
        }
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) { unregisterReceiver(screenReceiver); screenReceiverRegistered = false }
        cancelImeGeneration()
        cancelAdvancedGeneration()
        automaticGenerationJob?.cancel()
        automaticWatchJob?.cancel()
        unsupportedPackageJob?.cancel()
        automaticRounds.clear()
        cancelAutoRefresh()
        latestContextCache.clear()
        NextSayImeRuntime.session.unregisterHandler(imeGenerationHandler)
        if (::overlay.isInitialized) overlay.dispose()
        if (::ocrEngine.isInitialized) ocrEngine.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun runQuickReply() {
        if (overlay.isQuickOpen) {
            if (currentContact?.entity?.autoEnabled == true) overlay.suppressQuick()
            else { dismissQuickReply(); overlay.suppressQuick() }
            return
        }
        if (currentContact?.entity?.autoEnabled == true) {
            overlay.showCachedQuick()
            return
        }
        if (quickRequestJob?.isActive == true || controller.state.value is app.nextsay.overlay.OverlayState.Loading) {
            dismissQuickReply()
            overlay.hideQuick()
            return
        }
        requestQuickReply(overlay.quickInstructionText, collapseIfUnchanged = false)
    }

    private fun detectorKey(context: ChatContext): String =
        "${context.sourcePackage}|${contacts.accountSpace}|${currentContact?.entity?.id ?: context.replyRound?.title.orEmpty()}"

    private suspend fun prepareGenerationContext(context: ChatContext, includeMemory: Boolean = false): ChatContext {
        return withContext(Dispatchers.IO) {
            val selectedId = currentContact?.entity?.id?.takeIf {
                context.sourcePackage == currentContact?.entity?.sourcePackage && context.replyRound?.title == currentCapture?.title
            }
            val prepared = if (includeMemory) contacts.prepareContext(context, selectedId) else {
                val person = selectedId?.let { contacts.get(it) }?.takeIf {
                    it.entity.confirmed && it.entity.sourcePackage == context.sourcePackage && it.entity.accountSpace == contacts.accountSpace
                }
                context.copy(contactId = person?.entity?.id,
                    generationExtras = person?.let { contacts.extras(it.entity.id, context, includeMemory = false) })
            }
            currentContact = prepared.contactId?.let { contacts.get(it) }
            prepared
        }
    }

    private fun scheduleCurrentRefresh() {
        val pkg = activePackage ?: return
        if (overlay.isEditing && !overlay.isQuickOpen) return
        latestContextCache.markPageChanged(pkg)
        scheduleAutoRefresh(autoRefreshScheduler.onPageChanged(pkg, SystemClock.elapsedRealtime()))
    }

    private fun cancelAutomaticGeneration() {
        if (automaticGenerationJob?.isActive == true) {
            automaticGenerationJob?.cancel()
            controller.stopPendingGeneration()
            ++busyEpoch
            overlay.setBusy(false)
            overlay.showGenerationCancelled()
        }
        automaticGenerationJob = null
        automaticTicket?.let(automaticRounds::defer)
        automaticTicket = null
    }

    private suspend fun processAutomaticContext(context: ChatContext) {
        val revision = context.replyRound?.viewportRevision ?: 0L
        if (automaticViewportRevision != null && automaticViewportRevision != revision) {
            cancelAutomaticGeneration()
            automaticRounds.clear()
        }
        automaticViewportRevision = revision
        val incoming = incomingDetector.observe(detectorKey(context), context)
        // Keep manual rewrites on the same round as background observations.
        quickReplyFlow.observe(context.copy(contactId = currentContact?.entity?.id))
        if (currentContact?.entity?.autoEnabled != true || contacts.totalPaused) {
            automaticRounds.clear()
            return
        }
        if (context.replyRound?.tailObscured == true) {
            recordAutomaticState("tail_obscured")
            if (automaticGenerationJob?.isActive != true) overlay.showAutomaticStatus("最新消息被悬浮窗遮挡，请拖开一点；会继续检查")
            return
        }
        if (incoming) {
            automaticRounds.offer(context)
            overlay.setQuickInstruction("")
            cancelAutomaticGeneration()
        }
        if (!automaticRounds.hasPending) {
            val last = context.replyRound?.visibleMessages?.lastOrNull()
            if (last?.role == app.nextsay.context.MessageRole.UNKNOWN || (last?.confidence ?: 1f) < MIN_AUTO_ROLE_CONFIDENCE) {
                recordAutomaticState("uncertain_tail")
                if (automaticGenerationJob?.isActive != true) overlay.showAutomaticStatus("正在确认最新消息归属；不会盲目生成")
            } else recordAutomaticState("baseline_or_unchanged")
            return
        }
        val contact = currentContact ?: return
        if (!contact.entity.autoEnabled || contacts.totalPaused || (overlay.isEditing && !overlay.isQuickOpen) ||
            quickRequestJob?.isActive == true || imeGenerationJob?.isActive == true || advancedGenerationJob?.isActive == true ||
            automaticGenerationJob?.isActive == true || context.messages.lastOrNull()?.role !in
                setOf(app.nextsay.context.MessageRole.OTHER, app.nextsay.context.MessageRole.ME) ||
            activePackage != context.sourcePackage || resolveForegroundApplicationPackage() != context.sourcePackage ||
            !isUnlockedInteractive()) {
            recordAutomaticState(if (overlay.isEditing && !overlay.isQuickOpen) "pending_editor" else "pending_busy")
            return
        }
        if (nextSayDependencies.providerConfigStore.load() == null) return
        cancelAutomaticGeneration()
        val prepared = prepareGenerationContext(context)
        if (prepared.contactId != contact.entity.id) return
        val ticket = automaticRounds.begin(prepared) ?: return
        automaticTicket = ticket
        recordAutomaticState("generating")
        val pkg = context.sourcePackage
        val key = detectorKey(context)
        generationSurface = GenerationSurface.QUICK
        if (!overlay.isQuickOpen) overlay.suppressQuick()
        val epoch = ++busyEpoch
        // Automatic generation runs in the background. It must not disable the
        // manual "生成新回复" entry while confirmation or generation is pending.
        automaticGenerationJob = scope.launch {
            try {
                val freshContact = withContext(Dispatchers.IO) { contacts.get(contact.entity.id) }
                if (freshContact?.entity?.autoEnabled != true || contacts.totalPaused ||
                    activePackage != pkg || detectorKey(context) != key || (overlay.isEditing && !overlay.isQuickOpen) ||
                    resolveForegroundApplicationPackage() != pkg || !isUnlockedInteractive()) return@launch
                controller.showPreview(prepared)
                controller.generate("", surface = DiagnosticSurface.OVERLAY)
                // Success or API error is terminal; no automatic paid retry loop.
                automaticRounds.finish(ticket)
                recordAutomaticState(if (controller.state.value is app.nextsay.overlay.OverlayState.Results) "generated" else "generation_failed")
            } finally {
                automaticRounds.defer(ticket)
                if (automaticTicket?.epoch == ticket.epoch) automaticTicket = null
                if (epoch == busyEpoch) overlay.setBusy(false)
            }
        }
    }

    private fun recordAutomaticState(state: String) {
        if (lastAutomaticState == state) return
        lastAutomaticState = state
        nextSayDependencies.diagnostics.record(nextSayDependencies.diagnosticEventFactory.create(
            DiagnosticEventType.AUTOMATIC_STATE, DiagnosticSurface.OVERLAY,
            automaticState = state,
            keyboardVisible = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD },
            captureBottom = captureBottom,
            pendingIncoming = automaticRounds.hasPending,
            visibleMessageCount = currentCapture?.context?.replyRound?.visibleMessages?.size
                ?: currentCapture?.context?.messages?.size,
            latestRole = (currentCapture?.context?.replyRound?.visibleMessages?.lastOrNull()
                ?: currentCapture?.context?.messages?.lastOrNull())?.role?.name,
            latestConfidence = (currentCapture?.context?.replyRound?.visibleMessages?.lastOrNull()
                ?: currentCapture?.context?.messages?.lastOrNull())?.confidence,
        ))
    }

    private fun openContactMenu() {
        cancelAutoRefresh()
        cancelAutomaticGeneration()
        val capture = currentCapture
        val pkg = activePackage
        if (capture == null) { showContactMenu(); return }
        scope.launch {
            val resolution = withContext(Dispatchers.IO) { contacts.menuResolution(capture) }
            if (activePackage != pkg || currentCapture?.title != capture.title || resolveForegroundApplicationPackage() != pkg) return@launch
            if (resolution == null) {
                currentContact = null
                currentResolution = null
                overlay.showMenu("对象资料读取失败，请在对象管理中检查。未使用旧关系或资料。", listOf("对象管理" to { openContactSettings() }))
                return@launch
            }
            val selected = currentContact?.entity?.id
            currentContact = (resolution.matched.firstOrNull { it.entity.id == selected } ?: resolution.matched.singleOrNull())?.takeIf { it.entity.confirmed }
            currentResolution = resolution
            showContactMenu()
        }
    }
    private fun showContactMenu() {
        val capture = currentCapture
        val contact = currentContact
        val title = capture?.title ?: "尚未识别对象（请先手动生成一次）"
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (contact != null) {
            actions += (if (contact.entity.autoEnabled) "关闭此对象自动生成" else "开启此对象自动生成（仅候选）") to {
                if (!contact.entity.autoEnabled) {
                    overlay.showMenu("相关聊天与资料将发送到配置的 API，可能产生费用，不自动发送。", listOf("同意并开启" to { setAutomatic(contact, true) }))
                } else setAutomatic(contact, false)
            }
        } else {
            actions += "保存或选择聊天对象" to { openContactSettings() }
            currentResolution?.suggested?.take(3)?.forEach { candidate ->
                actions += "这是“${candidate.name}”吗？" to { openContactSettings(candidate.entity.id) }
            }
        }
        if (contact != null) actions += "编辑对象资料" to { openContactSettings(contact.entity.id) }
        actions += "更多回复选项" to { openAdvancedPanel() }
        val modeLabel = if (contact?.entity?.relationship == "lover") " · ${app.nextsay.provider.LoverReplyModes.label(contact.entity.replyMode)}风格" else ""
        overlay.showMenu("$title\n${if (contact == null) "未保存对象，只参考当前聊天" else app.nextsay.ui.RelationshipChoices.label(contact.entity.relationship)}$modeLabel", actions)
    }

    private fun setAutomatic(contact: ContactSnapshot, enabled: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val fresh = contacts.get(contact.entity.id) ?: return@withContext
                contacts.save(fresh.copy(entity = fresh.entity.copy(autoEnabled = enabled)))
            }
            currentContact = withContext(Dispatchers.IO) { contacts.get(contact.entity.id) }
            if (!enabled) cancelAutomaticGeneration()
            incomingDetector.reset() // Enabling establishes a baseline, never replays old messages.
            automaticRounds.clear()
            scheduleCurrentRefresh()
        }
    }

    private fun openContactSettings(id: String? = null) {
        val pkg = activePackage ?: return
        startActivity(Intent(this, ContactSettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("package", pkg)
            putExtra("title", currentCapture?.title.orEmpty())
            if (id != null) putExtra("contactId", id)
        })
    }

    private fun dismissQuickReply() {
        cancelQuickRequest()
        cancelAdvancedGeneration()
        controller.dismiss()
    }

    private fun cancelQuickRequest() {
        busyEpoch++
        val running = quickRequestJob?.isActive == true
        quickRequestJob?.cancel()
        quickRequestJob = null
        if (running) {
            controller.stopPendingGeneration()
            overlay.showGenerationCancelled()
        }
        overlay.setBusy(false)
    }

    private fun requestQuickReply(instruction: String, collapseIfUnchanged: Boolean) {
        val packageName = activePackage?.takeIf { it in SUPPORTED_PACKAGES } ?: return
        if (resolveForegroundApplicationPackage() != packageName) return
        generationSurface = GenerationSurface.QUICK
        cancelImeGeneration()
        cancelAdvancedGeneration()
        cancelAutomaticGeneration()
        missingConfiguration(DiagnosticSurface.OVERLAY)?.let { failure ->
            overlay.showQuickError(missingConfigurationMessage(), failure.diagnosticId)
            return
        }
        val requestEpoch = ++busyEpoch
        overlay.setBusy(true)
        overlay.showQuickReading()
        quickRequestJob = scope.launch {
            try {
                quickReplyFlow.request(packageName, instruction, collapseIfUnchanged)
                latestContextCache.fresh(packageName)?.let { context ->
                    incomingDetector.observe(detectorKey(context), context)
                    // Manual generation establishes the current visible frame as
                    // the baseline, but must not erase a later incoming round
                    // already queued while the request was running.
                    if (!automaticRounds.hasPending) automaticRounds.clear()
                }
            } finally {
                if (requestEpoch == busyEpoch) {
                    overlay.setBusy(false)
                    if (latestContextCache.isDirty(packageName)) scheduleCurrentRefresh()
                }
            }
        }
    }

    private fun regenerateQuickReply(instruction: String) {
        if (quickRequestJob?.isActive == true) return
        requestQuickReply(instruction, collapseIfUnchanged = false)
    }

    private fun openAdvancedPanel(refresh: Boolean = false) {
        val packageName = activePackage?.takeIf { it in SUPPORTED_PACKAGES } ?: return
        cancelQuickRequest()
        cancelImeGeneration()
        cancelAdvancedGeneration()
        cancelAutomaticGeneration()
        generationSurface = GenerationSurface.ADVANCED
        missingConfiguration(DiagnosticSurface.OVERLAY)?.let { failure ->
            overlay.showQuickError(missingConfigurationMessage(), failure.diagnosticId)
            return
        }
        val keepPanel = refresh && overlay.isPanelOpen
        val captureTicket = if (keepPanel) controller.beginContextCheck() else controller.beginCapture()
        overlay.hideQuick()
        val requestEpoch = ++busyEpoch
        overlay.setBusy(true)
        advancedGenerationJob = scope.launch {
            try {
                val result = obtainFreshContext(packageName)
                if (!controller.isCaptureCurrent(captureTicket)) return@launch
                when (result) {
                    is ContextCaptureResult.Success -> controller.completeCapture(captureTicket, prepareGenerationContext(result.context))
                    is ContextCaptureResult.CaptureError ->
                        if (keepPanel) controller.refresh(null) else showCaptureFailure(DiagnosticSurface.OVERLAY)
                    ContextCaptureResult.Busy -> Toast.makeText(
                        this@NextSayAccessibilityService,
                        "正在读取对话，请稍候",
                        Toast.LENGTH_SHORT,
                    ).show()
                    ContextCaptureResult.Cancelled -> Unit
                }
            } finally {
                if (requestEpoch == busyEpoch) overlay.setBusy(false)
            }
        }
    }

    private suspend fun generateForIme(targetPackage: String): Result<List<ReplyCandidate>> {
        missingConfiguration(DiagnosticSurface.IME)?.let { return Result.failure(it) }
        if (activePackage != targetPackage || resolveForegroundApplicationPackage() != targetPackage || !isUnlockedInteractive()) {
            return Result.failure(recordFailure(ProviderErrorCode.CAPTURE_FAILED, DiagnosticSurface.IME, includeCaptureDetails = false))
        }
        generationSurface = GenerationSurface.IME
        cancelQuickRequest()
        cancelAdvancedGeneration()
        cancelAutomaticGeneration()
        controller.dismiss()
        overlay.hideAllContent()
        val requestEpoch = ++busyEpoch
        val requestJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        imeGenerationJob = requestJob
        overlay.setBusy(true)
        return try {
            when (val result = obtainFreshContext(targetPackage)) {
                is ContextCaptureResult.Success -> {
                    val response = repository.generate(
                        context = prepareGenerationContext(result.context, includeMemory = true),
                        instruction = "",
                        relationship = "",
                        surface = DiagnosticSurface.IME,
                    )
                    if (requestEpoch != busyEpoch || latestContextCache.isDirty(targetPackage) ||
                        resolveForegroundApplicationPackage() != targetPackage || !isUnlockedInteractive()) {
                        Result.failure(recordFailure(ProviderErrorCode.CAPTURE_FAILED, DiagnosticSurface.IME, includeCaptureDetails = false))
                    } else response
                }
                is ContextCaptureResult.CaptureError -> Result.failure(
                    recordFailure(ProviderErrorCode.CAPTURE_FAILED, DiagnosticSurface.IME),
                )
                ContextCaptureResult.Busy -> Result.failure(IllegalStateException("正在处理上一次请求，请稍候"))
                ContextCaptureResult.Cancelled -> Result.failure(
                    recordFailure(ProviderErrorCode.CAPTURE_FAILED, DiagnosticSurface.IME),
                )
            }
        } finally {
            if (imeGenerationJob === requestJob) imeGenerationJob = null
            if (requestEpoch == busyEpoch) {
                overlay.setBusy(false)
                if (latestContextCache.isDirty(targetPackage)) scheduleCurrentRefresh()
            }
        }
    }

    private suspend fun obtainFreshContext(packageName: String, forceRefresh: Boolean = false): ContextCaptureResult {
        if (forceRefresh) latestContextCache.markPageChanged(packageName)
        latestContextCache.fresh(packageName)?.let { return ContextCaptureResult.Success(it) }
        if (!latestContextCache.isDirty(packageName)) latestContextCache.markPageChanged(packageName)
        autoRefreshJob?.cancelAndJoin()
        autoRefreshJob = null
        autoRefreshScheduler.cancel()
        val ticket = latestContextCache.beginCapture(packageName) ?: return ContextCaptureResult.Busy
        autoRefreshScheduler.onCaptureStarted()
        if (packageName == WECHAT_PACKAGE) {
            autoRefreshScheduler.recordWechatOcrStarted(SystemClock.elapsedRealtime())
        }
        return try {
            when (val result = contextCoordinator.capture(packageName)) {
                is ContextCaptureResult.Success -> {
                    if (latestContextCache.complete(ticket, result.context)) result
                    else ContextCaptureResult.Cancelled
                }
                else -> {
                    latestContextCache.fail(ticket)
                    result
                }
            }
        } finally {
            latestContextCache.fail(ticket)
            val nextAt = autoRefreshScheduler.onCaptureFinished(
                SystemClock.elapsedRealtime(),
                packageName,
            )
            if (nextAt != null) scheduleAutoRefresh(nextAt)
        }
    }

    private fun scheduleAutoRefresh(dueAtMillis: Long) {
        if (autoCaptureRunning) return
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            delay((dueAtMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            runScheduledRefresh()
        }
    }

    private suspend fun runScheduledRefresh() {
        // A job queued before opening candidates must not hide the user's window.
        if ((overlay.isEditing && !overlay.isQuickOpen) || quickRequestJob?.isActive == true || imeGenerationJob?.isActive == true) {
            autoRefreshScheduler.cancel()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val packageName = autoRefreshScheduler.consumeDue(now) ?: return
        if (
            activePackage != packageName ||
            !isUnlockedInteractive()
        ) {
            cancelAutoRefresh()
            return
        }
        val ticket = latestContextCache.beginCapture(packageName) ?: return
        autoRefreshScheduler.onCaptureStarted()
        autoCaptureRunning = true
        if (packageName == WECHAT_PACKAGE) {
            autoRefreshScheduler.recordWechatOcrStarted(now)
        }
        try {
            when (val result = contextCoordinator.capture(packageName)) {
                is ContextCaptureResult.Success -> {
                    if (latestContextCache.complete(ticket, result.context)) processAutomaticContext(result.context)
                }
                else -> latestContextCache.fail(ticket)
            }
        } finally {
            latestContextCache.fail(ticket)
            autoCaptureRunning = false
            autoRefreshJob = null
            val nextAt = autoRefreshScheduler.onCaptureFinished(
                SystemClock.elapsedRealtime(),
                packageName,
            )
            if (nextAt != null) scheduleAutoRefresh(nextAt)
        }
    }

    private fun cancelAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        autoRefreshScheduler.cancel()
    }

    private suspend fun captureWithOcr(packageName: String): CapturedConversation? {
        return run {
            // Let an already-visible view finish layout; never hide/re-add it.
            delay(CAPTURE_SETTLE_MILLIS)
            val before = overlay.captureExclusions()
            // WeChat message text still comes from OCR; accessibility supplies attachment geometry only.
            val mediaNodes = if (packageName == WECHAT_PACKAGE) {
                findSupportedRoot(packageName)?.let { root ->
                    try { flattener.flatten(root) } finally { recycleNode(root) }
                }.orEmpty()
            } else emptyList()
            if (mediaNodes.any { it.password }) { captureStage = "password_blocked"; return null }
            captureStage = "screenshot"
            val screenshot = screenshotSource.capture() ?: run { captureStage = "screenshot_busy"; return null }
            val bitmap = screenshot.getOrThrow()
            try {
                val after = overlay.captureExclusions()
                // Geometry belongs to the frame; reject if the user moved/closed a view.
                if (before != after) { captureStage = "overlay_moved"; return null }
                val exclusions = before
                val contentBottom = resolveChatContentBottom(bitmap.height, mediaNodes)
                captureBottom = contentBottom
                val bubbles = if (packageName == WECHAT_PACKAGE) {
                    withContext(Dispatchers.Default) {
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        for (rect in exclusions) {
                            for (y in rect.top.coerceAtLeast(0) until rect.bottom.coerceAtMost(bitmap.height)) {
                                for (x in rect.left.coerceAtLeast(0) until rect.right.coerceAtMost(bitmap.width)) pixels[y * bitmap.width + x] = Color.rgb(237, 237, 237)
                            }
                        }
                        val contentTop = (bitmap.height * 0.09f).toInt()
                        val labelledMedia = WechatMediaDetector().detect(mediaNodes, bitmap.width, contentTop, contentBottom)
                        val geometryMedia = WechatMediaGeometry().detect(pixels, bitmap.width, bitmap.height, contentTop, contentBottom,
                            exclusions.map { app.nextsay.context.ScreenRect(it.left,it.top,it.right,it.bottom) })
                            .filter { geometry -> labelledMedia.none { labelled -> Rect.intersects(
                                Rect(geometry.bounds.left, geometry.bounds.top, geometry.bounds.right, geometry.bounds.bottom),
                                Rect(labelled.bounds.left, labelled.bounds.top, labelled.bounds.right, labelled.bounds.bottom)) } }
                        WechatBubbleDetector().detect(pixels, bitmap.width, bitmap.height, contentTop, contentBottom) + labelledMedia + geometryMedia
                    }
                } else null
                captureStage = "ocr_recognize"
                val blocks = ocrEngine.recognize(bitmap).getOrThrow().filter { block ->
                    CaptureTextVisibility.keep(
                        app.nextsay.context.ScreenRect(block.bounds.left, block.bounds.top, block.bounds.right, block.bounds.bottom),
                        exclusions.map { app.nextsay.context.ScreenRect(it.left, it.top, it.right, it.bottom) },
                    )
                }
                val sourceApp = if (packageName == "com.tencent.mm") "wechat" else "qq"
                captureTextCount = blocks.size
                captureStage = "ocr_parse"
                val captured = ocrParser.parse(
                    blocks = blocks,
                    screenWidth = bitmap.width,
                    screenHeight = bitmap.height,
                    contentBottom = contentBottom,
                    sourcePackage = packageName,
                    sourceApp = sourceApp,
                    bubbles = bubbles,
                )
                if (captured == null && exclusions.any { Rect.intersects(it,
                        Rect((bitmap.width * .30f).toInt(), (bitmap.height * .025f).toInt(),
                            (bitmap.width * .70f).toInt(), (bitmap.height * .09f).toInt())) }) {
                    captureStage = "header_obscured"
                }
                val latestAvatar = mediaNodes.filter { node ->
                    val r = node.bounds
                    (node.contentDescription.orEmpty().contains("头像") || node.className?.endsWith("ImageView") == true) &&
                        r.top >= (bitmap.height*.09f).toInt() && r.bottom <= contentBottom &&
                        r.bottom-r.top in (bitmap.width*.05f).toInt()..(bitmap.width*.15f).toInt() &&
                        (r.right <= bitmap.width*.14f || r.left >= bitmap.width*.86f)
                }.maxByOrNull { it.bounds.top }
                val tail = latestAvatar?.let { avatar -> CaptureVisibility.latestTail(avatar.bounds, bitmap.width, contentBottom,
                    bubbles.orEmpty().filter { !it.isMedia }.map { it.bounds }) }
                // Only an actual attachment control can exempt a covered image. A
                // partially masked pixel rectangle cannot establish attachment identity.
                val confirmedMedia = if (packageName == WECHAT_PACKAGE) WechatMediaDetector().detect(
                    mediaNodes, bitmap.width, (bitmap.height*.09f).toInt(), contentBottom) else emptyList()
                val latestIsMedia = confirmedMedia.any { it.role != app.nextsay.context.MessageRole.UNKNOWN &&
                    latestAvatar != null && kotlin.math.abs(it.bounds.top-latestAvatar.bounds.top) < bitmap.width*.04f }
                val reliableTailRole = captured?.context?.messages?.lastOrNull()?.let { message ->
                    message.role != app.nextsay.context.MessageRole.UNKNOWN && message.confidence >= MIN_AUTO_ROLE_CONFIDENCE
                } == true
                val blocked = tail != null && !latestIsMedia && CaptureVisibility.blocked(
                    tail,
                    exclusions.map { app.nextsay.context.ScreenRect(it.left, it.top, it.right, it.bottom) },
                    reliableRole = reliableTailRole,
                )
                captured?.copy(tailObscured = blocked)?.also {
                    scrollRevisionTracker.onConversation(packageName, it.title, visibleKeyboardTop())
                    if (!it.tailObscured) scrollRevisionTracker.onFrame(it.context.messages)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun insertCandidate(candidate: ReplyCandidate) {
        val state = controller.state.value
        val expectedPackage = when (state) {
            is app.nextsay.overlay.OverlayState.Results -> state.context.sourcePackage
            else -> return
        }
        if (!isCurrentCandidateContext((state as? app.nextsay.overlay.OverlayState.Results)?.context) || !insertionGate.tryStart()) return
        overlay.hidePanelForInsertion()
        scope.launch {
            delay(100)
            val foregroundPackage = resolveForegroundApplicationPackage()
            if (foregroundPackage != expectedPackage || !controller.hasActiveResultsFor(expectedPackage) ||
                !isCurrentCandidateContext((controller.state.value as? app.nextsay.overlay.OverlayState.Results)?.context)) {
                insertionGate.finish()
                return@launch
            }
            val result = try {
                val root = findSupportedRoot(expectedPackage)
                if (root == null) {
                    InsertResult.Failure(null)
                } else {
                    try {
                        inserter.insert(root, expectedPackage, foregroundPackage, candidate.text)
                    } finally {
                        recycleNode(root)
                    }
                }
            } catch (_: RuntimeException) {
                InsertResult.Failure(null)
            } finally {
                insertionGate.finish()
            }
            when (result) {
                is InsertResult.Success -> {
                    controller.dismiss()
                    Toast.makeText(this@NextSayAccessibilityService, "已写入输入框，请确认后手动发送", Toast.LENGTH_SHORT).show()
                }
                is InsertResult.Failure -> {
                    val failure = recordFailure(
                        ProviderErrorCode.INSERTION_FAILED,
                        DiagnosticSurface.OVERLAY,
                    )
                    val current = controller.state.value
                    if (current is app.nextsay.overlay.OverlayState.Results) {
                        overlay.render(
                            app.nextsay.overlay.OverlayState.Error(
                                current.context,
                                failure.message.orEmpty(),
                                failure.diagnosticId,
                            ),
                        )
                    }
                    overlay.showCopyFallback(candidate)
                }
            }
        }
    }

    private fun insertQuickCandidate(candidate: ReplyCandidate) {
        val state = controller.state.value
        val expectedPackage = (state as? app.nextsay.overlay.OverlayState.Results)
            ?.context
            ?.sourcePackage
            ?: return
        if (expectedPackage !in QUICK_INSERT_PACKAGES || !isCurrentCandidateContext((state as? app.nextsay.overlay.OverlayState.Results)?.context) || !insertionGate.tryStart()) return
        scope.launch {
            delay(100)
            val inserted = try {
                val foregroundPackage = resolveForegroundApplicationPackage()
                if (
                    foregroundPackage != expectedPackage ||
                    !controller.hasActiveResultsFor(expectedPackage)
                    || !isCurrentCandidateContext((controller.state.value as? app.nextsay.overlay.OverlayState.Results)?.context)
                ) return@launch
                val root = findSupportedRoot(expectedPackage)
                if (root == null) {
                    false
                } else {
                    try {
                        inserter.insert(root, expectedPackage, foregroundPackage, candidate.text) is InsertResult.Success
                    } finally {
                        recycleNode(root)
                    }
                }
            } catch (_: RuntimeException) {
                false
            } finally {
                insertionGate.finish()
            }
            if (!inserted) {
                val failure = recordFailure(
                    ProviderErrorCode.INSERTION_FAILED,
                    DiagnosticSurface.OVERLAY,
                )
                overlay.showQuickError(failure.message.orEmpty(), failure.diagnosticId)
            }
        }
    }

    private fun missingConfiguration(surface: DiagnosticSurface): ProviderException? =
        if (nextSayDependencies.providerConfigStore.load() == null) {
            recordFailure(ProviderErrorCode.CONFIG_MISSING, surface)
        } else {
            null
        }

    private fun isCurrentCandidateContext(context: ChatContext?): Boolean = context != null &&
        context.sourcePackage == activePackage && context.replyRound?.title == currentCapture?.title &&
        context.contactId == currentContact?.entity?.id && !latestContextCache.isDirty(context.sourcePackage)

    private fun missingConfigurationMessage() =
        "请先打开 NextSay 配置模型服务（${ProviderErrorCode.CONFIG_MISSING.wireCode}）"

    private fun showCaptureFailure(surface: DiagnosticSurface) {
        val failure = recordFailure(ProviderErrorCode.CAPTURE_FAILED, surface)
        val hint = when (captureStage) {
            "header_obscured" -> "聊天标题可能被悬浮窗遮挡，请把悬浮球向下拖动后重试（APP-CAPTURE）"
            "overlay_moved" -> "读取时悬浮窗正在移动，请松手后重试（APP-CAPTURE）"
            "screenshot_busy" -> "截图正在忙，请稍等一下再重试（APP-CAPTURE）"
            else -> failure.message.orEmpty()
        }
        overlay.showQuickError(hint, failure.diagnosticId)
    }

    private fun recordFailure(
        code: ProviderErrorCode,
        surface: DiagnosticSurface,
        includeCaptureDetails: Boolean = true,
    ): ProviderException {
        val dependencies = nextSayDependencies
        val type = when (code) {
            ProviderErrorCode.CAPTURE_FAILED -> DiagnosticEventType.CAPTURE_FAILED
            ProviderErrorCode.INSERTION_FAILED -> DiagnosticEventType.INSERTION_FAILED
            else -> DiagnosticEventType.GENERATION_FAILED
        }
        val event = dependencies.diagnosticEventFactory.create(
            type = type,
            surface = surface,
            errorCode = code.wireCode,
            captureApp = captureApp.takeIf { code == ProviderErrorCode.CAPTURE_FAILED && includeCaptureDetails },
            captureStage = captureStage.takeIf { code == ProviderErrorCode.CAPTURE_FAILED && includeCaptureDetails },
            captureNodeCount = captureNodeCount.takeIf { code == ProviderErrorCode.CAPTURE_FAILED && includeCaptureDetails },
            captureTextCount = captureTextCount.takeIf { code == ProviderErrorCode.CAPTURE_FAILED && includeCaptureDetails },
        )
        dependencies.diagnostics.record(event)
        return ProviderException(code, event.id)
    }

    private fun copyDiagnostics(diagnosticId: String) {
        val dependencies = nextSayDependencies
        val summary = dependencies.diagnostics.find(diagnosticId)
            ?.let(dependencies.diagnosticFormatter::compact)
            ?: buildString {
                appendLine("NextSay 诊断信息")
                appendLine("错误编号：$diagnosticId")
                val current = controller.state.value as? app.nextsay.overlay.OverlayState.Error
                append("错误：${current?.message ?: ProviderErrorCode.APP_INTERNAL.wireCode}")
            }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("NextSay 诊断信息", summary),
        )
        Toast.makeText(this, "诊断信息已复制", Toast.LENGTH_SHORT).show()
    }

    private fun findSupportedRoot(expectedPackage: String): android.view.accessibility.AccessibilityNodeInfo? {
        for (window in windows) {
            val root = window.root ?: continue
            val rootPackage = root.packageName?.toString()
            if (rootPackage == expectedPackage) return root
            recycleNode(root)
        }
        return null
    }

    private fun resolveChatContentBottom(screenHeight: Int, nodes: List<app.nextsay.context.NodeSnapshot> = emptyList()): Int {
        val keyboardTop = visibleKeyboardTop()
        val inputNodes = nodes.filter { it.editable && !it.password }
        // A visible IME with no exposed input node still ends the chat viewport
        // exactly at its top edge. The fallback guard is only for missing IME data.
        return if (keyboardTop != null && inputNodes.isEmpty()) keyboardTop
        else ChatViewportBounds.bottom(screenHeight, keyboardTop, nodes, INPUT_AREA_GUARD_DP.dp)
    }

    private fun visibleKeyboardTop(): Int? = windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { window ->
                val region = Region()
                window.getRegionInScreen(region)
                region.bounds.takeIf { !it.isEmpty }?.top
            }
            .minOrNull()

    private fun resolveForegroundApplicationPackage(): String? {
        val snapshots = windows.map { window ->
            val root = window.root
            val packageName = root?.packageName?.toString()
            if (root != null) recycleNode(root)
            WindowPackageSnapshot(
                isApplication = window.type == AccessibilityWindowInfo.TYPE_APPLICATION,
                layer = window.layer,
                packageName = packageName,
            )
        }
        return foregroundWindowResolver.resolve(snapshots)
    }

    private fun defaultInputMethodPackage(): String? {
        val flattened = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: return null
        return ComponentName.unflattenFromString(flattened)?.packageName
    }

    @Suppress("DEPRECATION")
    private fun recycleNode(node: android.view.accessibility.AccessibilityNodeInfo) = node.recycle()

    private companion object {
        const val CAPTURE_SETTLE_MILLIS = 80L
        const val INPUT_AREA_GUARD_DP = 72
        const val WECHAT_PACKAGE = "com.tencent.mm"
        val SUPPORTED_PACKAGES = setOf(
            WECHAT_PACKAGE,
            "com.tencent.mobileqq",
            "com.tencent.tim",
            "com.tencent.qqlite",
        )
        val QUICK_INSERT_PACKAGES = setOf(
            "com.tencent.mobileqq",
            "com.tencent.tim",
            "com.tencent.qqlite",
        )
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
