package dev.ori.feature.premium.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.ori.feature.premium.ui.PaywallScreen

const val PAYWALL_ROUTE = "paywall"

fun NavGraphBuilder.paywallScreen(onNavigateBack: () -> Unit) {
    composable(PAYWALL_ROUTE) {
        PaywallScreen(onNavigateBack = onNavigateBack)
    }
}

fun NavController.navigateToPaywall(navOptions: NavOptions? = null) {
    navigate(PAYWALL_ROUTE, navOptions)
}
