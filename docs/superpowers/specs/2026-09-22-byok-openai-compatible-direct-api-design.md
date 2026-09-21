# NextSay BYOK OpenAI-Compatible Direct API Design

## Status

Approved conversational direction, pending written-spec review.

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
- Keep the existing Python backend source in the repository, but do not require it for direct mode.

## 3. Non-Goals

- Multiple saved provider profiles or provider switching.
- Provider-specific SDKs and proprietary, non-OpenAI-compatible protocols.
- Bundling a shared developer API key in the APK.
- Account registration, subscriptions, billing, quota resale, or centralized usage tracking.
- Synchronizing API configuration across devices.
- Streaming candidate text in the first version.

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

## 9. Security and Privacy

- Each user supplies and pays for their own provider credentials.
- No provider credential is bundled in the APK.
- Android application sandboxing protects the stored data from ordinary apps; Android Keystore encryption adds protection for the key at rest.
- A compromised or rooted device remains outside the security guarantee and is disclosed as a limitation.
- Requests go directly from the user's phone to the provider URL they entered.
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

### Integration Verification

- Existing backend tests remain passing because backend source is retained.
- All Android JVM tests pass.
- Debug APK assembles successfully.
- A manually entered OpenAI-compatible test configuration succeeds.
- Changing URL, key, or model takes effect without reinstalling or restarting services.
- Disconnecting the development computer does not affect generation over the phone's own network.
- No source or packaged resource contains a real API key.

## 11. Delivery Result

The deliverable is an installable Android APK that operates independently after the user enters and validates one OpenAI-compatible API configuration. A NextSay-operated backend is not required for this mode.
