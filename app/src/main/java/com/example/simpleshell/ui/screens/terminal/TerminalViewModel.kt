package com.example.simpleshell.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.ssh.TerminalSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val terminalSession: TerminalSession,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>("connectionId") ?: -1

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        observeTerminalOutput()
        connect()
    }

    private fun observeTerminalOutput() {
        viewModelScope.launch {
            terminalSession.outputFlow.collect { output ->
                _uiState.value = _uiState.value.copy(output = output)
            }
        }
        viewModelScope.launch {
            terminalSession.isConnected.collect { isConnected ->
                _uiState.value = _uiState.value.copy(
                    isConnected = isConnected,
                    isConnecting = if (isConnected) false else _uiState.value.isConnecting
                )
            }
        }
        viewModelScope.launch {
            terminalSession.sessionId.collect { sessionId ->
                _uiState.value = _uiState.value.copy(sessionId = sessionId)
            }
        }
        viewModelScope.launch {
            terminalSession.connectedAtMs.collect { connectedAtMs ->
                _uiState.value = _uiState.value.copy(connectedAtMs = connectedAtMs)
            }
        }
    }

    private fun connect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, error = null)
            try {
                val entity = connectionRepository.getConnectionById(connectionId)
                if (entity == null) {
                    _uiState.value = _uiState.value.copy(
                        isConnecting = false,
                        error = "Connection not found"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(connectionName = entity.name)

                val connection = Connection(
                    id = entity.id,
                    name = entity.name,
                    host = entity.host,
                    port = entity.port,
                    username = entity.username,
                    password = entity.password,
                    privateKey = entity.privateKey,
                    privateKeyPassphrase = entity.privateKeyPassphrase,
                    authType = if (entity.authType == "key")
                        Connection.AuthType.KEY else Connection.AuthType.PASSWORD
                )

                terminalSession.connect(connection)
                connectionRepository.updateLastConnectedAt(connectionId)

                _uiState.value = _uiState.value.copy(isConnecting = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    error = "Connection failed: ${e.message}"
                )
            }
        }
    }

    fun sendCommand(command: String) {
        terminalSession.sendCommand(command)
    }

    fun sendInput(input: String) {
        terminalSession.sendInput(input)
    }

    fun reconnect() {
        terminalSession.disconnect()
        terminalSession.clearOutput()
        connect()
    }

    override fun onCleared() {
        super.onCleared()
        terminalSession.disconnect()
    }
}
