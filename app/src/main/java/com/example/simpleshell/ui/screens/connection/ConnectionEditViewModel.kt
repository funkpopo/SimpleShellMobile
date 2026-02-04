package com.example.simpleshell.ui.screens.connection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.data.repository.GroupRepository
import com.example.simpleshell.domain.model.Connection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionEditViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val groupRepository: GroupRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>("connectionId") ?: -1

    private val _uiState = MutableStateFlow(ConnectionEditUiState())
    val uiState: StateFlow<ConnectionEditUiState> = _uiState.asStateFlow()

    init {
        observeGroups()
        if (connectionId > 0) {
            loadConnection()
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            groupRepository.getAllGroups()
                .catch {
                    _uiState.value = _uiState.value.copy(groups = emptyList())
                }
                .collect { groups ->
                    _uiState.value = _uiState.value.copy(groups = groups)
                }
        }
    }

    private fun loadConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val connection = connectionRepository.getConnectionById(connectionId)
                if (connection != null) {
                    _uiState.value = _uiState.value.copy(
                        name = connection.name,
                        groupId = connection.groupId,
                        host = connection.host,
                        port = connection.port.toString(),
                        username = connection.username,
                        password = connection.password ?: "",
                        privateKey = connection.privateKey ?: "",
                        privateKeyPassphrase = connection.privateKeyPassphrase ?: "",
                        authType = if (connection.authType == "key")
                            Connection.AuthType.KEY else Connection.AuthType.PASSWORD,
                        isLoading = false,
                        isEditMode = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateHost(host: String) {
        _uiState.value = _uiState.value.copy(host = host)
    }

    fun updateGroupId(groupId: Long?) {
        _uiState.value = _uiState.value.copy(groupId = groupId)
    }

    fun updatePort(port: String) {
        _uiState.value = _uiState.value.copy(port = port)
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun updatePrivateKey(privateKey: String) {
        _uiState.value = _uiState.value.copy(privateKey = privateKey)
    }

    fun updatePrivateKeyPassphrase(passphrase: String) {
        _uiState.value = _uiState.value.copy(privateKeyPassphrase = passphrase)
    }

    fun updateAuthType(authType: Connection.AuthType) {
        _uiState.value = _uiState.value.copy(authType = authType)
    }

    fun saveConnection() {
        val state = _uiState.value
        if (state.name.isBlank() || state.host.isBlank() || state.username.isBlank()) {
            _uiState.value = state.copy(error = "Please fill in all required fields")
            return
        }
        if (state.authType == Connection.AuthType.KEY && state.privateKey.isBlank()) {
            _uiState.value = state.copy(error = "Please provide a private key")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val entity = ConnectionEntity(
                    id = if (connectionId > 0) connectionId else 0,
                    name = state.name,
                    groupId = state.groupId,
                    host = state.host,
                    port = state.port.toIntOrNull() ?: 22,
                    username = state.username,
                    password = if (state.authType == Connection.AuthType.PASSWORD)
                        state.password else null,
                    privateKey = if (state.authType == Connection.AuthType.KEY)
                        state.privateKey else null,
                    privateKeyPassphrase = if (state.authType == Connection.AuthType.KEY)
                        state.privateKeyPassphrase.ifBlank { null } else null,
                    authType = if (state.authType == Connection.AuthType.KEY) "key" else "password"
                )

                if (connectionId > 0) {
                    connectionRepository.updateConnection(entity)
                } else {
                    connectionRepository.saveConnection(entity)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setError(message: String?) {
        _uiState.value = _uiState.value.copy(error = message)
    }
}
