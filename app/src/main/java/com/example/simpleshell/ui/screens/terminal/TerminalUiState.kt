package com.example.simpleshell.ui.screens.terminal

data class TerminalUiState(
    val output: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionName: String = "",
    val error: String? = null
)
