package app.nextsay.provider

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
    ) : ProviderConfigValidation {
        fun asValidatedProviderConfig() = ValidatedProviderConfig(
            config = config,
            chatCompletionsUrl = chatCompletionsUrl,
            scheme = scheme,
            host = host,
        )
    }

    data class Invalid(val error: ProviderConfigError) : ProviderConfigValidation
}
