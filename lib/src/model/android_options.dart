import 'package:flutter_keystore/src/model/android_prompt_info.dart';

class AndroidOptions {
  final bool authRequired;
  final String tag;
  final bool oncePrompt;
  final AndroidPromptInfo? androidPromptInfo;
  final int authValidityDuration;

  AndroidOptions({
    required this.authRequired,
    required this.tag,
    this.oncePrompt = false,
    this.authValidityDuration = 10,
    this.androidPromptInfo,
  });

  AndroidOptions copyWith({
    bool? authRequired,
    String? tag,
    bool? oncePrompt,
    int? authValidityDuration,
    AndroidPromptInfo? androidPromptInfo,
  }) {
    return AndroidOptions(
      authRequired: authRequired ?? this.authRequired,
      tag: tag ?? this.tag,
      oncePrompt: oncePrompt ?? this.oncePrompt,
      authValidityDuration: authValidityDuration ?? this.authValidityDuration,
      androidPromptInfo: androidPromptInfo ?? this.androidPromptInfo,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'authRequired': authRequired,
      'tag': tag,
      'authValidityDuration': authValidityDuration,
      'oncePrompt': oncePrompt,
      'androidPromptInfo': androidPromptInfo?.toMap(),
    };
  }
}
