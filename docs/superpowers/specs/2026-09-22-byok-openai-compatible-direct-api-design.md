# NextSay BYOK OpenAI-Compatible Direct API Design

## Status

Approved on 2026-09-22.

## 1. Goal

NextSay will support a bring-your-own-key (BYOK) mode in which every user configures one OpenAI-compatible model service on their own Android device. The user supplies the API base URL, API key, and model name. NextSay stores that configuration locally and calls the configured provider directly from the phone.

The completed app must generate replies without a developer computer, ADB connection, local Python process, or NextSay-operated public backend. The phone only needs network access to the provider selected by the user.

## 2. Agreed Product Scope

- Support one saved provider configuration at a time.
- Support the OpenAI-compatible Chat Completions contract.
- Collect three values from the user:
  - API base URL, such as `https://api.deepseek.com/v1`
  - API key
  - model name, such as `deepseek-chat`
- Provide a test-connection action before normal use.
- Store the API key in encrypted app-private storage backed by Android Keystore.
- Allow the user to edit or replace the saved configuration later.
- Provide privacy-safe local diagnostics that the user can copy or export for support without ADB.
- Keep the existing Python backend source in the repository, but do not require it for direct mode.

## 3. Non-Goals

- Multiple saved provider profiles or provider switching.
- Provider-specific SDKs and proprietary, non-OpenAI-compatible protocols.
- Bundling a shared developer API key in the APK.
- Account registration, subscriptions, billing, quota resale, or centralized usage tracking.
- Synchronizing API configuration across devices.
- Streaming candidate text in the first version.
- Automatic upload of diagnostics, crash reports, or analytics.

## 4. User Experience

### 4.1 First Run and Missing Configuration

The main activity checks whether a complete provider configuration exists. If not, it shows a prominent explanation that reply generation requires the user's own OpenAI-compatible service configuration and provides a direct entry into API settings.

Overlay and IME generation attempts made before configuration exists must stop locally and show a concise Chinese message directing the user to configure the service. They must not attempt a network request.

### 4.2 API Settings

The settings surface contains:

- `API 地址`
- `API Key`
- `模型名称`
- `测试连接`
- `保存`

The API key field uses password masking and supports an explicit show/hide control. Editing an existing configuration must not expose the stored key as plain text unless the user explicitly reveals it.

The settings surface explains that conversation text used for reply generation is sent directly to the configured third-party model service and is subject to that provider's privacy policy.

### 4.3 URL Rules

The user enters a base URL rather than a full operation URL. NextSay:

1. trims whitespace;
2. removes trailing slashes;
3. requires `https://` for normal saved configurations;
4. appends `/chat/completions` for requests.

For local development only, debug builds may accept cleartext `http://` addresses. Release behavior must reject them.

### 4.4 Test Connection

`测试连接` validates all three fields and sends a minimal Chat Completions request to the currently entered values. It does not require the values to be saved first.

The test reports one of these user-facing outcomes:

- connection successful;
- invalid URL;
- authentication rejected;
- model not found or unavailable;
- provider rate limit or insufficient quota;
- incompatible response;
- network unavailable or timed out;
- provider returned another HTTP error.

A successful test enables saving. Changing any field after a successful test invalidates that test result and disables saving until the edited values pass again. If a previously saved configuration is edited, generation continues using the last successfully saved configuration until the new values pass the test and are saved.

The test request uses a minimal prompt and asks for a very short response. The UI notes that the provider may charge a negligible amount for the test.

### 4.5 Error Support and Diagnostic Export

Every actionable generation or connection-test failure displays a stable error code and a `复制诊断信息` action. The settings surface also provides `诊断日志`, where the user can:

- view the most recent error summary;
- copy a compact diagnostic summary;
- export the complete local diagnostic log as a UTF-8 text file through the Android share sheet;
- clear all saved diagnostic data.

The export flow requires no development computer, data cable, ADB connection, NextSay account, or NextSay-operated server. A user can attach the exported file directly to a support conversation.

## 5. Android Architecture

### 5.1 Provider Configuration

Introduce a provider configuration model with:

```text
baseUrl: String
apiKey: String
model: String
```

A configuration is usable only when all three normalized values are nonblank and the URL passes validation.

### 5.2 Secure Configuration Store

Create a small configuration-store interface so UI and generation code do not depend on storage details. The production implementation stores non-secret metadata in app-private preferences and encrypts the API key with an Android Keystore-managed AES key.

The API key must never be included in logs, exceptions shown to the user, analytics, screenshots produced by the app, or Android backup data. Clearing application data removes the configuration.

### 5.3 OpenAI-Compatible Client

Add an Android client responsible for:

- constructing the normalized `/chat/completions` URL;
- applying `Authorization: Bearer <user key>`;
- sending the configured model name;
- converting the existing NextSay conversation, relationship, draft, and instruction into chat messages;
- requesting three structured reply candidates;
- parsing and validating the provider response;
- mapping transport and provider errors into stable domain errors.

The client must not log authorization headers, request bodies, response bodies, candidate text, or conversation text.

The first version uses non-streaming requests and the existing ten-second timeout. If a provider rejects the optional JSON response-format parameter as unsupported, the client retries once without that parameter while retaining the explicit JSON-only prompt. It must never retry authentication, quota, or generic server failures automatically.

### 5.4 Repository Integration

`NextSayRepository` continues to expose the existing generation behavior to overlay and IME callers. Its network dependency changes from the custom NextSay `/v1/replies` API to the direct OpenAI-compatible client. This keeps capture, redaction, overlay state, and IME flows isolated from provider protocol details.

Existing local redaction remains in place before request construction. The provider receives only the redacted conversation context already selected for generation.

### 5.5 Dependency Construction

Provider configuration is loaded when a generation begins rather than permanently embedding URL or credentials in a singleton created at process start. Updating settings therefore takes effect on the next generation without restarting the accessibility service or IME.

### 5.6 Privacy-Safe Diagnostic Recorder

Add a structured diagnostic recorder with a narrow typed API. Callers record predefined event types and safe fields instead of arbitrary strings. This prevents request or conversation content from reaching the log accidentally.

The recorder stores a rotating log in app-private storage with these fixed limits:

- at most 200 events;
- at most 256 KiB total;
- events older than seven days are removed;
- clearing application data or using `清除诊断日志` removes all events.

Safe diagnostic fields include:

- event timestamp and random event ID;
- app version and build type;
- Android version and device manufacturer/model;
- trigger surface: main activity, overlay, or IME;
- configured API scheme and host, excluding path parameters and query strings;
- configured model name;
- HTTP status code and request duration;
- normalized error category, such as DNS, connection, TLS, timeout, authentication, quota, model unavailable, incompatible response, or internal error;
- accessibility-service and IME enabled state;
- predefined state transitions that contain no user text;
- exception class and sanitized stack frames for unexpected internal failures.

The recorder must reject or remove:

- API keys, authorization headers, and other credentials;
- conversation text, contact or conversation titles, drafts, instructions, prompts, and candidate replies;
- HTTP request and response bodies;
- full URLs containing paths, query strings, fragments, or user information;
- raw exception messages unless they pass explicit field-level sanitization.

Release builds write only to this private recorder. Debug builds may additionally mirror the same sanitized structured events to Logcat. Neither build writes raw network bodies or authorization headers.

### 5.7 Local Crash Recovery

Install a minimal crash recorder that captures the exception class, sanitized application stack frames, timestamp, app version, and active surface before delegating to Android's existing uncaught-exception handler. It must not store the raw exception message or arbitrary thread state.

On the next app launch, NextSay indicates that a local crash report is available and offers to export or clear it. Crash data remains local until the user explicitly shares it.

## 6. Data Flow

1. The user installs and opens NextSay.
2. The user enters their API base URL, API key, and model name.
3. NextSay tests the entered configuration directly against the provider.
4. On success, NextSay encrypts and saves the configuration locally.
5. The user triggers reply generation in the overlay or IME.
6. Existing capture and redaction logic prepares the conversation context.
7. The Android client builds an OpenAI-compatible request and sends it directly to the configured provider over HTTPS.
8. The client validates exactly three reply candidates.
9. Existing UI surfaces display the candidates; insertion remains manual and never sends the message automatically.
10. If an error occurs, NextSay records only structured, privacy-safe diagnostic metadata and lets the user explicitly copy or export it.

## 7. Compatibility Contract

A provider is considered compatible when it accepts:

- `POST <base-url>/chat/completions`;
- bearer-token authorization;
- a JSON request containing `model`, `messages`, and standard generation options;
- an OpenAI-style response at `choices[0].message.content`.

NextSay does not promise compatibility with endpoints that use different authentication headers, request schemas, or response schemas. Such providers require a future explicit adapter.

## 8. Error Handling

- Configuration errors are detected before capture or network activity where possible.
- Authentication and quota errors direct the user back to settings without deleting the saved configuration.
- Malformed provider responses do not show raw response text, because it may contain private or unsafe content.
- Generation failure leaves the current conversation and draft untouched and allows an explicit retry.
- A settings test must be cancellable when the activity closes or the user starts another test.
- Each surfaced failure receives a stable error code that also appears in the diagnostic export.
- Diagnostic recording failure must never replace or crash the original user flow.

## 9. Security and Privacy

- Each user supplies and pays for their own provider credentials.
- No provider credential is bundled in the APK.
- Android application sandboxing protects the stored data from ordinary apps; Android Keystore encryption adds protection for the key at rest.
- A compromised or rooted device remains outside the security guarantee and is disclosed as a limitation.
- Requests go directly from the user's phone to the provider URL they entered.
- Diagnostic data stays in app-private local storage until the user explicitly exports it.
- Diagnostic exports include the provider scheme/host and model name, but never the API key, URL path/query, or conversation content.
- The existing promise that NextSay does not automatically send chat messages remains unchanged.

## 10. Testing and Acceptance

### Unit Tests

- URL normalization, HTTPS enforcement, and operation-path construction.
- Configuration completeness and safe replacement behavior.
- API-key encryption/decryption behavior through a testable storage boundary.
- Authorization and model request construction without leaking secrets in failures.
- parsing valid fenced and unfenced JSON candidate responses;
- rejection of blank, duplicate, missing, or extra candidates;
- HTTP and network error mapping;
- one compatibility retry when JSON response format is unsupported;
- missing-configuration behavior for repository, overlay, and IME entry points.
- diagnostic ring-buffer size and age limits;
- diagnostic field allow-listing and secret/content rejection;
- stable error-code correlation between UI and exported events;
- crash metadata recovery without raw exception messages;
- clearing and exporting diagnostic data.

### Integration Verification

- Existing backend tests remain passing because backend source is retained.
- All Android JVM tests pass.
- Debug APK assembles successfully.
- A manually entered OpenAI-compatible test configuration succeeds.
- Changing URL, key, or model takes effect without reinstalling or restarting services.
- Disconnecting the development computer does not affect generation over the phone's own network.
- No source or packaged resource contains a real API key.
- A connection or generation failure can be diagnosed from an exported file without ADB.
- Export inspection confirms that API keys, authorization headers, chat content, prompts, drafts, candidates, and raw network bodies are absent.

## 11. Delivery Result

The deliverable is an installable Android APK that operates independently after the user enters and validates one OpenAI-compatible API configuration. A NextSay-operated backend is not required for this mode.
