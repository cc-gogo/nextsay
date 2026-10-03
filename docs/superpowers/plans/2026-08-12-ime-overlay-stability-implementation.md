# NextSay IME Overlay Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the NextSay panel open while the current system IME edits the panel instruction, without weakening real app-switch handling.

**Architecture:** Add a pure Kotlin `ForegroundEventPolicy` at the accessibility boundary. `NextSayAccessibilityService` supplies the event package, parsed default-IME package, and live panel state before running the existing foreground resolver; `OverlayWindow` exposes read-only attachment state.

**Tech Stack:** Kotlin 2.x, Android AccessibilityService, Android `Settings.Secure`, JUnit 4, Gradle Android plugin.

## Global Constraints

- Do not commit or push Git changes.
- Never send a chat message automatically; candidate actions may only write into the current input field.
- Ignore an event only when the panel is open and its package exactly equals the current default IME package.
- Preserve current handling for real WeChat/QQ switches and for leaving all supported applications.
- Remove temporary overlay render and close-stack diagnostic logs after the policy is covered by tests.

---

### Task 1: Pure foreground-event policy

**Files:**
- Create: `android-app/src/main/java/app/nextsay/accessibility/ForegroundEventPolicy.kt`
- Create: `android-app/src/test/java/app/nextsay/accessibility/ForegroundEventPolicyTest.kt`

**Interfaces:**
- Consumes: event package `String?`, default IME package `String?`, panel-open state `Boolean`.
- Produces: `ForegroundEventPolicy.shouldIgnore(eventPackage: String?, defaultImePackage: String?, panelOpen: Boolean): Boolean`.

- [ ] **Step 1: Write failing policy tests**

```kotlin
class ForegroundEventPolicyTest {
    private val policy = ForegroundEventPolicy()

    @Test fun `ignores the current input method only while panel is open`() {
        assertTrue(policy.shouldIgnore("com.sohu.inputmethod.sogou", "com.sohu.inputmethod.sogou", true))
    }

    @Test fun `does not ignore input method while panel is closed`() {
        assertFalse(policy.shouldIgnore("com.sohu.inputmethod.sogou", "com.sohu.inputmethod.sogou", false))
    }

    @Test fun `does not ignore a real switch from WeChat to QQ`() {
        assertFalse(policy.shouldIgnore("com.tencent.mobileqq", "com.sohu.inputmethod.sogou", true))
    }

    @Test fun `does not ignore leaving supported applications`() {
        assertFalse(policy.shouldIgnore("com.honor.launcher", "com.sohu.inputmethod.sogou", true))
    }

    @Test fun `does not ignore any package when default input method is unknown`() {
        assertFalse(policy.shouldIgnore("com.sohu.inputmethod.sogou", null, true))
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `gradlew.bat :android-app:testDebugUnitTest --tests app.nextsay.accessibility.ForegroundEventPolicyTest --no-daemon`

Expected: compilation fails because `ForegroundEventPolicy` does not exist.

- [ ] **Step 3: Add the minimal policy**

```kotlin
class ForegroundEventPolicy {
    fun shouldIgnore(eventPackage: String?, defaultImePackage: String?, panelOpen: Boolean): Boolean =
        panelOpen && defaultImePackage != null && eventPackage == defaultImePackage
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `gradlew.bat :android-app:testDebugUnitTest --tests app.nextsay.accessibility.ForegroundEventPolicyTest --no-daemon`

Expected: all five policy tests pass.

### Task 2: Wire policy into accessibility events

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`

**Interfaces:**
- Consumes: `ForegroundEventPolicy.shouldIgnore(...)` from Task 1 and `Settings.Secure.DEFAULT_INPUT_METHOD` in flattened component format.
- Produces: `OverlayWindow.isPanelOpen: Boolean` and service-side `defaultInputMethodPackage(): String?`.

- [ ] **Step 1: Expose read-only panel state**

Add to `OverlayWindow`:

```kotlin
val isPanelOpen: Boolean
    get() = panelAttached
```

- [ ] **Step 2: Parse the default IME package at the event boundary**

Add imports for `android.content.ComponentName` and `android.provider.Settings`, then add:

```kotlin
private fun defaultInputMethodPackage(): String? {
    val flattened = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return ComponentName.unflattenFromString(flattened)?.packageName
}
```

- [ ] **Step 3: Ignore matching IME events before foreground resolution**

Create one `ForegroundEventPolicy` field and add this before `resolveForegroundApplicationPackage()` in `onAccessibilityEvent`:

```kotlin
if (foregroundEventPolicy.shouldIgnore(packageName, defaultInputMethodPackage(), overlay.isPanelOpen)) {
    Log.d(LOG_TAG, "ignoring current input method event while panel is open")
    return
}
```

This preserves existing resolution and dismissal behavior for WeChat, QQ, Settings, launchers, and every non-default-IME package.

- [ ] **Step 4: Remove temporary high-volume diagnostics**

Remove `render state=...` and the throwable-based `panel closing` log from `OverlayViewFactory.kt`. Keep normal trigger/candidate lifecycle logs.

- [ ] **Step 5: Run Android unit tests**

Run: `gradlew.bat :android-app:testDebugUnitTest --no-daemon`

Expected: all Android unit tests pass.

### Task 3: Static and package verification

**Files:**
- Verify only; no planned production changes.

**Interfaces:**
- Consumes: completed Tasks 1–2.
- Produces: lint-clean debug APK configured for `http://127.0.0.1:8000/` and `local-dev-token`.

- [ ] **Step 1: Run backend regression tests**

Run: `.venv\Scripts\python.exe -m pytest backend\tests -q`

Expected: 18 tests pass.

- [ ] **Step 2: Run Android tests, lint, and APK build**

Run:

```powershell
$env:JAVA_HOME='D:\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:lintDebug :android-app:assembleDebug `
  -PNEXTSAY_BACKEND_URL='http://127.0.0.1:8000/' `
  -PNEXTSAY_DEV_TOKEN='local-dev-token' `
  --no-daemon --warning-mode all
```

Expected: `BUILD SUCCESSFUL`, with the debug APK under `android-app/build/outputs/apk/debug/`.

- [ ] **Step 3: Confirm the diff is scoped**

Run: `git diff -- android-app/src/main android-app/src/test docs/superpowers`

Expected: only the policy, wiring, panel-state accessor, diagnostic-log removal, tests, specification, and this plan are present; no send action or unrelated refactor is introduced.

### Task 4: Honor X40 regression after installation

**Files:**
- Verify on device; no planned code changes.

**Interfaces:**
- Consumes: debug APK from Task 3 and the existing ADB reverse `tcp:8000 -> tcp:8000`.
- Produces: device evidence for the scenario that originally hid the panel.

- [ ] **Step 1: Install without force-stopping accessibility**

Run: `adb install -r android-app\build\outputs\apk\debug\android-app-debug.apk`

Expected: `Success`; do not run `am force-stop app.nextsay`.

- [ ] **Step 2: Exercise instruction editing in QQ and WeChat**

For each app, open NextSay and test empty input, appending to existing text, deleting text, and repeated edits. Switch Sogou between Chinese and English and hide/show the keyboard.

Expected: the white panel stays visible through every IME operation.

- [ ] **Step 3: Exercise safety boundaries**

Switch WeChat to QQ, QQ to WeChat, and then leave both supported apps. Generate three candidates and tap one.

Expected: stale panels close on real app switches, the trigger disappears outside supported apps, and a selected candidate is written into the input field without being sent.
