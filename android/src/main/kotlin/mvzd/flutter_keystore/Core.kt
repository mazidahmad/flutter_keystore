import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.View

import mvzd.flutter_keystore.PreferencesHelper
import mvzd.flutter_keystore.ciphers.StorageCipher18Implementation
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

class Core {
    companion object {
        private const val KEYSTORE_PROVIDER_ANDROID = "AndroidKeyStore"

        private fun getSecretKey(tag: String): SecretKey? {
            return try{
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID)

                keyStore.load(null)
                keyStore.getKey(tag, null) as SecretKey
            }catch (_: Exception){
                null
            }
        }

        private fun prepareSecretKey(tag: String, authRequired: Boolean): SecretKey {
            try {
                var key = getSecretKey(tag)
                if (key == null){
//                    makeAndStorePrivateKey(tag, authRequired)
//                    generateSecretKey(tag, authRequired)
//                    generateECKeyPair(tag, authRequired)
                    key = getSecretKey(tag)
                }
                return key!!
            }catch (_: Exception){
                throw Exception("Failed to prepare secret key")
            }
        }

        private fun makeAndStorePrivateKey(tag: String, authRequired: Boolean) {
            val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER_ANDROID)
            val keyGenParameterSpec =
                KeyGenParameterSpec.Builder(
                    tag,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setUserAuthenticationRequired(authRequired) // 2
//                    .setUserAuthenticationValidityDurationSeconds(120) //3
                    .build()
                kpg.initialize(keyGenParameterSpec)
                val kp = kpg.generateKeyPair()
        }

        private fun generateECKeyPair(tag: String, authRequired: Boolean) {
            val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER_ANDROID)
            val keyGenParameterSpec =
                KeyGenParameterSpec.Builder(
                    tag,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setUserAuthenticationRequired(authRequired) // 2
//                    .setUserAuthenticationValidityDurationSeconds(120) //3
                    .build()
            kpg.initialize(keyGenParameterSpec)
            val kp = kpg.generateKeyPair()
        }

        private fun getCipher(): Cipher {
            return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_GCM + "/"
                    + KeyProperties.ENCRYPTION_PADDING_NONE)
        }

        private fun generateSecretKey(tag: String, authRequired: Boolean = true) {
            val keyGenParameterSpec =
                KeyGenParameterSpec.Builder(
                    tag,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM) // 1
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(authRequired) // 2
//                    .setUserAuthenticationValidityDurationSeconds(120) //3
                    .build()
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER_ANDROID) // 4
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }

//        fun encrypt(context: Context, tag: String, authRequired: Boolean = true, data: ByteArray): ByteArray {
//            val secretKey = prepareSecretKey(tag, authRequired)
//            val cipher = getCipher()
//            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
//            val ivParameters = cipher.parameters.getParameterSpec(GCMParameterSpec::class.java)
//            val iv = ivParameters.iv
//            PreferencesHelper.saveIV(context, iv)
//            return cipher.doFinal(data)
//        }
//
//        fun decrypt(context: Context, data: ByteArray, key: String): ByteArray {
//            val cipher = getCipher()
//            val secretKey = getSecretKey(key)
//            val info = SecretKeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER_ANDROID)
//                .getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
//            val securityLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                Log.d("CORE", "Security Level : " + info.securityLevel)
//            } else {
//                TODO("VERSION.SDK_INT < S")
//            }
//            val iv = PreferencesHelper.iv(context)
//            val ivParameters = GCMParameterSpec(128, iv)
//            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameters)
//            return cipher.doFinal(data)
//        }
    }
}