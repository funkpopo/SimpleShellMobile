package com.example.simpleshell.domain.model

import java.util.Locale

/**
 * App-wide language preference.
 */
enum class Language(val locale: Locale) {
    SYSTEM(Locale.getDefault()),
    ENGLISH(Locale.US),
    CHINESE(Locale.SIMPLIFIED_CHINESE)
}
