package dev.ori.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.ori.feature.connections.navigation.CONNECTIONS_ROUTE
import dev.ori.feature.connections.navigation.addConnectionScreen
import dev.ori.feature.connections.navigation.connectionsScreen
import dev.ori.feature.connections.navigation.editConnectionScreen
import dev.ori.feature.filemanager.navigation.fileManagerScreen
import dev.ori.feature.terminal.navigation.terminalScreen
import dev.ori.feature.transfers.navigation.transferQueueScreen

@Composable
fun OriDevNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = CONNECTIONS_ROUTE,
        modifier = modifier,
    ) {
        connectionsScreen(navController)

        addConnectionScreen(navController)

        editConnectionScreen(navController)

        fileManagerScreen()

        terminalScreen()

        transferQueueScreen()

        composable("settings") {
            PlaceholderScreen(title = "Settings")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
