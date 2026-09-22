package app.nextsay.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesProviderConfigStoreTest {
    private val values = FakePreferenceValues()
    private val cipher = PrefixSecretCipher()
    private val store = SharedPreferencesProviderConfigStore(values, cipher)

    @Test
    fun `round trips one configuration without storing plaintext key`() {
        val config = ProviderConfig("https://api.example/v1", "secret-key", "model-a")

        store.save(config)

        assertEquals(config, store.load())
        assertEquals("enc:secret-key", values.getString("api_key"))
        assertFalse(values.snapshot().values.contains("secret-key"))
    }

    @Test
    fun `saving replaces all fields atomically`() {
        store.save(ProviderConfig("https://one/v1", "one", "m1"))

        store.save(ProviderConfig("https://two/v1", "two", "m2"))

        assertEquals(ProviderConfig("https://two/v1", "two", "m2"), store.load())
        assertEquals(2, values.replaceCalls)
    }

    @Test
    fun `partial or undecryptable data is cleared and treated as missing`() {
        values.putAll(
            mapOf(
                "base_url" to "https://x/v1",
                "api_key" to "broken",
                "model" to "m",
            ),
        )

        assertNull(store.load())
        assertTrue(values.snapshot().isEmpty())
    }

    @Test
    fun `clear removes every stored field`() {
        store.save(ProviderConfig("https://x/v1", "key", "model"))

        store.clear()

        assertNull(store.load())
    }

    private class PrefixSecretCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"

        override fun decrypt(ciphertext: String): String {
            require(ciphertext.startsWith("enc:"))
            return ciphertext.removePrefix("enc:")
        }
    }

    private class FakePreferenceValues : PreferenceValues {
        private val values = linkedMapOf<String, String>()
        var replaceCalls: Int = 0
            private set

        override fun getString(key: String): String? = values[key]

        override fun replace(values: Map<String, String>): Boolean {
            replaceCalls += 1
            this.values.clear()
            this.values.putAll(values)
            return true
        }

        override fun clear(): Boolean {
            values.clear()
            return true
        }

        fun putAll(entries: Map<String, String>) {
            values.putAll(entries)
        }

        fun snapshot(): Map<String, String> = values.toMap()
    }
}
