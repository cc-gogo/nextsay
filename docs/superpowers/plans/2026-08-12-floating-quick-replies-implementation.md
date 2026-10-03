# NextSay Floating Quick Replies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build event-driven local conversation refresh, draggable floating trigger, quick-copy reply cards, and a long-press advanced panel without automatic paste or send.

**Architecture:** Extract conversation capture into a coordinator that returns `ChatContext`, then let the accessibility service decide whether to cache, preview, or generate. Pure Kotlin cache, scheduling, gesture, and geometry components carry policy; Android window code only adapts accessibility events, coroutines, views, clipboard, and persisted preferences.

**Tech Stack:** Kotlin 1.9, Android AccessibilityService/API 30+, coroutines/StateFlow, ML Kit Chinese OCR, JUnit 4, Gradle Android plugin.

## Global Constraints

- Supported packages are WeChat `com.tencent.mm`, QQ `com.tencent.mobileqq`, TIM `com.tencent.tim`, and QQ Lite `com.tencent.qqlite`.
- Page changes debounce for exactly 800 ms; WeChat OCR starts no more often than once every 2,000 ms.
- Automatic refresh performs only local capture, OCR, encrypted history merge, and in-memory caching; it never calls the reply API.
- Screenshots stay in memory, are never uploaded or persisted, and the `Bitmap` is recycled after OCR.
- Candidate text, chat text, and instructions must not be logged.
- Quick-candidate taps copy only, vibrate, and close the quick window; no automatic paste, send, coordinate click, or gesture injection is allowed.
- Long press opens the existing advanced panel without automatically generating.
- Keep the existing lightweight IME implementation.
- Do not install through ADB, operate a phone, commit, or push in this implementation session.

---

### Task 1: Capture-Only Conversation Coordinator

**Files:**
- Create: `android-app/src/main/java/app/nextsay/capture/ConversationContextCoordinator.kt`
- Modify: `android-app/src/main/java/app/nextsay/capture/OneTapReplyCoordinator.kt`
- Test: `android-app/src/test/java/app/nextsay/capture/ConversationContextCoordinatorTest.kt`
- Test: `android-app/src/test/java/app/nextsay/capture/OneTapReplyCoordinatorTest.kt`

**Interfaces:**
- Produces: `sealed interface ContextCaptureResult` with `Success(context: ChatContext)`, `CaptureError(message: String)`, `Cancelled`, and `Busy`.
- Produces: `ConversationContextCoordinator.capture(packageName: String): ContextCaptureResult`.
- Compatibility: `OneTapReplyCoordinator.run(packageName)` delegates capture and invokes `onContextReady` only for explicit legacy callers.

- [ ] **Step 1: Write failing coordinator tests** proving WeChat uses OCR, QQ uses accessibility first then OCR, foreground changes cancel, concurrent capture returns `Busy`, and capture-only success has no generation callback.
- [ ] **Step 2: Run** `./gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.capture.ConversationContextCoordinatorTest" --no-daemon` and verify the missing class/API causes failure.
- [ ] **Step 3: Implement** `ConversationContextCoordinator` by moving capture, merge, foreground recheck, cancellation, and atomic run-gate logic from `OneTapReplyCoordinator` without adding an API-generation dependency.
- [ ] **Step 4: Convert** `OneTapReplyCoordinator` into the smallest compatibility wrapper over `ConversationContextCoordinator`; preserve its existing result behavior and tests.
- [ ] **Step 5: Run** both capture coordinator test classes and verify they pass.

### Task 2: Epoch-Aware Latest Context Cache

**Files:**
- Create: `android-app/src/main/java/app/nextsay/capture/LatestContextCache.kt`
- Test: `android-app/src/test/java/app/nextsay/capture/LatestContextCacheTest.kt`

**Interfaces:**
- Produces: `LatestContextCache.markPageChanged(packageName: String): Long` returning the new epoch.
- Produces: `beginCapture(packageName: String): CaptureTicket?`, `complete(ticket: CaptureTicket, context: ChatContext): Boolean`, `fail(ticket: CaptureTicket)`, `fresh(packageName: String): ChatContext?`, `isDirty(packageName: String): Boolean`, and `clear()`.
- `CaptureTicket(packageName: String, pageEpoch: Long)` prevents an old OCR result becoming fresh after a newer page event.

- [ ] **Step 1: Write failing tests** for initial missing state, dirty-on-event, matching-epoch completion, stale completion rejection, package switch clearing, and failure leaving the cache dirty.
- [ ] **Step 2: Run** the cache test class and verify it fails because the class is absent.
- [ ] **Step 3: Implement** the synchronized in-process cache with one active package, page epoch, captured epoch, context, and refreshing flag.
- [ ] **Step 4: Run** the cache tests and verify they pass.

### Task 3: Debounced Auto-Refresh Policy

**Files:**
- Create: `android-app/src/main/java/app/nextsay/capture/AutoRefreshScheduler.kt`
- Test: `android-app/src/test/java/app/nextsay/capture/AutoRefreshSchedulerTest.kt`

**Interfaces:**
- Produces: `AutoRefreshScheduler.onPageChanged(packageName: String, nowMillis: Long): Long?` returning absolute earliest execution time.
- Produces: `onCaptureStarted()`, `onCaptureFinished(nowMillis: Long, packageName: String): Long?`, `recordWechatOcrStarted(nowMillis: Long)`, `consumeDue(nowMillis: Long): String?`, and `cancel()`.
- The pure policy uses `debounceMillis = 800L` and `wechatCooldownMillis = 2_000L` constructor defaults.

- [ ] **Step 1: Write failing tests** for 800 ms debounce reset, WeChat cooldown deferral, no QQ cooldown, cancellation, and a page event arriving during capture being rescheduled after completion.
- [ ] **Step 2: Run** the scheduler test class and verify the missing class/API failure.
- [ ] **Step 3: Implement** the minimal state machine without Android, coroutine, view, or bitmap references.
- [ ] **Step 4: Run** scheduler tests and verify they pass.

### Task 4: Gesture Classification and Position Persistence Math

**Files:**
- Create: `android-app/src/main/java/app/nextsay/overlay/FloatingTriggerGestureController.kt`
- Create: `android-app/src/main/java/app/nextsay/overlay/OverlayPosition.kt`
- Create: `android-app/src/main/java/app/nextsay/overlay/OverlayPositionStore.kt`
- Test: `android-app/src/test/java/app/nextsay/overlay/FloatingTriggerGestureControllerTest.kt`
- Test: `android-app/src/test/java/app/nextsay/overlay/OverlayPositionTest.kt`

**Interfaces:**
- Produces: `GestureAction` values `None`, `Click`, `LongPress`, and `DragBy(deltaX: Float, deltaY: Float)` from `onDown`, `onMove`, timeout, and `onUp`.
- Produces: `OverlayPosition.toFractions(...)` and `OverlayPosition.fromFractions(...)` with clamping to available screen bounds.
- Produces: Android adapter `OverlayPositionStore.load(): PositionFractions` and `save(PositionFractions)` using private `SharedPreferences`.

- [ ] **Step 1: Write failing gesture tests** for click, long press, movement threshold, drag suppressing long press, and boundary equality.
- [ ] **Step 2: Run** gesture tests and confirm expected missing API failure.
- [ ] **Step 3: Implement** the pure gesture state machine using caller-provided touch slop and long-press duration.
- [ ] **Step 4: Write failing geometry tests** for fraction round-trip, zero-sized ranges, and clamping after screen-size change.
- [ ] **Step 5: Run** geometry tests and confirm expected missing API failure.
- [ ] **Step 6: Implement** pure position conversion and the small SharedPreferences adapter.
- [ ] **Step 7: Run** both overlay policy test classes and verify they pass.

### Task 5: Three Presentation Surfaces

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/ime/GenerationSurface.kt`
- Modify: `android-app/src/test/java/app/nextsay/ime/GenerationSurfaceTest.kt`

**Interfaces:**
- Produces: `GenerationSurface.QUICK`, `GenerationSurface.ADVANCED`, and `GenerationSurface.IME`.
- Produces: `GenerationPresentationPolicy.destination(surface): GenerationDestination`, where the destination is quick window, advanced panel, or IME-only.

- [ ] **Step 1: Replace tests with failing assertions** that each surface maps to exactly one destination.
- [ ] **Step 2: Run** the presentation policy tests and verify `QUICK`/`ADVANCED` are unresolved.
- [ ] **Step 3: Implement** the three-way policy and update callers to use the renamed enum values only after their target rendering paths exist.
- [ ] **Step 4: Run** the presentation tests and verify they pass.

### Task 6: Quick Reply Views and Clipboard Behavior

**Files:**
- Create: `android-app/src/main/java/app/nextsay/overlay/QuickReplyViewFactory.kt`
- Create: `android-app/src/main/java/app/nextsay/overlay/ClipboardReplyCopier.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Test: `android-app/src/test/java/app/nextsay/overlay/QuickReplyPresenterTest.kt`

**Interfaces:**
- Produces: `QuickReplyPresenter.render(state: OverlayState): QuickReplyModel` containing loading, exactly three candidate rows, or retryable error.
- Produces: `ClipboardReplyCopier.copy(candidate: ReplyCandidate): Boolean`; API 33+ sets `ClipDescription.EXTRA_IS_SENSITIVE` to `true`.
- Produces: quick-view callbacks `onCandidate`, `onRetry`, and `onDismiss` without insertion callbacks.

- [ ] **Step 1: Write failing presenter tests** for loading, results, error, and rejecting unrelated idle/preview rendering.
- [ ] **Step 2: Run** presenter tests and confirm the missing API failure.
- [ ] **Step 3: Implement** the pure presenter/model, then implement Android views with ripple candidate cards and no candidate-body logging.
- [ ] **Step 4: Implement** the clipboard adapter, sensitive clip metadata, haptic feedback, close-after-success, and visible retry status on copy failure.
- [ ] **Step 5: Run** presenter and existing overlay controller tests and verify they pass.

### Task 7: Overlay Window Gesture, Placement, and Dual Surfaces

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`

**Interfaces:**
- `OverlayWindow` accepts `onQuickTrigger`, `onAdvancedTrigger`, and drag callbacks.
- Produces: `renderQuick(state)`, `renderAdvanced(state)`, `hideQuick()`, `hideAdvanced()`, `hideAllContent()`, and preserves `hideForCapture()/restoreAfterCapture()`.
- Trigger position is restored from `OverlayPositionStore`, clamped, updated during drag, and saved as fractions on drag end.

- [ ] **Step 1: Wire** the tested gesture controller into the trigger `OnTouchListener`, using `ViewConfiguration.scaledTouchSlop` and `longPressTimeout`.
- [ ] **Step 2: Make dragging** close quick/advanced content, update top-left overlay coordinates within bounds, and save normalized position on release.
- [ ] **Step 3: Add** the independent quick window, place it toward the screen interior relative to the trigger, and clamp its vertical position.
- [ ] **Step 4: Keep** the existing advanced bottom panel focusable for its instruction editor and relationship picker.
- [ ] **Step 5: Run** all Android JVM tests before service integration.

### Task 8: Accessibility Event and Cache Integration

**Files:**
- Modify: `android-app/src/main/res/xml/accessibility_service_config.xml`
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Modify: `android-app/src/main/java/app/nextsay/accessibility/ForegroundEventPolicy.kt`
- Test: `android-app/src/test/java/app/nextsay/accessibility/ForegroundEventPolicyTest.kt`

**Interfaces:**
- Accessibility config subscribes to `typeWindowStateChanged|typeWindowsChanged|typeWindowContentChanged`.
- The service owns one `ConversationContextCoordinator`, `LatestContextCache`, and `AutoRefreshScheduler`.
- Automatic jobs call `capture()` and cache only; user quick/advanced/IME flows may call the reply API after obtaining a fresh context.

- [ ] **Step 1: Add failing policy tests** for ignoring NextSay/self-IME overlay events and accepting supported-app content changes while interactive.
- [ ] **Step 2: Run** policy tests and verify the new cases fail for the expected branch.
- [ ] **Step 3: Extend** the accessibility XML and event handler to mark page epochs and schedule a coroutine job at the policy deadline; cancel jobs on unsupported package, screen-off, destroy, and own-window events.
- [ ] **Step 4: Implement automatic capture** so it calls only `ConversationContextCoordinator.capture`, completes/rejects the cache ticket, and never calls `controller.showPreview()` or `controller.generate()`.
- [ ] **Step 5: Implement quick click** to select `QUICK`, acquire fresh context or perform one user capture, show preview, generate, and render state only in the quick window.
- [ ] **Step 6: Implement long press** to select `ADVANCED`, acquire fresh context or capture, show preview, and stop before generation.
- [ ] **Step 7: Adapt IME** to select `IME`, acquire a fresh/captured context, generate, and return exactly three candidates without showing either overlay surface.
- [ ] **Step 8: Ensure package departure** invalidates pending jobs/results, clears cache, dismisses controller, and removes all overlay content.
- [ ] **Step 9: Run** accessibility, capture, presentation, IME, and overlay unit tests.

### Task 9: Full Local Verification and APK

**Files:**
- Verify only; fix only failures caused by this feature.

- [ ] **Step 1: Run backend tests:** `.\.venv\Scripts\python.exe -m pytest backend\tests -q` and require zero failures.
- [ ] **Step 2: Set** `JAVA_HOME=D:\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8` for the Gradle process.
- [ ] **Step 3: Run** `.\gradlew.bat :android-app:testDebugUnitTest :android-app:lintDebug :android-app:assembleDebug -PNEXTSAY_BACKEND_URL='http://127.0.0.1:8000/' -PNEXTSAY_DEV_TOKEN='local-dev-token' --no-daemon --warning-mode all` and require exit code 0.
- [ ] **Step 4: Review** `git diff --check`, `git status --short`, and the implementation against every completion criterion in the approved design.
- [ ] **Step 5: Report** the APK path and explicitly state that no ADB install, phone operation, commit, or push occurred.
