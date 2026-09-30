package app.nextsay.history

interface MessageCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}
