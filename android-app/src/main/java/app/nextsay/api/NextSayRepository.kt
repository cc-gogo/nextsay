package app.nextsay.api

import app.nextsay.context.ChatContext
import app.nextsay.context.MessageRole
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
import kotlinx.coroutines.CancellationException

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
    ): Result<List<ReplyCandidate>> = try {
        if (context.messages.lastOrNull()?.role == MessageRole.UNKNOWN) {
            throw configurationFailure(ProviderErrorCode.CHAT_ROLE_UNKNOWN, surface)
        }
        val stored = configStore.load() ?: throw configurationFailure(
            ProviderErrorCode.CONFIG_MISSING,
            surface,
        )
        val validated = configValidator.validate(stored.baseUrl, stored.apiKey, stored.model)
        if (validated !is ProviderConfigValidation.Valid) {
            throw configurationFailure(ProviderErrorCode.CONFIG_INVALID, surface)
        }
        var remaining = 3000
        val boundedMessages = context.messages.takeLast(20).asReversed().mapNotNull { message ->
            if (remaining <= 0) null else message.copy(text = message.text.take(remaining)).also { remaining -= it.text.length }
        }.asReversed()
        val extras = context.generationExtras
        // Management is the single source of truth; a stale panel must not override it.
        val selectedRelationship = extras?.relationship ?: relationship.ifBlank { "unspecified" }
        val request = ReplyRequestDto(
            messages = boundedMessages.map {
                MessageDto(it.role.wireValue, redactor.redact(it.text), it.confidence)
            },
            draft = redactor.redact(context.draft.take(300)),
            instruction = redactor.redact(instruction.take(700)),
            relationship = selectedRelationship,
            replyMode = app.nextsay.provider.LoverReplyModes.normalize(selectedRelationship,
                extras?.replyMode.takeIf { selectedRelationship == extras?.relationship }),
            relationshipRules = redactor.redact(extras?.relationshipRules.orEmpty()
                .takeIf { selectedRelationship == extras?.relationship }.orEmpty().take(300)),
            contactDetails = redactor.redact(extras?.details.orEmpty().take(600)),
            contactPreferences = redactor.redact(extras?.preferences.orEmpty().take(400)),
            relevantMemory = redactor.redact(extras?.memory.orEmpty().take(600)),
        )
        val candidates = client.generate(
            validated.asValidatedProviderConfig(),
            request,
            surface,
        )
        if (candidates.size != 3) {
            throw configurationFailure(ProviderErrorCode.API_INCOMPATIBLE, surface)
        }
        Result.success(candidates.map { ReplyCandidate(it.style, it.text) })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
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
