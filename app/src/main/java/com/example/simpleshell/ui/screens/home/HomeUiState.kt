package com.example.simpleshell.ui.screens.home

import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity

data class HomeUiState(
    val groups: List<GroupEntity> = emptyList(),
    val connections: List<ConnectionEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
