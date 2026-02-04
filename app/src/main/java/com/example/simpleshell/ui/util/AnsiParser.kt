package com.example.simpleshell.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object AnsiParser {

    // Standard ANSI colors
    private val standardColors = mapOf(
        30 to Color(0xFF000000), // Black
        31 to Color(0xFFCD0000), // Red
        32 to Color(0xFF00CD00), // Green
        33 to Color(0xFFCDCD00), // Yellow
        34 to Color(0xFF0000EE), // Blue
        35 to Color(0xFFCD00CD), // Magenta
        36 to Color(0xFF00CDCD), // Cyan
        37 to Color(0xFFE5E5E5), // White
        // Bright colors
        90 to Color(0xFF7F7F7F), // Bright Black (Gray)
        91 to Color(0xFFFF0000), // Bright Red
        92 to Color(0xFF00FF00), // Bright Green
        93 to Color(0xFFFFFF00), // Bright Yellow
        94 to Color(0xFF5C5CFF), // Bright Blue
        95 to Color(0xFFFF00FF), // Bright Magenta
        96 to Color(0xFF00FFFF), // Bright Cyan
        97 to Color(0xFFFFFFFF)  // Bright White
    )
