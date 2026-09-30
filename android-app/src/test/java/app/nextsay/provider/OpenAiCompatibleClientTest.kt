package app.nextsay.provider

import app.nextsay.api.MessageDto
import app.nextsay.api.ReplyRequestDto
import app.nextsay.diagnostics.DiagnosticEvent
import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticFormatter
import app.nextsay.diagnostics.DiagnosticMetadata
import app.nextsay.diagnostics.DiagnosticRecorder
import app.nextsay.diagnostics.DiagnosticSurface
import com.google.gson.Gson
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class OpenAiCompatibleClientTest {
    private val gson = Gson()
    private val diagnostics = MemoryDiagnosticRecorder()
    private val eventFactory = DiagnosticEventFactory(
        metadataProvider = {
            DiagnosticMetadata("0.1.0", "test", "14", "Test Device")
        },
        nowMillis = { 1_000L },
        newId = { "event-${diagnostics.events().size + 1}" },
    )
    private var server: MockWebServer? = null

    @After
    fun tearDown() {
        server?.shutdown()
    }

    @Test
    fun `posts bearer request to normalized endpoint without leaking key to diagnostics`() = runTest {
        val server = startServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))

        val result = client(server).generate(validated(server), request(), DiagnosticSurface.OVERLAY)

        assertEquals(3, result.size)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer secret-key", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("deepseek-chat"))
        assertFalse(diagnostics.events().toString().contains("secret-key"))
    }

    @Test
    fun `user entered model cannot inject credentials into diagnostic export`() = runTest {
        val server = startServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))
        val config = validated(server).copy(
            config = validated(server).config.copy(
                model = "deepseek-chat Authorization: Bearer review-secret-key 私密对话",
            ),
        )

        client(server).generate(config, request(), DiagnosticSurface.OVERLAY)

        val export = DiagnosticFormatter().export(diagnostics.events())
        assertFalse(export.contains("review-secret-key"))
        assertFalse(export.contains("Authorization"))
        assertFalse(export.contains("私密对话"))
    }

    @Test
    fun `configured key is removed even when it looks like a model identifier`() = runTest {
        val server = startServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}",
        ))
        val key = "x9A34eTp03Value"
        val config = validated(server).copy(
            config = validated(server).config.copy(apiKey = key, model = key),
        )

        client(server).testConnection(config)

        assertFalse(diagnostics.events().toString().contains(key))
        assertFalse(DiagnosticFormatter().export(diagnostics.events()).contains(key))
    }

    @Test
    fun `retries once without response format only when provider rejects that option`() = runTest {
        val server = startServer()
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("response_format is unsupported"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody()))

        client(server).generate(validated(server), request(), DiagnosticSurface.OVERLAY)

        assertTrue(server.takeRequest().body.readUtf8().contains("response_format"))
        assertFalse(server.takeRequest().body.readUtf8().contains("response_format"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `maps authentication quota model and incompatible response`() = runTest {
        assertProviderCode(401, "{}", ProviderErrorCode.API_AUTH)
        assertProviderCode(429, "{}", ProviderErrorCode.API_QUOTA)
        assertProviderCode(404, "{}", ProviderErrorCode.API_MODEL)
        assertProviderCode(200, "{\"choices\":[]}", ProviderErrorCode.API_INCOMPATIBLE)
    }

    @Test
    fun `malformed successful responses are correlated as incompatible`() = runTest {
        for (body in listOf(
            "not-json",
            "{\"choices\":null}",
            "{\"choices\":[{\"message\":null}]}",
            "{\"choices\":[{\"message\":{\"content\":12}}]}",
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"candidates\\\":null}\"}}]}",
        )) {
            assertProviderCode(
                200, body, ProviderErrorCode.API_INCOMPATIBLE,
                expectedHttpStatus = if (body == "not-json") null else 200,
            )
            assertEquals("API-INCOMPATIBLE", diagnostics.events().last().errorCode)
        }
    }

    @Test
    fun `connection test maps malformed success body to incompatible`() = runTest {
        for (body in listOf(
            "not-json",
            "{\"choices\":null}",
            "{\"choices\":[{\"message\":null}]}",
            "{\"choices\":[{\"message\":{\"content\":12}}]}",
            "{\"choices\":[{\"message\":{\"content\":false}}]}",
        )) {
            val server = startServer()
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            val failure = runCatching { client(server).testConnection(validated(server)) }.exceptionOrNull()
            assertTrue(failure is ProviderException)
            assertEquals(ProviderErrorCode.API_INCOMPATIBLE, (failure as ProviderException).code)
            assertEquals("API-INCOMPATIBLE", diagnostics.events().last().errorCode)
            server.shutdown()
            this@OpenAiCompatibleClientTest.server = null
        }
    }

    @Test
    fun `maps timeout DNS TLS and connection failures`() = runTest {
        assertTransportCode(SocketTimeoutException(), ProviderErrorCode.NET_TIMEOUT)
        assertTransportCode(UnknownHostException(), ProviderErrorCode.NET_DNS)
        assertTransportCode(SSLException("tls"), ProviderErrorCode.NET_TLS)
        assertTransportCode(ConnectException(), ProviderErrorCode.NET_CONNECT)
    }

    @Test
    fun `coroutine cancellation is not converted into provider failure`() = runTest {
        val api = throwingApi(CancellationException("cancelled"))

        val thrown = runCatching {
            client(api).generate(localValidated(), request(), DiagnosticSurface.OVERLAY)
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `connection test uses tiny prompt and does not require candidate JSON`() = runTest {
        val server = startServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}",
            ),
        )

        client(server).testConnection(validated(server))

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("仅回复 OK"))
        assertTrue(body.contains("\"max_tokens\":4"))
        assertFalse(body.contains("response_format"))
    }

    private suspend fun assertProviderCode(
        status: Int,
        body: String,
        expected: ProviderErrorCode,
        expectedHttpStatus: Int? = status,
    ) {
        val server = startServer()
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
        val error = runCatching {
            client(server).generate(validated(server), request(), DiagnosticSurface.OVERLAY)
        }.exceptionOrNull() as ProviderException
        assertEquals(expected, error.code)
        assertEquals(expectedHttpStatus, error.httpStatus)
        server.shutdown()
        this.server = null
    }

    private suspend fun assertTransportCode(error: Exception, expected: ProviderErrorCode) {
        val api = throwingApi(error)
        val thrown = runCatching {
            client(api).generate(localValidated(), request(), DiagnosticSurface.OVERLAY)
        }.exceptionOrNull() as ProviderException
        assertEquals(expected, thrown.code)
    }

    private fun throwingApi(error: Exception) = object : OpenAiCompatibleApi {
        override suspend fun complete(
            url: String,
            authorization: String,
            request: ChatCompletionRequestDto,
        ): Response<ChatCompletionResponseDto> = throw error
    }

    private fun startServer() = MockWebServer().also {
        it.start()
        server = it
    }

    private fun client(server: MockWebServer): OpenAiCompatibleClient = OpenAiCompatibleClient.create(
        okHttpClient = OkHttpClient.Builder().build(),
        gson = gson,
        diagnostics = diagnostics,
        eventFactory = eventFactory,
    )

    private fun client(api: OpenAiCompatibleApi) = OpenAiCompatibleClient(
        api = api,
        promptBuilder = ReplyPromptBuilder(gson),
        parser = ReplyCandidateParser(gson),
        diagnostics = diagnostics,
        eventFactory = eventFactory,
        elapsedMillis = { 1_000L },
    )

    private fun validated(server: MockWebServer) = ValidatedProviderConfig(
        config = ProviderConfig(
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            apiKey = "secret-key",
            model = "deepseek-chat",
        ),
        chatCompletionsUrl = server.url("/v1/chat/completions").toString(),
        scheme = "http",
        host = server.hostName,
    )

    private fun localValidated() = ValidatedProviderConfig(
        config = ProviderConfig("https://api.example/v1", "secret-key", "deepseek-chat"),
        chatCompletionsUrl = "https://api.example/v1/chat/completions",
        scheme = "https",
        host = "api.example",
    )

    private fun request() = ReplyRequestDto(
        messages = listOf(MessageDto("other", "明天能交吗", 0.9f)),
        draft = "",
        instruction = "",
        relationship = "manager",
    )

    private fun successBody() =
        """{"choices":[{"message":{"content":"{\"candidates\":[{\"style\":\"concise\",\"text\":\"可以\"},{\"style\":\"tactful\",\"text\":\"好的，我会尽快完成\"},{\"style\":\"natural\",\"text\":\"行，弄好后发你\"}]}"}}]}"""

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
