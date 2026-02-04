package com.example.simpleshell.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object AnsiParser {

    // xterm 16-color palette. This matches the old hardcoded colors and also provides a base
    // for 256-color/truecolor support.
    private val xterm16 = listOf(
        Color(0xFF000000), // 0: Black
        Color(0xFFCD0000), // 1: Red
        Color(0xFF00CD00), // 2: Green
        Color(0xFFCDCD00), // 3: Yellow
        Color(0xFF0000EE), // 4: Blue
        Color(0xFFCD00CD), // 5: Magenta
        Color(0xFF00CDCD), // 6: Cyan
        Color(0xFFE5E5E5), // 7: White
        Color(0xFF7F7F7F), // 8: Bright Black (Gray)
        Color(0xFFFF0000), // 9: Bright Red
        Color(0xFF00FF00), // 10: Bright Green
        Color(0xFFFFFF00), // 11: Bright Yellow
        Color(0xFF5C5CFF), // 12: Bright Blue
        Color(0xFFFF00FF), // 13: Bright Magenta
        Color(0xFF00FFFF), // 14: Bright Cyan
        Color(0xFFFFFFFF)  // 15: Bright White
    )

    private data class TextStyle(
        var foreground: Color = Color(0xFFE5E5E5),
        var background: Color? = null,
        var bold: Boolean = false,
        var dim: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strikethrough: Boolean = false,
        var inverse: Boolean = false,
        var concealed: Boolean = false
    )

    fun parse(text: String, defaultColor: Color = Color(0xFFE5E5E5)): AnnotatedString {
        var currentStyle = TextStyle(foreground = defaultColor)

        // We need to keep the current line buffered so we can handle '\r' progress updates.
        // Collected chunks are applied at the very end to create an AnnotatedString.
        data class Chunk(val style: TextStyle, val text: StringBuilder = StringBuilder())

        val output = ArrayList<Chunk>(64)
        val line = ArrayList<Chunk>(16)
        val buffer = StringBuilder()

        fun addChunk(chunks: MutableList<Chunk>, style: TextStyle, text: CharSequence) {
            if (text.isEmpty()) return
            if (chunks.isNotEmpty() && chunks.last().style == style) {
                chunks.last().text.append(text)
            } else {
                chunks.add(Chunk(style.copy(), StringBuilder().append(text)))
            }
        }

        fun flushBufferToLine() {
            if (buffer.isEmpty()) return
            addChunk(line, currentStyle, buffer)
            buffer.setLength(0)
        }

        fun clearLine() {
            line.clear()
            buffer.setLength(0)
        }

        fun clearAll() {
            output.clear()
            clearLine()
        }

        fun commitLine(withNewline: Boolean) {
            flushBufferToLine()
            for (chunk in line) {
                if (output.isNotEmpty() && output.last().style == chunk.style) {
                    output.last().text.append(chunk.text)
                } else {
                    output.add(chunk)
                }
            }
            line.clear()
            if (withNewline) {
                addChunk(output, currentStyle, "\n")
            }
        }

        fun handleBackspace() {
            if (buffer.isNotEmpty()) {
                buffer.setLength(buffer.length - 1)
                return
            }
            if (line.isEmpty()) return
            val last = line.last()
            if (last.text.isNotEmpty()) {
                last.text.setLength(last.text.length - 1)
                if (last.text.isEmpty()) {
                    line.removeAt(line.lastIndex)
                }
            }
        }

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when (ch) {
                '\u001B' -> {
                    val next = if (i + 1 < text.length) text[i + 1] else null
                    when (next) {
                        '[' -> {
                            val seqEnd = findCsiSequenceEnd(text, i + 2)
                            if (seqEnd == -1) break

                            val finalChar = text[seqEnd]
                            val params = text.substring(i + 2, seqEnd)

                            when (finalChar) {
                                'm' -> {
                                    flushBufferToLine()
                                    val codes = parseParameters(params)
                                    currentStyle = applySgr(codes, currentStyle, defaultColor)
                                }
                                // Clear screen: e.g. `clear` usually prints ESC[H ESC[2J
                                'J' -> {
                                    val mode = parseParameters(params).firstOrNull() ?: 0
                                    if (mode == 2 || mode == 3) {
                                        clearAll()
                                    }
                                }
                                // Erase in line. Often used with progress updates.
                                'K' -> {
                                    val mode = parseParameters(params).firstOrNull() ?: 0
                                    if (mode == 1 || mode == 2) {
                                        clearLine()
                                    }
                                }
                                // Other CSI sequences (cursor movement, etc.) are ignored/stripped.
                            }
                            i = seqEnd + 1
                        }
                        ']' -> {
                            // OSC: ESC ] ... BEL  or  ESC ] ... ESC \
                            val oscEnd = findOscSequenceEnd(text, i + 2)
                            if (oscEnd == -1) break
                            i = oscEnd
                        }
                        else -> {
                            // Other ESC sequences. Many are 2 or 3 bytes (e.g. ESC(B, ESC)0, ESC7).
                            i += when (next) {
                                '(', ')', '*', '+', '-', '.', '/', '#' -> if (i + 2 < text.length) 3 else (text.length - i)
                                null -> 1
                                'c' -> {
                                    // RIS (Reset to Initial State) - treat as "clear + reset style"
                                    clearAll()
                                    currentStyle = TextStyle(foreground = defaultColor)
                                    2
                                }
                                else -> 2
                            }
                        }
                    }
                }
                '\r' -> {
                    // Handle CRLF as newline; otherwise treat CR as "return to line start".
                    if (i + 1 < text.length && text[i + 1] == '\n') {
                        commitLine(withNewline = true)
                        i += 2
                    } else {
                        // Keep only the latest content on this line (common for progress bars).
                        clearLine()
                        i++
                    }
                }
                '\n' -> {
                    commitLine(withNewline = true)
                    i++
                }
                '\b', '\u007F' -> {
                    handleBackspace()
                    i++
                }
                else -> {
                    val code = ch.code
                    if (isIgnoredControlChar(code)) {
                        i++
                    } else {
                        buffer.append(ch)
                        i++
                    }
                }
            }
        }

        // Flush remaining buffered text at the end.
        commitLine(withNewline = false)

        return buildAnnotatedString {
            for (chunk in output) {
                pushStyle(chunk.style.toSpanStyle(defaultColor))
                append(chunk.text.toString())
                pop()
            }
        }
    }

    private fun isIgnoredControlChar(code: Int): Boolean {
        // Keep: TAB (0x09), LF (0x0A), CR (0x0D handled separately).
        // Ignore: BEL, ESC, etc.
        return (code in 0x00..0x08) ||
            code == 0x0B ||
            code == 0x0C ||
            (code in 0x0E..0x1F) ||
            code == 0x7F
    }

    private fun findCsiSequenceEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            val code = text[i].code
            // CSI final bytes are 0x40..0x7E (see ECMA-48).
            if (code in 0x40..0x7E) return i
            i++
        }
        return -1
    }

    private fun findOscSequenceEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            when (text[i]) {
                '\u0007' -> return i + 1 // BEL terminator
                '\u001B' -> {
                    if (i + 1 < text.length && text[i + 1] == '\\') {
                        return i + 2 // ST terminator (ESC \)
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun parseParameters(params: String): List<Int> {
        if (params.isEmpty()) return emptyList()

        val result = ArrayList<Int>(8)
        var value = 0
        var inNumber = false

        fun emit() {
            result.add(if (inNumber) value else 0)
            value = 0
            inNumber = false
        }

        for (ch in params) {
            when {
                ch in '0'..'9' -> {
                    value = (value * 10) + (ch - '0')
                    inNumber = true
                }
                ch == ';' || ch == ':' -> emit()
                else -> {
                    // Treat any unexpected bytes as separators. This also keeps us safe from
                    // variants that include other parameter bytes.
                    emit()
                }
            }
        }
        emit()
        return result
    }

    private fun applySgr(codes: List<Int>, current: TextStyle, defaultColor: Color): TextStyle {
        // Empty params is equivalent to "0" (reset).
        if (codes.isEmpty()) return TextStyle(foreground = defaultColor)

        val style = current.copy()

        var i = 0
        while (i < codes.size) {
            val code = codes[i]
            when (code) {
                0 -> {
                    style.foreground = defaultColor
                    style.background = null
                    style.bold = false
                    style.dim = false
                    style.italic = false
                    style.underline = false
                    style.strikethrough = false
                    style.inverse = false
                    style.concealed = false
                }
                1 -> style.bold = true
                2 -> style.dim = true
                3 -> style.italic = true
                4, 21 -> style.underline = true
                7 -> style.inverse = true
                8 -> style.concealed = true
                9 -> style.strikethrough = true

                22 -> {
                    style.bold = false
                    style.dim = false
                }
                23 -> style.italic = false
                24 -> style.underline = false
                27 -> style.inverse = false
                28 -> style.concealed = false
                29 -> style.strikethrough = false

                in 30..37 -> style.foreground = xterm16[code - 30]
                in 90..97 -> style.foreground = xterm16[(code - 90) + 8]
                39 -> style.foreground = defaultColor

                in 40..47 -> style.background = xterm16[code - 40]
                in 100..107 -> style.background = xterm16[(code - 100) + 8]
                49 -> style.background = null

                // Extended colors:
                // - 38;5;<n> / 48;5;<n> for xterm 256 palette
                // - 38;2;<r>;<g>;<b> / 48;2;<r>;<g>;<b> for truecolor
                38, 48 -> {
                    val isForeground = code == 38
                    val mode = codes.getOrNull(i + 1)
                    when (mode) {
                        5 -> {
                            val idx = codes.getOrNull(i + 2)
                            if (idx != null) {
                                val color = xterm256Color(idx)
                                if (isForeground) style.foreground = color else style.background = color
                                i += 2
                            }
                        }
                        2 -> {
                            val r = codes.getOrNull(i + 2)
                            val g = codes.getOrNull(i + 3)
                            val b = codes.getOrNull(i + 4)
                            if (r != null && g != null && b != null) {
                                val color = rgbColor(r, g, b)
                                if (isForeground) style.foreground = color else style.background = color
                                i += 4
                            }
                        }
                    }
                }
            }
            i++
        }

        return style
    }

    private fun rgbColor(r: Int, g: Int, b: Int): Color {
        val rr = r.coerceIn(0, 255)
        val gg = g.coerceIn(0, 255)
        val bb = b.coerceIn(0, 255)
        val argb = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        return Color(argb)
    }

    private fun xterm256Color(index: Int): Color {
        val idx = index.coerceIn(0, 255)
        if (idx < 16) return xterm16[idx]

        if (idx in 16..231) {
            val cube = idx - 16
            val r = cube / 36
            val g = (cube % 36) / 6
            val b = cube % 6
            val levels = intArrayOf(0, 95, 135, 175, 215, 255)
            return rgbColor(levels[r], levels[g], levels[b])
        }

        // grayscale ramp 232..255
        val gray = 8 + (idx - 232) * 10
        return rgbColor(gray, gray, gray)
    }

    private fun TextStyle.toSpanStyle(defaultColor: Color): SpanStyle {
        var fg = foreground
        var bg = background

        if (inverse) {
            val invFg = bg ?: defaultColor
            val invBg = fg
            fg = invFg
            bg = invBg
        }

        if (concealed) {
            fg = bg ?: Color.Transparent
        }

        if (dim) {
            fg = fg.copy(alpha = fg.alpha * 0.6f)
        }

        val decorations = ArrayList<TextDecoration>(2)
        if (underline) decorations.add(TextDecoration.Underline)
        if (strikethrough) decorations.add(TextDecoration.LineThrough)
        val decoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations)

        return SpanStyle(
            color = fg,
            background = bg ?: Color.Transparent,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration
        )
    }
}
