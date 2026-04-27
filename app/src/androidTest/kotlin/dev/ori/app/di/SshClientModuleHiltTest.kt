package dev.ori.app.di

import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.ori.core.common.model.Protocol
import dev.ori.core.network.ssh.SshClient
import dev.ori.core.network.ssh.SshScpClientImpl
import dev.ori.core.network.ssh.SshSftpClientImpl
import dev.ori.data.di.DefaultSshClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Hilt smoke test that verifies the full `SshClientModule` wiring after T17's
 * migration to `@IntoMap` + `@DefaultSshClient`.
 *
 * Three invariants are pinned here:
 *  1. `Map<Protocol, SshClient>` resolves entries for every protocol key.
 *  2. Protocol.SCP → [SshScpClientImpl] (not [SshSftpClientImpl]).
 *  3. `@DefaultSshClient` still resolves to [SshSftpClientImpl].
 *
 * Runs as a JUnit 4 instrumentation test (Hilt's `@HiltAndroidTest` requires
 * JUnit 4 rule). Session-state cross-impl test is a pure JVM unit test in
 * `core-network/src/test/` — see `SshClientShareSessionStateTest`.
 */
@HiltAndroidTest
class SshClientModuleHiltTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var clients: Map<Protocol, @JvmSuppressWildcards SshClient>

    @Inject
    @DefaultSshClient
    lateinit var defaultClient: SshClient

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun protocolMap_resolvesAllSshProtocols() {
        assertThat(clients.keys).containsAtLeast(Protocol.SFTP, Protocol.SCP, Protocol.SSH)
        assertThat(clients[Protocol.SFTP]).isInstanceOf(SshSftpClientImpl::class.java)
        assertThat(clients[Protocol.SCP]).isInstanceOf(SshScpClientImpl::class.java)
        // Decision 9: SSH is bound to the SFTP impl, not SCP. Pin both halves so a
        // future binding swap is caught.
        assertThat(clients[Protocol.SSH]).isInstanceOf(SshSftpClientImpl::class.java)
        assertThat(clients[Protocol.SCP]).isNotInstanceOf(SshSftpClientImpl::class.java)
    }

    @Test
    fun defaultClient_isSftp() {
        assertThat(defaultClient).isInstanceOf(SshSftpClientImpl::class.java)
    }
}
