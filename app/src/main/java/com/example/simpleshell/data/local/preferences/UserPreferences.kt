package com.example.simpleshell.data.local.preferences

import com.example.simpleshell.domain.model.ThemeMode

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

