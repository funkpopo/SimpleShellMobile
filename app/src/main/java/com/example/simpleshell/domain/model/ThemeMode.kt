package com.example.simpleshell.domain.model

/**
 * App-wide theme preference.
 *
 * Keep this in the domain layer so both settings (data) and UI can depend on it without
 * introducing a UI -> data dependency cycle.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

