# NextSay BYOK OpenAI-Compatible Direct API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable NextSay APK in which each user saves one OpenAI-compatible API URL, API key, and model on their own phone, calls that provider directly, and can export privacy-safe diagnostic logs without ADB.

**Architecture:** Keep capture, redaction, overlay, and IME behavior behind `NextSayRepository`, but replace the custom NextSay backend client with an Android OpenAI-compatible client that loads encrypted user configuration for every generation. Add a typed local diagnostic recorder shared through an application container; provider failures carry stable error codes and diagnostic IDs into the UI, where users can copy or export a sanitized report.

**Tech Stack:** Kotlin 2.0, Android SDK 35/minSdk 30, Android Keystore AES-GCM, SharedPreferences, Retrofit 2.11, OkHttp 4.12, Gson, Kotlin coroutines, JUnit 4, MockWebServer 4.12.

**Spec:** `docs/superpowers/specs/2026-09-22-byok-openai-compatible-direct-api-design.md`

## Global Constraints

- Save exactly one provider configuration: API base URL, API key, and model name.
- Support only the OpenAI-compatible `POST <base-url>/chat/completions` contract with bearer authentication.
- Release builds accept only `https://`; debug builds may accept `http://` for local development.
- Do not bundle, log, export, or display a real provider API key.
- Do not log or export conversation text, titles, prompts, drafts, instructions, candidates, HTTP bodies, or authorization headers.
- Store the API key with an Android Keystore-managed AES-GCM key and exclude configuration and diagnostics from Android backup.
- Keep the existing Python backend source and tests, but the Android runtime must not depend on it.
- Test connection before saving; any field edit invalidates the successful test result.
- Keep direct requests non-streaming with a ten-second network timeout.
- Retain at most 200 diagnostic events, 256 KiB, and seven days of history.
- Preserve manual insertion and manual sending; never add automatic message sending.
- Follow test-driven development and commit after each task passes its focused tests.

## Review Focus

- A user pastes a full URL ending in `/chat/completions`: normalize it once instead of producing a duplicated path; pin this in Task 1.
- Encrypted preferences survive a process restart but become unreadable after key invalidation or partial writes: treat the configuration as missing without crashing or exposing ciphertext; pin this in Task 2.
- A provider returns HTTP 200 with fenced JSON, empty choices, duplicate candidates, or unexpected styles: accept only one valid concise/tactful/natural set; pin this in Task 4.
- A slow connection test finishes after the user edits a field or starts another test: ignore the stale result and never enable Save for untested values; pin this in Task 7.
- Diagnostic inputs attempt to include secrets, request bodies, arbitrary exception messages, or oversized history: fixed fields and rotation must prevent those values from entering the export; pin this in Tasks 3 and 8.

---

### Task 1: Provider Configuration and URL Validation

**Files:**
- Create: `android-app/src/main/java/app/nextsay/provider/ProviderConfig.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/ProviderConfigValidator.kt`
- Test: `android-app/src/test/java/app/nextsay/provider/ProviderConfigValidatorTest.kt`

**Interfaces:**
- Consumes: raw strings entered by the user and `BuildConfig.DEBUG` as `allowCleartext`.
- Produces: `ProviderConfig`, `ValidatedProviderConfig`, `ProviderConfigError`, `ProviderConfigValidation`, and `ProviderConfigValidator.validate(baseUrl, apiKey, model)`.

- [ ] **Step 1: Write failing validator tests**

```kotlin
class ProviderConfigValidatorTest {
    private val release = ProviderConfigValidator(allowCleartext = false)

    @Test fun `normalizes whitespace and trailing slash`() {
        val result = release.validate(" https://api.deepseek.com/v1/ ", " sk-user ", " deepseek-chat ")
            as ProviderConfigValidation.Valid
        assertEquals("https://api.deepseek.com/v1", result.config.baseUrl)
        assertEquals("https://api.deepseek.com/v1/chat/completions", result.chatCompletionsUrl)
        assertEquals("api.deepseek.com", result.host)
    }

    @Test fun `full operation URL is not duplicated`() {
        val result = release.validate(
            "https://example.com/v1/chat/completions",
            "key",
            "model",
        ) as ProviderConfigValidation.Valid
        assertEquals("https://example.com/v1/chat/completions", result.chatCompletionsUrl)
        assertEquals("https://example.com/v1", result.config.baseUrl)
    }

    @Test fun `release rejects cleartext URL`() {
        val result = release.validate("http://192.168.1.2:8000/v1", "key", "model")
        assertEquals(
            ProviderConfigValidation.Invalid(ProviderConfigError.HTTPS_REQUIRED),
            result,
        )
    }

    @Test fun `debug permits cleartext URL`() {
        val result = ProviderConfigValidator(true).validate(
            "http://192.168.1.2:8000/v1",
            "key",
            "model",
        )
        assertTrue(result is ProviderConfigValidation.Valid)
    }

    @Test fun `rejects missing fields and URL credentials query or fragment`() {
        assertEquals(ProviderConfigError.API_KEY_REQUIRED, invalid("https://x.test/v1", "", "m"))
        assertEquals(ProviderConfigError.MODEL_REQUIRED, invalid("https://x.test/v1", "k", ""))
        assertEquals(ProviderConfigError.INVALID_URL, invalid("https://u:p@x.test/v1", "k", "m"))
        assertEquals(ProviderConfigError.INVALID_URL, invalid("https://x.test/v1?q=1", "k", "m"))
        assertEquals(ProviderConfigError.INVALID_URL, invalid("https://x.test/v1#f", "k", "m"))
    }

    private fun invalid(url: String, key: String, model: String) =
        (release.validate(url, key, model) as ProviderConfigValidation.Invalid).error
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\agent\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
$env:ANDROID_HOME='D:\agent\codex\nextsay\.android-sdk'
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.provider.ProviderConfigValidatorTest" --no-daemon
```

Expected: compilation fails because the provider configuration types do not exist.

- [ ] **Step 3: Implement the immutable configuration and validator**

```kotlin
data class ProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

data class ValidatedProviderConfig(
    val config: ProviderConfig,
    val chatCompletionsUrl: String,
    val scheme: String,
    val host: String,
)

enum class ProviderConfigError {
    URL_REQUIRED,
    API_KEY_REQUIRED,
    MODEL_REQUIRED,
    INVALID_URL,
    HTTPS_REQUIRED,
}

sealed interface ProviderConfigValidation {
    data class Valid(
        val config: ProviderConfig,
        val chatCompletionsUrl: String,
        val scheme: String,
        val host: String,
    ) : ProviderConfigValidation

    data class Invalid(val error: ProviderConfigError) : ProviderConfigValidation
}
```

Implement `ProviderConfigValidator` with `java.net.URI`: trim all inputs, reject missing host/user-info/query/fragment, normalize trailing slashes, accept an already-complete `/chat/completions` path without duplicating it, and construct `ProviderConfigValidation.Valid` only after scheme enforcement.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the Step 2 command.

Expected: all `ProviderConfigValidatorTest` tests pass.

- [ ] **Step 5: Commit the provider configuration boundary**

```powershell
git add android-app/src/main/java/app/nextsay/provider/ProviderConfig.kt android-app/src/main/java/app/nextsay/provider/ProviderConfigValidator.kt android-app/src/test/java/app/nextsay/provider/ProviderConfigValidatorTest.kt
git commit -m "feat: validate user provider configuration"
```

### Task 2: Keystore-Backed Single Configuration Store

**Files:**
- Create: `android-app/src/main/java/app/nextsay/provider/SecretCipher.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/AndroidKeystoreSecretCipher.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/ProviderConfigStore.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/SharedPreferencesProviderConfigStore.kt`
- Test: `android-app/src/test/java/app/nextsay/provider/SharedPreferencesProviderConfigStoreTest.kt`

**Interfaces:**
- Consumes: normalized `ProviderConfig` from Task 1.
- Produces: `SecretCipher.encrypt/decrypt`, `ProviderConfigStore.load/save/clear`, and `SharedPreferencesProviderConfigStore`.

- [ ] **Step 1: Write failing store tests against an in-memory key-value adapter**

```kotlin
class SharedPreferencesProviderConfigStoreTest {
    private val values = FakePreferenceValues()
    private val cipher = PrefixSecretCipher()
    private val store = SharedPreferencesProviderConfigStore(values, cipher)

    @Test fun `round trips one configuration without storing plaintext key`() {
        val config = ProviderConfig("https://api.example/v1", "secret-key", "model-a")
        store.save(config)
        assertEquals(config, store.load())
        assertEquals("enc:secret-key", values.getString("api_key"))
        assertFalse(values.snapshot().values.contains("secret-key"))
    }

    @Test fun `saving replaces all fields atomically`() {
        store.save(ProviderConfig("https://one/v1", "one", "m1"))
        store.save(ProviderConfig("https://two/v1", "two", "m2"))
        assertEquals(ProviderConfig("https://two/v1", "two", "m2"), store.load())
    }

    @Test fun `partial or undecryptable data is cleared and treated as missing`() {
        values.putAll(mapOf("base_url" to "https://x/v1", "api_key" to "broken", "model" to "m"))
        assertNull(store.load())
        assertTrue(values.snapshot().isEmpty())
    }

    @Test fun `clear removes every stored field`() {
        store.save(ProviderConfig("https://x/v1", "key", "model"))
        store.clear()
        assertNull(store.load())
    }
}
```

Define the test adapters in the same test file: `PrefixSecretCipher` throws unless ciphertext starts with `enc:`, and `FakePreferenceValues` implements the production-facing `PreferenceValues` interface with atomic `replace(Map<String, String>)`.

- [ ] **Step 2: Run the store test and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.provider.SharedPreferencesProviderConfigStoreTest" --no-daemon
```

Expected: compilation fails because store and cipher interfaces are absent.

- [ ] **Step 3: Implement store interfaces and AES-GCM cipher**

```kotlin
interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

interface ProviderConfigStore {
    fun load(): ProviderConfig?
    fun save(config: ProviderConfig)
    fun clear()
}

interface PreferenceValues {
    fun getString(key: String): String?
    fun replace(values: Map<String, String>): Boolean
    fun clear(): Boolean
}
```

Implement `AndroidKeystoreSecretCipher` by following the existing `AndroidKeystoreMessageCipher` AES/GCM format but use the distinct alias `nextsay-provider-api-key-v1`. Implement the store so encryption completes before a single committed preference replacement; any missing field, failed decryption, or failed commit clears the configuration and returns `null` or throws from `save` without exposing ciphertext.

- [ ] **Step 4: Add the Android SharedPreferences adapter**

```kotlin
class AndroidPreferenceValues(context: Context) : PreferenceValues {
    private val preferences = context.getSharedPreferences("provider_config", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun replace(values: Map<String, String>): Boolean = preferences.edit()
        .clear()
        .also { editor -> values.forEach(editor::putString) }
        .commit()

    override fun clear(): Boolean = preferences.edit().clear().commit()
}
```

- [ ] **Step 5: Run focused configuration tests**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.provider.*" --no-daemon
```

Expected: validator and store suites pass.

- [ ] **Step 6: Commit encrypted configuration storage**

```powershell
git add android-app/src/main/java/app/nextsay/provider android-app/src/test/java/app/nextsay/provider
git commit -m "feat: store one encrypted provider configuration"
```

### Task 3: Typed Rotating Diagnostic Recorder

**Files:**
- Create: `android-app/src/main/java/app/nextsay/diagnostics/DiagnosticEvent.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/DiagnosticRecorder.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/DiagnosticStorage.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/DiagnosticEventFactory.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/JsonFileDiagnosticStorage.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/RotatingDiagnosticRecorder.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/DiagnosticFormatter.kt`
- Test: `android-app/src/test/java/app/nextsay/diagnostics/RotatingDiagnosticRecorderTest.kt`
- Test: `android-app/src/test/java/app/nextsay/diagnostics/DiagnosticFormatterTest.kt`

**Interfaces:**
- Consumes: only fixed typed metadata; no arbitrary message, headers, bodies, chat text, prompt, draft, instruction, or candidate fields exist.
- Produces: `DiagnosticRecorder.record/events/clear/find`, `DiagnosticEvent`, `DiagnosticEventFactory.create`, and `DiagnosticFormatter.compact/export`.

- [ ] **Step 1: Write failing rotation and allow-list tests**

```kotlin
class RotatingDiagnosticRecorderTest {
    @Test fun `keeps newest 200 events and removes events older than seven days`() {
        val storage = MemoryDiagnosticStorage()
        val recorder = RotatingDiagnosticRecorder(storage, maxEvents = 200, maxBytes = 256 * 1024)
        val initialTime = 8L * 24 * 60 * 60 * 1000
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        repeat(205) { index ->
            recorder.record(event(id = "e$index", timestamp = initialTime + index))
        }
        assertEquals(200, recorder.events().size)
        assertEquals("e5", recorder.events().first().id)
        recorder.prune(nowMillis = initialTime + sevenDays + 205)
        assertTrue(recorder.events().isEmpty())
    }

    @Test fun `byte cap drops oldest complete events`() {
        val storage = MemoryDiagnosticStorage()
        val recorder = RotatingDiagnosticRecorder(storage, maxEvents = 200, maxBytes = 450)
        repeat(10) { recorder.record(event(id = "id-$it", model = "model-name-$it")) }
        assertTrue(storage.encodedSize() <= 450)
        assertEquals("id-9", recorder.events().last().id)
    }

    @Test fun `event schema has no free form secret or content fields`() {
        val names = DiagnosticEvent::class.java.declaredFields.map { it.name }.toSet()
        assertFalse(names.any { it in setOf("apiKey", "authorization", "body", "message", "prompt", "chat") })
    }
}
```

- [ ] **Step 2: Write failing formatter tests**

```kotlin
class DiagnosticFormatterTest {
    @Test fun `export contains safe provider metadata and error correlation`() {
        val text = DiagnosticFormatter().export(
            listOf(event(id = "evt-7", host = "api.deepseek.com", model = "deepseek-chat", errorCode = "API-AUTH")),
        )
        assertTrue(text.contains("evt-7"))
        assertTrue(text.contains("api.deepseek.com"))
        assertTrue(text.contains("deepseek-chat"))
        assertTrue(text.contains("API-AUTH"))
        assertFalse(text.contains("Authorization"))
    }
}
```

- [ ] **Step 3: Run diagnostic tests and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.diagnostics.*" --no-daemon
```

Expected: compilation fails because diagnostic types are absent.

- [ ] **Step 4: Implement the fixed event schema and recorder interfaces**

```kotlin
enum class DiagnosticEventType {
    CONNECTION_TEST_STARTED,
    CONNECTION_TEST_SUCCEEDED,
    CONNECTION_TEST_FAILED,
    GENERATION_STARTED,
    GENERATION_SUCCEEDED,
    GENERATION_FAILED,
    CAPTURE_FAILED,
    INSERTION_FAILED,
    APP_CRASHED,
}

enum class DiagnosticSurface { MAIN, SETTINGS, OVERLAY, IME }

data class DiagnosticEvent(
    val id: String,
    val timestampMillis: Long,
    val type: DiagnosticEventType,
    val surface: DiagnosticSurface,
    val appVersion: String,
    val buildType: String,
    val androidVersion: String,
    val device: String,
    val providerScheme: String? = null,
    val providerHost: String? = null,
    val model: String? = null,
    val httpStatus: Int? = null,
    val durationMillis: Long? = null,
    val errorCode: String? = null,
    val accessibilityEnabled: Boolean? = null,
    val imeEnabled: Boolean? = null,
    val exceptionClass: String? = null,
    val stackFrames: List<String> = emptyList(),
)

interface DiagnosticRecorder {
    fun record(event: DiagnosticEvent)
    fun events(): List<DiagnosticEvent>
    fun find(id: String): DiagnosticEvent?
    fun clear()
}

data class DiagnosticMetadata(
    val appVersion: String,
    val buildType: String,
    val androidVersion: String,
    val device: String,
)

fun interface DiagnosticMetadataProvider {
    fun current(): DiagnosticMetadata
}

class DiagnosticEventFactory(
    private val metadataProvider: DiagnosticMetadataProvider,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun create(
        type: DiagnosticEventType,
        surface: DiagnosticSurface,
        providerScheme: String? = null,
        providerHost: String? = null,
        model: String? = null,
        httpStatus: Int? = null,
        durationMillis: Long? = null,
        errorCode: String? = null,
        accessibilityEnabled: Boolean? = null,
        imeEnabled: Boolean? = null,
        exceptionClass: String? = null,
        stackFrames: List<String> = emptyList(),
    ): DiagnosticEvent {
        val metadata = metadataProvider.current()
        return DiagnosticEvent(
            id = newId(),
            timestampMillis = nowMillis(),
            type = type,
            surface = surface,
            appVersion = metadata.appVersion,
            buildType = metadata.buildType,
            androidVersion = metadata.androidVersion,
            device = metadata.device,
            providerScheme = providerScheme,
            providerHost = providerHost,
            model = model,
            httpStatus = httpStatus,
            durationMillis = durationMillis,
            errorCode = errorCode,
            accessibilityEnabled = accessibilityEnabled,
            imeEnabled = imeEnabled,
            exceptionClass = exceptionClass,
            stackFrames = stackFrames,
        )
    }
}
```

Implement `DiagnosticEventFactory.create` by copying fixed device/app metadata and caller-supplied safe typed values into a new event. Implement `RotatingDiagnosticRecorder` with synchronized read-modify-write, seven-day pruning, then oldest-first removal until both count and UTF-8 encoded size limits pass. Catch storage failures inside `record` so diagnostics never crash product behavior.

- [ ] **Step 5: Implement JSON file storage and deterministic text formatting**

Use Gson to encode the event list to a single app-private file via write-to-temporary-then-rename. `DiagnosticFormatter.compact(event)` emits one short Chinese support block; `export(events)` emits a header plus one JSON object per line, containing exactly the fixed schema fields.

- [ ] **Step 6: Run diagnostic tests and verify GREEN**

Run the Step 3 command.

Expected: rotation, age, byte-cap, schema, lookup, clear, and formatter tests pass.

- [ ] **Step 7: Commit the diagnostic core**

```powershell
git add android-app/src/main/java/app/nextsay/diagnostics android-app/src/test/java/app/nextsay/diagnostics
git commit -m "feat: record privacy-safe local diagnostics"
```

### Task 4: Local Prompt Builder and Strict Candidate Parser

**Files:**
- Create: `android-app/src/main/java/app/nextsay/provider/OpenAiModels.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/ReplyPromptBuilder.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/ReplyCandidateParser.kt`
- Test: `android-app/src/test/java/app/nextsay/provider/ReplyPromptBuilderTest.kt`
- Test: `android-app/src/test/java/app/nextsay/provider/ReplyCandidateParserTest.kt`

**Interfaces:**
- Consumes: the existing `app.nextsay.api.ReplyRequestDto` after redaction.
- Produces: OpenAI request/response DTOs, `ReplyPromptBuilder.build`, and `ReplyCandidateParser.parse`.

- [ ] **Step 1: Write failing prompt tests**

```kotlin
class ReplyPromptBuilderTest {
    @Test fun `builds system and JSON user messages without changing reviewed context`() {
        val request = ReplyRequestDto(
            messages = listOf(MessageDto("other", "明天能交吗", 0.9f)),
            draft = "可以",
            instruction = "委婉一点",
            relationship = "manager",
        )
        val messages = ReplyPromptBuilder(Gson()).build(request)
        assertEquals(listOf("system", "user"), messages.map { it.role })
        assertTrue(messages[0].content.contains("不得虚构"))
        assertTrue(messages[0].content.contains("只返回 JSON"))
        assertTrue(messages[1].content.contains("明天能交吗"))
        assertTrue(messages[1].content.contains("manager"))
    }
}
```

- [ ] **Step 2: Write failing parser edge-case tests**

```kotlin
class ReplyCandidateParserTest {
    private val parser = ReplyCandidateParser(Gson())

    @Test fun `accepts fenced JSON and returns canonical style order`() {
        val result = parser.parse("""```json
            {"candidates":[
              {"style":"natural","text":"行"},
              {"style":"concise","text":"可以"},
              {"style":"tactful","text":"好的，我会尽快完成"}
            ]}
            ```""".trimIndent())
        assertEquals(listOf("concise", "tactful", "natural"), result.map { it.style })
    }

    @Test fun `rejects empty choices duplicate text wrong style count and long text`() {
        assertThrows(IllegalArgumentException::class.java) { parser.parse("{\"candidates\":[]}") }
        assertThrows(IllegalArgumentException::class.java) { parser.parse(envelope("好", "好", "行")) }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(envelopeWithStyles("brief", "tactful", "natural"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(envelope("x".repeat(501), "好", "行"))
        }
    }
}
```

Define `envelope(first, second, third)` and `envelopeWithStyles(first, second, third)` in the same test file as literal JSON builders that always emit three candidate objects; only their text or styles vary per assertion.

- [ ] **Step 3: Run prompt/parser tests and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.provider.ReplyPromptBuilderTest" --tests "app.nextsay.provider.ReplyCandidateParserTest" --no-daemon
```

Expected: compilation fails because prompt and parser types are absent.

- [ ] **Step 4: Port the reviewed backend prompt and implement strict parsing**

Define these DTOs in `OpenAiModels.kt`:

```kotlin
data class OpenAiMessageDto(val role: String, val content: String)
data class ResponseFormatDto(val type: String = "json_object")
data class ChatCompletionRequestDto(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    val temperature: Double = 0.7,
    val response_format: ResponseFormatDto? = ResponseFormatDto(),
    val max_tokens: Int? = null,
)
data class ChatCompletionResponseDto(val choices: List<ChatChoiceDto> = emptyList())
data class ChatChoiceDto(val message: ChatChoiceMessageDto)
data class ChatChoiceMessageDto(val content: String?)
data class CandidateEnvelopeDto(val candidates: List<ReplyCandidateDto> = emptyList())
```

Port the exact Chinese system rules from `backend/src/nextsay_backend/prompt.py`. Parse one optional Markdown JSON fence, require exactly one nonblank candidate for each `concise`, `tactful`, and `natural` style, reject duplicate text, cap candidate text at 500 characters, and return canonical style order.

- [ ] **Step 5: Run prompt/parser tests and verify GREEN**

Run the Step 3 command.

Expected: all prompt and candidate parser tests pass.

- [ ] **Step 6: Commit local prompt and response validation**

```powershell
git add android-app/src/main/java/app/nextsay/provider android-app/src/test/java/app/nextsay/provider
git commit -m "feat: build direct reply prompts on device"
```

### Task 5: OpenAI-Compatible Transport, Retry, and Error Mapping

**Files:**
- Modify: `android-app/build.gradle.kts`
- Create: `android-app/src/main/java/app/nextsay/provider/ProviderError.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/OpenAiCompatibleApi.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/ReplyProviderClient.kt`
- Create: `android-app/src/main/java/app/nextsay/provider/OpenAiCompatibleClient.kt`
- Test: `android-app/src/test/java/app/nextsay/provider/OpenAiCompatibleClientTest.kt`

**Interfaces:**
- Consumes: `ValidatedProviderConfig`, `ReplyRequestDto`, `ReplyPromptBuilder`, `ReplyCandidateParser`, `DiagnosticRecorder`, and `DiagnosticEventFactory`.
- Produces: `ReplyProviderClient.generate/testConnection` and `ProviderException(code, diagnosticId, httpStatus)`.

- [ ] **Step 1: Add MockWebServer for request-level JVM tests**

```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 2: Write failing request and response tests**

```kotlin
class OpenAiCompatibleClientTest {
    @Test fun `posts bearer request to normalized endpoint without leaking key to diagnostics`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))
        val result = client(server).generate(validated(server), request())
        assertEquals(3, result.size)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer secret-key", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("deepseek-chat"))
        assertFalse(diagnostics.events().toString().contains("secret-key"))
    }

    @Test fun `retries once without response format only when provider rejects that option`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("response_format is unsupported"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))
        client(server).generate(validated(server), request())
        assertTrue(server.takeRequest().body.readUtf8().contains("response_format"))
        assertFalse(server.takeRequest().body.readUtf8().contains("response_format"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `maps authentication quota model timeout DNS TLS and incompatible response`() = runTest {
        assertProviderCode(401, "{}", ProviderErrorCode.API_AUTH)
        assertProviderCode(429, "{}", ProviderErrorCode.API_QUOTA)
        assertProviderCode(404, "{}", ProviderErrorCode.API_MODEL)
        assertTransportCode(SocketTimeoutException(), ProviderErrorCode.NET_TIMEOUT)
        assertTransportCode(UnknownHostException(), ProviderErrorCode.NET_DNS)
        assertTransportCode(SSLException(), ProviderErrorCode.NET_TLS)
        assertProviderCode(200, "{\"choices\":[]}", ProviderErrorCode.API_INCOMPATIBLE)
    }

    @Test fun `connection test uses tiny prompt and does not require candidate JSON`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"))
        client(server).testConnection(validated(server))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("仅回复 OK"))
        assertTrue(body.contains("\"max_tokens\":4"))
    }
}
```

In the same test class, define `successBody()` as a literal OpenAI response whose message content contains the valid three-candidate JSON envelope; define `server`, `client(server)`, `validated(server)`, `request()`, `assertProviderCode`, and `assertTransportCode` fixtures explicitly, and shut down `MockWebServer` in `@After`.

- [ ] **Step 3: Run the client test and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.provider.OpenAiCompatibleClientTest" --no-daemon
```

Expected: compilation fails because transport and error types are absent.

- [ ] **Step 4: Implement stable error types and Retrofit dynamic URL API**

```kotlin
enum class ProviderErrorCode(val wireCode: String, val userMessage: String) {
    CONFIG_MISSING("CFG-MISSING", "请先配置模型服务"),
    CONFIG_INVALID("CFG-INVALID", "模型服务配置无效"),
    NET_DNS("NET-DNS", "无法解析 API 地址"),
    NET_CONNECT("NET-CONNECT", "无法连接模型服务"),
    NET_TLS("NET-TLS", "模型服务安全连接失败"),
    NET_TIMEOUT("NET-TIMEOUT", "模型服务请求超时"),
    API_AUTH("API-AUTH", "API Key 无效或无权限"),
    API_QUOTA("API-QUOTA", "API 额度不足或请求过于频繁"),
    API_MODEL("API-MODEL", "模型不存在或不可用"),
    API_HTTP("API-HTTP", "模型服务返回错误"),
    API_INCOMPATIBLE("API-INCOMPATIBLE", "模型服务返回格式不兼容"),
    CAPTURE_FAILED("APP-CAPTURE", "读取当前对话失败"),
    INSERTION_FAILED("APP-INSERT", "写入输入框失败"),
    APP_INTERNAL("APP-INTERNAL", "NextSay 内部错误"),
}

class ProviderException(
    val code: ProviderErrorCode,
    val diagnosticId: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException("${code.userMessage}（${code.wireCode}）", cause)

interface OpenAiCompatibleApi {
    @POST
    suspend fun complete(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequestDto,
    ): Response<ChatCompletionResponseDto>
}

interface ReplyProviderClient {
    suspend fun generate(
        config: ValidatedProviderConfig,
        request: ReplyRequestDto,
        surface: DiagnosticSurface,
    ): List<ReplyCandidateDto>

    suspend fun testConnection(config: ValidatedProviderConfig)
}
```

- [ ] **Step 5: Implement direct calls, one compatibility retry, and diagnostics**

Create Retrofit with base URL `https://localhost/` and the existing Gson converter; all actual requests use `@Url`. Configure OkHttp `callTimeout(10, TimeUnit.SECONDS)` and no body-logging interceptor. For generation, send `response_format`; retry once without it only when status is 400 and the error body names `response_format` or `json_object`. Create start/success/failure events through `DiagnosticEventFactory`, record scheme/host/model, duration, status, and stable code, then throw `ProviderException` with the failure event's ID. Never place error bodies or exception messages into diagnostic events.

- [ ] **Step 6: Run transport tests and all provider tests**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.provider.*" --no-daemon
```

Expected: request construction, retry count, strict parsing, connection test, transport mapping, and secret-absence tests pass.

- [ ] **Step 7: Commit the OpenAI-compatible client**

```powershell
git add android-app/build.gradle.kts android-app/src/main/java/app/nextsay/provider android-app/src/test/java/app/nextsay/provider
git commit -m "feat: call OpenAI-compatible providers directly"
```

### Task 6: Replace Backend Repository Dependency with User Configuration

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/api/NextSayRepository.kt`
- Modify: `android-app/src/main/java/app/nextsay/provider/ProviderConfig.kt`
- Delete: `android-app/src/main/java/app/nextsay/api/NextSayApi.kt`
- Modify: `android-app/src/test/java/app/nextsay/api/NextSayRepositoryTest.kt`

**Interfaces:**
- Consumes: `ProviderConfigStore`, `ProviderConfigValidator`, `ReplyProviderClient`, `TextRedactor`, `DiagnosticRecorder`, `DiagnosticEventFactory`, and a `DiagnosticSurface` for each generation.
- Produces: the unchanged high-level `NextSayRepository.generate(context, instruction, relationship, surface): Result<List<ReplyCandidate>>` behavior.

- [ ] **Step 1: Rewrite repository tests to use a fake direct client**

```kotlin
@Test fun `missing configuration fails before provider call`() = runTest {
    val client = FakeReplyProviderClient()
    val repository = repository(storeConfig = null, client = client)
    val result = repository.generate(context(), "", "unspecified", DiagnosticSurface.OVERLAY)
    val error = result.exceptionOrNull() as ProviderException
    assertEquals(ProviderErrorCode.CONFIG_MISSING, error.code)
    assertEquals(0, client.generateCalls)
}

@Test fun `redacts reviewed context before direct provider request`() = runTest {
    val client = FakeReplyProviderClient(replies = response().candidates)
    val repository = repository(storeConfig = config(), client = client)
    val result = repository.generate(
        context(),
        "替我回复 13812345678",
        "manager",
        DiagnosticSurface.OVERLAY,
    )
    assertTrue(result.isSuccess)
    assertEquals("联系我：[手机号]", client.request!!.messages.single().text)
    assertEquals("替我回复 [手机号]", client.request!!.instruction)
    assertEquals(DiagnosticSurface.OVERLAY, client.surface)
}

@Test fun `loads configuration for every generation`() = runTest {
    val store = MutableProviderConfigStore(config("model-a"))
    val client = FakeReplyProviderClient(replies = response().candidates)
    val repository = repository(store = store, client = client)
    repository.generate(context(), "", "unspecified", DiagnosticSurface.OVERLAY)
    store.value = config("model-b")
    repository.generate(context(), "", "unspecified", DiagnosticSurface.OVERLAY)
    assertEquals(listOf("model-a", "model-b"), client.models)
}
```

- [ ] **Step 2: Run the repository test and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.api.NextSayRepositoryTest" --no-daemon
```

Expected: compilation fails because repository still requires `NextSayApi`.

- [ ] **Step 3: Implement repository configuration loading and direct delegation**

```kotlin
class NextSayRepository(
    private val configStore: ProviderConfigStore,
    private val configValidator: ProviderConfigValidator,
    private val client: ReplyProviderClient,
    private val redactor: TextRedactor,
    private val diagnostics: DiagnosticRecorder,
    private val eventFactory: DiagnosticEventFactory,
) {
    suspend fun generate(
        context: ChatContext,
        instruction: String,
        relationship: String,
        surface: DiagnosticSurface,
    ): Result<List<ReplyCandidate>> = runCatching {
        val stored = configStore.load() ?: throw configurationFailure(
            ProviderErrorCode.CONFIG_MISSING,
            surface,
        )
        val validated = configValidator.validate(stored.baseUrl, stored.apiKey, stored.model)
        if (validated !is ProviderConfigValidation.Valid) throw configurationFailure(
            ProviderErrorCode.CONFIG_INVALID,
            surface,
        )
        val request = ReplyRequestDto(
            messages = context.messages.takeLast(20).map {
                MessageDto(it.role.wireValue, redactor.redact(it.text), it.confidence)
            },
            draft = redactor.redact(context.draft),
            instruction = redactor.redact(instruction),
            relationship = relationship,
        )
        client.generate(validated.asValidatedProviderConfig(), request, surface)
            .map { ReplyCandidate(it.style, it.text) }
    }

    private fun configurationFailure(
        code: ProviderErrorCode,
        surface: DiagnosticSurface,
    ): ProviderException {
        val event = eventFactory.create(
            type = DiagnosticEventType.GENERATION_FAILED,
            surface = surface,
            errorCode = code.wireCode,
        )
        diagnostics.record(event)
        return ProviderException(code, event.id)
    }
}
```

Provide `ProviderConfigValidation.Valid.asValidatedProviderConfig()` in Task 1's file so repository and settings use one conversion.

- [ ] **Step 4: Delete the custom backend Retrofit interface and rerun repository tests**

Run the Step 2 command.

Expected: missing-config, redaction, fresh-config, success, and invalid-candidate behavior pass through the direct client boundary.

- [ ] **Step 5: Commit repository migration**

```powershell
git add android-app/src/main/java/app/nextsay/api android-app/src/test/java/app/nextsay/api/NextSayRepositoryTest.kt
git commit -m "refactor: generate replies with user provider settings"
```

### Task 7: Test-Gated Settings State and API Settings Activity

**Files:**
- Create: `android-app/src/main/java/app/nextsay/NextSayApplication.kt`
- Create: `android-app/src/main/java/app/nextsay/AppDependencies.kt`
- Create: `android-app/src/main/java/app/nextsay/settings/ProviderConnectionTester.kt`
- Create: `android-app/src/main/java/app/nextsay/settings/ProviderSettingsController.kt`
- Create: `android-app/src/main/java/app/nextsay/settings/ApiSettingsActivity.kt`
- Create: `android-app/src/test/java/app/nextsay/settings/ProviderSettingsControllerTest.kt`
- Modify: `android-app/src/main/java/app/nextsay/MainActivity.kt`
- Modify: `android-app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ProviderConfigValidator`, `ProviderConfigStore`, `ReplyProviderClient.testConnection` through `ProviderConnectionTester`, `DiagnosticRecorder`, and `DiagnosticEventFactory`.
- Produces: one process-wide `AppDependencies`, `ProviderSettingsState`, `ProviderSettingsController`, and a programmatic Android settings activity.

- [ ] **Step 1: Write failing controller tests for test-before-save and stale results**

```kotlin
class ProviderSettingsControllerTest {
    @Test fun `valid fields must pass test before save`() = runTest {
        val store = FakeStore()
        val controller = controller(store, tester = SuccessfulTester())
        controller.updateUrl("https://api.example/v1")
        controller.updateApiKey("key")
        controller.updateModel("model")
        assertFalse(controller.state.value.canSave)
        controller.testConnection()
        assertTrue(controller.state.value.canSave)
        assertTrue(controller.save())
        assertEquals("model", store.load()!!.model)
    }

    @Test fun `editing any field invalidates successful test`() = runTest {
        val controller = configuredController()
        controller.testConnection()
        controller.updateModel("other-model")
        assertFalse(controller.state.value.canSave)
        assertFalse(controller.save())
    }

    @Test fun `late result from old values cannot enable save`() = runTest {
        val tester = DeferredTester()
        val controller = configuredController(tester)
        val first = launch { controller.testConnection() }
        runCurrent()
        controller.updateUrl("https://changed.example/v1")
        tester.completeSuccess()
        first.join()
        assertFalse(controller.state.value.canSave)
    }

    @Test fun `starting another test cancels authority of first result`() = runTest {
        val tester = OrderedDeferredTester()
        val controller = configuredController(tester)
        val first = launch { controller.testConnection() }
        runCurrent()
        val second = launch { controller.testConnection() }
        tester.completeSecondSuccess()
        second.join()
        tester.completeFirstFailure()
        first.join()
        assertTrue(controller.state.value.canSave)
    }
}
```

- [ ] **Step 2: Run settings controller tests and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.settings.ProviderSettingsControllerTest" --no-daemon
```

Expected: compilation fails because settings state types are absent.

- [ ] **Step 3: Implement epoch-gated settings state**

```kotlin
data class ProviderSettingsState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val testing: Boolean = false,
    val canSave: Boolean = false,
    val status: String = "",
    val diagnosticId: String? = null,
)

fun interface ProviderConnectionTester {
    suspend fun test(config: ValidatedProviderConfig): Result<Unit>
}
```

`ProviderSettingsController` owns a `MutableStateFlow<ProviderSettingsState>`, increments an epoch for every field edit and test start, validates before calling the tester, accepts a result only when both epoch and a fingerprint of all three normalized values still match, and permits `save()` only for the last successful fingerprint. Local validation failures create typed `CONNECTION_TEST_FAILED` events through `DiagnosticEventFactory`; provider failures reuse the `ProviderException.diagnosticId` already recorded by the client instead of creating a duplicate. State carries the stable code and event ID so the settings screen can copy the matching summary.

- [ ] **Step 4: Run controller tests and verify GREEN**

Run the Step 2 command.

Expected: validation, successful test, failure messaging, edit invalidation, and stale completion tests pass.

- [ ] **Step 5: Create the application dependency container**

Construct exactly one `ProviderConfigStore`, `ProviderConfigValidator(BuildConfig.DEBUG)`, `RotatingDiagnosticRecorder`, `DiagnosticEventFactory`, `OpenAiCompatibleClient`, and `DiagnosticFormatter` per process. The factory's metadata provider reads `BuildConfig.VERSION_NAME`, `BuildConfig.BUILD_TYPE`, `Build.VERSION.RELEASE`, and `Build.MANUFACTURER + " " + Build.MODEL`.

```kotlin
class NextSayApplication : Application() {
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
    }
}

val Context.nextSayDependencies: AppDependencies
    get() = (applicationContext as NextSayApplication).dependencies
```

Register `android:name=".NextSayApplication"` on the manifest `<application>` element.

- [ ] **Step 6: Build `ApiSettingsActivity` with explicit fields and actions**

Create a vertically scrolling programmatic form matching the existing `MainActivity` visual language:

```kotlin
val url = EditText(this).apply {
    hint = "https://api.deepseek.com/v1"
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
}
val apiKey = EditText(this).apply {
    hint = "API Key"
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
}
val model = EditText(this).apply {
    hint = "deepseek-chat"
    inputType = InputType.TYPE_CLASS_TEXT
}
val test = Button(this).apply { text = "测试连接" }
val save = Button(this).apply { text = "保存"; isEnabled = false }
val copyDiagnostics = Button(this).apply {
    text = "复制诊断信息"
    visibility = View.GONE
}
```

Add a show/hide API-key button, the disclosure `对话文字会直接发送到你配置的第三方模型服务，并受该服务隐私政策约束`, a note that connection testing may consume a negligible amount, and lifecycle-bound coroutine collection. Populate saved values on entry, but keep the key masked until the user taps Show. Show `复制诊断信息` only when `ProviderSettingsState.diagnosticId` is non-null, and copy the matching `DiagnosticFormatter.compact` result.

- [ ] **Step 7: Add configuration status and entry point to `MainActivity`**

Add `providerStatus` and `配置模型服务` controls before accessibility setup. In `onResume`, call `configStore.load()` and display either `状态：模型服务已配置（<host> / <model>）` in green or `状态：请先配置模型服务` in red. Launch `ApiSettingsActivity` explicitly.

- [ ] **Step 8: Register the settings activity and manually inspect first-run behavior**

```xml
<activity
    android:name=".settings.ApiSettingsActivity"
    android:exported="false" />
```

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug --no-daemon
```

Expected: tests pass, APK assembles, and no configuration secret appears in generated resources.

- [ ] **Step 9: Commit the test-gated settings flow**

```powershell
git add android-app/src/main/java/app/nextsay/NextSayApplication.kt android-app/src/main/java/app/nextsay/AppDependencies.kt android-app/src/main/java/app/nextsay/settings android-app/src/test/java/app/nextsay/settings android-app/src/main/java/app/nextsay/MainActivity.kt android-app/src/main/AndroidManifest.xml
git commit -m "feat: add user API configuration screen"
```

### Task 8: Application Container, Diagnostic Export, and Local Crash Recovery

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/NextSayApplication.kt`
- Modify: `android-app/src/main/java/app/nextsay/AppDependencies.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/CrashSanitizer.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/LocalCrashHandler.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/DiagnosticExportManager.kt`
- Create: `android-app/src/main/java/app/nextsay/diagnostics/DiagnosticsActivity.kt`
- Create: `android-app/src/main/res/xml/diagnostic_file_paths.xml`
- Test: `android-app/src/test/java/app/nextsay/diagnostics/CrashSanitizerTest.kt`
- Test: `android-app/src/test/java/app/nextsay/diagnostics/DiagnosticExportManagerTest.kt`
- Modify: `android-app/src/main/AndroidManifest.xml`
- Modify: `android-app/src/main/java/app/nextsay/MainActivity.kt`

**Interfaces:**
- Consumes: Task 7 application container and activities plus Task 3 recorder/formatter.
- Produces: explicit share/copy/clear diagnostics and a local crash event available after restart.

- [ ] **Step 1: Write failing crash sanitizer tests**

```kotlin
class CrashSanitizerTest {
    @Test fun `captures class and app frames but never raw message`() {
        val error = IllegalStateException("Authorization: Bearer secret-key chat=private")
        error.stackTrace = arrayOf(
            StackTraceElement("app.nextsay.provider.Client", "call", "Client.kt", 40),
            StackTraceElement("java.lang.Thread", "run", "Thread.java", 1),
        )
        val safe = CrashSanitizer().sanitize(error)
        assertEquals("java.lang.IllegalStateException", safe.exceptionClass)
        assertEquals(listOf("app.nextsay.provider.Client.call(Client.kt:40)"), safe.stackFrames)
        assertFalse(safe.toString().contains("secret-key"))
        assertFalse(safe.toString().contains("private"))
    }
}
```

- [ ] **Step 2: Write failing export tests**

```kotlin
class DiagnosticExportManagerTest {
    @Test fun `writes UTF-8 report with no secret-bearing source fields`() {
        val output = temporaryFolder.newFile("report.txt")
        DiagnosticExportManager(formatter).write(output, listOf(safeEvent()))
        val text = output.readText(Charsets.UTF_8)
        assertTrue(text.contains("api.deepseek.com"))
        assertFalse(text.contains("Bearer"))
        assertFalse(text.contains("聊天内容"))
    }
}
```

- [ ] **Step 3: Run crash/export tests and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.diagnostics.CrashSanitizerTest" --tests "app.nextsay.diagnostics.DiagnosticExportManagerTest" --no-daemon
```

Expected: compilation fails because crash and export types are absent.

- [ ] **Step 4: Extend `NextSayApplication` with crash recovery**

Reuse the process-wide dependencies created in Task 7 and install the local crash handler only after the recorder exists.

```kotlin
class NextSayApplication : Application() {
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
        LocalCrashHandler.install(dependencies.diagnostics)
    }
}

val Context.nextSayDependencies: AppDependencies
    get() = (applicationContext as NextSayApplication).dependencies
```

- [ ] **Step 5: Implement sanitized crash delegation**

`CrashSanitizer` keeps only the exception class and up to 40 stack frames whose class starts with `app.nextsay.`; it never reads `Throwable.message`. `LocalCrashHandler` writes one `APP_CRASHED` event and then invokes the previously installed uncaught handler exactly once. Guard recorder failure so the delegate still runs.

- [ ] **Step 6: Implement diagnostics copy/export/clear activity**

`DiagnosticsActivity` displays the newest safe summary, `复制诊断信息`, `导出诊断日志`, and `清除诊断日志`. Copy uses `ClipboardManager`; export writes `nextsay-diagnostics-<timestamp>.txt` under `cacheDir/diagnostics`, obtains a `content://` URI from `FileProvider`, and starts `ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION` and MIME type `text/plain`.

- [ ] **Step 7: Register application, activity, and FileProvider**

```xml
<application
    android:name=".NextSayApplication"
    android:allowBackup="false">
    <activity android:name=".diagnostics.DiagnosticsActivity" android:exported="false" />
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.diagnostics"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/diagnostic_file_paths" />
    </provider>
</application>
```

Set `diagnostic_file_paths.xml` to expose only `<cache-path name="diagnostics" path="diagnostics/" />`. Add a `诊断日志` button to `MainActivity`; when a prior crash event exists, show `检测到上次异常，可导出诊断日志`.

- [ ] **Step 8: Run tests and assemble APK**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest :android-app:assembleDebug --no-daemon
```

Expected: crash/export tests and existing tests pass; manifest merge and APK assembly succeed.

- [ ] **Step 9: Commit diagnostics UI and application wiring**

```powershell
git add android-app/src/main/java/app/nextsay/NextSayApplication.kt android-app/src/main/java/app/nextsay/AppDependencies.kt android-app/src/main/java/app/nextsay/diagnostics android-app/src/test/java/app/nextsay/diagnostics android-app/src/main/res/xml/diagnostic_file_paths.xml android-app/src/main/AndroidManifest.xml android-app/src/main/java/app/nextsay/MainActivity.kt
git commit -m "feat: export local diagnostics without ADB"
```

### Task 9: Overlay, IME, and Accessibility Runtime Integration

**Files:**
- Modify: `android-app/src/main/java/app/nextsay/accessibility/NextSayAccessibilityService.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayState.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayController.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/OverlayViewFactory.kt`
- Modify: `android-app/src/main/java/app/nextsay/overlay/QuickReplyViewFactory.kt`
- Modify: `android-app/src/main/java/app/nextsay/ime/ImeReplyState.kt`
- Modify: `android-app/src/main/java/app/nextsay/ime/ImeReplySession.kt`
- Modify: `android-app/src/main/java/app/nextsay/ime/ImeKeyboardViewFactory.kt`
- Modify: `android-app/src/main/java/app/nextsay/ime/NextSayInputMethodService.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/OverlayControllerTest.kt`
- Modify: `android-app/src/test/java/app/nextsay/overlay/QuickReplyPresenterTest.kt`
- Modify: `android-app/src/test/java/app/nextsay/ime/ImeReplySessionTest.kt`

**Interfaces:**
- Consumes: `AppDependencies`, direct `NextSayRepository`, `ProviderException.diagnosticId`, and `DiagnosticFormatter`.
- Produces: user-visible stable error codes plus copyable diagnostic summaries from overlay and IME; no capture/network attempt when configuration is missing.

- [ ] **Step 1: Write failing error-correlation tests**

```kotlin
@Test fun `provider failure preserves diagnostic ID in overlay state`() = runTest {
    val controller = OverlayController { _, _, _, _ ->
        Result.failure(ProviderException(ProviderErrorCode.API_AUTH, "evt-auth", 401))
    }
    controller.showPreview(context)
    controller.generate()
    val error = controller.state.value as OverlayState.Error
    assertEquals("evt-auth", error.diagnosticId)
    assertTrue(error.message.contains("API-AUTH"))
}

@Test fun `IME error preserves diagnostic ID`() = runTest {
    val session = readySession(ImeGenerationHandler {
        Result.failure(ProviderException(ProviderErrorCode.NET_TIMEOUT, "evt-timeout"))
    })
    session.requestGeneration()
    val error = session.state.value as ImeReplyState.Error
    assertEquals("evt-timeout", error.diagnosticId)
}

@Test fun `quick error offers diagnostic action only when ID exists`() {
    assertEquals(
        "evt-1",
        (QuickReplyPresenter().render(OverlayState.Error(context, "失败", "evt-1")) as QuickReplyModel.Error).diagnosticId,
    )
}
```

- [ ] **Step 2: Run focused UI-state tests and verify RED**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.overlay.*" --tests "app.nextsay.ime.*" --no-daemon
```

Expected: compilation fails because error states lack diagnostic IDs and generation lambdas lack surfaces.

- [ ] **Step 3: Carry diagnostic IDs through controller, overlay, and IME states**

Add `diagnosticId: String? = null` to `OverlayState.Error`, `QuickReplyModel.Error`, and `ImeReplyState.Error`. Update `OverlayController` generation callback to accept `DiagnosticSurface`; extract `ProviderException.diagnosticId` on failure. Update `ImeReplySession` similarly. Map missing/failed context capture to `APP-CAPTURE`, failed candidate insertion to `APP-INSERT`, `TimeoutCancellationException` to `NET-TIMEOUT`, and every remaining local generation exception to `APP-INTERNAL`. Record each through `DiagnosticEventFactory` so every actionable generation failure has both a stable code and event ID.

- [ ] **Step 4: Add copy-diagnostics actions to all error surfaces**

Extend callbacks with these exact members:

```kotlin
data class QuickReplyCallbacks(
    val onCandidate: (ReplyCandidate) -> Unit,
    val onGenerate: (String) -> Unit,
    val onRetry: (String) -> Unit,
    val onCopyDiagnostics: (String) -> Unit,
)

data class ImeKeyboardCallbacks(
    val onGenerate: () -> Unit,
    val onCandidate: (Int) -> Unit,
    val onSwitchInputMethod: () -> Unit,
    val onCopyDiagnostics: (String) -> Unit,
)
```

Add the matching member to `PanelCallbacks`. Render `复制诊断信息` only when the state has an ID; callbacks look up that ID in `DiagnosticRecorder`, format it, and copy it to the clipboard. If lookup fails, copy the stable code and ID already present in state instead of exposing internal data.

- [ ] **Step 5: Replace backend construction in the accessibility service**

```kotlin
val dependencies = nextSayDependencies
val repository = NextSayRepository(
    dependencies.providerConfigStore,
    dependencies.providerConfigValidator,
    dependencies.replyProviderClient,
    redactor,
    dependencies.diagnostics,
    dependencies.diagnosticEventFactory,
)
controller = OverlayController { context, instruction, relationship, surface ->
    repository.generate(context, instruction, relationship, surface)
}
```

Remove `BuildConfig.BACKEND_URL`, `BuildConfig.DEV_TOKEN`, and `NextSayApiFactory` imports. Before quick capture, advanced generation, or IME capture, check `providerConfigStore.load()`. When missing, record one typed failure event and show `请先打开 NextSay 配置模型服务（CFG-MISSING）` without capturing or making a network request.

- [ ] **Step 6: Replace raw Android logging with typed diagnostics or debug-only mirroring**

Remove direct `android.util.Log` calls from `NextSayAccessibilityService` and `OverlayViewFactory`. Keep only useful typed events for failures and lifecycle milestones. `RotatingDiagnosticRecorder` is the single place allowed to mirror sanitized event metadata to Logcat, guarded by `BuildConfig.DEBUG`; it must never pass a throwable or raw exception message to `Log`.

- [ ] **Step 7: Run focused and full Android tests**

Run:

```powershell
.\gradlew.bat :android-app:testDebugUnitTest --tests "app.nextsay.overlay.*" --tests "app.nextsay.ime.*" --tests "app.nextsay.api.*" --no-daemon
.\gradlew.bat :android-app:testDebugUnitTest --no-daemon
```

Expected: focused and full tests pass with direct provider surfaces and diagnostic correlation.

- [ ] **Step 8: Run privacy static searches**

Run:

```powershell
rg -n "BACKEND_URL|DEV_TOKEN|NextSayApiFactory|Authorization.*Log|body\(\).*Log|Log\.(v|d|i|w|e)" android-app/src/main/java
rg -n "sk-[A-Za-z0-9_-]{8,}|Bearer [A-Za-z0-9_-]{8,}" android-app/src/main
```

Expected: no backend build constants or direct `Log` calls remain outside the sanitized debug mirror; no credential-like literal is found.

- [ ] **Step 9: Commit runtime integration**

```powershell
git add android-app/src/main/java/app/nextsay/accessibility android-app/src/main/java/app/nextsay/overlay android-app/src/main/java/app/nextsay/ime android-app/src/test/java/app/nextsay/overlay android-app/src/test/java/app/nextsay/ime
git commit -m "feat: use BYOK provider across overlay and IME"
```

### Task 10: Build Configuration, Documentation, and Full APK Verification

**Files:**
- Modify: `android-app/build.gradle.kts`
- Modify: `android-app/src/main/AndroidManifest.xml`
- Modify: `README.md`
- Modify: `backend/README.md`
- Verify: `android-app/build/outputs/apk/debug/android-app-debug.apk`

**Interfaces:**
- Consumes: the completed Android direct-provider path and retained optional backend.
- Produces: documented standalone installation/configuration steps and a freshly verified installable APK.

- [ ] **Step 1: Remove obsolete backend build constants and tighten cleartext policy**

Delete these `defaultConfig` fields:

```kotlin
buildConfigField("String", "BACKEND_URL", "\"${project.findProperty("NEXTSAY_BACKEND_URL") ?: "http://10.0.2.2:8000/"}\"")
buildConfigField("String", "DEV_TOKEN", "\"${project.findProperty("NEXTSAY_DEV_TOKEN") ?: "local-dev-token"}\"")
```

Set `android:usesCleartextTraffic="false"` in the main manifest. Add `android-app/src/debug/AndroidManifest.xml` with only an application override `android:usesCleartextTraffic="true"` so debug builds retain local HTTP testing while release builds cannot use cleartext.

- [ ] **Step 2: Update user documentation for standalone BYOK operation**

Document this exact first-run sequence in `README.md`:

```text
1. 安装并打开 NextSay。
2. 打开“配置模型服务”。
3. 填写 OpenAI 兼容 API 地址、自己的 API Key 和模型名称。
4. 点击“测试连接”；成功后点击“保存”。
5. 开启无障碍服务或 NextSay 输入法后使用。
6. 报错时打开“诊断日志”，复制或导出文件用于排查。
```

State clearly that no computer, ADB connection, or NextSay server is required after installation. Update `backend/README.md` to label the Python service as optional legacy/proxy infrastructure, not the default Android path.

- [ ] **Step 3: Run backend regression tests**

Run:

```powershell
$env:PYTHONPATH='D:\agent\codex\nextsay\backend\src'
& '.\.venv\Scripts\python.exe' -m pytest backend\tests -q
```

Expected: 18 tests pass.

- [ ] **Step 4: Run Android unit tests and APK assembly from clean outputs**

Run:

```powershell
$env:JAVA_HOME='D:\agent\codex\nextsay\.tools\jdk17-clean\jdk17.0.20_8'
$env:ANDROID_HOME='D:\agent\codex\nextsay\.android-sdk'
.\gradlew.bat :android-app:clean :android-app:testDebugUnitTest :android-app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`, zero unit-test failures, and a new `android-app/build/outputs/apk/debug/android-app-debug.apk`.

- [ ] **Step 5: Verify the APK contains no configured secret or obsolete backend address**

Run:

```powershell
$apk='android-app\build\outputs\apk\debug\android-app-debug.apk'
Get-Item $apk | Select-Object FullName,Length,LastWriteTime
rg -a -n "local-dev-token|10\.0\.2\.2:8000|NEXTSAY_API_KEY|sk-[A-Za-z0-9_-]{8,}" $apk
```

Expected: APK exists with the current timestamp; the secret/address scan returns zero matches.

- [ ] **Step 6: Perform manual device acceptance without development tethering**

Install the APK, disconnect ADB and the development computer, then verify:

1. Missing configuration blocks generation and points to settings.
2. DeepSeek-compatible URL, user key, and model pass connection test and save.
3. Editing any field disables Save until retested.
4. Overlay generation returns exactly three candidates over the phone's own Wi-Fi or mobile data.
5. IME generation returns exactly three candidates and inserts only the selected text.
6. Authentication failure shows `API-AUTH` and a copy-diagnostics action.
7. Exported diagnostics identify host/model/status/error ID but contain no API key or conversation/candidate text.
8. Clearing diagnostics removes all events and prior crash indication.

- [ ] **Step 7: Commit documentation and delivery configuration**

```powershell
git add android-app/build.gradle.kts android-app/src/main/AndroidManifest.xml android-app/src/debug/AndroidManifest.xml README.md backend/README.md
git commit -m "docs: document standalone BYOK Android setup"
```

- [ ] **Step 8: Record final evidence**

Record the backend test count, Android test count, APK path/size/timestamp, provider used for manual verification without the API key, and the diagnostic export inspection result in the implementation handoff. Do not claim completion if device acceptance or secret scanning has not been performed.
