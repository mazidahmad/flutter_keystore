package mvzd.flutter_keystore

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.NonNull
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
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
import mvzd.flutter_keystore.ciphers.StorageCipher18Implementation
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
  private lateinit var storageCipher: StorageCipher18Implementation

  private lateinit var cryptographyManager: CryptographyManager
  private lateinit var promptInfo: BiometricPrompt.PromptInfo
  private var message: String = ""
  private var data: ByteArray? = null
  private var fragmentActivity: FragmentActivity? = null

  private val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
  private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }


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
    try {
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

      val resultError: ErrorCallback = { errorInfo ->
        result.error(
          "AuthError:${errorInfo.error}",
          errorInfo.message.toString(),
          errorInfo.errorDetails
        )
        logger.error("AuthError: $errorInfo")

      }

      @UiThread
      fun withAuth(
        @WorkerThread cb: () -> Unit
      ) {
        authenticate(promptInfo, {
          cb()
        },onError = resultError)
      }

      when(call.method){
        "encrypt" -> {
          this.data = storageCipher.encrypt((this.message as String).toByteArray(Charsets.UTF_8))
          result.success(this.data)
        }
        "decrypt" -> {
          if(authRequired == true) {
            withAuth {
              this.data = storageCipher.decrypt(this.data!!)
              this.message = String(this.data!!)
              result.success(this.message)
            }
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
    }catch (e: MethodCallException) {
      logger.error(e) { "Error while processing method call ${call.method}" }
      result.error(e.errorCode, e.errorMessage, e.errorDetails)
    } catch (e: Exception) {
      logger.error(e) { "Error while processing method call '${call.method}'" }
      result.error("Unexpected Error", e.message, e.toCompleteString())
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  private fun createBiometricPrompt(context: FragmentActivity, onSuccess: () -> Unit,
                                    onError: ErrorCallback): BiometricPrompt {
    val executor = ContextCompat.getMainExecutor(context)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        logger.trace("onAuthenticationError($errorCode, $errString)")
        ui(onError) {
          onError(
            AuthenticationErrorInfo(
              AuthenticationError.forCode(
                errorCode
              ), errString
            )
          )
        }
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        Log.d(TAG, "Authentication failed for an unknown reason")
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        Log.d(TAG, "Authentication was successful")
        try {
          onSuccess()
        }catch (e: Exception){
          ui(onError) {
            onError(
              AuthenticationErrorInfo(
                AuthenticationError.forCode(
                  0
                ), e.message!!
              )
            )
          }
        }
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

  @AnyThread
  private inline fun ui(
    @UiThread crossinline onError: ErrorCallback,
    @UiThread crossinline cb: () -> Unit
  ) = handler.post {
    try {
      cb()
    } catch (e: Throwable) {
      logger.error(e) { "Error while calling UI callback. This must not happen." }
      onError(
        AuthenticationErrorInfo(
          AuthenticationError.Unknown,
          "Unexpected authentication error. ${e.localizedMessage}",
          e
        )
      )
    }
  }

  private inline fun worker(
    @UiThread crossinline onError: ErrorCallback,
    @WorkerThread crossinline cb: () -> Unit
  ) = executor.submit {
    try {
      cb()
    } catch (e: Throwable) {
      logger.error(e) { "Error while calling worker callback. This must not happen." }
      handler.post {
        onError(
          AuthenticationErrorInfo(
            AuthenticationError.Unknown,
            "Unexpected authentication error. ${e.localizedMessage}",
            e
          )
        )
      }
    }
  }

  @UiThread
  private fun authenticate(
    promptInfo: BiometricPrompt.PromptInfo,
    @WorkerThread onSuccess: () -> Unit,
    onError: ErrorCallback
  ) {
    logger.trace("authenticate()")
    val activity = fragmentActivity ?: return run {
      logger.error { "We are not attached to an activity." }
      onError(
        AuthenticationErrorInfo(
          AuthenticationError.Failed,
          "Plugin not attached to any activity."
        )
      )
    }

    val prompt = createBiometricPrompt(activity, onSuccess, onError)
    prompt.authenticate(promptInfo)
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

