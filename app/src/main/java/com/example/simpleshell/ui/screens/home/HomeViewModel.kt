package com.example.simpleshell.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadConnections()
    }

    private fun loadConnections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            combine(
                groupRepository.getAllGroups(),
                connectionRepository.getAllConnections()
            ) { groups, connections ->
                groups to connections
            }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
                .collect { (groups, connections) ->
                    _uiState.value = _uiState.value.copy(
                        groups = groups,
                        connections = connections,
                        isLoading = false,
                        error = null
                    )
                }
        }
    }

    fun deleteConnection(connection: ConnectionEntity) {
        viewModelScope.launch {
            connectionRepository.deleteConnection(connection)
        }
    }

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            // Avoid duplicate group names to reduce user confusion.
            if (groupRepository.groupNameExists(trimmed)) return@launch
            groupRepository.createGroup(trimmed)
        }
    }

    fun renameGroup(groupId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            groupRepository.renameGroup(groupId, trimmed)
        }
    }

    fun deleteGroup(group: GroupEntity) {
        viewModelScope.launch {
            // Keep the connections, but ungroup them.
            connectionRepository.clearGroupForConnections(group.id)
            groupRepository.deleteGroup(group)
        }
    }
}
