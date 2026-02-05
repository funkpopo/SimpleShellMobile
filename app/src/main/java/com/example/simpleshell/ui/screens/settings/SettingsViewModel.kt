package com.example.simpleshell.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.BuildConfig
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import com.example.simpleshell.data.remote.UpdateCheckResult
import com.example.simpleshell.data.remote.UpdateChecker
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val updateChecker: UpdateChecker
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
                        themeColor = prefs.themeColor
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
}

