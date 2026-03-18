package com.example.simpleshell.ui.screens.connection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.importing.SimpleShellPcCryptoCompat
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.PortForwardingEntity
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.data.repository.GroupRepository
import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.domain.model.PortForwardingRule
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
                    val decryptedPassword = SimpleShellPcCryptoCompat.decryptNullableMaybe(connection.password).orEmpty()
                    val decryptedPassphrase =
                        SimpleShellPcCryptoCompat.decryptNullableMaybe(connection.privateKeyPassphrase).orEmpty()
                    val decryptedPrivateKey =
                        SimpleShellPcCryptoCompat.decryptNullableMaybe(connection.privateKey)
                    
                    val rules = connectionRepository.getPortForwardingRulesOnce(connectionId).map {
                        PortForwardingRule(
                            id = it.id,
                            connectionId = it.connectionId,
                            type = PortForwardingRule.Type.valueOf(it.type),
                            localPort = it.localPort,
                            remoteHost = it.remoteHost,
                            remotePort = it.remotePort,
                            isEnabled = it.isEnabled
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        name = connection.name,
                        groupId = connection.groupId,
                        host = connection.host,
                        port = connection.port.toString(),
                        username = connection.username,
                        password = decryptedPassword,
                        privateKey = decryptedPrivateKey.orEmpty(),
                        privateKeyPassphrase = decryptedPassphrase,
                        authType = if (connection.authType == "key")
                            Connection.AuthType.KEY else Connection.AuthType.PASSWORD,
                        portForwardingRules = rules,
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

    fun addPortForwardingRule(rule: PortForwardingRule) {
        _uiState.value = _uiState.value.copy(
            portForwardingRules = _uiState.value.portForwardingRules + rule
        )
    }

    fun updatePortForwardingRule(index: Int, rule: PortForwardingRule) {
        val rules = _uiState.value.portForwardingRules.toMutableList()
        if (index in rules.indices) {
            rules[index] = rule
            _uiState.value = _uiState.value.copy(portForwardingRules = rules)
        }
    }

    fun removePortForwardingRule(index: Int) {
        val rules = _uiState.value.portForwardingRules.toMutableList()
        if (index in rules.indices) {
            rules.removeAt(index)
            _uiState.value = _uiState.value.copy(portForwardingRules = rules)
        }
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

                val rulesEntities = state.portForwardingRules.map {
                    PortForwardingEntity(
                        id = it.id,
                        connectionId = entity.id,
                        type = it.type.name,
                        localPort = it.localPort,
                        remoteHost = it.remoteHost,
                        remotePort = it.remotePort,
                        isEnabled = it.isEnabled
                    )
                }

                if (connectionId > 0) {
                    connectionRepository.updateConnection(entity, rulesEntities)
                } else {
                    connectionRepository.saveConnection(entity, rulesEntities)
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
