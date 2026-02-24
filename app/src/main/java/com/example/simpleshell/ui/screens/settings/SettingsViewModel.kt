package com.example.simpleshell.ui.screens.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.BuildConfig
import com.example.simpleshell.data.importing.SimpleShellPcConfigExporter
import com.example.simpleshell.data.importing.SimpleShellPcConfigImporter
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import com.example.simpleshell.data.remote.UpdateCheckResult
import com.example.simpleshell.data.remote.UpdateChecker
import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker,
    private val pcConfigImporter: SimpleShellPcConfigImporter,
    private val pcConfigExporter: SimpleShellPcConfigExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.preferences
                .catch {
                    _uiState.value = SettingsUiState()
                }
                .collect { prefs ->
                    _uiState.value = _uiState.value.copy(
                        themeMode = prefs.themeMode,
                        dynamicColor = prefs.dynamicColor,
                        themeColor = prefs.themeColor,
                        language = prefs.language
                    )
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDynamicColor(enabled)
        }
    }

    fun setThemeColor(color: ThemeColor) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeColor(color)
        }
    }

    fun setLanguage(language: Language) {
        viewModelScope.launch {
            userPreferencesRepository.setLanguage(language)
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updateCheckState = UpdateCheckState.Checking)

            val result = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)

            _uiState.value = _uiState.value.copy(
                updateCheckState = when (result) {
                    is UpdateCheckResult.NewVersionAvailable ->
                        UpdateCheckState.NewVersionAvailable(result.releaseInfo)
                    is UpdateCheckResult.AlreadyLatest ->
                        UpdateCheckState.AlreadyLatest
                    is UpdateCheckResult.Error ->
                        UpdateCheckState.Error(result.message)
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(updateCheckState = UpdateCheckState.Idle)
    }

    fun importPcConfig(jsonText: String) {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(importState = ImportState.Error("Empty config file"))
            return
        }
        if (_uiState.value.importState is ImportState.Importing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importState = ImportState.Importing)
            try {
                val summary = withContext(Dispatchers.IO) {
                    pcConfigImporter.importFromConfigJson(trimmed)
                }
                _uiState.value = _uiState.value.copy(importState = ImportState.Success(summary))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    importState = ImportState.Error(e.message ?: "Import failed")
                )
            }
        }
    }

    fun exportPcConfig(contentResolver: ContentResolver, uri: Uri) {
        if (_uiState.value.syncState is SyncState.Working) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncState = SyncState.Working)
            try {
                val payload = withContext(Dispatchers.IO) {
                    pcConfigExporter.exportToConfigJson().toByteArray(Charsets.UTF_8)
                }

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(payload)
                        stream.flush()
                    } ?: error("Unable to open destination file")
                }

                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Success("Config exported")
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Error(e.message ?: "Export failed")
                )
            }
        }
    }

    fun dismissSyncDialog() {
        _uiState.value = _uiState.value.copy(syncState = SyncState.Idle)
    }

    fun reportImportError(message: String) {
        _uiState.value = _uiState.value.copy(importState = ImportState.Error(message))
    }

    fun dismissImportDialog() {
        _uiState.value = _uiState.value.copy(importState = ImportState.Idle)
    }
}
