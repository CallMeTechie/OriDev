package dev.ori.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.ori.feature.connections.navigation.CONNECTIONS_ROUTE
import dev.ori.feature.connections.navigation.addConnectionScreen
import dev.ori.feature.connections.navigation.connectionsScreen
import dev.ori.feature.connections.navigation.editConnectionScreen
import dev.ori.feature.editor.navigation.diffViewerScreen
import dev.ori.feature.editor.navigation.editorScreen
import dev.ori.feature.editor.navigation.navigateToEditor
import dev.ori.feature.filemanager.navigation.fileManagerScreen
import dev.ori.feature.proxmox.navigation.navigateToCreateVm
import dev.ori.feature.proxmox.navigation.navigateToProxmox
import dev.ori.feature.proxmox.navigation.proxmoxDashboardScreen
import dev.ori.feature.settings.navigation.settingsScreen
import dev.ori.feature.terminal.navigation.navigateToTerminal
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
        connectionsScreen(
            navController = navController,
            onNavigateToProxmox = { navController.navigateToProxmox() },
        )

        proxmoxDashboardScreen(
            onNavigateToTerminal = { profileId -> navController.navigateToTerminal(profileId) },
            onNavigateToCreateVm = { nodeId -> navController.navigateToCreateVm(nodeId) },
            onNavigateBackFromWizard = { navController.popBackStack() },
        )

        addConnectionScreen(navController)

        editConnectionScreen(navController)

        fileManagerScreen(
            onNavigateToEditor = { filePath, isRemote ->
                navController.navigateToEditor(filePath, isRemote)
            },
        )

        terminalScreen()

        transferQueueScreen()

        editorScreen(onNavigateBack = { navController.popBackStack() })

        diffViewerScreen(onNavigateBack = { navController.popBackStack() })

        settingsScreen()
    }
}
