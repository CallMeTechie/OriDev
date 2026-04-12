package dev.ori.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.ori.feature.onboarding.ui.BatteryOptimizationScreen
import dev.ori.feature.onboarding.ui.DoneScreen
import dev.ori.feature.onboarding.ui.PermissionsScreen
import dev.ori.feature.onboarding.ui.WelcomeScreen

private const val ROUTE_WELCOME = "onboarding/welcome"
private const val ROUTE_PERMISSIONS = "onboarding/permissions"
private const val ROUTE_BATTERY = "onboarding/battery"
private const val ROUTE_DONE = "onboarding/done"

/**
 * First-run onboarding flow: Welcome -> Permissions -> Battery optimization -> Done.
 *
 * Call site is expected to gate the rest of the app behind
 * [dev.ori.feature.onboarding.data.OnboardingPreferences.completed]; [onFinish]
 * is invoked from the final "Starten" CTA and must persist `completed = true`.
 */
@Composable
fun OnboardingFlow(onFinish: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: OnboardingViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = ROUTE_WELCOME,
    ) {
        composable(ROUTE_WELCOME) {
            WelcomeScreen(
                onContinue = { navController.navigate(ROUTE_PERMISSIONS) },
            )
        }
        composable(ROUTE_PERMISSIONS) {
            PermissionsScreen(
                onContinue = { navController.navigate(ROUTE_BATTERY) },
            )
        }
        composable(ROUTE_BATTERY) {
            BatteryOptimizationScreen(
                onContinue = { navController.navigate(ROUTE_DONE) },
            )
        }
        composable(ROUTE_DONE) {
            DoneScreen(
                onStart = {
                    viewModel.markCompleted(onMarked = onFinish)
                },
            )
        }
    }
}
