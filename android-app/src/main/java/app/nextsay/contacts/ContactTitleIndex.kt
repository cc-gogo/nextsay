package app.nextsay.contacts

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Preserves the existing HMAC format/key; contact lookup never uses an API key. */
internal object ContactTitleIndex {
    fun hash(normalizedTitle: String): String {
        val key = synchronized(this) {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey("nextsay-contact-index-v1", null) as? SecretKey)
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder("nextsay-contact-index-v1", KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).build())
                }.generateKey()
        }
        return Mac.getInstance("HmacSHA256").apply { init(key) }
            .doFinal(normalizedTitle.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
