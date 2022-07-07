import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_keystore/flutter_keystore.dart';
import 'package:flutter_keystore/flutter_keystore_platform_interface.dart';
import 'package:flutter_keystore/flutter_keystore_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterKeystorePlatform 
    with MockPlatformInterfaceMixin
    implements FlutterKeystorePlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FlutterKeystorePlatform initialPlatform = FlutterKeystorePlatform.instance;

  test('$MethodChannelFlutterKeystore is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterKeystore>());
  });

  test('getPlatformVersion', () async {
    FlutterKeystore flutterKeystorePlugin = FlutterKeystore();
    MockFlutterKeystorePlatform fakePlatform = MockFlutterKeystorePlatform();
    FlutterKeystorePlatform.instance = fakePlatform;
  
    expect(await flutterKeystorePlugin.getPlatformVersion(), '42');
  });
}
