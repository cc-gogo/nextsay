# NextSay 0.2.7 — unified managed contact profiles

## Delivered behavior

- The management-page profile is the single source of relationship, lover reply mode, details, preferences and memory. The overlay no longer offers a competing temporary relationship selector.
- Saving a managed profile makes it usable for the uniquely identified matching chat in the same platform/account space. Equivalent whitespace/case in a title does not require a second binding step.
- Existing edited legacy profiles are compatible in the default account space, retain history, and cannot silently activate automatic paid generation. Unedited imports do not become trusted personal profiles automatically.
- Each generation prepares context from the latest stored profile. Repository request construction gives that relationship priority over stale panel parameters; UI and generation use the same material.
- Namesakes require an explicit object choice, which persists. Fuzzy letter substitutions alone do not establish identity. Different accounts/platforms remain isolated.
- Unlinking removes equivalent spacing aliases and prevents silent replacement by another namesake. Binding-history labels distinguish saving, selection, unlinking and merging.
- Contact-menu read failures clear cached identity/material and show an object-management error entry. Cancellation still propagates normally.

## Verification

- Full Android host suite: 344 tests across 59 suites; 0 failures, 0 errors, 0 skips.
- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- Real local Activity save → Store/Room → prepared context → Panel regression is included, with only device encryption replaced for the host environment.
- The menu damaged-ciphertext regression was observed failing before the error boundary, then passed in the full suite; cancellation propagation also passed.
- Backend: 18 passed. Initial invocation without `PYTHONPATH` could not collect four modules; rerunning with `backend/src` configured passed the entire suite.
- Independent read-only review confirmed the last menu exception blocker was resolved, with no new related blocker. Review does not establish live-device behavior.
- `git diff --check`: exit 0; existing Git LF/CRLF conversion warnings remain.
- APK badging confirms application `app.nextsay`, versionName 0.2.7, versionCode 15. APK v2 signature verification passed; certificate matches the existing debug signer.

## Artifact

- Path: `D:/agent/codex/nextsay/releases/NextSay-0.2.7-debug.apk`
- Size: 57,340,296 bytes.
- SHA256: `4146DBFB50DAED51FBE7B4F61199D5771E9F10AC05844EC01C43E5CE7BD62EA1`
- Debug certificate SHA256: `bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`

## Boundaries and installation status

- Not installed in this development run. An optional 0.2.7 USB-install question was sent; no answer has arrived. Previous installation authorization for 0.2.6 was not reused.
- No phone interaction, chat read, user API request, real-model evaluation, uninstall or data clear in this run. Instrumentation APK was built, not executed on a phone.
- Local tests validate profile routing and request construction, not real chat-recognition reliability or model reply quality. Actual Android Keystore restoration was not tested here.
- Existing user changes and data were preserved; no commit, push, reset or unrelated cleanup.

## Skills used

Systematic debugging (including root-cause tracing), test-driven development (including writing-good-tests), requesting code review, receiving code review, verification before completion.

## Subsequent USB installation — 2026-10-03

- User explicitly requested installation on the connected phone.
- USB device A9GVVB2A12014220, HONOR RMO-AN00, was authorized and connected. Release SHA256 matched the artifact above.
- `adb install -r` returned `Success`; no uninstall or data clear was performed.
- Phone package manager confirms versionName 0.2.7, versionCode 15, lastUpdateTime 2026-10-03 10:41:14 (Asia/Shanghai).
- Requested NextSay MainActivity launch. No chat read, model request, candidate generation or phone regression test was performed during installation.
- Installation/version verification does not establish real chat-recognition or model-reply quality.
