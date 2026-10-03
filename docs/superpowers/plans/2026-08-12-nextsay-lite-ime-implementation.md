# NextSay Lite AI IME Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight NextSay Android input method that generates replies from the existing capture pipeline and commits a selected candidate into WeChat or QQ/TIM through `InputConnection.commitText()`.

**Architecture:** Keep OCR, history, API generation, the floating trigger, and the overlay inside `NextSayAccessibilityService`. Add a process-local `ImeReplySession` that owns IME state and delegates generation to a handler registered by the accessibility service; the new `NextSayInputMethodService` renders that state and is the only new component allowed to call `commitText()`.

**Tech Stack:** Kotlin 2.0.21, Android `InputMethodService`, coroutines/`StateFlow`, programmatic Android Views, JUnit 4, Gradle Android plugin 8.7.3.

## Global Constraints

- Work only in `D:\codex\nextsay`; do not create a Git commit or push.
- Candidate selection may call `InputConnection.commitText()` but must never find, click, or invoke a chat send action.
- Support only `com.tencent.mm`, `com.tencent.mobileqq`, `com.tencent.tim`, and `com.tencent.qqlite`.
- Reject password, visible-password, web-password, and numeric-password fields.
- Do not read existing editor text through `InputConnection`, persist IME candidates, or log user-entered text.
- Preserve the existing green trigger and white overlay behavior for overlay-originated generation.
- Use TDD for every pure policy and state transition: write the test, observe the expected failure, add the minimum implementation, and observe GREEN.

---

### Task 1: Editor eligibility and presentation policies

**Files:**
- Create: `android-app/src/main/java/app/nextsay/ime/ImeEditorPolicy.kt`
- Create: `android-app/src/main/java/app/nextsay/ime/GenerationSurface.kt`
- Create: `android-app/src/test/java/app/nextsay/ime/ImeEditorPolicyTest.kt`
- Create: `android-app/src/test/java/app/nextsay/ime/GenerationSurfaceTest.kt`

**Interfaces:**
- Produces: `ImeEditorPolicy.evaluate(packageName: String?, inputType: Int): ImeEditorEligibility`.
- Produces: `ImeEditorEligibility.Available(targetPackage: String)` or `Unavailable(reason: ImeUnavailableReason)`.
- Produces: `GenerationSurface.OVERLAY` and `GenerationSurface.IME`, plus `GenerationPresentationPolicy.shouldRenderOverlay(surface: GenerationSurface): Boolean`.

- [ ] **Step 1: Write failing editor-policy tests**

Use literal packages and Android `InputType` constants. Cover all four supported packages, an unsupported package, null package, ordinary text, `TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, and `TYPE_NUMBER_VARIATION_PASSWORD`.

```kotlin
@Test fun `accepts ordinary WeChat text editor`() {
    assertEquals(
        ImeEditorEligibility.Available("com.tencent.mm"),
        policy.evaluate("com.tencent.mm", InputType.TYPE_CLASS_TEXT),
    )
}

@Test fun `rejects visible password editor`() {
    assertEquals(
        ImeEditorEligibility.Unavailable(ImeUnavailableReason.SENSITIVE_FIELD),
        policy.evaluate(
            "com.tencent.mm",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        ),
    )
}
```

- [ ] **Step 2: Write failing presentation-policy tests**

```kotlin
@Test fun `overlay requests render the white panel`() {
    assertTrue(policy.shouldRenderOverlay(GenerationSurface.OVERLAY))
}

@Test fun `IME requests do not render the white panel`() {
    assertFalse(policy.shouldRenderOverlay(GenerationSurface.IME))
}
```

- [ ] **Step 3: Run focused tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
.\gradlew.bat :android-app:testDebugUnitTest `
  --tests app.nextsay.ime.ImeEditorPolicyTest `
  --tests app.nextsay.ime.GenerationSurfaceTest `
  --no-daemon
```

Expected: compilation fails because the IME policy types do not exist.

- [ ] **Step 4: Implement the minimum policies**

`ImeEditorPolicy` must compare the input class and variation masks separately. It must return `UNSUPPORTED_APP` before inspecting field variations and `SENSITIVE_FIELD` for the four password variants. `GenerationPresentationPolicy` returns `surface == GenerationSurface.OVERLAY`.

- [ ] **Step 5: Re-run focused tests and verify GREEN**

Run the Step 3 command. Expected: every editor and presentation policy test passes.

### Task 2: Process-local IME reply session

**Files:**
- Create: `android-app/src/main/java/app/nextsay/ime/ImeReplyState.kt`
- Create: `android-app/src/main/java/app/nextsay/ime/ImeReplySession.kt`
- Create: `android-app/src/main/java/app/nextsay/ime/NextSayImeRuntime.kt`
- Create: `android-app/src/test/java/app/nextsay/ime/ImeReplySessionTest.kt`

**Interfaces:**
- Consumes: `ImeEditorEligibility` from Task 1 and existing `ReplyCandidate`.
- Produces: `ImeReplyState.Unavailable`, `Ready`, `Loading`, `Results(targetPackage, candidates, message: String? = null)`, `Error`, and `Inserted`.
- Produces: `ImeGenerationHandler.generate(targetPackage: String): Result<List<ReplyCandidate>>`.
- Produces: `ImeReplySession.attachEditor(eligibility)`, `detachEditor()`, `registerHandler(handler)`, `unregisterHandler(handler)`, `requestGeneration()`, `candidateForCommit(index, targetPackage)`, and `completeCommit(success)`.
- Produces: singleton `NextSayImeRuntime.session` for the two Android services in the same process.

- [ ] **Step 1: Write failing session tests**

Tests must use a real `ImeReplySession` and a small deterministic handler; do not assert on mock calls. Cover:

- supported editor + registered handler becomes `Ready`;
- no handler remains `Unavailable(SERVICE_DISCONNECTED)`;
- generation emits `Loading`, then exactly three candidates in `Results`;
- a second request while loading returns without starting another generation;
- switching WeChat to QQ invalidates WeChat results;
- detaching the editor clears results;
- mismatched target package returns no candidate and clears stale results;
- successful commit becomes `Inserted` and clears candidates;
- failed commit keeps results and exposes the connection error;
- unregistering only the same handler removes service availability;
- a late result cannot restore candidates after the editor target changes.

Example observable behavior:

```kotlin
@Test fun `late WeChat generation cannot restore results after switching to QQ`() = runTest {
    val deferred = CompletableDeferred<Result<List<ReplyCandidate>>>()
    val session = ImeReplySession()
    session.registerHandler(ImeGenerationHandler { deferred.await() })
    session.attachEditor(ImeEditorEligibility.Available("com.tencent.mm"))
    launch { session.requestGeneration() }
    runCurrent()
    session.attachEditor(ImeEditorEligibility.Available("com.tencent.mobileqq"))

    deferred.complete(Result.success(replies()))
    advanceUntilIdle()

    assertEquals(ImeReplyState.Ready("com.tencent.mobileqq"), session.state.value)
}
```

- [ ] **Step 2: Run the focused session test and verify RED**

Run: `.\gradlew.bat :android-app:testDebugUnitTest --tests app.nextsay.ime.ImeReplySessionTest --no-daemon`

Expected: compilation fails because session types do not exist.

- [ ] **Step 3: Implement the minimum state machine**

Use `MutableStateFlow`, an editor epoch counter, and a private `requestRunning` flag. Do not persist state. `requestGeneration()` snapshots the target and epoch, publishes `Loading`, awaits the registered handler, then publishes results only when target and epoch still match. Treat candidate counts other than three as `Error`.

`candidateForCommit()` returns a candidate only from `Results` whose target equals the current editor and supplied target. On mismatch, return null and restore the current editor's `Ready` state. `completeCommit(false)` restores the same candidates in `Results` with a non-null connection error message; `completeCommit(true)` publishes `Inserted` without retaining candidate text.

- [ ] **Step 4: Add the production singleton**

```kotlin
object NextSayImeRuntime {
    val session = ImeReplySession()
}
```

- [ ] **Step 5: Run the focused test and all IME policy tests**

Run: `.\gradlew.bat :android-app:testDebugUnitTest --tests 'app.nextsay.ime.*' --no-daemon`

Expected: all IME state and policy tests pass.

### Task 3: Connect the accessibility generation pipeline

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/OverlayControllerTest.kt` only if an existing observable contract needs an additional assertion.

**Interfaces:**
- Consumes: `NextSayImeRuntime.session`, `ImeGenerationHandler`, `GenerationSurface`, and `GenerationPresentationPolicy`.
- Produces: service-side `generateForIme(targetPackage: String): Result<List<ReplyCandidate>>`.
- Produces: public `OverlayWindow.hidePanel()` that only closes the white panel and leaves the green trigger intact.

- [ ] **Step 1: Register and unregister the IME handler**

Create one stable `ImeGenerationHandler` field. Register it after the accessibility service has initialized `oneTapCoordinator`; unregister that exact instance in `onDestroy()` before cancelling the scope.

- [ ] **Step 2: Track generation surface**

Set `GenerationSurface.OVERLAY` before existing green-trigger runs and `GenerationSurface.IME` before IME runs. In the controller state collector:

- render through `OverlayWindow` for `OVERLAY`;
- call `overlay.hidePanel()` and skip `overlay.render()` for `IME`.

Keep the surface associated with the latest request until the next entry point explicitly selects a new surface. This prevents a delayed `StateFlow` collection from rendering IME results in the white panel. The existing green-trigger path explicitly selects `OVERLAY`; the IME path explicitly selects `IME`.

- [ ] **Step 3: Implement IME generation using the existing coordinator**

`generateForIme` must reject when `activePackage` or `resolveForegroundApplicationPackage()` differs from the requested target. It then runs the existing `OneTapReplyCoordinator`. Map `CaptureError`, `Busy`, and `Cancelled` to explicit failures. For success, read `OverlayController.state.value`; return candidates only from `OverlayState.Results` with matching `context.sourcePackage` and exactly three candidates.

Do not duplicate OCR, history, API, or prompt logic.

- [ ] **Step 4: Expose panel-only hiding**

Add `OverlayWindow.hidePanel() = closePanel()`. It must not detach or hide the green trigger.

- [ ] **Step 5: Run Android unit tests**

Run: `.\gradlew.bat :android-app:testDebugUnitTest --no-daemon`

Expected: all existing overlay/capture tests and new IME tests pass.

### Task 4: Implement the lightweight input method UI and commit adapter

**Files:**
- Create: `android-app/src/main/java/app/nextsay/ime/NextSayInputMethodService.kt`
- Create: `android-app/src/main/java/app/nextsay/ime/ImeKeyboardViewFactory.kt`
- Create: `android-app/src/main/res/xml/method.xml`
- Modify: `android-app/src/main/AndroidManifest.xml`
- Modify: `android-app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `NextSayImeRuntime.session`, `ImeEditorPolicy`, `ImeReplyState`, `currentInputConnection`, and `currentInputEditorInfo`.
- Produces: an exported, permission-protected `InputMethodService` registered with `android.view.InputMethod` metadata.

- [ ] **Step 1: Register the input method service and metadata**

Add to the manifest:

```xml
<service
    android:name=".ime.NextSayInputMethodService"
    android:exported="true"
    android:label="@string/ime_service_name"
    android:permission="android.permission.BIND_INPUT_METHOD">
    <intent-filter>
        <action android:name="android.view.InputMethod" />
    </intent-filter>
    <meta-data
        android:name="android.view.im"
        android:resource="@xml/method" />
</service>
```

`method.xml` contains a single `<input-method>` with `android:supportsSwitchingToNextInputMethod="true"`; do not declare language subtypes because this is not a character keyboard.

- [ ] **Step 2: Build the compact keyboard view**

`ImeKeyboardViewFactory` creates a programmatic view with:

- title and status;
- “切换输入法” action;
- “读取上下文并生成” / “重新生成” primary action;
- a vertical candidate container with ripple and haptic feedback;
- no letter, number, delete, enter, or send keys.

`render(state)` must remove stale candidate views before drawing new state. Candidate labels contain only returned candidate text; logs contain lifecycle state names only, never candidate text.

- [ ] **Step 3: Connect IME lifecycle to the session**

In `onStartInput`, evaluate `EditorInfo.packageName` and `inputType`, then call `attachEditor`. In `onFinishInput`, call `detachEditor`. In `onCreateInputView`, subscribe to the shared state on a main-thread coroutine and render it; cancel the scope in `onDestroy`.

- [ ] **Step 4: Commit a selected candidate safely**

On candidate click:

1. obtain the current package from `currentInputEditorInfo`;
2. ask `candidateForCommit(index, packageName)`;
3. re-evaluate the current editor and reject sensitive/unsupported fields;
4. call only `currentInputConnection?.commitText(candidate.text, 1)`;
5. pass the Boolean result to `completeCommit`.

Do not call `performEditorAction`, `sendKeyEvent`, accessibility actions, clipboard APIs, or chat view IDs.

- [ ] **Step 5: Add the input-method picker action**

Use `InputMethodManager.showInputMethodPicker()` from the explicit button. Do not programmatically change the secure default-input-method setting.

- [ ] **Step 6: Run tests, lint, and assemble**

Run:

```powershell
$env:JAVA_HOME='D:\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:lintDebug :android-app:assembleDebug `
  -PNEXTSAY_BACKEND_URL='http://127.0.0.1:8000/' `
  -PNEXTSAY_DEV_TOKEN='local-dev-token' `
  --no-daemon --warning-mode all
```

Expected: `BUILD SUCCESSFUL`; lint reports no fatal issue and a debug APK is produced.

### Task 5: Add first-use IME setup to the main activity

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/MainActivity.kt`
- Modify: `android-app/src/main/res/values/strings.xml`
- Create: `android-app/src/test/java/app/nextsay/ImeServiceStatusTest.kt`
- Create: `android-app/src/main/java/app/nextsay/ImeServiceStatus.kt`

**Interfaces:**
- Produces: `ImeServiceStatus.isEnabled(enabledIds: Set<String>, targetId: String): Boolean`.
- Consumes: `InputMethodManager.enabledInputMethodList` and system actions `Settings.ACTION_INPUT_METHOD_SETTINGS` / `InputMethodManager.showInputMethodPicker()`.

- [ ] **Step 1: Write and verify a failing status test**

Test exact component membership, including an empty set and a similarly named foreign component. Run the focused test and confirm it fails because `ImeServiceStatus` is absent.

- [ ] **Step 2: Implement the minimum status policy**

```kotlin
class ImeServiceStatus {
    fun isEnabled(enabledIds: Set<String>, targetId: String): Boolean = targetId in enabledIds
}
```

Run the focused test and confirm GREEN.

- [ ] **Step 3: Add status and setup actions to `MainActivity`**

Add a NextSay input method status row and button. When disabled, the button opens `Settings.ACTION_INPUT_METHOD_SETTINGS`. When enabled, it opens the system picker. Refresh both accessibility and IME status in `onResume`.

Update the introductory copy so it explains the two modes: existing floating panel and stable NextSay keyboard insertion. Keep the privacy copy explicit that messages are never sent automatically.

- [ ] **Step 4: Run all Android unit tests**

Run: `.\gradlew.bat :android-app:testDebugUnitTest --no-daemon`

Expected: all tests pass.

### Task 6: Full verification and Honor X40 installation

**Files:**
- Verify: `android-app/build/outputs/apk/debug/android-app-debug.apk`
- No Git commit or push.

**Interfaces:**
- Consumes: completed Tasks 1–5 and the local backend at `http://127.0.0.1:8000/`.
- Produces: verified APK and device regression evidence.

- [ ] **Step 1: Run the complete automated verification**

```powershell
.\.venv\Scripts\python.exe -m pytest backend\tests -q
$env:JAVA_HOME='D:\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:lintDebug :android-app:assembleDebug `
  -PNEXTSAY_BACKEND_URL='http://127.0.0.1:8000/' `
  -PNEXTSAY_DEV_TOKEN='local-dev-token' `
  --no-daemon --warning-mode all
```

Expected: backend reports 18 passing tests and Gradle reports `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect the APK registration before installation**

Use `apkanalyzer manifest print` or `aapt dump xmltree` from the local Android SDK. Confirm the IME service has `BIND_INPUT_METHOD`, the `android.view.InputMethod` action, and `@xml/method` metadata. Confirm no SMS, contacts, notification listener, storage, or clipboard permission was added.

- [ ] **Step 3: Install without force-stopping accessibility**

Use the connected Honor X40 device explicitly and run `adb -s <serial> install -r <apk>`. Do not run `am force-stop app.nextsay`. Restore `adb reverse tcp:8000 tcp:8000` and confirm the backend health endpoint.

- [ ] **Step 4: Enable and select NextSay manually**

From the updated main activity, open input-method settings, enable NextSay, then open the picker and select it. This step requires the user's system confirmation and must not be automated through secure settings.

- [ ] **Step 5: Run the real-device matrix**

In both WeChat and QQ:

- empty draft → generate → commit one candidate;
- existing draft with cursor at start, middle, and end;
- selected text replacement;
- successful commit leaves message unsent;
- switch to Sogou and back to NextSay;
- generate in WeChat, switch to QQ, confirm stale candidate cannot commit;
- disable accessibility and confirm the keyboard explains the dependency;
- open a password field and confirm AI controls/candidates are unavailable;
- repeat generation, hide/show keyboard, background/foreground app, and confirm no crash.

- [ ] **Step 6: Review the final scoped changes**

Inspect all new and modified files. Confirm every changed production line maps to the approved IME design, candidate text is never logged or persisted, and no send action, clipboard fallback, simulated coordinate input, Git commit, or unrelated refactor was introduced.
