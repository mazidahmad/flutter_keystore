import 'dart:typed_data';

export 'package:flutter_keystore/src/model/models.dart';

import 'package:flutter/services.dart';
import 'package:flutter_keystore/src/flutter_keystore_platform_interface.dart';
import 'package:flutter_keystore/src/model/android_options.dart';

class FlutterKeystore {
  Future<Uint8List?> encrypt(
      {required AndroidOptions options, required String message}) async {
    try {
      return await FlutterKeystorePlatform.instance
          .encrypt(options: options, message: message);
    } on PlatformException catch (e) {
      if (e.message!.contains("CanAuthenticateResponse")) {
        throw Exception(e.message);
      }
      rethrow;
    } catch (e) {
      rethrow;
    }
  }

  Future<String?> decrypt(
      {required Uint8List message, required AndroidOptions options}) async {
    try {
      return await FlutterKeystorePlatform.instance
          .decrypt(message: message, options: options);
    } on PlatformException catch (e) {
      if (e.message!.contains("CanAuthenticateResponse")) {
        throw Exception(e.message);
      }
      rethrow;
    } catch (e) {
      rethrow;
    }
  }

  Future<void> resetConfiguration({required AndroidOptions options}) async {
    return await FlutterKeystorePlatform.instance
        .resetConfiguration(options: options);
  }
}
