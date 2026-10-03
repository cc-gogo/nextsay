# NextSay 0.2.6 — relationship reply guides

## Delivered

- Eight relationship-specific built-in guides; users do not need to write defaults themselves.
- Lover: Natural / Huangmao, defaults to Natural and is saved per contact in the object editor.
- Natural adapts contextual reply composition from Goutoujunshi: receiving emotion, lowering pressure, focused clarification and repair.
- Huangmao adapts concise confidence, playful agreement, light reframing and genuine warmth from the actual Huangmao rules/example/scenario files. Serious concerns and clear boundaries take precedence.
- Current instructions take precedence over personal preferences, shared relationship rules and built-in defaults. Examples are independently written teaching examples, not actual chat facts.
- Non-lover relationships cannot activate Huangmao; a per-round relationship change does not change the saved profile.
- Additive Room migration 2 → 3 retains existing encrypted profile/history fields and defaults older contacts to Natural; 1 → 2 remains registered.
- No new classification/model request, automatic sending, popup window, or automatic-generation opt-in.

## How to use

Open object management → choose the contact → edit profile → select 恋人 → choose 自然 or 黄毛 → save. The saved lover style is shown in the floating-ball menu and advanced panel. Other relationships use their own defaults automatically.

## Evidence

- Android full host suite: **325 tests, 0 failures, 0 errors, 0 skips** across 58 suites.
- Backend suite: **18 passed**.
- `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- Added store-level coverage for save → reopen → generation extras, per-contact isolation, normalization after relationship change, and merge inheritance/retention.
- Independent read-only review found no critical/important issue. Its optional store-coverage suggestion was implemented and included in the final full suite.
- APK badging confirms `app.nextsay`, versionName **0.2.6**, versionCode **14**. Signature verification passed (v2, Android debug certificate).
- `git diff --check`: no whitespace errors; Git reports existing LF/CRLF conversion warnings.
- APK: `D:/agent/codex/nextsay/releases/NextSay-0.2.6-debug.apk` (56,944,945 bytes).
- SHA256: `C8F40FF742AE65221EF364BA4A610C4092ADDA03AD784234F45BD6F9F6A05A0F`.

## Boundaries

- No phone installation/control, user API calls, or live model-quality evaluation in this run. Instrumentation APK was built, not executed on a phone.
- Local tests establish prompt routing, UI selection, database compatibility/persistence and request construction, not the quality of real model replies.
- Migration uses synthetic ciphertext to verify preservation; it is not a real-device keystore restoration test.
- Shao Ailun / 邵艾伦 was not independently verified; no faithful reproduction of that creator is claimed. Representative links/examples are needed for further adaptation.
- Source files and adaptation/license details are recorded in `2026-10-03-relationship-prompt-sources.md`; attribution is packaged in `android-app/src/main/assets/relationship-guide-notices.txt`.
- No commits, pushes, resets or cleanup of unrelated existing workspace changes.

## Skills used

using-superpowers, brainstorming, defuddle, test-driven-development, requesting-code-review, verification-before-completion. The external repositories were read as source material, not installed or executed as skills.

## Subsequent installation — 2026-10-03

- User explicitly requested installation on the connected phone.
- Device: HONOR RMO-AN00, USB serial A9GVVB2A12014220.
- Checked release hash against the recorded SHA256, then used `adb install -r`; returned `Success`. No uninstall or data clear performed.
- Phone package manager confirms versionName 0.2.6, versionCode 14, lastUpdateTime 2026-10-03 02:15:22 (Asia/Shanghai).
- Requested MainActivity launch; final foreground snapshot reports NextSay ContactSettingsActivity, not a chat app. No UI taps were performed.
- No chat capture, candidate generation, model connection test or instrumentation test was requested/executed in this installation run.
- This confirms installation/version, not live-model quality or complete device regression.
