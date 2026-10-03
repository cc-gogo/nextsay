# Contacts / memory / automatic candidates implementation

Spec: ../specs/2026-10-02-contacts-memory-auto-candidates-design.md
Execution: inline in existing user-authorized working checkout. Preserve baseline dirty changes.

## Global constraints and rulings

- User explicitly requests tests only at final stage: write regression cases during implementation, defer execution until verification. This overrides intermediate RED/GREEN runs; do not claim strict TDD.
- No phone install, biometric prompts, real API calls, private-chat inspection, Git commit/push, or publishing.
- Preserve provider settings, history ciphertext, exclusions and ball position.
- No automatic insert/send. Identity ambiguity excludes all old profile/history.
- Ruling: Android 13 screenshots include overlays. Keep views visible and mask their areas; obscured messages are unavailable, so conservative detection may miss messages until exposed. Android 14+ can capture the application window without overlays.

## Tasks

1. Add stable encrypted contact store, Room 1→2 additive migration, alias HMAC, account spaces and legacy import; regression tests for isolation, overlap/segments and relevance budgets.
2. Add contact management and long-press menu, lover relationship, shared rules, persistent details, memory editing/deletion/association and privacy confirmation.
3. Add bounded generation enrichment and explicit precedence, safe identity/round handling and automatic baseline detector.
4. Integrate foreground/debounce scheduler, manual/auto coordination, editor pause and stale-result cancellation. No API on opening auto-mode cached results.
5. Preserve attached quick window during refresh/capture, exclude overlay screenshot regions, cached presentation and ready indicator.
6. Final fresh read-only review, local unit/backend tests, Android build/instrumentation compilation, APK signature/hash; handoff honestly notes physical phone tests not performed.

## Shared interfaces

1→2: ContactStore CRUD and alias/account isolation; all mutations run on IO and transactional.
1→3: Confirmed contact local metadata never serialized; only bounded selected profile/memory fields enter DTO.
3→4: IncomingRoundDetector baseline/dedup/scroll guards; result epochs shared with manual flow.
4→5: Presentation visibility independent of generation state; no refresh-time removeView/hide.

## Review focus

Unknown/same-title contact leakage; migrated plaintext names; cancellation races/manual-auto overlap; lost editor draft; full-screen OCR contamination; unbounded/private prompts; selected memory controls; startup/lockscreen baseline; no hidden-window reopen; no destruction of prior history.

## Progress

- Setup: spec approved; applicable skills read; source interfaces inspected. No new implementation verified yet.
- Tasks 1–5: implementation written. Stable profiles/HMAC aliases, non-destructive Room schema migration with ciphertext transfer, settings/confirmation, bounded extras, conservative auto detector, manual/auto scheduler and persistent attached-window refresh are integrated.
- Final stage first run: Gradle unit tests + both APK builds succeeded (1m26s); backend 18 passed. New instrumentation tests compiled, not run because no phone installation allowed and no emulator is installed.
- Fresh read-only review in progress; final corrections and full rerun precede delivery. No commits or actual phone/API interactions performed.
- Final review findings addressed in one correction pass: nonbusy Waiting/cancellation state; clear attached candidates on identity changes; unlocked gate and screen-transition baseline reset; track IME/advanced/manual jobs and reject stale IME responses; bind screenshot exclusion geometry to frame and reject changed layouts; structural legacy-import metadata avoids arbitrary-title prefix collisions, ciphertext transferred per conversation. Alias association audit added, temporary instructions aligned with background round observations.
- Reviewer coverage minor addressed by extending synthetic instrumentation migration test through import, idempotence, ciphertext transfer and prefix-title/exclusion collision cases. This test is compiled but execution deferred under the no-install constraint.
- Ruling: Physical flicker/IME/OEM capture, actual WeChat/QQ recognition and Keystore/provider runtime behavior remain unverified this delivery — no authorized device/API run; computer verification cannot substitute for them. Prior 0.1.6 evidence is not claimed as verification of 0.2.0.
- Task 6: complete for the authorized computer-only deliverable. Final fresh suite: 261 JVM tests, 0 failures/errors/skips; 18 backend tests pass; both APK builds succeed; SQLite schema/FK/index comparison across 9 tables passes and legacy ciphertext retained; APK v2 signature matches previous certificate. Named 0.2.0 APK and hash recorded in handoff. No actual device tests counted as passed.
- Work remains in original codex/byok-direct-api checkout without committing, merging, pushing or deleting any prior artifacts. User asked for an APK, not branch integration; finishing preserves current branch/worktree.
