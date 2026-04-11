package dev.ori.feature.transfers.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import dev.ori.feature.transfers.ui.TransferQueueScreen

const val TRANSFERS_ROUTE = "transfers"

fun NavGraphBuilder.transferQueueScreen() {
    composable(route = TRANSFERS_ROUTE) {
        TransferQueueScreen()
    }
}

fun NavController.navigateToTransfers(navOptions: NavOptions? = null) {
    navigate(TRANSFERS_ROUTE, navOptions)
}
