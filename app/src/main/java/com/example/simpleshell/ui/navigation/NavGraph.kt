package com.example.simpleshell.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.simpleshell.ui.screens.connection.ConnectionEditScreen
import com.example.simpleshell.ui.screens.home.HomeScreen
import com.example.simpleshell.ui.screens.sftp.SftpScreen
import com.example.simpleshell.ui.screens.terminal.TerminalScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAddConnection = {
                    navController.navigate(Screen.ConnectionEdit.createRoute(null))
                },
                onEditConnection = { connectionId ->
                    navController.navigate(Screen.ConnectionEdit.createRoute(connectionId))
                },
                onConnectTerminal = { connectionId ->
                    navController.navigate(Screen.Terminal.createRoute(connectionId))
                },
                onConnectSftp = { connectionId ->
                    navController.navigate(Screen.Sftp.createRoute(connectionId))
                }
            )
        }

        composable(
            route = Screen.ConnectionEdit.route,
            arguments = listOf(
                navArgument("connectionId") { type = NavType.LongType }
            )
        ) {
            ConnectionEditScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("connectionId") { type = NavType.LongType }
            )
        ) {
            TerminalScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Sftp.route,
            arguments = listOf(
                navArgument("connectionId") { type = NavType.LongType }
            )
        ) {
            SftpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
