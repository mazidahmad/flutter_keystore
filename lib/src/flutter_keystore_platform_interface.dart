import 'dart:typed_data';

import 'package:flutter_keystore/src/model/access_control.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_keystore_method_channel.dart';

abstract class FlutterKeystorePlatform extends PlatformInterface {
  /// Constructs a FlutterKeystorePlatform.
  FlutterKeystorePlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterKeystorePlatform _instance = MethodChannelFlutterKeystore();

  /// The default instance of [FlutterKeystorePlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterKeystore].
  static FlutterKeystorePlatform get instance => _instance;
  
  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterKeystorePlatform] when
  /// they register themselves.
  static set instance(FlutterKeystorePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<Uint8List?> encrypt({required AccessControl accessControl, required String message});
  Future<String?> decrypt({required Uint8List message, required AccessControl accessControl});
  // Future<String?> getPublicKey();
}
