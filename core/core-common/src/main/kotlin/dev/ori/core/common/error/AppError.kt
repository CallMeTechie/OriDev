package dev.ori.core.common.error

sealed class AppError(val message: String, val cause: Throwable? = null) {
    class NetworkError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class AuthenticationError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class HostKeyMismatch(val host: String, val expectedFingerprint: String, val actualFingerprint: String) :
        AppError("Host key mismatch for $host")
    class HostKeyUnknown(val host: String, val fingerprint: String, val keyType: String) :
        AppError("Unknown host key for $host")
    class FileOperationError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class TransferError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class PermissionDenied(message: String) : AppError(message)
    class StorageError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class ProxmoxApiError(val statusCode: Int, message: String) : AppError(message)
    class PremiumRequired(val feature: String) : AppError("Premium required for $feature")
    class LimitReached(val resource: String, val limit: Int) : AppError("Limit of $limit reached for $resource")
}
