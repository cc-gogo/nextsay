package app.nextsay.api

import app.nextsay.context.ChatContext
import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.diagnostics.DiagnosticRecorder
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.overlay.ReplyCandidate
import app.nextsay.privacy.TextRedactor
import app.nextsay.provider.ProviderConfigStore
import app.nextsay.provider.ProviderConfigValidation
import app.nextsay.provider.ProviderConfigValidator
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import app.nextsay.provider.ReplyProviderClient

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
        if (validated !is ProviderConfigValidation.Valid) {
            throw configurationFailure(ProviderErrorCode.CONFIG_INVALID, surface)
        }
        val request = ReplyRequestDto(
            messages = context.messages.takeLast(20).map {
                MessageDto(it.role.wireValue, redactor.redact(it.text), it.confidence)
            },
            draft = redactor.redact(context.draft),
            instruction = redactor.redact(instruction),
            relationship = relationship,
        )
        val candidates = client.generate(
            validated.asValidatedProviderConfig(),
            request,
            surface,
        )
        if (candidates.size != 3) {
            throw configurationFailure(ProviderErrorCode.API_INCOMPATIBLE, surface)
        }
        candidates.map { ReplyCandidate(it.style, it.text) }
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
