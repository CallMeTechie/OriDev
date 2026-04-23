package dev.ori.domain.model

/**
 * Emitted by the ResumeCoordinator for the app-level snackbar host.
 * Lives in :domain so both :data (producer) and :app (consumer-to-nav)
 * can reference it without a cross-module leak.
 */
data class ResumeSnackbar(
    val message: String,
    val actionLabel: String?,
    val action: ResumeAction,
)

sealed interface ResumeAction {
    data class OpenConnections(val profileId: Long) : ResumeAction
    data object None : ResumeAction
}
