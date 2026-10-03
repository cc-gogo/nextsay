# NextSay 0.2.10 — fast capture path and background history

## Approved scope

- Prioritize the first candidate using profile details and a small recent memory pool. Load farther history in the background; do not delete history or disable memory.
- Continue automatic generation for reliable new OTHER and ME messages. Keyboard/layout changes alone must not create a new round. Preserve candidate views while refreshing.
- Cover-install and run local synthetic device performance tests only. No live chat capture, provider API call, typing, pasting or message sending is authorized in this run.

## Root cause and changes

- The service still explicitly supplied a 1,500ms debounce despite the previous default change. It now uses the actual 300ms default, retaining the 2-second WeChat OCR cooldown.
- Authorized privacy-safe timing evidence put OCR median at approximately 548ms, identity/history median at 3,928ms and capture median at 4,602ms. The measured model calls were 585–1,184ms and all manual.
- Android Keystore decryption was repeated across complete history pools. The same phone's 0.2.9 synthetic benchmark used 601 in-memory records: cold/warm observe 18,921/18,305ms; cold/warm prepare 19,042/18,861ms. This is a synthetic large-history case, not the user's actual history count.
- A bounded process-only ciphertext-keyed plaintext cache avoids repeated hardware decrypts without writing unencrypted history to disk. Management edits produce different ciphertext and remain visible.
- Capture returns the visible frame after identity resolution and queues persistence on one FIFO worker, rather than waiting for history saving. Once initialized, profile resolution does not wait for the history-writer mutex.
- Persistence filters the newest segment before decryption. Generation preview uses profile-only extras; actual overlay and IME requests include memory, with at most twelve cold history decrypts plus already warmed farther rows.
- Background warming is separate from generation and catches ordinary failures; cancellation propagates. Failed warming records a privacy-safe history diagnostic rather than crashing the app.
- Scroll revision waits for message evidence, preserving an unchanged reliable tail through keyboard subsets and corrections of earlier uncertain rows.
- Queued saves carry an epoch; object-local invalidations reject only that object's stale frames. Global clearing/account changes reject all older frames. Deletion and saving share a mutex. Read snapshots are guarded against deletion-time cache re-entry.

## Verification status

- Final full command `:android-app:testDebugUnitTest :android-app:assembleDebug :android-app:assembleDebugAndroidTest --no-daemon`: BUILD SUCCESSFUL in 2m 6s. Authorized pre-existing screenshot/layout fixtures were supplied; no new real-chat reading occurred.
- Android host suite: **414 tests / 68 suites, zero failures, errors or skips**. Backend: **18 passed**. `git diff --check` exited 0 with existing LF/CRLF conversion warnings.
- Targeted tests reproduced cold memory cost, writer lock blocking, keyboard subset/earlier uncertain-row correction, stale queued history, cross-object queue loss, corrupt warm history, deleted-row caching, deleted-profile caching, and tokens captured during DAO deletion before the respective fixes. No-op API skeleton compilation failures are not counted as assertion red/green evidence.
- Independent read-only review identified deletion/cache and asynchronous warm-failure boundaries; follow-up accepted the fixes with no remaining definite Critical/Important issue in scope. Review did not execute builds or operate the phone.
- APK badging confirms `app.nextsay`, **0.2.10 / versionCode 18**. Signature verification exited 0; certificate SHA256 matches prior debug builds: `bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`.
- Release `D:/agent/codex/nextsay/releases/NextSay-0.2.10-debug.apk`: **57,476,884 bytes**, SHA256 **B280531016372D7EE26F7903A32D28DE746A18EC117AC79F7DC9A3187C644A1E**. The absent destination was checked before copying; no existing release was overwritten.
- USB cover-install of target and matching local test APK both returned **Success**. Phone package manager confirms **0.2.10 / code18**, lastUpdateTime **2026-10-03 13:04:53** (Asia/Shanghai).
- Same phone/synthetic 601-row instrumentation test returned **OK (1 test)** in **38.832s**; its setup includes 601 real Keystore encryptions, and total test time includes the entire background warmup, not just the foreground request path.

| Stage | 0.2.9 duration / decrypts | 0.2.10 duration / decrypts |
| --- | --- | --- |
| resolve cold | 280ms / 7 | 154ms / 3 |
| resolve warm | 263ms / 7 | 24ms / 0 |
| observe cold | 18,921ms / 601 | 74ms / 1 |
| observe warm | 18,305ms / 601 | 38ms / 0 |
| prepare cold | 19,042ms / 610 | 477ms / 11 |
| prepare warm | 18,861ms / 610 | 86ms / 0 |
| background memory warm | not separated | 18,332ms / 589 |
| prepare after background warm | not separated | 86ms / 0 |

The background hardware work is deliberately not removed: it completed after the first preparation already returned. Database row count stayed 601. This benchmark is one local component comparison, not a live-service responsiveness guarantee.

## Workflow influence

Systematic debugging isolated the local identity/history bottleneck from OCR and unmeasured provider latency. Regression tests and independent review added keyboard, background-failure and deletion-concurrency safeguards. Verification-before-completion required a fresh full build/test suite, signature/version checks, successful cover-install and an actual phone benchmark before delivery.

## Limits

- Benchmarks use synthetic records in an in-memory Room database with the phone's real Keystore, not its real conversation database or provider API.
- Exact/confirmed object paths are accelerated. Unmatched/ambiguous identity fallback retains conservative historical evidence checks and can still take longer on cold history.
- A competing memory warmup may be skipped and retried by later captures. Farther history remains on disk; the bounded process cache is not a complete always-loaded archive.
- Real WeChat/QQ event delivery, OCR quality, keyboard behavior, visual flicker and end-to-end model latency are not established by synthetic component tests.
- Existing unrelated dirty/untracked changes are preserved. No commit, push, reset, uninstall or user-data clear is performed.

## Follow-up: keyboard viewport fix

- User supplied a real screenshot showing the keyboard open, a visible new incoming bubble, and stale candidates. Privacy-safe diagnostics showed repeated OCR/history captures but automatic state stopped at `uncertain_tail`; manual generation succeeded in 1,116ms. This isolates the failure to capture/ownership gating, not provider latency.
- Root cause found: when the IME window was visible but Android exposed no editable input node, the capture boundary still subtracted the fallback input guard. The newest bubble above the keyboard could be excluded or its geometry evidence lost.
- Added a regression for an IME boundary without an input node and changed `ChatViewportBounds`/service boundary selection to use the IME top directly only in that case. Missing IME data and invalid/offscreen nodes retain the prior conservative fallback.
- Fresh verification: Android unit suite **415 tests, 0 failures/errors/skips**, backend **18 passed**, debug APK assemble and androidTest APK assemble successful. Cover-install returned `Success`; phone package manager confirms **0.2.10 / versionCode 18**, lastUpdateTime `2026-10-03 13:32:28` Asia/Shanghai. APK SHA256 `07F87850A9C72DF55165DD0E8495697216B5A0BDCB544EE45305E4DDCC15CE79`; debug certificate unchanged.
- This remains a capture-gating fix, not a claim that every live chat will auto-generate. A real-chat run would require explicit authorization to inspect the active screen and may call the configured provider.
