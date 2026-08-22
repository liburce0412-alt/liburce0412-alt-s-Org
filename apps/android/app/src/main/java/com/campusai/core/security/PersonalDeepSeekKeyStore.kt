package com.campusai.core.security

import android.content.Context

class PersonalDeepSeekKeyStore(context: Context) {
    private val appContext = context.applicationContext

    fun save(rawKey: String): Result<Unit> {
        val key = rawKey.trim()
        if (!isValid(key)) {
            return Result.failure(IllegalArgumentException("Key 格式无效：请粘贴完整的 DeepSeek API Key，且不要包含空格。"))
        }
        return if (SecurePreferences.encrypt(appContext, STORAGE_KEY, key)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("系统安全存储不可用，Key 未保存。请确认设备已启用安全锁屏后重试。"))
        }
    }

    fun read(): String = SecurePreferences.decrypt(appContext, STORAGE_KEY)

    fun hasKey(): Boolean = read().isNotBlank()

    fun delete(): Boolean = SecurePreferences.encrypt(appContext, STORAGE_KEY, "")

    fun maskedLabel(): String {
        val key = read()
        return if (key.length >= 8) "${key.take(3)}••••${key.takeLast(4)}" else "已安全保存"
    }

    companion object {
        private const val STORAGE_KEY = "personal_deepseek_api_key"

        internal fun isValid(key: String): Boolean =
            key.length >= 20 && key.none(Char::isWhitespace) && key.all { it.code in 33..126 }
    }
}
