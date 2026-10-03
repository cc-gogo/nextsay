package app.nextsay.api

import app.nextsay.context.ChatContext
import app.nextsay.context.ChatMessage
import app.nextsay.context.MessageRole
import app.nextsay.diagnostics.DiagnosticEvent
import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticMetadata
import app.nextsay.diagnostics.DiagnosticRecorder
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.privacy.TextRedactor
import app.nextsay.provider.ProviderConfig
import app.nextsay.provider.ProviderConfigStore
import app.nextsay.provider.ProviderConfigValidator
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import app.nextsay.provider.ReplyProviderClient
import app.nextsay.provider.ValidatedProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextSayRepositoryTest {
    @Test
    fun `saved lover mode reaches the provider for default generation`() = runTest {
        val client = FakeReplyProviderClient(replies = responseCandidates())
        val repository = repository(MutableProviderConfigStore(config()), client)
        val extras = extrasWithMode("lover", "huangmao")
        assertTrue(repository.generate(context().copy(generationExtras = extras), "", "", DiagnosticSurface.OVERLAY).isSuccess)
        val payload = com.google.gson.JsonParser.parseString(com.google.gson.Gson().toJson(client.request)).asJsonObject
        assertTrue(payload.get("replyMode") != null)
        assertEquals("huangmao", payload.get("replyMode").asString)
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun `stale panel relationship cannot override the managed lover mode`() = runTest {
        val client = FakeReplyProviderClient(replies = responseCandidates())
        val repository = repository(MutableProviderConfigStore(config()), client)
        val extras = extrasWithMode("lover", "huangmao")
        assertTrue(repository.generate(context().copy(generationExtras = extras), "", "colleague", DiagnosticSurface.OVERLAY).isSuccess)
        val payload = com.google.gson.JsonParser.parseString(com.google.gson.Gson().toJson(client.request)).asJsonObject
        assertTrue(payload.get("replyMode") != null)
        assertEquals("huangmao", payload.get("replyMode").asString)
        assertEquals("lover", client.request!!.relationship)
    }

    private fun extrasWithMode(relationship: String, mode: String): app.nextsay.contacts.GenerationExtras {
        val gson = com.google.gson.Gson()
        val raw = com.google.gson.JsonParser.parseString(gson.toJson(app.nextsay.contacts.GenerationExtras(relationship))).asJsonObject
        raw.addProperty("replyMode", mode)
        return gson.fromJson(raw, app.nextsay.contacts.GenerationExtras::class.java)
    }

    @Test
    fun `ordinary fallback cannot discard the managed lover relationship and rules`() = runTest {
        val client = FakeReplyProviderClient(replies = responseCandidates())
        val repository = repository(MutableProviderConfigStore(config()), client)
        val enriched = context().copy(generationExtras = app.nextsay.contacts.GenerationExtras("lover", "恋人规则"))
        assertTrue(repository.generate(enriched, "", "unspecified", DiagnosticSurface.OVERLAY).isSuccess)
        assertEquals("lover", client.request!!.relationship)
        assertEquals("恋人规则", client.request!!.relationshipRules)
        assertEquals("lover", enriched.generationExtras!!.relationship)
    }

    @Test
    fun `local contact identity is omitted and selected profile fields stay bounded and redacted`() = runTest {
        val client = FakeReplyProviderClient(replies = responseCandidates())
        val repository = repository(MutableProviderConfigStore(config()), client)
        val enriched = context().copy(
            contactId = "private-local-uuid",
            generationExtras = app.nextsay.contacts.GenerationExtras("lover", "温柔".repeat(500), "联系 13812345678 " + "背景".repeat(800), "自然".repeat(500), "考试".repeat(800)),
            messages = List(30) { ChatMessage(MessageRole.OTHER, "消息".repeat(200), 1f) },
        )
        assertTrue(repository.generate(enriched, "本轮".repeat(800), "", DiagnosticSurface.OVERLAY).isSuccess)
        val request = client.request!!
        assertEquals("lover", request.relationship)
        assertTrue(request.contactDetails.contains("[手机号]"))
        assertTrue(request.messages.sumOf { it.text.length } + request.draft.length + request.instruction.length + request.relationshipRules.length + request.contactDetails.length + request.contactPreferences.length + request.relevantMemory.length <= 6000)
        assertTrue(!com.google.gson.Gson().toJson(request).contains("private-local-uuid"))
        assertTrue(!com.google.gson.Gson().toJson(enriched).contains("private-local-uuid"))
    }
    @Test
    fun `uncertain latest speaker fails locally before sending chat to provider`() = runTest {
        val client = FakeReplyProviderClient(replies = responseCandidates())
        val repository = repository(MutableProviderConfigStore(config()), client)
        val result = repository.generate(
            context().copy(messages = context().messages + ChatMessage(MessageRole.UNKNOWN, "这一句是谁发的", 0.4f)),
            "", "unspecified", DiagnosticSurface.OVERLAY,
        )
        assertTrue(result.isFailure)
        assertEquals(0, client.generateCalls)
        assertEquals(ProviderErrorCode.CHAT_ROLE_UNKNOWN, (result.exceptionOrNull() as ProviderException).code)
    }

    @Test
    fun `missing configuration fails before provider call`() = runTest {
        val client = FakeReplyProviderClient()
        val repository = repository(MutableProviderConfigStore(null), client)

        val result = repository.generate(
            context(),
            "",
            "unspecified",
            DiagnosticSurface.OVERLAY,
        )

        val error = result.exceptionOrNull() as ProviderException
        assertEquals(ProviderErrorCode.CONFIG_MISSING, error.code)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun `redacts reviewed context before direct provider request`() = runTest {
        val client = FakeReplyProviderClient(replies = responseCandidates())
        val repository = repository(MutableProviderConfigStore(config()), client)

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

    @Test
    fun `loads configuration for every generation`() = runTest {
        val store = MutableProviderConfigStore(config("model-a"))
        val client = FakeReplyProviderClient(replies = responseCandidates())
        val repository = repository(store, client)

        repository.generate(context(), "", "unspecified", DiagnosticSurface.OVERLAY)
        store.value = config("model-b")
        repository.generate(context(), "", "unspecified", DiagnosticSurface.OVERLAY)

        assertEquals(listOf("model-a", "model-b"), client.models)
    }

    @Test
    fun `rejects direct response without exactly three candidates`() = runTest {
        val client = FakeReplyProviderClient(replies = responseCandidates().take(2))
        val repository = repository(MutableProviderConfigStore(config()), client)

        val result = repository.generate(
            context(),
            "",
            "unspecified",
            DiagnosticSurface.OVERLAY,
        )

        val error = result.exceptionOrNull() as ProviderException
        assertEquals(ProviderErrorCode.API_INCOMPATIBLE, error.code)
        assertEquals("event-1", error.diagnosticId)
    }

    @Test
    fun `repository propagates cancellation rather than wrapping it as a failed result`() = runTest {
        val client = FakeReplyProviderClient(failure = CancellationException("cancelled"))
        val repository = repository(MutableProviderConfigStore(config()), client)

        val thrown = runCatching {
            repository.generate(context(), "", "unspecified", DiagnosticSurface.OVERLAY)
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    private fun repository(
        store: MutableProviderConfigStore,
        client: FakeReplyProviderClient,
    ): NextSayRepository {
        val diagnostics = MemoryDiagnosticRecorder()
        return NextSayRepository(
            configStore = store,
            configValidator = ProviderConfigValidator(allowCleartext = false),
            client = client,
            redactor = TextRedactor(),
            diagnostics = diagnostics,
            eventFactory = DiagnosticEventFactory(
                metadataProvider = {
                    DiagnosticMetadata("0.1.0", "test", "14", "Test Device")
                },
                nowMillis = { 1_000L },
                newId = { "event-${diagnostics.events().size + 1}" },
            ),
        )
    }

    private fun context() = ChatContext(
        sourceApp = "wechat",
        sourcePackage = "com.tencent.mm",
        messages = listOf(ChatMessage(MessageRole.OTHER, "联系我：13812345678", 0.9f)),
        draft = "",
        confidence = 0.9f,
    )

    private fun config(model: String = "model") = ProviderConfig(
        baseUrl = "https://api.example/v1",
        apiKey = "key",
        model = model,
    )

    private fun responseCandidates() = listOf(
        ReplyCandidateDto("concise", "好"),
        ReplyCandidateDto("tactful", "好的，我会联系您"),
        ReplyCandidateDto("natural", "行，我稍后联系你"),
    )

    private class MutableProviderConfigStore(
        var value: ProviderConfig?,
    ) : ProviderConfigStore {
        override fun load(): ProviderConfig? = value
        override fun save(config: ProviderConfig) {
            value = config
        }
        override fun clear() {
            value = null
        }
    }

    private class FakeReplyProviderClient(
        private val replies: List<ReplyCandidateDto> = emptyList(),
        private val failure: Throwable? = null,
    ) : ReplyProviderClient {
        var generateCalls = 0
        var request: ReplyRequestDto? = null
        var surface: DiagnosticSurface? = null
        val models = mutableListOf<String>()

        override suspend fun generate(
            config: ValidatedProviderConfig,
            request: ReplyRequestDto,
            surface: DiagnosticSurface,
        ): List<ReplyCandidateDto> {
            generateCalls += 1
            this.request = request
            this.surface = surface
            models += config.config.model
            failure?.let { throw it }
            return replies
        }

        override suspend fun testConnection(config: ValidatedProviderConfig) = Unit
    }

    private class MemoryDiagnosticRecorder : DiagnosticRecorder {
        private val values = mutableListOf<DiagnosticEvent>()
        override fun record(event: DiagnosticEvent) {
            values += event
        }
        override fun events(): List<DiagnosticEvent> = values.toList()
        override fun find(id: String): DiagnosticEvent? = values.firstOrNull { it.id == id }
        override fun clear() = values.clear()
    }
}
