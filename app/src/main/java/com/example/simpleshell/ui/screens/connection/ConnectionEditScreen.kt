package com.example.simpleshell.ui.screens.connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.domain.model.Connection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConnectionEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

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
                    Text(if (uiState.isEditMode) "Edit Connection" else "New Connection")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("Connection Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::updateHost,
                label = { Text("Host *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("example.com or 192.168.1.1") }
            )

            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::updatePort,
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = { Text("Username *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "Authentication Method",
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterChip(
                    selected = uiState.authType == Connection.AuthType.PASSWORD,
                    onClick = { viewModel.updateAuthType(Connection.AuthType.PASSWORD) },
                    label = { Text("Password") }
                )
                FilterChip(
                    selected = uiState.authType == Connection.AuthType.KEY,
                    onClick = { viewModel.updateAuthType(Connection.AuthType.KEY) },
                    label = { Text("Private Key") }
                )
            }

            if (uiState.authType == Connection.AuthType.PASSWORD) {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    }
                )
            } else {
                OutlinedTextField(
                    value = uiState.privateKey,
                    onValueChange = viewModel::updatePrivateKey,
                    label = { Text("Private Key (PEM format)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----") }
                )

                OutlinedTextField(
                    value = uiState.privateKeyPassphrase,
                    onValueChange = viewModel::updatePrivateKeyPassphrase,
                    label = { Text("Key Passphrase (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
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
                    Text(if (uiState.isEditMode) "Update" else "Save")
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
