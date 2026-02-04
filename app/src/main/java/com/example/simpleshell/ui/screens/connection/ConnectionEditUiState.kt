package com.example.simpleshell.ui.screens.connection

import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.domain.model.Connection

data class ConnectionEditUiState(
    val name: String = "",
    val groupId: Long? = null,
    val groups: List<GroupEntity> = emptyList(),
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
    val privateKeyPassphrase: String = "",
    val authType: Connection.AuthType = Connection.AuthType.PASSWORD,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val isEditMode: Boolean = false
)
