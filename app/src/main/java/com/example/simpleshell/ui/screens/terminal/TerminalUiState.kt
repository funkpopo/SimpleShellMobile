package com.example.simpleshell.ui.screens.terminal

data class TerminalTabState(
    val sessionId: String,
    val output: String = "",
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val sshSessionId: Long = 0,
    val connectedAtMs: Long? = null,
    val error: String? = null
)

data class TerminalUiState(
    val connectionName: String = "",
    val tabs: List<TerminalTabState> = emptyList(),
    val currentSessionId: String? = null,
    /**
     * User-controlled scale for terminal output text. 1.0 = default.
     */
    val fontScale: Float = 1.0f
) {
    val currentTab: TerminalTabState?
        get() = tabs.find { it.sessionId == currentSessionId }
}
