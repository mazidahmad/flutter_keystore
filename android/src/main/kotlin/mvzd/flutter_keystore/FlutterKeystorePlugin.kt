package mvzd.flutter_keystore

import android.content.Context
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import mvzd.flutter_keystore.ciphers.StorageCipher18Implementation

/** FlutterKeystorePlugin */
class FlutterKeystorePlugin: FlutterPlugin, MethodCallHandler {
  private lateinit var channel : MethodChannel
  private lateinit var context: Context
  private lateinit var storageCipher: StorageCipher18Implementation


  fun initInstance(context: Context, tag: String){
    if (!::storageCipher.isInitialized){
      storageCipher = StorageCipher18Implementation(context, tag)
    }
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_keystore")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    val message = call.argument<Any>("message")!!
    val tag = call.argument<String>("tag")!!
    val authRequired = call.argument<Boolean?>("authRequired")

    initInstance(context, tag)

    when(call.method){
      "encrypt" -> {
        val encrypted = authRequired?.let {
          storageCipher.encrypt((message as String).toByteArray(Charsets.UTF_8))
        }
        result.success(encrypted)
      }
      "decrypt" -> {
        val decrypted = storageCipher.decrypt(message as ByteArray)
        result.success(String(decrypted))
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
}
