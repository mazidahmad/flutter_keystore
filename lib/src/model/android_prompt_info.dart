class AndroidPromptInfo {
  final String title;
  final String? subtitle;
  final String? description;
  final bool confirmationRequired;
  final String negativeButton;
  
  AndroidPromptInfo({
    required this.title,
    this.subtitle,
    this.description,
    required this.confirmationRequired,
    required this.negativeButton,
  });

  AndroidPromptInfo copyWith({
    String? title,
    String? subtitle,
    String? description,
    bool? confirmationRequired,
    String? negativeButton,
  }) {
    return AndroidPromptInfo(
      title: title ?? this.title,
      subtitle: subtitle ?? this.subtitle,
      description: description ?? this.description,
      confirmationRequired: confirmationRequired ?? this.confirmationRequired,
      negativeButton: negativeButton ?? this.negativeButton,
    );
  }

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'title': title,
      'subtitle': subtitle,
      'description': description,
      'confirmationRequired': confirmationRequired,
      'negativeButton': negativeButton,
    };
  }
}
