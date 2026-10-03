# WeChat Direct Focus Insertion Experiment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Determine whether WeChat's hidden focused `MMEditText` can be obtained through `AccessibilityNodeInfo.findFocus(FOCUS_INPUT)` and safely written without developing a full keyboard.

**Architecture:** Add a pure safety policy for direct-focus metadata, then make `ReplyInserter` try the direct input-focus node before its existing recursive editable-node search. Keep all existing package, password, focus, and `ACTION_SET_TEXT` checks.

**Tech Stack:** Kotlin, Android Accessibility APIs, JUnit 4, Gradle Android plugin.

## Global Constraints

- Do not commit or push Git changes.
- Never invoke send actions, editor actions, key events, gestures, coordinates, or clipboard paste.
- Never log or persist editor text.
- Preserve the existing recursive selector as fallback for QQ.

---

### Task 1: Direct-focus safety policy

**Files:**
- Create: `android-app/src/main/java/app/nextsay/insertion/DirectFocusPolicy.kt`
- Create: `android-app/src/test/java/app/nextsay/insertion/DirectFocusPolicyTest.kt`

**Interfaces:**
- Produces: `DirectFocusSnapshot(editable: Boolean, focused: Boolean, password: Boolean)`.
- Produces: `DirectFocusPolicy.canUse(snapshot: DirectFocusSnapshot): Boolean`.

- [ ] Write tests proving only an editable, focused, non-password node is accepted; split rejection cases for missing focus, non-editable, and password nodes.
- [ ] Run `:android-app:testDebugUnitTest --tests app.nextsay.insertion.DirectFocusPolicyTest` and verify RED because policy types are absent.
- [ ] Implement `snapshot.editable && snapshot.focused && !snapshot.password`.
- [ ] Re-run the focused test and verify GREEN.

### Task 2: Prefer the hidden focused editor

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/insertion/ReplyInserter.kt`

**Interfaces:**
- Consumes: `root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)` and `DirectFocusPolicy`.
- Preserves: existing `EditableFieldSelector` fallback and `ReplyInserterPolicy` final validation.

- [ ] Inject a `DirectFocusPolicy` into `ReplyInserter` with a production default.
- [ ] At the start of `selectField`, call `root.findFocus(FOCUS_INPUT)` exactly once.
- [ ] Build a metadata-only snapshot. Return the node if policy accepts it; otherwise recycle it and continue into the unchanged recursive fallback.
- [ ] Run all Android unit tests and confirm no existing insertion behavior regresses.

### Task 3: Verify and run the Honor X40 experiment

**Files:**
- Verify: `android-app/build/outputs/apk/debug/android-app-debug.apk`.

**Interfaces:**
- Produces: a debug APK configured for `http://127.0.0.1:8000/`.

- [ ] Run backend tests and Android unit tests, lint, and APK assembly.
- [ ] Audit source to confirm `ACTION_SET_TEXT` remains the only editor-writing action and no send/clipboard/gesture logic was added.
- [ ] Install the APK without force-stopping the accessibility service; restore `adb reverse tcp:8000 tcp:8000`.
- [ ] User selects Sogou, focuses the WeChat chat editor, generates via the green trigger, and taps one candidate.
- [ ] Record success only if text appears in the WeChat editor and remains unsent. If copy fallback still appears, record the direct-focus experiment as failed and do not add further accessibility simulation.
