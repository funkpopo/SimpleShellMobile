package com.example.simpleshell.ui.screens.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ShortcutItem(
    val label: String,
    val description: String,
    val controlCode: String
)

private val shortcuts = listOf(
    ShortcutItem("Ctrl+C", "中断", "\u0003"),
    ShortcutItem("Ctrl+D", "退出", "\u0004"),
    ShortcutItem("Ctrl+Z", "挂起", "\u001A"),
    ShortcutItem("Ctrl+L", "清屏", "\u000C"),
    ShortcutItem("Tab", "补全", "\t"),
    ShortcutItem("↑", "上条命令", "\u001B[A"),
    ShortcutItem("↓", "下条命令", "\u001B[B"),
    ShortcutItem("←", "光标左移", "\u001B[D"),
    ShortcutItem("→", "光标右移", "\u001B[C"),
    ShortcutItem("Ctrl+A", "行首", "\u0001"),
    ShortcutItem("Ctrl+E", "行尾", "\u0005"),
    ShortcutItem("Ctrl+U", "删至行首", "\u0015"),
    ShortcutItem("Ctrl+K", "删至行尾", "\u000B"),
    ShortcutItem("Ctrl+W", "删前词", "\u0017")
)

@Composable
fun ShortcutPanel(
    onSendInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
    ) {
        // Toggle bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "快捷键",
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isExpanded) "收起" else "展开",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(16.dp)
            )
        }

        // Shortcut buttons
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                shortcuts.forEach { shortcut ->
                    ShortcutButton(
                        shortcut = shortcut,
                        onClick = { onSendInput(shortcut.controlCode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutButton(
    shortcut: ShortcutItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF3D3D3D),
        modifier = Modifier.height(40.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = shortcut.label,
                color = Color(0xFF4CAF50),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = shortcut.description,
                color = Color(0xFF888888),
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
