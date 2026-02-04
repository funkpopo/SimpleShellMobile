package com.example.simpleshell.ui.screens.home

import com.example.simpleshell.data.local.database.entity.ConnectionEntity

data class HomeUiState(
    val connections: List<ConnectionEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
