package app.nextsay.provider

import android.content.Context

class SharedPreferencesProviderConfigStore(
    private val values: PreferenceValues,
    private val cipher: SecretCipher,
) : ProviderConfigStore {
    constructor(context: Context, cipher: SecretCipher) : this(
        AndroidPreferenceValues(context),
        cipher,
    )

    override fun load(): ProviderConfig? {
        val baseUrl = values.getString(BASE_URL)
        val encryptedApiKey = values.getString(API_KEY)
        val model = values.getString(MODEL)
        if (baseUrl == null && encryptedApiKey == null && model == null) return null
        if (baseUrl == null || encryptedApiKey == null || model == null) {
            values.clear()
            return null
        }
        return try {
            ProviderConfig(baseUrl, cipher.decrypt(encryptedApiKey), model)
        } catch (_: Exception) {
            values.clear()
            null
        }
    }

    override fun save(config: ProviderConfig) {
        val encryptedApiKey = cipher.encrypt(config.apiKey)
        val committed = values.replace(
            mapOf(
                BASE_URL to config.baseUrl,
                API_KEY to encryptedApiKey,
                MODEL to config.model,
            ),
        )
        if (!committed) {
            values.clear()
            throw IllegalStateException("Unable to save provider configuration")
        }
    }

    override fun clear() {
        check(values.clear()) { "Unable to clear provider configuration" }
    }

    private companion object {
        const val BASE_URL = "base_url"
        const val API_KEY = "api_key"
        const val MODEL = "model"
    }
}

class AndroidPreferenceValues(context: Context) : PreferenceValues {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun replace(values: Map<String, String>): Boolean = preferences.edit()
        .clear()
        .also { editor -> values.forEach(editor::putString) }
        .commit()

    override fun clear(): Boolean = preferences.edit().clear().commit()

    private companion object {
        const val PREFERENCES_NAME = "provider_config"
    }
}
