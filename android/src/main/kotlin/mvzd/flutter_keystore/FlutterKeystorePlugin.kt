package mvzd.flutter_keystore

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

private fun Throwable.toCompleteString(): String {
  val out = StringWriter().let { out ->
    printStackTrace(PrintWriter(out))
    out.toString()
  }
  return "$this\n$out"
}


/** FlutterKeystorePlugin */
class FlutterKeystorePlugin: FlutterPlugin, ActivityAware, MethodCallHandler {
  private val TAG : String = "MethodChanel"
  private lateinit var channel : MethodChannel
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
      val tag = call.argument<String>("tag")!!
      val authRequired = call.argument<Boolean?>("authRequired")
      val info = call.argument<Map<String, Any>?>("androidPromptInfo")


      if (info != null) {
        promptInfo = createPromptInfo(info)
      }else{
        promptInfo = createPromptInfo(mapOf<String, Any>(
          "title" to "Authenticate",
          "subtitle" to "Biometric authentication to app",
          "description" to "Validate fingerprint to continue",
          "confirmationRequired" to false,
          "negativeButton" to "Cancel"
        ))
      }

      if (message != null){
        if (message is ByteArray){
          this.data = message
        }else{
          this.message = message as String
        }
      }

      when(call.method){
        "encrypt" -> {
          if (authRequired!! && cipher == null) {
            authenticateToEncrypt(tag, authRequired) {
              this.cipher = it?.cipher
              val encryptedData = cryptographyManager.encryptData((this.message).toByteArray(Charsets.UTF_8), this.cipher!!, tag, authRequired)
              this.data = encryptedData
              result.success(this.data)
            }
          }else{
            if (this.cipher == null){
              this.cipher = cryptographyManager.getInitializedCipherForEncryption(tag, authRequired)
            }
            val encryptedData = cryptographyManager.encryptData((this.message).toByteArray(Charsets.UTF_8), this.cipher!!, tag, authRequired)
            this.data = encryptedData
            result.success(this.data)
          }
        }
        "decrypt" -> {
          if(authRequired!! && cipher == null){
            authenticateToDecrypt(tag, authRequired, this.data!!) {
              this.cipher = it?.cipher
              val resultData = cryptographyManager.decryptData(this.data!!, this.cipher!!, tag, authRequired)
              result.success(String(resultData))
            }
          }else{
            if (this.cipher == null){
              this.cipher = cryptographyManager.getInitializedCipherForEncryption(tag, authRequired)
            }
            val resultData = cryptographyManager.decryptData(this.data!!, this.cipher!!, tag, authRequired)
            result.success(String(resultData))
          }
        }
        "resetConfiguration" -> {
          cryptographyManager.resetConfiguration(tag)
          this.cipher = null
          result.success(true)
        }
        else -> {
          result.notImplemented()
        }
      }
    }catch (e: MethodCallException) {
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
    return BiometricPrompt.PromptInfo.Builder()
      .setTitle(info["title"] as String)
      .setSubtitle(info["subtitle"] as String?)
      .setDescription(info["description"] as String?)
      .setConfirmationRequired(info["confirmationRequired"] as Boolean)
      .setNegativeButtonText(info["negativeButton"] as String)
      .build()
  }

  private fun authenticateToEncrypt(tag: String,authRequired: Boolean, onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit) {
    if (BiometricManager.from(context).canAuthenticate() == BiometricManager
        .BIOMETRIC_SUCCESS) {
      val cipher = cryptographyManager.getInitializedCipherForEncryption(tag, authRequired)
      val biometricPrompt = createBiometricPrompt(onSuccess)
      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
  }

  private fun authenticateToDecrypt(tag: String, authRequired: Boolean, data: ByteArray, onSuccess: (cryptoObject: BiometricPrompt.CryptoObject?) -> Unit) {
    if (BiometricManager.from(context).canAuthenticate() == BiometricManager
        .BIOMETRIC_SUCCESS) {
      val cipher = cryptographyManager.getInitializedCipherForDecryption(tag,data, authRequired)
      val biometricPrompt = createBiometricPrompt(onSuccess)
      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    val activity = binding.activity
    if (activity !is FragmentActivity) {
      Log.d("AttachedToActivity", "Got attached to activity which is not a FragmentActivity: $activity" )
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

