package dev.ori.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.ori.feature.settings.ui.SettingsScreen

const val SETTINGS_ROUTE: String = "settings"

fun NavGraphBuilder.settingsScreen(onNavigateToPaywall: () -> Unit = {}) {
    composable(SETTINGS_ROUTE) {
        SettingsScreen(onNavigateToPaywall = onNavigateToPaywall)
    }
}

fun NavController.navigateToSettings() {
    navigate(SETTINGS_ROUTE)
}
