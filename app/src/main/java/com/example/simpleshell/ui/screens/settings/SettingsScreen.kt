package com.example.simpleshell.ui.screens.settings

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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Code
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.BuildConfig
import com.example.simpleshell.data.remote.ReleaseInfo
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    text = "外观",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("主题") },
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
                    headlineContent = { Text("动态配色 (Android 12+)") },
                    supportingContent = { Text("跟随系统壁纸颜色生成配色") },
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
                        headlineContent = { Text("主题颜色") },
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
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    text = "关于",
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
                            Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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
                                    contentDescription = "检查更新"
                                )
                            }
                            Icon(
                                imageVector = if (aboutExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (aboutExpanded) "收起" else "展开"
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
                            headlineContent = { Text("作者") },
                            supportingContent = { Text("Funkpopo") },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        ListItem(
                            headlineContent = { Text("项目地址") },
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
        title = { Text("选择主题") },
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
                Text("关闭")
            }
        }
    )
}

private fun themeModeLabel(mode: ThemeMode): String {
    return when (mode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }
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
                contentDescription = "已选择",
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
        title = { Text("发现新版本") },
        text = {
            Column {
                Text("最新版本: ${releaseInfo.tagName}")
                if (releaseInfo.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "更新内容:",
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
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        }
    )
}

@Composable
private fun AlreadyLatestDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("检查更新") },
        text = { Text("当前已是最新版本") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
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
        title = { Text("检查更新失败") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

