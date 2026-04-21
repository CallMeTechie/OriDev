package dev.ori.core.security.crypto

import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Replaces Android's built-in stripped-down "BC" security provider with
 * the full `bcprov-jdk18on` bundled via SSHJ (1.80 as of writing).
 *
 * ### Why this exists
 *
 * Android ships with a `Security.Provider` whose name is also "BC" but
 * which is a reduced-algorithm subset of the real BouncyCastle. SSHJ and
 * similar libraries do `Security.getProvider("BC")` and ask for modern
 * algorithms like `Ed25519`, only to get back Android's limited BC and
 * fail with:
 *
 * ```
 * NoSuchAlgorithmException: no such algorithm: 25519 for provider BC
 * ```
 *
 * which is exactly what the Non-Fatal error log on the user's Pixel Fold
 * showed after the fail2ban unban + sshd reachable. Re-registering the
 * full BC at the top of the provider list makes Ed25519 / Curve25519 /
 * ChaCha20-Poly1305 etc. all resolvable, which unblocks SSHJ's modern
 * host-key and key-exchange algorithms.
 *
 * ### When to call
 *
 * Call [install] once at application startup, **before** the first SSHJ
 * / SFTP / crypto operation. [dev.ori.app.OriDevApplication.onCreate]
 * wires it up.
 */
public object BouncyCastleSetup {

    private const val TAG = "BouncyCastleSetup"
    private const val PROVIDER_NAME = "BC"

    /**
     * Idempotent: safe to call multiple times. If the currently registered
     * "BC" is already a [BouncyCastleProvider] (i.e. we — or the library —
     * already replaced Android's default), this is a no-op.
     */
    public fun install() {
        val existing = Security.getProvider(PROVIDER_NAME)
        if (existing is BouncyCastleProvider) {
            return
        }
        Security.removeProvider(PROVIDER_NAME)
        val position = Security.insertProviderAt(BouncyCastleProvider(), 1)
        if (position == -1) {
            Log.w(TAG, "Failed to register BouncyCastle provider; SSHJ Ed25519 may not work.")
        }
    }
}
