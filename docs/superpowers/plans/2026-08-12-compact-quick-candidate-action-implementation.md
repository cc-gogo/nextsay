# Compact Quick Candidate Action Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make quick candidates compact, copy every selected reply, and additionally write QQ-family replies into the active input field without sending.

**Architecture:** Introduce a pure quick-candidate action policy that decides copy/insert behavior from the result package. Keep Android clipboard and accessibility insertion as service/window adapters, preserving existing foreground checks and insertion gates.

**Tech Stack:** Kotlin, Android AccessibilityService/API 30+, JUnit 4, Gradle Android plugin.

## Global Constraints

- WeChat only copies.
- QQ, TIM, and QQ Lite copy first and then attempt input insertion.
- The only successful user message is `已复制`.
- Copy failure keeps candidates visible and prevents insertion.
- No path clicks Send or automatically sends a message.
- Quick window width is 280dp; candidate font is 13sp; status font is 12sp.
- Do not commit or push.

---

### Task 1: Package-Aware Candidate Action

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/QuickReplyViewFactory.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/QuickReplyPresenterTest.kt`

**Interfaces:**
- Produces: `QuickCandidateHandler.select(candidate: ReplyCandidate, sourcePackage: String)`.
- Consumes callbacks: `copy`, `insert`, `onCopied`, and `onCopyFailed`.

- [ ] Add failing tests proving WeChat copies without insertion, QQ copies then inserts, QQ insertion failure does not change copy success, and copy failure prevents insertion.
- [ ] Run `:android-app:testDebugUnitTest --tests app.nextsay.overlay.QuickReplyPresenterTest` and verify the new signature/behavior fails.
- [ ] Implement the minimal package policy for the four supported package names.
- [ ] Run the focused tests and require exit code 0.

### Task 2: Android Insertion and Short Feedback Integration

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`

**Interfaces:**
- `OverlayWindow` receives `onQuickInsert: (ReplyCandidate) -> Unit` and determines `sourcePackage` from the active `OverlayState.Results`.
- `NextSayAccessibilityService` reuses `ReplyInserter`, foreground validation, result validation, and `InsertionGate` for QQ-family packages.

- [ ] Wire copy success to the exact Toast `已复制` and close the quick window.
- [ ] Wire QQ insertion after copy with no second success/failure Toast.
- [ ] Keep WeChat out of the insertion path and retain no-send behavior.
- [ ] Compile the debug Kotlin source and require exit code 0.

### Task 3: Compact Quick Cards

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/overlay/QuickReplyViewFactory.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`

**Interfaces:**
- Quick window constants: width 280dp, root padding 6dp, status 12sp, candidate 13sp, candidate padding 10dp by 8dp, gap 4dp, corner 8dp.

- [ ] Apply only the approved dimension and type-size reductions.
- [ ] Preserve four-line candidates, ripple, haptic feedback, interior-side expansion, and boundary clamping.
- [ ] Run lint and inspect the report for fatal issues.

### Task 4: Verification and Phone Installation

**Files:**
- Verify only.

- [ ] Run all Android JVM tests, lint, and debug APK assembly with local backend URL/token.
- [ ] Install with `adb install -r -t`, restore `adb reverse tcp:8000 tcp:8000`, and re-enable accessibility if Honor disables it.
- [ ] Verify WeChat copy-only, QQ copy-and-insert, compact cards, exact short feedback, and zero automatic sends/crashes.
