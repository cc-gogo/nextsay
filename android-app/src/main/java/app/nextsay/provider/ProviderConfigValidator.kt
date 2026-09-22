package app.nextsay.provider

import java.net.URI

class ProviderConfigValidator(
    private val allowCleartext: Boolean,
) {
    fun validate(baseUrl: String, apiKey: String, model: String): ProviderConfigValidation {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val normalizedKey = apiKey.trim()
        val normalizedModel = model.trim()
        if (normalizedUrl.isBlank()) {
            return ProviderConfigValidation.Invalid(ProviderConfigError.URL_REQUIRED)
        }
        if (normalizedKey.isBlank()) {
            return ProviderConfigValidation.Invalid(ProviderConfigError.API_KEY_REQUIRED)
        }
        if (normalizedModel.isBlank()) {
            return ProviderConfigValidation.Invalid(ProviderConfigError.MODEL_REQUIRED)
        }

        val uri = try {
            URI(normalizedUrl)
        } catch (_: Exception) {
            return ProviderConfigValidation.Invalid(ProviderConfigError.INVALID_URL)
        }
        val scheme = uri.scheme?.lowercase()
            ?: return ProviderConfigValidation.Invalid(ProviderConfigError.INVALID_URL)
        val host = uri.host?.lowercase()
            ?: return ProviderConfigValidation.Invalid(ProviderConfigError.INVALID_URL)
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
            return ProviderConfigValidation.Invalid(ProviderConfigError.INVALID_URL)
        }
        if (scheme != "https" && !(allowCleartext && scheme == "http")) {
            return ProviderConfigValidation.Invalid(ProviderConfigError.HTTPS_REQUIRED)
        }

        val operationSuffix = "/chat/completions"
        val normalizedBaseUrl = if (normalizedUrl.endsWith(operationSuffix)) {
            normalizedUrl.removeSuffix(operationSuffix).trimEnd('/')
        } else {
            normalizedUrl
        }
        val endpoint = "$normalizedBaseUrl$operationSuffix"
        return ProviderConfigValidation.Valid(
            config = ProviderConfig(normalizedBaseUrl, normalizedKey, normalizedModel),
            chatCompletionsUrl = endpoint,
            scheme = scheme,
            host = host,
        )
    }
}
