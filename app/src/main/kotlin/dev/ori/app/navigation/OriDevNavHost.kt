package dev.ori.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.ori.app.ui.navigateToTopLevelRoute
import dev.ori.domain.repository.SessionRegistry
import dev.ori.feature.connections.navigation.CONNECTIONS_ROUTE
import dev.ori.feature.connections.navigation.addConnectionScreen
import dev.ori.feature.connections.navigation.connectionsScreen
import dev.ori.feature.connections.navigation.editConnectionScreen
import dev.ori.feature.editor.navigation.diffViewerScreen
import dev.ori.feature.editor.navigation.editorScreen
import dev.ori.feature.editor.navigation.navigateToEditor
import dev.ori.feature.filemanager.navigation.FILE_MANAGER_ROUTE
import dev.ori.feature.filemanager.navigation.fileManagerScreen
import dev.ori.feature.proxmox.navigation.navigateToCreateVm
import dev.ori.feature.proxmox.navigation.navigateToProxmox
import dev.ori.feature.proxmox.navigation.proxmoxDashboardScreen
import dev.ori.feature.settings.navigation.SETTINGS_ROUTE
import dev.ori.feature.settings.navigation.settingsScreen
import dev.ori.feature.terminal.navigation.TERMINAL_ROUTE
import dev.ori.feature.terminal.navigation.terminalScreen
import dev.ori.feature.transfers.navigation.TRANSFERS_ROUTE
import dev.ori.feature.transfers.navigation.transferQueueScreen

/**
 * Top-level tab routes we accept as a cold-start `startDestination`.
 * Restored from [SessionResumePreferences.lastTopLevelRoute]; anything
 * outside this allowlist falls back to [CONNECTIONS_ROUTE] so a stale
 * preferences value can never crash the NavHost with an unknown route.
 */
private val KNOWN_TOP_LEVEL_ROUTES = setOf(
    CONNECTIONS_ROUTE,
    FILE_MANAGER_ROUTE,
    TERMINAL_ROUTE,
    TRANSFERS_ROUTE,
    SETTINGS_ROUTE,
)

@Composable
fun OriDevNavHost(
    navController: NavHostController,
    sessionRegistry: SessionRegistry,
    startDestination: String = CONNECTIONS_ROUTE,
    modifier: Modifier = Modifier,
) {
    val safeStart = if (startDestination in KNOWN_TOP_LEVEL_ROUTES) {
        startDestination
    } else {
        CONNECTIONS_ROUTE
    }
    NavHost(
        navController = navController,
        startDestination = safeStart,
        modifier = modifier,
    ) {
        connectionsScreen(
            navController = navController,
            onNavigateToProxmox = { navController.navigateToProxmox() },
            // PR 2 — phantom `terminal/{profileId}` / `filemanager/{profileId}`
            // routes are gone. The sheet hands us a sessionId (eventually
            // produced by sessionRegistry.connect(profileId).getOrThrow().id
            // in a follow-up chunk); we focus the registry on it and
            // navigate to the bottom-tab base route so the user sees the
            // tab highlight flip correctly.
            onOpenTerminal = { sessionId ->
                sessionRegistry.focus(sessionId)
                navController.navigateToTopLevelRoute(TERMINAL_ROUTE)
            },
            onOpenFileManager = { sessionId ->
                sessionRegistry.focus(sessionId)
                navController.navigateToTopLevelRoute(FILE_MANAGER_ROUTE)
            },
        )

        proxmoxDashboardScreen(
            // PR 2 — Proxmox's "Open Terminal" button loses its profileId
            // argument. Interim behaviour: the Terminal lands on its empty
            // state and the user picks a profile manually. Proxmox-owned
            // connect-on-demand will come back in a later chunk via the
            // same registry.connect() path as the Connections sheet.
            onNavigateToTerminal = { navController.navigateToTopLevelRoute(TERMINAL_ROUTE) },
            onNavigateToCreateVm = { nodeId -> navController.navigateToCreateVm(nodeId) },
            onNavigateBackFromWizard = { navController.popBackStack() },
        )

        addConnectionScreen(navController)

        editConnectionScreen(navController)

        fileManagerScreen(
            onNavigateToEditor = { filePath, isRemote ->
                navController.navigateToEditor(filePath, isRemote)
            },
            // PR 3 — empty-state CTA on the remote pane.
            onNavigateToConnections = {
                navController.navigateToTopLevelRoute(CONNECTIONS_ROUTE)
            },
        )

        terminalScreen(
            // PR 3 — empty-state CTA when no terminal sessions are open.
            onNavigateToConnections = {
                navController.navigateToTopLevelRoute(CONNECTIONS_ROUTE)
            },
        )

        transferQueueScreen()

        editorScreen(onNavigateBack = { navController.popBackStack() })

        diffViewerScreen(onNavigateBack = { navController.popBackStack() })

        settingsScreen()
    }
}
