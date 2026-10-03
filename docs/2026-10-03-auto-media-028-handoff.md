# NextSay 0.2.8 — automatic incoming rounds and image boundaries

## Delivered

- Retain a locally observed incoming round while editing or other generation work is busy. Resume from a fresh, matching frame rather than requiring a second event.
- A newer incoming round supersedes an older ticket. Old HTTP responses cannot replace the newer round. API success/failure is terminal; no automatic paid failure retry loop.
- Preserve automatic HTTP generation across same-app keyboard/window layout changes, rechecking eligibility and the current frame before presenting its result.
- Seed keyboard geometry at conversation baseline. One observed keyboard resize does not count as a history scroll; subsequent scrolling with unchanged keyboard layout remains conservative. A real viewport revision cancels automatic generation and clears pending work.
- Confirm pending/results using source package, title, viewport revision and up to four shared trailing messages. Keyboard subsets are allowed within the same revision; identical last words alone cannot override conflicting preceding evidence.
- Do not consume low-confidence, unknown or obscured tails as a completed incoming round. A later reliable read can still recognize them.
- Debounce continuous accessibility events with a bounded delay, preserving the WeChat OCR cooldown. An enabled, visible, unlocked chat has a four-second local read fallback. This is not another model call.
- Use visible input-row bounds to avoid overcropping the final message when the keyboard is open.
- Recognize image attachment controls and conservatively detect unlabelled rectangular media locally. Exclude image-internal OCR from typed conversation; preserve one `[图片]` placeholder and outer-message ownership.
- Reject geometric image candidates touching or adjacent to overlay exclusions, preventing an erased white-bubble tail from turning text into an image. Only actual attachment controls can exempt covered media from text-read occlusion checks.
- Occlusion checks extend from the latest external avatar to the input row, including lower lines even if the bubble contour is truncated.
- Image-only result cards explain that image contents have not been read. Existing prompt rules prohibit inventing image details.
- Automatic status updates render into an existing window without hiding/removing/readding it. No automatic sending was added.
- Added bounded, allowlisted automatic-state diagnostics, keyboard visibility, capture boundary and pending flags. These fields do not contain chat text, screenshots, titles or credentials.
- Relationship profiles, Natural/Huangmao prompts and the deferred creator-video adaptation were not changed in this release.

## Verification

- Final command: `:android-app:testDebugUnitTest :android-app:assembleDebug :android-app:assembleDebugAndroidTest --no-daemon`: **BUILD SUCCESSFUL**, 1m42s.
- Host Android suite: **376 tests across 63 suites; 0 failures, 0 errors, 0 skips**. Authorized, previously saved QQ layout and WeChat screenshot fixtures were supplied; no live phone capture occurred.
- Backend: **18 passed**.
- Actual assertion failures were observed before fixes for keyboard-prefix recovery, unreliable-tail recovery, debounce starvation, diagnostic fields, repeated-tail history approval, image-content notice and first-keyboard-layout revision. Some new helper/queue/geometry/bounds APIs initially failed compilation before implementation; these are not claimed as assertion red/green evidence.
- Added pure-policy queue/detector combinations, synthetic screenshot geometry→parser tests, viewport/occlusion tests, diagnostics tests and presenter tests. They do not constitute execution of the accessibility service lifecycle on a phone.
- Independent read-only review identified three important boundary bugs, then the first-keyboard initialization omission. Each was checked against the source and addressed. Final review reported no remaining definite blocker; it did not run tests or verify device behavior.
- APK badging: `app.nextsay`, versionName **0.2.8**, versionCode **16**.
- APK signature verification succeeded (v2); certificate SHA256 `bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`, matching the prior debug certificate.
- APK: `D:/agent/codex/nextsay/releases/NextSay-0.2.8-debug.apk`, **57,398,516 bytes**.
- APK SHA256: `C6BD519A3F20170BA7C8043E5ED3F7A8DC58F21112725A5703F51BAEA2A97090`.
- `git diff --check` exited 0; existing LF/CRLF conversion warnings remain.

## Limits / next device check

- Not installed on the phone in this run. No phone UI control, live chat reading, user API call or instrumentation-test execution occurred. The instrumentation APK was built only.
- Actual HONOR/WeChat keyboard event ordering, automatic trigger rate, screenshot accuracy and visual no-flicker behavior still require device verification. Local tests cannot establish these rates or model quality.
- Image geometry is a conservative heuristic, not visual understanding. Background-similar or complex images may remain unrecognized; uncertain ownership does not authorize automatic generation.
- With incomplete anchors, real manual scrolling, lock/unlock or a changed chat, the system may deliberately rebuild its baseline rather than replay messages. It only handles the visible active chat, not all background incoming notifications.
- A paid result that cannot be verified against the current frame is discarded without a paid retry. The user may explicitly request a fresh reply.
- Default diagnostics record states and bounded metadata, not chat content. They may explain an eligibility/visibility decision but do not replace an authorized reproduction of a device-specific problem.
- Existing unrelated workspace changes were preserved. No commit, push, reset or destructive cleanup was performed.

## Workflow influence

Test-driven-development was used for local regressions, receiving-code-review for checking reported boundary failures before editing, and verification-before-completion for the final suite, artifact version and signature evidence. The review skill authorized the existing read-only review subtask.

## Subsequent USB installation — 2026-10-03

- The user explicitly requested phone installation after delivery.
- Verified the release SHA256 before installation; connected USB device `A9GVVB2A12014220` was online (HONOR RMO-AN00).
- Before installation: versionName 0.2.7, versionCode 15.
- `adb install -r` returned **Success**. No uninstall or data-clear command was used.
- Phone package manager confirms versionName **0.2.8**, versionCode **16**, lastUpdateTime **2026-10-03 11:34:24** (Asia/Shanghai).
- Requested NextSay MainActivity launch; `am start` succeeded. No chat screen capture, API call, candidate generation or instrumentation-test execution was performed.
- This verifies installation/version only, not the actual automatic-trigger rate or visual behavior on the phone.
