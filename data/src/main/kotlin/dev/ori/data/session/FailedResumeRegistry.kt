package dev.ori.data.session

import dev.ori.domain.repository.SessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of a failed automatic session resume. Surfaced by the UI so
 * the user can retry or dismiss. Auto-cleared once the profile appears
 * back in [SessionRegistry.openSessions] (retry succeeded out-of-band).
 */
data class FailedResume(
    val profileId: Long,
    val profileName: String,
    val reason: String,
)

/**
 * Process-wide registry of profiles whose auto-resume failed. The list
 * is self-maintaining: entries disappear as soon as the corresponding
 * profile shows up in [SessionRegistry.openSessions], so consumers do
 * not have to manually dismiss stale errors after a successful retry.
 */
@Singleton
class FailedResumeRegistry internal constructor(
    sessionRegistry: SessionRegistry,
    scope: CoroutineScope,
) {
    @Inject
    constructor(sessionRegistry: SessionRegistry) : this(
        sessionRegistry,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val _failed = MutableStateFlow<List<FailedResume>>(emptyList())
    val failed: StateFlow<List<FailedResume>> = _failed.asStateFlow()

    init {
        sessionRegistry.openSessions
            .onEach { sessions ->
                val active = sessions.map { it.profileId }.toSet()
                _failed.update { list -> list.filterNot { it.profileId in active } }
            }
            .launchIn(scope)
    }

    fun add(entry: FailedResume) {
        _failed.update { it + entry }
    }

    fun clear() {
        _failed.value = emptyList()
    }
}
