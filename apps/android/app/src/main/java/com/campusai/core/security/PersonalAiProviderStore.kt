package com.campusai.core.security

import android.content.Context
import com.campusai.core.ai.CloudAiProvider
import com.campusai.core.ai.CloudProviderConfiguration
import org.json.JSONObject

internal interface ProviderSecretStorage {
    fun read(key: String): String
    fun write(key: String, value: String): Boolean
}

internal class SecurePreferencesProviderStorage(context: Context) : ProviderSecretStorage {
    private val appContext = context.applicationContext

    override fun read(key: String): String = SecurePreferences.decrypt(appContext, key)

    override fun write(key: String, value: String): Boolean =
        SecurePreferences.encrypt(appContext, key, value)
}

internal class ProviderCredential(val value: String) {
    override fun toString(): String = "ProviderCredential(value=redacted)"
}

class PersonalAiProviderStore internal constructor(
    private val storage: ProviderSecretStorage,
) {
    constructor(context: Context) : this(SecurePreferencesProviderStorage(context))

    fun saveCredential(provider: CloudAiProvider, rawCredential: String): Result<Unit> {
        val credential = rawCredential.trim()
        credentialValidationError(provider, credential)?.let { message ->
            return Result.failure(IllegalArgumentException(message))
        }
        val current = readPayload(provider)
        return write(provider, credential, current.modelId)
    }

    fun hasCredential(provider: CloudAiProvider): Boolean = readCredential(provider) != null

    fun deleteCredential(provider: CloudAiProvider): Boolean {
        val current = readPayload(provider)
        return write(provider, "", current.modelId).isSuccess
    }

    fun saveSelectedModel(provider: CloudAiProvider, rawModelId: String): Result<Unit> {
        val modelId = provider.normalizeModelId(rawModelId)
        if (modelId.isNotBlank() && !provider.acceptsModelId(modelId)) {
            return Result.failure(IllegalArgumentException("不支持的 ${provider.displayName} 模型 ID。"))
        }
        val current = readPayload(provider)
        return write(provider, current.credential, modelId)
    }

    fun selectedModel(provider: CloudAiProvider): String = readPayload(provider).modelId

    fun configuration(provider: CloudAiProvider): CloudProviderConfiguration {
        val payload = readPayload(provider)
        return CloudProviderConfiguration(
            provider = provider,
            selectedModelId = payload.modelId,
            hasCredential = payload.credential.isNotBlank(),
            maskedCredential = mask(payload.credential),
        )
    }

    internal fun readCredential(provider: CloudAiProvider): ProviderCredential? =
        readPayload(provider).credential.takeIf(String::isNotBlank)?.let(::ProviderCredential)

    private fun write(provider: CloudAiProvider, credential: String, modelId: String): Result<Unit> {
        val value = if (credential.isBlank() && modelId.isBlank()) {
            ""
        } else {
            JSONObject()
                .put("version", FORMAT_VERSION)
                .put("credential", credential)
                .put("modelId", modelId)
                .toString()
        }
        return if (storage.write(provider.storageKey(), value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("系统安全存储不可用，${provider.displayName} 配置未保存。"))
        }
    }

    private fun readPayload(provider: CloudAiProvider): StoredProviderPayload {
        val raw = storage.read(provider.storageKey())
        if (raw.isBlank()) return StoredProviderPayload()
        val structured = runCatching {
            val json = JSONObject(raw)
            if (json.optInt("version") != FORMAT_VERSION) return@runCatching null
            val credential = json.optString("credential").trim()
            val modelId = provider.normalizeModelId(json.optString("modelId"))
            if (credential.isNotBlank() && credentialValidationError(provider, credential) != null) return@runCatching null
            if (modelId.isNotBlank() && !provider.acceptsModelId(modelId)) return@runCatching null
            StoredProviderPayload(credential, modelId)
        }.getOrNull()
        if (structured != null) return structured

        // The previous DeepSeek implementation stored one encrypted raw key in this same slot.
        return if (provider == CloudAiProvider.DEEPSEEK && credentialValidationError(provider, raw) == null) {
            StoredProviderPayload(raw, "")
        } else {
            StoredProviderPayload()
        }
    }

    private fun CloudAiProvider.storageKey(): String = when (this) {
        CloudAiProvider.DEEPSEEK -> "personal_deepseek_api_key"
        CloudAiProvider.GOOGLE_GEMINI -> "personal_google_gemini_provider_v1"
    }

    private fun mask(credential: String): String = when {
        credential.length >= 8 -> "${credential.take(3)}••••${credential.takeLast(4)}"
        credential.isNotBlank() -> "已安全保存"
        else -> ""
    }

    private data class StoredProviderPayload(
        val credential: String = "",
        val modelId: String = "",
    )

    companion object {
        private const val FORMAT_VERSION = 1
        private val PRINTABLE_KEY_PATTERN = Regex("[!-~]{20,512}")
        internal fun credentialValidationError(provider: CloudAiProvider, credential: String): String? = when {
            !PRINTABLE_KEY_PATTERN.matches(credential) ->
                "Key 格式无效：请粘贴完整的 ${provider.displayName} API Key，且不要包含空格。"
            else -> null
        }
    }
}
