package com.example.simpleshell.ui.screens.sftp

import com.example.simpleshell.domain.model.SftpFile

data class SftpUiState(
    val currentPath: String = "/",
    val files: List<SftpFile> = emptyList(),
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionName: String = "",
    val error: String? = null,
    val selectedFile: SftpFile? = null
)
