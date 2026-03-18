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
                    files = sortFiles(files, _uiState.value.sortOption, _uiState.value.sortAscending),
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

    fun setSortOption(option: SortOption) {
        val ascending = if (_uiState.value.sortOption == option) {
            !_uiState.value.sortAscending
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(
            sortOption = option,
            sortAscending = ascending,
            files = sortFiles(_uiState.value.files, option, ascending)
        )
    }

    private fun sortFiles(files: List<SftpFile>, option: SortOption, ascending: Boolean): List<SftpFile> {
        val comparator = when (option) {
            SortOption.NAME -> compareBy<SftpFile> { it.name.lowercase() }
            SortOption.SIZE -> compareBy { it.size }
            SortOption.MODIFIED_TIME -> compareBy { it.modifiedTime }
        }
        
        val finalComparator = if (ascending) comparator else comparator.reversed()
        
        // Always keep directories at the top
        return files.sortedWith(compareBy<SftpFile> { !it.isDirectory }.then(finalComparator))
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

    fun uploadFile(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Copy URI to temp file
                val tempFile = java.io.File(context.cacheDir, getFileName(context, uri) ?: "upload_tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val currentPath = _uiState.value.currentPath.trimEnd('/')
                val remotePath = "$currentPath/${tempFile.name}"
                sftpManager.uploadFile(tempFile.absolutePath, remotePath)
                tempFile.delete()
                
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to upload file: ${e.message}"
                )
            }
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result
    }

    fun downloadFile(file: SftpFile, destUri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tempFile = java.io.File(context.cacheDir, file.name)
                sftpManager.downloadFile(file.path, tempFile.absolutePath)
                
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                tempFile.delete()
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to download file: ${e.message}"
                )
            }
        }
    }

    fun openEditor(file: SftpFile, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (file.size > 1024 * 1024) { // 1MB limit
                    throw Exception("File is too large to edit")
                }
                val tempFile = java.io.File(context.cacheDir, file.name)
                sftpManager.downloadFile(file.path, tempFile.absolutePath)
                val content = tempFile.readText()
                tempFile.delete()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    editingFile = file,
                    editingFileContent = content
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to open editor: ${e.message}"
                )
            }
        }
    }
    
    fun saveEditedFile(content: String, context: android.content.Context) {
        val file = _uiState.value.editingFile ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val tempFile = java.io.File(context.cacheDir, file.name)
                tempFile.writeText(content)
                sftpManager.uploadFile(tempFile.absolutePath, file.path)
                tempFile.delete()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    editingFile = null,
                    editingFileContent = null
                )
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to save file: ${e.message}"
                )
            }
        }
    }
    
    fun closeEditor() {
        _uiState.value = _uiState.value.copy(
            editingFile = null,
            editingFileContent = null
        )
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
