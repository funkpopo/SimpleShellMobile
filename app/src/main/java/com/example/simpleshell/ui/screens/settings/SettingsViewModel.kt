package com.example.simpleshell.ui.screens.settings

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.BuildConfig
import com.example.simpleshell.data.importing.SimpleShellPcConfigExporter
import com.example.simpleshell.data.importing.SimpleShellPcConfigImporter
import com.example.simpleshell.data.importing.SimpleShellPcCredentialLockedException
import com.example.simpleshell.data.importing.SimpleShellPcInvalidMasterPasswordException
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import com.example.simpleshell.data.remote.UpdateCheckResult
import com.example.simpleshell.data.remote.UpdateChecker
import com.example.simpleshell.data.remote.WebDavSyncManager
import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode
import com.example.simpleshell.utils.BiometricMasterPasswordStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker,
    private val pcConfigImporter: SimpleShellPcConfigImporter,
    private val pcConfigExporter: SimpleShellPcConfigExporter,
    private val webDavSyncManager: WebDavSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var pendingImportJson: String? = null
    private var pendingBiometricSaveScope: String? = null
    private var pendingBiometricSavePassword: String? = null

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
                        language = prefs.language,
                        webDavUrl = prefs.webDavUrl,
                        webDavUsername = prefs.webDavUsername,
                        webDavPassword = prefs.webDavPassword,
                        fingerprintUnlockEnabled = prefs.fingerprintUnlockEnabled,
                        biometricMasterPasswordEnabled = prefs.biometricMasterPasswordEnabled
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

    fun setWebDavUrl(url: String) {
        viewModelScope.launch {
            userPreferencesRepository.setWebDavUrl(url)
        }
    }

    fun setWebDavUsername(username: String) {
        viewModelScope.launch {
            userPreferencesRepository.setWebDavUsername(username)
        }
    }

    fun setWebDavPassword(password: String) {
        viewModelScope.launch {
            userPreferencesRepository.setWebDavPassword(password)
        }
    }

    fun setFingerprintUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFingerprintUnlockEnabled(enabled)
        }
    }

    fun setBiometricMasterPasswordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBiometricMasterPasswordEnabled(enabled)
            if (!enabled) {
                withContext(Dispatchers.IO) {
                    BiometricMasterPasswordStore.clearAll(appContext)
                }
            }
        }
    }

    fun backupToWebDav() {
        if (_uiState.value.syncState is SyncState.Working) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncState = SyncState.Working)
            val result = webDavSyncManager.backup()
            _uiState.value = _uiState.value.copy(
                syncState = if (result.isSuccess) {
                    SyncState.Success("Backup to WebDAV successful")
                } else {
                    SyncState.Error(result.exceptionOrNull()?.message ?: "Backup failed")
                }
            )
        }
    }

    fun restoreFromWebDav() {
        if (_uiState.value.syncState is SyncState.Working) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncState = SyncState.Working)
            restoreFromWebDavInternal(masterPassword = null, rememberForBiometric = false, fromBiometric = false)
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
            importPcConfigInternal(trimmed, masterPassword = null, rememberForBiometric = false, fromBiometric = false)
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

    fun submitCredentialPrompt(
        masterPassword: String,
        rememberForBiometric: Boolean = false,
        fromBiometric: Boolean = false
    ) {
        val prompt = _uiState.value.credentialPrompt ?: return
        if ((_uiState.value.syncState is SyncState.Working) || (_uiState.value.importState is ImportState.Importing)) {
            return
        }

        viewModelScope.launch {
            when (prompt.mode) {
                CredentialPromptMode.IMPORT_CONFIG -> {
                    val json = pendingImportJson ?: return@launch
                    _uiState.value = _uiState.value.copy(importState = ImportState.Importing)
                    importPcConfigInternal(json, masterPassword, rememberForBiometric, fromBiometric)
                }

                CredentialPromptMode.RESTORE_WEBDAV -> {
                    _uiState.value = _uiState.value.copy(syncState = SyncState.Working)
                    restoreFromWebDavInternal(masterPassword, rememberForBiometric, fromBiometric)
                }
            }
        }
    }

    fun dismissCredentialPrompt() {
        pendingImportJson = null
        _uiState.value = _uiState.value.copy(credentialPrompt = null)
    }

    fun updateCredentialPromptError(message: String) {
        val prompt = _uiState.value.credentialPrompt ?: return
        _uiState.value = _uiState.value.copy(
            credentialPrompt = prompt.copy(errorMessage = message)
        )
    }

    fun completeBiometricSave(cipher: Cipher) {
        val scope = pendingBiometricSaveScope
        val password = pendingBiometricSavePassword
        pendingBiometricSaveScope = null
        pendingBiometricSavePassword = null
        if (scope.isNullOrBlank() || password.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(biometricSaveRequest = null)
            return
        }

        try {
            BiometricMasterPasswordStore.saveSecret(appContext, scope, password, cipher)
            _uiState.value = _uiState.value.copy(biometricSaveRequest = null)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                biometricSaveRequest = null,
                syncState = SyncState.Error(e.message ?: "Failed to save biometric master password")
            )
        }
    }

    fun dismissBiometricSaveRequest() {
        pendingBiometricSaveScope = null
        pendingBiometricSavePassword = null
        _uiState.value = _uiState.value.copy(biometricSaveRequest = null)
    }

    private suspend fun importPcConfigInternal(
        jsonText: String,
        masterPassword: String?,
        rememberForBiometric: Boolean,
        fromBiometric: Boolean
    ) {
        try {
            val summary = withContext(Dispatchers.IO) {
                pcConfigImporter.importFromConfigJson(jsonText, masterPassword)
            }
            val scope = _uiState.value.credentialPrompt?.secretScope
            pendingImportJson = null
            _uiState.value = _uiState.value.copy(
                importState = ImportState.Success(summary),
                credentialPrompt = null
            )
            requestBiometricSaveIfNeeded(scope, masterPassword, rememberForBiometric)
        } catch (e: SimpleShellPcInvalidMasterPasswordException) {
            if (fromBiometric) {
                BiometricMasterPasswordStore.clearSecret(appContext, e.securityRandomKey)
            }
            pendingImportJson = jsonText
            _uiState.value = _uiState.value.copy(
                importState = ImportState.Idle,
                credentialPrompt = CredentialPromptState(
                    mode = CredentialPromptMode.IMPORT_CONFIG,
                    errorMessage = if (fromBiometric) {
                        "Saved fingerprint master password is invalid. Enter the master password again."
                    } else {
                        e.message ?: "Invalid master password"
                    },
                    secretScope = e.securityRandomKey
                )
            )
        } catch (e: SimpleShellPcCredentialLockedException) {
            pendingImportJson = jsonText
            _uiState.value = _uiState.value.copy(
                importState = ImportState.Idle,
                credentialPrompt = CredentialPromptState(
                    mode = CredentialPromptMode.IMPORT_CONFIG,
                    errorMessage = null,
                    secretScope = e.securityRandomKey
                )
            )
        } catch (e: Exception) {
            pendingImportJson = null
            _uiState.value = _uiState.value.copy(
                importState = ImportState.Error(e.message ?: "Import failed"),
                credentialPrompt = null
            )
        }
    }

    private suspend fun restoreFromWebDavInternal(
        masterPassword: String?,
        rememberForBiometric: Boolean,
        fromBiometric: Boolean
    ) {
        val result = webDavSyncManager.restore(masterPassword)
        val failure = result.exceptionOrNull()
        when (failure) {
            is SimpleShellPcInvalidMasterPasswordException -> {
                if (fromBiometric) {
                    BiometricMasterPasswordStore.clearSecret(appContext, failure.securityRandomKey)
                }
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Idle,
                    credentialPrompt = CredentialPromptState(
                        mode = CredentialPromptMode.RESTORE_WEBDAV,
                        errorMessage = if (fromBiometric) {
                            "Saved fingerprint master password is invalid. Enter the master password again."
                        } else {
                            failure.message ?: "Invalid master password"
                        },
                        secretScope = failure.securityRandomKey
                    )
                )
            }

            is SimpleShellPcCredentialLockedException -> {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Idle,
                    credentialPrompt = CredentialPromptState(
                        mode = CredentialPromptMode.RESTORE_WEBDAV,
                        errorMessage = null,
                        secretScope = failure.securityRandomKey
                    )
                )
            }

            null -> {
                val scope = _uiState.value.credentialPrompt?.secretScope
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Success("Restore from WebDAV successful"),
                    credentialPrompt = null
                )
                requestBiometricSaveIfNeeded(scope, masterPassword, rememberForBiometric)
            }

            else -> {
                _uiState.value = _uiState.value.copy(
                    syncState = SyncState.Error(failure.message ?: "Restore failed"),
                    credentialPrompt = null
                )
            }
        }
    }

    private fun requestBiometricSaveIfNeeded(
        scope: String?,
        masterPassword: String?,
        rememberForBiometric: Boolean
    ) {
        if (!rememberForBiometric || scope.isNullOrBlank() || masterPassword.isNullOrEmpty()) return
        if (!_uiState.value.biometricMasterPasswordEnabled) return

        pendingBiometricSaveScope = scope
        pendingBiometricSavePassword = masterPassword
        _uiState.value = _uiState.value.copy(
            biometricSaveRequest = BiometricSaveRequest(scope)
        )
    }
}
