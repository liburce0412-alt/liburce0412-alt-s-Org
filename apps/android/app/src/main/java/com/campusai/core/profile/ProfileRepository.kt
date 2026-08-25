package com.campusai.core.profile

import com.campusai.core.network.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

data class CampusProfile(
    val id: String = "",
    val displayName: String = "Caesar 用户",
    val avatarPath: String = "",
    val coverPath: String = "",
    val bio: String = "",
    val role: String = "student",
    val level: Int = 1,
    val experience: Int = 0,
    val streakDays: Int = 0,
) {
    val avatarUrl: String get() = avatarPath.takeIf(String::isNotBlank)?.let { SupabaseClient.publicMediaUrl("avatars", it) }.orEmpty()
    val coverUrl: String get() = coverPath.takeIf(String::isNotBlank)?.let { SupabaseClient.publicMediaUrl("covers", it) }.orEmpty()
    val isStaff: Boolean get() = role in setOf("moderator", "admin", "super_admin")
}

data class ProfileState(
    val profile: CampusProfile = CampusProfile(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class ProfileRepository {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    suspend fun load(userId: String, fallbackName: String = "Caesar 用户") {
        if (userId.isBlank()) {
            _state.value = ProfileState(profile = CampusProfile(displayName = fallbackName))
            return
        }
        _state.value = _state.value.copy(loading = true, error = null, message = null)
        SupabaseClient.restGet(
            "profiles",
            mapOf(
                "select" to "id,display_name,avatar_path,cover_path,bio,role,level,experience,streak_days",
                "id" to "eq.$userId",
                "limit" to "1",
            ),
        ).fold(
            onSuccess = { rows ->
                val row = rows.optJSONObject(0)
                _state.value = ProfileState(
                    profile = if (row == null) CampusProfile(id = userId, displayName = fallbackName) else row.toProfile(fallbackName),
                )
            },
            onFailure = { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: "资料读取失败，请检查网络后重试。")
            },
        )
    }

    suspend fun updateText(userId: String, displayName: String, bio: String): Boolean {
        val cleanName = displayName.trim()
        if (userId.isBlank()) return fail("请先登录，再修改资料。")
        if (cleanName.length !in 2..32) return fail("名称需要 2–32 个字符。")
        if (bio.trim().length > 160) return fail("个人简介不能超过 160 个字符。")
        return update(
            userId,
            JSONObject()
                .put("display_name", cleanName)
                .put("bio", bio.trim())
                .put("updated_at", java.time.Instant.now().toString()),
            "资料已保存。",
        )
    }

    suspend fun uploadImage(
        userId: String,
        kind: ProfileImageKind,
        bytes: ByteArray,
        contentType: String,
    ): Boolean {
        if (userId.isBlank()) return fail("请先登录，再上传图片。")
        val limit = if (kind == ProfileImageKind.AVATAR) 5L * 1024 * 1024 else 10L * 1024 * 1024
        if (bytes.isEmpty()) return fail("没有读取到图片内容，请重新选择。")
        if (bytes.size > limit) return fail(if (kind == ProfileImageKind.AVATAR) "头像不能超过 5 MB。" else "背景图不能超过 10 MB。")
        val safeType = contentType.takeIf { it in setOf("image/jpeg", "image/png", "image/webp") }
            ?: return fail("仅支持 JPEG、PNG 或 WebP 图片。")
        val extension = when (safeType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "$userId/${UUID.randomUUID()}.$extension"
        val oldPath = if (kind == ProfileImageKind.AVATAR) _state.value.profile.avatarPath else _state.value.profile.coverPath
        val bucket = if (kind == ProfileImageKind.AVATAR) "avatars" else "covers"
        val column = if (kind == ProfileImageKind.AVATAR) "avatar_path" else "cover_path"
        _state.value = _state.value.copy(saving = true, error = null, message = null)
        return try {
            val uploaded = SupabaseClient.uploadObject(bucket, path, bytes, safeType)
            if (uploaded.isFailure) {
                return fail(uploaded.exceptionOrNull()?.message ?: "图片上传失败，请重试。")
            }
            val updated = update(
                userId,
                JSONObject().put(column, path).put("updated_at", java.time.Instant.now().toString()),
                if (kind == ProfileImageKind.AVATAR) "头像已更新。" else "背景已更新。",
            )
            if (!updated) {
                SupabaseClient.deleteObject(bucket, path)
                false
            } else {
                if (oldPath.isNotBlank() && oldPath != path) SupabaseClient.deleteObject(bucket, oldPath)
                true
            }
        } catch (error: Throwable) {
            SupabaseClient.deleteObject(bucket, path)
            fail(error.message ?: "图片保存没有完成，请检查网络后重试。")
        } finally {
            if (_state.value.saving) _state.value = _state.value.copy(saving = false)
        }
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null, error = null) }

    private suspend fun update(userId: String, payload: JSONObject, message: String): Boolean {
        _state.value = _state.value.copy(saving = true, error = null, message = null)
        return SupabaseClient.restUpdate("profiles", mapOf("id" to "eq.$userId"), payload).fold(
            onSuccess = { row ->
                _state.value = ProfileState(profile = row.toProfile(_state.value.profile.displayName), message = message)
                true
            },
            onFailure = { fail(it.message ?: "资料保存失败，请检查网络后重试。") },
        )
    }

    private fun fail(message: String): Boolean {
        _state.value = _state.value.copy(loading = false, saving = false, error = message, message = null)
        return false
    }
}

enum class ProfileImageKind { AVATAR, COVER }

private fun JSONObject.toProfile(fallbackName: String) = CampusProfile(
    id = nullableString("id"),
    displayName = nullableString("display_name").ifBlank { fallbackName },
    avatarPath = nullableString("avatar_path"),
    coverPath = nullableString("cover_path"),
    bio = nullableString("bio"),
    role = nullableString("role").ifBlank { "student" },
    level = optInt("level", 1),
    experience = optInt("experience", 0),
    streakDays = optInt("streak_days", 0),
)

private fun JSONObject.nullableString(name: String): String =
    if (isNull(name)) "" else optString(name).takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
