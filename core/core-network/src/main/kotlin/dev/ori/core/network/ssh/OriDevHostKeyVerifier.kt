package dev.ori.core.network.ssh

import dev.ori.core.common.error.AppError
import dev.ori.core.common.result.AppErrorException
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OriDevHostKeyVerifier @Inject constructor(
    private val hostKeyStore: HostKeyStore,
) : HostKeyVerifier {

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = fingerprint(key)
        val keyType = key.algorithm

        return runBlocking {
            val stored = hostKeyStore.findHost(hostname, port)

            when {
                stored == null -> {
                    throw AppErrorException(
                        AppError.HostKeyUnknown(
                            host = hostname,
                            fingerprint = fingerprint,
                            keyType = keyType,
                        ),
                    )
                }

                stored.fingerprint == fingerprint -> {
                    hostKeyStore.updateLastSeen(hostname, port)
                    true
                }

                else -> {
                    throw AppErrorException(
                        AppError.HostKeyMismatch(
                            host = hostname,
                            expectedFingerprint = stored.fingerprint,
                            actualFingerprint = fingerprint,
                        ),
                    )
                }
            }
        }
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> {
        return runBlocking {
            val stored = hostKeyStore.findHost(hostname, port)
            if (stored != null) listOf(stored.keyType) else emptyList()
        }
    }

    companion object {
        fun fingerprint(key: PublicKey): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(key.encoded)
            return Base64.getEncoder().encodeToString(hash)
        }
    }
}
