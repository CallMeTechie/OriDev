package dev.ori.feature.connections.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.ori.feature.connections.ui.AddEditConnectionScreen
import dev.ori.feature.connections.ui.ConnectionListScreen

const val CONNECTIONS_ROUTE = "connections"
const val ADD_CONNECTION_ROUTE = "connections/add"
const val EDIT_CONNECTION_ROUTE = "connections/edit/{profileId}"

fun NavGraphBuilder.connectionsScreen(
    navController: NavController,
    onNavigateToProxmox: () -> Unit = {},
    onOpenTerminal: (Long) -> Unit = {},
    onOpenFileManager: (Long) -> Unit = {},
) {
    composable(route = CONNECTIONS_ROUTE) {
        ConnectionListScreen(
            onNavigateToAdd = { navController.navigate(ADD_CONNECTION_ROUTE) },
            onNavigateToEdit = { profileId ->
                navController.navigate("connections/edit/$profileId")
            },
            onNavigateToProxmox = onNavigateToProxmox,
            onOpenTerminal = onOpenTerminal,
            onOpenFileManager = onOpenFileManager,
        )
    }
}

fun NavGraphBuilder.addConnectionScreen(navController: NavController) {
    composable(route = ADD_CONNECTION_ROUTE) {
        AddEditConnectionScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

fun NavGraphBuilder.editConnectionScreen(navController: NavController) {
    composable(
        route = EDIT_CONNECTION_ROUTE,
        arguments = listOf(
            navArgument("profileId") { type = NavType.LongType },
        ),
    ) {
        AddEditConnectionScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

fun NavController.navigateToConnections(navOptions: NavOptions? = null) {
    navigate(CONNECTIONS_ROUTE, navOptions)
}
