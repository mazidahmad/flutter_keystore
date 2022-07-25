package mvzd.flutter_keystore

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.NonNull
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.flutter.Log

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import mu.KotlinLogging
import mvzd.flutter_keystore.model.Options
import java.io.PrintWriter
import java.io.StringWriter
import javax.crypto.Cipher

private val logger = KotlinLogging.logger {}

typealias ErrorCallback = (errorInfo: AuthenticationErrorInfo) -> Unit

class MethodCallException(
    val errorCode: String,
    val errorMessage: String?,
    val errorDetails: Any? = null
) : Exception(errorMessage ?: errorCode)

data class AuthenticationErrorInfo(
    val error: AuthenticationError,
    val message: CharSequence,
    val errorDetails: String? = null
) {
    constructor(
        error: AuthenticationError,
        message: CharSequence,
        e: Throwable
    ) : this(error, message, e.toCompleteString())
}

@SuppressLint("RestrictedApi")
@Suppress("unused")
enum class AuthenticationError(vararg val code: Int) {
    Canceled(BiometricPrompt.ERROR_CANCELED),
    Timeout(BiometricPrompt.ERROR_TIMEOUT),
    UserCanceled(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON),
    Unknown(-1),

    /** Authentication valid, but unknown */
    Failed(-2),
    ;

    companion object {
        fun forCode(code: Int) =
            values().firstOrNull { it.code.contains(code) } ?: Unknown
    }
}

@Suppress("unused")
enum class CanAuthenticateResponse(val code: Int) {
    Success(BiometricManager.BIOMETRIC_SUCCESS),
    ErrorHwUnavailable(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE),
    ErrorNoBiometricEnrolled(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
    ErrorNoHardware(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE),
    ;

    override fun toString(): String {
        return "CanAuthenticateResponse.${name}"
    }
}

private fun Throwable.toCompleteString(): String {
    val out = StringWriter().let { out ->
        printStackTrace(PrintWriter(out))
        out.toString()
    }
    return "$this\n$out"
}


/** FlutterKeystorePlugin */
class FlutterKeystorePlugin : FlutterPlugin, ActivityAware, MethodCallHandler {
    private val TAG: String = "MethodChanel"
    private lateinit var channel: MethodChannel
    private lateinit var context: Context

    private lateinit var cryptographyManager: CryptographyManager
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var message: String = ""
    private var data: ByteArray? = null
    private var fragmentActivity: FragmentActivity? = null
    private var cipher: Cipher? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_keystore")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        cryptographyManager = CryptographyManager(context)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        TODO("Not yet implemented")
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        try {
            val resultError: ErrorCallback = { errorInfo ->
                result.error(
                    "AuthError:${errorInfo.error}",
                    errorInfo.message.toString(),
                    errorInfo.errorDetails
                )
                logger.error("AuthError: $errorInfo")

            }

            fun <T> requiredArgument(name: String) =
                call.argument<T>(name) ?: throw MethodCallException(
                    "MissingArgument",
                    "Missing required argument '$name'"
                )

            val message = call.argument<Any>("message")
            val optionsParam = call.argument<Map<String, Any>?>("options")

            val info = optionsParam?.get("androidPromptInfo") as Map<String, Any>?


            if (info != null) {
                promptInfo = createPromptInfo(info)
            } else {
                promptInfo = createPromptInfo(
                    mapOf<String, Any>(
                        "title" to "Authenticate",
                        "subtitle" to "Biometric authentication to app",
                        "description" to "Validate fingerprint to continue",
                        "confirmationRequired" to false,
                        "negativeButton" to "Cancel"
                    )
                )
            }

            val options = Options(
                authRequired = (optionsParam?.get("authRequired") ?: false) as Boolean,
                tag = optionsParam?.get("tag")!! as String,
                oncePrompt = (optionsParam["oncePrompt"] ?: false) as Boolean,
                promptInfo = promptInfo,
                authValidityDuration = optionsParam["authValidityDuration"] as Int
            )

            if (message != null) {
                if (message is ByteArray) {
                    this.data = message
                } else {
                    this.message = message as String
                }
            }

            when (call.method) {
                "encrypt" -> {
                    if (options.authRequired && cipher == null) {
                        try {
                            val encryptedData = cryptographyManager.encryptData(
                                    (this.message).toByteArray(Charsets.UTF_8),
                                    this.cipher,
                                    options
                            )
                            this.data = encryptedData
                            result.success(this.data)
                        }catch (e: Exception){
                            authenticateToEncrypt(options) {
                                this.cipher = it?.cipher
                                val encryptedData = cryptographyManager.encryptData(
                                        (this.message).toByteArray(Charsets.UTF_8),
                                        this.cipher,
                                        options
                                )
                                this.data = encryptedData
                                result.success(this.data)

                            }
                        }
                    } else {
                        if (this.cipher == null) {
                            this.cipher = cryptographyManager.getInitializedCipherForEncryption(
                                options
                            )
                        }
                        val encryptedData = cryptographyManager.encryptData(
                            (this.message).toByteArray(Charsets.UTF_8),
                            this.cipher!!,
                            options
                        )
                        this.data = encryptedData
                        result.success(this.data)
                    }
                }
                "decrypt" -> {
                    if (options.authRequired && cipher == null) {
                        try {
                            if (!options.oncePrompt){
                                val resultData = cryptographyManager.decryptData(
                                        this.data!!,
                                        this.cipher!!,
                                        options
                                )
                                result.success(String(resultData))
                            }else{
                                authenticateToDecrypt(options, this.data!!) {
                                    this.cipher = it?.cipher
                                    val resultData = cryptographyManager.decryptData(
                                            this.data!!,
                                            this.cipher,
                                            options
                                    )
                                    result.success(String(resultData))
                                }
                            }
                        }catch (e: Exception){
                            authenticateToDecrypt(options, this.data!!) {
                                this.cipher = it?.cipher
                                val resultData = cryptographyManager.decryptData(
                                        this.data!!,
                                        this.cipher,
                                        options
                                )
                                result.success(String(resultData))
                            }
                        }
                    } else {
                        if (this.cipher == null) {
                            this.cipher = cryptographyManager.getInitializedCipherForEncryption(
                                options
                            )
                        }
                        val resultData = cryptographyManager.decryptData(
                            this.data!!,
                            this.cipher!!,
                            options
                        )
                        result.success(String(resultData))
                    }
                }
                "resetConfiguration" -> {
                    cryptographyManager.resetConfiguration(options.tag)
                    this.cipher = null
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        } catch (e: MethodCallException) {
            logger.error(e) { "Error while processing method call ${call.method}" }
            result.error(e.errorCode, e.errorMessage, e.errorDetails)
        } catch (e: Exception) {
            logger.error(e) { "Error while processing method call '${call.method}'" }
            result.error("Unexpected Error", e.message, e.toCompleteString())
        }
    }

    private fun createBiometricPrompt(onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(TAG, "$errorCode :: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Authentication failed for an unknown reason")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Authentication was successful")
                onSuccess(result.cryptoObject)
            }
        }

        //The API requires the client/Activity context for displaying the prompt view
        return BiometricPrompt(fragmentActivity!!, executor, callback)
    }

    private fun createPromptInfo(info: Map<String, Any>): BiometricPrompt.PromptInfo {
        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(info["title"] as String)
            .setSubtitle(info["subtitle"] as String?)
            .setDescription(info["description"] as String?)
            .setConfirmationRequired(info["confirmationRequired"] as Boolean)
            .setNegativeButtonText(info["negativeButton"] as String)
        return promptBuilder.build()
    }

    private fun authenticateToEncrypt(
        options: Options,
        onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit
    ) {
        if (BiometricManager.from(context).canAuthenticate() == BiometricManager
                .BIOMETRIC_SUCCESS
        ) {

            val biometricPrompt = createBiometricPrompt(onSuccess)
            biometricPrompt.authenticate(options.promptInfo)
        }
    }

    private fun authenticateToDecrypt(
        options: Options,
        data: ByteArray,
        onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit
    ) {
        if (BiometricManager.from(context).canAuthenticate() == BiometricManager
                .BIOMETRIC_SUCCESS
        ) {
            val biometricPrompt = createBiometricPrompt(onSuccess)
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun canAuthenticate(): Boolean {
        val response = BiometricManager.from(context).canAuthenticate()
//    if (response != BiometricManager.BIOMETRIC_SUCCESS) throw Exception(
//      CanAuthenticateResponse.values().filter { it.code == response }.toString()
//    )
        if (response != BiometricManager.BIOMETRIC_SUCCESS) return false
        return true
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        val activity = binding.activity
        if (activity !is FragmentActivity) {
            Log.d(
                "AttachedToActivity",
                "Got attached to activity which is not a FragmentActivity: $activity"
            )
            return
        }
        fragmentActivity = activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivity() {
    }
}

