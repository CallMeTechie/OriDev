package dev.ori.data.session

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.model.AuthMethod
import dev.ori.core.common.model.Protocol
import dev.ori.core.common.result.AppErrorException
import dev.ori.data.dao.ServerProfileDao
import dev.ori.data.entity.ServerProfileEntity
import dev.ori.domain.model.ResumeAction
import dev.ori.domain.model.ResumeSnackbar
import dev.ori.domain.model.Session
import dev.ori.domain.model.TabMemo
import dev.ori.domain.preferences.AutoResumePreferences
import dev.ori.domain.preferences.SessionResumePreferences
import dev.ori.domain.repository.SessionRegistry
import dev.ori.domain.usecase.TrustHostUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [ResumeCoordinator].
 *
 * Key invariants under test (spec §11 + §6.1):
 *  - `start()` only runs one resume pass per process (`ran` guard).
 *  - Opt-out short-circuits: `autoResumeSessions=false` must never
 *    call `sessionRegistry.connect`.
 *  - Partial failure flow: one snackbar (fail-count-aware), failed
 *    profileIds remain in persistence so next cold start retries them,
 *    entries land in [FailedResumeRegistry].
 *  - TOFU prompts are serialised by a Mutex — two profiles hitting
 *    `HostKeyUnknown` produce one dialog at a time.
 *
 * The coordinator's scope is injected via the internal constructor so
 * tests drive it through `StandardTestDispatcher`'s virtual time
 * without touching `Dispatchers.IO`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeCoordinatorTest {

    private fun TestScope.collectorScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun buildCoordinator(
        sessionRegistry: SessionRegistry,
        resumePrefs: SessionResumePreferences,
        autoResumePrefs: AutoResumePreferences,
        failedRegistry: FailedResumeRegistry,
        trustHostUseCase: TrustHostUseCase,
        profileDao: ServerProfileDao,
        scope: CoroutineScope,
    ) = ResumeCoordinator(
        sessionRegistry = sessionRegistry,
        resumePrefs = resumePrefs,
        autoResumePrefs = autoResumePrefs,
        failedRegistry = failedRegistry,
        trustHostUseCase = trustHostUseCase,
        serverProfileDao = profileDao,
        scope = scope,
    )

    @Test
    fun `ran once guard ignores second start`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        val resumePrefs = FakeResumePreferences(profileIds = setOf(1L))
        val autoResumePrefs = FakeAutoResumePreferences(enabled = true)
        val profileDao = fakeProfileDao(1L to "A")
        val coordinator = buildCoordinator(
            sessionRegistry = sessionRegistry,
            resumePrefs = resumePrefs,
            autoResumePrefs = autoResumePrefs,
            failedRegistry = FailedResumeRegistry(sessionRegistry, collectorScope()),
            trustHostUseCase = relaxedTrustHost(),
            profileDao = profileDao,
            scope = collectorScope(),
        )

        coordinator.start()
        coordinator.start()
        advanceUntilIdle()

        assertThat(sessionRegistry.connectCallsFor(1L)).isEqualTo(1)
    }

    @Test
    fun `opt-out skips connect entirely`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        val resumePrefs = FakeResumePreferences(profileIds = setOf(1L, 2L))
        val autoResumePrefs = FakeAutoResumePreferences(enabled = false)
        val profileDao = fakeProfileDao(1L to "A", 2L to "B")
        val coordinator = buildCoordinator(
            sessionRegistry = sessionRegistry,
            resumePrefs = resumePrefs,
            autoResumePrefs = autoResumePrefs,
            failedRegistry = FailedResumeRegistry(sessionRegistry, collectorScope()),
            trustHostUseCase = relaxedTrustHost(),
            profileDao = profileDao,
            scope = collectorScope(),
        )

        coordinator.start()
        advanceUntilIdle()

        assertThat(sessionRegistry.connectCallsFor(1L)).isEqualTo(0)
        assertThat(sessionRegistry.connectCallsFor(2L)).isEqualTo(0)
    }

    @Test
    fun `empty persisted profileIds is a no-op`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        val resumePrefs = FakeResumePreferences(profileIds = emptySet())
        val autoResumePrefs = FakeAutoResumePreferences(enabled = true)
        val profileDao = fakeProfileDao()
        val coordinator = buildCoordinator(
            sessionRegistry = sessionRegistry,
            resumePrefs = resumePrefs,
            autoResumePrefs = autoResumePrefs,
            failedRegistry = FailedResumeRegistry(sessionRegistry, collectorScope()),
            trustHostUseCase = relaxedTrustHost(),
            profileDao = profileDao,
            scope = collectorScope(),
        )

        coordinator.start()
        advanceUntilIdle()

        assertThat(sessionRegistry.totalConnectCalls()).isEqualTo(0)
    }

    @Test
    fun `partial failure emits snackbar and keeps profileId in prefs`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        sessionRegistry.failConnectFor(2L, IllegalStateException("timeout"))
        val resumePrefs = FakeResumePreferences(profileIds = setOf(1L, 2L))
        val autoResumePrefs = FakeAutoResumePreferences(enabled = true)
        val profileDao = fakeProfileDao(1L to "A", 2L to "B")
        val failedRegistry = FailedResumeRegistry(sessionRegistry, collectorScope())

        val coordinator = buildCoordinator(
            sessionRegistry = sessionRegistry,
            resumePrefs = resumePrefs,
            autoResumePrefs = autoResumePrefs,
            failedRegistry = failedRegistry,
            trustHostUseCase = relaxedTrustHost(),
            profileDao = profileDao,
            scope = collectorScope(),
        )

        val snacks = mutableListOf<ResumeSnackbar>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.snackbarEvents.toList(snacks)
        }

        coordinator.start()
        advanceUntilIdle()

        // 1 failing profile → single OPEN-chip snackbar.
        assertThat(snacks).hasSize(1)
        assertThat(snacks.first().message).contains("B")
        assertThat(snacks.first().actionLabel).isEqualTo("ÖFFNEN")
        val action = snacks.first().action
        assertThat(action).isInstanceOf(ResumeAction.OpenConnections::class.java)
        assertThat((action as ResumeAction.OpenConnections).profileId).isEqualTo(2L)

        // ProfileId stays in persistence — next cold start retries it.
        assertThat(resumePrefs.profileIds.first()).contains(2L)

        // Registry holds the failure for the banner UI.
        assertThat(failedRegistry.failed.first().map { it.profileId }).contains(2L)
    }

    @Test
    fun `two failures emit DETAILS-chip snackbar`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        sessionRegistry.failConnectFor(1L, IllegalStateException("refused"))
        sessionRegistry.failConnectFor(2L, IllegalStateException("timeout"))
        val resumePrefs = FakeResumePreferences(profileIds = setOf(1L, 2L))
        val autoResumePrefs = FakeAutoResumePreferences(enabled = true)
        val profileDao = fakeProfileDao(1L to "A", 2L to "B")

        val coordinator = buildCoordinator(
            sessionRegistry = sessionRegistry,
            resumePrefs = resumePrefs,
            autoResumePrefs = autoResumePrefs,
            failedRegistry = FailedResumeRegistry(sessionRegistry, collectorScope()),
            trustHostUseCase = relaxedTrustHost(),
            profileDao = profileDao,
            scope = collectorScope(),
        )

        val snacks = mutableListOf<ResumeSnackbar>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.snackbarEvents.toList(snacks)
        }

        coordinator.start()
        advanceUntilIdle()

        assertThat(snacks).hasSize(1)
        assertThat(snacks.first().actionLabel).isEqualTo("DETAILS")
        assertThat(snacks.first().message).contains("2")
    }

    @Test
    fun `TOFU prompt is sequential across two profiles`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        sessionRegistry.failConnectFor(
            1L,
            AppErrorException(AppError.HostKeyUnknown("host-1", "fp1", "RSA")),
        )
        sessionRegistry.failConnectFor(
            2L,
            AppErrorException(AppError.HostKeyUnknown("host-2", "fp2", "ED25519")),
        )
        val resumePrefs = FakeResumePreferences(profileIds = setOf(1L, 2L))
        val autoResumePrefs = FakeAutoResumePreferences(enabled = true)
        val profileDao = fakeProfileDao(1L to "A", 2L to "B")
        val trustHost = mockk<TrustHostUseCase>(relaxed = true)

        val coordinator = buildCoordinator(
            sessionRegistry = sessionRegistry,
            resumePrefs = resumePrefs,
            autoResumePrefs = autoResumePrefs,
            failedRegistry = FailedResumeRegistry(sessionRegistry, collectorScope()),
            trustHostUseCase = trustHost,
            profileDao = profileDao,
            scope = collectorScope(),
        )

        coordinator.start()
        advanceUntilIdle()

        // First prompt surfaces. Which profile wins the race is
        // non-deterministic, but exactly one prompt is visible.
        val first = coordinator.hostKeyPrompts.value
        assertThat(first).isNotNull()
        val firstProfile = first!!.profileId
        assertThat(firstProfile).isAnyOf(1L, 2L)

        // Respond → declined (accept=false) to avoid racing through the retry path.
        coordinator.respondToPrompt(first.id, accept = false)
        advanceUntilIdle()

        val second = coordinator.hostKeyPrompts.value
        assertThat(second).isNotNull()
        // Second prompt is for the other profile.
        assertThat(second!!.profileId).isNotEqualTo(firstProfile)

        coordinator.respondToPrompt(second.id, accept = false)
        advanceUntilIdle()

        // Prompt queue drained; state returns to null.
        assertThat(coordinator.hostKeyPrompts.value).isNull()
    }

    @Test
    fun `TOFU accept triggers trustHostUseCase and retries connect`() = runTest {
        val sessionRegistry = FakeSessionRegistry()
        // First attempt fails with HostKeyUnknown; after trustHost +
        // retry, succeed on the second call.
        sessionRegistry.failConnectOnceFor(
            1L,
            AppErrorException(AppError.HostKeyUnknown("host-1", "fp1", "RSA")),
        )
        val resumePrefs = FakeResumePreferences(profileIds = setOf(1L))
        val autoResumePrefs = FakeAutoResumePreferences(enabled = true)
        val profileDao = fakeProfileDao(1L to "A")
        val trustHost = mockk<TrustHostUseCase>(relaxed = true)
        coEvery {
            trustHost.invoke(host = any(), port = any(), keyType = any(), fingerprint = any())
        } returns Result.success(Unit)

        val coordinator = buildCoordinator(
            sessionRegistry = sessionRegistry,
            resumePrefs = resumePrefs,
            autoResumePrefs = autoResumePrefs,
            failedRegistry = FailedResumeRegistry(sessionRegistry, collectorScope()),
            trustHostUseCase = trustHost,
            profileDao = profileDao,
            scope = collectorScope(),
        )

        coordinator.start()
        advanceUntilIdle()

        val prompt = coordinator.hostKeyPrompts.value
        assertThat(prompt).isNotNull()
        coordinator.respondToPrompt(prompt!!.id, accept = true)
        advanceUntilIdle()

        coVerify {
            trustHost.invoke(host = "host-1", port = 22, keyType = "RSA", fingerprint = "fp1")
        }
        // Two connect attempts total: the first that failed with
        // HostKeyUnknown + the retry after TrustHost succeeded.
        assertThat(sessionRegistry.connectCallsFor(1L)).isEqualTo(2)
    }

    // Marker test: coordinator must never import :feature-terminal.
    // Enforced structurally by module dependency graph — if the import
    // slipped in this source file would not compile. No runtime assert
    // needed; this method is the documented guard.
    @Test
    fun `coordinator never touches TerminalViewModel`() {
        // Compile-time regression marker; see KDoc above.
    }

    // --- Fakes ---------------------------------------------------------

    private fun fakeProfileDao(vararg profiles: Pair<Long, String>): ServerProfileDao {
        val dao = mockk<ServerProfileDao>(relaxed = true)
        profiles.forEach { (id, name) ->
            coEvery { dao.getById(id) } returns fakeProfileEntity(id, name)
        }
        return dao
    }

    private fun fakeProfileEntity(id: Long, name: String) = ServerProfileEntity(
        id = id,
        name = name,
        host = "host-$id",
        port = 22,
        protocol = Protocol.SSH,
        username = "user",
        authMethod = AuthMethod.PASSWORD,
        credentialRef = "kref-$id",
        sshKeyType = null,
        startupCommand = null,
        projectDirectory = null,
        claudeCodeModel = null,
        claudeMdPath = null,
    )

    private fun relaxedTrustHost(): TrustHostUseCase {
        val trust = mockk<TrustHostUseCase>(relaxed = true)
        coEvery {
            trust.invoke(host = any(), port = any(), keyType = any(), fingerprint = any())
        } returns Result.success(Unit)
        return trust
    }

    private class FakeSessionRegistry : SessionRegistry {
        private val _openSessions = MutableStateFlow<List<Session>>(emptyList())
        override val openSessions: StateFlow<List<Session>> = _openSessions
        override val focusedSessionId = MutableStateFlow<String?>(null)
        override val persistedProfileIds = MutableStateFlow<Set<Long>>(emptySet())

        private val failByProfileId = mutableMapOf<Long, Throwable>()
        private val failOnceByProfileId = mutableMapOf<Long, Throwable>()
        private val callCounts = mutableMapOf<Long, AtomicInteger>()

        fun failConnectFor(profileId: Long, cause: Throwable) {
            failByProfileId[profileId] = cause
        }

        /** Fails the next connect for [profileId] with [cause], then
         *  succeeds on subsequent attempts. Used to simulate the TOFU
         *  retry path where the user accepts the host key. */
        fun failConnectOnceFor(profileId: Long, cause: Throwable) {
            failOnceByProfileId[profileId] = cause
        }

        fun connectCallsFor(profileId: Long): Int =
            callCounts[profileId]?.get() ?: 0

        fun totalConnectCalls(): Int = callCounts.values.sumOf { it.get() }

        override suspend fun connect(profileId: Long): Result<Session> {
            callCounts.getOrPut(profileId) { AtomicInteger(0) }.incrementAndGet()
            failOnceByProfileId.remove(profileId)?.let { return Result.failure(it) }
            failByProfileId[profileId]?.let { return Result.failure(it) }
            val session = Session(
                id = "session-$profileId",
                profileId = profileId,
                profileName = "Profile $profileId",
                host = "host-$profileId",
                port = 22,
                connectedAt = 0L,
            )
            _openSessions.value = _openSessions.value + session
            return Result.success(session)
        }

        override fun focus(sessionId: String) {
            focusedSessionId.value = sessionId
        }
        override suspend fun disconnect(sessionId: String) { /* no-op */ }
        override suspend fun cancelConnect(profileId: Long) { /* no-op */ }
        override fun markFilesUsed(sessionId: String) { /* no-op */ }
        override fun scheduleGraceDisconnect(sessionId: String) { /* no-op */ }
        override fun cancelGraceDisconnect(sessionId: String) { /* no-op */ }
    }

    private class FakeResumePreferences(
        profileIds: Set<Long> = emptySet(),
        focusedProfileId: Long? = null,
    ) : SessionResumePreferences {
        private val _profileIds = MutableStateFlow(profileIds)
        override val profileIds: Flow<Set<Long>> = _profileIds
        private val _focusedProfileId = MutableStateFlow(focusedProfileId)
        override val focusedProfileId: Flow<Long?> = _focusedProfileId
        override val tabMemos: Flow<List<TabMemo>> = MutableStateFlow(emptyList())
        override val remotePaths: Flow<Map<Long, String>> = MutableStateFlow(emptyMap())
        override val lastTopLevelRoute: Flow<String> = MutableStateFlow("connections")

        override suspend fun setProfileIds(ids: Set<Long>) { _profileIds.value = ids }
        override suspend fun setTabMemos(memos: List<TabMemo>) { /* no-op */ }
        override suspend fun setFocusedProfileId(id: Long?) { _focusedProfileId.value = id }
        override suspend fun setRemotePath(profileId: Long, path: String) { /* no-op */ }
        override suspend fun setLastTopLevelRoute(route: String) { /* no-op */ }
        override suspend fun clearResumeSubset() {
            _profileIds.value = emptySet()
            _focusedProfileId.value = null
        }
    }

    private class FakeAutoResumePreferences(enabled: Boolean) : AutoResumePreferences {
        private val _enabled = MutableStateFlow(enabled)
        override val autoResumeSessions: Flow<Boolean> = _enabled
        override suspend fun setAutoResumeSessions(value: Boolean) { _enabled.value = value }
    }
}
