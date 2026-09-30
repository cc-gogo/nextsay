package app.nextsay.settings

import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticEventType
import app.nextsay.diagnostics.DiagnosticRecorder
import app.nextsay.diagnostics.DiagnosticSurface
import app.nextsay.provider.ProviderConfig
import app.nextsay.provider.ProviderConfigStore
import app.nextsay.provider.ProviderConfigValidation
import app.nextsay.provider.ProviderConfigValidator
import app.nextsay.provider.ProviderErrorCode
import app.nextsay.provider.ProviderException
import app.nextsay.provider.ValidatedProviderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProviderSettingsState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val testing: Boolean = false,
    val canSave: Boolean = false,
    val status: String = "",
    val diagnosticId: String? = null,
)

class ProviderSettingsController(
    private val store: ProviderConfigStore,
    private val validator: ProviderConfigValidator,
    private val tester: ProviderConnectionTester,
    private val diagnostics: DiagnosticRecorder,
    private val eventFactory: DiagnosticEventFactory,
) {
    private val initial = store.load()
    private val mutableState = MutableStateFlow(
        ProviderSettingsState(
            baseUrl = initial?.baseUrl.orEmpty(),
            apiKey = initial?.apiKey.orEmpty(),
            model = initial?.model.orEmpty(),
        ),
    )
    private var epoch = 0L
    private var successfulFingerprint: String? = null

    val state: StateFlow<ProviderSettingsState> = mutableState.asStateFlow()

    fun updateUrl(value: String) = update { copy(baseUrl = value) }

    fun updateApiKey(value: String) = update { copy(apiKey = value) }

    fun updateModel(value: String) = update { copy(model = value) }

    suspend fun testConnection() {
        val attemptEpoch = ++epoch
        successfulFingerprint = null
        mutableState.value = mutableState.value.copy(
            testing = true,
            canSave = false,
            status = "正在测试连接…",
            diagnosticId = null,
        )
        val validated = validateCurrent()
        if (validated == null) {
            val event = eventFactory.create(
                type = DiagnosticEventType.CONNECTION_TEST_FAILED,
                surface = DiagnosticSurface.SETTINGS,
                errorCode = ProviderErrorCode.CONFIG_INVALID.wireCode,
            )
            diagnostics.record(event)
            if (epoch == attemptEpoch) {
                mutableState.value = mutableState.value.copy(
                    testing = false,
                    status = errorStatus(ProviderErrorCode.CONFIG_INVALID),
                    diagnosticId = event.id,
                )
            }
            return
        }
        val fingerprint = fingerprint(validated)
        val result = tester.test(validated)
        if (epoch != attemptEpoch || fingerprint != currentFingerprint()) return
        result.fold(
            onSuccess = {
                successfulFingerprint = fingerprint
                mutableState.value = mutableState.value.copy(
                    testing = false,
                    canSave = true,
                    status = "连接成功，可以保存",
                    diagnosticId = null,
                )
            },
            onFailure = { error ->
                val providerError = error as? ProviderException
                val code = providerError?.code ?: ProviderErrorCode.APP_INTERNAL
                val diagnosticId = providerError?.diagnosticId ?: recordUnexpectedFailure(code)
                mutableState.value = mutableState.value.copy(
                    testing = false,
                    canSave = false,
                    status = errorStatus(code),
                    diagnosticId = diagnosticId,
                )
            },
        )
    }

    fun save(): Boolean {
        val validated = validateCurrent() ?: return false
        val fingerprint = fingerprint(validated)
        if (!mutableState.value.canSave || successfulFingerprint != fingerprint) return false
        try {
            store.save(validated.config)
        } catch (_: Exception) {
            val code = ProviderErrorCode.APP_INTERNAL
            successfulFingerprint = null
            mutableState.value = mutableState.value.copy(
                canSave = false,
                status = "保存失败，请重试（${code.wireCode}）",
                diagnosticId = recordUnexpectedFailure(code),
            )
            return false
        }
        mutableState.value = mutableState.value.copy(
            baseUrl = validated.config.baseUrl,
            apiKey = validated.config.apiKey,
            model = validated.config.model,
            status = "已保存",
        )
        return true
    }

    private fun update(transform: ProviderSettingsState.() -> ProviderSettingsState) {
        epoch += 1
        successfulFingerprint = null
        mutableState.value = mutableState.value.transform().copy(
            testing = false,
            canSave = false,
            status = "",
            diagnosticId = null,
        )
    }

    private fun validateCurrent(): ValidatedProviderConfig? {
        val value = mutableState.value
        val validation = validator.validate(value.baseUrl, value.apiKey, value.model)
        return (validation as? ProviderConfigValidation.Valid)?.asValidatedProviderConfig()
    }

    private fun currentFingerprint(): String? = validateCurrent()?.let(::fingerprint)

    private fun fingerprint(config: ValidatedProviderConfig): String = listOf(
        config.config.baseUrl,
        config.config.apiKey,
        config.config.model,
    ).joinToString("\u0000")

    private fun recordUnexpectedFailure(code: ProviderErrorCode): String {
        val event = eventFactory.create(
            type = DiagnosticEventType.CONNECTION_TEST_FAILED,
            surface = DiagnosticSurface.SETTINGS,
            errorCode = code.wireCode,
        )
        diagnostics.record(event)
        return event.id
    }

    private fun errorStatus(code: ProviderErrorCode) =
        "${code.userMessage}（${code.wireCode}）"
}
