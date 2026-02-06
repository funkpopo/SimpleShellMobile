package com.example.simpleshell.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE: Preferences.Key<String> = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR: Preferences.Key<Boolean> = booleanPreferencesKey("dynamic_color")
        val THEME_COLOR: Preferences.Key<String> = stringPreferencesKey("theme_color")
        val LANGUAGE: Preferences.Key<String> = stringPreferencesKey("language")
        val TERMINAL_FONT_SCALE: Preferences.Key<Float> = floatPreferencesKey("terminal_font_scale")
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data
        .map { prefs ->
            UserPreferences(
                themeMode = prefs[Keys.THEME_MODE]?.toThemeMode() ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                themeColor = prefs[Keys.THEME_COLOR]?.toThemeColor() ?: ThemeColor.PURPLE,
                language = prefs[Keys.LANGUAGE]?.toLanguage() ?: Language.SYSTEM,
                terminalFontScale = prefs[Keys.TERMINAL_FONT_SCALE] ?: 1.0f
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setThemeColor(color: ThemeColor) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.THEME_COLOR] = color.name
        }
    }

    suspend fun setLanguage(language: Language) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }

    suspend fun setTerminalFontScale(scale: Float) {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[Keys.TERMINAL_FONT_SCALE] = scale
        }
    }

    suspend fun buildSyncUiSettingsJson(): String {
        val prefs = preferences.first()
        val json = JSONObject()

        val languageCode = when (prefs.language) {
            Language.CHINESE -> "zh-CN"
            Language.ENGLISH -> "en-US"
            Language.SYSTEM -> "system"
        }

        json.put("language", languageCode)

        when (prefs.themeMode) {
            ThemeMode.DARK -> json.put("darkMode", true)
            ThemeMode.LIGHT -> json.put("darkMode", false)
            ThemeMode.SYSTEM -> Unit
        }

        json.put("terminalFontSize", (prefs.terminalFontScale * 14f).toInt().coerceIn(10, 30))
        json.put("mobileThemeMode", prefs.themeMode.name)
        json.put("mobileDynamicColor", prefs.dynamicColor)
        json.put("mobileThemeColor", prefs.themeColor.name)
        json.put("mobileLanguage", prefs.language.name)
        json.put("mobileTerminalFontScale", prefs.terminalFontScale)

        return json.toString()
    }

    suspend fun applySyncUiSettingsJson(valueJson: String) {
        val json = runCatching { JSONObject(valueJson) }.getOrNull() ?: return

        val rawThemeMode = json.optString("mobileThemeMode", "").toThemeMode()
        if (rawThemeMode != null) {
            setThemeMode(rawThemeMode)
        } else if (json.has("darkMode")) {
            setThemeMode(if (json.optBoolean("darkMode", false)) ThemeMode.DARK else ThemeMode.LIGHT)
        }

        if (json.has("mobileDynamicColor")) {
            setDynamicColor(json.optBoolean("mobileDynamicColor", true))
        }

        json.optString("mobileThemeColor", "").toThemeColor()?.let {
            setThemeColor(it)
        }

        val language = when (json.optString("language", "").lowercase()) {
            "zh-cn", "zh" -> Language.CHINESE
            "en-us", "en" -> Language.ENGLISH
            "system", "" -> json.optString("mobileLanguage", "").toLanguage() ?: Language.SYSTEM
            else -> json.optString("mobileLanguage", "").toLanguage() ?: Language.SYSTEM
        }
        setLanguage(language)

        val scale = when {
            json.has("mobileTerminalFontScale") -> json.optDouble("mobileTerminalFontScale", 1.0).toFloat()
            json.has("terminalFontSize") -> (json.optDouble("terminalFontSize", 14.0) / 14.0).toFloat()
            else -> 1.0f
        }
        setTerminalFontScale(scale.coerceIn(0.4f, 2.5f))
    }
}

private fun String.toThemeMode(): ThemeMode? {
    return try {
        ThemeMode.valueOf(this)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun String.toThemeColor(): ThemeColor? {
    return try {
        ThemeColor.valueOf(this)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun String.toLanguage(): Language? {
    return try {
        Language.valueOf(this)
    } catch (_: IllegalArgumentException) {
        null
    }
}
