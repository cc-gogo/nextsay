# NextSay Overlay Refresh And Insertion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add privacy-safe manual context refresh and make candidate taps feel responsive, return focus to the chat editor, and insert text without sending.

**Architecture:** Keep passive accessibility events limited to foreground-package tracking. A new refresh callback performs the existing explicit snapshot operation and replaces controller state only on success. Candidate insertion uses a pure bottom-editor selection policy plus Android node traversal, while the overlay window owns ripple/haptic feedback and temporarily closes before insertion.

**Tech Stack:** Kotlin 2.0, Android SDK 35/minSdk 30, Android accessibility APIs, kotlinx.coroutines, JUnit 4.

## Global Constraints

- Work only in `D:\codex\nextsay` and do not commit or push.
- Refresh never uploads; generation remains a second explicit action.
- Passive accessibility events never flatten or read chat node text.
- Insertion may focus and set text, but must never click or send.
- Password nodes, unsupported packages, blank candidates, and unsafe editors remain rejected.

---

### Task 1: Refresh State Contract

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayController.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/OverlayControllerTest.kt`

**Interfaces:**
- Produces: `OverlayController.refresh(context: ChatContext?)`.
- Behavior: non-null context enters `Preview` and invalidates candidates; null context preserves the reviewed context in `Error`.

- [ ] Add tests that start from `Results`, call `refresh(newContext)`, and assert `Preview(newContext)` with no old candidates.
- [ ] Add a test that calls `refresh(null)` and asserts `Error` retains the previous `ChatContext`.
- [ ] Run `gradlew.bat :android-app:testDebugUnitTest --tests app.nextsay.overlay.OverlayControllerTest` and confirm the missing method fails compilation.
- [ ] Implement:

```kotlin
fun refresh(context: ChatContext?) {
    if (context != null) {
        showPreview(context)
        return
    }
    val reviewed = reviewedContext() ?: return
    generationEpoch += 1
    mutableState.value = OverlayState.Error(reviewed, "刷新失败，已保留原上下文")
}
```

- [ ] Re-run the focused test and confirm it passes.

### Task 2: Safe Chat Editor Selection

**Files:**
- Create: `android-app/src/main/java/app/nextsay/insertion/EditableFieldSelector.kt`
- Create: `android-app/src/test/java/app/nextsay/insertion/EditableFieldSelectorTest.kt`
- Modify: `android-app/src/main/java/app/nextsay/insertion/ReplyInserter.kt`
- Modify: `android-app/src/test/java/app/nextsay/insertion/ReplyInserterPolicyTest.kt`

**Interfaces:**
- Produces: `EditableFieldSnapshot(index, bottom, editable, focused, password)`.
- Produces: `EditableFieldSelector.select(fields) -> Int?`.
- Selection order: safe focused editor first; otherwise safe editor with greatest `bottom`; password/non-editable nodes never qualify.

- [ ] Write literal-fixture tests proving focused priority, bottom-most fallback, and password rejection.
- [ ] Run the selector test and confirm missing production types fail compilation.
- [ ] Implement the pure selector using `firstOrNull { focused && safe }` then `maxByOrNull { bottom }`.
- [ ] Update `ReplyInserter` to collect editable node copies, use the selector, request `ACTION_FOCUS` when needed, then call only `ACTION_SET_TEXT`.
- [ ] Preserve and return the previous draft; recycle all copied nodes.
- [ ] Run insertion and policy tests and confirm they pass.

### Task 3: Refresh And Candidate Interaction UI

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`

**Interfaces:**
- Adds: `PanelCallbacks.onRefresh: () -> Unit`.
- Adds: `OverlayWindow.hidePanelForInsertion()`.
- Candidate row click contract: ripple -> haptic -> callback once.

- [ ] Add a title-row refresh `ImageButton` using `android.R.drawable.ic_popup_sync`, fixed `48dp` touch target, and content description `刷新上下文`.
- [ ] Connect `onRefresh` to `controller.refresh(captureOnUserRequest())`; no repository call occurs.
- [ ] Replace selectable candidate `TextView` with a clickable row whose background is a `RippleDrawable` over the existing 8dp neutral surface.
- [ ] On click, disable the row, call `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`, and invoke `onCandidate` once.
- [ ] Before insertion, set an `insertionInProgress` guard and call `overlay.hidePanelForInsertion()`.
- [ ] On the next main-loop turn, insert through `ReplyInserter`; success dismisses controller, failure re-renders retained results and shows explicit copy fallback.
- [ ] Search `android-app/src/main` for `ACTION_CLICK` and send-button identifiers; expected result is none.

### Task 4: Full And Device Verification

**Files:**
- Modify: `README.md` only if the manual workflow wording is stale.

- [ ] Run `gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug --warning-mode all` and require exit code 0.
- [ ] Build with `NEXTSAY_BACKEND_URL=http://127.0.0.1:8000/`, install with `adb install -r`, and restore `adb reverse tcp:8000 tcp:8000`.
- [ ] Verify the accessibility service remains enabled and the QQ foreground has a `ty=2032` NextSay overlay window.
- [ ] In a test chat, send/receive a new message, tap refresh, and confirm the preview is replaced before generation.
- [ ] Tap a candidate and confirm ripple/haptic feedback, panel closure, editor focus, text insertion, and no automatic send.
- [ ] Leave QQ and confirm the trigger window is removed.
