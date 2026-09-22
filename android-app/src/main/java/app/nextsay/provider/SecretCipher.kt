package app.nextsay.provider

interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}
