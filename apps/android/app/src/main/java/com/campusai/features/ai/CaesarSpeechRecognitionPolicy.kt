package com.campusai.features.ai

internal enum class CaesarSpeechRecognizerKind {
    ON_DEVICE,
    SYSTEM,
    UNAVAILABLE,
}

internal data class CaesarSpeechRecognitionPolicy(
    val recognizerKind: CaesarSpeechRecognizerKind,
    val requiresSystemConsent: Boolean,
    val preferOffline: Boolean,
)

internal fun caesarSpeechRecognitionPolicy(
    onDeviceAvailable: Boolean,
    onDeviceRuntimeFailed: Boolean = false,
    systemAvailable: Boolean,
    systemConsentGranted: Boolean,
): CaesarSpeechRecognitionPolicy = when {
    onDeviceAvailable && !onDeviceRuntimeFailed -> CaesarSpeechRecognitionPolicy(
        recognizerKind = CaesarSpeechRecognizerKind.ON_DEVICE,
        requiresSystemConsent = false,
        preferOffline = true,
    )
    systemAvailable -> CaesarSpeechRecognitionPolicy(
        recognizerKind = CaesarSpeechRecognizerKind.SYSTEM,
        requiresSystemConsent = !systemConsentGranted,
        preferOffline = false,
    )
    else -> CaesarSpeechRecognitionPolicy(
        recognizerKind = CaesarSpeechRecognizerKind.UNAVAILABLE,
        requiresSystemConsent = false,
        preferOffline = false,
    )
}
