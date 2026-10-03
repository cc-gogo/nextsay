# NextSay MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. This repository must remain local and uncommitted unless the user later authorizes commits.

**Goal:** Build a locally testable FastAPI reply service and an Android 11+ companion app that extracts visible WeChat/QQ text on explicit user action, previews it, generates three replies, and inserts a selected reply without sending it.

**Architecture:** The backend is a small Python package with a strict request/response schema, deterministic mock provider, and OpenAI-compatible provider. The Android app is a native Kotlin accessibility service with cleanly separated node extraction, chat normalization, privacy redaction, overlay state, API transport, and guarded text insertion.

**Tech Stack:** Python 3.12, FastAPI, Pydantic v2, HTTPX, pytest; Kotlin, Android SDK 35, minimum SDK 30, AndroidX, Kotlin coroutines, Retrofit/OkHttp, JUnit.

## Global Constraints

- Work only in `D:\codex\nextsay`.
- Do not create Git commits or configure/push a Git remote.
- Android minimum version is Android 11 / API 30.
- Only `com.tencent.mm` and configured QQ packages may expose the trigger.
- Accessibility events must never initiate context upload or model calls.
- The user must preview context before upload.
- Replies may be inserted but never sent automatically.
- Screenshot OCR, full Chinese IME, group-chat attribution, billing, accounts, and analytics are excluded.
- Provider credentials exist only in backend environment variables.
- Backend logs must not contain conversation text or generated replies.
- The current machine lacks Android SDK, Gradle, and JDK 17; Android source will receive static/unit-level review, while APK compilation requires installing those prerequisites.

---

### Task 1: Backend Contracts And Deterministic Reply Service

**Files:**
- Create: `backend/pyproject.toml`
- Create: `backend/src/nextsay_backend/__init__.py`
- Create: `backend/src/nextsay_backend/schemas.py`
- Create: `backend/src/nextsay_backend/provider.py`
- Create: `backend/src/nextsay_backend/service.py`
- Create: `backend/tests/test_service.py`

**Interfaces:**
- Produces: `Message`, `ReplyRequest`, `ReplyCandidate`, `ReplyResponse` Pydantic models.
- Produces: `ReplyProvider.generate(request: ReplyRequest) -> list[ReplyCandidate]`.
- Produces: `ReplyService.generate(request: ReplyRequest) -> ReplyResponse`.

- [ ] Write tests proving empty messages are rejected, message count and length are bounded, and the mock provider produces exactly `concise`, `tactful`, and `natural` candidates.
- [ ] Run `python -m pytest backend/tests/test_service.py -v` and confirm it fails because the package does not exist.
- [ ] Define enums and Pydantic models with maximum 20 messages, 1000 characters per message, 1000-character instruction, and 500-character candidate text.
- [ ] Implement `MockReplyProvider` using the latest other-party message and deterministic Chinese templates so local development needs no external API.
- [ ] Implement `ReplyService` to reject duplicate/blank candidates and enforce all three styles.
- [ ] Run `python -m pytest backend/tests/test_service.py -v` and confirm all tests pass.

### Task 2: Prompt Builder And OpenAI-Compatible Provider

**Files:**
- Create: `backend/src/nextsay_backend/prompt.py`
- Create: `backend/src/nextsay_backend/openai_provider.py`
- Create: `backend/tests/test_prompt.py`
- Create: `backend/tests/test_openai_provider.py`

**Interfaces:**
- Consumes: `ReplyRequest`, `ReplyCandidate`, `ReplyProvider`.
- Produces: `build_messages(request: ReplyRequest) -> list[dict[str, str]]`.
- Produces: `OpenAICompatibleProvider.generate(request: ReplyRequest) -> list[ReplyCandidate]`.

- [ ] Write prompt tests asserting roles, relationship, draft, instruction, schema requirements, no-fabrication rule, and manual-send rule are present.
- [ ] Write HTTPX mock-transport tests for success, timeout, non-2xx status, malformed JSON, fenced JSON, and fewer than three candidates.
- [ ] Run both test files and confirm failures for missing modules.
- [ ] Implement a Chinese system prompt that requests JSON with three candidate objects and never exposes chain-of-thought.
- [ ] Implement the provider using `POST {base_url}/chat/completions`, bearer authentication, a 10-second timeout, and response parsing from `choices[0].message.content`.
- [ ] Strip a single Markdown JSON fence before Pydantic validation; reject all other malformed output.
- [ ] Run both test files and confirm all tests pass.

### Task 3: FastAPI Application And Privacy-Safe Logging

**Files:**
- Create: `backend/src/nextsay_backend/settings.py`
- Create: `backend/src/nextsay_backend/app.py`
- Create: `backend/tests/test_api.py`
- Create: `backend/.env.example`
- Create: `backend/README.md`

**Interfaces:**
- Consumes: `ReplyService`, `MockReplyProvider`, `OpenAICompatibleProvider`, `ReplyRequest`, `ReplyResponse`.
- Produces: `create_app() -> FastAPI` with `GET /health` and `POST /v1/replies`.

- [ ] Write API tests for health, mock reply success, invalid payload, request ID header, shared development token, and provider failure mapping.
- [ ] Attach a log-capture test proving message text and candidate text are absent from logs.
- [ ] Run `python -m pytest backend/tests/test_api.py -v` and confirm it fails for missing app code.
- [ ] Implement environment settings: `NEXTSAY_PROVIDER`, `NEXTSAY_API_KEY`, `NEXTSAY_BASE_URL`, `NEXTSAY_MODEL`, and `NEXTSAY_DEV_TOKEN`.
- [ ] Implement provider selection with `mock` as the safe default.
- [ ] Implement request IDs, latency/status metadata logging, a development bearer-token guard, and exception-to-HTTP-status mapping.
- [ ] Document virtual environment setup, local server startup, curl examples, and provider configuration without real secrets.
- [ ] Run `python -m pytest backend/tests -v` and confirm all backend tests pass.

### Task 4: Android Project And Context Domain Model

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `android-app/build.gradle.kts`
- Create: `android-app/proguard-rules.pro`
- Create: `android-app/src/main/AndroidManifest.xml`
- Create: `android-app/src/main/java/app/nextsay/context/ContextModels.kt`
- Create: `android-app/src/main/java/app/nextsay/context/NodeSnapshot.kt`
- Create: `android-app/src/main/java/app/nextsay/context/ContextNormalizer.kt`
- Create: `android-app/src/test/java/app/nextsay/context/ContextNormalizerTest.kt`

**Interfaces:**
- Produces: `NodeSnapshot(text, contentDescription, className, viewId, bounds, editable, focused)`.
- Produces: `ChatMessage(role, text, confidence)` and `ChatContext(sourceApp, messages, draft, confidence)`.
- Produces: `ContextNormalizer.normalize(packageName, nodes, screenWidth) -> ChatContext`.

- [ ] Write JVM tests for y-ordering, duplicate parent/child text, metadata filtering, draft exclusion, left/right role inference, and unsupported package rejection.
- [ ] Create an Android application project targeting SDK 35 with minimum SDK 30 and namespace `app.nextsay`.
- [ ] Implement immutable domain models with no Android dependencies except integer screen bounds represented by a local `ScreenRect` data class.
- [ ] Implement deterministic normalization rules and explicit `UNKNOWN` roles below the confidence threshold.
- [ ] Review Gradle dependency/version compatibility against Android Gradle Plugin 8.7 and Kotlin 2.0; compilation remains pending until JDK 17 and Android SDK are available.

### Task 5: Privacy Redaction And Accessibility Snapshot

**Files:**
- Create: `android-app/src/main/java/app/nextsay/privacy/TextRedactor.kt`
- Create: `android-app/src/test/java/app/nextsay/privacy/TextRedactorTest.kt`
- Create: `android-app/src/main/java/app/nextsay/accessibility/NodeTreeFlattener.kt`
- Create: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Create: `android-app/src/main/res/xml/accessibility_service_config.xml`
- Create: `android-app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `NodeSnapshot`, `ContextNormalizer`.
- Produces: `TextRedactor.redact(text: String) -> String`.
- Produces: `NodeTreeFlattener.flatten(root: AccessibilityNodeInfo) -> List<NodeSnapshot>`.
- Produces: `NextSayAccessibilityService.captureOnUserRequest() -> ChatContext?`.

- [ ] Write redaction tests for mainland phone numbers, email addresses, and numeric identifiers of 8 or more digits, including false-positive checks for dates and short numbers.
- [ ] Implement bounded depth-first traversal with maximum 2000 nodes and recycled-node-safe value copying.
- [ ] Configure accessibility package names for WeChat and common QQ variants, window-content retrieval, and no passive event processing beyond tracking the active package.
- [ ] Implement capture only through an explicit binder/controller callback from the overlay trigger.
- [ ] Ensure password fields, unsupported packages, and absent roots return no context.

### Task 6: Backend Client, Overlay State, And Candidate UI

**Files:**
- Create: `android-app/src/main/java/app/nextsay/api/ApiModels.kt`
- Create: `android-app/src/main/java/app/nextsay/api/NextSayApi.kt`
- Create: `android-app/src/main/java/app/nextsay/api/NextSayRepository.kt`
- Create: `android-app/src/main/java/app/nextsay/overlay/OverlayState.kt`
- Create: `android-app/src/main/java/app/nextsay/overlay/OverlayController.kt`
- Create: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Create: `android-app/src/test/java/app/nextsay/overlay/OverlayControllerTest.kt`

**Interfaces:**
- Consumes: reviewed `ChatContext` and `TextRedactor` output.
- Produces: `NextSayRepository.generate(request) -> Result<ReplyResponseDto>`.
- Produces: state machine `Idle -> Preview -> Loading -> Results|Error`.

- [ ] Write state-machine tests for explicit preview, cancellation, retry without recapture, timeout, three-candidate success, and app-change invalidation.
- [ ] Implement Retrofit DTOs matching `/v1/replies` and an interceptor for the revocable development token.
- [ ] Implement a 10-second call timeout and normalize transport/provider failures into user-safe messages.
- [ ] Build an accessibility overlay with one circular trigger and an unframed bottom panel containing context preview, instruction field, relationship menu, generate button, three candidate rows, retry, and dismiss.
- [ ] Keep all candidate text selectable and ensure loading/error states cannot resize the trigger.

### Task 7: Guarded Reply Insertion And End-To-End Local Demo

**Files:**
- Create: `android-app/src/main/java/app/nextsay/insertion/ReplyInserter.kt`
- Create: `android-app/src/test/java/app/nextsay/insertion/ReplyInserterPolicyTest.kt`
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayController.kt`
- Create: `README.md`

**Interfaces:**
- Consumes: selected candidate and the active accessibility root.
- Produces: `ReplyInserter.insert(root, expectedPackage, text) -> InsertResult`.

- [ ] Write policy tests proving blank text, package changes, password fields, non-editable nodes, and missing focus cannot insert.
- [ ] Implement focused-editable-node lookup and `ACTION_SET_TEXT` while preserving the existing draft for undo.
- [ ] Provide clipboard copy only as an explicit fallback when accessibility insertion fails.
- [ ] Search the Android source for `ACTION_CLICK`, send-button IDs, and autonomous event-triggered API calls; the expected result is none.
- [ ] Add root documentation covering architecture, permissions, backend startup, Android prerequisites, privacy behavior, and a manual WeChat/QQ test checklist.
- [ ] Run `python -m pytest backend/tests -v` and record the passing result.
- [ ] Run all available static searches and record that APK/JVM tests are pending because Android SDK, Gradle, and JDK 17 are not installed.

