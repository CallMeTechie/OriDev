package dev.ori.core.network.proxmox

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate

class CertificateValidatorTest {

    @Test
    fun `computeFingerprint deterministic for same cert`() {
        val cert = generateSelfSignedCert()
        val fp1 = CertificateValidator.computeFingerprint(cert)
        val fp2 = CertificateValidator.computeFingerprint(cert)

        assertThat(fp1).isEqualTo(fp2)
    }

    @Test
    fun `computeFingerprint format is hex with colons`() {
        val cert = generateSelfSignedCert()
        val fp = CertificateValidator.computeFingerprint(cert)

        // Expected: 32 uppercase hex pairs separated by 31 colons
        assertThat(fp).matches("^[0-9A-F]{2}(:[0-9A-F]{2}){31}$")
    }

    @Test
    fun `probeFingerprint invalid host returns failure`() = runBlocking {
        val result = CertificateValidator.probeFingerprint("no-such-host.invalid", 8006)

        assertThat(result).isInstanceOf(FingerprintProbeResult.Failure::class.java)
    }

    private fun generateSelfSignedCert(): X509Certificate {
        val held = HeldCertificate.Builder()
            .commonName("test-proxmox")
            .build()
        return held.certificate
    }
}
