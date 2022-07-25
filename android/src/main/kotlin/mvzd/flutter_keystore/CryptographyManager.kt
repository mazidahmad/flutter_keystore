package mvzd.flutter_keystore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import mvzd.flutter_keystore.model.Options
import java.math.BigInteger
import java.security.*
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal


/**
 * Copyright (C) 2020 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

interface CryptographyManager {

    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [ENCRYPT_MODE][Cipher.ENCRYPT_MODE] is used.
     */
    fun getInitializedCipherForEncryption(options: Options): Cipher

    /**
     * This method first gets or generates an instance of SecretKey and then initializes the Cipher
     * with the key. The secret key uses [DECRYPT_MODE][Cipher.DECRYPT_MODE] is used.
     */
    fun getInitializedCipherForDecryption(options: Options, initializationVector: ByteArray): Cipher

    /**
     * The Cipher created with [getInitializedCipherForEncryption] is used here
     */
    fun encryptData(input: ByteArray, cipher: Cipher, options: Options): ByteArray

    /**
     * The Cipher created with [getInitializedCipherForDecryption] is used here
     */
    fun decryptData(input: ByteArray, cipher: Cipher,  options: Options): ByteArray

    fun resetConfiguration(keyName: String)
}

fun CryptographyManager(context: Context): CryptographyManager = CryptographyManagerImpl(context)

private class CryptographyManagerImpl(context: Context) : CryptographyManager {


    private val KEYSTORE_PROVIDER_ANDROID = "AndroidKeyStore"
    private val TYPE_RSA = "RSA"
    private var context: Context? = context

    private val ivSize = 16
    private val keySize = 16
    private val KEY_ALGORITHM = "AES"
    private val AES_PREFERENCES_KEY = "2f1cd779055f4212b9997520a948686f"
    private val SHARED_PREFERENCES_NAME = "FlutterKeyStore"
    private var secureRandom: SecureRandom? = null

    init {
        secureRandom = SecureRandom()
    }

    fun wrap(key: Key?, keyName: String): ByteArray? {
        val publicKey: PublicKey = getPublicKey(keyName)
        val cipher = getRsaCipher()
        cipher.init(Cipher.WRAP_MODE, publicKey)
        return cipher.wrap(key)
    }

    fun unwrap(wrappedKey: ByteArray?, cipher: Cipher, algorithm: String?): Key? {
        return cipher.unwrap(wrappedKey, algorithm, Cipher.SECRET_KEY)
    }

    private fun getOrSaveAesKey(options: Options): ByteArray{
        val preferences =
            context?.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = preferences?.edit()
        getOrCreateSecretKey(options)
        val aesKey = preferences?.getString(AES_PREFERENCES_KEY, null)
        if (aesKey == null) {
            val key = ByteArray(keySize)
            secureRandom!!.nextBytes(key)
            val secretKey = SecretKeySpec(key, KEY_ALGORITHM)

            val encryptedKey = wrap(secretKey, options.tag)
            editor?.putString(AES_PREFERENCES_KEY, Base64.encodeToString(encryptedKey, Base64.DEFAULT))
            editor?.apply()
            return encryptedKey!!
        }

        return Base64.decode(aesKey, Base64.DEFAULT)
    }

    private fun getPublicKey(keyName: String): PublicKey {
        val ks =
            KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID)
        ks.load(null)
        val cert = ks.getCertificate(keyName)
            ?: throw java.lang.Exception("No certificate found under alias: $keyName")
        return cert.publicKey ?: throw java.lang.Exception("No key found under alias: $keyName")
    }

    override fun getInitializedCipherForEncryption(options: Options): Cipher {
        val cipher = getRsaCipher()
        val secretKey = getOrCreateSecretKey(options)
        cipher.init(Cipher.UNWRAP_MODE, secretKey)
        return cipher
    }

    override fun getInitializedCipherForDecryption(options: Options, initializationVector: ByteArray): Cipher {
        val cipher = getRsaCipher()
        val secretKey = getOrCreateSecretKey(options)
        cipher.init(Cipher.UNWRAP_MODE, secretKey)
        return cipher
    }

    override fun encryptData(input: ByteArray, cipher: Cipher, options: Options): ByteArray {
        val encryptedAes = getOrSaveAesKey(options)
        val secretKey = unwrap(encryptedAes, cipher, KEY_ALGORITHM)
        val iv = ByteArray(ivSize)
        secureRandom!!.nextBytes(iv)
        val ivParameterSpec = IvParameterSpec(iv)
        val aesCipher = getCipher()
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)
        val payload = aesCipher.doFinal(input)
        val combined = ByteArray(iv.size + payload.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(payload, 0, combined, iv.size, payload.size)
        return combined
    }

    override fun decryptData(input: ByteArray, cipher: Cipher, options: Options): ByteArray {
        val encryptedAes = getOrSaveAesKey(options)
        val secretKey = unwrap(encryptedAes, cipher, KEY_ALGORITHM)
        val iv = ByteArray(ivSize)
        System.arraycopy(input, 0, iv, 0, iv.size)
        val ivParameterSpec = IvParameterSpec(iv)
        val payloadSize = input.size - ivSize
        val payload = ByteArray(payloadSize)
        System.arraycopy(input, iv.size, payload, 0, payloadSize)
        val aesCipher = getCipher()
        aesCipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)
        return aesCipher.doFinal(payload)
    }

    override fun resetConfiguration(keyName: String) {
        val preferences =
            context?.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = preferences?.edit()
        editor?.remove(AES_PREFERENCES_KEY)
        editor?.apply()

        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID)
        ks.load(null)
        ks.deleteEntry(keyName)
    }

    private fun getRsaCipher(): Cipher {
        return Cipher.getInstance(
            "RSA/ECB/PKCS1Padding",
            "AndroidKeyStoreBCWorkaround"
        )
    }

    private fun getCipher(): Cipher {
        return Cipher.getInstance("AES/CBC/PKCS7Padding")
    }

    private fun getOrCreateSecretKey(options: Options): PrivateKey {
        // If Secretkey was previously created for that keyName, then grab and return it.
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID)
        ks.load(null)
        ks.getKey(options.tag, null)
            ?.let { return it as PrivateKey }

        // if you reach here, then a new SecretKey must be generated for that keyName
        return try {
            createKeys(true, options)
        }catch (e: Exception){
            Log.d("CreateKey", "This device doesn't support StrongBox")
            createKeys( false, options)
        }
    }

    private fun createKeys(setIsStrongBox: Boolean, options: Options): PrivateKey {
        val localeBeforeFakingEnglishLocale = Locale.getDefault()
        try {
            setLocale(Locale.ENGLISH)
            val start = Calendar.getInstance()
            val end = Calendar.getInstance()
            end.add(Calendar.YEAR, 25)
            val kpGenerator: KeyPairGenerator = KeyPairGenerator.getInstance(
                TYPE_RSA,
                KEYSTORE_PROVIDER_ANDROID
            )
            val spec: AlgorithmParameterSpec

            val builder = KeyGenParameterSpec.Builder(
                options.tag,
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
            )
                .setCertificateSubject(X500Principal("CN=${options.tag}"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSerialNumber(BigInteger.valueOf(1))
                .setCertificateNotBefore(start.time)
                .setCertificateNotAfter(end.time)
                .setUserAuthenticationRequired(options.authRequired)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(options.authRequired)
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q){
                builder.setUserAuthenticationValidityDurationSeconds(options.authValidityDuration)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(options.authValidityDuration, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            }

            if (setIsStrongBox) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setIsStrongBoxBacked(true)
                }
            }
            spec = builder.build()
            kpGenerator.initialize(spec)
            val keyPair = kpGenerator.generateKeyPair()

            return keyPair.private
        } finally {
            setLocale(localeBeforeFakingEnglishLocale)
        }
    }

    /**
     * Sets default locale.
     */
    private fun setLocale(locale: Locale) {
        Locale.setDefault(locale)
        val config = context!!.resources.configuration
        config.setLocale(locale)
        context!!.createConfigurationContext(config)
    }
}