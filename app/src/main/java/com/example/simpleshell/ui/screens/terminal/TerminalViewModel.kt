package com.example.simpleshell.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.importing.SimpleShellPcCryptoCompat
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.ssh.TerminalSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val terminalSessionManager: TerminalSessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>("connectionId") ?: -1
    private val session = terminalSessionManager.getSession(connectionId)

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    init {
        observeTerminalOutput()
        connect()
    }

    private fun observeTerminalOutput() {
        viewModelScope.launch {
            session.outputFlow.collect { output ->
                _uiState.value = _uiState.value.copy(output = output)
            }
        }
        viewModelScope.launch {
            session.isConnected.collect { isConnected ->
                _uiState.value = _uiState.value.copy(
                    isConnected = isConnected,
                    isConnecting = if (isConnected) false else _uiState.value.isConnecting
                )
            }
        }
        viewModelScope.launch {
            session.sessionId.collect { sessionId ->
                _uiState.value = _uiState.value.copy(sessionId = sessionId)
            }
        }
        viewModelScope.launch {
            session.connectedAtMs.collect { connectedAtMs ->
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

                val decryptedPassword = SimpleShellPcCryptoCompat.decryptNullableMaybe(entity.password)
                val decryptedPassphrase =
                    SimpleShellPcCryptoCompat.decryptNullableMaybe(entity.privateKeyPassphrase)

                val connection = Connection(
                    id = entity.id,
                    name = entity.name,
                    host = entity.host,
                    port = entity.port,
                    username = entity.username,
                    password = decryptedPassword,
                    privateKey = entity.privateKey,
                    privateKeyPassphrase = decryptedPassphrase,
                    authType = if (entity.authType == "key")
                        Connection.AuthType.KEY else Connection.AuthType.PASSWORD
                )

                val didConnect = terminalSessionManager.connectIfNeeded(connection)
                if (didConnect) {
                    connectionRepository.updateLastConnectedAt(connectionId)
                }

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
        session.sendCommand(command)
    }

    fun sendInput(input: String) {
        session.sendInput(input)
    }

    fun reconnect() {
        // Disconnect may block; do it on IO to avoid ANR.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                session.disconnect()
            }
            session.clearOutput()
            connect()
        }
    }
}
