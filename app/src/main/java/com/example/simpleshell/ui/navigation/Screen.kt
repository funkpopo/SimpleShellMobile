package com.example.simpleshell.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object ConnectionEdit : Screen("connection_edit/{connectionId}") {
        fun createRoute(connectionId: Long? = null) = "connection_edit/${connectionId ?: -1}"
    }
    object Terminal : Screen("terminal/{connectionId}") {
        fun createRoute(connectionId: Long) = "terminal/$connectionId"
    }
    object Sftp : Screen("sftp/{connectionId}") {
        fun createRoute(connectionId: Long) = "sftp/$connectionId"
    }
}
