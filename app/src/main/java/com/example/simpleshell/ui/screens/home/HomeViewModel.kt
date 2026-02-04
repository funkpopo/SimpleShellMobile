package com.example.simpleshell.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadConnections()
    }

    private fun loadConnections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            connectionRepository.getAllConnections()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
                .collect { connections ->
                    _uiState.value = _uiState.value.copy(
                        connections = connections,
                        isLoading = false,
                        error = null
                    )
                }
        }
    }

    fun deleteConnection(connection: ConnectionEntity) {
        viewModelScope.launch {
            connectionRepository.deleteConnection(connection)
        }
    }
}
