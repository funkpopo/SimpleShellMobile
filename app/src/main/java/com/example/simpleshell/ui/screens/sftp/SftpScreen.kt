package com.example.simpleshell.ui.screens.sftp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.service.ConnectionForegroundService
import com.example.simpleshell.domain.model.SftpFile
import com.example.simpleshell.ui.screens.sftp.SortOption
import com.example.simpleshell.ui.util.POST_NOTIFICATIONS_PERMISSION
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
    var showSortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var askedNotificationPermission by remember { mutableStateOf(false) }

    fun startConnectionService() {
        val intent = Intent(context, ConnectionForegroundService::class.java).apply {
            putExtra(
                ConnectionForegroundService.EXTRA_TITLE,
                uiState.connectionName.ifEmpty { "SFTP" }
            )
            putExtra(ConnectionForegroundService.EXTRA_SUBTITLE, "SFTP 已连接")
        }
        ContextCompat.startForegroundService(context, intent)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && uiState.isConnected) {
            startConnectionService()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadFile(it, context) }
    }

    var fileToDownload by remember { mutableStateOf<SftpFile?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { destUri ->
            fileToDownload?.let { file ->
                viewModel.downloadFile(file, destUri, context)
            }
        }
        fileToDownload = null
    }

    LaunchedEffect(uiState.isConnected, uiState.connectionName) {
        if (uiState.isConnected) {
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    POST_NOTIFICATIONS_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasNotificationPermission) {
                startConnectionService()
            } else {
                if (!askedNotificationPermission) {
                    askedNotificationPermission = true
                    notificationPermissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)
                }
            }
        }
    }

    BackHandler(enabled = uiState.isConnected) {
        if (uiState.editingFile != null) {
            viewModel.closeEditor()
        } else if (!viewModel.navigateUp()) {
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

    uiState.selectedFile?.let { file ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSelectedFile() },
            title = { Text(file.name) },
            text = { Text("What would you like to do with this file?") },
            confirmButton = {
                TextButton(onClick = {
                    fileToDownload = file
                    viewModel.clearSelectedFile()
                    // Start download
                    downloadLauncher.launch(file.name)
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearSelectedFile()
                    // Open editor
                    viewModel.openEditor(file, context)
                }) {
                    Text("Edit")
                }
            }
        )
    }

    if (uiState.editingFile != null) {
        var editedContent by remember(uiState.editingFileContent) { mutableStateOf(uiState.editingFileContent ?: "") }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Editing: ${uiState.editingFile!!.name}") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeEditor() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.saveEditedFile(editedContent, context) }) {
                            Icon(Icons.Default.Save, "Save")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                TextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.connectionName.ifEmpty { "SFTP" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort by Name" + if (uiState.sortOption == SortOption.NAME) (if (uiState.sortAscending) " ↑" else " ↓") else "") },
                            onClick = {
                                viewModel.setSortOption(SortOption.NAME)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Size" + if (uiState.sortOption == SortOption.SIZE) (if (uiState.sortAscending) " ↑" else " ↓") else "") },
                            onClick = {
                                viewModel.setSortOption(SortOption.SIZE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort by Date" + if (uiState.sortOption == SortOption.MODIFIED_TIME) (if (uiState.sortAscending) " ↑" else " ↓") else "") },
                            onClick = {
                                viewModel.setSortOption(SortOption.MODIFIED_TIME)
                                showSortMenu = false
                            }
                        )
                    }
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
        },
        floatingActionButton = {
            if (uiState.isConnected) {
                FloatingActionButton(onClick = { uploadLauncher.launch("*/*") }) {
                    Icon(Icons.Default.Upload, "Upload File")
                }
            }
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (uiState.isConnected) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.currentPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
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
                if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
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
