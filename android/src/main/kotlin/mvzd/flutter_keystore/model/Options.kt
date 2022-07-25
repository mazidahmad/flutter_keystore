package mvzd.flutter_keystore.model

import androidx.biometric.BiometricPrompt

data class Options(
    val authRequired: Boolean,
    val tag: String,
    val oncePrompt: Boolean,
    val promptInfo: BiometricPrompt.PromptInfo,
    val authValidityDuration: Int
)
