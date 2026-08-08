# NextSay Android MVP Design

Date: 2026-08-08
Status: Approved direction, pending written-spec review

## 1. Product Goal

NextSay is an Android reply assistant that helps users respond inside chat apps without switching to a separate AI application or copying generated text manually.

The MVP validates one workflow:

1. The user opens a one-to-one text conversation in WeChat or QQ.
2. The user explicitly taps the NextSay accessibility overlay.
3. NextSay extracts the currently visible text conversation from the active accessibility tree.
4. The user can review the extracted context and optionally provide an instruction.
5. The backend returns three short reply candidates.
6. The user selects a candidate, which NextSay inserts into the focused chat input field.
7. The user reviews and sends the message manually.

The MVP is a companion overlay, not a replacement Chinese keyboard. Users keep their existing keyboard. A full input method can reuse the same context and backend modules after the core experience is validated.

## 2. Success Criteria

The MVP succeeds when a supported Android device can complete the following flow in current WeChat or QQ versions:

- Extract at least the last incoming visible plain-text message.
- Preserve visible message order and classify messages as `me`, `other`, or `unknown` when the UI exposes sufficient geometry.
- Show the extracted context before any network request.
- Generate exactly three distinct reply candidates within a 10-second timeout.
- Insert a selected candidate into the current editable field without sending it.
- Avoid collecting context until the user explicitly taps the NextSay control.

## 3. Scope

### Included

- Android 11 and newer.
- WeChat package `com.tencent.mm`.
- QQ package identifiers discovered during device testing, configured through a package allowlist.
- One-to-one conversations containing visible plain text.
- Accessibility-tree extraction.
- Three response styles: concise, tactful, and natural.
- Optional user instruction, such as "This is my manager; explain that the task may be delayed."
- OpenAI-compatible model providers through a NextSay backend.
- Explicit context preview, retry, insert, and dismiss actions.
- Local redaction for obvious phone numbers, email addresses, and long numeric identifiers before upload, with a preview of the final payload.

### Excluded

- iOS.
- A replacement Chinese keyboard.
- Automatic sending or clicking chat-app send controls.
- Background or continuous conversation monitoring.
- Group-chat speaker attribution.
- Images, stickers, voice messages, transfers, links, quoted cards, and mini programs.
- Scrolling or retrieving off-screen chat history.
- Screenshot OCR in the first implementation.
- Accounts, subscriptions, billing, analytics, or long-term conversation memory.

## 4. Architecture

The repository is a monorepo:

```text
nextsay/
|-- android-app/
|   |-- accessibility/   # Active-window access and node traversal
|   |-- context/         # App adapters and normalized message model
|   |-- overlay/         # Trigger, context preview, candidate panel
|   |-- insertion/       # Focused-field discovery and ACTION_SET_TEXT
|   |-- api/             # Backend client and transport DTOs
|   `-- privacy/         # Allowlist, redaction, local data lifetime
|-- backend/
|   |-- api/             # FastAPI endpoints
|   |-- providers/       # OpenAI-compatible provider adapter
|   |-- prompts/         # Chinese reply-generation policy
|   `-- schemas/         # Validated request and response models
`-- docs/
```

### Android Components

`NextSayAccessibilityService` is limited to the configured chat packages. It exposes a user-triggered snapshot operation and does not initiate model calls from passive accessibility events.

`ContextExtractor` walks `rootInActiveWindow`, collecting node text, content descriptions, class names, view IDs, and screen bounds. It excludes the NextSay overlay, current editable draft, keyboard UI, timestamps, status labels, navigation labels, and duplicate parent/child text.

App-specific adapters normalize the extracted nodes:

- `WeChatContextAdapter`
- `QqContextAdapter`
- `GenericChatContextAdapter` as a low-confidence fallback

Each adapter returns:

```json
{
  "sourceApp": "wechat",
  "messages": [
    {"role": "other", "text": "Can this be finished today?", "confidence": 0.91},
    {"role": "me", "text": "I am checking the final data.", "confidence": 0.84}
  ],
  "draft": "",
  "confidence": 0.87
}
```

Role inference uses message bounds relative to screen width and app-specific accessibility structure. If role confidence is insufficient, the role remains `unknown`; the UI must not silently invent speaker identity.

`OverlayController` owns four states:

- Idle trigger
- Context preview
- Loading
- Candidate or error result

`ReplyInserter` locates the currently focused editable accessibility node and uses `ACTION_SET_TEXT`. It replaces only the current draft after showing the exact result. It never finds or clicks a send button.

### Backend Components

The FastAPI backend exposes:

```text
GET  /health
POST /v1/replies
```

`POST /v1/replies` accepts normalized messages, current draft, optional instruction, relationship type, and locale. The backend validates payload size, applies the system prompt, calls an OpenAI-compatible provider, and validates structured output.

The response contract is:

```json
{
  "candidates": [
    {"style": "concise", "text": "..."},
    {"style": "tactful", "text": "..."},
    {"style": "natural", "text": "..."}
  ],
  "requestId": "..."
}
```

The provider API key exists only on the backend. The Android application receives a development backend URL and a revocable development token through local configuration.

## 5. Data Flow And Privacy

1. No chat data is collected during normal typing or passive accessibility events.
2. Tapping the overlay creates an in-memory accessibility snapshot.
3. Text extraction and initial filtering happen locally.
4. The user sees and can edit or remove extracted messages.
5. Local redaction runs before submission.
6. Only the reviewed normalized text payload is transmitted over HTTPS.
7. The backend does not persist prompts, chat text, or model responses.
8. Operational logs contain request ID, latency, status code, provider name, and token counts only.
9. Android clears the snapshot when the panel closes, the app changes, or the request completes.
10. Password fields and non-allowlisted applications disable all NextSay actions.

The first-run flow must explain that accessibility access can expose visible screen content, that collection occurs only after a tap, and that selected text is sent to a cloud model.

## 6. Prompt Behavior

The backend instructs the model to:

- Reply as the user, not as an assistant explaining what to say.
- Use only the supplied conversation and instruction.
- Avoid inventing dates, commitments, facts, or relationships.
- Produce three meaningfully different candidates.
- Keep each candidate concise unless the instruction explicitly asks for a longer form.
- Preserve the conversation language, defaulting to Simplified Chinese.
- Return schema-valid JSON only.

Relationship presets in the MVP are `unspecified`, `manager`, `teacher`, `customer`, `colleague`, `friend`, and `family`.

## 7. Error Handling

- Accessibility disabled: show a direct path to Android accessibility settings.
- Unsupported application: keep the trigger disabled and explain the package allowlist in the containing app, not over the chat screen.
- No usable context: show a local manual-context field; do not send an empty request.
- Low extraction confidence: require user confirmation and display `unknown` roles.
- No focused editable node: retain the selected candidate and offer copy as a fallback.
- Network timeout: stop after 10 seconds and offer retry without re-reading the screen.
- Invalid provider response: reject it and show a retryable error; never insert partial JSON or model reasoning.
- App/window change during generation: discard the result unless the user returns to the same package and explicitly reopens it.

## 8. Testing Strategy

### Android Unit Tests

- Node flattening, de-duplication, metadata filtering, ordering, and role inference.
- WeChat and QQ adapters using sanitized accessibility-tree fixtures.
- Redaction behavior and false-positive boundaries.
- State transitions for preview, loading, success, timeout, and cancellation.
- Candidate insertion guards and no-send invariant.

### Backend Tests

- Request validation and payload limits.
- Prompt construction without logging chat text.
- Provider timeout and malformed-response handling.
- Exactly-three-candidate schema enforcement.
- Mock-provider end-to-end API test.

### Device Verification

- At least one Android 11-13 device and one Android 14+ device.
- Current WeChat and QQ one-to-one text chats.
- Empty draft and existing draft insertion.
- Switching chats/apps while generation is running.
- Accessibility disabled and network unavailable states.

Actual sanitized accessibility-tree fixtures must be captured from devices before app-specific parsing is considered complete.

## 9. Open-Source Reference Policy

The implementation may reuse code only when its license permits the intended distribution and all attribution requirements are met.

- Apache-2.0, BSD, and MIT references may be reused selectively with notices and code review.
- GPL projects are architectural references unless the project distribution is intentionally made GPL-compatible.
- Repositories without a license are behavior references only; their source code must not be copied.
- WeChat and QQ adapters are clean-room implementations based on Android accessibility APIs and observed sanitized node structures.

The initial implementation will study these projects without copying incompatible code:

- `SvReenen/Deskdrop`
- `bOsowski/KeyboardGPT`
- `sjonany/comedy-coach-app`
- `Stijnman/emotional-messaging-helper`
- `zegh6389/TypoApp`

## 10. Future Phases

After the MVP passes device validation:

1. Add on-device OCR only for chat versions that do not expose usable accessibility text.
2. Add group-chat attribution and quoted-message handling.
3. Introduce authentication, quotas, billing, and model routing.
4. Integrate the assistant into a production Chinese IME after licensing and engine evaluation.
5. Evaluate iOS with a reduced, user-supplied context workflow.

