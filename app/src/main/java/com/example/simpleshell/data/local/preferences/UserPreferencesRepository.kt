package com.example.simpleshell.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data
        .map { prefs ->
            UserPreferences(
                themeMode = prefs[Keys.THEME_MODE]?.toThemeMode() ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                themeColor = prefs[Keys.THEME_COLOR]?.toThemeColor() ?: ThemeColor.PURPLE,
                language = prefs[Keys.LANGUAGE]?.toLanguage() ?: Language.SYSTEM
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
}

private fun String.toThemeMode(): ThemeMode {
    return try {
        ThemeMode.valueOf(this)
    } catch (_: IllegalArgumentException) {
        ThemeMode.SYSTEM
    }
}

private fun String.toThemeColor(): ThemeColor {
    return try {
        ThemeColor.valueOf(this)
    } catch (_: IllegalArgumentException) {
        ThemeColor.PURPLE
    }
}

private fun String.toLanguage(): Language {
    return try {
        Language.valueOf(this)
    } catch (_: IllegalArgumentException) {
        Language.SYSTEM
    }
}

