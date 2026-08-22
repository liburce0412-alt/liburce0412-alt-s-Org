package com.campusai.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.campusai.core.model.AiProvider
import com.campusai.core.model.MotionMode
import com.campusai.core.model.RenderQuality
import com.campusai.core.model.SpectraEnvironment
import com.campusai.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.campusPreferences by preferencesDataStore("campusai_user_preferences")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val motionMode: MotionMode = MotionMode.ON,
    val renderQuality: RenderQuality = RenderQuality.AUTO,
    val environment: SpectraEnvironment = SpectraEnvironment.ORIGINAL,
    val soundEnabled: Boolean = true,
    val aiProvider: AiProvider = AiProvider.AUTO,
    val localModelWifiOnly: Boolean = true,
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val motion = stringPreferencesKey("motion_mode")
        val quality = stringPreferencesKey("render_quality")
        val environment = stringPreferencesKey("spectra_environment")
        val sound = booleanPreferencesKey("sound_enabled")
        val aiProvider = stringPreferencesKey("ai_provider")
        val localModelWifiOnly = booleanPreferencesKey("local_model_wifi_only")
    }

    val preferences: Flow<UserPreferences> = context.campusPreferences.data.map { values ->
        UserPreferences(
            themeMode = values[Keys.theme].toEnumOr(ThemeMode.SYSTEM),
            motionMode = values[Keys.motion].toEnumOr(MotionMode.ON),
            renderQuality = values[Keys.quality].toEnumOr(RenderQuality.AUTO),
            environment = values[Keys.environment].toEnumOr(SpectraEnvironment.ORIGINAL),
            soundEnabled = values[Keys.sound] ?: true,
            aiProvider = values[Keys.aiProvider].toEnumOr(AiProvider.AUTO),
            localModelWifiOnly = values[Keys.localModelWifiOnly] ?: true,
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.campusPreferences.edit { it[Keys.theme] = value.name }
    suspend fun setMotion(value: MotionMode) = context.campusPreferences.edit { it[Keys.motion] = value.name }
    suspend fun setQuality(value: RenderQuality) = context.campusPreferences.edit { it[Keys.quality] = value.name }
    suspend fun setEnvironment(value: SpectraEnvironment) = context.campusPreferences.edit { it[Keys.environment] = value.name }
    suspend fun setSound(value: Boolean) = context.campusPreferences.edit { it[Keys.sound] = value }
    suspend fun setAiProvider(value: AiProvider) = context.campusPreferences.edit { it[Keys.aiProvider] = value.name }
    suspend fun setLocalModelWifiOnly(value: Boolean) = context.campusPreferences.edit { it[Keys.localModelWifiOnly] = value }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
    this?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: fallback
