package com.example.simpleshell.ui.screens.settings

import com.example.simpleshell.domain.model.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

