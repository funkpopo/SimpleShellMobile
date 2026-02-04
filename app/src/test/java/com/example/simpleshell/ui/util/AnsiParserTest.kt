package com.example.simpleshell.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnsiParserTest {

    private fun styleAt(text: AnnotatedString, index: Int): SpanStyle? {
        return text.spanStyles.firstOrNull { index >= it.start && index < it.end }?.item
    }

    @Test
    fun parse_plainText_keepsText() {
        val result = AnsiParser.parse("hello")
        assertEquals("hello", result.text)
    }

    @Test
    fun parse_standardSgrForeground_appliesColor() {
        val defaultColor = Color(0xFFE5E5E5)
        val input = "a \u001B[31mred\u001B[0m b"
        val result = AnsiParser.parse(input, defaultColor)

        assertEquals("a red b", result.text)

        val redStart = "a ".length
        val style = styleAt(result, redStart)
        assertNotNull(style)
        assertEquals(Color(0xFFCD0000), style!!.color)
    }

    @Test
    fun parse_256Color_sgr38_5_isSupported() {
        // 196 is pure red in the xterm 6x6x6 cube.
        val input = "\u001B[38;5;196mX\u001B[0m"
        val result = AnsiParser.parse(input)

        assertEquals("X", result.text)
        val style = styleAt(result, 0)
        assertNotNull(style)
        assertEquals(Color(0xFFFF0000), style!!.color)
    }

    @Test
    fun parse_trueColor_sgr38_2_isSupported_withColonSeparators() {
        val input = "\u001B[38:2:1:2:3mX\u001B[0m"
        val result = AnsiParser.parse(input)

        assertEquals("X", result.text)
        val style = styleAt(result, 0)
        assertNotNull(style)
        assertEquals(Color(0xFF010203), style!!.color)
    }

    @Test
    fun parse_underlineAndItalic_areSupported() {
        val input = "\u001B[3;4mX\u001B[0m"
        val result = AnsiParser.parse(input)

        assertEquals("X", result.text)
        val style = styleAt(result, 0)
        assertNotNull(style)
        assertEquals(FontStyle.Italic, style!!.fontStyle)
        assertEquals(TextDecoration.Underline, style.textDecoration)
    }

    @Test
    fun parse_inverse_withoutBackground_swapsToBackgroundHighlight() {
        val defaultColor = Color(0xFFE5E5E5)
        val input = "\u001B[31;7mX\u001B[0m"
        val result = AnsiParser.parse(input, defaultColor)

        val style = styleAt(result, 0)
        assertNotNull(style)
        // With inverse and no explicit background, we use the current foreground as background
        // and default foreground as the text color.
        assertEquals(defaultColor, style!!.color)
        assertEquals(Color(0xFFCD0000), style.background)
    }

    @Test
    fun parse_carriageReturn_progress_keepsOnlyLatestLineContent() {
        val input = "Downloading 10%\rDownloading 20%\rDownloading 30%\nDone\n"
        val result = AnsiParser.parse(input)
        assertEquals("Downloading 30%\nDone\n", result.text)
    }

    @Test
    fun parse_osc_and_nonSgrCsi_areStripped() {
        val input = "a\u001B]0;title\u0007b\u001B[?25lc"
        val result = AnsiParser.parse(input)
        assertEquals("abc", result.text)
    }
}

