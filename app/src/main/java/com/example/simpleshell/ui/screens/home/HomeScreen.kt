package com.example.simpleshell.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditConnection: (Long) -> Unit,
    onConnectTerminal: (Long) -> Unit,
    onConnectSftp: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var renameGroupTarget by remember { mutableStateOf<GroupEntity?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<GroupEntity?>(null) }

    if (showCreateGroupDialog) {
        GroupNameDialog(
            title = "新建分组",
            initialValue = "",
            confirmLabel = "创建",
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateGroupDialog = false
            }
        )
    }

    renameGroupTarget?.let { group ->
        GroupNameDialog(
            title = "重命名分组",
            initialValue = group.name,
            confirmLabel = "保存",
            onDismiss = { renameGroupTarget = null },
            onConfirm = { name ->
                viewModel.renameGroup(group.id, name)
                renameGroupTarget = null
            }
        )
    }

    deleteGroupTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteGroupTarget = null },
            title = { Text("删除分组") },
            text = { Text("确认删除分组“${group.name}”？该分组下的连接将变为未分组。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group)
                    deleteGroupTarget = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SimpleShell") },
                actions = {
                    IconButton(onClick = { showCreateGroupDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Add Group")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddConnection) {
                Icon(Icons.Default.Add, contentDescription = "Add Connection")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
                uiState.connections.isEmpty() && uiState.groups.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No connections yet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add a new connection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    val knownGroupIds = remember(uiState.groups) { uiState.groups.map { it.id }.toSet() }
                    val ungroupedConnections = remember(uiState.connections, knownGroupIds) {
                        uiState.connections.filter { it.groupId == null || it.groupId !in knownGroupIds }
                    }
                    val connectionsByGroup = remember(uiState.connections, knownGroupIds) {
                        uiState.connections
                            .filter { it.groupId != null && it.groupId in knownGroupIds }
                            // Safe because of the filter above; avoid `!!` for better readability and tooling support.
                            .groupBy { requireNotNull(it.groupId) { "groupId was null after filtering" } }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (ungroupedConnections.isNotEmpty()) {
                            item(key = "header_ungrouped") {
                                GroupHeader(
                                    title = "未分组",
                                    subtitle = "${ungroupedConnections.size} 个连接",
                                    isEditable = false,
                                    onRename = {},
                                    onDelete = {}
                                )
                            }
                            items(ungroupedConnections, key = { "conn_${it.id}" }) { connection ->
                                ConnectionRow(
                                    connection = connection,
                                    isTerminalConnected = connection.id in uiState.connectedTerminalConnectionIds,
                                    onDisconnectTerminal = { viewModel.disconnectTerminal(connection.id) },
                                    onEdit = { onEditConnection(connection.id) },
                                    onDelete = { viewModel.deleteConnection(connection) },
                                    onTerminal = { onConnectTerminal(connection.id) },
                                    onSftp = { onConnectSftp(connection.id) }
                                )
                            }
                            item(key = "spacer_ungrouped") { Spacer(modifier = Modifier.height(8.dp)) }
                        }

                        uiState.groups.forEach { group ->
                            val groupConnections = connectionsByGroup[group.id] ?: emptyList()
                            item(key = "header_group_${group.id}") {
                                GroupHeader(
                                    title = group.name,
                                    subtitle = "${groupConnections.size} 个连接",
                                    isEditable = true,
                                    onRename = { renameGroupTarget = group },
                                    onDelete = { deleteGroupTarget = group }
                                )
                            }

                            if (groupConnections.isEmpty()) {
                                item(key = "empty_group_${group.id}") {
                                    Text(
                                        text = "暂无连接",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                                    )
                                }
                            } else {
                                items(groupConnections, key = { "conn_${it.id}" }) { connection ->
                                    ConnectionRow(
                                        connection = connection,
                                        isTerminalConnected = connection.id in uiState.connectedTerminalConnectionIds,
                                        onDisconnectTerminal = { viewModel.disconnectTerminal(connection.id) },
                                        onEdit = { onEditConnection(connection.id) },
                                        onDelete = { viewModel.deleteConnection(connection) },
                                        onTerminal = { onConnectTerminal(connection.id) },
                                        onSftp = { onConnectSftp(connection.id) }
                                    )
                                }
                            }

                            item(key = "spacer_group_${group.id}") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: ConnectionEntity,
    isTerminalConnected: Boolean,
    onDisconnectTerminal: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTerminal: () -> Unit,
    onSftp: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to delete '${connection.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ListItem(
            leadingContent = {
                val dotColor = if (isTerminalConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(dotColor, CircleShape)
                )
            },
            headlineContent = {
                Text(
                    text = connection.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = "${connection.username}@${connection.host}:${connection.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTerminal) {
                        Icon(Icons.Default.Terminal, contentDescription = "Terminal")
                    }
                    IconButton(onClick = onSftp) {
                        Icon(Icons.Default.Folder, contentDescription = "SFTP")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (isTerminalConnected) {
                                DropdownMenuItem(
                                    text = { Text("断开连接") },
                                    onClick = {
                                        menuExpanded = false
                                        onDisconnectTerminal()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LinkOff, contentDescription = null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun GroupHeader(
    title: String,
    subtitle: String,
    isEditable: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isEditable) {
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename Group")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Group",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "提示：删除分组不会删除连接，只会取消分组。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.trim().isNotEmpty()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
