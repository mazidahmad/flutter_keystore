import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:flutter_keystore/flutter_keystore.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _flutterKeystorePlugin = FlutterKeystore();

  final String tag = "keychain-coinbit.privateKey";
  final String tagBiometric = "keychain-coinbit.privateKeyPresence";
  bool _isRequiresBiometric = false;
  String publicKey = "";

  TextEditingController input = TextEditingController();

  Uint8List encrypted = Uint8List(0);
  Uint8List encryptedWithPublicKey = Uint8List(0);
  String decrypted = "";

  List<String> _listData = [];

  late AndroidOptions _options;
  late AndroidPromptInfo _androidPromptInfo;

  void saveBiometric(value) async {
    var prefs = await SharedPreferences.getInstance();
    await prefs.setBool("authRequired", value);
    setState(() {
      _options = _options.copyWith(
          tag: value ? tagBiometric : tag, authRequired: value);
    });
  }

  void saveData() async {
    var prefs = await SharedPreferences.getInstance();
    await prefs.setStringList("datas", _listData);
  }

  void getData() async {
    var prefs = await SharedPreferences.getInstance();
    setState(() {
      _listData = prefs.getStringList("datas") ?? [];
      _isRequiresBiometric = prefs.getBool("authRequired") ?? false;
      _options = _options.copyWith(
          tag: _isRequiresBiometric ? tagBiometric : tag,
          authRequired: _isRequiresBiometric);
    });
  }

  void resetConfig(AndroidOptions options) async {
    var prefs = await SharedPreferences.getInstance();
    prefs.clear();
    setState(() {
      _listData = [];
    });
    await _flutterKeystorePlugin.resetConfiguration(options: options);
  }

  @override
  void initState() {
    super.initState();
    getData();
    _androidPromptInfo = AndroidPromptInfo(
        title: "Confirm Biometric",
        confirmationRequired: false,
        negativeButton: "Cancel Auth");
    _options = AndroidOptions(
        tag: _isRequiresBiometric ? tagBiometric : tag,
        authRequired: _isRequiresBiometric,
        authValidityDuration: 10,
        oncePrompt: true,
        androidPromptInfo: _androidPromptInfo);
  }

  void encrypt(String message) {
    _flutterKeystorePlugin
        .encrypt(options: _options, message: message)
        .then((value) {
      setState(() {
        encrypted = value ?? Uint8List(0);
        _listData.add(String.fromCharCodes(encrypted));
        saveData();
      });
    });
  }

  void decrypt(Uint8List data) async {
    try {
      await _flutterKeystorePlugin
          .decrypt(message: data, options: _options)
          .then((value) {
        setState(() {
          decrypted = value ?? "";
        });
      });
    } on PlatformException catch (e) {
      if (e.message != null && e.message!.toLowerCase().contains("cancel")) {
        print("Calcelled by user");
      }
      print(e.message);
    } catch (e) {
      print(e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          children: [
            TextField(
              controller: input,
            ),
            Row(
              children: [
                const Text("Biometric"),
                const SizedBox(
                  width: 10,
                ),
                Switch(
                    value: _isRequiresBiometric,
                    onChanged: (value) {
                      setState(() {
                        _isRequiresBiometric = value;
                        encrypted = Uint8List(0);
                        decrypted = "";
                        resetConfig(_options);
                        saveBiometric(value);
                      });
                    }),
              ],
            ),
            TextButton(
                onPressed: () {
                  encrypt(input.text);
                  // input.clear();
                },
                child: Text("encrypt!")),
            Text(encrypted.toString()),
            TextButton(
                onPressed: () {
                  decrypt(encrypted);
                },
                child: Text("decrypt!")),
            Text(decrypted),
            TextButton(
                onPressed: () {
                  decrypt(Uint8List.fromList(
                      ("dgasydtasid7tasuidytasvduyas").codeUnits));
                },
                child: Text("decrypt false!")),
            SizedBox(
              height: 20,
            ),
            Text("List Saved Data"),
            Divider(),
            Expanded(
              child: ListView.builder(
                itemCount: _listData.length,
                itemBuilder: (_, idx) => Column(
                  children: [
                    Row(
                      children: [
                        Expanded(child: Text(_listData[idx])),
                        SizedBox(
                          width: 10,
                        ),
                        TextButton(
                          onPressed: () {
                            decrypt(
                                Uint8List.fromList((_listData[idx]).codeUnits));
                          },
                          child: Text("decrypt"),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            // Divider(),
            // TextButton(onPressed: () {
            //   // removeKey();
            // }, child: Text("reset key")),
            // Divider(),
            // TextButton(onPressed: () {
            //   // cobaError();
            // }, child: Text("coba Error")),
            // Divider(),
            // Text(publicKey),
            // TextButton(onPressed: () {
            //   // getPublicKey();
            // }, child: Text("get public key")),
            // TextButton(onPressed: () {
            //   // encryptWithPublicKey(input.text);
            // }, child: Text("encrypt with public key")),
            // Text(encryptedWithPublicKey.toString()),
            // TextButton(onPressed: () {
            //   decrypted = "";
            //   // decrypt(encryptedWithPublicKey);
            // }, child: Text("decrypt from encryptedWithPublicKey")),
          ],
        ),
      ),
    );
  }
}
