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
import mvzd.flutter_keystore.ciphers.StorageCipher18Implementation


class MethodCallException(
  val errorCode: String,
  val errorMessage: String?,
  val errorDetails: Any? = null
) : Exception(errorMessage ?: errorCode)


/** FlutterKeystorePlugin */
class FlutterKeystorePlugin: FlutterPlugin, ActivityAware, MethodCallHandler {
  private val TAG : String = "MethodChanel"
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private lateinit var storageCipher: StorageCipher18Implementation

  private lateinit var cryptographyManager: CryptographyManager
  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var promptInfo: BiometricPrompt.PromptInfo
  private var message: String = ""
  private var data: ByteArray? = null
  private var fragmentActivity: FragmentActivity? = null


  private fun initInstance(context: Context, tag: String){
    if (!::storageCipher.isInitialized){
      storageCipher = StorageCipher18Implementation(context, tag)
    }
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_keystore")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    cryptographyManager = CryptographyManager()
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    val message = call.argument<Any>("message")!!
    val tag = call.argument<String>("tag")!!
    val authRequired = call.argument<Boolean>("authRequired")
    val info = call.argument<Map<String, Any>?>("androidPromptInfo")

    initInstance(context, tag)

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

    if (message is ByteArray){
      this.data = message
    }else{
      this.message = message as String
    }

    fun <T> requiredArgument(name: String) =
      call.argument<T>(name) ?: throw MethodCallException(
        "MissingArgument",
        "Missing required argument '$name'"
      )

    when(call.method){
      "encrypt" -> {
          this.data = storageCipher.encrypt((this.message as String).toByteArray(Charsets.UTF_8))
          result.success(this.data)
      }
      "decrypt" -> {
        if(authRequired == true) {
          biometricPrompt = createBiometricPrompt(fragmentActivity!!){
            this.data = storageCipher.decrypt(this.data!!)
            this.message = String(this.data!!)
            result.success(this.message)
          }
          authenticateToDecrypt(context)
        }else{
          this.data = storageCipher.decrypt(this.data!!)
          this.message = String(this.data!!)
          result.success(this.message)
        }
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  private fun createBiometricPrompt(context: FragmentActivity, onSuccess: () -> Unit): BiometricPrompt {
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
        onSuccess()
      }
    }

    return BiometricPrompt(context, executor, callback)
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

  private fun authenticateToEncrypt(context: Context, tag: String) {
    if (BiometricManager.from(context).canAuthenticate() == BiometricManager
        .BIOMETRIC_SUCCESS) {
      biometricPrompt.authenticate(promptInfo)
    }
  }

  private fun authenticateToDecrypt(context: Context) {
    if (BiometricManager.from(context).canAuthenticate() == BiometricManager
        .BIOMETRIC_SUCCESS) {
//      val cipher = cryptographyManager.getInitializedCipherForDecryption(tag,data)
//      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
      biometricPrompt.authenticate(promptInfo)
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

