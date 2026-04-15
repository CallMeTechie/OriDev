# SSH TOFU Host-Key Verification Audit (Option 5 – S3)

**Date:** 2026-04-15
**Scope:** Security hardening task S3 — audit Trust-On-First-Use (TOFU)
host-key verification for every SSH connection path in Ori:Dev.
**Outcome:** Case D — existing implementation is correct. No code changes
required. Regression tests added to lock in the three TOFU behaviors.

## CLAUDE.md requirement

> **SSH Host Keys:** Trust on First Use (TOFU). Reject on mismatch.

## Current state

### SSH connection surface

There is exactly **one** SSHJ connection path in the codebase:
`core/core-network/src/main/kotlin/dev/ori/core/network/ssh/SshClientImpl.kt`
— method `connect(host, port, username, password, privateKey)`.

```
val client = SSHClient()
client.addHostKeyVerifier(hostKeyVerifier)   // OriDevHostKeyVerifier
client.connect(host, port)
```

A repo-wide search for `SSHClient()` / `SSHClient(` outside tests returns a
single hit, so no alternative bypass path exists. There is no usage of
SSHJ's `PromiscuousVerifier`, no lambda no-op (`HostKeyVerifier { _, _, _ ->
true }`), and `setHostKeyVerifier(null)` is never called.

### Verifier implementation

`core/core-network/src/main/kotlin/dev/ori/core/network/ssh/OriDevHostKeyVerifier.kt`
is a custom `net.schmizz.sshj.transport.verification.HostKeyVerifier`
implementation backed by a `HostKeyStore` port. `HostKeyStore` is
implemented in `data/repository/HostKeyStoreImpl.kt` against the Room
`KnownHostDao` and `KnownHostEntity` (unique index on `(host, port)`).

The `verify(hostname, port, key)` logic is:

| DAO lookup result            | Behavior                                                                 |
|------------------------------|--------------------------------------------------------------------------|
| `null` (unknown host)        | throw `AppErrorException(AppError.HostKeyUnknown)` — **reject**          |
| fingerprint matches          | call `updateLastSeen`, return `true` — **accept**                        |
| fingerprint differs          | throw `AppErrorException(AppError.HostKeyMismatch)` — **reject**         |

Fingerprint is computed as the Base64 encoding of the SHA-256 digest of
`PublicKey.encoded`.

### TOFU persistence flow

The verifier itself never persists a fingerprint for unknown hosts — it
rejects with `HostKeyUnknown` and carries the presented fingerprint and key
algorithm in the error. The `ConnectUseCase` rethrows both `HostKeyUnknown`
and `HostKeyMismatch` untouched so the calling UI layer can:

1. Surface the presented fingerprint to the user.
2. On explicit consent, call `KnownHostRepository.trustHost(host, port,
   keyType, fingerprint)` to persist the fingerprint.
3. Retry `ConnectUseCase`.

This is a stricter "Trust on First **Prompt**" variant of TOFU — the user
is always shown the fingerprint before it is written to the DAO, matching
the intent of the CLAUDE.md security requirement.

## Findings

Ordered by severity. None are security flaws.

### (None) No critical issues

- `PromiscuousVerifier`: **not used anywhere**.
- Lambda no-op verifier: **not used**.
- `null` verifier: **not used**.
- Mismatch silently accepted: **no** — mismatch throws, lastSeen is not
  updated, stored key is not overwritten.
- Alternative SSH code path bypassing the verifier: **none found**.

### Low — explicit-prompt TOFU requires UI wiring (follow-up)

The verifier's unknown-host path throws `HostKeyUnknown`, carrying the
fingerprint to the caller for user consent. That path is correct and
strictly safer than silent auto-trust, **but** the UI prompt that would
catch `HostKeyUnknown` and call `KnownHostRepository.trustHost` is not yet
wired up in the connection flow. Until it is, a first connection to a new
host is not possible through the UI — the user cannot complete TOFU.

This is a UX/feature gap, not a security regression. Tracked as follow-up
below.

### Low — `runBlocking` inside `verify()`

`OriDevHostKeyVerifier.verify` wraps the DAO suspend calls in
`runBlocking`. SSHJ invokes the verifier from its transport thread during
the key-exchange handshake, so this is acceptable (no caller-side
coroutine context to join), but is worth noting in case the transport
layer is refactored onto a suspend boundary in future.

## Fixes applied in this PR

**None — code is correct.**

## Tests added

New file:
`core/core-network/src/test/kotlin/dev/ori/core/network/ssh/OriDevHostKeyVerifierTest.kt`

Five regression tests locking in the verifier contract:

1. `verify_unknownHost_throwsHostKeyUnknownAndDoesNotPersist` — unknown
   host throws `HostKeyUnknown` with the correct fingerprint/keyType and
   never calls `updateLastSeen`.
2. `verify_knownHostWithMatchingFingerprint_acceptsAndUpdatesLastSeen` —
   matching fingerprint returns `true` and bumps `lastSeen`.
3. `verify_knownHostWithMismatchedFingerprint_throwsHostKeyMismatchAndDoesNotOverwrite`
   — mismatch throws `HostKeyMismatch` and never updates the stored key.
4. `findExistingAlgorithms_knownHost_returnsStoredKeyType`.
5. `findExistingAlgorithms_unknownHost_returnsEmptyList`.

Tests generate a real 2048-bit RSA public key via `KeyPairGenerator` so
the SHA-256 fingerprint path runs end-to-end against the same code that
production uses.

## Follow-up work

1. **UI prompt for unknown/mismatched host keys.** Add a Compose dialog
   (in `feature-connections`) that catches `AppErrorException` carrying
   `HostKeyUnknown` or `HostKeyMismatch`, shows the presented fingerprint
   and key type, and — on user consent — calls
   `KnownHostRepository.trustHost` and retries `ConnectUseCase`. For
   `HostKeyMismatch` the prompt must require an extra confirmation and
   explicitly remove the stored key before re-trusting.
2. **`hostKeyStore.trustHost` port.** Consider promoting `trustHost` from
   `KnownHostRepository` to the `HostKeyStore` port in
   `core-network` so the verifier could auto-persist on first use if we
   ever decide to trade the explicit-prompt model for silent TOFU on new
   hosts. Not recommended unless product explicitly requests it.
3. **SSH key-exchange algorithm pinning.** Current verifier trusts any
   key type the server presents. A future hardening pass could constrain
   to Ed25519 / ECDSA-P256 / RSA-SHA2-{256,512}.
