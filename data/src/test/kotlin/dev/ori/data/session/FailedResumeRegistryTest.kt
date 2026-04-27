package dev.ori.data.session

import com.google.common.truth.Truth.assertThat
import dev.ori.domain.model.Session
import dev.ori.domain.repository.SessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FailedResumeRegistryTest {

    private fun TestScope.collectorScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `add and observe failed entries`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        val registry = FailedResumeRegistry(sessionRegistry, collectorScope())
        registry.add(FailedResume(1L, "A", "timeout"))
        assertThat(registry.failed.first()).hasSize(1)
    }

    @Test
    fun `auto-removes entry when openSessions contains its profileId`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        val registry = FailedResumeRegistry(sessionRegistry, collectorScope())
        registry.add(FailedResume(1L, "A", "timeout"))
        sessionRegistry.emitSessions(listOf(fakeSession(profileId = 1L)))
        assertThat(registry.failed.first()).isEmpty()
    }

    @Test
    fun `clear wipes all entries`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        val registry = FailedResumeRegistry(sessionRegistry, collectorScope())
        registry.add(FailedResume(1L, "A", "x"))
        registry.add(FailedResume(2L, "B", "y"))
        registry.clear()
        assertThat(registry.failed.first()).isEmpty()
    }

    private fun fakeSession(profileId: Long) = Session(
        id = "session-$profileId",
        profileId = profileId,
        profileName = "Profile $profileId",
        host = "host",
        port = 22,
        connectedAt = 0L,
        protocol = dev.ori.core.common.model.Protocol.SFTP,
    )

    private class FakeSessionRegistry : SessionRegistry {
        private val _openSessions = MutableStateFlow<List<Session>>(emptyList())
        override val openSessions: StateFlow<List<Session>> = _openSessions

        fun emitSessions(sessions: List<Session>) {
            _openSessions.value = sessions
        }

        override val focusedSessionId = MutableStateFlow<String?>(null)
        override val persistedProfileIds = MutableStateFlow<Set<Long>>(emptySet())
        override suspend fun connect(profileId: Long) = error("not needed")
        override fun focus(sessionId: String) { /* no-op */ }
        override suspend fun disconnect(sessionId: String) { /* no-op */ }
        override suspend fun cancelConnect(profileId: Long) { /* no-op */ }
        override fun markFilesUsed(sessionId: String) { /* no-op */ }
        override fun scheduleGraceDisconnect(sessionId: String) { /* no-op */ }
        override fun cancelGraceDisconnect(sessionId: String) { /* no-op */ }
    }
}
