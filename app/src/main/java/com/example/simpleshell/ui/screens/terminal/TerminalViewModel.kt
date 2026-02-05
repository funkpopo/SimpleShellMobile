package com.example.simpleshell.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.importing.SimpleShellPcCryptoCompat
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.ssh.TerminalSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val terminalSessionManager: TerminalSessionManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>("connectionId") ?: -1
    private val session = terminalSessionManager.getSession(connectionId)

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var saveFontScaleJob: Job? = null

    init {
        observePreferences()
        observeTerminalOutput()
        connect()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.preferences
                .catch {
                    // If prefs are unreadable/corrupted, keep defaults.
                }
                .collect { prefs ->
                    val clamped = clampFontScale(prefs.terminalFontScale)
                    _uiState.value = _uiState.value.copy(fontScale = clamped)
                }
        }
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

    fun setFontScale(scale: Float) {
        val clamped = clampFontScale(scale)
        _uiState.value = _uiState.value.copy(fontScale = clamped)

        // Persist with a small debounce so pinch gestures don't spam DataStore edits.
        saveFontScaleJob?.cancel()
        saveFontScaleJob = viewModelScope.launch(Dispatchers.IO) {
            // A short delay is enough to coalesce multiple quick updates.
            kotlinx.coroutines.delay(250)
            userPreferencesRepository.setTerminalFontScale(clamped)
        }
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

    private fun clampFontScale(raw: Float): Float {
        // Keep the range conservative: too small becomes unreadable; too large breaks layout.
        val min = 0.4f
        val max = 2.5f
        return raw.coerceIn(min, max)
    }
}
