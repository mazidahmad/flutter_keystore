package mvzd.flutter_keystore.ciphers

interface StorageCipher {
    fun encrypt(input: ByteArray): ByteArray
    fun decrypt(input: ByteArray): ByteArray
}