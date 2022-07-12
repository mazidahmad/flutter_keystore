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
import java.nio.charset.Charset

/** FlutterKeystorePlugin */
class FlutterKeystorePlugin: FlutterPlugin, ActivityAware, MethodCallHandler {
  private val TAG : String = "MethodChanel"
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private lateinit var storageCipher: StorageCipher18Implementation

//  private var attachedActivity: FragmentActivity? = null
//  private val biometricManager by lazy { BiometricManager.from(context) }

  private lateinit var cryptographyManager: CryptographyManager
  private var readyToEncrypt: Boolean = false
  private lateinit var biometricPrompt: BiometricPrompt
  private lateinit var promptInfo: BiometricPrompt.PromptInfo
  private var message: String = ""
  private var data: ByteArray? = null
  private var mode: CipherMode = CipherMode.ENCRYPT
  private var fragmentActivity: FragmentActivity? = null


  fun initInstance(context: Context, tag: String){
    if (!::storageCipher.isInitialized){
      storageCipher = StorageCipher18Implementation(context, tag)
    }
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_keystore")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    cryptographyManager = CryptographyManager()
    promptInfo = createPromptInfo()
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    val message = call.argument<Any>("message")!!
    val tag = call.argument<String>("tag")!!
    val authRequired = call.argument<Boolean?>("authRequired")

    if (message is ByteArray){
      this.data = message
    }else{
      this.message = message as String
    }

    initInstance(context, tag)

    fun <T> requiredArgument(name: String) =
      call.argument<T>(name) ?: throw MethodCallException(
        "MissingArgument",
        "Missing required argument '$name'"
      )

    val getAndroidPromptInfo = {
      requiredArgument<Map<String, Any>>("PARAM_ANDROID_PROMPT_INFO").let {
        AndroidPromptInfo(
//          title = it["title"] as String,
//          subtitle = it["subtitle"] as String?,
//          description = it["description"] as String?,
//          negativeButton = it["negativeButton"] as String,
//          confirmationRequired = it["confirmationRequired"] as Boolean,
          title = "Biometric Validate",
          subtitle = "Biometric for app",
          description = "Confirm biometric to continue",
          negativeButton = "Use password or passcode",
          confirmationRequired = false,
        )
      }
    }

    when(call.method){
      "encrypt" -> {
//        val encrypted = authRequired?.let {
//          storageCipher.encrypt((message as String).toByteArray(Charsets.UTF_8))
        mode = CipherMode.ENCRYPT
//        if(authRequired == true) {
//          biometricPrompt = createBiometricPrompt(fragmentActivity!!, result)
//          authenticateToEncrypt(context, tag)
////          result.success(this.data!!)
//        }else{
          this.data = storageCipher.encrypt((this.message as String).toByteArray(Charsets.UTF_8))
          result.success(this.data)
//        }
      }
      "decrypt" -> {
//        val decrypted = storageCipher.decrypt(message as ByteArray)
//        result.success(String(decrypted))
        mode = CipherMode.DECRYPT
        if(authRequired == true) {
          biometricPrompt = createBiometricPrompt(fragmentActivity!!, result)
          authenticateToDecrypt(context,tag, this.data!!)
        }else{
          this.data = storageCipher.decrypt(this.data!!)
          this.message = String(this.data!!)
          result.success(this.message)
        }
//        result.success(this.message)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  private fun createBiometricPrompt(context: FragmentActivity, resultCall: Result): BiometricPrompt {
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
        processData(result.cryptoObject, resultCall)
      }
    }

    return BiometricPrompt(context, executor, callback)
  }

  private fun createPromptInfo(): BiometricPrompt.PromptInfo {
    return BiometricPrompt.PromptInfo.Builder()
      .setTitle("Confirm Biometric") // e.g. "Sign in"
      .setSubtitle("Biometric for app") // e.g. "Biometric for My App"
      .setDescription("Confirm biometric to continue") // e.g. "Confirm biometric to continue"
      .setConfirmationRequired(false)
      .setNegativeButtonText("User Account Password") // e.g. "Use Account Password"
      // .setDeviceCredentialAllowed(true) // Allow PIN/pattern/password authentication.
      // Also note that setDeviceCredentialAllowed and setNegativeButtonText are
      // incompatible so that if you uncomment one you must comment out the other
      .build()
  }

  private fun processData(cryptoObject: BiometricPrompt.CryptoObject?, result: Result) {
    if (mode == CipherMode.ENCRYPT) {
//      val encryptedData = cryptographyManager.encryptData(message, cryptoObject?.cipher!!)
//      encryptedData.ciphertext
//      this.data = encryptedData.initializationVector
      this.data = storageCipher.encrypt((this.message as String).toByteArray(Charsets.UTF_8))
      result.success(this.data)
    } else {
//      this.message = cryptographyManager.decryptData(data!!, cryptoObject?.cipher!!)
      this.data = storageCipher.decrypt(this.data!!)
      this.message = String(this.data!!)
      result.success(this.message)
    }
  }

  private fun authenticateToEncrypt(context: Context, tag: String) {
    if (BiometricManager.from(context).canAuthenticate() == BiometricManager
        .BIOMETRIC_SUCCESS) {
//      val cipher = cryptographyManager.getInitializedCipherForEncryption(tag)
//      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
      biometricPrompt.authenticate(promptInfo)
    }
  }

  private fun authenticateToDecrypt(context: Context, tag: String, data: ByteArray) {
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

enum class CipherMode {
  ENCRYPT, DECRYPT
}

data class AndroidPromptInfo(
  val title: String,
  val subtitle: String?,
  val description: String?,
  val negativeButton: String,
  val confirmationRequired: Boolean
)

class MethodCallException(
  val errorCode: String,
  val errorMessage: String?,
  val errorDetails: Any? = null
) : Exception(errorMessage ?: errorCode)

