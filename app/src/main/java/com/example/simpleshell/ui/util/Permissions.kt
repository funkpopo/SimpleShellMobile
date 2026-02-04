package com.example.simpleshell.ui.util

/**
 * Android 13+ notification runtime permission.
 *
 * Using the string literal avoids referencing [android.Manifest.permission.POST_NOTIFICATIONS],
 * which triggers lint's InlinedApi warning when minSdk < 33.
 */
internal const val POST_NOTIFICATIONS_PERMISSION: String = "android.permission.POST_NOTIFICATIONS"

