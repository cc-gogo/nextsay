# NextSay 0.2.4 — QQ capture and visible version

## Root cause and changes

- Device's QQ exposes message bodies as `mjo`, title as `34v`; previous allowlists knew only `mjh`/`mjn`, `304`/`32z`. Added verified IDs, retaining old support and excluding avatar/chrome nodes from message text.
- QQ OCR title is left-aligned, unlike WeChat; accept QQ's header alignment and exclude its online-status row. Without bubble/avatar evidence, QQ OCR sender roles are UNKNOWN with confidence capped at .4, not guessed from text bounds. WeChat parsing remains unchanged.
- Homepage now displays generated BuildConfig version and build code without requiring a diagnostic error.
- No changes to overlay capture/refresh visibility, provider configuration, contact binding, or user memory.

## Verification

- TDD regressions first failed for missing IDs/version label/title selection and wrong QQ OCR sender inference.
- Authorized local phone XML integration: seven message nodes; top clipped bubble lacks avatar and is UNKNOWN, six remaining messages are ME with avatar evidence; latest message ME. No OCR fallback.
- Full Android host suite: 303 tests, zero failures/errors/skips, with approved QQ layout and prior WeChat geometry fixtures enabled.
- Backend: 18 tests passed. Debug app and instrumentation APK builds successful.
- Scoped independent read-only review: no remaining findings.
- Cover-install on HONOR RMO-AN00 / Android 13 succeeded. Package manager reports 0.2.4 / code 12. Homepage screenshot verifies visible `版本 0.2.4 · 构建 12`.

## Artifact

- `releases/NextSay-0.2.4-debug.apk`
- SHA256: `76174F53A205FE5A1B6BFEAD0B782AF97C3008CB5F051EDA86171DFFFB9339D4`
- Certificate SHA256: `bc776ece608fae7e54ccf2b0b9d7b154e35013fe6029be3e0d4253dd3bf4050a`
- Phone capture fixtures and screenshots remain ignored/local, never committed.

## Device context verification pending

User authorized installation/homepage and read-only context preview for the same QQ test chat, no API calls, generation, typing, pasting or sending. Temporarily set `暂停所有自动生成` ON for the local inspection; original state OFF must be restored after verification (outside QQ to avoid triggering generation).

At last screen check phone had moved to system accessibility settings, displaying a confirmation to disable NextSay. Requested user cancel disable and return to the same test chat, then avoid simultaneous operation. Do not claim actual context preview verified yet.

Diagnostics count baseline and post-install/pre-check unchanged: GENERATION_STARTED 61, GENERATION_SUCCEEDED 55, GENERATION_FAILED 24, CANDIDATE_REQUESTED 21, CAPTURE_FAILED 9, APP_CRASHED 1. Check counts after preview. Long-press ball → 更多回复选项 opens capture/preview only; never tap generate or candidate rows. Do not use generic UIAutomator dump while inspecting overlay: its automation session can temporarily suppress/reconnect accessibility services and disturb overlay state. Prefer plain screenshots, or instrumentation UiAutomation with FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES if needed.
