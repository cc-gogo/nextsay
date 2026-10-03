# NextSay 0.2.9 — bidirectional automatic generation and stable candidates

## Delivered behavior

- With automatic generation enabled for the active contact, a reliably observed new OTHER message requests a reply; a new ME message requests a supplement. Ownership selects the writing target, not whether generation is allowed. No automatic sending was added.
- Keyboard subsets, repeated reads and correction of an already consumed earlier uncertain OCR row do not themselves trigger generation. Contact/viewport changes still establish a baseline, and unknown, low-confidence or obscured latest tails are not consumed prematurely.
- Fixed a reproduced freeze: consuming an earlier UNKNOWN row followed by a reliable tail previously made a later correction of that row break all anchor overlap, blocking subsequent new messages. Only previously consumed non-tail uncertain rows permit correction; the final anchor remains an exact role/text match. Contact-identity matching was not relaxed.
- Applied the same earlier-row correction rule to pending/running automatic tickets, retaining package, title, viewport revision, reliable tail and epoch checks. API errors remain terminal without an automatic paid retry loop.
- Background uncertainty/occlusion statuses cannot replace Candidates, Loading or retryable Error states. Existing candidate views remain present, including after collapsing and reopening the quick panel. Actual new reliable messages may still update candidates.
- For text-tail occlusion, use an available avatar-aligned bubble contour plus a margin, avoiding treating distant blank space below a complete bubble as blocked. Missing contours retain the conservative avatar-to-input fallback; existing image-control boundaries remain unchanged.
- Reduced default page-change debounce from 800ms to 300ms. WeChat's 2-second OCR cooldown, bounded continuous-event delay and 4-second local-read watchdog remain. This is not a guarantee of a 300ms end-to-end reply.
- Added privacy-safe CAPTURE_TIMING events for reading, history merge and total capture. Combined with existing model-request duration events, diagnostics can distinguish local read latency from API latency. Timing callbacks cannot turn a successful capture into an error or swallow capture cancellation.
- Relationship profiles, memory, Natural/Huangmao guides and existing DeepSeek non-thinking mode were retained; the deferred creator-video adaptation was not added.

## Speed investigation

- Existing APK main-system-prompt extraction found 0.2.5 at 1,006 characters and both 0.2.7/0.2.8 at 1,890 characters. The latter two have identical prompt hashes, so the latest slowdown cannot simply be attributed to an increase in this prompt. This comparison does not measure all per-request context or API latency.
- Previously saved, authorized screenshot fixtures were benchmarked on this computer with four warm-up runs and ten measured runs: median WechatBubbleDetector 2ms and WechatMediaGeometry 4ms. These are only host geometric calculations, not phone MLKit OCR, total capture or model/network timing.
- Confirmed local trigger-freeze and candidate-state replacement defects were repaired. Online model response speed was not measured, and no guarantee of faster API output is made.

## Verification

- Observed real assertion failures before fixing self-message generation, candidate-state replacement, default debounce, diagnostic allowlisting, consumed uncertain-anchor correction and pending-ticket correction. Some new API/geometry/timing helpers initially had compilation failures; these are not claimed as assertion red/green evidence.
- Latest full command: `:android-app:testDebugUnitTest :android-app:assembleDebug :android-app:assembleDebugAndroidTest --no-daemon`: BUILD SUCCESSFUL in 52s.
- Android host tests: **393 tests across 64 suites; 0 failures, 0 errors, 0 skips**. Previously authorized QQ layout and WeChat screenshot fixtures were supplied; no live phone read occurred.
- Backend: **18 passed**.
- Timing tests cover callback exceptions, propagated cancellation and subsequent capture not remaining Busy. UI tests use a real Robolectric OverlayWindow and verify actual candidate views are retained.
- Independent read-only review found the anchor correction defect; a failing test reproduced it before implementation. Follow-up review accepted the correction and found no new definite important issue. It did not run tests or operate the phone.
- `git diff --check` exited 0; pre-existing LF/CRLF conversion warnings remain.
- APK badging: `app.nextsay`, versionName **0.2.9**, versionCode **17**.
- APK signature verification succeeded using v2. Certificate SHA256: `bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`, matching previous debug releases.
- Release: `D:/agent/codex/nextsay/releases/NextSay-0.2.9-debug.apk`, **57,416,020 bytes**. The absent release target was checked before copying; no existing release was overwritten.
- Release and build APK SHA256 both: `0F5B9662862E70B7DBC0FA455956077106A38307E32F6D9EE1E67E2434DAF1CD`.

## Limits and next check

- This run did not install 0.2.9, control the phone, capture live chats, call the user's API or execute instrumentation tests. The instrumentation APK was built only. The last verified installed phone version remains 0.2.8/versionCode 16.
- Real HONOR/WeChat/QQ event delivery, trigger reliability, keyboard behavior, visual flicker and end-to-end latency require device verification. Component tests do not execute the complete accessibility-service lifecycle.
- Automatic generation observes the visible active unlocked chat, not every background notification. Image placeholders do not provide visual understanding. Uncertain ownership/occlusion can still defer a new generation without clearing existing candidates.
- Read-only review suggested additional forward-overlap correction and running-ticket correction coverage; current regressions primarily exercise contained-anchor correction and pending-ticket begin. These are non-blocking follow-up test suggestions, not claimed device evidence.
- Existing unrelated workspace changes were preserved. No commit, push, reset, uninstall, data clear or destructive cleanup was performed.

## Workflow influence

Systematic debugging separated trigger/state defects from unmeasured API speed. Test-driven development supplied failing reproductions, the review skill supplied an independent read-only check, and verification-before-completion required a fresh full suite and artifact version/signature checks before delivery.

## Subsequent USB installation — 2026-10-03

- The user explicitly requested installation after delivery. USB device `A9GVVB2A12014220` was online (HONOR RMO-AN00).
- Verified the release APK SHA256 before installation; the installed package was previously 0.2.8/versionCode 16.
- `adb install -r` returned **Success**. No uninstall or data-clear command was used.
- Phone package manager now confirms **0.2.9 / versionCode 17**, lastUpdateTime **2026-10-03 12:12:24** (Asia/Shanghai).
- Requested NextSay MainActivity launch; `am start` succeeded. No live chat capture, API call, candidate generation or instrumentation-test execution was performed.
- Installation/version are verified; real automatic trigger reliability, visual behavior and API latency are not yet established by this installation.
