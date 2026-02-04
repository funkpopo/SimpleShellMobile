package com.example.simpleshell.ui.screens.terminal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simpleshell.service.ConnectionForegroundService
import com.example.simpleshell.ui.util.AnsiParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // 用于捕获键盘输入的缓冲区
    var inputBuffer by remember { mutableStateOf("") }
    var lastSentLength by remember { mutableIntStateOf(0) }
    var isResetting by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var askedNotificationPermission by remember { mutableStateOf(false) }

    fun startConnectionService() {
        val intent = Intent(context, ConnectionForegroundService::class.java).apply {
            putExtra(
                ConnectionForegroundService.EXTRA_TITLE,
                uiState.connectionName.ifEmpty { "Terminal" }
            )
            putExtra(ConnectionForegroundService.EXTRA_SUBTITLE, "Terminal 已连接")
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopConnectionService() {
        context.stopService(Intent(context, ConnectionForegroundService::class.java))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && uiState.isConnected) {
            startConnectionService()
        }
    }

    LaunchedEffect(uiState.isConnected, uiState.connectionName) {
        if (uiState.isConnected) {
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasNotificationPermission) {
                startConnectionService()
            } else {
                if (!askedNotificationPermission) {
                    askedNotificationPermission = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            stopConnectionService()
        }
    }

    // 闪烁光标动画
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    LaunchedEffect(uiState.output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val titleText = if (uiState.sessionId > 0) {
                            "${uiState.connectionName.ifEmpty { "Terminal" }}  #${uiState.sessionId}"
                        } else {
                            uiState.connectionName.ifEmpty { "Terminal" }
                        }
                        Text(
                            titleText,
                            maxLines = 1
                        )
                        if (uiState.isConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reconnect() }) {
                        Icon(Icons.Default.Refresh, "Reconnect")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            when {
                uiState.isConnecting -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connecting...", color = Color.White)
                        }
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                uiState.error ?: "Unknown error",
                                color = Color(0xFFFF5252)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.reconnect() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {
                    // Terminal area with integrated input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(6.dp)
                        ) {
                            if (!uiState.isConnected) {
                                Text(
                                    text = "Disconnected \u2014 tap Reconnect",
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // 终端输出文本，末尾带闪烁光标
                            val textWithCursor = remember(uiState.output) {
                                buildAnnotatedString {
                                    val parsed = if (uiState.output.isEmpty()) {
                                        AnsiParser.parse("Waiting for output...", Color(0xFFAAAAAA))
                                    } else {
                                        AnsiParser.parse(uiState.output, Color(0xFFE5E5E5))
                                    }
                                    append(parsed)
                                    appendInlineContent("cursor", "[█]")
                                }
                            }

                            val cursorInlineContent = mapOf(
                                "cursor" to InlineTextContent(
                                    Placeholder(
                                        width = 10.sp,
                                        height = 16.sp,
                                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .height(16.dp)
                                            .background(Color(0xFF4CAF50).copy(alpha = cursorAlpha))
                                    )
                                }
                            )

                            Text(
                                text = textWithCursor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                inlineContent = cursorInlineContent
                            )

                            // 隐藏的输入框，用于捕获键盘输入并实时发送到终端
                            BasicTextField(
                                value = inputBuffer,
                                onValueChange = { newValue ->
                                    if (isResetting) {
                                        inputBuffer = newValue
                                        if (newValue.isEmpty()) {
                                            isResetting = false
                                            lastSentLength = 0
                                        }
                                        return@BasicTextField
                                    }

                                    when {
                                        newValue.length > lastSentLength -> {
                                            // 用户输入了新字符，发送到终端
                                            val newChars = newValue.substring(lastSentLength)
                                            viewModel.sendInput(newChars)
                                            lastSentLength = newValue.length
                                        }
                                        newValue.length < lastSentLength -> {
                                            // 用户删除了字符，发送退格
                                            val deletedCount = lastSentLength - newValue.length
                                            repeat(deletedCount) {
                                                viewModel.sendInput("\u007F") // DEL字符
                                            }
                                            lastSentLength = newValue.length
                                        }
                                    }
                                    inputBuffer = newValue
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .height(1.dp), // 最小高度，几乎不可见
                                textStyle = TextStyle(
                                    color = Color.Transparent, // 文字透明
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 1.sp
                                ),
                                cursorBrush = SolidColor(Color.Transparent), // 光标透明
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        viewModel.sendInput("\n") // 发送换行
                                        isResetting = true
                                        inputBuffer = ""
                                        lastSentLength = 0
                                    }
                                ),
                                singleLine = true
                            )
                        }
                    }

                    // Shortcut panel at the bottom
                    ShortcutPanel(
                        onSendInput = { input -> viewModel.sendInput(input) }
                    )
                }
            }
        }
    }
}
