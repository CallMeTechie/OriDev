package dev.ori.feature.proxmox.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.ori.feature.proxmox.ui.CreateVmWizard
import dev.ori.feature.proxmox.ui.ProxmoxDashboardScreen

const val PROXMOX_ROUTE = "proxmox"
const val PROXMOX_CREATE_VM_ROUTE = "proxmox/create-vm/{nodeId}"

fun NavGraphBuilder.proxmoxDashboardScreen(
    // PR 2 — simplified to zero-argument navigation. The caller hops
    // straight to the Terminal top-level route and the user picks a
    // profile from there. Proxmox-driven connect-on-demand comes back
    // in a later chunk via `sessionRegistry.connect(profileId)`.
    onNavigateToTerminal: () -> Unit,
    onNavigateToCreateVm: (nodeId: Long) -> Unit,
    onNavigateBackFromWizard: () -> Unit,
) {
    composable(PROXMOX_ROUTE) {
        ProxmoxDashboardScreen(
            onNavigateToTerminal = onNavigateToTerminal,
            onNavigateToCreateVm = onNavigateToCreateVm,
        )
    }
    composable(
        route = PROXMOX_CREATE_VM_ROUTE,
        arguments = listOf(navArgument("nodeId") { type = NavType.LongType }),
    ) {
        CreateVmWizard(
            onNavigateToTerminal = onNavigateToTerminal,
            onBack = onNavigateBackFromWizard,
        )
    }
}

fun NavController.navigateToProxmox() {
    navigate(PROXMOX_ROUTE) { launchSingleTop = true }
}

fun NavController.navigateToCreateVm(nodeId: Long) {
    navigate("proxmox/create-vm/$nodeId")
}
