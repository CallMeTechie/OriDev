package dev.ori.feature.terminal.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.ori.feature.terminal.ui.TerminalScreen

const val TERMINAL_ROUTE = "terminal"

fun NavGraphBuilder.terminalScreen(
    onNavigateToConnections: () -> Unit = {},
) {
    composable(route = TERMINAL_ROUTE) {
        TerminalScreen(onNavigateToConnections = onNavigateToConnections)
    }
}

fun NavController.navigateToTerminal(navOptions: NavOptions? = null) {
    navigate(TERMINAL_ROUTE, navOptions)
}
