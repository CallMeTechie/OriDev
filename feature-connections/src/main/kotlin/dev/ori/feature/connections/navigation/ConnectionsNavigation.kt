package dev.ori.feature.connections.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.ori.feature.connections.ui.ConnectionListScreen

const val CONNECTIONS_ROUTE = "connections"

fun NavGraphBuilder.connectionsScreen() {
    composable(route = CONNECTIONS_ROUTE) {
        ConnectionListScreen()
    }
}

fun NavController.navigateToConnections(navOptions: NavOptions? = null) {
    navigate(CONNECTIONS_ROUTE, navOptions)
}
