import 'package:flutter_keystore/src/model/android_prompt_info.dart';

class AccessControl {
  final bool setUserAuthenticatedRequired;
  final String tag;
  final AndroidPromptInfo? androidPromptInfo;

  AccessControl({
    required this.setUserAuthenticatedRequired,
    required this.tag,
    this.androidPromptInfo,
  });

  AccessControl copyWith({
    bool? setUserAuthenticatedRequired,
    String? tag,
    AndroidPromptInfo? androidPromptInfo,
  }) {
    return AccessControl(
      setUserAuthenticatedRequired: setUserAuthenticatedRequired ?? this.setUserAuthenticatedRequired,
      tag: tag ?? this.tag,
      androidPromptInfo: androidPromptInfo ?? this.androidPromptInfo,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'setUserAuthenticatedRequired': setUserAuthenticatedRequired,
      'tag': tag,
      'androidPromptInfo': androidPromptInfo?.toMap(),
    };
  }
}
