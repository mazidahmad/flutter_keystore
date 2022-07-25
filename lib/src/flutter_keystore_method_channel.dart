import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_keystore/src/model/android_options.dart';

import 'flutter_keystore_platform_interface.dart';

/// An implementation of [FlutterKeystorePlatform] that uses method channels.
class MethodChannelFlutterKeystore extends FlutterKeystorePlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_keystore');

  @override
  Future<Uint8List?> encrypt(
      {required AndroidOptions options, required String message}) async {
    final result = await methodChannel.invokeMethod<Uint8List?>(
        'encrypt', {'message': message, 'options': options.toMap()});
    return result;
  }

  @override
  Future<String?> decrypt(
      {required Uint8List message, required AndroidOptions options}) async {
    final result = await methodChannel.invokeMethod<String?>(
        'decrypt', {'message': message, 'options': options.toMap()});
    return result;
  }

  @override
  Future<void> resetConfiguration({required AndroidOptions options}) async {
    await methodChannel
        .invokeMethod<bool>('resetConfiguration', {'options': options.toMap()});
  }
}
