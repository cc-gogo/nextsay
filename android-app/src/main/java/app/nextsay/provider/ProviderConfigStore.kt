package app.nextsay.provider

interface ProviderConfigStore {
    fun load(): ProviderConfig?
    fun save(config: ProviderConfig)
    fun clear()
}

interface PreferenceValues {
    fun getString(key: String): String?
    fun replace(values: Map<String, String>): Boolean
    fun clear(): Boolean
}
