import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_keystore/src/model/access_control.dart';

import 'flutter_keystore_platform_interface.dart';

/// An implementation of [FlutterKeystorePlatform] that uses method channels.
class MethodChannelFlutterKeystore extends FlutterKeystorePlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_keystore');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<Uint8List?> encrypt({required AccessControl accessControl, required String message}) async{
    final result = await methodChannel.invokeMethod<Uint8List?>('encrypt', {
      'tag': accessControl.tag,
      'message': message,
      'authRequired': accessControl.setUserAuthenticatedRequired
    });
    return result;
  }
  
  @override
  Future<String?> decrypt({required Uint8List message, required AccessControl accessControl}) async{
    final result = await methodChannel.invokeMethod<String?>('decrypt', {
      'message': message,
      'tag': accessControl.tag
    });
    return result;
  }
}
