package com.campusai.core.security

import com.campusai.core.ai.CloudAiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonalAiProviderStoreTest {
    @Test
    fun `Codex base URL credential and selected model persist only in its secure provider slot`() {
        val storage = MemoryProviderStorage()
        val store = PersonalAiProviderStore(storage)
        val key = "codex-unit-secret-key"

        assertTrue(store.saveBaseUrl(CloudAiProvider.CODEX, "https://private-node.example/v2/").isSuccess)
        assertTrue(store.saveCredential(CloudAiProvider.CODEX, key).isSuccess)
        assertTrue(store.saveSelectedModel(CloudAiProvider.CODEX, "future-reasoner_2027.04").isSuccess)

        val reloaded = PersonalAiProviderStore(storage)
        val codex = reloaded.configuration(CloudAiProvider.CODEX)
        assertEquals("https://private-node.example/v2", codex.baseUrl)
        assertEquals("future-reasoner_2027.04", codex.selectedModelId)
        assertTrue(codex.hasCredential)
        assertFalse(codex.toString().contains(key))
        assertTrue(reloaded.hasCredential(CloudAiProvider.CODEX))
        assertFalse(reloaded.hasCredential(CloudAiProvider.DEEPSEEK))
        assertFalse(reloaded.hasCredential(CloudAiProvider.GOOGLE_GEMINI))
        assertTrue(storage.values.containsKey("personal_codex_provider_v1"))
        assertFalse(storage.values.containsKey("personal_deepseek_api_key"))
        assertFalse(storage.values.containsKey("personal_google_gemini_provider_v1"))
    }

    @Test
    fun `changing Codex request origin clears bearer credential and selected model`() {
        val storage = MemoryProviderStorage()
        val store = PersonalAiProviderStore(storage)
        assertTrue(store.saveCredential(CloudAiProvider.CODEX, "codex-unit-secret-key").isSuccess)
        assertTrue(store.saveSelectedModel(CloudAiProvider.CODEX, "gpt-5.6-sol").isSuccess)

        assertTrue(store.saveBaseUrl(CloudAiProvider.CODEX, "https://replacement-node.example/v1").isSuccess)

        val changed = PersonalAiProviderStore(storage).configuration(CloudAiProvider.CODEX)
        assertEquals("https://replacement-node.example/v1", changed.baseUrl)
        assertFalse(changed.hasCredential)
        assertEquals("", changed.selectedModelId)
        assertFalse(storage.values.getValue("personal_codex_provider_v1").contains("codex-unit-secret-key"))
    }

    @Test
    fun `credentials and selected models remain isolated by provider`() {
        val storage = MemoryProviderStorage()
        val store = PersonalAiProviderStore(storage)
        val deepSeekKey = "unit-deepseek-key-1234567890"
        val geminiKey = "AIza${"x".repeat(30)}"

        assertTrue(store.saveCredential(CloudAiProvider.DEEPSEEK, deepSeekKey).isSuccess)
        assertTrue(store.saveSelectedModel(CloudAiProvider.DEEPSEEK, "deepseek-v4-pro").isSuccess)
        assertTrue(store.saveCredential(CloudAiProvider.GOOGLE_GEMINI, geminiKey).isSuccess)
        assertTrue(store.saveSelectedModel(CloudAiProvider.GOOGLE_GEMINI, "models/gemini-3.7-flash").isSuccess)

        val reloaded = PersonalAiProviderStore(storage)
        val deepSeek = reloaded.configuration(CloudAiProvider.DEEPSEEK)
        val gemini = reloaded.configuration(CloudAiProvider.GOOGLE_GEMINI)
        assertEquals("deepseek-v4-pro", deepSeek.selectedModelId)
        assertEquals("gemini-3.7-flash", gemini.selectedModelId)
        assertFalse(deepSeek.toString().contains(deepSeekKey))
        assertFalse(gemini.toString().contains(geminiKey))
        assertEquals("ProviderCredential(value=redacted)", reloaded.readCredential(CloudAiProvider.DEEPSEEK).toString())

        assertTrue(store.deleteCredential(CloudAiProvider.DEEPSEEK))
        assertFalse(store.hasCredential(CloudAiProvider.DEEPSEEK))
        assertTrue(store.hasCredential(CloudAiProvider.GOOGLE_GEMINI))
        assertEquals("deepseek-v4-pro", store.selectedModel(CloudAiProvider.DEEPSEEK))
    }

    @Test
    fun `legacy encrypted DeepSeek slot is read and rewritten into versioned payload`() {
        val storage = MemoryProviderStorage().apply {
            values["personal_deepseek_api_key"] = "unit-legacy-key-1234567890"
        }
        val store = PersonalAiProviderStore(storage)

        assertTrue(store.hasCredential(CloudAiProvider.DEEPSEEK))
        assertTrue(store.saveSelectedModel(CloudAiProvider.DEEPSEEK, "deepseek-v4-flash").isSuccess)

        val migrated = storage.values.getValue("personal_deepseek_api_key")
        assertTrue(migrated.startsWith("{"))
        assertTrue(migrated.contains("\"version\":1"))
        assertEquals("deepseek-v4-flash", store.selectedModel(CloudAiProvider.DEEPSEEK))
    }

    @Test
    fun `invalid credentials and cross provider models fail closed`() {
        val store = PersonalAiProviderStore(MemoryProviderStorage())

        assertTrue(store.saveCredential(CloudAiProvider.CODEX, "x").isSuccess)
        assertTrue(store.saveCredential(CloudAiProvider.GOOGLE_GEMINI, "contains whitespace and is invalid").isFailure)
        assertTrue(store.saveCredential(CloudAiProvider.DEEPSEEK, "short").isFailure)
        assertTrue(store.saveSelectedModel(CloudAiProvider.GOOGLE_GEMINI, "deepseek-v4-pro").isFailure)
        assertFalse(store.hasCredential(CloudAiProvider.GOOGLE_GEMINI))
    }

    @Test
    fun `Gemini authorization keys are accepted without assuming a legacy prefix`() {
        val store = PersonalAiProviderStore(MemoryProviderStorage())
        val authorizationKey = "AQ.${"x".repeat(52)}"

        assertTrue(store.saveCredential(CloudAiProvider.GOOGLE_GEMINI, authorizationKey).isSuccess)
        assertTrue(store.hasCredential(CloudAiProvider.GOOGLE_GEMINI))
    }

    private class MemoryProviderStorage : ProviderSecretStorage {
        val values = linkedMapOf<String, String>()

        override fun read(key: String): String = values[key].orEmpty()

        override fun write(key: String, value: String): Boolean {
            if (value.isBlank()) values.remove(key) else values[key] = value
            return true
        }
    }
}
