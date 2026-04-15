package com.example.simpleshell.ui.screens.settings

import com.example.simpleshell.data.importing.SimpleShellPcImportSummary
import com.example.simpleshell.data.remote.ReleaseInfo
import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themeColor: ThemeColor = ThemeColor.PURPLE,
    val language: Language = Language.SYSTEM,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val fingerprintUnlockEnabled: Boolean = false,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    val importState: ImportState = ImportState.Idle,
    val syncState: SyncState = SyncState.Idle,
    val credentialPrompt: CredentialPromptState? = null
)

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data object AlreadyLatest : UpdateCheckState()
    data class NewVersionAvailable(val releaseInfo: ReleaseInfo) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class ImportState {
    data object Idle : ImportState()
    data object Importing : ImportState()
    data class Success(val summary: SimpleShellPcImportSummary) : ImportState()
    data class Error(val message: String) : ImportState()
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Working : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

data class CredentialPromptState(
    val mode: CredentialPromptMode,
    val errorMessage: String? = null
)

enum class CredentialPromptMode {
    IMPORT_CONFIG,
    RESTORE_WEBDAV
}
