package app.nextsay.provider

import app.nextsay.api.ReplyCandidateDto
import app.nextsay.api.ReplyRequestDto
import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.diagnostics.DiagnosticRecorder
import app.nextsay.diagnostics.DiagnosticSurface
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OpenAiCompatibleClient(
    private val api: OpenAiCompatibleApi,
    private val promptBuilder: ReplyPromptBuilder,
    private val parser: ReplyCandidateParser,
    private val diagnostics: DiagnosticRecorder,
    private val eventFactory: DiagnosticEventFactory,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val connectionTestApi: OpenAiCompatibleApi = api,
) : ReplyProviderClient {
    override suspend fun generate(
        config: ValidatedProviderConfig,
        request: ReplyRequestDto,
        surface: DiagnosticSurface,
    ): List<ReplyCandidateDto> = runRecorded(
        config = config,
        surface = surface,
        startedType = DiagnosticEventType.GENERATION_STARTED,
        succeededType = DiagnosticEventType.GENERATION_SUCCEEDED,
        failedType = DiagnosticEventType.GENERATION_FAILED,
    ) {
        val messages = promptBuilder.build(request)
        val firstRequest = ChatCompletionRequestDto(
            model = config.config.model,
            messages = messages,
            thinking = quickReplyThinking(config),
        )
        var response = execute(config, firstRequest)
        if (response.code() == 400 && rejectsResponseFormat(response)) {
            response = execute(config, firstRequest.copy(response_format = null))
        }
        val content = requireContent(response)
        try {
            parser.parse(content)
        } catch (error: IllegalArgumentException) {
            throw RawProviderFailure(
                ProviderErrorCode.API_INCOMPATIBLE,
                response.code(),
                error,
                responseMetadata(response.body()),
            )
        }
    }

    override suspend fun testConnection(config: ValidatedProviderConfig) {
        runRecorded(
            config = config,
            surface = DiagnosticSurface.SETTINGS,
            startedType = DiagnosticEventType.CONNECTION_TEST_STARTED,
            succeededType = DiagnosticEventType.CONNECTION_TEST_SUCCEEDED,
            failedType = DiagnosticEventType.CONNECTION_TEST_FAILED,
        ) {
            val response = execute(
                config,
                ChatCompletionRequestDto(
                    model = config.config.model,
                    messages = listOf(OpenAiMessageDto("user", "仅回复 OK")),
                    temperature = 0.0,
                    response_format = null,
                    // Reasoning and the final answer may share the output budget.
                    max_tokens = 1024,
                    thinking = quickReplyThinking(config),
                ),
                requestApi = connectionTestApi,
            )
            requireContent(response)
        }
    }

    private suspend fun execute(
        config: ValidatedProviderConfig,
        request: ChatCompletionRequestDto,
        requestApi: OpenAiCompatibleApi = api,
    ): Response<ChatCompletionResponseDto> = try {
        requestApi.complete(
            url = config.chatCompletionsUrl,
            authorization = "Bearer ${config.config.apiKey}",
            request = request,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        // A cancelled response may surface either a transport or decoding error.
        // Cancellation takes precedence over all failure classifications.
        currentCoroutineContext().ensureActive()
        val code = when (error) {
            is JsonParseException, is MalformedJsonException, is EOFException, is IllegalStateException ->
                ProviderErrorCode.API_INCOMPATIBLE
            else -> mapTransportError(error)
        }
        throw RawProviderFailure(code, cause = error)
    }

    private fun quickReplyThinking(config: ValidatedProviderConfig): ThinkingModeDto? =
        if (config.host.equals("api.deepseek.com", ignoreCase = true)) {
            ThinkingModeDto("disabled")
        } else null // Do not send provider-specific options to generic compatible APIs.

    private fun requireSuccess(
        response: Response<ChatCompletionResponseDto>,
    ): ChatCompletionResponseDto {
        if (!response.isSuccessful) {
            throw RawProviderFailure(mapHttpStatus(response.code()), response.code())
        }
        return response.body()
            ?: throw RawProviderFailure(ProviderErrorCode.API_INCOMPATIBLE, response.code())
    }

    private fun rejectsResponseFormat(response: Response<ChatCompletionResponseDto>): Boolean {
        val body = runCatching { response.errorBody()?.string().orEmpty() }.getOrDefault("")
        return body.contains("response_format", ignoreCase = true) ||
            body.contains("json_object", ignoreCase = true)
    }

    private fun requireContent(response: Response<ChatCompletionResponseDto>): String {
        val body = requireSuccess(response)
        val metadata = responseMetadata(body)
        if (metadata.finishReason == "length") {
            throw RawProviderFailure(
                ProviderErrorCode.API_OUTPUT_LIMIT, response.code(), metadata = metadata,
            )
        }
        val content = body.choices?.firstOrNull()?.message?.content
        if (content == null || !content.isJsonPrimitive || !content.asJsonPrimitive.isString) {
            throw RawProviderFailure(
                ProviderErrorCode.API_INCOMPATIBLE, response.code(), metadata = metadata,
            )
        }
        return content.asString.takeIf { it.isNotBlank() }
            ?: throw RawProviderFailure(
                ProviderErrorCode.API_INCOMPATIBLE, response.code(), metadata = metadata,
            )
    }

    /** Reduce provider data to fixed labels/booleans; never retain answer or reasoning text. */
    private fun responseMetadata(body: ChatCompletionResponseDto?): ResponseMetadata {
        val choice = body?.choices?.firstOrNull()
        val rawReason = choice?.finish_reason
        val reason = rawReason?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.takeIf { it in FINISH_REASONS }
        val content = choice?.message?.content
        val contentState = when {
            content == null || content.isJsonNull -> "missing"
            !content.isJsonPrimitive || !content.asJsonPrimitive.isString -> "non_string"
            content.asString.isBlank() -> "blank"
            else -> "present"
        }
        val reasoningPresent = choice?.message?.reasoning_content?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank()
        }
        return ResponseMetadata(reason, contentState, reasoningPresent)
    }

    private suspend fun <T> runRecorded(
        config: ValidatedProviderConfig,
        surface: DiagnosticSurface,
        startedType: DiagnosticEventType,
        succeededType: DiagnosticEventType,
        failedType: DiagnosticEventType,
        operation: suspend () -> T,
    ): T {
        val startedAt = elapsedMillis()
        diagnostics.record(eventFactory.create(startedType, surface, config))
        return try {
            operation().also {
                diagnostics.record(
                    eventFactory.create(
                        succeededType,
                        surface,
                        config,
                        durationMillis = elapsedMillis() - startedAt,
                    ),
                )
            }
        } catch (failure: RawProviderFailure) {
            val event = eventFactory.create(
                failedType,
                surface,
                config,
                httpStatus = failure.httpStatus,
                durationMillis = elapsedMillis() - startedAt,
                errorCode = failure.code.wireCode,
                finishReason = failure.metadata?.finishReason,
                contentState = failure.metadata?.contentState,
                reasoningPresent = failure.metadata?.reasoningPresent,
                exceptionClass = failure.cause?.javaClass?.name,
            )
            diagnostics.record(event)
            throw ProviderException(
                code = failure.code,
                diagnosticId = event.id,
                httpStatus = failure.httpStatus,
                cause = failure.cause,
            )
        }
    }

    private fun DiagnosticEventFactory.create(
        type: DiagnosticEventType,
        surface: DiagnosticSurface,
        config: ValidatedProviderConfig,
        httpStatus: Int? = null,
        durationMillis: Long? = null,
        errorCode: String? = null,
        finishReason: String? = null,
        contentState: String? = null,
        reasoningPresent: Boolean? = null,
        exceptionClass: String? = null,
    ) = create(
        type = type,
        surface = surface,
        providerScheme = safeMetadata(config.scheme, config),
        providerHost = safeMetadata(config.host, config),
        model = safeMetadata(config.config.model, config),
        httpStatus = httpStatus,
        durationMillis = durationMillis,
        errorCode = errorCode,
        finishReason = finishReason?.let { safeMetadata(it, config) },
        contentState = contentState,
        reasoningPresent = reasoningPresent,
        exceptionClass = exceptionClass?.let { safeMetadata(it, config) },
    )

    private fun safeMetadata(value: String, config: ValidatedProviderConfig): String? =
        value.takeUnless { it.contains(config.config.apiKey, ignoreCase = true) }

    private fun mapHttpStatus(status: Int): ProviderErrorCode = when (status) {
        401, 403 -> ProviderErrorCode.API_AUTH
        402, 429 -> ProviderErrorCode.API_QUOTA
        404 -> ProviderErrorCode.API_MODEL
        else -> ProviderErrorCode.API_HTTP
    }

    private fun mapTransportError(error: Throwable): ProviderErrorCode = when (error) {
        is InterruptedIOException -> ProviderErrorCode.NET_TIMEOUT
        is UnknownHostException -> ProviderErrorCode.NET_DNS
        is SSLException -> ProviderErrorCode.NET_TLS
        is ConnectException -> ProviderErrorCode.NET_CONNECT
        else -> ProviderErrorCode.NET_CONNECT
    }

    private class RawProviderFailure(
        val code: ProviderErrorCode,
        val httpStatus: Int? = null,
        override val cause: Throwable? = null,
        val metadata: ResponseMetadata? = null,
    ) : RuntimeException(cause)

    private data class ResponseMetadata(
        val finishReason: String?,
        val contentState: String,
        val reasoningPresent: Boolean?,
    )

    companion object {
        private val FINISH_REASONS = setOf(
            "stop", "length", "tool_calls", "function_call", "content_filter", "insufficient_system_resource",
        )

        fun create(
            okHttpClient: OkHttpClient,
            gson: Gson,
            diagnostics: DiagnosticRecorder,
            eventFactory: DiagnosticEventFactory,
        ): OpenAiCompatibleClient {
            val generationClient = okHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
            val probeClient = okHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
            fun createApi(client: OkHttpClient) = Retrofit.Builder()
                .baseUrl("https://localhost/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(OpenAiCompatibleApi::class.java)
            return OpenAiCompatibleClient(
                api = createApi(generationClient),
                promptBuilder = ReplyPromptBuilder(gson),
                parser = ReplyCandidateParser(gson),
                diagnostics = diagnostics,
                eventFactory = eventFactory,
                connectionTestApi = createApi(probeClient),
            )
        }
    }
}
