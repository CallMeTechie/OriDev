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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.ori.app.di.AppEntryPoint
import dev.ori.app.navigation.KNOWN_TOP_LEVEL_ROUTES
import dev.ori.app.navigation.OriDevNavHost
import dev.ori.core.ui.icons.lucide.ArrowLeftRight
import dev.ori.core.ui.icons.lucide.Folder
import dev.ori.core.ui.icons.lucide.LucideIcons
import dev.ori.core.ui.icons.lucide.Settings
import dev.ori.core.ui.icons.lucide.Terminal
import dev.ori.core.ui.icons.lucide.Wifi
import dev.ori.core.ui.theme.OriDevTheme
import dev.ori.domain.repository.SessionRegistry
import dev.ori.feature.connections.navigation.CONNECTIONS_ROUTE
import dev.ori.feature.filemanager.navigation.FILE_MANAGER_ROUTE
import dev.ori.feature.terminal.navigation.TERMINAL_ROUTE
import dev.ori.feature.transfers.navigation.TRANSFERS_ROUTE
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/**
 * PR 2 — Hilt cannot directly inject into `@Composable` functions, so we
 * expose the process-wide [SessionRegistry] singleton via an
 * [EntryPoint] that reads the application Hilt component. Consumers pull
 * it inside [OriDevApp] and hand the reference to [OriDevNavHost] which
 * uses it to `focus(sessionId)` before switching to the top-level
 * Terminal / File Manager destination.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SessionRegistryEntryPoint {
    fun sessionRegistry(): SessionRegistry
}

@Composable
fun OriDevApp(startDestination: String = CONNECTIONS_ROUTE) {
    OriDevTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRoute = currentDestination?.route

        val configuration = LocalConfiguration.current
        val isWideScreen = configuration.screenWidthDp >= WIDE_SCREEN_BREAKPOINT_DP

        val context = LocalContext.current
        val sessionRegistry = remember(context) {
            EntryPointAccessors
                .fromApplication(context.applicationContext, SessionRegistryEntryPoint::class.java)
                .sessionRegistry()
        }
        val resumePrefs = remember(context) {
            EntryPointAccessors
                .fromApplication(context.applicationContext, AppEntryPoint::class.java)
                .sessionResumePrefs()
        }

        // Task 13 — debounced (1 s) write of the last top-level route so a
        // cold start can restore the tab the user was on. Only tab-root
        // routes are persisted; deep-links (editor, add-connection, …) are
        // ignored so restart never lands on a modal screen. The 1 s debounce
        // collapses rapid tab-switching into a single DataStore write.
        val coroutineScope = rememberCoroutineScope()
        var writeJob by remember { mutableStateOf<Job?>(null) }
        LaunchedEffect(currentRoute) {
            val route = currentRoute
            if (route != null && route in KNOWN_TOP_LEVEL_ROUTES) {
                writeJob?.cancel()
                writeJob = coroutineScope.launch {
                    delay(ROUTE_WRITE_DEBOUNCE_MS)
                    resumePrefs.setLastTopLevelRoute(route)
                }
            }
        }

        val snackbarHostState = remember { SnackbarHostState() }

        if (isWideScreen) {
            // Foldable unfolded (>= 600dp): NavigationRail on the leading edge,
            // Scaffold hosts the content without a bottom bar.
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    navController = navController,
                    currentDestination = currentDestination,
                )
                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    OriDevNavHost(
                        navController = navController,
                        sessionRegistry = sessionRegistry,
                        startDestination = startDestination,
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
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                OriDevNavHost(
                    navController = navController,
                    sessionRegistry = sessionRegistry,
                    startDestination = startDestination,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }

        // Task 14 — app-level snackbar/dialog host. Lives outside the
        // Scaffold so it receives ResumeCoordinator events regardless of
        // which tab the user is on (a non-Connections landing tab would
        // otherwise mean ConnectionListViewModel is not subscribed yet).
        SnackbarHostEffect(
            snackbarHostState = snackbarHostState,
            navController = navController,
        )
    }
}

private const val ROUTE_WRITE_DEBOUNCE_MS = 1_000L

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
                onClick = { navController.navigateToTopLevelRoute(item.route) },
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
                onClick = { navController.navigateToTopLevelRoute(item.route) },
            )
        }
    }
}

private fun NavDestination?.isOnRoute(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

internal fun NavHostController.navigateToTopLevelRoute(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
