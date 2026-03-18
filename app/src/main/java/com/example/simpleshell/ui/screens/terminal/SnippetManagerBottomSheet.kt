package com.example.simpleshell.ui.screens.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.data.local.database.entity.SnippetEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetManagerBottomSheet(
    onDismiss: () -> Unit,
    onSnippetSelected: (String) -> Unit,
    viewModel: SnippetViewModel = hiltViewModel()
) {
    val snippets by viewModel.snippets.collectAsState()
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<SnippetEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Snippets / Scripts",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = {
                    editingSnippet = null
                    showAddEditDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Snippet")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (snippets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No snippets saved yet.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(snippets) { snippet ->
                        SnippetItem(
                            snippet = snippet,
                            onClick = {
                                onSnippetSelected(snippet.content)
                                onDismiss()
                            },
                            onEdit = {
                                editingSnippet = snippet
                                showAddEditDialog = true
                            },
                            onDelete = {
                                viewModel.deleteSnippet(snippet)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditSnippetDialog(
            snippet = editingSnippet,
            onDismiss = { showAddEditDialog = false },
            onSave = { name, content ->
                if (editingSnippet == null) {
                    viewModel.addSnippet(name, content)
                } else {
                    viewModel.updateSnippet(editingSnippet!!, name, content)
                }
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun SnippetItem(
    snippet: SnippetEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snippet.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = snippet.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun AddEditSnippetDialog(
    snippet: SnippetEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(snippet?.name ?: "") }
    var content by remember { mutableStateOf(snippet?.content ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (snippet == null) "Add Snippet" else "Edit Snippet")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Command / Script") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, content) },
                enabled = name.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
