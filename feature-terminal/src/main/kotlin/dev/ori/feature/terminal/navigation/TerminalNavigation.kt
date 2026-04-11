package dev.ori.feature.terminal.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.ori.feature.terminal.ui.TerminalScreen

const val TERMINAL_ROUTE = "terminal"
const val TERMINAL_WITH_PROFILE_ROUTE = "terminal/{profileId}"

fun NavGraphBuilder.terminalScreen() {
    composable(route = TERMINAL_ROUTE) {
        TerminalScreen()
    }
    composable(
        route = TERMINAL_WITH_PROFILE_ROUTE,
        arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
    ) { backStackEntry ->
        val profileId = backStackEntry.arguments?.getLong("profileId") ?: return@composable
        TerminalScreen(initialProfileId = profileId)
    }
}

fun NavController.navigateToTerminal(profileId: Long? = null, navOptions: NavOptions? = null) {
    val route = if (profileId != null) "terminal/$profileId" else TERMINAL_ROUTE
    navigate(route, navOptions)
}
