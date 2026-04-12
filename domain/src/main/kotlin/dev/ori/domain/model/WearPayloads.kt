package dev.ori.domain.model

/**
 * Payloads exchanged between phone and watch via Data Layer API.
 * Must remain serializable with a stable binary format -- avoid renaming fields.
 *
 * Serialization is done manually via android.os.Bundle / PutDataMapRequest rather than
 * Kotlin serialization to avoid adding a new dependency. Each payload has a toMap() /
 * fromMap() pair in the data sync layer.
 */

data class WearConnectionPayload(
    val profileId: Long,
    val serverName: String,
    val host: String,
    val status: String, // ConnectionStatus enum name
    val connectedSinceMillis: Long?,
)

data class WearTransferPayload(
    val transferId: Long,
    val sourcePath: String,
    val destinationPath: String,
    val direction: String, // TransferDirection enum name
    val status: String, // TransferStatus enum name
    val totalBytes: Long,
    val transferredBytes: Long,
    val filesTransferred: Int,
    val fileCount: Int,
)

data class WearSnippetPayload(
    val id: Long,
    val name: String,
    val command: String,
    val category: String,
    val serverProfileId: Long?,
)

data class WearCommandRequest(
    val requestId: String,
    val profileId: Long,
    val command: String,
)

data class WearCommandResponse(
    val requestId: String,
    val exitCode: Int,
    val stdout: String, // truncated to first 4KB
    val stderr: String, // truncated to first 1KB
    val truncated: Boolean,
)

/** 2FA: phone -> watch when a connection needs approval */
data class WearTwoFactorRequest(
    val requestId: String,
    val profileId: Long,
    val serverName: String,
    val host: String,
    val expiresAtMillis: Long, // 30s from now
)

/** 2FA: watch -> phone response */
data class WearTwoFactorResponse(
    val requestId: String,
    val approved: Boolean,
)

/** Data Layer paths and Message paths */
object WearPaths {
    const val CONNECTIONS_STATUS = "/oridev/connections/status"
    const val TRANSFERS_ACTIVE = "/oridev/transfers/active"
    const val SNIPPETS_WATCH = "/oridev/snippets/watch"
    const val COMMAND_EXECUTE = "/oridev/command/execute"
    const val COMMAND_RESPONSE = "/oridev/command/response"
    const val PANIC_DISCONNECT_ALL = "/oridev/panic/disconnect-all"
    const val CONNECT_REQUEST = "/oridev/connect"
    const val DISCONNECT_REQUEST = "/oridev/disconnect"
    const val TWO_FA_REQUEST = "/oridev/2fa/request"
    const val TWO_FA_RESPONSE = "/oridev/2fa/response"
}
