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
import kotlinx.coroutines.flow.update
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

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var saveFontScaleJob: Job? = null
    private val sessionJobs = mutableMapOf<String, Job>()

    init {
        observePreferences()
        loadConnectionAndInitialize()
    }

    private fun loadConnectionAndInitialize() {
        viewModelScope.launch {
            val entity = connectionRepository.getConnectionById(connectionId)
            if (entity != null) {
                _uiState.update { it.copy(connectionName = entity.name) }
            }
            
            // Get existing sessions or create one
            val existingSessions = terminalSessionManager.getSessionsForConnection(connectionId)
            if (existingSessions.isEmpty()) {
                createNewTab()
            } else {
                existingSessions.forEach { session ->
                    addTabForSession(session.sessionId)
                }
                _uiState.update { it.copy(currentSessionId = existingSessions.first().sessionId) }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            userPreferencesRepository.preferences
                .catch {
                    // If prefs are unreadable/corrupted, keep defaults.
                }
                .collect { prefs ->
                    val clamped = clampFontScale(prefs.terminalFontScale)
                    _uiState.update { it.copy(fontScale = clamped) }
                }
        }
    }

    fun createNewTab() {
        val session = terminalSessionManager.createSession(connectionId)
        addTabForSession(session.sessionId)
        _uiState.update { it.copy(currentSessionId = session.sessionId) }
        connectSession(session.sessionId)
    }

    private fun addTabForSession(sessionId: String) {
        val newTab = TerminalTabState(sessionId = sessionId)
        _uiState.update { state ->
            if (state.tabs.any { it.sessionId == sessionId }) state
            else state.copy(tabs = state.tabs + newTab)
        }
        observeTerminalSession(sessionId)
    }

    fun switchTab(sessionId: String) {
        if (_uiState.value.tabs.any { it.sessionId == sessionId }) {
            _uiState.update { it.copy(currentSessionId = sessionId) }
        }
    }

    fun closeTab(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            terminalSessionManager.disconnect(sessionId)
        }
        sessionJobs[sessionId]?.cancel()
        sessionJobs.remove(sessionId)

        _uiState.update { state ->
            val newTabs = state.tabs.filter { it.sessionId != sessionId }
            val newCurrent = if (state.currentSessionId == sessionId) {
                newTabs.lastOrNull()?.sessionId
            } else {
                state.currentSessionId
            }
            state.copy(tabs = newTabs, currentSessionId = newCurrent)
        }
    }

    private fun observeTerminalSession(sessionId: String) {
        val session = terminalSessionManager.getSession(sessionId) ?: return
        
        val job = viewModelScope.launch {
            launch {
                session.outputFlow.collect { output ->
                    updateTabState(sessionId) { it.copy(output = output) }
                }
            }
            launch {
                session.isConnected.collect { isConnected ->
                    updateTabState(sessionId) { 
                        it.copy(
                            isConnected = isConnected,
                            isConnecting = if (isConnected) false else it.isConnecting
                        )
                    }
                }
            }
            launch {
                session.sshSessionId.collect { sshSessionId ->
                    updateTabState(sessionId) { it.copy(sshSessionId = sshSessionId) }
                }
            }
            launch {
                session.connectedAtMs.collect { connectedAtMs ->
                    updateTabState(sessionId) { it.copy(connectedAtMs = connectedAtMs) }
                }
            }
        }
        sessionJobs[sessionId] = job
    }

    private fun updateTabState(sessionId: String, update: (TerminalTabState) -> TerminalTabState) {
        _uiState.update { state ->
            val newTabs = state.tabs.map { tab ->
                if (tab.sessionId == sessionId) update(tab) else tab
            }
            state.copy(tabs = newTabs)
        }
    }

    private fun connectSession(sessionId: String) {
        viewModelScope.launch {
            updateTabState(sessionId) { it.copy(isConnecting = true, error = null) }
            try {
                val entity = connectionRepository.getConnectionById(connectionId)
                if (entity == null) {
                    updateTabState(sessionId) { it.copy(isConnecting = false, error = "Connection not found") }
                    return@launch
                }

                val decryptedPassword = SimpleShellPcCryptoCompat.decryptNullableMaybe(entity.password)
                val decryptedPassphrase =
                    SimpleShellPcCryptoCompat.decryptNullableMaybe(entity.privateKeyPassphrase)
                val decryptedPrivateKey =
                    SimpleShellPcCryptoCompat.decryptNullableMaybe(entity.privateKey)

                val connection = Connection(
                    id = entity.id,
                    name = entity.name,
                    host = entity.host,
                    port = entity.port,
                    username = entity.username,
                    password = decryptedPassword,
                    privateKey = decryptedPrivateKey,
                    privateKeyPassphrase = decryptedPassphrase,
                    authType = if (entity.authType == "key")
                        Connection.AuthType.KEY else Connection.AuthType.PASSWORD
                )

                val didConnect = terminalSessionManager.connectIfNeeded(connection, sessionId)
                if (didConnect) {
                    connectionRepository.updateLastConnectedAt(connectionId)
                }

                updateTabState(sessionId) { it.copy(isConnecting = false) }
            } catch (e: Exception) {
                updateTabState(sessionId) { 
                    it.copy(isConnecting = false, error = "Connection failed: ${e.message}") 
                }
            }
        }
    }

    fun sendCommand(command: String) {
        val currentSessionId = _uiState.value.currentSessionId ?: return
        terminalSessionManager.getSession(currentSessionId)?.sendCommand(command)
    }

    fun sendInput(input: String) {
        val currentSessionId = _uiState.value.currentSessionId ?: return
        terminalSessionManager.getSession(currentSessionId)?.sendInput(input)
    }

    fun setFontScale(scale: Float) {
        val clamped = clampFontScale(scale)
        _uiState.update { it.copy(fontScale = clamped) }

        saveFontScaleJob?.cancel()
        saveFontScaleJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(250)
            userPreferencesRepository.setTerminalFontScale(clamped)
        }
    }

    fun reconnect(sessionId: String? = null) {
        val targetSessionId = sessionId ?: _uiState.value.currentSessionId ?: return
        val session = terminalSessionManager.getSession(targetSessionId) ?: return
        
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                session.disconnect()
            }
            session.clearOutput()
            connectSession(targetSessionId)
        }
    }

    private fun clampFontScale(raw: Float): Float {
        val min = 0.4f
        val max = 2.5f
        return raw.coerceIn(min, max)
    }
    
    override fun onCleared() {
        super.onCleared()
        sessionJobs.values.forEach { it.cancel() }
    }
}
