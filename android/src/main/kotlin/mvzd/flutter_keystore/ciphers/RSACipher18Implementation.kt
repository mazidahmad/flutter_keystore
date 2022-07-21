package mvzd.flutter_keystore.ciphers

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import java.math.BigInteger
import java.security.*
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.security.auth.x500.X500Principal

class RSACipher18Implementation {
    private val KEYSTORE_PROVIDER_ANDROID = "AndroidKeyStore"
    private val TYPE_RSA = "RSA"
    private var KEY_ALIAS: String? = null
    private var context: Context? = null
    private var cipher: Cipher

    constructor(context: Context, tag: String, rsaCipher: Cipher, authRequired: Boolean) {
        KEY_ALIAS = context.packageName + ".$tag"
        this.context = context
        this.cipher = rsaCipher
        checkAndGeneratePrivateKey(authRequired)
    }

    fun wrap(key: Key?): ByteArray? {
        val publicKey: PublicKey = getPublicKey()
//        val cipher = getRSACipher()
        cipher.init(Cipher.WRAP_MODE, publicKey)
        return cipher.wrap(key)
    }

    fun unwrap(wrappedKey: ByteArray?, algorithm: String?): Key? {
        val privateKey: PrivateKey = getPrivateKey()
//        val cipher = getRSACipher()
        cipher.init(Cipher.UNWRAP_MODE, privateKey)
        return cipher.unwrap(wrappedKey, algorithm, Cipher.SECRET_KEY)
    }

//    private fun getRSACipher(): Cipher? {
//        return Cipher.getInstance(
//            "RSA/ECB/PKCS1Padding",
//            "AndroidKeyStoreBCWorkaround"
//        )
//    }

    fun getInitializeUnwrapCipher(cipher: Cipher, authRequired: Boolean): Cipher{
        val privateKey: PrivateKey = getOrCreateSecretKey(authRequired)
        cipher.init(Cipher.UNWRAP_MODE, privateKey)
        return cipher
    }

    fun getInitializeWrapCipher(): Cipher{
        val publicKey: PublicKey = getPublicKey()
        cipher.init(Cipher.WRAP_MODE, publicKey)
        return cipher
    }

    private fun checkAndGeneratePrivateKey(authRequired: Boolean) {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID)
        ks.load(null)
        val privateKey = ks.getKey(KEY_ALIAS, null)
        if (privateKey == null) {
            try {
                createKeys(true, authRequired)
            } catch (_: Exception) {
                Log.d("CreateKey", "This device doesn't support StrongBox")
                createKeys(false, authRequired)
            }
        }
    }

    fun getSecretKey(): PrivateKey? {
        return try{
           getPrivateKey()
        }catch (_: Exception){
            null
        }
    }

    private fun getPrivateKey(): PrivateKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID)
        ks.load(null)
        val key = ks.getKey(KEY_ALIAS, null)
            ?: throw java.lang.Exception("No key found under alias: $KEY_ALIAS")
        val keyFactory: KeyFactory = KeyFactory.getInstance(
            key.algorithm,
            KEYSTORE_PROVIDER_ANDROID
        )
        var keyInfo: KeyInfo? = null
        keyInfo = keyFactory.getKeySpec(key, KeyInfo::class.java)
        val securityLevel: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            securityLevel = keyInfo!!.securityLevel
            Log.d("TAG", "Security Level : $securityLevel")
        } else {
            Log.d("TAG", "Hardware Security : " + keyInfo.isInsideSecureHardware)
        }
        if (key !is PrivateKey) {
            throw java.lang.Exception("Not an instance of a PrivateKey")
        }
        return key
    }

    fun getOrCreateSecretKey(authRequired: Boolean): PrivateKey {
        checkAndGeneratePrivateKey(authRequired)
        return getPrivateKey()
    }

    private fun getPublicKey(): PublicKey {
        val ks =
            KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID)
        ks.load(null)
        val cert = ks.getCertificate(KEY_ALIAS)
            ?: throw java.lang.Exception("No certificate found under alias: $KEY_ALIAS")
        return cert.publicKey ?: throw java.lang.Exception("No key found under alias: $KEY_ALIAS")
    }

    private fun createKeys(setIsStrongBox: Boolean, authRequired: Boolean) {
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
                KEY_ALIAS!!,
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
            )
                .setCertificateSubject(X500Principal("CN=$KEY_ALIAS"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSerialNumber(BigInteger.valueOf(1))
                .setCertificateNotBefore(start.time)
                .setCertificateNotAfter(end.time)
                .setUserAuthenticationRequired(authRequired)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(30, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            if (setIsStrongBox) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setIsStrongBoxBacked(true)
                }
            }
            spec = builder.build()
            kpGenerator.initialize(spec)
            kpGenerator.generateKeyPair()
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