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
    // PR 2 — future-proofing for the Chunk that rewires the detail sheet
    // to `sessionRegistry.connect(profileId).getOrThrow().id`. The host
    // (OriDevNavHost) calls `sessionRegistry.focus(sessionId)` + top-level
    // nav when this fires, so the lambda deals in session ids, not
    // profile ids.
    onOpenTerminal: (sessionId: String) -> Unit = {},
    onOpenFileManager: (sessionId: String) -> Unit = {},
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

fun NavGraphBuilder.addConnectionScreen(
    navController: NavController,
    onNavigateToPaywall: () -> Unit = {},
) {
    composable(route = ADD_CONNECTION_ROUTE) {
        AddEditConnectionScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPaywall = onNavigateToPaywall,
        )
    }
}

fun NavGraphBuilder.editConnectionScreen(
    navController: NavController,
    onNavigateToPaywall: () -> Unit = {},
) {
    composable(
        route = EDIT_CONNECTION_ROUTE,
        arguments = listOf(
            navArgument("profileId") { type = NavType.LongType },
        ),
    ) {
        AddEditConnectionScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPaywall = onNavigateToPaywall,
        )
    }
}

fun NavController.navigateToConnections(navOptions: NavOptions? = null) {
    navigate(CONNECTIONS_ROUTE, navOptions)
}
