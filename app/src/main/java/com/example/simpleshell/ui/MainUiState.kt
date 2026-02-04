package com.example.simpleshell.ui

import com.example.simpleshell.domain.model.ThemeMode

data class MainUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

