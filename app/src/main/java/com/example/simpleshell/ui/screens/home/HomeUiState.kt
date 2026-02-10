package com.example.simpleshell.ui.screens.home

import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.ssh.ResourceStats

data class HomeUiState(
    val groups: List<GroupEntity> = emptyList(),
    val connections: List<ConnectionEntity> = emptyList(),
    val connectedTerminalConnectionIds: Set<Long> = emptySet(),
    val resourceStats: Map<Long, ResourceStats> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)
