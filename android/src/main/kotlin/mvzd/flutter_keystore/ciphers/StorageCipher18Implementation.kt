package mvzd.flutter_keystore.ciphers

import android.content.Context
import android.nfc.Tag
import android.util.Base64
import android.util.Log
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class StorageCipher18Implementation: StorageCipher {
    private val ivSize = 16
    private val keySize = 16
    private val KEY_ALGORITHM = "AES"
    private val AES_PREFERENCES_KEY = "2f1cd779055f4212b9997520a948686f"
    private val SHARED_PREFERENCES_NAME = "FlutterKeyStore"
    private var cipher: Cipher? = null
    private var secureRandom: SecureRandom? = null
    private var secretKey: Key? = null
    private var rsaCipher: RSACipher18Implementation? = null

    constructor(context: Context, tag: String, authRequired: Boolean) {
        secureRandom = SecureRandom()
        rsaCipher = RSACipher18Implementation(context, tag, authRequired)
        val preferences =
            context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        val aesKey = preferences.getString(AES_PREFERENCES_KEY, null)
        cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        if (aesKey != null) {
            val encrypted: ByteArray
            try {
                encrypted = Base64.decode(aesKey, Base64.DEFAULT)
                secretKey = rsaCipher!!.unwrap(encrypted, KEY_ALGORITHM)
                return
            } catch (e: Exception) {
                Log.e("StorageCipher18Impl", "unwrap key failed", e)
            }
        }else {
            val key = ByteArray(keySize)
            secureRandom!!.nextBytes(key)
            secretKey = SecretKeySpec(key, KEY_ALGORITHM)
        }
        val encryptedKey = rsaCipher!!.wrap(secretKey)
        editor.putString(AES_PREFERENCES_KEY, Base64.encodeToString(encryptedKey, Base64.DEFAULT))
        editor.apply()
    }

    override fun encrypt(input: ByteArray): ByteArray {
        val iv = ByteArray(ivSize)
        secureRandom!!.nextBytes(iv)
        val ivParameterSpec = IvParameterSpec(iv)
        cipher!!.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
        val payload = cipher!!.doFinal(input)
        val combined = ByteArray(iv.size + payload.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(payload, 0, combined, iv.size, payload.size)
        return combined
    }

    override fun decrypt(input: ByteArray): ByteArray {
        val iv = ByteArray(ivSize)
        System.arraycopy(input, 0, iv, 0, iv.size)
        val ivParameterSpec = IvParameterSpec(iv)
        val payloadSize = input.size - ivSize
        val payload = ByteArray(payloadSize)
        System.arraycopy(input, iv.size, payload, 0, payloadSize)
        cipher!!.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
        return cipher!!.doFinal(payload)
    }
}