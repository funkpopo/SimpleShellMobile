package com.example.simpleshell.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.BuildConfig
import com.example.simpleshell.R
import com.example.simpleshell.data.remote.ReleaseInfo
import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode
import com.example.simpleshell.utils.BiometricHelper
import com.example.simpleshell.utils.BiometricMasterPasswordStore
import androidx.fragment.app.FragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val isBiometricStrongAvailable = remember { BiometricHelper.isBiometricStrongAvailable(context) }

    uiState.biometricSaveRequest?.let { request ->
        val saveTitle = stringResource(R.string.pc_config_master_password_save_biometric_title)
        val saveSubtitle = stringResource(R.string.pc_config_master_password_save_biometric_subtitle)
        LaunchedEffect(request.secretScope) {
            val cryptoObject = runCatching {
                BiometricMasterPasswordStore.createEncryptionCryptoObject(request.secretScope)
            }.getOrNull()
            if (cryptoObject == null) {
                viewModel.dismissBiometricSaveRequest()
                return@LaunchedEffect
            }

            BiometricHelper.showBiometricCryptoPrompt(
                activity = activity,
                cryptoObject = cryptoObject,
                title = saveTitle,
                subtitle = saveSubtitle,
                onSuccess = { result ->
                    val cipher = result.cryptoObject?.cipher
                    if (cipher != null) {
                        viewModel.completeBiometricSave(cipher)
                    } else {
                        viewModel.dismissBiometricSaveRequest()
                    }
                },
                onError = { _, _ -> viewModel.dismissBiometricSaveRequest() },
                onFailed = { }
            )
        }
    }

    val configFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }.orEmpty()

                if (jsonText.isBlank()) {
                    viewModel.reportImportError(context.getString(R.string.import_pc_config_empty))
                } else {
                    viewModel.importPcConfig(jsonText)
                }
            } catch (e: Exception) {
                viewModel.reportImportError(
                    context.getString(R.string.import_pc_config_read_error, e.message ?: "")
                )
            }
        }
    }

    val configExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPcConfig(context.contentResolver, uri)
        }
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            current = uiState.themeMode,
            onDismiss = { showThemeDialog = false },
            onSelected = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = uiState.language,
            onDismiss = { showLanguageDialog = false },
            onSelected = { language ->
                viewModel.setLanguage(language)
                showLanguageDialog = false
            }
        )
    }

    if (showWebDavDialog) {
        WebDavDialog(
            url = uiState.webDavUrl,
            username = uiState.webDavUsername,
            password = uiState.webDavPassword,
            onDismiss = { showWebDavDialog = false },
            onSave = { url, user, pass ->
                viewModel.setWebDavUrl(url)
                viewModel.setWebDavUsername(user)
                viewModel.setWebDavPassword(pass)
                showWebDavDialog = false
            },
            onBackup = { viewModel.backupToWebDav() },
            onRestore = { viewModel.restoreFromWebDav() }
        )
    }

    when (val updateState = uiState.updateCheckState) {
        is UpdateCheckState.NewVersionAvailable -> {
            UpdateAvailableDialog(
                releaseInfo = updateState.releaseInfo,
                onDismiss = { viewModel.dismissUpdateDialog() },
                onDownload = {
                    uriHandler.openUri(updateState.releaseInfo.htmlUrl)
                    viewModel.dismissUpdateDialog()
                }
            )
        }
        is UpdateCheckState.AlreadyLatest -> {
            AlreadyLatestDialog(onDismiss = { viewModel.dismissUpdateDialog() })
        }
        is UpdateCheckState.Error -> {
            UpdateErrorDialog(
                message = updateState.message,
                onDismiss = { viewModel.dismissUpdateDialog() }
            )
        }
        else -> {}
    }

    when (val syncState = uiState.syncState) {
        is SyncState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSyncDialog() },
                title = { Text(stringResource(R.string.sync_package_result_title)) },
                text = { Text(syncState.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissSyncDialog() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is SyncState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSyncDialog() },
                title = { Text(stringResource(R.string.sync_package_error_title)) },
                text = { Text(syncState.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissSyncDialog() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        else -> Unit
    }

    when (val importState = uiState.importState) {
        is ImportState.Success -> {
            val summary = importState.summary
            AlertDialog(
                onDismissRequest = { viewModel.dismissImportDialog() },
                title = { Text(stringResource(R.string.import_pc_config_success_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.import_pc_config_success_message,
                            summary.importedConnections,
                            summary.importedPasswordConnections,
                            summary.importedKeyConnections,
                            summary.createdGroups,
                            summary.skippedConnections
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissImportDialog() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is ImportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissImportDialog() },
                title = { Text(stringResource(R.string.import_pc_config_error_title)) },
                text = { Text(importState.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissImportDialog() }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        else -> Unit
    }

    uiState.credentialPrompt?.let { prompt ->
        var masterPassword by remember(prompt.mode, prompt.errorMessage) { mutableStateOf("") }
        var rememberWithFingerprint by remember(prompt.mode, prompt.errorMessage, prompt.secretScope) {
            mutableStateOf(false)
        }
        val canUseStoredFingerprintPassword =
            uiState.biometricMasterPasswordEnabled &&
                isBiometricStrongAvailable &&
                prompt.secretScope != null &&
                BiometricMasterPasswordStore.hasStoredSecret(context, prompt.secretScope)
        val canSaveWithFingerprint =
            uiState.biometricMasterPasswordEnabled &&
                isBiometricStrongAvailable &&
                prompt.secretScope != null
        val unlockTitle = stringResource(R.string.pc_config_master_password_use_biometric_title)
        val unlockSubtitle = stringResource(R.string.pc_config_master_password_use_biometric_subtitle)
        AlertDialog(
            onDismissRequest = { viewModel.dismissCredentialPrompt() },
            title = { Text(stringResource(R.string.pc_config_master_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when (prompt.mode) {
                            CredentialPromptMode.IMPORT_CONFIG ->
                                stringResource(R.string.pc_config_master_password_import_message)
                            CredentialPromptMode.RESTORE_WEBDAV ->
                                stringResource(R.string.pc_config_master_password_restore_message)
                        }
                    )

                    if (!prompt.errorMessage.isNullOrBlank()) {
                        Text(
                            text = prompt.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    OutlinedTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it },
                        label = { Text(stringResource(R.string.pc_config_master_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (canUseStoredFingerprintPassword) {
                        TextButton(
                            onClick = {
                                val scope = prompt.secretScope ?: return@TextButton
                                val cryptoObject = BiometricMasterPasswordStore
                                    .createDecryptionCryptoObject(context, scope)
                                if (cryptoObject == null) {
                                    viewModel.updateCredentialPromptError(
                                        context.getString(R.string.pc_config_master_password_biometric_unavailable)
                                    )
                                    return@TextButton
                                }

                                BiometricHelper.showBiometricCryptoPrompt(
                                    activity = activity,
                                    cryptoObject = cryptoObject,
                                    title = unlockTitle,
                                    subtitle = unlockSubtitle,
                                    onSuccess = { result ->
                                        val cipher = result.cryptoObject?.cipher
                                        if (cipher == null) {
                                            viewModel.updateCredentialPromptError(
                                                context.getString(R.string.pc_config_master_password_biometric_unavailable)
                                            )
                                            return@showBiometricCryptoPrompt
                                        }

                                        try {
                                            val savedPassword = BiometricMasterPasswordStore.decryptSecret(
                                                context,
                                                scope,
                                                cipher
                                            )
                                            viewModel.submitCredentialPrompt(
                                                masterPassword = savedPassword,
                                                rememberForBiometric = false,
                                                fromBiometric = true
                                            )
                                        } catch (_: Exception) {
                                            BiometricMasterPasswordStore.clearSecret(context, scope)
                                            viewModel.updateCredentialPromptError(
                                                context.getString(R.string.pc_config_master_password_biometric_unavailable)
                                            )
                                        }
                                    },
                                    onError = { _, message ->
                                        viewModel.updateCredentialPromptError(message.toString())
                                    },
                                    onFailed = { }
                                )
                            }
                        ) {
                            Text(stringResource(R.string.pc_config_master_password_use_biometric))
                        }
                    }

                    if (canSaveWithFingerprint) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rememberWithFingerprint = !rememberWithFingerprint }
                        ) {
                            Checkbox(
                                checked = rememberWithFingerprint,
                                onCheckedChange = { rememberWithFingerprint = it }
                            )
                            Text(
                                text = stringResource(R.string.pc_config_master_password_remember_biometric),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.submitCredentialPrompt(
                            masterPassword = masterPassword,
                            rememberForBiometric = rememberWithFingerprint,
                            fromBiometric = false
                        )
                    },
                    enabled = masterPassword.isNotBlank()
                ) {
                    Text(stringResource(R.string.pc_config_master_password_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCredentialPrompt() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.appearance),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme)) },
                    supportingContent = { Text(themeModeLabel(uiState.themeMode)) },
                    leadingContent = {
                        Icon(Icons.Default.Palette, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeDialog = true }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language)) },
                    supportingContent = { Text(languageLabel(uiState.language)) },
                    leadingContent = {
                        Icon(Icons.Default.Language, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageDialog = true }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.dynamic_color_desc)) },
                    leadingContent = {
                        Icon(Icons.Default.Palette, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor
                        )
                    }
                )
            }

            item {
                AnimatedVisibility(
                    visible = !uiState.dynamicColor,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.theme_color)) },
                        supportingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp)
                            ) {
                                ThemeColor.entries.forEach { color ->
                                    ThemeColorItem(
                                        color = color,
                                        isSelected = color == uiState.themeColor,
                                        onClick = { viewModel.setThemeColor(color) }
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Default.ColorLens, contentDescription = null)
                        }
                    )
                }
            }

            item {
                val isBiometricAvailable = remember { BiometricHelper.isBiometricAvailable(context) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.fingerprint_unlock)) },
                    supportingContent = { 
                        Text(
                            if (isBiometricAvailable) stringResource(R.string.fingerprint_unlock_desc)
                            else stringResource(R.string.fingerprint_unlock_not_supported)
                        ) 
                    },
                    leadingContent = {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.fingerprintUnlockEnabled && isBiometricAvailable,
                            onCheckedChange = viewModel::setFingerprintUnlockEnabled,
                            enabled = isBiometricAvailable
                        )
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.fingerprint_master_password_unlock)) },
                    supportingContent = {
                        Text(
                            if (isBiometricStrongAvailable) {
                                stringResource(R.string.fingerprint_master_password_unlock_desc)
                            } else {
                                stringResource(R.string.fingerprint_unlock_not_supported)
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.biometricMasterPasswordEnabled && isBiometricStrongAvailable,
                            onCheckedChange = viewModel::setBiometricMasterPasswordEnabled,
                            enabled = isBiometricStrongAvailable
                        )
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.data),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("WebDAV Sync") },
                    supportingContent = { Text("Configure WebDAV for backup and restore") },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWebDavDialog = true }
                )
            }

            item {
                val isImporting = uiState.importState is ImportState.Importing
                val syncWorking = uiState.syncState is SyncState.Working
                ListItem(
                    headlineContent = { Text(stringResource(R.string.import_pc_config)) },
                    supportingContent = { Text(stringResource(R.string.import_pc_config_desc)) },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                    trailingContent = {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isImporting && !syncWorking) {
                            configFilePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                        }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_pc_config)) },
                    supportingContent = { Text(stringResource(R.string.export_pc_config_desc)) },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                    trailingContent = {
                        if (syncWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isImporting && !syncWorking) {
                            configExportPicker.launch("config-${System.currentTimeMillis()}.json")
                        }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("SimpleShell") },
                    supportingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                            if (uiState.updateCheckState is UpdateCheckState.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.checkForUpdate() },
                                enabled = uiState.updateCheckState !is UpdateCheckState.Checking
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.check_update)
                                )
                            }
                            Icon(
                                imageVector = if (aboutExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (aboutExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { aboutExpanded = !aboutExpanded }
                )
            }

            item {
                AnimatedVisibility(
                    visible = aboutExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.author)) },
                            supportingContent = { Text("Funkpopo") },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.project_url)) },
                            supportingContent = { Text("github.com/funkpopo/simpleshellmobile") },
                            leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    uriHandler.openUri("https://github.com/funkpopo/simpleshellmobile")
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onSelected: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (mode == current),
                            onClick = { onSelected(mode) }
                        )
                        Text(themeModeLabel(mode))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }
}

@Composable
private fun languageLabel(language: Language): String {
    return when (language) {
        Language.SYSTEM -> stringResource(R.string.language_system)
        Language.ENGLISH -> stringResource(R.string.language_english)
        Language.CHINESE -> stringResource(R.string.language_chinese)
    }
}

@Composable
private fun LanguageDialog(
    current: Language,
    onDismiss: () -> Unit,
    onSelected: (Language) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Language.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(language) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (language == current),
                            onClick = { onSelected(language) }
                        )
                        Text(languageLabel(language))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ThemeColorItem(
    color: ThemeColor,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val displayColor = when (color) {
        ThemeColor.PURPLE -> Color(0xFF6650a4)
        ThemeColor.BLUE -> Color(0xFF1976D2)
        ThemeColor.GREEN -> Color(0xFF388E3C)
        ThemeColor.ORANGE -> Color(0xFFF57C00)
        ThemeColor.RED -> Color(0xFFD32F2F)
        ThemeColor.TEAL -> Color(0xFF00796B)
        ThemeColor.PINK -> Color(0xFFC2185B)
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(displayColor)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.selected),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    releaseInfo: ReleaseInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_version_found)) },
        text = {
            Column {
                Text(stringResource(R.string.latest_version, releaseInfo.tagName))
                if (releaseInfo.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.update_content),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = releaseInfo.body.take(500),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.go_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.later))
            }
        }
    )
}

@Composable
private fun AlreadyLatestDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_update_title)) },
        text = { Text(stringResource(R.string.already_latest)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_update_failed)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
private fun WebDavDialog(
    url: String,
    username: String,
    password: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    var editUrl by remember { mutableStateOf(url) }
    var editUsername by remember { mutableStateOf(username) }
    var editPassword by remember { mutableStateOf(password) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV Sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editUrl,
                    onValueChange = { editUrl = it },
                    label = { Text("Server URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editPassword,
                    onValueChange = { editPassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onBackup) {
                        Text("Backup")
                    }
                    TextButton(onClick = onRestore) {
                        Text("Restore")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(editUrl, editUsername, editPassword) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
