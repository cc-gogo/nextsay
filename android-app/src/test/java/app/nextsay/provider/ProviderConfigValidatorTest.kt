package app.nextsay.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigValidatorTest {
    private val release = ProviderConfigValidator(allowCleartext = false)

    @Test
    fun `normalizes whitespace and trailing slash`() {
        val result = release.validate(
            " https://api.deepseek.com/v1/ ",
            " sk-user ",
            " deepseek-chat ",
        ) as ProviderConfigValidation.Valid

        assertEquals("https://api.deepseek.com/v1", result.config.baseUrl)
        assertEquals("sk-user", result.config.apiKey)
        assertEquals("deepseek-chat", result.config.model)
        assertEquals("https://api.deepseek.com/v1/chat/completions", result.chatCompletionsUrl)
        assertEquals("api.deepseek.com", result.host)
    }

    @Test
    fun `full operation URL is not duplicated`() {
        val result = release.validate(
            "https://example.com/v1/chat/completions",
            "key",
            "model",
        ) as ProviderConfigValidation.Valid

        assertEquals("https://example.com/v1/chat/completions", result.chatCompletionsUrl)
        assertEquals("https://example.com/v1", result.config.baseUrl)
    }

    @Test
    fun `release rejects cleartext URL`() {
        val result = release.validate("http://192.168.1.2:8000/v1", "key", "model")

        assertEquals(
            ProviderConfigValidation.Invalid(ProviderConfigError.HTTPS_REQUIRED),
            result,
        )
    }

    @Test
    fun `debug permits cleartext URL`() {
        val result = ProviderConfigValidator(allowCleartext = true).validate(
            "http://192.168.1.2:8000/v1",
            "key",
            "model",
        )

        assertTrue(result is ProviderConfigValidation.Valid)
    }

    @Test
    fun `rejects missing fields and URL credentials query or fragment`() {
        assertEquals(ProviderConfigError.URL_REQUIRED, invalid("", "k", "m"))
        assertEquals(ProviderConfigError.API_KEY_REQUIRED, invalid("https://x.test/v1", "", "m"))
        assertEquals(ProviderConfigError.MODEL_REQUIRED, invalid("https://x.test/v1", "k", ""))
        assertEquals(ProviderConfigError.INVALID_URL, invalid("https://u:p@x.test/v1", "k", "m"))
        assertEquals(ProviderConfigError.INVALID_URL, invalid("https://x.test/v1?q=1", "k", "m"))
        assertEquals(ProviderConfigError.INVALID_URL, invalid("https://x.test/v1#f", "k", "m"))
    }

    private fun invalid(url: String, key: String, model: String) =
        (release.validate(url, key, model) as ProviderConfigValidation.Invalid).error
}
