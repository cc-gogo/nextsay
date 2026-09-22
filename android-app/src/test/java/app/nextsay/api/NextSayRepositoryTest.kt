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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextSayRepositoryTest {
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

        assertTrue(result.isFailure)
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
