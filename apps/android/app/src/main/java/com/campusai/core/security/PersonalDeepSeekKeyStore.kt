package com.campusai.core.security

import android.content.Context
import com.campusai.core.ai.CloudAiProvider

class PersonalDeepSeekKeyStore(context: Context) {
    private val providers = PersonalAiProviderStore(context.applicationContext)

    fun save(rawKey: String): Result<Unit> {
        return providers.saveCredential(CloudAiProvider.DEEPSEEK, rawKey)
    }

    fun read(): String = providers.readCredential(CloudAiProvider.DEEPSEEK)?.value.orEmpty()

    fun hasKey(): Boolean = providers.hasCredential(CloudAiProvider.DEEPSEEK)

    fun delete(): Boolean = providers.deleteCredential(CloudAiProvider.DEEPSEEK)

    fun maskedLabel(): String = providers.configuration(CloudAiProvider.DEEPSEEK).maskedCredential

    fun selectedModel(): String = providers.selectedModel(CloudAiProvider.DEEPSEEK)

    fun saveSelectedModel(modelId: String): Result<Unit> =
        providers.saveSelectedModel(CloudAiProvider.DEEPSEEK, modelId)

    companion object {
        internal fun isValid(key: String): Boolean =
            PersonalAiProviderStore.credentialValidationError(CloudAiProvider.DEEPSEEK, key) == null
    }
}
