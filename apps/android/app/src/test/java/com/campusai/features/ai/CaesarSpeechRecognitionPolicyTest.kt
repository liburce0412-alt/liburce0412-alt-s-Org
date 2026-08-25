package com.campusai.features.ai

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
}
