package com.campusai.features.ai

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaesarSpeechRecognitionPolicyTest {
    @Test
    fun `on-device recognition always wins without system consent`() {
        val policy = caesarSpeechRecognitionPolicy(
            onDeviceAvailable = true,
            systemAvailable = true,
            systemConsentGranted = false,
        )

        assertEquals(CaesarSpeechRecognizerKind.ON_DEVICE, policy.recognizerKind)
        assertFalse(policy.requiresSystemConsent)
        assertTrue(policy.preferOffline)
    }

    @Test
    fun `system recognition remains blocked until explicit consent`() {
        val policy = caesarSpeechRecognitionPolicy(
            onDeviceAvailable = false,
            systemAvailable = true,
            systemConsentGranted = false,
        )

        assertEquals(CaesarSpeechRecognizerKind.SYSTEM, policy.recognizerKind)
        assertTrue(policy.requiresSystemConsent)
        assertFalse(policy.preferOffline)
    }

    @Test
    fun `on-device runtime failure falls back to consented system recognizer`() {
        val policy = caesarSpeechRecognitionPolicy(
            onDeviceAvailable = true,
            onDeviceRuntimeFailed = true,
            systemAvailable = true,
            systemConsentGranted = false,
        )

        assertEquals(CaesarSpeechRecognizerKind.SYSTEM, policy.recognizerKind)
        assertTrue(policy.requiresSystemConsent)
        assertFalse(policy.preferOffline)
    }

    @Test
    fun `system recognition can start after consent without offline claim`() {
        val policy = caesarSpeechRecognitionPolicy(
            onDeviceAvailable = false,
            systemAvailable = true,
            systemConsentGranted = true,
        )

        assertEquals(CaesarSpeechRecognizerKind.SYSTEM, policy.recognizerKind)
        assertFalse(policy.requiresSystemConsent)
        assertFalse(policy.preferOffline)
    }

    @Test
    fun `no recognizer reports unavailable`() {
        val policy = caesarSpeechRecognitionPolicy(
            onDeviceAvailable = false,
            systemAvailable = false,
            systemConsentGranted = false,
        )

        assertEquals(CaesarSpeechRecognizerKind.UNAVAILABLE, policy.recognizerKind)
        assertFalse(policy.requiresSystemConsent)
        assertFalse(policy.preferOffline)
    }

    @Test
    fun `system permission mismatch uses user mediated recognition activity`() {
        assertTrue(
            shouldUseSpeechRecognitionActivityFallback(
                recognizerKind = CaesarSpeechRecognizerKind.SYSTEM,
                errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                appMicrophonePermissionGranted = true,
            ),
        )
        assertFalse(
            shouldUseSpeechRecognitionActivityFallback(
                recognizerKind = CaesarSpeechRecognizerKind.ON_DEVICE,
                errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                appMicrophonePermissionGranted = true,
            ),
        )
        assertFalse(
            shouldUseSpeechRecognitionActivityFallback(
                recognizerKind = CaesarSpeechRecognizerKind.SYSTEM,
                errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                appMicrophonePermissionGranted = true,
            ),
        )
    }

    @Test
    fun `xiaomi permission mismatch opens engine permission recovery when available`() {
        assertEquals(
            CaesarSpeechFallbackRoute.XIAOMI_ENGINE_PERMISSION_RECOVERY,
            caesarSpeechFallbackRoute(
                recognizerKind = CaesarSpeechRecognizerKind.SYSTEM,
                errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                appMicrophonePermissionGranted = true,
                xiaomiEnginePermissionRecoveryAvailable = true,
            ),
        )
    }

    @Test
    fun `permission mismatch keeps standard fallback on other devices`() {
        assertEquals(
            CaesarSpeechFallbackRoute.STANDARD_RECOGNITION_ACTIVITY,
            caesarSpeechFallbackRoute(
                recognizerKind = CaesarSpeechRecognizerKind.SYSTEM,
                errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                appMicrophonePermissionGranted = true,
                xiaomiEnginePermissionRecoveryAvailable = false,
            ),
        )
    }

    @Test
    fun `xiaomi recovery is not used for unrelated recognizer failures`() {
        assertEquals(
            CaesarSpeechFallbackRoute.STANDARD_RECOGNITION_ACTIVITY,
            caesarSpeechFallbackRoute(
                recognizerKind = CaesarSpeechRecognizerKind.SYSTEM,
                errorCode = SpeechRecognizer.ERROR_SERVER,
                appMicrophonePermissionGranted = true,
                xiaomiEnginePermissionRecoveryAvailable = true,
            ),
        )
        assertEquals(
            CaesarSpeechFallbackRoute.NONE,
            caesarSpeechFallbackRoute(
                recognizerKind = CaesarSpeechRecognizerKind.SYSTEM,
                errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                appMicrophonePermissionGranted = false,
                xiaomiEnginePermissionRecoveryAvailable = true,
            ),
        )
    }
}
