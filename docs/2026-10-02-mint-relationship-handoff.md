# NextSay 0.2.2 — relationship synchronization and pale mint UI

## Delivered behavior

- More reply options selects the current object's saved relationship on opening, and synchronizes when a newly captured context identifies another object. Unassociated contexts reset to ordinary.
- An explicit relationship chosen in the panel applies only to its current session; loading, results, error and attached-panel context refresh preserve it. Closing and reopening reloads the saved profile.
- Retry submits the visible relationship and instruction instead of a stale failed-request selection. Explicit ordinary no longer silently inherits a saved lover relationship. Quick, automatic and IME generation still inherit saved profiles through an internal empty relationship argument.
- Incompatible saved relationship rules are omitted when an explicit temporary relationship differs. Profiles, memory, account spaces and API settings are not changed.
- Advanced context refresh preserves the attached root via `beginContextCheck`; failure retains reviewed context. Capture and generation both disable relevant controls without removing the window.
- Shared mint styling separates pale primary fills from text accent colors. Overlay background opacity is 245/255, while text remains opaque. System dark mode remains supported with lighter mint tones.
- The floating trigger has a chat-bubble mark, pale translucent fill, fine border and softer shadow. The 52dp target and click/long-press/drag behavior are unchanged; a dotted variant indicates hidden new candidates.

## Verification

- Android host suite: **277 tests, 0 failures, 0 errors, 0 skipped**. Includes native-view Robolectric tests of saved selection/submission, temporary choice lifetime, changed profiles, ordinary retry, capture control locking/attachment, and AA contrast in both themes over light/dark underlays.
- New relationship, pale-fill and retry tests were observed failing against the old behavior before their fixes. The capture-control test also reproduced unlocked inputs before the fix.
- Backend: **18 passed**.
- Debug application and Android instrumentation APKs build successfully. `git diff --check` is clean.
- Independent read-only review identified the legacy advanced refresh remount path; that path was fixed and re-reviewed with no remaining critical/important findings.
- **No device feature tests ran during development.** HONOR rejected the temporary instrumentation package with `INSTALL_FAILED_ABORTED: User rejected permissions`. Development stopped device installation attempts at that point; no private chat inspection, model requests, message input or sends were performed. Subsequent user-authorized application installation is recorded below.
- Host window tests use an Activity token adapter; they do not prove OEM pixel rendering, gesture feel, keyboard behavior or physical flicker. Service refresh success/failure/cancellation routing was source-reviewed; the full service route is not end-to-end simulated in host tests.
- Navigation to another conversation while the advanced editor is open still follows the existing editing/capture policy; reopen or explicitly refresh to load that conversation. No automatic capture was added during editing.

## Package

- `D:\agent\codex\nextsay\releases\NextSay-0.2.2-debug.apk`
- Application `app.nextsay`; versionCode **10**, versionName **0.2.2**; 57,080,361 bytes.
- SHA256: `1AB7EDDD61C7645E984EC175A2D7D2A45207703DB8884C15BF79C9BB80BC2C45`.
- Signing certificate SHA256: `bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a` (unchanged from 0.2.1).
- Previous `releases/NextSay-0.2.1-debug.apk` retained. No database migration, deletion, Git commit or push.

## Repeat host verification

```powershell
$env:JAVA_HOME='D:\agent\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
$env:NEXTSAY_ROBOLECTRIC_SDK_DIR='D:\agent\codex\nextsay\.tools\robolectric-sdks'
$env:NEXTSAY_TEST_SCREENSHOT='C:\Users\ASUS\AppData\Local\Temp\nextsay-role-check-20261002\screen.png'
.\gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug :android-app:assembleDebugAndroidTest --no-daemon
$env:PYTHONPATH='D:\agent\codex\nextsay\backend\src'
.\.venv\Scripts\python.exe -m pytest backend\tests -q
```

Robolectric is test-only and not shipped in the app. These tests use legacy graphics, without desktop native Skia. Local API 33 and API 35 instrumented SDK JARs in ignored `.tools/robolectric-sdks` were downloaded from a Maven mirror and verified against Maven Central SHA512 checksums. Without the optional SDK directory environment variable, Robolectric downloads dependencies from Maven Central. The screenshot is the previously approved local OCR fixture, not a new phone capture; if absent the optional geometry test skips.

## Subsequent user-authorized USB installation

On 2026-10-02 the user explicitly requested retrying installation over USB. The connected HONOR RMO-AN00 accepted `adb install -r releases/NextSay-0.2.2-debug.apk` with **Success**; no uninstall or data clearing was performed. Installed package metadata confirms versionCode **10**, versionName **0.2.2**, and the installed `base.apk` SHA256 matches the delivered package above. Launching `app.nextsay/.MainActivity` returned **Status: ok** and a live application PID. This verifies installation and startup only, not end-to-end chat/overlay behavior. No chats were viewed, model requests made, messages entered or sent.
