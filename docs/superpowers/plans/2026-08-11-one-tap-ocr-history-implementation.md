# NextSay One-Tap OCR History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-tap reply flow that keeps QQ on structured accessibility capture, uses offline OCR for WeChat, merges per-conversation local history, and automatically requests reply candidates without ever sending a message.

**Architecture:** Pure Kotlin parsers and mergers sit behind Android-specific screenshot, OCR, encryption, and Room adapters. `NextSayAccessibilityService` orchestrates one user-triggered operation, selects accessibility or OCR capture by package, persists safe single-chat history, and then reuses the existing repository and insertion boundary.

**Tech Stack:** Kotlin 2.0.21, Android API 30-35, coroutines 1.9.0, ML Kit bundled Chinese text recognition, Room 2.6.1 with KSP, Android Keystore AES-GCM, JUnit 4.

## Global Constraints

- Only a user click may capture a screen or upload recognized text.
- A one-tap run automatically uploads recognized text and requests replies; there is no second confirmation.
- Raw screenshots remain in memory, are never logged, saved, cached, or uploaded, and are released immediately after OCR.
- QQ/TIM use accessibility nodes first; WeChat uses local OCR because its current accessibility root is empty.
- The first release persists only one-to-one conversations with a unique visible title; group chats use current-screen context only.
- Candidate insertion uses `ACTION_SET_TEXT` only and never clicks a send button.
- Chat titles, message text, screenshots, and API bodies must not appear in logs.
- Do not create Git commits or push changes.

---

### Task 1: Add OCR and Room build support

**Files:**
- Modify: `build.gradle.kts`
- Modify: `android-app/build.gradle.kts`
- Modify: `android-app/src/main/res/xml/accessibility_service_config.xml`

**Interfaces:**
- Produces: bundled Chinese OCR classes, Room/KSP generated classes, and the `CAPABILITY_CAN_TAKE_SCREENSHOT` service capability.

- [ ] **Step 1: Record the current dependency/build baseline**

Run:

```powershell
$env:JAVA_HOME='D:\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug --no-daemon
```

Expected: existing tests and APK build pass before dependency changes.

- [ ] **Step 2: Add KSP, Room, and bundled Chinese OCR dependencies**

Add KSP plugin `2.0.21-1.0.28`, Room runtime/ktx/compiler `2.6.1`, and `com.google.mlkit:text-recognition-chinese:16.0.1`. Add `android:canTakeScreenshot="true"` to the accessibility service XML.

- [ ] **Step 3: Resolve dependencies and rebuild**

Run the baseline command again. Expected: Gradle resolves all artifacts and the existing suite remains green.

### Task 2: Define OCR layout parsing and conversation capture contracts

**Files:**
- Create: `android-app/src/main/java/app/nextsay/capture/CapturedConversation.kt`
- Create: `android-app/src/main/java/app/nextsay/ocr/OcrTextBlock.kt`
- Create: `android-app/src/main/java/app/nextsay/ocr/WechatOcrParser.kt`
- Test: `android-app/src/test/java/app/nextsay/ocr/WechatOcrParserTest.kt`

**Interfaces:**
- Produces: `CapturedConversation(title: String?, context: ChatContext, persistable: Boolean)`.
- Produces: `WechatOcrParser.parse(blocks, screenWidth, screenHeight, contentBottom): CapturedConversation?`.

- [ ] **Step 1: Write failing parser tests**

Cover title selection from the centered header band, top-to-bottom message ordering, left/right role assignment, keyboard/input chrome exclusion, timestamp filtering, low-confidence rejection, empty-title rejection, and group-title detection as non-persistable.

- [ ] **Step 2: Run the parser tests and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.ocr.WechatOcrParserTest" --no-daemon
```

Expected: compilation/test failure because the parser contracts do not exist.

- [ ] **Step 3: Implement the minimal pure parser**

Use immutable `OcrTextBlock(text, bounds, confidence)` values. Normalize whitespace, filter screen chrome, select the title only from the centered header band, and classify messages by horizontal center. Return `null` when either a stable title or at least one message is absent.

- [ ] **Step 4: Run parser tests and verify GREEN**

Run the targeted command, then the full JVM suite.

### Task 3: Implement deterministic history merging and request context limits

**Files:**
- Create: `android-app/src/main/java/app/nextsay/history/HistoryMerger.kt`
- Create: `android-app/src/main/java/app/nextsay/history/GenerationContextBuilder.kt`
- Test: `android-app/src/test/java/app/nextsay/history/HistoryMergerTest.kt`
- Test: `android-app/src/test/java/app/nextsay/history/GenerationContextBuilderTest.kt`

**Interfaces:**
- Produces: `HistoryMerger.merge(existing, visible): MergeResult` where `MergeResult` contains combined messages, appended messages, and `reliableOverlap`.
- Produces: `GenerationContextBuilder.build(history, visible, maxMessages = 20, maxCharacters = 3000): ChatContext`.

- [ ] **Step 1: Write failing merger tests**

Cover full overlap, partial suffix/prefix overlap, repeated identical short messages, role-sensitive comparison, no-overlap refusal, and empty history bootstrap.

- [ ] **Step 2: Verify merger tests fail for missing production types**

- [ ] **Step 3: Implement longest suffix/prefix sequence overlap**

Normalize whitespace for comparison but preserve display text. Bootstrap an empty conversation from visible messages. When non-empty history has no reliable overlap, return history unchanged and mark the current capture non-persistable.

- [ ] **Step 4: Write failing context-builder tests**

Assert the most recent 20 messages are retained and total text is trimmed from the oldest side until it is at most 3000 characters.

- [ ] **Step 5: Implement and verify the context builder**

Run both targeted test classes and the full JVM suite.

### Task 4: Add encrypted per-conversation Room history

**Files:**
- Create: `android-app/src/main/java/app/nextsay/history/db/ConversationEntity.kt`
- Create: `android-app/src/main/java/app/nextsay/history/db/MessageEntity.kt`
- Create: `android-app/src/main/java/app/nextsay/history/db/ConversationDao.kt`
- Create: `android-app/src/main/java/app/nextsay/history/db/NextSayDatabase.kt`
- Create: `android-app/src/main/java/app/nextsay/history/MessageCipher.kt`
- Create: `android-app/src/main/java/app/nextsay/history/AndroidKeystoreMessageCipher.kt`
- Create: `android-app/src/main/java/app/nextsay/history/ConversationHistoryRepository.kt`
- Test: `android-app/src/test/java/app/nextsay/history/MessageCipherContractTest.kt`
- Test: `android-app/src/test/java/app/nextsay/history/ConversationHistoryRepositoryTest.kt`

**Interfaces:**
- Produces: `ConversationHistoryRepository.mergeAndLoad(capture): HistoryLoadResult`.
- Produces: `excludeTitle(packageName, normalizedTitle)` and `clearAll()` maintenance methods.

- [ ] **Step 1: Write failing cipher and repository contract tests**

Use an in-memory fake cipher and fake DAO for JVM tests. Assert stored entity text is ciphertext, separate titles never share messages, excluded titles are not persisted, and no-overlap captures do not pollute history.

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement Room schema and repository**

Use a unique index on `(sourcePackage, normalizedTitle)`. Encrypt each message body with AES/GCM/NoPadding using a non-exportable Android Keystore key; store Base64 of version byte, IV, and ciphertext. Never store raw screenshots.

- [ ] **Step 4: Verify GREEN and run Room schema compilation**

Run JVM tests and `:android-app:assembleDebug` so KSP validates DAO queries.

### Task 5: Implement single-frame screenshot and ML Kit OCR adapters

**Files:**
- Create: `android-app/src/main/java/app/nextsay/capture/AccessibilityScreenshotSource.kt`
- Create: `android-app/src/main/java/app/nextsay/ocr/MlKitChineseOcrEngine.kt`
- Create: `android-app/src/main/java/app/nextsay/ocr/OcrEngine.kt`
- Test: `android-app/src/test/java/app/nextsay/capture/ScreenshotCaptureGateTest.kt`

**Interfaces:**
- Produces: `AccessibilityScreenshotSource.capture(): Result<Bitmap>`.
- Produces: `OcrEngine.recognize(bitmap): Result<List<OcrTextBlock>>`.
- Consumes: screenshot bounds supplied by the accessibility service and pure `WechatOcrParser` from Task 2.

- [ ] **Step 1: Write a failing capture-gate test**

Assert concurrent capture attempts are rejected and the gate is released on success and exception.

- [ ] **Step 2: Verify RED and implement the gate**

- [ ] **Step 3: Implement screenshot callback bridging**

Convert the `HardwareBuffer` to an immutable software bitmap, close the buffer in every path, and resume the coroutine exactly once. Do not log bitmap or OCR content.

- [ ] **Step 4: Implement bundled ML Kit recognition**

Convert ML Kit text blocks into pure `OcrTextBlock` values and always close the `InputImage`/bitmap ownership at the coordinator boundary. Add a warm-up method that initializes the recognizer without reading the screen.

- [ ] **Step 5: Build and lint**

Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.

### Task 6: Add a one-tap capture/generate coordinator

**Files:**
- Create: `android-app/src/main/java/app/nextsay/capture/OneTapReplyCoordinator.kt`
- Test: `android-app/src/test/java/app/nextsay/capture/OneTapReplyCoordinatorTest.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayController.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayState.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/OverlayControllerTest.kt`

**Interfaces:**
- Produces: `run(packageName): OneTapResult` with `Success(context)`, `CaptureError(message)`, or `Cancelled`.
- Consumes: accessibility capture, OCR capture, history repository, and generation context builder.

- [ ] **Step 1: Write failing coordinator routing tests**

Assert QQ uses accessibility only, WeChat uses OCR only, an empty QQ tree can fall back to OCR, one click causes one API generation, package changes cancel work, and failures never call the API.

- [ ] **Step 2: Verify RED**

- [ ] **Step 3: Implement coordinator and capture states**

Add `Capturing(packageName)` and a context-free `CaptureError(message)` to the overlay state. Preserve existing retry semantics for API errors. A successful capture calls `showPreview(context)` and immediately `generate()`.

- [ ] **Step 4: Verify GREEN and all existing state tests**

Run coordinator and overlay tests, then the complete JVM suite.

### Task 7: Integrate one-tap behavior, overlay feedback, and service cleanup

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Modify: `android-app/src/main/res/values/strings.xml`
- Modify: `android-app/src/main/java/app/nextsay/MainActivity.kt`

**Interfaces:**
- Consumes: all Task 2-6 contracts.
- Preserves: `ReplyInserter` and `InsertionGate` behavior; no send action is added.

- [ ] **Step 1: Add/adjust state tests before service wiring**

Assert capture status text, capture errors, automatic loading, and stale result rejection. Verify these tests fail before UI changes.

- [ ] **Step 2: Wire the service**

Replace `openPreview()` with a scoped one-tap job. Hide all NextSay overlay views for one frame before WeChat capture and restore them in `finally`. Remove temporary root/view-ID diagnostic logs, retaining only counts and failure codes without text.

- [ ] **Step 3: Improve touch feedback**

Use a bounded `RippleDrawable`, `HapticFeedbackConstants.CONFIRM`, a stable 64dp touch window, and immediate disabled/loading state. Apply the same confirmation haptic to reply candidates.

- [ ] **Step 4: Update disclosure copy**

State that clicking the floating button captures visible content locally and automatically uploads recognized text to request replies. Keep the explicit “never automatically sends” copy.

- [ ] **Step 5: Run static safety scans**

Run:

```powershell
rg -n "ACTION_CLICK|performAction\([^\n]*ACTION_CLICK|自动发送" android-app/src/main
rg -n "Log\..*(text|title|message|request)" android-app/src/main
```

Expected: no send-button click path and no sensitive-content logging.

### Task 8: Verification, device install, and focused real-phone checks

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-08-11-one-tap-ocr-history-design.md` only if implementation reveals a genuine design correction.

**Interfaces:**
- Produces: tested debug APK at `android-app/build/outputs/apk/debug/android-app-debug.apk`.

- [ ] **Step 1: Update README behavior and privacy boundaries**

Document one-tap automatic generation, QQ accessibility, WeChat on-device OCR, local history, screenshot non-retention, and current same-title/group limitations.

- [ ] **Step 2: Run backend and Android verification**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest backend\tests -q
$env:JAVA_HOME='D:\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:lintDebug :android-app:assembleDebug `
  -PNEXTSAY_BACKEND_URL='http://127.0.0.1:8000/' `
  -PNEXTSAY_DEV_TOKEN='local-dev-token' `
  --no-daemon --warning-mode all
```

Expected: all tests pass, lint has no errors, and APK assembly succeeds.

- [ ] **Step 3: Install without force-stopping accessibility**

Use `adb install --no-streaming -r` and accept the Honor USB install prompt. Confirm the service remains enabled/bound; do not run `am force-stop app.nextsay`.

- [ ] **Step 4: Verify QQ on the connected phone**

Confirm one tap uses structured text, immediately requests three mock replies, candidates have ripple/haptic feedback, insertion writes text but does not send, and switching apps invalidates results.

- [ ] **Step 5: Verify WeChat on the connected phone**

Confirm one tap captures without the NextSay overlay in the bitmap, local OCR finds title/messages, the debug panel shows exact recognized text, repeat capture appends only new visible messages, and a second unique title loads a separate history.

- [ ] **Step 6: Inspect privacy and performance evidence**

Confirm logcat contains counts/timings but no content, no screenshot file exists in app/external cache, hot OCR is under 1 second on the Honor device, and the backend receives only the bounded text context.

