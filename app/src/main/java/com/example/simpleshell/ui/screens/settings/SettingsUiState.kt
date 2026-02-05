package com.example.simpleshell.ui.screens.settings

import com.example.simpleshell.data.remote.ReleaseInfo
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themeColor: ThemeColor = ThemeColor.PURPLE,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle
)

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data object AlreadyLatest : UpdateCheckState()
    data class NewVersionAvailable(val releaseInfo: ReleaseInfo) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

