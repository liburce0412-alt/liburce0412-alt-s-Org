package com.campusai.features.ai

import android.speech.SpeechRecognizer

internal enum class CaesarSpeechRecognizerKind {
    ON_DEVICE,
    SYSTEM,
    UNAVAILABLE,
}

internal enum class CaesarSpeechFallbackRoute {
    NONE,
    XIAOMI_ENGINE_PERMISSION_RECOVERY,
    STANDARD_RECOGNITION_ACTIVITY,
}

internal const val XIAOMI_PUBLIC_SPEECH_ACTION = "com.xiaomi.mibrain.action.RECOGNIZE_SPEECH"

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

/**
 * Some vendor RecognitionService implementations advertise availability but reject direct
 * microphone access for third-party callers. Their exported ACTION_RECOGNIZE_SPEECH activity can
 * still perform the same user-mediated transcription, so fail over only for service/audio faults.
 */
internal fun shouldUseSpeechRecognitionActivityFallback(
    recognizerKind: CaesarSpeechRecognizerKind,
    errorCode: Int,
    appMicrophonePermissionGranted: Boolean,
): Boolean = recognizerKind == CaesarSpeechRecognizerKind.SYSTEM &&
    appMicrophonePermissionGranted &&
    errorCode in setOf(
        SpeechRecognizer.ERROR_AUDIO,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    )

/**
 * Xiaomi's system recognizer can report ERROR_INSUFFICIENT_PERMISSIONS even when the caller has
 * microphone access because the speech-engine package itself lost that permission. Its public
 * activity lets Android restore the engine permission without CampusAI inspecting another app.
 */
internal fun caesarSpeechFallbackRoute(
    recognizerKind: CaesarSpeechRecognizerKind,
    errorCode: Int,
    appMicrophonePermissionGranted: Boolean,
    xiaomiEnginePermissionRecoveryAvailable: Boolean,
): CaesarSpeechFallbackRoute {
    if (!shouldUseSpeechRecognitionActivityFallback(
            recognizerKind = recognizerKind,
            errorCode = errorCode,
            appMicrophonePermissionGranted = appMicrophonePermissionGranted,
        )
    ) {
        return CaesarSpeechFallbackRoute.NONE
    }

    return if (
        errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
        xiaomiEnginePermissionRecoveryAvailable
    ) {
        CaesarSpeechFallbackRoute.XIAOMI_ENGINE_PERMISSION_RECOVERY
    } else {
        CaesarSpeechFallbackRoute.STANDARD_RECOGNITION_ACTIVITY
    }
}
