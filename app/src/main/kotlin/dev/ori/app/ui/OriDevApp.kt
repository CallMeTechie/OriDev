package dev.ori.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ori.app.navigation.OriDevNavHost
import dev.ori.core.ui.icons.lucide.ArrowLeftRight
import dev.ori.core.ui.icons.lucide.Folder
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Settings
import dev.ori.core.ui.icons.lucide.Terminal
import dev.ori.core.ui.icons.lucide.Wifi
import dev.ori.core.ui.theme.OriDevTheme
import dev.ori.feature.connections.navigation.CONNECTIONS_ROUTE
import dev.ori.feature.filemanager.navigation.FILE_MANAGER_ROUTE
import dev.ori.feature.terminal.navigation.TERMINAL_ROUTE
import dev.ori.feature.transfers.navigation.TRANSFERS_ROUTE

private const val WIDE_SCREEN_BREAKPOINT_DP = 600

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(CONNECTIONS_ROUTE, "Connections", LucideIcons.Wifi),
    TopLevelDestination(FILE_MANAGER_ROUTE, "Files", LucideIcons.Folder),
    TopLevelDestination(TERMINAL_ROUTE, "Terminal", LucideIcons.Terminal),
    TopLevelDestination(TRANSFERS_ROUTE, "Transfers", LucideIcons.ArrowLeftRight),
    TopLevelDestination("settings", "Settings", LucideIcons.Settings),
)

@Composable
fun OriDevApp() {
    OriDevTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val configuration = LocalConfiguration.current
        val isWideScreen = configuration.screenWidthDp >= WIDE_SCREEN_BREAKPOINT_DP

        if (isWideScreen) {
            // Foldable unfolded (>= 600dp): NavigationRail on the leading edge,
            // Scaffold hosts the content without a bottom bar.
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    navController = navController,
                    currentDestination = currentDestination,
                )
                Scaffold(modifier = Modifier.weight(1f)) { innerPadding ->
                    OriDevNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        } else {
            // Phone (< 600dp): NavigationBar at the bottom. Window insets are
            // zeroed so the gesture-nav inset stops padding the bar height —
            // reclaims ~24dp of vertical real estate on phones with gesture nav.
            Scaffold(
                bottomBar = {
                    AppNavigationBar(
                        navController = navController,
                        currentDestination = currentDestination,
                    )
                },
            ) { innerPadding ->
                OriDevNavHost(
                    navController = navController,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun AppNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    NavigationBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        topLevelDestinations.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentDestination.isOnRoute(item.route),
                onClick = { navController.navigateToTopLevel(item.route) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    NavigationRail {
        topLevelDestinations.forEach { item ->
            NavigationRailItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentDestination.isOnRoute(item.route),
                onClick = { navController.navigateToTopLevel(item.route) },
            )
        }
    }
}

private fun NavDestination?.isOnRoute(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
