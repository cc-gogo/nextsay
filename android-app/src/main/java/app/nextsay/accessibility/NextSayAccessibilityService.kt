package app.nextsay.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.graphics.Region
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import app.nextsay.api.NextSayRepository
import app.nextsay.capture.AccessibilityScreenshotSource
import app.nextsay.capture.CapturedConversation
import app.nextsay.capture.AutoRefreshScheduler
import app.nextsay.capture.ContextCaptureResult
import app.nextsay.capture.ConversationContextCoordinator
import app.nextsay.capture.LatestContextCache
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
import app.nextsay.overlay.OverlayWindow
import app.nextsay.overlay.PanelCallbacks
import app.nextsay.overlay.ReplyCandidate
import app.nextsay.ocr.MlKitChineseOcrEngine
import app.nextsay.ocr.WechatOcrParser
import app.nextsay.privacy.TextRedactor
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.nextSayDependencies
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
    private val autoRefreshScheduler = AutoRefreshScheduler()
    private val imeGenerationHandler = ImeGenerationHandler(::generateForIme)
    private var activePackage: String? = null
    private var generationSurface = GenerationSurface.ADVANCED
    private lateinit var controller: OverlayController
    private lateinit var overlay: OverlayWindow
    private lateinit var ocrEngine: MlKitChineseOcrEngine
    private lateinit var screenshotSource: AccessibilityScreenshotSource
    private lateinit var contextCoordinator: ConversationContextCoordinator
    private var autoRefreshJob: Job? = null
    private var autoCaptureRunning = false

    override fun onServiceConnected() {
        Log.i(LOG_TAG, "service connected")
        val dependencies = nextSayDependencies
        val repository = NextSayRepository(
            configStore = dependencies.providerConfigStore,
            configValidator = dependencies.providerConfigValidator,
            client = dependencies.replyProviderClient,
            redactor = redactor,
            diagnostics = dependencies.diagnostics,
            eventFactory = dependencies.diagnosticEventFactory,
        )
        controller = OverlayController { context, instruction, relationship ->
            repository.generate(
                context,
                instruction,
                relationship,
                if (generationSurface == GenerationSurface.IME) {
                    DiagnosticSurface.IME
                } else {
                    DiagnosticSurface.OVERLAY
                },
            )
        }
        ocrEngine = MlKitChineseOcrEngine().also { it.warmUp() }
        screenshotSource = AccessibilityScreenshotSource(this)
        val historyRepository = ConversationHistoryRepository(
            NextSayDatabase.get(this).conversationDao(),
            AndroidKeystoreMessageCipher(),
        )
        overlay = OverlayWindow(
            service = this,
            callbacks = PanelCallbacks(
                onRefresh = ::openAdvancedPanel,
                onGenerate = { instruction, relationship ->
                    scope.launch { controller.generate(instruction, relationship) }
                },
                onRetry = { scope.launch { controller.retry() } },
                onCandidate = ::insertCandidate,
                onDismiss = controller::dismiss,
            ),
            onQuickTrigger = ::runQuickReply,
            onAdvancedTrigger = ::openAdvancedPanel,
            onQuickGenerate = ::regenerateQuickReply,
            onQuickInsert = ::insertQuickCandidate,
        )
        contextCoordinator = ConversationContextCoordinator(
            accessibilityCapture = ::captureAccessibilityConversation,
            ocrCapture = ::captureWithOcr,
            mergeHistory = { capture ->
                Log.i(LOG_TAG, "history merge started messages=${capture.context.messages.size}")
                try {
                    withContext(Dispatchers.IO) { historyRepository.mergeAndLoad(capture) }
                        .also { Log.i(LOG_TAG, "history merge completed messages=${it.messages.size} persisted=${it.persisted}") }
                } catch (error: Throwable) {
                    Log.e(LOG_TAG, "history merge failed type=${error.javaClass.simpleName}", error)
                    throw error
                }
            },
            isPackageActive = { packageName ->
                activePackage == packageName && resolveForegroundApplicationPackage() == packageName
            },
        )
        NextSayImeRuntime.session.registerHandler(imeGenerationHandler)
        scope.launch {
            controller.state.collectLatest { state ->
                when (generationPresentationPolicy.destination(generationSurface)) {
                    GenerationDestination.QUICK_WINDOW -> overlay.renderQuick(state)
                    GenerationDestination.ADVANCED_PANEL -> overlay.renderAdvanced(state)
                    GenerationDestination.IME_ONLY -> overlay.hideAllContent()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::overlay.isInitialized) return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive == true
        if (!interactive) {
            cancelAutoRefresh()
            return
        }
        if (
            foregroundEventPolicy.shouldIgnore(
                eventPackage = packageName,
                defaultImePackage = defaultInputMethodPackage(),
                panelOpen = overlay.isAnyContentOpen,
            )
        ) {
            Log.d(LOG_TAG, "ignoring current input method event while panel is open")
            return
        }
        val foregroundPackage = foregroundWindowResolver.resolveEventPackage(
            resolvedPackage = resolveForegroundApplicationPackage(),
            eventPackage = packageName,
            supportedPackages = SUPPORTED_PACKAGES,
        )
        val supported = foregroundPackage in SUPPORTED_PACKAGES
        Log.d(
            LOG_TAG,
            "event type=${event.eventType} source=$packageName foreground=$foregroundPackage supported=$supported",
        )
        if (supported) {
            val supportedPackage = foregroundPackage!!
            if (activePackage != supportedPackage) {
                controller.onActivePackageChanged(supportedPackage)
                latestContextCache.clear()
            }
            activePackage = supportedPackage
            overlay.setSupportedAppActive(true)
            if (
                foregroundEventPolicy.shouldScheduleRefresh(
                    eventPackage = packageName,
                    foregroundPackage = supportedPackage,
                    ownPackage = applicationContext.packageName,
                    defaultImePackage = defaultInputMethodPackage(),
                    supportedPackages = SUPPORTED_PACKAGES,
                    interactive = interactive,
                )
            ) {
                latestContextCache.markPageChanged(supportedPackage)
                scheduleAutoRefresh(
                    autoRefreshScheduler.onPageChanged(supportedPackage, SystemClock.elapsedRealtime()),
                )
            }
        } else {
            cancelAutoRefresh()
            latestContextCache.clear()
            activePackage = null
            controller.dismiss()
            overlay.setSupportedAppActive(false)
        }
    }

    override fun onInterrupt() = Unit

    fun captureOnUserRequest(): ChatContext? {
        val expectedPackage = activePackage?.takeIf { it in SUPPORTED_PACKAGES }
        if (expectedPackage == null) {
            Log.w(LOG_TAG, "capture rejected: no supported active package")
            return null
        }
        return captureAccessibilityConversation(expectedPackage)?.context
    }

    private fun captureAccessibilityConversation(expectedPackage: String): CapturedConversation? {
        val root = findSupportedRoot(expectedPackage)
        if (root == null) {
            Log.w(LOG_TAG, "capture rejected: no root for $expectedPackage")
            return null
        }
        return try {
            val nodes = flattener.flatten(root)
            if (nodes.any { it.password }) {
                Log.w(LOG_TAG, "capture rejected: password node present")
                return null
            }
            val context = normalizer.normalize(expectedPackage, nodes, resources.displayMetrics.widthPixels)
            Log.i(LOG_TAG, "capture normalized nodes=${nodes.size} messages=${context.messages.size}")
            val title = titleExtractor.extract(expectedPackage, nodes)
            context.takeIf { it.messages.isNotEmpty() }?.let {
                CapturedConversation(
                    title = title ?: "当前会话",
                    context = it,
                    persistable = title != null,
                )
            }
        } finally {
            recycleNode(root)
        }
    }

    override fun onDestroy() {
        cancelAutoRefresh()
        latestContextCache.clear()
        NextSayImeRuntime.session.unregisterHandler(imeGenerationHandler)
        if (::overlay.isInitialized) overlay.dispose()
        if (::ocrEngine.isInitialized) ocrEngine.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun runQuickReply() {
        val packageName = activePackage?.takeIf { it in SUPPORTED_PACKAGES } ?: return
        generationSurface = GenerationSurface.QUICK
        controller.dismiss()
        overlay.setBusy(true)
        overlay.showQuickReading()
        scope.launch {
            try {
                when (val result = obtainFreshContext(packageName)) {
                    is ContextCaptureResult.Success -> {
                        controller.showPreview(result.context)
                        controller.generate()
                    }
                    is ContextCaptureResult.CaptureError -> overlay.showQuickError(result.message)
                    ContextCaptureResult.Busy -> overlay.showQuickError("正在读取对话，请稍候")
                    ContextCaptureResult.Cancelled -> overlay.hideQuick()
                }
            } finally {
                overlay.setBusy(false)
            }
        }
    }

    private fun regenerateQuickReply(instruction: String) {
        val packageName = activePackage?.takeIf { it in SUPPORTED_PACKAGES } ?: return
        if (resolveForegroundApplicationPackage() != packageName) return
        generationSurface = GenerationSurface.QUICK
        val state = controller.state.value
        if (state == app.nextsay.overlay.OverlayState.Idle) {
            runQuickReply()
            return
        }
        scope.launch { controller.generate(instruction) }
    }

    private fun openAdvancedPanel() {
        val packageName = activePackage?.takeIf { it in SUPPORTED_PACKAGES } ?: return
        generationSurface = GenerationSurface.ADVANCED
        controller.dismiss()
        overlay.hideQuick()
        overlay.setBusy(true)
        scope.launch {
            try {
                when (val result = obtainFreshContext(packageName)) {
                    is ContextCaptureResult.Success -> controller.showPreview(result.context)
                    is ContextCaptureResult.CaptureError -> Toast.makeText(
                        this@NextSayAccessibilityService,
                        result.message,
                        Toast.LENGTH_SHORT,
                    ).show()
                    ContextCaptureResult.Busy -> Toast.makeText(
                        this@NextSayAccessibilityService,
                        "正在读取对话，请稍候",
                        Toast.LENGTH_SHORT,
                    ).show()
                    ContextCaptureResult.Cancelled -> Unit
                }
            } finally {
                overlay.setBusy(false)
            }
        }
    }

    private suspend fun generateForIme(targetPackage: String): Result<List<ReplyCandidate>> {
        if (activePackage != targetPackage || resolveForegroundApplicationPackage() != targetPackage) {
            return Result.failure(IllegalStateException("当前聊天已变化，请重新生成"))
        }
        generationSurface = GenerationSurface.IME
        controller.dismiss()
        overlay.hideAllContent()
        overlay.setBusy(true)
        return try {
            when (val result = obtainFreshContext(targetPackage)) {
                is ContextCaptureResult.Success -> {
                    controller.showPreview(result.context)
                    controller.generate()
                    val state = controller.state.value
                    if (
                        state is app.nextsay.overlay.OverlayState.Results &&
                        state.context.sourcePackage == targetPackage &&
                        state.candidates.size == REQUIRED_CANDIDATE_COUNT
                    ) {
                        Result.success(state.candidates)
                    } else {
                        Result.failure(IllegalStateException("没有生成可用的回复，请重试"))
                    }
                }
                is ContextCaptureResult.CaptureError -> Result.failure(IllegalStateException(result.message))
                ContextCaptureResult.Busy -> Result.failure(IllegalStateException("正在处理上一次请求，请稍候"))
                ContextCaptureResult.Cancelled -> Result.failure(IllegalStateException("当前聊天已变化，请重新生成"))
            }
        } finally {
            overlay.setBusy(false)
        }
    }

    private suspend fun obtainFreshContext(packageName: String): ContextCaptureResult {
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
        val now = SystemClock.elapsedRealtime()
        val packageName = autoRefreshScheduler.consumeDue(now) ?: return
        if (
            activePackage != packageName ||
            getSystemService(PowerManager::class.java)?.isInteractive != true
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
                is ContextCaptureResult.Success -> latestContextCache.complete(ticket, result.context)
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
        overlay.hideForCapture()
        return try {
            delay(CAPTURE_SETTLE_MILLIS)
            val screenshot = screenshotSource.capture() ?: return null
            val bitmap = screenshot.getOrThrow()
            try {
                val blocks = ocrEngine.recognize(bitmap).getOrThrow()
                val sourceApp = if (packageName == "com.tencent.mm") "wechat" else "qq"
                val captured = ocrParser.parse(
                    blocks = blocks,
                    screenWidth = bitmap.width,
                    screenHeight = bitmap.height,
                    contentBottom = resolveChatContentBottom(bitmap.height),
                    sourcePackage = packageName,
                    sourceApp = sourceApp,
                )
                Log.i(LOG_TAG, "ocr normalized blocks=${blocks.size} messages=${captured?.context?.messages?.size ?: 0}")
                captured
            } finally {
                bitmap.recycle()
            }
        } finally {
            overlay.restoreAfterCapture()
        }
    }

    private fun insertCandidate(candidate: ReplyCandidate) {
        val state = controller.state.value
        val expectedPackage = when (state) {
            is app.nextsay.overlay.OverlayState.Results -> state.context.sourcePackage
            else -> return
        }
        if (!insertionGate.tryStart()) return
        Log.i(LOG_TAG, "candidate insertion started")
        overlay.hidePanelForInsertion()
        scope.launch {
            delay(100)
            val foregroundPackage = resolveForegroundApplicationPackage()
            if (foregroundPackage != expectedPackage || !controller.hasActiveResultsFor(expectedPackage)) {
                Log.w(
                    LOG_TAG,
                    "candidate insertion cancelled foregroundMatches=${foregroundPackage == expectedPackage} resultsActive=${controller.hasActiveResultsFor(expectedPackage)}",
                )
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
            } catch (error: RuntimeException) {
                Log.e(LOG_TAG, "candidate insertion failed", error)
                InsertResult.Failure(null)
            } finally {
                insertionGate.finish()
            }
            when (result) {
                is InsertResult.Success -> {
                    Log.i(LOG_TAG, "candidate insertion completed result=Success")
                    controller.dismiss()
                    Toast.makeText(this@NextSayAccessibilityService, "已写入输入框，请确认后手动发送", Toast.LENGTH_SHORT).show()
                }
                is InsertResult.Failure -> {
                    Log.w(LOG_TAG, "candidate insertion completed result=Failure reason=${result.reason}")
                    overlay.render(controller.state.value)
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
        if (expectedPackage !in QUICK_INSERT_PACKAGES || !insertionGate.tryStart()) return
        scope.launch {
            delay(100)
            try {
                val foregroundPackage = resolveForegroundApplicationPackage()
                if (
                    foregroundPackage != expectedPackage ||
                    !controller.hasActiveResultsFor(expectedPackage)
                ) return@launch
                val root = findSupportedRoot(expectedPackage) ?: return@launch
                try {
                    inserter.insert(root, expectedPackage, foregroundPackage, candidate.text)
                } finally {
                    recycleNode(root)
                }
            } catch (error: RuntimeException) {
                Log.e(LOG_TAG, "quick candidate insertion failed", error)
            } finally {
                insertionGate.finish()
            }
        }
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

    private fun resolveChatContentBottom(screenHeight: Int): Int {
        val imeTop = windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { window ->
                val region = Region()
                window.getRegionInScreen(region)
                region.bounds.takeIf { !it.isEmpty }?.top
            }
            .minOrNull()
        return ((imeTop ?: screenHeight) - INPUT_AREA_GUARD_DP.dp).coerceIn(1, screenHeight)
    }

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
        const val LOG_TAG = "NextSayService"
        const val CAPTURE_SETTLE_MILLIS = 80L
        const val INPUT_AREA_GUARD_DP = 72
        const val REQUIRED_CANDIDATE_COUNT = 3
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
