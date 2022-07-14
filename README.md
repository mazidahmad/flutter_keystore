
# flutter_keystore

  

A library to encrypt and decrypt data using Android Keystore with TEE/StrongBox. 
  

## Getting Started

Instalation

 - Requirements :
	 - Android API Level >= 23
	 - Make sure to use kotlin version 1.4.31
	 - MainActivity should to extends FlutterFragmentActivity
	 
	 
 - Usage
 
Create Object
```dart

    import  'package:flutter_keystore/flutter_keystore.dart';
    
    final flutterKeystore = FlutterKeystore()
```
	
Encrypt & Decrypt
```dart

    var promptInfo = AndroidPromptInfo(title:  "Confirm Biometric", confirmationRequired:  false, negativeButton:  "Cancel Auth");
    var accessControl = AccessControl(tag: "TAG", setUserAuthenticatedRequired:  true, androidPromptInfo:  promptInfo);
 
    var encryptedData = await flutterKeystore.encrypt(accessControl: accessControl, message: message);
    
    var decryptedData = await  flutterKeystore.decrypt(message: encryptedData, accessControl:  accessControl);
```
