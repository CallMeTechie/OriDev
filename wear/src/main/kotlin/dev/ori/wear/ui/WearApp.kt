package dev.ori.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.ori.wear.ui.screens.CommandOutputScreen
import dev.ori.wear.ui.screens.ConnectionListScreen
import dev.ori.wear.ui.screens.MainTileScreen
import dev.ori.wear.ui.screens.PanicButtonScreen
import dev.ori.wear.ui.screens.QuickCommandsScreen
import dev.ori.wear.ui.screens.ServerHealthScreen
import dev.ori.wear.ui.screens.TransferMonitorScreen
import dev.ori.wear.ui.screens.TwoFactorDialogScreen
import dev.ori.wear.ui.theme.OriDevWearTheme

@Composable
fun WearApp() {
    val viewModel: WearAppViewModel = hiltViewModel()
    val pending2Fa by viewModel.pending2Fa.collectAsStateWithLifecycle()

    OriDevWearTheme {
        val navController = rememberSwipeDismissableNavController()

        DisposableEffect(Unit) {
            viewModel.startSync()
            onDispose { viewModel.stopSync() }
        }

        // 2FA overlay takes priority over any nav state.
        if (pending2Fa != null) {
            TwoFactorDialogScreen()
            return@OriDevWearTheme
        }

        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = ROUTE_MAIN,
            ) {
                composable(ROUTE_MAIN) { MainTileScreen(navController) }
                composable(ROUTE_CONNECTIONS) { ConnectionListScreen(navController) }
                composable(ROUTE_TRANSFERS) { TransferMonitorScreen(navController) }
                composable(ROUTE_QUICK_COMMANDS) { QuickCommandsScreen(navController) }
                composable(ROUTE_SERVER_HEALTH) { ServerHealthScreen(navController) }
                composable(ROUTE_PANIC) { PanicButtonScreen(navController) }
                composable(ROUTE_COMMAND_OUTPUT) { CommandOutputScreen(navController) }
            }
        }
    }
}
