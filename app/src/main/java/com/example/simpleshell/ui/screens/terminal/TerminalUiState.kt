package com.example.simpleshell.ui.screens.terminal

data class TerminalUiState(
    val output: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val sessionId: Long = 0,
    val connectedAtMs: Long? = null,
    val connectionName: String = "",
    val error: String? = null,
    /**
     * User-controlled scale for terminal output text. 1.0 = default.
     */
    val fontScale: Float = 1.0f
)
