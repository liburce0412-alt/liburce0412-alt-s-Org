package com.campusai.core.health.mifitness

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

private fun anonymousAccountScope(userId: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(ACCOUNT_SCOPE_NAMESPACE.toByteArray(Charsets.UTF_8))
    digest.update(0.toByte())
    digest.update(userId.toByteArray(Charsets.UTF_8))
    return digest.digest()
        .take(16)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val ACCOUNT_SCOPE_NAMESPACE = "mi-fitness-cn-v1"

class MiFitnessCredential internal constructor(
    val userId: String,
    val passToken: String,
) {
    /** Pseudonymous cache partition; no account or token material is stored in summaries. */
    val accountScope: String = anonymousAccountScope(userId)

    override fun toString(): String =
        "MiFitnessCredential(userId=redacted, passToken=redacted)"
}

class MiFitnessCredentialStore internal constructor(
    private val storage: MiFitnessSecretStorage,
) {
    constructor(context: Context) : this(SecurePreferencesMiFitnessStorage(context))

    fun save(userId: String, passToken: String): Result<Unit> {
        val normalizedUserId = userId.trim()
        val normalizedPassToken = passToken.trim()
        validationError(normalizedUserId, normalizedPassToken)?.let {
            return Result.failure(IllegalArgumentException(it))
        }

        val payload = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("userId", normalizedUserId)
            .put("passToken", normalizedPassToken)
            .toString()
        return if (storage.write(STORAGE_KEY, payload)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("系统安全存储不可用，小米运动健康凭据未保存。"))
        }
    }

    fun read(): MiFitnessCredential? {
        val payload = storage.read(STORAGE_KEY)
        if (payload.isBlank()) return null
        return runCatching {
            val json = JSONObject(payload)
            if (json.optInt("version") != FORMAT_VERSION) return@runCatching null
            val userId = json.getString("userId")
            val passToken = json.getString("passToken")
            if (validationError(userId, passToken) != null) null
            else MiFitnessCredential(userId, passToken)
        }.getOrNull()
    }

    fun hasCredentials(): Boolean = read() != null

    fun delete(): Boolean = storage.write(STORAGE_KEY, "")

    companion object {
        private const val FORMAT_VERSION = 1
        private const val STORAGE_KEY = "mi_fitness_cn_credentials_v1"
        private const val MAX_PASS_TOKEN_LENGTH = 8_192

        internal fun validationError(userId: String, passToken: String): String? = when {
            !USER_ID_PATTERN.matches(userId) -> "小米账号标识无效。"
            passToken.isEmpty() || passToken.length > MAX_PASS_TOKEN_LENGTH || !passToken.all(::isCookieOctet) -> "passToken 无效。"
            else -> null
        }

        private fun isCookieOctet(value: Char): Boolean = value.code == 0x21 ||
            value.code in 0x23..0x2b ||
            value.code in 0x2d..0x3a ||
            value.code in 0x3c..0x5b ||
            value.code in 0x5d..0x7e

        private val USER_ID_PATTERN = Regex("[0-9]{1,32}")
    }
}
