# NextSay 0.2.5 — drag and capture handoff

## Scope and authorization

- User approved replacing the app and running local simulated overlay tests.
- No model requests, message input or message sending authorized for this test.
- Actual WeChat context verification requires separate permission; not performed yet.

## Implemented

- Dragging the floating ball keeps the candidate window open and updates its position without replacing its root.
- Candidate window avoids the chat title and bottom input region; long content scrolls.
- Limited horizontal space uses a vertical placement and height limit so the candidate window does not cover the ball.
- Requirements remain intact while dragging. Candidate refresh keeps the same window.
- OCR failure with the header obstructed has a privacy-safe `header_obscured` diagnostic and specific guidance.
- Version: 0.2.5 / versionCode 13. The follow behavior applies to the candidate window, not all other panels.

## Evidence

- Android host reports inspected: 311 tests, zero failures/errors/skips.
- Prior completed backend run: 18 tests passing.
- Prior completed phone run: three selected QuickOverlayKeyboardTest tests passed (`OK (3 tests)`) covering vertical drag, horizontal drag, same-root refresh and suppression.
- Prior review identified horizontal overlap; fixed with regression coverage and reviewed again.
- APK built and installed; package manager confirms 0.2.5 / 13, and the home screenshot shows that version.
- APK: `releases/NextSay-0.2.5-debug.apk`.
- SHA256: `4F7BEA63765F7C5ADF66A26DE7BD34C0E43AF32127A788004CEC69D891843F8F`.
- `git diff --check`: no whitespace errors.

## Device cleanup and outstanding checks

- Device: HONOR RMO-AN00 / Android 13, serial A9GVVB2A12014220.
- Temporary instrumentation package `app.nextsay.test` uninstalled successfully. Main app and its data retained.
- Diagnostic counts remain unchanged after local testing and cleanup: APP_CRASHED 1, CANDIDATE_REQUESTED 26, CAPTURE_FAILED 18, CONNECTION_TEST_STARTED 4, CONNECTION_TEST_SUCCEEDED 5, GENERATION_FAILED 20, GENERATION_STARTED 66, GENERATION_SUCCEEDED 60.
- Accessibility setting still lists NextSay as enabled, but `dumpsys accessibility` lists it under crashed services, not bound services. The available crash buffer contains an old October 2 exception, not evidence of a new 0.2.5 crash. Cause of the post-test binding state is not established.
- Restore the service using normal system UI; do not overwrite secure accessibility settings.
- Global automatic generation was temporarily paused for safe testing (`auto-paused=true`); original value was false. Restore through the contacts UI while the main app, not a chat, is foreground.
- UI unexpectedly moved from accessibility settings to model settings during attempted recovery. Clicking stopped; awaiting confirmation that the user is not operating concurrently. No API configuration changed.
- Real WeChat capture is NOT verified. Title avoidance addresses a suspected obstruction; it is not proof that every APP-CAPTURE cause is resolved.
- Screenshots and authorized fixtures remain in ignored `.tools/qq-capture-20261003`; no secrets or chat text are included in this handoff.

## Final recovery update

- User confirmed temporary exclusive control again. On resuming, home already showed the assistant enabled; system now confirms NextSay bound and no crashed services. No secure settings were overwritten.
- Restored global pause through the contacts UI: verified `auto-paused=false`. Returned to MainActivity. No contact profile or provider configuration saved or changed.
- A connection test was recorded at 01:34:46 during the unexpected screen transition: SETTINGS CONNECTION_TEST_STARTED/SUCCEEDED. An intended settings-row tap may have landed on the model test button after the page changed. Do not claim zero API requests for the entire recovery. This test uses synthetic test input, not chat text. Local instrumentation itself had no API requests.
- Latest candidate generation remains 00:59:19, before this recovery. No new candidate generation or capture failure during recovery. Event totals changed through rolling retention; compare latest timestamps rather than totals alone.
- Temporary test package is absent; only `app.nextsay` remains. Home shows 0.2.5 / build 13 and assistant enabled.
- WeChat read-only authorization/verification remains outstanding.
