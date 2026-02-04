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

    private val backgroundColors = mapOf(
        40 to Color(0xFF000000), // Black
        41 to Color(0xFFCD0000), // Red
        42 to Color(0xFF00CD00), // Green
        43 to Color(0xFFCDCD00), // Yellow
        44 to Color(0xFF0000EE), // Blue
        45 to Color(0xFFCD00CD), // Magenta
        46 to Color(0xFF00CDCD), // Cyan
        47 to Color(0xFFE5E5E5), // White
        // Bright backgrounds
        100 to Color(0xFF7F7F7F),
        101 to Color(0xFFFF0000),
        102 to Color(0xFF00FF00),
        103 to Color(0xFFFFFF00),
        104 to Color(0xFF5C5CFF),
        105 to Color(0xFFFF00FF),
        106 to Color(0xFF00FFFF),
        107 to Color(0xFFFFFFFF)
    )

    // Regex to match ANSI escape sequences
    private val ansiRegex = Regex("\u001B\\[([0-9;]*)m")

    private data class TextStyle(
        var foreground: Color = Color(0xFFE5E5E5),
        var background: Color? = null,
        var bold: Boolean = false
    )

    fun parse(text: String, defaultColor: Color = Color(0xFFE5E5E5)): AnnotatedString {
        return buildAnnotatedString {
            var currentStyle = TextStyle(foreground = defaultColor)
            var lastEnd = 0

            ansiRegex.findAll(text).forEach { match ->
                // Append text before this escape sequence
                if (match.range.first > lastEnd) {
                    val segment = text.substring(lastEnd, match.range.first)
                    appendStyledText(segment, currentStyle)
                }

                // Parse the escape sequence
                val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
                currentStyle = applyAnsiCodes(codes, currentStyle, defaultColor)

                lastEnd = match.range.last + 1
            }

            // Append remaining text
            if (lastEnd < text.length) {
                val segment = text.substring(lastEnd)
                appendStyledText(segment, currentStyle)
            }
        }
    }

    private fun AnnotatedString.Builder.appendStyledText(text: String, style: TextStyle) {
        val spanStyle = SpanStyle(
            color = style.foreground,
            background = style.background ?: Color.Transparent,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal
        )
        pushStyle(spanStyle)
        append(text)
        pop()
    }

    private fun applyAnsiCodes(codes: List<Int>, current: TextStyle, defaultColor: Color): TextStyle {
        val newStyle = current.copy()

        if (codes.isEmpty() || codes == listOf(0)) {
            // Reset
            return TextStyle(foreground = defaultColor)
        }

        for (code in codes) {
            when (code) {
                0 -> {
                    newStyle.foreground = defaultColor
                    newStyle.background = null
                    newStyle.bold = false
                }
                1 -> newStyle.bold = true
                22 -> newStyle.bold = false
                in 30..37, in 90..97 -> {
                    newStyle.foreground = standardColors[code] ?: defaultColor
                }
                39 -> newStyle.foreground = defaultColor
                in 40..47, in 100..107 -> {
                    newStyle.background = backgroundColors[code]
                }
                49 -> newStyle.background = null
            }
        }
        return newStyle
    }
}
