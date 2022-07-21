package mvzd.flutter_keystore.ciphers

import javax.crypto.Cipher

interface StorageCipher {
    fun encrypt(input: ByteArray): ByteArray
    fun decrypt(input: ByteArray): ByteArray
}