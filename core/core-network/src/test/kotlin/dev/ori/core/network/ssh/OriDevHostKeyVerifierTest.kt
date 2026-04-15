package dev.ori.core.network.ssh

import com.google.common.truth.Truth.assertThat
import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PublicKey

/**
 * Regression tests for the SSH TOFU (Trust On First Use) host-key verifier.
 *
 * These tests lock in the three contractually-required behaviors so future
 * refactors cannot silently weaken host-key verification:
 *
 *  1. Unknown host → reject with [AppError.HostKeyUnknown] so the UI can
 *     prompt the user for explicit consent before persisting the fingerprint.
 *  2. Known host + matching fingerprint → accept and bump `lastSeen`.
 *  3. Known host + mismatched fingerprint → reject with
 *     [AppError.HostKeyMismatch]. NEVER silently overwrite.
 *
 * See docs/superpowers/specs/2026-04-15-tofu-audit.md for the full audit.
 */
class OriDevHostKeyVerifierTest {

    private lateinit var hostKeyStore: HostKeyStore
    private lateinit var verifier: OriDevHostKeyVerifier
    private lateinit var publicKey: PublicKey
    private lateinit var fingerprint: String

    private val host = "example.com"
    private val port = 22

    @BeforeEach
    fun setUp() {
        hostKeyStore = mockk(relaxed = true)
        verifier = OriDevHostKeyVerifier(hostKeyStore)
        publicKey = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }
            .generateKeyPair().public
        fingerprint = OriDevHostKeyVerifier.fingerprint(publicKey)
    }

    @Test
    fun verify_unknownHost_throwsHostKeyUnknownAndDoesNotPersist() {
        coEvery { hostKeyStore.findHost(host, port) } returns null

        val thrown = assertThrows(AppErrorException::class.java) {
            verifier.verify(host, port, publicKey)
        }

        assertThat(thrown.error).isInstanceOf(AppError.HostKeyUnknown::class.java)
        val err = thrown.error as AppError.HostKeyUnknown
        assertThat(err.host).isEqualTo(host)
        assertThat(err.fingerprint).isEqualTo(fingerprint)
        assertThat(err.keyType).isEqualTo("RSA")
        // Must NOT auto-trust: persistence only happens after explicit user consent
        // via KnownHostRepository.trustHost, not from inside the verifier.
        coVerify(exactly = 0) { hostKeyStore.updateLastSeen(any(), any()) }
    }

    @Test
    fun verify_knownHostWithMatchingFingerprint_acceptsAndUpdatesLastSeen() {
        coEvery { hostKeyStore.findHost(host, port) } returns StoredHostKey(
            fingerprint = fingerprint,
            keyType = "RSA",
        )

        val accepted = verifier.verify(host, port, publicKey)

        assertThat(accepted).isTrue()
        coVerify(exactly = 1) { hostKeyStore.updateLastSeen(host, port) }
    }

    @Test
    fun verify_knownHostWithMismatchedFingerprint_throwsHostKeyMismatchAndDoesNotOverwrite() {
        val storedFingerprint = "DIFFERENT_FINGERPRINT_BASE64=="
        coEvery { hostKeyStore.findHost(host, port) } returns StoredHostKey(
            fingerprint = storedFingerprint,
            keyType = "RSA",
        )

        val thrown = assertThrows(AppErrorException::class.java) {
            verifier.verify(host, port, publicKey)
        }

        assertThat(thrown.error).isInstanceOf(AppError.HostKeyMismatch::class.java)
        val err = thrown.error as AppError.HostKeyMismatch
        assertThat(err.host).isEqualTo(host)
        assertThat(err.expectedFingerprint).isEqualTo(storedFingerprint)
        assertThat(err.actualFingerprint).isEqualTo(fingerprint)
        // Critical: mismatch path MUST NOT silently overwrite the stored key.
        coVerify(exactly = 0) { hostKeyStore.updateLastSeen(any(), any()) }
    }

    @Test
    fun findExistingAlgorithms_knownHost_returnsStoredKeyType() {
        coEvery { hostKeyStore.findHost(host, port) } returns StoredHostKey(
            fingerprint = fingerprint,
            keyType = "RSA",
        )

        val algorithms = verifier.findExistingAlgorithms(host, port)

        assertThat(algorithms).containsExactly("RSA")
    }

    @Test
    fun findExistingAlgorithms_unknownHost_returnsEmptyList() {
        coEvery { hostKeyStore.findHost(host, port) } returns null

        val algorithms = verifier.findExistingAlgorithms(host, port)

        assertThat(algorithms).isEmpty()
    }

    companion object {
        private const val KEY_SIZE = 2048
    }
}
