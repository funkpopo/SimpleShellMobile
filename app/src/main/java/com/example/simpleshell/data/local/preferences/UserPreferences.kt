package com.example.simpleshell.data.local.preferences

import com.example.simpleshell.domain.model.Language
import com.example.simpleshell.domain.model.ThemeColor
import com.example.simpleshell.domain.model.ThemeMode

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val themeColor: ThemeColor = ThemeColor.PURPLE,
    val language: Language = Language.SYSTEM,
    /**
     * Scale factor for the SSH terminal content (output area).
     *
     * 1.0 = default size. Values outside a reasonable range should be clamped by callers.
     */
    val terminalFontScale: Float = 1.0f
)
