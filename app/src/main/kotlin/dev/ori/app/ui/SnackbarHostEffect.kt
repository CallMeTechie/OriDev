package dev.ori.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import dagger.hilt.android.EntryPointAccessors
import dev.ori.app.di.AppEntryPoint
import dev.ori.domain.model.ResumeAction
import dev.ori.feature.connections.navigation.CONNECTIONS_ROUTE
import kotlinx.coroutines.withTimeoutOrNull

/**
 * App-level sink for [dev.ori.data.session.ResumeCoordinator] side effects.
 *
 * Two responsibilities, both of which must live above the NavHost so they
 * survive tab switches (and fire even when the user lands on a non-
 * Connections tab on cold start):
 *
 * 1. **Snackbars** — collect [dev.ori.data.session.ResumeCoordinator.snackbarEvents]
 *    and push them into [SnackbarHostState]. When the user taps the action
 *    we route via the NavController using [ResumeAction].
 * 2. **Host-key TOFU dialog** — observe
 *    [dev.ori.data.session.ResumeCoordinator.hostKeyPrompts]. A dialog at
 *    this level is important because the standard TOFU dialog lives inside
 *    `ConnectionListViewModel`, which is not subscribed until the
 *    Connections tab is first rendered — a user whose last tab was e.g.
 *    Terminal would otherwise never see the prompt and the coordinator
 *    would time out after 30 s (see
 *    [dev.ori.data.session.ResumeCoordinator] `HOST_KEY_PROMPT_TIMEOUT_MS`).
 */
@Composable
fun SnackbarHostEffect(
    snackbarHostState: SnackbarHostState,
    navController: NavHostController,
) {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) {
        EntryPointAccessors
            .fromApplication(context, AppEntryPoint::class.java)
            .resumeCoordinator()
    }

    LaunchedEffect(Unit) {
        coordinator.snackbarEvents.collect { event ->
            val result = withTimeoutOrNull(SNACKBAR_TIMEOUT_MS) {
                snackbarHostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.actionLabel,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                when (event.action) {
                    is ResumeAction.OpenConnections -> {
                        // Tab-style nav so the user lands on the Connections
                        // tab root, not a stacked instance of it.
                        navController.navigateToTopLevelRoute(CONNECTIONS_ROUTE)
                        // Note: profileId scroll-to-profile is deferred to
                        // a follow-up — needs a shared state-holder since
                        // ConnectionListViewModel may not be subscribed yet.
                    }
                    ResumeAction.None -> Unit
                }
            }
        }
    }

    val currentPrompt by coordinator.hostKeyPrompts.collectAsState()
    currentPrompt?.let { prompt ->
        HostKeyTrustDialog(
            prompt = prompt,
            onAccept = { coordinator.respondToPrompt(prompt.id, accept = true) },
            onDecline = { coordinator.respondToPrompt(prompt.id, accept = false) },
        )
    }
}

private const val SNACKBAR_TIMEOUT_MS = 6_000L
