package com.example.simpleshell.ui.screens.sftp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.importing.SimpleShellPcCryptoCompat
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.domain.model.SftpFile
import com.example.simpleshell.ssh.SftpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SftpViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val sftpManager: SftpManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val connectionId: Long = savedStateHandle.get<Long>("connectionId") ?: -1

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()
    private val pathHistory = mutableListOf<String>()

    companion object {
        private const val MAX_PATH_HISTORY = 128
    }

    init {
        observeConnectionState()
        connect()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            sftpManager.isConnectedFlow.collect { isConnected ->
                _uiState.value = _uiState.value.copy(
                    isConnected = isConnected,
                    isConnecting = if (isConnected) false else _uiState.value.isConnecting
                )
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

                sftpManager.connect(connection)
                connectionRepository.updateLastConnectedAt(connectionId)

                // Default to filesystem root instead of "~/" or "/root" for a more predictable UX.
                val homePath = "/"
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    currentPath = homePath
                )
                pathHistory.add(homePath)
                loadFiles(homePath)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    error = "Connection failed: ${e.message}"
                )
            }
        }
    }

    private fun loadFiles(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val files = sftpManager.listFiles(path)
                _uiState.value = _uiState.value.copy(
                    files = files,
                    currentPath = path,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load files: ${e.message}"
                )
            }
        }
    }

    fun navigateTo(file: SftpFile) {
        if (file.isDirectory) {
            if (pathHistory.size >= MAX_PATH_HISTORY) {
                pathHistory.removeAt(0)
            }
            pathHistory.add(file.path)
            loadFiles(file.path)
        } else {
            _uiState.value = _uiState.value.copy(selectedFile = file)
        }
    }

    fun navigateUp(): Boolean {
        if (pathHistory.size > 1) {
            // Avoid calling java.util.List#removeLast which requires newer API levels.
            pathHistory.removeAt(pathHistory.lastIndex)
            val previousPath = pathHistory.last()
            loadFiles(previousPath)
            return true
        }
        return false
    }

    fun refresh() {
        loadFiles(_uiState.value.currentPath)
    }

    fun clearSelectedFile() {
        _uiState.value = _uiState.value.copy(selectedFile = null)
    }

    fun deleteFile(file: SftpFile) {
        viewModelScope.launch {
            try {
                if (file.isDirectory) {
                    sftpManager.deleteDirectory(file.path)
                } else {
                    sftpManager.deleteFile(file.path)
                }
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete: ${e.message}"
                )
            }
        }
    }

    fun createDirectory(name: String) {
        viewModelScope.launch {
            try {
                val currentPath = _uiState.value.currentPath.trimEnd('/')
                val newPath = "$currentPath/$name"
                sftpManager.createDirectory(newPath)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create directory: ${e.message}"
                )
            }
        }
    }

    fun reconnect() {
        sftpManager.disconnect()
        pathHistory.clear()
        _uiState.value = SftpUiState()
        connect()
    }

    override fun onCleared() {
        super.onCleared()
        // Run on a background thread since viewModelScope is already cancelled at this point
        // and disconnect() may perform blocking I/O.
        Thread { sftpManager.disconnect() }.start()
    }
}
