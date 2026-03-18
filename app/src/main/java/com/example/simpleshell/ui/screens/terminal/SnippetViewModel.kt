package com.example.simpleshell.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleshell.data.local.database.entity.SnippetEntity
import com.example.simpleshell.data.repository.SnippetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SnippetViewModel @Inject constructor(
    private val repository: SnippetRepository
) : ViewModel() {

    val snippets: StateFlow<List<SnippetEntity>> = repository.getAllSnippets()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addSnippet(name: String, content: String) {
        viewModelScope.launch {
            repository.insertSnippet(
                SnippetEntity(
                    name = name,
                    content = content
                )
            )
        }
    }

    fun updateSnippet(snippet: SnippetEntity, newName: String, newContent: String) {
        viewModelScope.launch {
            repository.updateSnippet(
                snippet.copy(name = newName, content = newContent)
            )
        }
    }

    fun deleteSnippet(snippet: SnippetEntity) {
        viewModelScope.launch {
            repository.deleteSnippet(snippet)
        }
    }
}
