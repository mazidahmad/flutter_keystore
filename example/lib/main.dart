import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_keystore/flutter_keystore.dart';
import 'package:flutter_keystore/src/model/access_control.dart';

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

  late AccessControl _accessControl;

  @override
  void initState() {
    super.initState();
    _accessControl = AccessControl(tag: _isRequiresBiometric ? tagBiometric : tag, setUserAuthenticatedRequired: _isRequiresBiometric);
  }

  void encrypt(String message) {
    _flutterKeystorePlugin.encrypt(accessControl: _accessControl, message: message).then((value){
      setState(() {
        encrypted = value ?? Uint8List(0);
      });
    });
  }

  void decrypt(Uint8List data) {
    _flutterKeystorePlugin.decrypt(message: data, accessControl: _accessControl).then((value){
      setState(() {
        decrypted = value ?? "";
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: ListView(
          children: [
            TextField(
              controller: input,
            ),
            Row(
              children: [
                const Text("Biometric"),
                const SizedBox(width: 10,),
                Switch(value: _isRequiresBiometric, onChanged: (value) {
                  setState(() {
                    _isRequiresBiometric = value;
                    encrypted = Uint8List(0);
                    decrypted = "";
                    _accessControl = AccessControl(tag: _isRequiresBiometric ? tagBiometric : tag, setUserAuthenticatedRequired: _isRequiresBiometric);
                  });
                }),
              ],
            ),
            TextButton(onPressed: () {
              encrypt(input.text);
              // input.clear();
            }, child: Text("encrypt!")),
            Text(
                encrypted.toString()
            ),
            TextButton(onPressed: () {
              decrypt(encrypted);
            }, child: Text("decrypt!")),
            Text(
                decrypted
            ),
            Divider(),
            TextButton(onPressed: () {
              // removeKey();
            }, child: Text("reset key")),
            Divider(),
            TextButton(onPressed: () {
              // cobaError();
            }, child: Text("coba Error")),
            Divider(),
            Text(publicKey),
            TextButton(onPressed: () {
              // getPublicKey();
            }, child: Text("get public key")),
            TextButton(onPressed: () {
              // encryptWithPublicKey(input.text);
            }, child: Text("encrypt with public key")),
            Text(encryptedWithPublicKey.toString()),
            TextButton(onPressed: () {
              decrypted = "";
              // decrypt(encryptedWithPublicKey);
            }, child: Text("decrypt from encryptedWithPublicKey")),
          ],
        ),
      ),
    );
  }
}
