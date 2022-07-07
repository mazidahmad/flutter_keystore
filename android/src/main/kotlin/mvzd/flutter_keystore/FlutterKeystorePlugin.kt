package mvzd.flutter_keystore

import Core
import Core.Companion.decrypt
import Core.Companion.encrypt
import android.content.Context
import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** FlutterKeystorePlugin */
class FlutterKeystorePlugin: FlutterPlugin, MethodCallHandler {
  private lateinit var channel : MethodChannel
  private lateinit var context: Context

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_keystore")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    var message = call.argument<Object>("message")!!
    var tag = call.argument<String>("tag")!!
    var authRequired = call.argument<Boolean?>("authRequired")

    when(call.method){
      "getPlatformVersion" -> {
        result.success("Android ${android.os.Build.VERSION.RELEASE}")
      }
      "encrypt" -> {
        var encrypted = authRequired?.let {
          encrypt(context, tag,
            it, (message as String).toByteArray(Charsets.UTF_8))
        }
        result.success(encrypted)
      }
      "decrypt" -> {
        var decrypted = decrypt(context, message as ByteArray, tag)
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
