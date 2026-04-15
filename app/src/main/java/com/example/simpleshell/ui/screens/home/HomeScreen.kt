package com.example.simpleshell.ui.screens.home

import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.R
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.ssh.ResourceStats

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
    // Group expansion state is managed locally (UI concern). Default is "all collapsed" by leaving the map empty.
    val expandedGroupState = remember { mutableStateMapOf<Long, Boolean>() }
    val ungroupedGroupId = Long.MIN_VALUE

    fun isGroupExpanded(groupId: Long): Boolean = expandedGroupState[groupId] ?: false
    fun toggleGroupExpanded(groupId: Long) {
        expandedGroupState[groupId] = !(expandedGroupState[groupId] ?: false)
    }

    if (showCreateGroupDialog) {
        GroupNameDialog(
            title = stringResource(R.string.new_group),
            initialValue = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name ->
                viewModel.createGroup(name)
                showCreateGroupDialog = false
            }
        )
    }

    renameGroupTarget?.let { group ->
        GroupNameDialog(
            title = stringResource(R.string.rename_group),
            initialValue = group.name,
            confirmLabel = stringResource(R.string.save),
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
            title = { Text(stringResource(R.string.delete_group)) },
            text = { Text(stringResource(R.string.delete_group_confirm, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group)
                    deleteGroupTarget = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showCreateGroupDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.add_group))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddConnection) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_connection))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = uiState,
                label = "HomeContentAnimation",
                transitionSpec = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                }
            ) { state ->
                when {
                    state.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.error_message, state.error ?: ""),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    state.connections.isEmpty() && state.groups.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.no_connections),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.tap_to_add_connection),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        val knownGroupIds = remember(state.groups) { state.groups.map { it.id }.toSet() }
                        val ungroupedConnections = remember(state.connections, knownGroupIds) {
                            state.connections.filter { it.groupId == null || it.groupId !in knownGroupIds }
                        }
                        val connectionsByGroup = remember(state.connections, knownGroupIds) {
                            state.connections
                                .filter { it.groupId != null && it.groupId in knownGroupIds }
                                .groupBy { requireNotNull(it.groupId) { "groupId was null after filtering" } }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (ungroupedConnections.isNotEmpty()) {
                                val isUngroupedExpanded = isGroupExpanded(ungroupedGroupId)
                                item(key = "header_ungrouped") {
                                    GroupHeader(
                                        modifier = Modifier.animateItem(),
                                        title = stringResource(R.string.ungrouped),
                                        subtitle = stringResource(R.string.connections_count, ungroupedConnections.size),
                                        isEditable = false,
                                        isExpanded = isUngroupedExpanded,
                                        onToggleExpanded = { toggleGroupExpanded(ungroupedGroupId) },
                                        onRename = {},
                                        onDelete = {}
                                    )
                                }
                                if (isUngroupedExpanded) {
                                    items(ungroupedConnections, key = { "conn_${it.id}" }) { connection ->
                                        ConnectionRow(
                                            modifier = Modifier.animateItem(),
                                            connection = connection,
                                            isTerminalConnected = connection.id in state.connectedTerminalConnectionIds,
                                            resourceStats = state.resourceStats[connection.id],
                                            onDisconnectTerminal = { viewModel.disconnectTerminal(connection.id) },
                                            onEdit = { onEditConnection(connection.id) },
                                            onDelete = { viewModel.deleteConnection(connection) },
                                            onTerminal = { onConnectTerminal(connection.id) },
                                            onSftp = { onConnectSftp(connection.id) }
                                        )
                                    }
                                }
                                item(key = "spacer_ungrouped") { Spacer(modifier = Modifier.animateItem().height(8.dp)) }
                            }

                            state.groups.forEach { group ->
                                val groupConnections = connectionsByGroup[group.id] ?: emptyList()
                                val isExpanded = isGroupExpanded(group.id)
                                item(key = "header_group_${group.id}") {
                                    GroupHeader(
                                        modifier = Modifier.animateItem(),
                                        title = group.name,
                                        subtitle = stringResource(R.string.connections_count, groupConnections.size),
                                        isEditable = true,
                                        isExpanded = isExpanded,
                                        onToggleExpanded = { toggleGroupExpanded(group.id) },
                                        onRename = { renameGroupTarget = group },
                                        onDelete = { deleteGroupTarget = group }
                                    )
                                }

                                if (isExpanded) {
                                    if (groupConnections.isEmpty()) {
                                        item(key = "empty_group_${group.id}") {
                                            Text(
                                                text = stringResource(R.string.no_connections_in_group),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.animateItem().padding(start = 16.dp, bottom = 8.dp)
                                            )
                                        }
                                    } else {
                                        items(groupConnections, key = { "conn_${it.id}" }) { connection ->
                                            ConnectionRow(
                                                modifier = Modifier.animateItem(),
                                                connection = connection,
                                                isTerminalConnected = connection.id in state.connectedTerminalConnectionIds,
                                                resourceStats = state.resourceStats[connection.id],
                                                onDisconnectTerminal = { viewModel.disconnectTerminal(connection.id) },
                                                onEdit = { onEditConnection(connection.id) },
                                                onDelete = { viewModel.deleteConnection(connection) },
                                                onTerminal = { onConnectTerminal(connection.id) },
                                                onSftp = { onConnectSftp(connection.id) }
                                            )
                                        }
                                    }
                                }

                                item(key = "spacer_group_${group.id}") {
                                    Spacer(modifier = Modifier.animateItem().height(8.dp))
                                }
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
    modifier: Modifier = Modifier,
    connection: ConnectionEntity,
    isTerminalConnected: Boolean,
    resourceStats: ResourceStats?,
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
            title = { Text(stringResource(R.string.delete_connection)) },
            text = { Text(stringResource(R.string.delete_connection_confirm, connection.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isTerminalConnected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.terminal))
                    }
                    IconButton(onClick = onSftp) {
                        Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.sftp))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (isTerminalConnected) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.disconnect)) },
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
                                text = { Text(stringResource(R.string.edit)) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
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

        if (isTerminalConnected && resourceStats != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ResourceBars(stats = resourceStats)
        }
    }
}

@Composable
private fun ResourceBars(stats: ResourceStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ResourceBarRow(
            label = stringResource(R.string.resource_cpu),
            percent = stats.cpuPercent,
            detail = "%.1f%%".format(stats.cpuPercent)
        )
        ResourceBarRow(
            label = stringResource(R.string.resource_mem),
            percent = stats.memPercent,
            detail = "%.1f%%  %s".format(stats.memPercent, stats.memDisplayString())
        )
    }
}

@Composable
private fun ResourceBarRow(
    label: String,
    percent: Float,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = when {
                percent > 90f -> MaterialTheme.colorScheme.error
                percent > 70f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun GroupHeader(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    isEditable: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isEditable) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_group))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_group),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.delete_group_hint),
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
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
