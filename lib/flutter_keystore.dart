
import 'dart:typed_data';

export 'package:flutter_keystore/src/model/models.dart';

import 'package:flutter_keystore/src/flutter_keystore_platform_interface.dart';
import 'package:flutter_keystore/src/model/access_control.dart';

class FlutterKeystore {
  Future<Uint8List?> encrypt({required AccessControl accessControl, required String message}) {
    return FlutterKeystorePlatform.instance.encrypt(accessControl : accessControl, message: message);
  }

  Future<String?> decrypt({required Uint8List message, required AccessControl accessControl}) {
    return FlutterKeystorePlatform.instance.decrypt(message: message, accessControl: accessControl);
  }
}
