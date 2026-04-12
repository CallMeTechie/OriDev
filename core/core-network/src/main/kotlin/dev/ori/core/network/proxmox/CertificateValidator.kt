package dev.ori.core.network.proxmox

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class FingerprintProbeResult {
    data class Success(val fingerprint: String) : FingerprintProbeResult()
    data class Failure(val reason: String) : FingerprintProbeResult()
}

object CertificateValidator {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val PROBE_SOCKET_TIMEOUT_MS = 10_000

    /**
     * Compute SHA-256 fingerprint of a certificate.
     * Format: uppercase hex with colons (e.g., "A1:B2:C3:...").
     */
    fun computeFingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Builds an OkHttpClient that validates the server certificate against a pinned fingerprint
     * DURING the TLS handshake. Fails the handshake (before any request bytes are sent) if mismatch.
     */
    fun buildPinnedClient(pinnedFingerprint: String): OkHttpClient {
        val pinningTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                throw CertificateException("Client certificates not supported")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                if (chain.isEmpty()) {
                    throw CertificateException("Empty certificate chain")
                }
                val leaf = chain[0]
                val actual = computeFingerprint(leaf)
                if (actual != pinnedFingerprint) {
                    throw CertificateException(
                        "Certificate fingerprint mismatch.\nExpected: $pinnedFingerprint\nActual: $actual",
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(pinningTrustManager), java.security.SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, pinningTrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Probes a Proxmox host via a one-shot SSLSocket to extract the leaf certificate fingerprint.
     * Used ONLY during the "Add Node" wizard, BEFORE any request with auth headers is made.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun probeFingerprint(host: String, port: Int): FingerprintProbeResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val trustAll = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(trustAll), java.security.SecureRandom())
                }
                val socket = sslContext.socketFactory.createSocket(host, port) as SSLSocket
                socket.use { s ->
                    s.soTimeout = PROBE_SOCKET_TIMEOUT_MS
                    s.startHandshake()
                    val leaf = s.session.peerCertificates.firstOrNull() as? X509Certificate
                        ?: return@withContext FingerprintProbeResult.Failure("No leaf certificate")
                    FingerprintProbeResult.Success(computeFingerprint(leaf))
                }
            } catch (e: java.net.UnknownHostException) {
                FingerprintProbeResult.Failure("Unknown host: $host")
            } catch (e: java.net.ConnectException) {
                FingerprintProbeResult.Failure("Connection refused to $host:$port")
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                FingerprintProbeResult.Failure("TLS handshake failed: ${e.message}")
            } catch (e: Exception) {
                FingerprintProbeResult.Failure("Probe failed: ${e.message}")
            }
        }
}
