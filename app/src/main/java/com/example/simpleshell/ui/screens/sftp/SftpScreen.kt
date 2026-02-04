package com.example.simpleshell.ui.screens.sftp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.domain.model.SftpFile
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreen(
    onNavigateBack: () -> Unit,
    viewModel: SftpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<SftpFile?>(null) }

    BackHandler(enabled = uiState.isConnected) {
        if (!viewModel.navigateUp()) {
            onNavigateBack()
        }
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                viewModel.createDirectory(name)
                showNewFolderDialog = false
            }
        )
    }

    showDeleteDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${if (file.isDirectory) "Folder" else "File"}") },
            text = { Text("Are you sure you want to delete '${file.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFile(file)
                    showDeleteDialog = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.connectionName.ifEmpty { "SFTP" })
                        Text(
                            uiState.currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, "New Folder")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isConnecting -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting...")
                    }
                }
                uiState.error != null && !uiState.isConnected -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            uiState.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.reconnect() }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.files, key = { it.path }) { file ->
                            FileListItem(
                                file = file,
                                onClick = { viewModel.navigateTo(file) },
                                onDelete = { showDeleteDialog = file }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileListItem(
    file: SftpFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (file.isDirectory)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row {
                Text(
                    formatFileSize(file.size),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    formatDate(file.modifiedTime),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}