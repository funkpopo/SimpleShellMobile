package com.example.simpleshell.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ShortcutItem(
    val label: String,
    val controlCode: String
)

private val shortcuts = listOf(
    ShortcutItem("Ctrl+C", "\u0003"),
    ShortcutItem("Ctrl+D", "\u0004"),
    ShortcutItem("Ctrl+Z", "\u001A"),
    ShortcutItem("Ctrl+L", "\u000C"),
    ShortcutItem("Tab", "\t"),
    ShortcutItem("↑", "\u001B[A"),
    ShortcutItem("↓", "\u001B[B"),
    ShortcutItem("←", "\u001B[D"),
    ShortcutItem("→", "\u001B[C"),
    ShortcutItem("Ctrl+A", "\u0001"),
    ShortcutItem("Ctrl+E", "\u0005"),
    ShortcutItem("Ctrl+U", "\u0015"),
    ShortcutItem("Ctrl+K", "\u000B"),
    ShortcutItem("Ctrl+W", "\u0017")
)

@Composable
fun ShortcutPanel(
    onSendInput: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Termux-style always-visible "extra keys" row (compact).
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        // Keep visual gaps tight; clickable Surfaces add extra invisible padding due to min-touch targets.
        // We use a non-clickable Surface + Modifier.clickable in ShortcutButton to avoid that.
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shortcuts.forEach { shortcut ->
            ShortcutButton(
                shortcut = shortcut,
                onClick = { onSendInput(shortcut.controlCode) }
            )
        }
    }
}

@Composable
private fun ShortcutButton(
    shortcut: ShortcutItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF1F1F1F),
        // Don't use Surface(onClick=...) here: Material applies minimum touch-target enforcement which
        // increases the *layout* size without increasing the painted background, causing large visual gaps.
        modifier = Modifier
            .height(34.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shortcut.label,
                color = Color(0xFF4CAF50),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
