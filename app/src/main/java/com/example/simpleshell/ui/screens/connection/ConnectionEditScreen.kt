package com.example.simpleshell.ui.screens.connection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalFocusManager
import com.example.simpleshell.R
import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.domain.model.PortForwardingRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConnectionEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val keyText = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
                if (!keyText.isNullOrBlank()) {
                    viewModel.updatePrivateKey(keyText)
                    viewModel.setError(null)
                } else {
                    viewModel.setError(context.getString(R.string.key_file_empty))
                }
            } catch (e: Exception) {
                viewModel.setError(context.getString(R.string.key_file_read_error, e.message ?: ""))
            }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show snackbar or handle error
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isEditMode) stringResource(R.string.edit_connection) else stringResource(R.string.new_connection))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // With edge-to-edge + IME, content can be obscured by the on-screen keyboard.
                // Adding IME padding keeps the bottom fields/buttons reachable while typing.
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.connection_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(
                expanded = groupMenuExpanded,
                onExpandedChange = { groupMenuExpanded = !groupMenuExpanded }
            ) {
                val ungroupedText = stringResource(R.string.ungrouped)
                val currentGroupName = uiState.groups.firstOrNull { it.id == uiState.groupId }?.name ?: ungroupedText

                OutlinedTextField(
                    value = currentGroupName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.group)) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupMenuExpanded)
                    }
                )

                ExposedDropdownMenu(
                    expanded = groupMenuExpanded,
                    onDismissRequest = { groupMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ungrouped)) },
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.updateGroupId(null)
                            groupMenuExpanded = false
                        }
                    )
                    uiState.groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.updateGroupId(group.id)
                                groupMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::updateHost,
                label = { Text(stringResource(R.string.host)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.host_placeholder)) }
            )

            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::updatePort,
                label = { Text(stringResource(R.string.port)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = { Text(stringResource(R.string.username)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = stringResource(R.string.auth_method),
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterChip(
                    selected = uiState.authType == Connection.AuthType.PASSWORD,
                    onClick = { viewModel.updateAuthType(Connection.AuthType.PASSWORD) },
                    label = { Text(stringResource(R.string.password)) }
                )
                FilterChip(
                    selected = uiState.authType == Connection.AuthType.KEY,
                    onClick = { viewModel.updateAuthType(Connection.AuthType.KEY) },
                    label = { Text(stringResource(R.string.private_key)) }
                )
            }

            if (uiState.authType == Connection.AuthType.PASSWORD) {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text(stringResource(R.string.password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.toggle_password_visibility)
                            )
                        }
                    }
                )
            } else {
                OutlinedTextField(
                    value = uiState.privateKey,
                    onValueChange = viewModel::updatePrivateKey,
                    label = { Text(stringResource(R.string.private_key_pem)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text(stringResource(R.string.private_key_placeholder)) }
                )

                OutlinedButton(
                    onClick = { keyFilePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.import_key_from_file))
                }

                OutlinedTextField(
                    value = uiState.privateKeyPassphrase,
                    onValueChange = viewModel::updatePrivateKeyPassphrase,
                    label = { Text(stringResource(R.string.key_passphrase)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Port Forwarding Rules",
                style = MaterialTheme.typography.titleMedium
            )

            uiState.portForwardingRules.forEachIndexed { index, rule ->
                PortForwardingRuleItem(
                    rule = rule,
                    onUpdate = { updatedRule -> viewModel.updatePortForwardingRule(index, updatedRule) },
                    onDelete = { viewModel.removePortForwardingRule(index) }
                )
            }

            OutlinedButton(
                onClick = {
                    viewModel.addPortForwardingRule(
                        PortForwardingRule(
                            type = PortForwardingRule.Type.LOCAL,
                            localPort = 8080,
                            remoteHost = "localhost",
                            remotePort = 80
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Port Forwarding Rule")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::saveConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (uiState.isEditMode) stringResource(R.string.update) else stringResource(R.string.save))
                }
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardingRuleItem(
    rule: PortForwardingRule,
    onUpdate: (PortForwardingRule) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = rule.type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        PortForwardingRule.Type.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    onUpdate(rule.copy(type = type))
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Rule")
                }
            }

            OutlinedTextField(
                value = rule.localPort.toString(),
                onValueChange = { onUpdate(rule.copy(localPort = it.toIntOrNull() ?: rule.localPort)) },
                label = { Text("Local Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (rule.type != PortForwardingRule.Type.DYNAMIC) {
                OutlinedTextField(
                    value = rule.remoteHost ?: "",
                    onValueChange = { onUpdate(rule.copy(remoteHost = it)) },
                    label = { Text("Remote Host") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = rule.remotePort?.toString() ?: "",
                    onValueChange = { onUpdate(rule.copy(remotePort = it.toIntOrNull())) },
                    label = { Text("Remote Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rule.isEnabled,
                    onCheckedChange = { onUpdate(rule.copy(isEnabled = it)) }
                )
                Text("Enabled")
            }
        }
    }
}
